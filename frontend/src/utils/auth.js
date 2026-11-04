import { useUserStore } from '@/stores/user'

/** 超级管理员权限码：用户拥有该权限时默认放行所有权限判断 */
const SUPER_ADMIN_AUTH = 'SYS_ADMIN'

/**
 * 前端权限计算公式：判断当前用户是否拥有要求的权限。
 *
 * - 权限集合取自当前登录用户信息中的 `auths` 字段
 *   （登录时后端随用户信息返回，前端存储在 localStorage 键 `common_user` 中，
 *   由 `stores/user.js` 统一维护，登录/登出时自动写入与清理）
 * - `requiredAuth` 支持传入单个权限码字符串，或权限码数组；
 *   数组按「任一命中即通过（OR）」规则判断
 * - 用户权限集合包含 `SYS_ADMIN` 时默认返回 `true`
 *
 * @param {string|string[]} [requiredAuth] 要求的权限码，单个或数组
 * @returns {boolean} 是否通过权限判断
 * @example
 * hasAuth('SYS_ATT_MAIN')          // 是否拥有附件后台管理权限
 * hasAuth(['SYS_ORG_ROLE_ADMIN', 'SYS_ORG_ELEMENT_ADD']) // 拥有任一即可
 */
export function hasAuth(requiredAuth) {
  const userStore = useUserStore()
  const auths =
    userStore.userInfo && Array.isArray(userStore.userInfo.auths)
      ? userStore.userInfo.auths
      : []

  // 超级管理员默认放行
  if (auths.includes(SUPER_ADMIN_AUTH)) {
    return true
  }

  // 未传要求权限：无明确放行依据，返回 false
  if (requiredAuth == null || requiredAuth === '') {
    return false
  }

  const requiredList = Array.isArray(requiredAuth) ? requiredAuth : [requiredAuth]
  return requiredList.some((auth) => auths.includes(auth))
}

export default hasAuth
