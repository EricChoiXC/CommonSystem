/**
 * SSE 连接管理 composable
 *
 * 管理前端 EventSource 连接，自动连接/断线重连/页面卸载自动清理。
 * 由于 EventSource 不支持自定义请求头，登录凭证通过 URL query 参数传递。
 * 收到服务端推送的新待办事件后，触发回调通知页面组件。
 *
 * @param {Object} options
 * @param {Function} [options.onNewTodo] - 收到新待办事件时的回调，接收 { todoId, todoName, notifyType, modelName, modelId, url, receiveTime }
 * @param {Function} [options.onConnected] - 连接建立时的回调
 * @returns {{ connected: import('vue').Ref<boolean> }}
 */
import { onMounted, onUnmounted, ref } from 'vue'

export function useSse({ onNewTodo, onConnected } = {}) {
  const connected = ref(false)
  let eventSource = null

  function connect() {
    const token = localStorage.getItem('common_token')
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

    eventSource.addEventListener('connected', () => {
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
      // 心跳事件，无需处理
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
    connected.value = false
  }

  onMounted(connect)
  onUnmounted(disconnect)

  return { connected }
}