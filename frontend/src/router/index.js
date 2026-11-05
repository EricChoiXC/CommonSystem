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
        meta: { title: '日程', parent: '工作台' }
      },
      {
        path: 'calendar/create',
        name: 'CalendarCreate',
        component: () => import('@/views/calendar/CalendarFormView.vue'),
        meta: { title: '新建日程', parent: '工作台' }
      },
      {
        path: 'calendar/edit/:id',
        name: 'CalendarEdit',
        component: () => import('@/views/calendar/CalendarFormView.vue'),
        meta: { title: '编辑日程', parent: '工作台', tab: 'name' }
      },
      {
        path: 'calendar/detail/:id',
        name: 'CalendarDetail',
        component: () => import('@/views/calendar/CalendarDetailView.vue'),
        meta: { title: '日程详情', parent: '工作台', tab: 'name' }
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
        meta: { title: '群组', parent: '组织架构' }
      },
      {
        path: 'org/group/create',
        name: 'GroupCreate',
        component: () => import('@/views/org/GroupFormView.vue'),
        meta: { title: '新建群组', parent: '组织架构' }
      },
      {
        path: 'org/group/edit/:id',
        name: 'GroupEdit',
        component: () => import('@/views/org/GroupFormView.vue'),
        meta: { title: '编辑群组', parent: '组织架构', tab: 'name' }
      },
      {
        path: 'org/group/detail/:id',
        name: 'GroupDetail',
        component: () => import('@/views/org/GroupDetailView.vue'),
        meta: { title: '群组详情', parent: '组织架构', tab: 'name' }
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
