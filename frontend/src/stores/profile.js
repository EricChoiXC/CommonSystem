import { defineStore } from 'pinia'
import { ref } from 'vue'
import { viewAttachment } from '@/api/attachment'

/**
 * 顶栏头像状态 Store(Pinia 轻量 store,会话级缓存、不做持久化)
 *
 * 用途:管理当前登录用户的头像附件 id 与本地 blob URL,avatarUrl 直接绑定 <img :src>;
 * avatarUrl 为 null 时回退为账号首字符头像(由消费方自行兜底)。
 *
 * 消费方:
 * - 顶栏 HomeView.vue:挂载时调用 applyAvatarAttId 加载头像,登出时调用 reset 清理;
 * - 资料页 ProfileView.vue:上传/移除头像成功后调用 applyAvatarAttId 统一刷新。
 *
 * 来源:任务 PF4-FE-001;对应《docs/v1_2-用户管理中心/个人资料页与第三方账号绑定》
 * 开发方案 4.5(头像展示与刷新)与 7.1 假设 A2——头像刷新机制选型 Pinia 轻量 store
 * (工程内无事件总线先例,不引入 mitt,不新增全局事件机制)。
 *
 * 风格选型:采用 setup store(与 stores/notify.js 一致)。头像状态涉及 blob URL 的
 * 创建/释放副作用,setup 风格可在闭包内定义私有清理函数 clearAvatar(不暴露为公共
 * action),副作用辅助逻辑不泄漏到 store 外;且本 store 与 notify.js 同为"全局布局
 * 消费的轻量跨页状态",保持同类 store 风格统一。登出必须调用 reset 释放 blob URL
 * (方案 6.5 R5 口径:会话级缓存,登出失效)。
 */
export const useProfileStore = defineStore('profile', () => {
  /** 当前头像附件 id(String|null):null 表示无头像 */
  const avatarAttId = ref(null)

  /** 本地 blob URL(String|null):URL.createObjectURL 产生,直接绑定 <img :src> */
  const avatarUrl = ref(null)

  /**
   * 释放当前 blob URL 并清空 avatarUrl(revoke 后的 URL 已失效,须同步置 null)
   */
  function clearAvatar() {
    if (avatarUrl.value) {
      URL.revokeObjectURL(avatarUrl.value)
      avatarUrl.value = null
    }
  }

  /**
   * 头像统一入口:顶栏挂载与资料页上传/移除头像后均调用本方法
   *
   * @param {string|null} attId 头像附件 id
   *   - 为空(undefined/null/空串):移除头像,revoke 旧 URL,两 state 置 null,回退首字符头像;
   *   - 非空:记录 avatarAttId 并经 viewAttachment 取 Blob,revoke 旧 URL 后以
   *     URL.createObjectURL 生成新 blob URL 写入 avatarUrl。
   *   - 请求失败静默降级(console.warn,不抛出):两 state 置 null,不影响主界面。
   */
  function applyAvatarAttId(attId) {
    if (!attId) {
      avatarAttId.value = null
      clearAvatar()
      return
    }
    avatarAttId.value = attId
    viewAttachment(attId)
      .then((res) => {
        // 响应返回时头像已被再次上传/移除:丢弃过期响应,避免 revoke 掉新一轮刚创建的 URL
        if (avatarAttId.value !== attId) return
        clearAvatar()
        avatarUrl.value = URL.createObjectURL(res.data)
      })
      .catch((err) => {
        console.warn('[profile] 头像加载失败,已回退默认头像:', attId, err)
        if (avatarAttId.value !== attId) return
        avatarAttId.value = null
        clearAvatar()
      })
  }

  /**
   * 登出清理:revoke blob URL 并清空头像状态(会话级缓存,登出失效,方案 6.5 R5 口径)
   */
  function reset() {
    avatarAttId.value = null
    clearAvatar()
  }

  return { avatarAttId, avatarUrl, applyAvatarAttId, reset }
})
