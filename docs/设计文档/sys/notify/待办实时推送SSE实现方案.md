# 待办实时推送 SSE 实现方案

## 1. 概述

### 1.1 目标

实现"服务器发送待办，在线客户端实时接收并提示"的实时推送能力。

### 1.2 技术选型

| 选项 | 选择 | 理由 |
|------|------|------|
| **通信协议** | SSE (Server-Sent Events) | 零额外依赖（Spring `SseEmitter` 内置），单向推送场景匹配，实现简洁 |
| **Token 传递** | URL Query 参数 | 前端 `EventSource` 不支持自定义请求头，URL 参数方案改动最小 |
| **连接管理** | `ConcurrentHashMap` | 单实例部署直接可用；多实例时升级为 Redis Pub/Sub |

### 1.3 设计原则

- 不新增数据库表，SSE 推送仅做实时通知，持久化仍走现有 `SysNotifyTodoService.send()`
- 不修改现有 API 接口的请求/响应格式
- 推送失败不影响核心业务（发送待办成功写入数据库后，推送失败不抛异常）
- 鉴权沿用现有 `NotifyResolver` + `X-Login-Token` 体系

---

## 2. 架构设计

### 2.1 数据流

```
┌──────────────────────────────────────────────────────────────────┐
│                        后端服务                                    │
│                                                                   │
│  SysNotifyTodoService.send(todo, userIds)                         │
│    │  1. 写入 sys_notify_todo 主表                                │
│    │  2. 批量写入 sys_notify_todo_target 接收人表                 │
│    │  3. 发布 NotifySendEvent (ApplicationEvent)                 │
│    ▼                                                              │
│  NotifySseEventListener ( @Async + @TransactionalEventListener ) │
│    │  遍历 userIds，查询在线用户表                                 │
│    │  对在线用户通过 SseEmitterManager.sendToUser() 推送          │
│    ▼                                                              │
│  SseEmitterManager                                                │
│  ┌──────────────────────────────────────┐                        │
│  │  userId → SseEmitter 映射表          │                        │
│  │  (ConcurrentHashMap<String, SseEmitter>)                      │
│  │  ┌──────┐ ┌──────┐ ┌──────┐         │                        │
│  │  │ u001 │ │ u002 │ │ u003 │  ...    │                        │
│  │  └──────┘ └──────┘ └──────┘         │                        │
│  └──────────────────────────────────────┘                        │
│           │                                                       │
│           ▼  SSE 数据帧                                           │
│  ──────────────────────────────────────────────────────────────── │
│    event: new-todo                                                │
│    data: {"todoId":"...","todoName":"审批单待办",...}             │
│                                                                   │
│  ──────────────────────────────────────────────────────────────── │
│           │                                                       │
│           ▼  HTTP 长连接                                          │
└──────────────────────────────────────────────────────────────────┘
           │
           ▼
┌──────────────────────────────────────────────────────────────────┐
│                    前端客户端                                      │
│                                                                   │
│  EventSource 连接 /backend/sys/notify/sse/subscribe              │
│    onmessage (event: new-todo) →                                  │
│      1. 桌面通知 (Notification API)                               │
│      2. ElNotification 气泡提示                                   │
│      3. 刷新待办列表 (loadList())                                 │
│      4. 更新全局待办角标 (Pinia store)                            │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 连接生命周期

```
用户登录 → 鉴权成功 → 前端创建 EventSource
                            │
                            ▼
                    GET /backend/sys/notify/sse/subscribe?token=xxx
                            │
                            ▼
                    CurrentUserFilter 解析 token → 获取 userId
                            │
                            ▼
                    SseEmitterManager.register(userId, emitter)
                    ┌─────────────────────────────────────┐
                    │  SseEmitter 超时: 30 分钟            │
                    │  心跳: 每 30 秒发送注释帧保持连接    │
                    │  断线重连: 前端自动重连（EventSource  │
                    │    内置）                            │
                    └─────────────────────────────────────┘
                            │
            ┌───────────────┴───────────────┐
            ▼                               ▼
     用户注销 / 关闭页面              用户登录超时
            │                               │
            ▼                               ▼
    SseEmitterManager.remove(userId)    SseEmitter.onTimeout()
                                         → 自动清理
