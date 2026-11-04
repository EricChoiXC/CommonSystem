import { watch } from 'vue'

/**
 * 表单页路由 query 监听工具。
 *
 * 静态新建路由（如 /order/create）的页签去重键与 KeepAlive 实例 key 均为路由 path
 * （见 views/HomeView.vue，query 不参与 key），二次携带 query 进入同一新建页时
 * 组件实例被复用、onMounted 不会重跑，列表页/详情页经 query 带入的信息会丢失。
 *
 * 该工具监听路由 query 变化并回调 apply；实例与 path 一一绑定，仅在当前路由仍为
 * 本页路径时回调，避免被 KeepAlive 缓存的其他页面实例被无关路由的 query 误触发；
 * query 未携带所需信息时（如切回已打开页签）由各表单页在 apply 内自行跳过，
 * 保留用户已填内容。
 */
export function watchRouteQuery(route, apply) {
  const selfPath = route.path
  watch(
    () => route.query,
    (query) => {
      if (route.path !== selfPath) return
      apply(query || {})
    }
  )
}
