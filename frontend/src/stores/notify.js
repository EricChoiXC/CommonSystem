import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 通知待办状态 Store
 *
 * 管理待办未读计数与 SSE 事件版本号，供全局布局（HomeView.vue）更新未读计数、
 * 供 MyTodoView.vue 监听版本号变化自动刷新列表。
 */
export const useNotifyStore = defineStore('notify', () => {
  /** 未读计数（红点显示） */
  const unreadCount = ref(0)

  /** 版本号：每次收到新待办推送时递增，供组件 watch 触发刷新 */
  const version = ref(0)

  /**
   * 未读计数 +1，版本号 +1
   */
  function increment() {
    unreadCount.value++
    version.value++
  }

  /**
   * 设置未读计数（用于从后端加载初始值）
   */
  function setCount(n) {
    unreadCount.value = n
  }

  /**
   * 重置未读计数（用户进入待办页面时调用）
   */
  function reset() {
    unreadCount.value = 0
  }

  return { unreadCount, version, increment, setCount, reset }
})