```

---

## 3. 后端实现

### 3.1 新增文件清单

| 文件 | 路径 | 职责 |
|------|------|------|
| `NotifySseController.java` | `sys/notify/controller/` | SSE 订阅端点 |
| `SseEmitterManager.java` | `sys/notify/service/` | 连接管理器 |
| `NotifySendEvent.java` | `sys/notify/event/` | 待办发送事件 |
| `NotifySseEventListener.java` | `sys/notify/service/listeners/` | 事件监听器 |

### 3.2 修改文件清单

| 文件 | 修改内容 |
|------|----------|
| `SysNotifyTodoService.java` | `send()` 方法末尾 `publishEvent(new NotifySendEvent(...))` |
| `SysNotifyConstants.java` | 新增 `ACTION_SSE_SUBSCRIBE = "sys.notify.todo.sseSubscribe"` |
| `NotifyResolver.java` | `resolve()` 中新增 `case ACTION_SSE_SUBSCRIBE` |
| `AsyncConfig.java` | 确认 `@EnableAsync` 已启用，确认线程池配置 |

### 3.3 详细设计

#### 3.3.1 `NotifySseController.java`

```java
@RestController
@RequestMapping("${project.request.prefix}/sys/notify/sse")
public class NotifySseController {

    private final SseEmitterManager sseEmitterManager;
    private final NotifyResolver resolver;

    /**
     * SSE 订阅：GET /backend/sys/notify/sse/subscribe?token=xxx
     * 返回 SseEmitter 长连接，超时 30 分钟，每 30 秒心跳。
     * 鉴权方式：URL query 参数 token → 由 CurrentUserFilter 前置解析，
     * 或在此处手动从 token 参数解析 userId。
     */
    @GetMapping("/subscribe")
    public SseEmitter subscribe(@RequestParam("token") String token) {
        // 1. 解析 token 获取 userId（复用 LoginSessionService）
        String userId = loginSessionService.resolve(token);
        if (userId == null) {
            throw new AuthorizationException("UNAUTHORIZED", "登录凭证无效");
        }
        // 2. 鉴权（登录即可）
        resolver.resolve(new AuthorizationContext(ACTION_SSE_SUBSCRIBE));
        // 3. 创建 SseEmitter（30 分钟超时）
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        // 4. 注册到管理器
        sseEmitterManager.register(userId, emitter);
        // 5. 设置回调清理
        emitter.onCompletion(() -> sseEmitterManager.remove(userId));
        emitter.onTimeout(() -> sseEmitterManager.remove(userId));
        emitter.onError(e -> sseEmitterManager.remove(userId));
        // 6. 发送初始连接成功事件
        emitter.send(SseEmitter.event().name("connected").data("{\"status\":\"ok\"}"));
        // 7. 启动心跳线程
        startHeartbeat(emitter, userId);
        return emitter;
    }
}
```

**关键设计决策**：

- EventSource 不支持自定义请求头，因此 token 从 URL query 参数传入
- 当前 `CurrentUserFilter` 只从 `X-Login-Token` 请求头解析，不在 SSE 端点上生效
- 因此 `subscribe` 端点需要**手动调用 `loginSessionService.resolve(token)`** 解析用户
- 鉴权走 `NotifyResolver`，新增 `ACTION_SSE_SUBSCRIBE` 标识（登录即可）

#### 3.3.2 `SseEmitterManager.java`

```java
@Service
public class SseEmitterManager {
    /** 在线用户映射表：userId → SseEmitter */
    private final ConcurrentHashMap<String, SseEmitter> onlineUsers = new ConcurrentHashMap<>();

    /**
     * 注册用户连接。同一用户重复订阅时，关闭旧连接，使用新连接。
     */
    public void register(String userId, SseEmitter emitter) {
        SseEmitter old = onlineUsers.put(userId, emitter);
        if (old != null) {
            try { old.complete(); } catch (Exception ignored) {}
        }
    }

    /**
     * 移除用户连接。
     */
    public void remove(String userId) {
        onlineUsers.remove(userId);
    }

    /**
     * 向指定用户推送待办事件。
     * @return true=推送成功, false=用户不在线或推送失败
     */
    public boolean sendToUser(String userId, String eventName, String data) {
        SseEmitter emitter = onlineUsers.get(userId);
        if (emitter == null) return false;
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            return true;
        } catch (IOException e) {
            // 连接已断开，清理
            onlineUsers.remove(userId);
            return false;
        }
    }

    /**
     * 获取在线用户数（仅统计，不做鉴权使用）
     */
    public int getOnlineCount() {
        return onlineUsers.size();
    }
}
```

**关键设计决策**：

- 同一用户多标签页：后打开的覆盖前一个连接（`ConcurrentHashMap.put` 天然覆盖）
- 推送失败时自动清理过期连接，不抛异常
- 所有方法线程安全，无需额外同步

#### 3.3.3 `NotifySendEvent.java`

**包路径**：`com.project.backend.sys.notify.event`

```java
public class NotifySendEvent extends ApplicationEvent {
    private final String todoId;
    private final String todoName;
    private final String notifyType;   // 1=待办 2=待阅
    private final String modelName;
    private final String modelId;
    private final String url;
    private final List<String> userIds;

