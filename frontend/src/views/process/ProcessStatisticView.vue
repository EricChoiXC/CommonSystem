<template>
  <div class="page-card">
    <el-card shadow="never">
      <!-- 筛选区/指标卡/聚合表由共用统计面板承载（mode=admin），页面仅负责路由与菜单语义 -->
      <ProcessStatisticPanel mode="admin" />
    </el-card>
  </div>
</template>

<script setup>
import ProcessStatisticPanel from '@/components/process/ProcessStatisticPanel.vue'

/**
 * 流程统计页（管理员，v1_7 P17-FE-009，路由 /process/statistic）：
 * 挂载共用统计面板 ProcessStatisticPanel（mode=admin），呈现「筛选区 → 指标卡 → 聚合表」
 * 统计布局（非列表页三段式，UI 分册 §10.1 冻结稿）；菜单挂【系统管理 → 流程管理 → 流程统计】，
 * 准入权限 SYS_PROCESS_ADMIN / SYS_PROCESS_EXAMPLE_ADMIN（路由守卫 + 菜单显隐，后端兜底）。
 * 依据 docs/设计文档/sys/process/流程v1_7前端UI设计分册.md §10.1。
 */
</script>

<script>
export default { name: 'ProcessStatisticView' }
</script>

<style scoped>
.page-card {
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
}
</style>
