<template>
  <div class="page-card">
    <el-card>
      <template #header>
        <div class="page-header">
          <span class="page-title">模块管理</span>
          <div class="module-actions">
            <el-button :loading="loading" @click="loadData">刷新</el-button>
          </div>
        </div>
      </template>

      <!-- 四分栏卡片布局：模块组数量极少，无筛选区/工具区/分页（卡片操作页形态） -->
      <el-row :gutter="16" v-loading="loading">
        <el-col v-for="row in rows" :key="row.fieldId" :span="6" class="module-col">
          <el-card shadow="hover" class="module-card">
            <template #header>
              <div class="module-card-header">
                <span class="module-name">{{ row.fieldName }}</span>
                <span class="module-key">{{ row.fieldKey }}</span>
              </div>
            </template>
            <div class="module-remark">{{ row.fieldRemark || '-' }}</div>
            <div class="module-status">
              <!-- 状态标签与开关同源：同一 row.fieldEnable，切换走 handleToggle 二次确认 -->
              <el-tag :type="row.fieldEnable ? 'success' : 'danger'" size="small">
                {{ row.fieldEnable ? '启用' : '停用' }}
              </el-tag>
              <el-switch v-model="row.fieldEnable" @change="handleToggle(row)" />
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { updateModuleEnable } from '@/api/common'
import { useModuleStore } from '@/stores/module'

/**
 * 模块管理页：/module（纯卡片操作页）
 * 仅 SYS_MODULE_ADMIN 权限可见（SYS_ADMIN 自动放行），整体启停各模块组。
 * 页面持有本地工作副本 rows（深拷贝展示），不直接绑 store 列表——
 * 避免确认弹窗打开期间 store 状态被提前改写导致顶部菜单瞬时误隐藏；
 * 启停成功后经 moduleStore.applyEnable 同步 store，菜单过滤即时生效。
 * 交互口径见 docs/v1_1-common/05-增加接口开关/接口开关机制UI设计分册.md 第 3~5 节。
 */
const moduleStore = useModuleStore()

const loading = ref(false)
/** 本地工作副本：模块组卡片行（fieldId / fieldKey / fieldName / fieldEnable / fieldRemark） */
const rows = ref([])

/** 加载模块组列表：复用 store 缓存，未加载过时先经 store 拉取，再深拷贝为本页展示行 */
async function loadData() {
  loading.value = true
  try {
    if (!moduleStore.loaded) {
      try {
        await moduleStore.loadModules()
      } catch (error) {
        ElMessage.error(error.message || '加载失败')
        rows.value = []
        return
      }
    }
    // 深拷贝展示，避免弹窗确认期间直接改写 store 状态
    rows.value = moduleStore.modules.map((m) => ({ ...m }))
  } finally {
    loading.value = false
  }
}

/**
 * 开关切换：@change 触发时 row.fieldEnable 已是切换后的目标值，切换前值 = !row.fieldEnable。
 * 1. 先 ElMessageBox.confirm 二次确认（文案按目标值区分），取消则回滚显示态、不发起请求；
 * 2. 确认后调 updateModuleEnable，成功经 store.applyEnable 同步单例数据源；
 * 3. 失败回滚开关显示态，不重复弹错误提示（MODULE_DISABLED 等已由 axios 拦截器统一提示）。
 */
async function handleToggle(row) {
  const previous = !row.fieldEnable
  const message = row.fieldEnable
    ? `确认开启模块组「${row.fieldName}」？开启后该模块组全部接口恢复服务。`
    : `确认关闭模块组「${row.fieldName}」？关闭后该模块组全部接口停止服务，用户侧菜单将隐藏并提示「模块未开启」。`
  try {
    await ElMessageBox.confirm(message, '模块启停确认', {
      type: 'warning',
      confirmButtonText: row.fieldEnable ? '确认开启' : '确认关闭'
    })
  } catch {
    // 用户取消：不发起请求，回滚开关显示态
    row.fieldEnable = previous
    return
  }
  try {
    await updateModuleEnable({ fieldId: row.fieldId, fieldEnable: row.fieldEnable })
    // 成功：同步 store（菜单过滤即时生效）；后端成功路径内已完成缓存刷新与广播，前端无感
    moduleStore.applyEnable(row.fieldId, row.fieldEnable)
    ElMessage.success(row.fieldEnable ? '模块组已开启' : '模块组已关闭')
  } catch {
    // 失败：仅回滚开关显示态，错误提示由 axios 拦截器统一给出，避免双重提示
    row.fieldEnable = previous
  }
}

onMounted(loadData)
</script>

<script>
export default { name: 'ModuleManageView' }
</script>

<style scoped>
.module-actions {
  display: flex;
  gap: 8px;
}

.module-col {
  margin-bottom: 16px;
}

.module-card-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.module-name {
  font-weight: 600;
}

.module-key {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.module-remark {
  min-height: 20px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.module-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