    public NotifySendEvent(Object source, String todoId, String todoName,
                           String notifyType, String modelName, String modelId,
                           String url, List<String> userIds) {
        super(source);
        // 全部字段赋值
    }
    // getters
}
```

#### 3.3.4 `NotifySseEventListener.java`

**包路径**：`com.project.backend.sys.notify.service.listeners`

```java
@Component
public class NotifySseEventListener {

    private final SseEmitterManager sseEmitterManager;

    /**
     * 事务提交后触发：保证待办已持久化到数据库，推送失败不抛异常。
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotifySend(NotifySendEvent event) {
        // 1. 构建推送数据 JSON
        String data = buildPushData(event);
        // 2. 遍历接收人，向在线用户推送
        for (String userId : event.getUserIds()) {
            sseEmitterManager.sendToUser(userId, "new-todo", data);
        }
    }

    private String buildPushData(NotifySendEvent event) {
        // 使用 Jackson 序列化为 JSON 字符串
        // 字段：todoId, todoName, notifyType, modelName, modelId, url, receiveTime
    }
}
```

**使用 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`** 确保：
- 仅在 `send()` 事务提交后才推送
- 推送失败不回滚事务
- 结合 `@Async` 异步执行，不阻塞主线程

#### 3.3.5 修改 `SysNotifyTodoService.send()`

在 `send()` 方法末尾，`return` 之前，发布事件：

```java
// 在 return todo; 之前
eventPublisher.publishEvent(new NotifySendEvent(
    this,
    todo.getFieldId(),
    todo.getFieldName(),
    todo.getFieldNotifyType(),
    todo.getFieldModelName(),
    todo.getFieldModelId(),
    todo.getFieldUrl(),
    userIds
));
```

#### 3.3.6 修改 `SysNotifyConstants.java`

新增常量：

```java
/** SSE 订阅（登录即可） */
public static final String ACTION_SSE_SUBSCRIBE = "sys.notify.todo.sseSubscribe";
```

#### 3.3.7 修改 `NotifyResolver.java`

在 `resolve(AuthorizationContext context)` 的 `switch` 中新增：

```java
case ACTION_SSE_SUBSCRIBE -> resolveLoggedIn();
```

#### 3.3.8 确认 `AsyncConfig.java`

确保 `@EnableAsync` 已启用（已有 `AsyncConfig.java`，应已配置）：

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    // 可选：自定义线程池，避免占用 Tomcat 请求线程
    @Bean("notifySseExecutor")
    public Executor notifySseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notify-sse-");
        return executor;
    }
}
```

### 3.4 心跳机制

SSE 连接可能因网络设备超时断开，需要定期发送心跳：

```java
private void startHeartbeat(SseEmitter emitter, String userId) {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    scheduler.scheduleAtFixedRate(() -> {
        try {
            emitter.send(SseEmitter.event().name("heartbeat").data(""));
        } catch (IOException e) {
            scheduler.shutdown();
            sseEmitterManager.remove(userId);
        }
    }, 30, 30, TimeUnit.SECONDS);
}
```

---

## 4. 前端实现

### 4.1 新增文件

| 文件 | 路径 | 职责 |
|------|------|------|
| `useSse.js` | `src/utils/` | SSE 连接管理 composable |

### 4.2 修改文件

| 文件 | 修改内容 |
|------|----------|
| `MyTodoView.vue` | 引入 `useSse`，收到推送时触发桌面通知 + 气泡提示 + 刷新列表 |
| 全局布局组件（如 `HomeView.vue`） | 待办未读数角标 |

### 4.3 详细设计

#### 4.3.1 `useSse.js`

```js
// src/utils/useSse.js
import { ref, onMounted, onUnmounted } from 'vue'
import { ElNotification } from 'element-plus'

/**
 * SSE 连接管理 composable
 * @param {Object} options
 * @param {Function} options.onNewTodo - 收到新待办的回调
 * @param {Function} options.onConnected - 连接建立的回调
 * @returns {{ connected: Ref<boolean>, reconnect: Function }}
 */
export function useSse({ onNewTodo, onConnected } = {}) {
  const connected = ref(false)
  let eventSource = null
  let reconnectTimer = null

  function connect() {
    const token = localStorage.getItem('erp_token')
    if (!token) {
      console.warn('[useSse] 未登录，跳过 SSE 连接')
      return
    }

    // 关闭旧连接
    if (eventSource) {
      eventSource.close()
    }

    const baseUrl = window.location.origin
    const url = `${baseUrl}/backend/sys/notify/sse/subscribe?token=${encodeURIComponent(token)}`
    eventSource = new EventSource(url)

    eventSource.onopen = () => {
      connected.value = true
      onConnected?.()
    }

    eventSource.addEventListener('connected', (e) => {
      connected.value = true
    })

    eventSource.addEventListener('new-todo', (e) => {
      try {
        const data = JSON.parse(e.data)
        onNewTodo?.(data)
      } catch (err) {
        console.error('[useSse] 解析推送数据失败:', err)
      }
    })

    eventSource.addEventListener('heartbeat', () => {
      // 心跳，无需处理
    })

    eventSource.onerror = () => {
      connected.value = false
      // EventSource 内置自动重连，无需手动处理
    }
  }

  function disconnect() {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    connected.value = false
  }

  onMounted(connect)
  onUnmounted(disconnect)

  return { connected }
}
```

#### 4.3.2 修改 `MyTodoView.vue`

在 `script setup` 中插入：

```js
import { useSse } from '@/utils/useSse'
import { ElNotification } from 'element-plus'

// SSE 连接：接收新待办实时推送
useSse({
  onNewTodo(data) {
    // 1. 桌面通知（需用户授权）
    if (Notification.permission === 'granted') {
      new Notification('新待办', {
        body: data.todoName,
        icon: '/favicon.ico'
      })
    }

    // 2. 页面内气泡提示
    ElNotification({
      title: '新待办',
      message: data.todoName,
      type: data.notifyType === '1' ? 'warning' : 'info',
      duration: 5000,
      onClick: () => {
        // 点击跳转到待办列表
        router.push('/todo')
      }
    })

    // 3. 刷新列表
    loadList()

    // 4. 更新未读计数（通过 Pinia）
    // ...
  }
})
```

在 `onMounted` 中申请桌面通知权限：

```js
onMounted(() => {
  // 申请桌面通知权限
  if ('Notification' in window && Notification.permission === 'default') {
    Notification.requestPermission()
  }
  // ... 原有逻辑
})
```

#### 4.3.3 全局待办角标（可选增强）

在 `stores/user.js` 或新建 `stores/notify.js` 中维护待办未读数：

```js
// stores/notify.js
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useNotifyStore = defineStore('notify', () => {
  const unreadCount = ref(0)

  function increment() { unreadCount.value++ }
  function setCount(n) { unreadCount.value = n }
  function reset() { unreadCount.value = 0 }

  return { unreadCount, increment, setCount, reset }
})
```

在全局布局组件（如 `HomeView.vue` 的侧边栏 "待办" 菜单项）中显示角标：

```html
<el-menu-item index="/todo">
  <el-icon><Bell /></el-icon>
  <template #title>
    <span>待办</span>
    <el-badge :value="notifyStore.unreadCount" :hidden="notifyStore.unreadCount === 0" />
  </template>
</el-menu-item>
```

---

## 5. 鉴权与安全

### 5.1 鉴权流程

```
请求: GET /backend/sys/notify/sse/subscribe?token=xxx
  │
  ├─ 1. 手动从 query 参数提取 token
  ├─ 2. 调用 LoginSessionService.resolve(token) 获取 userId
  │      ├─ 无效 → 返回 401
  │      └─ 有效 → 继续
  ├─ 3. NotifyResolver.resolve(ACTION_SSE_SUBSCRIBE)
  │      ├─ 登录且启用 → 放行
  │      └─ 拒绝 → 返回 403
  └─ 4. 创建 SseEmitter，注册到管理器
```

### 5.2 安全措施

| 措施 | 实现 |
|------|------|
| 连接数限制 | 同一用户仅保留最新连接，`SseEmitterManager.register()` 覆盖旧连接 |
| 超时清理 | SseEmitter 30 分钟超时 + 心跳 30 秒，断开后自动清理 |
| 推送数据隔离 | 仅推送该用户接收的待办，不泄露他人数据 |
| 防止 Token 泄露 | URL 参数 `token` 使用 HTTPS 传输，不在浏览器历史记录中持久化 |

---

## 6. 水平扩展方案（未来）

当前方案基于单实例 `ConcurrentHashMap`。当需要多实例部署时，升级为 Redis Pub/Sub：

```
                      ┌──────────────┐
                      │  Redis       │
                      │  Pub/Sub     │
                      │  channel:    │
                      │  notify:sse  │
                      └──────┬───────┘
                             │
            ┌────────────────┼────────────────┐
            │                │                │
            ▼                ▼                ▼
      ┌──────────┐     ┌──────────┐     ┌──────────┐
      │ 实例 A   │     │ 实例 B   │     │ 实例 C   │
      │ SseEmitter│     │ SseEmitter│     │ SseEmitter│
      │ Manager  │     │ Manager  │     │ Manager  │
      └──────────┘     └──────────┘     └──────────┘
```

**改造要点**：

1. `SseEmitterManager` 不再直接发送事件，而是发布到 Redis Pub/Sub
2. 每个实例订阅 Redis 频道，收到消息后查找本地 `ConcurrentHashMap` 推送
3. 新增 `RedisMessageListener` 配置

---

## 7. 浏览器兼容性

| 浏览器 | EventSource 支持 |
|--------|-----------------|
| Chrome | 6+ ✅ |
| Firefox | 6+ ✅ |
| Safari | 5+ ✅ |
| Edge | 79+ ✅ |
| IE | ❌ 不支持 |

IE 不支持 EventSource，但本项目前端使用 Vue 3，目标用户为现代浏览器，无需特别处理。

---

## 8. 实现顺序与任务清单

### 阶段一：后端基础（预计 1-2 小时）

| # | 任务 | 文件 | 验收标准 |
|---|------|------|----------|
| 1 | 新增 `SseEmitterManager` | `sys/notify/service/SseEmitterManager.java` | 管理类可注册/移除/推送，线程安全 |
| 2 | 新增 `NotifySendEvent` | `sys/notify/event/NotifySendEvent.java` | POJO 承载所有推送字段 |
| 3 | 新增 `NotifySseEventListener` | `sys/notify/service/listeners/NotifySseEventListener.java` | 事务提交后异步推送，推送失败不抛异常 |
| 4 | 新增 `NotifySseController` | `sys/notify/controller/NotifySseController.java` | SSE 端点返回 `SseEmitter`，心跳正常 |
| 5 | 修改 `SysNotifyTodoService.send()` | `SysNotifyTodoService.java` | 发送待办后发布 `NotifySendEvent` |
| 6 | 修改 `SysNotifyConstants` | `SysNotifyConstants.java` | 新增 `ACTION_SSE_SUBSCRIBE` |
| 7 | 修改 `NotifyResolver` | `NotifyResolver.java` | 新增 `case ACTION_SSE_SUBSCRIBE` |

### 阶段二：前端接入（预计 1 小时）

| # | 任务 | 文件 | 验收标准 |
|---|------|------|----------|
| 8 | 新增 `useSse.js` composable | `src/utils/useSse.js` | 自动连接/断线重连/自动清理 |
| 9 | 修改 `MyTodoView.vue` | `MyTodoView.vue` | 收到推送后桌面通知 + 气泡提示 + 刷新列表 |
| 10 | 申请桌面通知权限 | `MyTodoView.vue` `onMounted` | 页面挂载时请求权限 |

### 阶段三：验证与优化（可选）

| # | 任务 | 验收标准 |
|---|------|----------|
| 11 | 全局待办角标 | 侧边栏或导航栏显示待办未读数 |
| 12 | 多标签页测试 | 同一用户多标签页，仅最后一个标签页收到推送 |
| 13 | 断线重连验证 | 网络断开后，EventSource 自动重连，新连接正常推送 |

---

## 9. 不涉及变更

以下内容在本次方案中**不做变更**：

- 数据库表结构（无新增表、无字段变更）
- 现有 API 接口的请求/响应格式（`/myList`、`/send` 等接口不变）
- 现有鉴权体系（`CurrentUserFilter`、`LoginSessionService` 不变）
- 前端路由配置（不新增路由）
- 构建依赖（不新增 Maven/NPM 依赖）
- 部署配置（不新增反向代理配置）

---

## 10. 回退方案

若 SSE 推送在生产环境出现稳定性问题，可回退到短轮询方案：

1. 前端 `useSse.js` 替换为 `setInterval` 每 15 秒调用 `/myList` 查询未读数
2. 后端无需作任何变更
3. 切换成本：仅需修改前端 `useSse.js` 一个文件