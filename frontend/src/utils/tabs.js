// 标签页跨组件协同工具
//
// 标签页状态由 HomeView 内部维护，普通页面无法直接操作标签栏。
// 新建/编辑页在保存、提交成功后需要关闭自身标签：
// 先用 markTabToClose 按当前路由 path 标记，再跳转回列表页；
// HomeView 在路由变化时统一取出标记并移除对应标签。

const pendingClosePaths = new Set()

/** 标记需要关闭的标签页（按标签页当前 path 匹配） */
export function markTabToClose(path) {
  if (path) {
    pendingClosePaths.add(path)
  }
}

/** 取出并清空所有待关闭的标签页 path，无标记时返回 null */
export function consumeTabsToClose() {
  if (!pendingClosePaths.size) {
    return null
  }
  const paths = [...pendingClosePaths]
  pendingClosePaths.clear()
  return paths
}
