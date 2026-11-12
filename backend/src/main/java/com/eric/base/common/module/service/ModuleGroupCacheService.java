package com.eric.base.common.module.service;

import com.eric.base.common.module.dao.CommonModuleDao;
import com.eric.base.common.module.model.CommonModule;
import com.eric.base.common.module.properties.CommonModuleProperties;
import com.eric.base.common.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 模块组开关状态缓存服务
 *
 * <p>设计依据 docs/v1_1-common/05-增加接口开关/接口开关机制设计方案-修订稿.md 第 9 节：
 * 进程内 TTL 缓存为主，Redis pub/sub 广播为可选增强，DB 故障 fail-open。
 * 判定入口 {@link #isEnabled}，供模块开关拦截器（MOD2-BE-004）逐请求调用：</p>
 * <ul>
 * <li>结构：进程内不可变 Map（fieldKey → fieldEnable），volatile 引用整体替换，读路径无锁；</li>
 * <li>TTL：缓存超过 {@link CommonModuleProperties#CACHE_TTL_SECONDS}（默认 30 秒，决策 D1）时
 *     回源 DB 全量刷新（common_module_group 表量极小，全量查询代价可忽略），
 *     刷新经锁单飞，防止并发请求重复回源；</li>
 * <li>fail-open：DB 异常且无可用缓存 → 放行并告警；DB 异常但有缓存 → 沿用上次缓存值并告警；
 *     key 不在 common_module_group 中（未知 key，如注解与库中拼写不一致）→ 按「开启」放行并告警；</li>
 * <li>广播：切换成功路径（{@link #refreshAndBroadcast}，MOD3 管理面调用）在完成本实例刷新后，
 *     经 Redis pub/sub 广播失效通知其他实例；广播 best-effort，失败仅记日志、依赖 TTL 收敛
 *     （部署环境默认无 Redis，取 {@code ModelCacheService} 同款取舍）。</li>
 * </ul>
 * <p>构造时不做任何 IO（懒加载，预热由启动同步任务 MOD2-BE-003 显式调用 {@link #refresh()}），
 * 保证 Spring 测试上下文不写库、不连 Redis。</p>
 *
 * @author Eric
 * @date 2026/11/12
 * @description 模块组（module-group）接口开关状态缓存：TTL 缓存 + Redis 广播失效 + fail-open。
 */
@Service
public class ModuleGroupCacheService implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(ModuleGroupCacheService.class);

    /** Redis 广播频道：模块组开关切换后发布失效通知，各实例订阅后失效自身缓存（下次判定回源） */
    public static final String CHANNEL_MODULE_GROUP_EVICT = "common:module:group:evict";

    /** 广播消息负载（接收方不解析内容，收到频道消息即整体失效，仅用于日志定位） */
    private static final String EVICT_MESSAGE = "evict";

    private final CommonModuleDao moduleDao;
    private final RedisTemplate<String, Object> redisTemplate;

    /** 刷新单飞锁：TTL 过期瞬间并发请求只允许一个线程回源 DB，其余复用结果 */
    private final Object refreshLock = new Object();

    /**
     * 缓存快照：不可变 Map + 加载时间戳，volatile 引用整体替换保证并发读安全；
     * loadedAtMillis = 0 表示尚未加载（构造初始态）或已被失效（下次判定回源）。
     */
    private volatile CacheSnapshot snapshot = new CacheSnapshot(Map.of(), 0);

    /**
     * 缓存快照载体（statuses 不可变、loadedAtMillis 只读，可安全跨线程共享）
     */
    private record CacheSnapshot(Map<String, Boolean> statuses, long loadedAtMillis) {
    }

    public ModuleGroupCacheService(CommonModuleDao moduleDao, RedisTemplate<String, Object> redisTemplate) {
        this.moduleDao = moduleDao;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 判断模块组是否开启（模块开关拦截器的逐请求判定入口）。
     * <p>缓存未加载或超过 TTL 时回源 DB 全量刷新；DB 异常按 fail-open 处理（见类注释），
     * 未知 key 按「开启」放行并告警（修订稿第 2 节语义表口径）。</p>
     *
     * @param moduleGroupKey 模块组标识（@ModuleGroup 注解的 moduleGroupKey）
     * @return true 开启（含 fail-open 场景）/ false 关闭
     */
    public boolean isEnabled(String moduleGroupKey) {
        if (StringUtil.isEmpty(moduleGroupKey)) {
            // 空 key 无从判定，按开启放行（无注解场景不会走到这里）
            return true;
        }
        CacheSnapshot current = ensureFreshSnapshot();
        Boolean enable = current.statuses().get(moduleGroupKey);
        if (null == enable) {
            log.warn("module group key [{}] not found in cache (size={}), treat as enabled (fail-open)",
                    moduleGroupKey, current.statuses().size());
            return true;
        }
        return enable;
    }

    /**
     * 全量预热 / 刷新：从 DB 全量加载并整体替换缓存。
     * <p>即使缓存仍新鲜也强制回源，供启动同步任务（MOD2-BE-003）启动期预热等场景调用；
     * DB 刷新失败仅记录日志并保留旧缓存，不抛出。</p>
     */
    public void refresh() {
        loadFromDb(true);
    }

    /**
     * 切换成功路径（MOD3 管理面 /enable 更新 DB 后调用）：本实例立即全量刷新，
     * 并尝试经 Redis pub/sub 广播失效通知其他实例。
     * <p>广播 best-effort：Redis 不可用或发布失败仅记日志，其他实例依赖 TTL 收敛（D1）。</p>
     */
    public void refreshAndBroadcast() {
        refresh();
        try {
            redisTemplate.convertAndSend(CHANNEL_MODULE_GROUP_EVICT, EVICT_MESSAGE);
        } catch (Exception e) {
            log.warn("failed to broadcast module group evict via redis, channel={}, rely on ttl convergence ({}s)",
                    CHANNEL_MODULE_GROUP_EVICT, CommonModuleProperties.CACHE_TTL_SECONDS, e);
        }
    }

    /**
     * 收到广播消息（其他实例切换了模块组开关）：失效本实例缓存，下次判定回源 DB。
     * <p>只失效不回源，避免在 Redis 监听线程上做 DB IO；消息负载不参与判定，
     * 本实例广播时自己也会收到本消息，重复失效一次代价可忽略。</p>
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            invalidate();
            log.info("module group cache invalidated by redis broadcast, channel={}", CHANNEL_MODULE_GROUP_EVICT);
        } catch (Exception e) {
            log.warn("failed to invalidate module group cache on broadcast message", e);
        }
    }

    /**
     * 失效缓存（保留旧数据，仅清空加载时间戳）：下次判定回源 DB，
     * DB 回源失败时仍可沿用旧缓存值。供广播消息处理与单测模拟 TTL 到期使用。
     */
    void invalidate() {
        snapshot = new CacheSnapshot(snapshot.statuses(), 0);
    }

    /**
     * 取当前快照，未加载或超过 TTL 时先回源刷新
     */
    private CacheSnapshot ensureFreshSnapshot() {
        CacheSnapshot current = snapshot;
        if (isFresh(current)) {
            return current;
        }
        return loadFromDb(false);
    }

    /**
     * 缓存是否新鲜：已加载且距加载时间未超过 TTL
     */
    private boolean isFresh(CacheSnapshot current) {
        return current.loadedAtMillis() > 0
                && System.currentTimeMillis() - current.loadedAtMillis() < ttlMillis();
    }

    private long ttlMillis() {
        return TimeUnit.SECONDS.toMillis(CommonModuleProperties.CACHE_TTL_SECONDS);
    }

    /**
     * 回源 DB 全量加载并整体替换缓存（单飞：同一线程持锁期间其他请求等待复用结果）。
     *
     * @param force true 强制刷新（切换成功路径 / 预热）；false 仅在缓存过期时刷新
     * @return 刷新后的快照；DB 异常时返回刷新前的快照（可能为空快照，由调用方按 fail-open 处理）
     */
    private CacheSnapshot loadFromDb(boolean force) {
        synchronized (refreshLock) {
            CacheSnapshot current = snapshot;
            // 单飞：等锁期间其他线程可能已完成刷新，非强制且缓存已新鲜时直接复用
            if (!force && isFresh(current)) {
                return current;
            }
            try {
                List<CommonModule> modules = moduleDao.selectAll();
                Map<String, Boolean> statuses = new HashMap<>();
                for (CommonModule module : modules) {
                    // 脏数据（无 key 或无状态）不进入缓存，避免空指针
                    if (StringUtil.isNotEmpty(module.getFieldKey()) && null != module.getFieldEnable()) {
                        statuses.put(module.getFieldKey(), module.getFieldEnable());
                    }
                }
                CacheSnapshot fresh = new CacheSnapshot(Map.copyOf(statuses), System.currentTimeMillis());
                snapshot = fresh;
                log.info("module group cache refreshed from db, size={}", fresh.statuses().size());
                return fresh;
            } catch (Exception e) {
                // fail-open：DB 故障不放大为全站拒绝；有旧缓存沿用旧值，无旧缓存由调用方按未知 key 放行
                log.warn("failed to refresh module group cache from db, keep last cache (fail-open)", e);
                return current;
            }
        }
    }
}
