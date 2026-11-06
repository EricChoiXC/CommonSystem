<template>
  <div class="home-layout">
    <!-- 顶部菜单栏：LOGO + 路由面包屑 + 用户信息/登出 -->
    <header class="layout-header">
      <div class="header-left">
        <!-- LOGO：显示系统应用名称，取后端 application.name（为空时回退默认应用名） -->
        <div class="layout-logo" :title="applicationName">{{ applicationName }}</div>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="`${item}-${index}`">
            {{ item }}
          </el-breadcrumb-item>
        </el-breadcrumb>
      </div>
      <div class="header-user-box">
        <!-- 待办通知铃铛：未读计数红点，点击跳转待办列表 -->
        <el-badge :value="notifyStore.unreadCount" :hidden="notifyStore.unreadCount === 0" class="notify-badge">
          <el-button class="notify-bell-btn" size="small" text @click="router.push('/todo')">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" width="18" height="18">
              <path d="M8 16a2 2 0 0 0 2-2H6a2 2 0 0 0 2 2zM12 7c0-2.5-1.5-4.5-4-5V.5a.5.5 0 0 0-1 0V2C4.5 2.5 3 4.5 3 7v3l-1 1v1h12v-1l-1-1V7z" fill="currentColor"/>
            </svg>
          </el-button>
        </el-badge>
        <!-- 日程图标：点击弹出今日日程弹窗，底部有跳转按钮 -->
        <el-popover
          ref="calendarPopoverRef"
          trigger="click"
          :width="300"
          placement="bottom"
          @show="handleTodayCalendarShow"
        >
          <template #reference>
            <el-button class="calendar-btn" size="small" text>
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" width="18" height="18">
                <path d="M3.5 0a.5.5 0 0 1 .5.5V1h8V.5a.5.5 0 0 1 1 0V1h1a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H2a2 2 0 0 1-2-2V3a2 2 0 0 1 2-2h1V.5a.5.5 0 0 1 .5-.5zM1 4v10a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V4H1zm3 3a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7A.5.5 0 0 1 4 7zm0 3a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7A.5.5 0 0 1 4 10z" fill="currentColor"/>
              </svg>
            </el-button>
          </template>
          <div class="calendar-popover-content">
            <div v-if="todayCalendarList.length === 0" class="calendar-popover-empty">今日暂无日程</div>
            <div v-for="item in todayCalendarList" :key="item.fieldId" class="calendar-popover-item">
              <span class="calendar-popover-time">{{ formatScheduleTime(item) }}</span>
              <span class="calendar-popover-title">{{ item.fieldName }}</span>
              <el-tag :type="item.fieldType === 'TEAM' ? 'warning' : 'info'" size="small">
                {{ item.fieldType === 'TEAM' ? '团队' : '个人' }}
              </el-tag>
            </div>
            <div class="calendar-popover-footer">
              <el-button type="primary" link size="small" @click="goCalendar">跳转日程</el-button>
            </div>
          </div>
        </el-popover>
        <!-- 群组图标：点击跳转群组管理页 -->
        <el-button class="group-btn" size="small" text aria-label="群组" @click="router.push('/org/group')">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" width="18" height="18">
            <path d="M6 1.9a2.6 2.6 0 1 1 0 5.2 2.6 2.6 0 0 1 0-5.2zM.8 13.5c0-2.9 2.3-5.2 5.2-5.2s5.2 2.3 5.2 5.2H.8zM12.4 3.5a2.1 2.1 0 1 1 0 4.2 2.1 2.1 0 0 1 0-4.2zM9.6 14.5c.1-2 1-3.7 2.5-4.7 1.9.6 3.3 2.4 3.3 4.7H9.6z" fill="currentColor"/>
          </svg>
        </el-button>
        <!-- 语言切换下拉：仅改前端 locale + localStorage 记忆，不调用户语言接口、不落库（任务清单 I18N6-FE-002） -->
        <el-dropdown
          class="language-dropdown"
          trigger="click"
          @command="handleLanguageCommand"
        >
          <el-button class="language-btn" size="small" text :title="t('frontend.header.language.switch')">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" width="18" height="18">
              <path d="M4.55 3.07A7.99 7.99 0 0 1 8 2.2a8 8 0 1 1 0 16 7.99 7.99 0 0 1-3.45-.79L4.55 17.4zm-.35 1.13L2.6 9h3.5l1.1-4.8zM8 3.8L6.6 9H2.75A6.4 6.4 0 0 1 8 3.8zM9.4 9l1.4 5.2A6.42 6.42 0 0 1 8 15.6 6.4 6.4 0 0 1 2.75 9H9.4z" fill="none"/>
              <path d="M8 1a7 7 0 1 0 0 14A7 7 0 0 0 8 1zm5.5 6h-2.06a10.7 10.7 0 0 0-.83-3.4A5.52 5.52 0 0 1 13.5 7zM8 2.5c.62 0 1.5 1.4 1.87 3.5H6.13C6.5 3.9 7.38 2.5 8 2.5zM2.5 8c0-.51.07-1 .2-1.5h2.56A12 12 0 0 0 5.13 10H2.7A5.48 5.48 0 0 1 2.5 8zm1.9 3.5h1.2c.2.9.48 1.72.83 2.4A5.52 5.52 0 0 1 4.4 11.5zM8 13.5c-.62 0-1.5-1.4-1.87-3.5h3.74C9.5 12.1 8.62 13.5 8 13.5zm1.57-1.1c.35-.68.63-1.5.83-2.4h1.2a5.52 5.52 0 0 1-2.03 2.4zm1.3-3.9h-1.14c.05-.52.07-1.05.07-1.5s-.02-.98-.07-1.5h2.56c.13.5.2.99.2 1.5s-.07 1-.2 1.5z" fill="currentColor"/>
            </svg>
            <span class="language-btn-text">{{ currentLanguageName }}</span>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="lang in languageOptions"
                :key="lang.fieldCode"
                :command="lang.fieldCode"
                :disabled="lang.fieldCode === languageStore.current"
              >
                {{ lang.fieldName }}（{{ lang.fieldCode }}）
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-avatar :size="26" class="header-avatar">{{ avatarText }}</el-avatar>
        <span class="header-username">{{ userName }}</span>
        <span class="header-dept">{{ deptName }}</span>
        <el-button type="danger" size="small" plain @click="handleLogout">登出</el-button>
      </div>
    </header>

    <!-- 顶部信息栏下方：横向菜单栏，一级菜单横向排列，点击展开次级（二级/三级）菜单 -->
    <nav class="layout-nav">
      <el-menu
        class="layout-menu"
        mode="horizontal"
        menu-trigger="click"
        close-on-click-outside
        unique-opened
        router
        :default-active="activeMenu"
      >
        <el-sub-menu v-for="group in filteredMenus" :key="group.title" :index="group.title">
          <template #title>{{ group.title }}</template>
          <el-sub-menu v-for="sub in group.subMenus" :key="`${group.title}-${sub.title}`" :index="`${group.title}-${sub.title}`">
            <template #title>{{ sub.title }}</template>
            <el-menu-item v-for="item in sub.children" :key="item.path" :index="item.path">
              {{ item.title }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-for="item in group.leafChildren" :key="item.path" :index="item.path">
            {{ item.title }}
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </nav>

    <div class="layout-tabs">
      <el-tabs
        ref="tabsRef"
        v-model="activeTab"
        type="card"
        closable
        @tab-click="handleTabClick"
        @tab-remove="handleTabRemove"
        @wheel="handleTabsWheel"
      >
        <el-tab-pane v-for="tab in visitedTabs" :key="tab.key" :name="tab.path" :label="tab.title" :closable="tab.path !== defaultTab.path" />
      </el-tabs>
      <el-dropdown
        class="tab-mgmt-dropdown"
        trigger="click"
        @command="handleTabMgmtCommand"
      >
        <el-button class="tab-mgmt-btn" size="small" text>
          <el-icon><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" width="16" height="16"><path d="M3 3h10v1H3zm0 4h10v1H3zm0 4h10v1H3z" fill="currentColor" stroke="currentColor" stroke-width="0.5"/></svg></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="closeOthers">关闭其他</el-dropdown-item>
            <el-dropdown-item command="closeAll">关闭全部</el-dropdown-item>
            <el-dropdown-item divided disabled>— 切换标签 —</el-dropdown-item>
            <el-dropdown-item
              v-for="tab in visitedTabs"
              :key="tab.key"
              :command="tab.path"
              :disabled="tab.path === activeTab"
            >
              {{ tab.title }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <main class="layout-main">
      <!-- KeepAlive 页签缓存：切换标签页返回时保留页面状态（列表筛选/分页、表单已填内容）；
           include 跟随当前打开的标签页动态生成，关闭标签即释放对应缓存；
           实例按路由 path 区分，同一组件的不同单据（如编辑不同 id）互不串数据 -->
      <RouterView v-slot="{ Component, route: viewRoute }">
        <KeepAlive :include="cachedViewNames">
          <component :is="Component" :key="viewRoute.path" />
        </KeepAlive>
      </RouterView>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useNotifyStore } from '@/stores/notify'
import { useLanguageStore } from '@/stores/language'
import { hasAuth } from '@/utils/auth'
import { logout } from '@/api/sys'
import { loadAddressBook } from '@/api/org'
import { queryTodayCalendar } from '@/api/calendar'
import { getInitInfo } from '@/api/common'
import { listLanguages } from '@/api/i18n'
import { useSse } from '@/utils/useSse'
import { consumeTabsToClose, TABS_STORAGE_KEY } from '@/utils/tabs'
import { useI18n } from 'vue-i18n'
import request from '@/api/index'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const notifyStore = useNotifyStore()
const languageStore = useLanguageStore()
const { t } = useI18n()

/* ========== 顶部栏语言切换（不落库，仅 locale + localStorage） ========== */

/** 启用语言选项（免鉴权 language/list，fieldEnable=true）；加载失败时下拉为空、按钮仅显示当前语言名 */
const languageOptions = ref([])

const currentLanguageName = computed(() => {
  const hit = languageOptions.value.find((lang) => lang.fieldCode === languageStore.current)
  return hit ? hit.fieldName : languageStore.current
})

async function loadLanguageOptions() {
  try {
    const res = await listLanguages({ fieldEnable: 'true' })
    languageOptions.value = res.data || []
  } catch {
    languageOptions.value = []
  }
}

/** 切换语言：懒加载语言包 → vue-i18n locale → Element Plus locale（App.vue 联动）→ localStorage */
async function handleLanguageCommand(code) {
  try {
    await languageStore.setLanguage(code)
  } catch {
    ElMessage.error(t('frontend.common.loadFailed'))
  }
}

/** 登出时清空标签页记录，避免下一个用户登录后看到上一用户残留的页签 */
function clearVisitedTabs() {
  visitedTabs.value = []
  activeTab.value = ''
  sessionStorage.removeItem(TABS_STORAGE_KEY)
}

/* ========== 左侧菜单 ========== */

// 菜单配置：一级菜单 auth 为空表示所有登录用户可见；
// 二级分组/二级菜单项 auth 为空表示随上一级可见
const menus = [
  {
    title: '工作台',
    auth: '',
    children: [
      { title: '待办', path: '/todo', auth: '' },
      { title: '日程', path: '/calendar', auth: '' }
    ]
  },
  {
    title: '组织架构',
    auth: '',
    children: [
      { title: '当前组织架构', path: '/org', auth: '' },
      { title: '群组', path: '/org/group', auth: '' }
    ]
  },
  {
    title: '系统管理',
    auth: '',
    children: [
      {
        title: '流程管理',
        auth: '',
        children: [
          { title: '公共配置', path: '/processSetting', auth: ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_SETTING_ADMIN'] },
          { title: '模板管理', path: '/processTemplate', auth: ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_TEMPLATE_ADMIN', 'SYS_PROCESS_TEMPLATE_VIEWER'] },
          { title: '流程实例', path: '/processExample', auth: ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_EXAMPLE_ADMIN', 'SYS_PROCESS_EXAMPLE_VIEWER'] }
        ]
      },
      {
        title: '通知待办',
        auth: 'SYS_NOTIFY_ADMIN',
        children: [
          { title: '待办管理', path: '/notify', auth: '' },
          { title: '发送待办', path: '/notifySend', auth: '' }
        ]
      },
      {
        title: '附件管理',
        auth: 'SYS_ATT_MAIN',
        children: [
          { title: '附件管理', path: '/att', auth: '' },
          { title: '文件管理', path: '/attFile', auth: '' }
        ]
      },
      {
        title: '权限管理',
        auth: '',
        children: [
          { title: '角色管理', path: '/role', auth: ['SYS_ORG_ROLE_ADMIN', 'SYS_ORG_ADMIN'] },
          { title: '权限预览', path: '/rolePreview', auth: ['SYS_ORG_ROLE_ADMIN', 'SYS_ORG_ADMIN'] }
        ]
      },
      { title: '码值管理', path: '/dict', auth: '' },
      {
        title: '定时任务',
        auth: 'SYS_TASK_ADMIN',
        children: [
          { title: '定时任务管理', path: '/task', auth: '' },
          { title: '执行记录', path: '/taskHistory', auth: '' }
        ]
      },
      {
        title: '日志管理',
        auth: 'SYS_LOG_ADMIN',
        children: [{ title: '日志管理', path: '/log', auth: '' }]
      },
      {
        title: '后台管理',
        auth: 'SYS_ADMIN',
        children: [
          { title: '公共配置', path: '/setting', auth: '' }
        ]
      },
      {
        title: '多语言管理',
        auth: 'SYS_I18N_ADMIN',
        children: [
          { title: '语言管理', path: '/i18nLanguage', auth: '' },
          { title: '翻译维护', path: '/i18nTranslation', auth: '' },
          { title: '翻译部署', path: '/i18nDeploy', auth: '' }
        ]
      }
    ]
  }
]

// 递归过滤菜单节点：auth 不满足返回 null；分组下无可见子项时整个分组隐藏。
// 叶子节点直接返回自身，分组节点返回过滤后的新对象。
function filterMenuNode(node) {
  if (node.auth && !hasAuth(node.auth)) return null
  const children = (node.children || [])
    .map((child) => (child.children ? filterMenuNode(child) : !child.auth || hasAuth(child.auth) ? child : null))
    .filter(Boolean)
  if (!children.length) return null
  return { ...node, children }
}

// 按权限过滤菜单：一级无权限整个隐藏；二级分组/菜单项无权限单独隐藏；
// 拆出 subMenus（含子级的二级分组）与 leafChildren（直接可点的二级菜单项）
const filteredMenus = computed(() =>
  menus
    .map((group) => filterMenuNode(group))
    .filter(Boolean)
    .map((group) => ({
      ...group,
      subMenus: group.children.filter((child) => child.children),
      leafChildren: group.children.filter((child) => !child.children)
    }))
)

const activeMenu = computed(() => {
  const path = route.path
  // 新建、编辑、详情等子路由不在菜单中，归约到对应列表页菜单项保持高亮
  // 群组子路由归约到群组菜单项；必须先于下方 /org/ 归约判断，否则群组页会被误归约、误高亮「当前组织架构」
  if (path.startsWith('/org/group')) return '/org/group'
  if (path.startsWith('/org/')) return '/org'
  if (path.startsWith('/role/')) return '/role'
  // 日程子路由归约到日程菜单项
  if (path.startsWith('/calendar/')) return '/calendar'
  // 附件操作日志为附件管理列表的子页面，归约到附件管理菜单项
  if (path.startsWith('/attOperation/')) return '/att'
  // 日志详情为日志管理列表的子页面，归约到日志管理菜单项
  if (path.startsWith('/log/')) return '/log'
  // 定时任务编辑为定时任务管理列表的子页面，归约到定时任务管理菜单项
  if (path.startsWith('/task/')) return '/task'
  // 执行记录详情为执行记录列表的子页面，归约到执行记录菜单项
  if (path.startsWith('/taskHistory/')) return '/taskHistory'
  // 流程模板新建/编辑/详情为模板管理列表的子页面，归约到模板管理菜单项
  if (path.startsWith('/processTemplate/')) return '/processTemplate'
  // 多语言三个页面均为叶子路径（弹窗编辑无子路由），无需归约
  return path
})

/* ========== 顶部栏：面包屑 / 用户信息 / 登出 ========== */

const breadcrumbs = computed(() => {
  const title = route.meta && route.meta.title
  if (!title) return ['首页']
  const parent = route.meta.parent
  const group = route.meta.group
  return parent ? [parent, ...(group ? [group] : []), title] : [title]
})

const userName = computed(() => (userStore.userInfo && userStore.userInfo.fieldName) || '')
const avatarText = computed(() => (userName.value ? userName.value.charAt(0) : ''))

// 所属部门：登录返回的用户信息不含部门名称，按父级组织 ID 经地址本 load 回填；
// 未挂组织或查询失败时显示 '-'
const deptName = ref('-')
const todayCalendarList = ref([])
const calendarPopoverRef = ref(null)

// 系统应用名称（LOGO 文案）：取后端 application.properties 的 application.name，
// 后端对空配置统一回退默认应用名；接口异常时前端同样回退默认应用名，保证 LOGO 不空白
const applicationName = ref('CommonSystem')

async function loadApplicationName() {
  try {
    const res = await getInitInfo()
    const name = res && res.data && res.data.applicationName
    if (name) applicationName.value = name
  } catch {
    applicationName.value = 'CommonSystem'
  }
}

function formatTime(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  return `${h}:${m}`
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const mo = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${mo}-${day}`
}

/** 判断日程是否为跨天日程（开始与结束不在同一天） */
function isCrossDay(startStr, endStr) {
  if (!startStr || !endStr) return false
  const s = new Date(startStr)
  const e = new Date(endStr)
  return s.getFullYear() !== e.getFullYear() || s.getMonth() !== e.getMonth() || s.getDate() !== e.getDate()
}

/** 顶部日程弹窗时间展示：同天仅显示时分，跨天日程额外显示日期 */
function formatScheduleTime(item) {
  if (!item) return ''
  const start = item.fieldStartTime
  const end = item.fieldEndTime
  if (isCrossDay(start, end)) {
    return `${formatDate(start)} ${formatTime(start)} - ${formatDate(end)} ${formatTime(end)}`
  }
  return `${formatTime(start)} - ${formatTime(end)}`
}

async function handleTodayCalendarShow() {
  try {
    const res = await queryTodayCalendar()
    todayCalendarList.value = res.data || []
  } catch {
    todayCalendarList.value = []
  }
}

function goCalendar() {
  calendarPopoverRef.value?.hide()
  router.push('/calendar')
}

/** 检查是否需要强制修改密码，如果需要则跳转到修改密码页面 */
async function checkForceChangePwd() {
  if (!userStore.isLoggedIn) return
  try {
    const res = await request.get('/backend/sys/org/user/checkPwdChange')
    if (res.metadata && res.metadata.forceChangePwd === true) {
      router.push('/changePwd')
    }
  } catch {
    // 检查失败不阻塞主页渲染
  }
}

onMounted(async () => {
  // 读取应用名称回填 LOGO
  loadApplicationName()
  // 加载顶部栏语言切换下拉选项
  loadLanguageOptions()
  // 检查是否需要强制修改密码
  checkForceChangePwd()
  const parentId = userStore.userInfo && userStore.userInfo.fieldParentId
  if (!parentId) return
  try {
    const res = await loadAddressBook({ body: { ids: [parentId] } })
    const list = res && Array.isArray(res.data) ? res.data : []
    const item = list[0]
    deptName.value = (item && item.fieldName) || '-'
  } catch {
    deptName.value = '-'
  }
})

/** 待办类型文本映射，供 SSE 桌面通知使用 */
const NOTIFY_TYPE_LABEL = { '1': '待办', '2': '待阅' }

// SSE 连接（全局生命周期）：接收新待办实时推送，桌面通知 + 气泡提示 + 更新未读计数
useSse({
  onNewTodo(data) {
    // 1. 桌面通知（需用户已授权）
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification('新' + (NOTIFY_TYPE_LABEL[data.notifyType] || '待办'), {
        body: data.todoName,
        icon: '/favicon.ico'
      })
    }

    // 2. 页面内气泡提示
    ElNotification({
      title: '新' + (NOTIFY_TYPE_LABEL[data.notifyType] || '待办'),
      message: data.todoName,
      type: data.notifyType === '1' ? 'warning' : 'info',
      duration: 5000,
      onClick: () => {
        router.push('/todo')
      }
    })

    // 3. 更新未读计数（红点 + 版本号）
    notifyStore.increment()
  }
})

// 申请桌面通知权限（页面挂载时请求一次）
onMounted(() => {
  if ('Notification' in window && Notification.permission === 'default') {
    Notification.requestPermission()
  }
})

async function handleLogout() {
  try {
    await logout()
  } catch {
    // 登出接口失败不阻塞本地登出，会话 token 一并清理
  }
  userStore.logout()
  clearVisitedTabs()
  ElMessage.success('已退出登录')
  router.replace('/login')
}

/* ========== 路由标签页 ========== */

// 默认标签：第一个可见菜单的第一个叶子菜单项；无可见菜单时回退首页
function firstVisibleLeaf(nodes) {
  for (const node of nodes || []) {
    if (node.children && node.children.length) {
      const hit = firstVisibleLeaf(node.children)
      if (hit) return hit
    } else if (node.path) {
      return node
    }
  }
  return null
}

const defaultTab = computed(() => {
  const first = firstVisibleLeaf(filteredMenus.value)
  return first ? { path: first.path, title: first.title } : { path: '/', title: '首页' }
})

/** 从 sessionStorage 恢复标签页记录（仅保留 serializable 字段） */
function restoreTabs() {
  try {
    const raw = sessionStorage.getItem(TABS_STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed) && parsed.length > 0) return parsed
    }
  } catch {
    // 数据损坏则忽略
  }
  return null
}

/** 保存标签页数据到 sessionStorage */
function persistTabs(tabs) {
  try {
    sessionStorage.setItem(TABS_STORAGE_KEY, JSON.stringify(tabs))
  } catch {
    // sessionStorage 空间不足等异常静默忽略
  }
}

const visitedTabs = ref(restoreTabs() || [])
const activeTab = ref('')

// 路由变化：将当前页面加入标签页并激活；无 meta.title 的路由不建标签。
// 去重键：默认按 path；meta.tab === 'name' 时按路由 name 去重。
// 每次变化后同步保存到 sessionStorage。
watch(
  () => route.path,
  (path) => {
    // 表单页保存/提交成功后标记关闭的标签页，跳转时在此统一移除
    const tabsToClose = consumeTabsToClose()
    if (tabsToClose) {
      visitedTabs.value = visitedTabs.value.filter((tab) => !tabsToClose.includes(tab.path))
      persistTabs(visitedTabs.value)
    }
    const title = route.meta && route.meta.title
    if (!title) {
      activeTab.value = defaultTab.value.path
      if (!visitedTabs.value.some((tab) => tab.path === defaultTab.value.path)) {
        visitedTabs.value.push({ key: defaultTab.value.path, ...defaultTab.value })
      }
      persistTabs(visitedTabs.value)
      return
    }
    const key = route.meta && route.meta.tab === 'name' ? route.name || path : path
    const existing = visitedTabs.value.find((tab) => tab.key === key)
    if (existing) {
      existing.path = path
    } else {
      visitedTabs.value.push({ key, path, title })
    }
    activeTab.value = path
    persistTabs(visitedTabs.value)
  },
  { immediate: true }
)

function handleTabClick(tab) {
  router.push(tab.paneName)
}

/* ========== 页签栏滚轮横向滚动 ========== */

/** el-tabs 组件实例，用于定位内部导航滚动容器 */
const tabsRef = ref(null)

/** 取页签栏内部的横向滚动容器（Element Plus 页签导航） */
function getTabsScrollEl() {
  const root = tabsRef.value && tabsRef.value.$el
  return root ? root.querySelector('.el-tabs__nav-scroll') : null
}

/** 鼠标竖向滚轮时，横向滚动页签栏 */
function handleTabsWheel(event) {
  const scrollEl = getTabsScrollEl()
  if (!scrollEl) return
  const maxScroll = scrollEl.scrollWidth - scrollEl.clientWidth
  if (maxScroll <= 0) return
  event.preventDefault()
  scrollEl.scrollLeft += event.deltaY
}

/** 激活页签变化后，滚动使其完整显示在可视区域内 */
function scrollActiveTabIntoView() {
  const scrollEl = getTabsScrollEl()
  if (!scrollEl) return
  const active = scrollEl.querySelector('.el-tabs__item.is-active')
  if (!active) return
  const box = scrollEl.getBoundingClientRect()
  const item = active.getBoundingClientRect()
  if (item.left < box.left) {
    scrollEl.scrollLeft -= box.left - item.left
  } else if (item.right > box.right) {
    scrollEl.scrollLeft += item.right - box.right
  }
}

watch(activeTab, () => {
  nextTick(scrollActiveTabIntoView)
})

/* ========== KeepAlive 页签缓存 ========== */

/**
 * 取路由末级匹配记录对应的视图组件名（SFC 显式 name 优先，其次 script setup 推断名）。
 * 懒加载组件首次访问前 components.default 是加载函数，取不到名字则不参与缓存；
 * 首次访问后 vue-router 会用解析结果回填路由记录，后续即可正常匹配。
 */
function getViewComponentName(resolved) {
  const records = resolved.matched || []
  const comp = records.length ? records[records.length - 1].components.default : null
  if (!comp || typeof comp === 'function') return null
  return comp.name || comp.__name || null
}

/**
 * 当前打开标签页对应的视图组件名集合，作为 KeepAlive 的 include：
 * - 切换标签页时页面组件被缓存，返回时恢复原状态（不重新挂载）；
 * - 关闭标签页后集合自动移除对应组件名，缓存随之释放，
 *   再次进入（如保存后重开新建页）即为全新页面。
 */
const cachedViewNames = computed(() => {
  const names = new Set()
  // 当前路由自身一定已解析完成，加入依赖以在每次导航后重新计算
  const currentName = getViewComponentName(route)
  if (currentName) names.add(currentName)
  for (const tab of visitedTabs.value) {
    try {
      const name = getViewComponentName(router.resolve(tab.path))
      if (name) names.add(name)
    } catch {
      // 标签 path 无法解析（如异常数据）时跳过该标签
    }
  }
  return [...names]
})

function handleTabRemove(name) {
  // 禁止关闭首页标签
  if (name === defaultTab.value.path) return
  const index = visitedTabs.value.findIndex((tab) => tab.path === name)
  if (index === -1) return
  visitedTabs.value.splice(index, 1)
  if (name === activeTab.value) {
    if (!visitedTabs.value.length) {
      visitedTabs.value.push({ key: defaultTab.value.path, ...defaultTab.value })
    }
    const next = visitedTabs.value[index] || visitedTabs.value[index - 1]
    router.push(next ? next.path : defaultTab.value.path)
  }
  persistTabs(visitedTabs.value)
}

/**
 * 标签管理下拉菜单命令处理
 */
function handleTabMgmtCommand(command) {
  if (command === 'closeOthers') {
    const current = visitedTabs.value.find((t) => t.path === activeTab.value)
    // 保留当前标签和默认首页（如果存在）
    const homeTab = visitedTabs.value.find((t) => t.path === defaultTab.value.path)
    visitedTabs.value = current
      ? [current, ...(homeTab && homeTab.path !== current.path ? [homeTab] : [])]
      : (homeTab ? [homeTab] : [])
    persistTabs(visitedTabs.value)
    // 如当前标签不存在则跳转首页
    if (!current) {
      router.push(defaultTab.value.path)
    }
  } else if (command === 'closeAll') {
    // 关闭全部标签，仅保留首页
    visitedTabs.value = [{ key: defaultTab.value.path, ...defaultTab.value }]
    activeTab.value = defaultTab.value.path
    router.push(defaultTab.value.path)
    persistTabs(visitedTabs.value)
  } else {
    // 切换标签路由
    router.push(command)
  }
}
</script>

<style scoped>
/* —— 整体框架：顶部菜单栏 → 横向菜单栏 → 路由标签页 → 主体（纵向排列） —— */
.home-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* —— 顶部菜单栏：LOGO（左）+ 路由面包屑 + 用户信息/登出（右） —— */
.layout-header {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background-color: #ffffff;
  border-bottom: 1px solid #e4e7ed;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
  min-width: 0;
}

.layout-logo {
  /* 应用名（application.name）长度不定：限制最大宽度，超宽裁剪，完整名称经 title 提示 */
  max-width: 260px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 4px;
  color: #002140;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* —— 横向菜单栏：一级菜单横向排列，点击标题展开次级菜单 —— */
.layout-nav {
  flex-shrink: 0;
  background-color: #ffffff;
  /* 横向菜单高度默认 60px 偏高，压缩后为主内容区让出纵向空间 */
  --el-menu-horizontal-height: 46px;
}

.layout-menu {
  padding: 0 8px;
}

/* 已展开的一级菜单标题同步高亮，便于识别当前展开的分组 */
.layout-nav :deep(.el-menu--horizontal > .el-sub-menu.is-opened > .el-sub-menu__title) {
  color: var(--el-color-primary);
  border-bottom: 2px solid var(--el-color-primary);
}

.header-user-box {
  display: flex;
  align-items: center;
  gap: 10px;
}

.notify-badge {
  margin-right: 4px;
}

.notify-bell-btn {
  padding: 4px 6px;
  min-height: 28px;
  color: #606266;
}

.notify-bell-btn:hover {
  color: #409eff;
  background-color: #ecf5ff;
}

.header-avatar {
  background-color: #409eff;
  color: #ffffff;
  font-size: 13px;
}

.header-username {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.header-dept {
  font-size: 13px;
  color: #909399;
}

.layout-tabs {
  flex-shrink: 0;
  padding: 8px 8px 0;
  background-color: #f5f7fa;
  display: flex;
  align-items: flex-start;
}

.layout-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
  flex: 1;
  min-width: 0;
}

.tab-mgmt-dropdown {
  flex-shrink: 0;
  margin-left: 4px;
  margin-top: 2px;
}

.tab-mgmt-btn {
  padding: 4px 6px;
  min-height: 28px;
}

.tab-mgmt-btn:hover {
  background-color: #e4e7ed;
}

/* 页签栏占满剩余宽度，标签管理功能按钮固定在最右侧 */
.layout-tabs :deep(.el-tabs) {
  flex: 1;
  min-width: 0;
}

/* 页签超出宽度时支持鼠标滚轮横向滚动 */
.layout-tabs :deep(.el-tabs__nav-scroll) {
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.layout-tabs :deep(.el-tabs__nav-scroll::-webkit-scrollbar) {
  display: none;
}

/* 关闭 Element Plus 自身基于 transform 的偏移，改用原生滚动容器滚动 */
.layout-tabs :deep(.el-tabs__nav) {
  transform: none !important;
  transition: none;
}

/* 隐藏 Element Plus 自带的左右滚动箭头（改用滚轮 / 滚动容器） */
.layout-tabs :deep(.el-tabs__nav-prev),
.layout-tabs :deep(.el-tabs__nav-next) {
  display: none;
}

/* 关闭 is-scrollable 状态下为箭头预留的左右内边距 */
.layout-tabs :deep(.el-tabs__nav-wrap.is-scrollable) {
  padding: 0;
}

.layout-main {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background-color: #f0f2f5;
}

/* —— 日程图标按钮 —— */
.calendar-btn {
  padding: 4px 6px;
  min-height: 28px;
  color: #606266;
}

.calendar-btn:hover {
  color: #409eff;
  background-color: #ecf5ff;
}

/* —— 群组图标按钮：与铃铛/日程按钮同款外观 —— */
.group-btn {
  padding: 4px 6px;
  min-height: 28px;
  color: #606266;
}

.group-btn:hover {
  color: #409eff;
  background-color: #ecf5ff;
}

/* —— 语言切换下拉：与铃铛/日程/群组按钮同款外观，附加当前语言名 —— */
.language-dropdown {
  margin: 0 4px;
}

.language-btn {
  padding: 4px 6px;
  min-height: 28px;
  color: #606266;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.language-btn:hover {
  color: #409eff;
  background-color: #ecf5ff;
}

.language-btn-text {
  font-size: 13px;
  max-width: 72px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* —— 日程弹出框 —— */
.calendar-popover-content {
  max-height: 260px;
  overflow-y: auto;
}

.calendar-popover-empty {
  text-align: center;
  color: #909399;
  padding: 16px 0;
  font-size: 13px;
}

.calendar-popover-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid #f0f0f0;
}

.calendar-popover-item:last-child {
  border-bottom: none;
}

.calendar-popover-time {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  min-width: 80px;
}

.calendar-popover-title {
  flex: 1;
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.calendar-popover-footer {
  text-align: center;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
}
</style>
