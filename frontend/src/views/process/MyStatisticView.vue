<template>
  <div class="page-card">
    <el-card shadow="never">
      <!-- 五项指标卡由共用统计面板承载（mode=my，无筛选区），页面仅负责路由与菜单语义 -->
      <ProcessStatisticPanel mode="my" />
    </el-card>
  </div>
</template>

<script setup>
import ProcessStatisticPanel from '@/components/process/ProcessStatisticPanel.vue'

/**
 * 我的流程统计页（v1_7 P17-FE-009，路由 /approval/statistic）：
 * 挂载共用统计面板 ProcessStatisticPanel（mode=my），呈现五项指标卡
 * （今日发起/我的未办结/待审/今日已审/平均耗时，UI 分册 §10.2 冻结稿，无筛选区）；
 * 菜单挂【审批 → 我的流程统计】，全员可见（SYS_PROCESS_MY_VIEWER 默认全员授予，
 * SYS_ADMIN 经 hasAuth 超管短路可见；统计对象由后端会话定位，防越权）。
 * 依据 docs/设计文档/sys/process/流程v1_7前端UI设计分册.md §10.2。
 */
</script>

<script>
export default { name: 'MyStatisticView' }
</script>

<style scoped>
.page-card {
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
}
</style>
