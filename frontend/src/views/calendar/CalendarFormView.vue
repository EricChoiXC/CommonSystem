<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="page-header">
        <span class="page-title">{{ isEdit ? '编辑日程' : '新建日程' }}</span>
        <div class="header-btns">
          <el-button @click="goBack">返回</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </div>
      </div>
    </template>

    <el-form :model="form" label-width="40%" :disabled="saving" class="page-form">
      <!-- 标题 -->
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="标题" required>
            <el-input v-model="form.fieldName" placeholder="请输入标题" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="类型" required>
            <el-select v-model="form.fieldType" style="width: 100%" :disabled="isEdit">
              <el-option label="个人日程" value="PERSONAL" />
              <el-option label="团队日程" value="TEAM" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 时间 -->
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="日程开始时间" required>
            <DateTimePicker
              v-model="form.fieldStartTime"
              calendar-type="datetime"
              placeholder="选择开始时间"
              style="width: 100%"
              datetime-format="YYYY-MM-DD HH:mm:ss"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="日程结束时间" required>
            <DateTimePicker
              v-model="form.fieldEndTime"
              calendar-type="datetime"
              placeholder="选择结束时间"
              style="width: 100%"
              datetime-format="YYYY-MM-DD HH:mm:ss"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 接收人（团队日程） -->
      <el-row :gutter="12" v-if="form.fieldType === 'TEAM'">
        <el-col :span="24">
          <el-form-item label="接收人" label-width="20%" required>
            <AddressBook
              v-model:field-id="selectedIds"
              v-model:field-name="selectedNames"
              org-type="PERSON"
              multiple
            />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 内容 -->
      <el-row :gutter="12">
        <el-col :span="24">
          <el-form-item label="内容" label-width="20%">
            <el-input v-model="form.fieldContent" type="textarea" :rows="4" placeholder="请输入日程内容" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { markTabToClose } from '@/utils/tabs'
import { ElMessage } from 'element-plus'
import { saveCalendar, updateCalendar, loadCalendar } from '@/api/calendar'
import { getInitID } from '@/api/common'
import { watchRouteQuery } from '@/utils/routeQuery'
import DateTimePicker from '@/components/DateTimePicker.vue'

const route = useRoute()
const router = useRouter()

const isEdit = computed(() => !!route.params.id)
const saving = ref(false)

const form = reactive({
  fieldId: '',
  fieldName: '',
  fieldType: 'PERSONAL',
  fieldStartTime: '',
  fieldEndTime: '',
  fieldContent: '',
  personIds: []
})

/** 地址本选中的 ID 字符串（逗号分隔） */
const selectedIds = ref('')
const selectedNames = ref('')

/** 从地址本选中的 ID 字符串解析为数组 */
function parseSelectedIds() {
  if (!selectedIds.value) return []
  return selectedIds.value.split(';').filter(Boolean)
}

onMounted(async () => {
  if (isEdit.value) {
    const id = route.params.id
    if (!id) {
      ElMessage.error('缺少编辑对象的 ID')
      goBack()
      return
    }
    try {
      const res = await loadCalendar(id)
      const data = res.data
      if (!data) {
        ElMessage.warning('日程不存在')
        goBack()
        return
      }
      form.fieldId = data.fieldId
      form.fieldName = data.fieldName
      form.fieldType = data.fieldType
      form.fieldStartTime = data.fieldStartTime
      form.fieldEndTime = data.fieldEndTime
      form.fieldContent = data.fieldContent
      // 回填接收人
      if (data.persons && data.persons.length > 0) {
        selectedIds.value = data.persons.map(p => p.fieldPersonId).join(';')
        selectedNames.value = data.persons.map(p => p.fieldPersonName).join(';')
      }
    } catch (e) {
      ElMessage.error(e.message || '加载失败')
      goBack()
    }
  } else {
    // 新建：初始化 ID
    try {
      const res = await getInitID()
      form.fieldId = res.data || ''
    } catch {
      form.fieldId = ''
    }
    // 从路由 query 取默认日期
    if (route.query.startDate) {
      form.fieldStartTime = route.query.startDate + ' 09:00:00'
      form.fieldEndTime = route.query.startDate + ' 10:00:00'
    }
  }
})

// 二次从日历页进入时实例复用、onMounted 不会重跑，监听 query 同步带入的默认日期
watchRouteQuery(route, (q) => {
  if (isEdit.value || !q.startDate) return
  form.fieldStartTime = q.startDate + ' 09:00:00'
  form.fieldEndTime = q.startDate + ' 10:00:00'
})

async function handleSave() {
  if (!form.fieldName) {
    ElMessage.warning('请输入标题')
    return
  }
  if (!form.fieldStartTime) {
    ElMessage.warning('请选择开始时间')
    return
  }
  if (!form.fieldEndTime) {
    ElMessage.warning('请选择结束时间')
    return
  }
  if (form.fieldEndTime < form.fieldStartTime) {
    ElMessage.warning('结束时间不能早于开始时间')
    return
  }
  if (form.fieldType === 'TEAM' && !selectedIds.value) {
    ElMessage.warning('团队日程请选择接收人')
    return
  }

  saving.value = true
  try {
    const body = {
      fieldId: form.fieldId,
      fieldName: form.fieldName,
      fieldType: form.fieldType,
      fieldStartTime: form.fieldStartTime,
      fieldEndTime: form.fieldEndTime,
      fieldContent: form.fieldContent,
      personIds: parseSelectedIds()
    }

    if (isEdit.value) {
      await updateCalendar(body)
      ElMessage.success('编辑成功')
    } else {
      await saveCalendar(body)
      ElMessage.success('创建成功')
    }
    markTabToClose(route.path)
    router.push('/calendar')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/calendar')
}
</script>

<script>
export default { name: 'CalendarFormView' }
</script>