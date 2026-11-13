import { defineStore } from 'pinia'
import { listModules } from '@/api/common'

/**
 * 模块组状态 store
 *
 * 对应 docs/v1_1-common/05-增加接口开关/接口开关机制UI设计分册.md 第 1、6 节：
 * 登录后应用初始化时调用 loadModules() 拉取模块组列表并缓存在内存中，
 * 供菜单过滤、路由空态与模块管理页三方共用单一数据源。
 * isDisabled 采用 fail-open 语义：未成功加载、列表为空或 key 不存在时一律按开启放行，
 * 避免接口偶发失败导致全站功能被误关；仅明确 fieldEnable === false 时才判定关闭。
 * 登出时由布局层调用 reset() 清空缓存，避免下一个用户看到上一个用户的模块状态。
 */
export const useModuleStore = defineStore('module', {
  state: () => ({
    modules: [],
    loaded: false
  }),
  getters: {
    /**
     * 按模块组 key（fieldKey）查询是否已关闭
     * fail-open：未加载 / 空列表 / key 不存在均返回 false（按开启放行）
     */
    isDisabled: (state) => (key) => {
      if (!state.loaded || !state.modules || state.modules.length === 0 || !key) {
        return false
      }
      const hit = state.modules.find((item) => item.fieldKey === key)
      if (!hit) {
        return false
      }
      return hit.fieldEnable === false
    },
    /**
     * 按模块组 key 返回中文名称（fieldName），查不到时回退 key 本身，
     * 供「模块未开启」空态展示用
     */
    moduleName: (state) => (key) => {
      if (!key) {
        return key
      }
      const hit = state.modules.find((item) => item.fieldKey === key)
      return hit ? hit.fieldName : key
    }
  },
  actions: {
    /**
     * 拉取模块组列表并缓存；异常向上抛出，由调用方决定提示方式，store 内不弹消息、不吞异常
     */
    async loadModules() {
      const res = await listModules()
      this.modules = res.data || []
      this.loaded = true
    },
    /**
     * 按 fieldId 更新本地模块组启停状态（供管理页在启停接口成功后调用，
     * 保持单例数据源同步，让菜单过滤即时生效，无需重新拉取列表）
     */
    applyEnable(fieldId, fieldEnable) {
      const hit = this.modules.find((item) => item.fieldId === fieldId)
      if (hit) {
        hit.fieldEnable = fieldEnable
      }
    },
    /**
     * 清空模块组缓存（登出时由布局层调用）
     */
    reset() {
      this.modules = []
      this.loaded = false
    }
  }
})
