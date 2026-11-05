<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="page-header">
        <span class="page-title">群组详情</span>
        <div class="header-btns">
          <el-button plain @click="goBack">返回</el-button>
          <el-button v-if="canManage" type="primary" @click="goEdit">编辑</el-button>
        </div>
      </div>
    </template>

    <!-- 详情网格：默认一行两字段（字段名:内容 = 2:3）；长内容字段（is-full）一行一字段（2:8） -->
    <div v-if="detail.row" class="detail-grid">
      <template v-for="(item, index) in detailFields" :key="item.placeholder ? `__placeholder__${index}` : item.label">
        <div v-if="item.placeholder" class="detail-item detail-item-placeholder">
          <span class="detail-label"></span>
          <span class="detail-value"></span>
        </div>
        <div v-else class="detail-item" :class="{ 'is-full': item.full }">
          <span class="detail-label">{{ item.label }}</span>
          <span class="detail-value" :class="{ 'detail-tags': !!item.tags }">
            <el-tag v-if="item.tag" :type="item.tagType" size="small">{{ item.value }}</el-tag>
            <!-- 多值标签列表（成员 / 可见范围），空列表显示 '-' -->
            <template v-else-if="item.tags && item.tags.length">
              <el-tag v-for="(name, tagIndex) in item.tags" :key="tagIndex" size="small">{{ name }}</el-tag>
            </template>
            <template v-else-if="item.tags">-</template>
            <template v-else>{{ item.value }}</template>
          </span>
        </div>
      </template>
    </div>
    <el-empty v-else description="加载中或数据不存在" />
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { hasAuth } from '@/utils/auth'
import { loadGroup } from '@/api/org'
import { getDictTextMap } from '@/utils/dict'
import { buildDetailGridFields } from '@/utils/detail'

/**
 * 群组详情页：/org/group/detail/:id（06 清单 4.18、02-群组功能分析.md 3.4），
 * 按路由 id 经 loadGroup 只读展示：基本字段 + 成员（userNames）+ 可见范围（viewerNames）。
 * 排版与 OrgDetailView 基准一致：detail-grid 默认一行两字段（2:3），
 * 成员/可见范围长内容字段独占一行（is-full，字段名:内容 = 2:8），
 * 由 buildDetailGridFields 自动补位防边框断裂。
 * 类型/布尔文本（dict.org_group_type / yes_no）由 dict 模块提供，加载失败时回退兜底文本。
 * 维护权限 canManage 与 GroupManageView#canManage / GroupFormView#canMaintain 同口径：
 * 管理员（SYS_ORG_GROUP_ADMIN / SYS_ORG_ADMIN，SYS_ADMIN 由 hasAuth 放行）可维护全部群组，
 * 普通用户仅可维护自己的个人群组；最终权限由后端 checkMaintainable 兜底。
 * 可见范围：fieldIsAllViewer 为 true（COMMON 未选可见用户时后端派生）直接显示「全员可见」。
 * 路由与菜单由后续任务接入，本页面不修改 router。
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const detail = reactive({ row: null })

/** 码值文本映射（dict：org_group_type 字符串码值 COMMON/PERSONAL / yes_no 布尔值） */
const TYPE_TEXT = ref({})
const YES_NO_TEXT = ref({})

/** 管理员权限（与后端 group 鉴权一致）：群组管理员 / 模块管理员 */
const AUTH_GROUP_ADMIN = ['SYS_ORG_GROUP_ADMIN', 'SYS_ORG_ADMIN']

/**
 * 编辑按钮可见性：管理员可维护全部群组；
 * 普通用户仅可维护自己的个人群组（PERSONAL 且 fieldOwnerId 为当前登录人）。
 */
const canManage = computed(() => {
  const r = detail.row
  if (!r) return false
  if (hasAuth(AUTH_GROUP_ADMIN)) return true
  const currentId = userStore.userInfo && userStore.userInfo.fieldId
  return r.fieldType === 'PERSONAL' && !!currentId && r.fieldOwnerId === currentId
})

onMounted(async () => {
  const id = route.params.id
  if (!id) {
    ElMessage.error('缺少查看对象的 ID')
    goBack()
    return
  }
  loadDictOptions()
  try {
    const res = await loadGroup(id)
    detail.row = res.data || null
    if (!detail.row) {
      ElMessage.warning('未找到该群组')
    }
  } catch (error) {
    // 拦截器已统一提取 message；接口失败提示后返回列表
    ElMessage.error(error.message || '加载详情失败')
    goBack()
  }
})

/** 加载码值文本映射（org_group_type / yes_no）；失败不阻塞详情展示，文本回退兜底 */
async function loadDictOptions() {
  try {
    const [typeText, yesNoText] = await Promise.all([
      getDictTextMap('org_group_type'),
      getDictTextMap('yes_no', { boolean: true })
    ])
    TYPE_TEXT.value = typeText
    YES_NO_TEXT.value = yesNoText
  } catch {
    // 码值加载失败：文本回退原值 / 兜底文案
  }
}

/** 详情字段数组：成员与可见范围长内容字段标记 full（一行一字段，2:8） */
const detailFields = computed(() => {
  const r = detail.row
  if (!r) return []
  const fields = [
    { label: '名称', value: r.fieldName || '-' },
    { label: '类型', value: typeText(r.fieldType) },
    { label: '所有人', value: r.fieldOwnerName || '-' },
    { label: '全员可见', value: yesNoText(r.fieldIsAllViewer) },
    { label: '启用状态', value: r.fieldEnable ? '启用' : '停用', tag: true, tagType: r.fieldEnable ? 'success' : 'danger' },
    { label: '创建时间', value: formatTime(r.fieldCreateTime) },
    { label: '更新时间', value: formatTime(r.fieldUpdateTime) },
    // 成员：userNames 逐个 el-tag 渲染（独占一行）
    { label: '成员', tags: r.userNames || [], full: true },
    // 可见范围：全员可见显示文字，否则 viewerNames el-tag 列表（独占一行）
    buildViewerField(r)
  ]
  return buildDetailGridFields(fields)
})

/** 可见范围字段：fieldIsAllViewer 为 true 显示「全员可见」，否则 viewerNames 列表，空列表显示 '-' */
function buildViewerField(row) {
  if (row.fieldIsAllViewer) {
    return { label: '可见范围', value: '全员可见', full: true }
  }
  return { label: '可见范围', tags: row.viewerNames || [], full: true }
}

function typeText(value) {
  return TYPE_TEXT.value[value] || '-'
}

function yesNoText(value) {
  if (value == null) return '-'
  return YES_NO_TEXT.value[value] ?? (value ? '是' : '否')
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function goBack() {
  router.push('/org/group')
}

function goEdit() {
  if (detail.row && detail.row.fieldId) {
    router.push(`/org/group/edit/${detail.row.fieldId}`)
  }
}
</script>

<script>
export default { name: 'GroupDetailView' }
</script>

<style scoped>
/* 成员 / 可见范围多值标签容器：flex 换行 + 间距（边框与宽度公共样式见 styles/form-page.css） */
.detail-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
}
</style>
