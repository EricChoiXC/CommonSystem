import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { hasAuth } from '@/utils/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue')
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/HomeView.vue'),
    // 布局壳默认进入第一个可访问菜单
    redirect: '/todo',
    children: [
      {
        path: 'calendar',
        name: 'Calendar',
        component: () => import('@/views/calendar/CalendarView.vue'),
        // meta.moduleGroup 为模块组开关运行期感知：所属模块组已关闭时布局层渲染「模块未开启」空态（UI 分册第 7 节）
        meta: { title: '日程', parent: '工作台', moduleGroup: 'calendar' }
      },
      {
        path: 'calendar/create',
        name: 'CalendarCreate',
        component: () => import('@/views/calendar/CalendarFormView.vue'),
        meta: { title: '新建日程', parent: '工作台', moduleGroup: 'calendar' }
      },
      {
        path: 'calendar/edit/:id',
        name: 'CalendarEdit',
        component: () => import('@/views/calendar/CalendarFormView.vue'),
        meta: { title: '编辑日程', parent: '工作台', tab: 'name', moduleGroup: 'calendar' }
      },
      {
        path: 'calendar/detail/:id',
        name: 'CalendarDetail',
        component: () => import('@/views/calendar/CalendarDetailView.vue'),
        meta: { title: '日程详情', parent: '工作台', tab: 'name', moduleGroup: 'calendar' }
      },
      {
        path: 'todo',
        name: 'MyTodo',
        component: () => import('@/views/notify/MyTodoView.vue'),
        meta: { title: '待办', parent: '工作台' }
      },
      {
        path: 'org',
        name: 'OrgManage',
        component: () => import('@/views/org/OrgManageView.vue'),
        meta: { title: '当前组织架构', parent: '组织架构' }
      },
      {
        path: 'org/create',
        name: 'OrgCreate',
        component: () => import('@/views/org/OrgFormView.vue'),
        meta: { title: '新建组织架构', parent: '组织架构' }
      },
      {
        path: 'org/edit/:id',
        name: 'OrgEdit',
        component: () => import('@/views/org/OrgFormView.vue'),
        meta: { title: '编辑组织架构', parent: '组织架构', tab: 'name' }
      },
      {
        path: 'org/detail/:id',
        name: 'OrgDetail',
        component: () => import('@/views/org/OrgDetailView.vue'),
        meta: { title: '组织架构详情', parent: '组织架构', tab: 'name' }
      },
      {
        path: 'org/group',
        name: 'GroupManage',
        component: () => import('@/views/org/GroupManageView.vue'),
        // meta.moduleGroup 为模块组开关运行期感知：所属模块组已关闭时布局层渲染「模块未开启」空态（UI 分册第 7 节）
        meta: { title: '群组', parent: '组织架构', moduleGroup: 'org-group' }
      },
      {
        path: 'org/group/create',
        name: 'GroupCreate',
        component: () => import('@/views/org/GroupFormView.vue'),
        meta: { title: '新建群组', parent: '组织架构', moduleGroup: 'org-group' }
      },
      {
        path: 'org/group/edit/:id',
        name: 'GroupEdit',
        component: () => import('@/views/org/GroupFormView.vue'),
        meta: { title: '编辑群组', parent: '组织架构', tab: 'name', moduleGroup: 'org-group' }
      },
      {
        path: 'org/group/detail/:id',
        name: 'GroupDetail',
        component: () => import('@/views/org/GroupDetailView.vue'),
        meta: { title: '群组详情', parent: '组织架构', tab: 'name', moduleGroup: 'org-group' }
      },
      {
        path: 'profile',
        name: 'OrgProfile',
        component: () => import('@/views/org/ProfileView.vue'),
        // 本人自助页面（PF4-FE-002）：登录即可访问，无菜单归属（方案 4.5），
        // 不设 parent（面包屑仅「个人资料」）与 auth（仅登录校验）；
        // tab: 'name' 为工程通用写法，标签去重键取路由 name。
        meta: { title: '个人资料', tab: 'name' }
      },
      {
        path: 'role',
        name: 'RoleManage',
        component: () => import('@/views/org/RoleManageView.vue'),
        meta: { title: '角色管理', parent: '系统管理', group: '权限管理', auth: ['SYS_ORG_ROLE_ADMIN', 'SYS_ORG_ADMIN'] }
      },
      {
        path: 'role/create',
        name: 'RoleCreate',
        component: () => import('@/views/org/RoleFormView.vue'),
        meta: { title: '新建角色', parent: '系统管理', group: '权限管理', auth: ['SYS_ORG_ROLE_ADMIN', 'SYS_ORG_ADMIN'] }
      },
      {
        path: 'role/edit/:id',
        name: 'RoleEdit',
        component: () => import('@/views/org/RoleFormView.vue'),
        meta: { title: '编辑角色', parent: '系统管理', group: '权限管理', auth: ['SYS_ORG_ROLE_ADMIN', 'SYS_ORG_ADMIN'], tab: 'name' }
      },
      {
        path: 'rolePreview',
        name: 'RolePreview',
        component: () => import('@/views/org/RolePreviewView.vue'),
        meta: { title: '权限预览', parent: '系统管理', group: '权限管理', auth: ['SYS_ORG_ROLE_ADMIN', 'SYS_ORG_ADMIN'] }
      },
      {
        path: 'att',
        name: 'AttManage',
        component: () => import('@/views/att/AttManageView.vue'),
        meta: { title: '附件管理', parent: '系统管理', group: '附件管理', auth: 'SYS_ATT_MAIN' }
      },
      {
        path: 'attFile',
        name: 'AttFileManage',
        component: () => import('@/views/att/AttFileManageView.vue'),
        meta: { title: '文件管理', parent: '系统管理', group: '附件管理', auth: 'SYS_ATT_MAIN' }
      },
      {
        path: 'attOperation/:id',
        name: 'AttOperation',
        component: () => import('@/views/att/AttOperationView.vue'),
        meta: { title: '附件操作日志', parent: '系统管理', group: '附件管理', auth: 'SYS_ATT_MAIN', tab: 'name' }
      },
      {
        path: 'notify',
        name: 'NotifyManage',
        component: () => import('@/views/notify/TodoManageView.vue'),
        meta: { title: '待办管理', parent: '系统管理', group: '通知待办', auth: 'SYS_NOTIFY_ADMIN' }
      },
      {
        path: 'notifySend',
        name: 'NotifySend',
        component: () => import('@/views/notify/TodoSendView.vue'),
        meta: { title: '发送待办', parent: '系统管理', group: '通知待办', auth: 'SYS_NOTIFY_ADMIN' }
      },
      {
        path: 'log',
        name: 'LogManage',
        component: () => import('@/views/log/LogManageView.vue'),
        meta: { title: '日志管理', parent: '系统管理', group: '日志管理', auth: 'SYS_LOG_ADMIN' }
      },
      {
        path: 'dict',
        name: 'DictManage',
        component: () => import('@/views/dict/DictManageView.vue'),
        meta: { title: '码值管理', parent: '系统管理' }
      },
      {
        path: 'setting',
        name: 'CommonSetting',
        component: () => import('@/views/setting/CommonSettingView.vue'),
        meta: { title: '公共配置', parent: '系统管理', group: '后台管理', auth: 'SYS_ADMIN' }
      },
      {
        path: 'module',
        name: 'ModuleManage',
        component: () => import('@/views/setting/ModuleManageView.vue'),
        // 顶层扁平路径 /module（与 /setting、/dict 同惯例，决策 D4）；模块组管理员维护模块组开关
        meta: { title: '模块管理', parent: '系统管理', group: '后台管理', auth: 'SYS_MODULE_ADMIN' }
      },
      {
        path: 'log/detail/:id',
        name: 'LogDetail',
        component: () => import('@/views/log/LogDetailView.vue'),
        meta: { title: '日志详情', parent: '系统管理', group: '日志管理', auth: 'SYS_LOG_ADMIN', tab: 'name' }
      },
      {
        path: 'task',
        name: 'TaskManage',
        component: () => import('@/views/task/TaskManageView.vue'),
        meta: { title: '定时任务管理', parent: '系统管理', group: '定时任务', auth: 'SYS_TASK_ADMIN' }
      },
      {
        path: 'task/edit/:id',
        name: 'TaskEdit',
        component: () => import('@/views/task/TaskFormView.vue'),
        meta: { title: '编辑定时任务', parent: '系统管理', group: '定时任务', auth: 'SYS_TASK_ADMIN', tab: 'name' }
      },
      {
        path: 'taskHistory',
        name: 'TaskHistory',
        component: () => import('@/views/task/TaskHistoryView.vue'),
        meta: { title: '执行记录', parent: '系统管理', group: '定时任务', auth: 'SYS_TASK_ADMIN' }
      },
      {
        path: 'taskHistory/detail/:id',
        name: 'TaskHistoryDetail',
        component: () => import('@/views/task/TaskHistoryDetailView.vue'),
        meta: { title: '执行记录详情', parent: '系统管理', group: '定时任务', auth: 'SYS_TASK_ADMIN', tab: 'name' }
      },
      {
        // 编号管理列表页（NB4-FE-005）：菜单挂【后台管理】-【编号机制】三级分组下，
        // group 取二级分组名渲染面包屑「系统管理 / 编号机制 / 编号管理」（对齐流程管理先例）
        path: 'number',
        name: 'NumberManage',
        component: () => import('@/views/number/NumberManageView.vue'),
        meta: { title: '编号管理', parent: '系统管理', group: '编号机制', auth: ['SYS_NUMBER_ADMIN', 'SYS_NUMBER_RULE_EDITOR'] }
      },
      {
        // 编号新建页（NB4-FE-005）：与编辑共用 NumberFormView，按路由 path 区分模式
        path: 'number/create',
        name: 'NumberCreate',
        component: () => import('@/views/number/NumberFormView.vue'),
        meta: { title: '新增编号规则', parent: '系统管理', group: '编号机制', auth: ['SYS_NUMBER_ADMIN', 'SYS_NUMBER_RULE_EDITOR'] }
      },
      {
        // 编号编辑页（NB4-FE-005）：tab: 'name' 复用单标签（对齐 org/task edit 先例）
        path: 'number/edit/:id',
        name: 'NumberEdit',
        component: () => import('@/views/number/NumberFormView.vue'),
        meta: { title: '编辑编号规则', parent: '系统管理', group: '编号机制', auth: ['SYS_NUMBER_ADMIN', 'SYS_NUMBER_RULE_EDITOR'], tab: 'name' }
      },
      {
        path: 'processSetting',
        name: 'ProcessSetting',
        component: () => import('@/views/process/ProcessSettingView.vue'),
        meta: { title: '公共配置', parent: '系统管理', group: '流程管理', auth: ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_SETTING_ADMIN'] }
      },
      {
        path: 'processTemplate',
        name: 'ProcessTemplate',
        component: () => import('@/views/process/TemplateManageView.vue'),
        meta: { title: '模板管理', parent: '系统管理', group: '流程管理', auth: ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_TEMPLATE_ADMIN', 'SYS_PROCESS_TEMPLATE_VIEWER'] }
      },
      {
        path: 'processTemplate/create',
        name: 'ProcessTemplateCreate',
        component: () => import('@/views/process/TemplateFormView.vue'),
        meta: { title: '新建流程模板', parent: '系统管理', group: '流程管理', auth: ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_TEMPLATE_ADMIN'] }
      },
      {
        path: 'processTemplate/edit/:id',
        name: 'ProcessTemplateEdit',
        component: () => import('@/views/process/TemplateFormView.vue'),
        meta: { title: '编辑流程模板', parent: '系统管理', group: '流程管理', auth: ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_TEMPLATE_ADMIN'], tab: 'name' }
      },
      {
        path: 'processTemplate/detail/:id',
        name: 'ProcessTemplateDetail',
        component: () => import('@/views/process/TemplateFormView.vue'),
        meta: { title: '流程模板详情', parent: '系统管理', group: '流程管理', auth: ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_TEMPLATE_ADMIN', 'SYS_PROCESS_TEMPLATE_VIEWER'], tab: 'name' }
      },
      {
        path: 'processExample',
        name: 'ProcessExample',
        component: () => import('@/views/process/ExampleManageView.vue'),
        meta: { title: '流程实例', parent: '系统管理', group: '流程管理', auth: ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_EXAMPLE_ADMIN', 'SYS_PROCESS_EXAMPLE_VIEWER'] }
      },
      {
        path: 'processExample/detail/:id',
        name: 'ProcessExampleDetail',
        component: () => import('@/views/process/ExampleDetailView.vue'),
        meta: { title: '流程实例详情', parent: '系统管理', group: '流程管理', auth: ['SYS_PROCESS_ADMIN', 'SYS_PROCESS_EXAMPLE_ADMIN', 'SYS_PROCESS_EXAMPLE_VIEWER'], tab: 'name' }
      },
      {
        path: 'i18nLanguage',
        name: 'I18nLanguageManage',
        component: () => import('@/views/i18n/I18nLanguageManageView.vue'),
        meta: { title: '语言管理', parent: '系统管理', group: '多语言管理', auth: 'SYS_I18N_ADMIN' }
      },
      {
        path: 'i18nTranslation',
        name: 'I18nTranslationManage',
        component: () => import('@/views/i18n/I18nTranslationManageView.vue'),
        meta: { title: '翻译维护', parent: '系统管理', group: '多语言管理', auth: 'SYS_I18N_ADMIN' }
      },
      {
        path: 'i18nDeploy',
        name: 'I18nDeploy',
        component: () => import('@/views/i18n/I18nDeployView.vue'),
        meta: { title: '翻译部署', parent: '系统管理', group: '多语言管理', auth: 'SYS_I18N_ADMIN' }
      },
    ]
  },
  {
    path: '/changePwd',
    name: 'ForceChangePwd',
    component: () => import('@/views/ForceChangePwdView.vue')
  },
  {
    path: '/ui',
    name: 'Ui',
    component: () => import('@/views/ui.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：登录态校验 + 页面级权限校验
// - 未登录访问业务页 → 重定向登录页；已登录访问登录页 → 重定向首页
// - 访问声明了 meta.auth 的页面时，无对应权限 → 重定向首页（首页菜单已按权限过滤）
// - /changePwd 需要登录态，但不经过 meta.auth 权限校验
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.path !== '/login' && !userStore.isLoggedIn) {
    return { path: '/login' }
  }
  if (to.path === '/login' && userStore.isLoggedIn) {
    return { path: '/' }
  }
  if (to.meta && to.meta.auth && !hasAuth(to.meta.auth)) {
    return { path: '/' }
  }
  return true
})

export default router
