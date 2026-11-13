<template>
  <div class="ui-page">
    <!-- 左侧：公共组件菜单 -->
    <aside class="ui-side">
      <div class="ui-side-title">公共组件</div>
      <el-menu :default-active="active" class="ui-menu" @select="handleSelect">
        <el-menu-item v-for="item in components" :key="item.name" :index="item.name">
          <span class="ui-menu-label">{{ item.label }}</span>
          <span class="ui-menu-desc">{{ item.desc }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <!-- 右侧：使用样例 + 配置项说明 -->
    <main class="ui-main">
      <template v-if="current">
        <h2 class="ui-title">{{ current.label }}</h2>
        <p class="ui-summary">{{ current.summary }}</p>

        <section class="ui-section">
          <h3>使用样例</h3>
          <el-alert
            v-if="current.demoTip"
            :title="current.demoTip"
            type="info"
            :closable="false"
            class="ui-tip"
          />
          <div class="ui-demo">
            <!-- 地址本 -->
            <div v-if="active === 'AddressBook'" class="ui-demo-item">
              <AddressBook
                v-model:field-id="demo.addressId"
                v-model:field-name="demo.addressName"
                :org-type="'ALL'"
              />
              <p class="ui-demo-result">
                当前选中：fieldId = {{ demo.addressId || '（未选择）' }}，fieldName = {{ demo.addressName || '（未选择）' }}
              </p>
            </div>

            <!-- 附件：fieldModelName = 'template'，fieldKey = 'template' -->
            <div v-else-if="active === 'Attachment'" class="ui-demo-item">
              <Attachment
                field-model-id="template-demo"
                field-model-name="template"
                field-key="template"
              />
              <p class="ui-demo-result">
                当前配置：fieldModelName = "template"，fieldKey = "template"，fieldModelId = "template-demo"
              </p>
            </div>

            <!-- 日期时间选择器 -->
            <div v-else-if="active === 'DateTimePicker'" class="ui-demo-item">
              <DateTimePicker v-model="demo.date" field-name="单据日期" calendar-type="datetime" />
              <p class="ui-demo-result">当前值：{{ demo.date || '（未选择）' }}</p>
            </div>

            <!-- 选择器 -->
            <div v-else-if="active === 'Selector'" class="ui-demo-item">
              <Selector
                v-model:field-id="demo.materialId"
                v-model:field-name="demo.materialName"
                url="/backend/material/list"
                :filter="[{ text: '名称', name: 'fieldName' }]"
                :show="[
                  { text: '名称', name: 'fieldName' },
                  { text: '编码', name: 'fieldCode' }
                ]"
              />
              <p class="ui-demo-result">
                当前选中：fieldId = {{ demo.materialId || '（未选择）' }}，fieldName = {{ demo.materialName || '（未选择）' }}
              </p>
            </div>

            <!-- 编号规则编排 -->
            <div v-else-if="active === 'NumberRuleComposer'" class="ui-demo-item">
              <NumberRuleComposer
                :value="demo.numberRule"
                :on-change="(v) => (demo.numberRule = v)"
                field-name="fieldRule"
              />
              <p class="ui-demo-result">
                当前规则：共 {{ demo.numberRule.items.length }} 项；最新值：{{ numberRuleSummary }}
              </p>
            </div>
          </div>

          <pre class="ui-code"><code>{{ current.code }}</code></pre>
        </section>

        <section class="ui-section">
          <h3>配置项说明</h3>
          <el-table :data="current.props" border size="small">
            <el-table-column prop="name" label="属性名" width="180" />
            <el-table-column prop="type" label="类型" width="130" />
            <el-table-column prop="required" label="必填" width="70" align="center" />
            <el-table-column prop="default" label="默认值" width="200" show-overflow-tooltip />
            <el-table-column prop="desc" label="说明" min-width="280" show-overflow-tooltip />
          </el-table>
        </section>

        <section v-if="current.emits && current.emits.length" class="ui-section">
          <h3>事件说明</h3>
          <el-table :data="current.emits" border size="small">
            <el-table-column prop="name" label="事件名" width="220" />
            <el-table-column prop="desc" label="说明" min-width="320" />
          </el-table>
        </section>
      </template>

      <el-empty v-else description="请从左侧选择组件" />
    </main>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { AddressBook, Attachment, DateTimePicker, NumberRuleComposer, Selector } from '@/components'

/** 演示区绑定的状态 */
const demo = reactive({
  addressId: '',
  addressName: '',
  materialId: '',
  materialName: '',
  date: '',
  numberRule: {
    items: [
      { id: 'ci-a1b2c3d4e5f6', type: 'text', name: '前缀', text: 'CG' },
      { id: 'ci-b2c3d4e5f6a1', type: 'datetime', name: '日期', format: 'yyyyMMdd' },
      { id: 'ci-c3d4e5f6a1b2', type: 'seq', name: '流水号', start: 1, length: 3, padZero: true, resetPeriod: 'DAY' }
    ]
  }
})

/** 编号规则演示：当前 value 的 JSON 摘要（截断展示） */
const numberRuleSummary = computed(() => {
  const json = JSON.stringify(demo.numberRule)
  return json.length > 160 ? json.slice(0, 160) + '…' : json
})

const active = ref('AddressBook')

/** 公共组件清单：菜单项 + 使用样例 + 配置项说明 */
const components = [
  {
    name: 'AddressBook',
    label: '地址本',
    desc: '组织架构选择弹窗',
    summary: '提供组织架构选择的弹窗，弹窗分上中下三层：上层关键字查询、中层组织架构树 / 查询两个页签、下层已选项与确认取消。',
    demoTip: '地址本依赖后端组织架构接口，演示打开弹窗需后端服务支持。',
    code: `<template>
  <AddressBook
    v-model:field-id="form.orgId"
    v-model:field-name="form.orgName"
    :org-type="'ALL'"
    :multi="false"
  />
</template>`,
    props: [
      { name: 'fieldId', type: 'String', required: '是', default: "''", desc: '关联的 id 值字段' },
      { name: 'fieldName', type: 'String', required: '是', default: "''", desc: '关联的名称值字段' },
      {
        name: 'orgType',
        type: 'String',
        required: '否',
        default: "'ALL'",
        desc: '可选的组织架构类型：ALL / ORG / DEPT / ORGDEPT / PERSON / POST'
      },
      { name: 'multi', type: 'Boolean', required: '否', default: 'false', desc: '是否多选' },
      { name: 'septarator', type: 'String', required: '否', default: "';'", desc: '多选的分隔符，要求仅限符号' },
      { name: 'limitRange', type: 'Array', required: '否', default: '[]', desc: '限制的组织架构范围：组织架构 fieldId 值数组，默认为空（不限制）' },
      {
        name: 'callback',
        type: 'Function',
        required: '否',
        default: 'null',
        desc: '确认选择后的回调函数 callback(fieldId, fieldName, selectedList)'
      }
    ],
    emits: [
      { name: 'update:fieldId', desc: '确认选择 / 清空时回写 fieldId 值' },
      { name: 'update:fieldName', desc: '确认选择 / 清空时回写 fieldName 值' }
    ]
  },
  {
    name: 'Attachment',
    label: '附件',
    desc: '附件上传与管理',
    summary: '提供附件的上传、列表展示、预览、下载与删除；上传 / 删除按钮显隐由 canUpload / canDelete 控制，预览与下载对所有人显示，删除执行前二次确认，最终权限由后端接口二次校验。',
    code: `<template>
  <Attachment
    field-model-id="template-demo"
    field-model-name="template"
    field-key="template"
  />
</template>`,
    props: [
      {
        name: 'fieldModelId',
        type: 'String',
        required: '否',
        default: "''",
        desc: '传递给接口的 fieldModelId 字段值（上传前需已存在文档 id）'
      },
      { name: 'fieldModelName', type: 'String', required: '是', default: '-', desc: '传递给接口的 fieldModelName 字段值' },
      { name: 'fieldKey', type: 'String', required: '否', default: "''", desc: '传递给接口的 fieldKey 字段值' },
      { name: 'multi', type: 'Boolean', required: '否', default: 'false', desc: '是否可上传多个附件' },
      {
        name: 'fileType',
        type: 'String',
        required: '否',
        default: "''",
        desc: "可上传的文件后缀类型，默认为空（不限制），如 'jpg,png' 或 '.jpg,.png'"
      },
      { name: 'uploadCallback', type: 'Function', required: '否', default: 'null', desc: '上传结束后的回调函数 callback(attachment)' },
      {
        name: 'uploadValidate',
        type: 'Function',
        required: '否',
        default: 'null',
        desc: '确认上传文件到触发后端上传接口中间的校验函数，函数返回值必须为 true 才能继续上传'
      },
      { name: 'deleteCallback', type: 'Function', required: '否', default: 'null', desc: '删除结束后的回调函数 callback(attachment)' },
      { name: 'canUpload', type: 'Boolean', required: '否', default: 'true', desc: '是否显示上传入口，由页面自行控制' },
      { name: 'canDelete', type: 'Boolean', required: '否', default: 'true', desc: '是否显示删除按钮，由页面自行控制' }
    ],
    emits: []
  },
  {
    name: 'DateTimePicker',
    label: '日期时间选择器',
    desc: '日期时间选择',
    summary: '通过 calendarType 切换选择器类型（year / month / week / date / daterange / time / datetime / datetimerange），支持年份 / 月份 / 小时 / 分钟 / 星期范围限制，范围参数支持 a-b 区间或 [a,b,c] 列表两种写法。',
    code: `<template>
  <DateTimePicker
    v-model="form.date"
    field-name="单据日期"
    calendar-type="datetime"
  />
</template>`,
    props: [
      { name: 'fieldName', type: 'String', required: '否', default: "''", desc: '选择器关联的字段，用于 name 属性与单值类型占位提示' },
      {
        name: 'calendarType',
        type: 'String',
        required: '否',
        default: "'datetime'",
        desc: '选择器类型：year / month / week / date / daterange / time / datetime / datetimerange'
      },
      { name: 'dateFormat', type: 'String', required: '否', default: "'YYYY-MM-DD'", desc: '日期格式（年/月/周/日期/日期范围）' },
      { name: 'timeFormat', type: 'String', required: '否', default: "'HH:mm:ss'", desc: '时间格式' },
      { name: 'datetimeFormat', type: 'String', required: '否', default: "'YYYY-MM-DD HH:mm:ss'", desc: '日期时间格式（datetime / datetimerange）' },
      { name: 'yearRange', type: 'String / Array', required: '否', default: "''", desc: "可选年份范围或年份列表，如 '2024-2030'、[2024, 2025, 2026]" },
      { name: 'monthRange', type: 'String / Array', required: '否', default: "''", desc: "可选月份范围或月份列表，如 '1-12'、[1, 3, 5, 7, 9, 11]" },
      { name: 'hourRange', type: 'String / Array', required: '否', default: "''", desc: "可选小时范围或小时列表，如 '6-18'、[8, 9, 10, 14, 15, 16]" },
      { name: 'minuteRange', type: 'String / Array', required: '否', default: "''", desc: "可选分钟范围或分钟列表，如 '0-59'、[0, 15, 30, 45]" },
      {
        name: 'weekDays',
        type: 'String / Array',
        required: '否',
        default: "''",
        desc: '可选星期范围或星期列表，如 [1]、[1, 2, 3, 4, 5]，约定 1 为星期一，7 为星期日'
      },
      { name: 'placeholder', type: 'String', required: '否', default: "''", desc: '单值类型占位提示覆盖，不传时按 fieldName 生成「请选择 + fieldName」' },
      { name: 'startPlaceholder', type: 'String', required: '否', default: "'开始时间'", desc: '范围类型起始占位提示' },
      { name: 'endPlaceholder', type: 'String', required: '否', default: "'结束时间'", desc: '范围类型结束占位提示' },
      { name: 'rangeSeparator', type: 'String', required: '否', default: "'至'", desc: '范围类型分隔符' }
    ],
    emits: [
      { name: 'change', desc: '值变更时触发：单值类型回传字符串/日期对象，范围类型回传 [开始, 结束] 数组' }
    ]
  },
  {
    name: 'Selector',
    label: '选择器',
    desc: '数据来源选择弹窗',
    summary: '通过 url 调用列表查询接口（POST，请求体遵循 BaseRequest：{ body, pages }），filter 定义可筛选项，show 定义列表显示项，确认后回调。',
    demoTip: '选择器依赖后端列表接口，演示打开弹窗需后端服务支持。',
    code: `<template>
  <Selector
    v-model:field-id="form.materialId"
    v-model:field-name="form.materialName"
    url="/backend/material/list"
    :filter="[{ text: '名称', name: 'fieldName' }]"
    :show="[
      { text: '名称', name: 'fieldName' },
      { text: '编码', name: 'fieldCode' }
    ]"
  />
</template>`,
    props: [
      { name: 'fieldId', type: 'String', required: '是', default: "''", desc: '关联的 id 值字段' },
      { name: 'fieldName', type: 'String', required: '是', default: "''", desc: '关联的名称值字段' },
      { name: 'url', type: 'String', required: '是', default: '-', desc: '请求的列表查询接口 url（POST，请求体遵循 BaseRequest { body, pages }）' },
      {
        name: 'filter',
        type: 'Array',
        required: '否',
        default: "[{ text: '名称', name: 'fieldName' }]",
        desc: '可筛选项，对象数组 { text: 查询的中文名, name: 查询的字段名称 }'
      },
      {
        name: 'show',
        type: 'Array',
        required: '否',
        default: "[{ text: '名称', name: 'fieldName' }]",
        desc: '列表显示项，对象数组 { text: 显示的中文名, name: 显示的字段名称 }'
      },
      { name: 'multi', type: 'Boolean', required: '否', default: 'false', desc: '是否多选' },
      { name: 'septarator', type: 'String', required: '否', default: "';'", desc: '多选的分隔符，要求仅限符号' },
      {
        name: 'callback',
        type: 'Function',
        required: '否',
        default: 'null',
        desc: '确认选择后的回调函数 callback(fieldId, fieldName, selectedList)'
      }
    ],
    emits: [
      { name: 'update:fieldId', desc: '确认选择 / 清空时回写 fieldId 值' },
      { name: 'update:fieldName', desc: '确认选择 / 清空时回写 fieldName 值' }
    ]
  },
  {
    name: 'NumberRuleComposer',
    label: '编号规则编排',
    desc: '编号规则配置组件',
    summary: '三区布局（插入项 / 已插入项列表 / 选中项属性面板），实时调用 preview/render 预览 3 个样例号，校验失败就地红字提示；seq 项 id 自动生成且不可编辑（计数器锚点）。',
    demoTip: '实时预览依赖后端 preview/render 接口，需后端服务与登录态支持。',
    code: `<template>
  <NumberRuleComposer
    :value="form.fieldRule"
    :on-change="(v) => (form.fieldRule = v)"
    field-name="fieldRule"
  />
</template>`,
    props: [
      { name: 'value', type: 'Object', required: '是', default: 'null', desc: '规则配置对象（value 协议 { items: [...] }，方案 §2.1 冻结契约），传 null / undefined 视为空规则' },
      { name: 'onChange', type: 'Function', required: '是', default: '-', desc: '值变更回调：组件基于当前 value 深拷贝生成新对象后回传 onChange(newValue)' },
      { name: 'fieldName', type: 'String', required: '否', default: "''", desc: '宿主表单字段名（编号编辑页传 "fieldRule"），仅用于组件内部 aria / 表单语义提示' },
      { name: 'disabled', type: 'Boolean', required: '否', default: 'false', desc: '是否禁用（置灰插入、排序、删除与属性编辑）' }
    ],
    emits: []
  }
]

const current = computed(() => components.find((item) => item.name === active.value))

function handleSelect(index) {
  active.value = index
}
</script>

<style scoped>
.ui-page {
  min-height: 100vh;
  display: flex;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', Arial, sans-serif;
}

/* 左侧菜单 */
.ui-side {
  width: 240px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}

.ui-side-title {
  padding: 16px 20px 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.ui-menu {
  border-right: none;
}

.ui-menu-label {
  display: block;
  font-size: 14px;
}

.ui-menu-desc {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}

/* 右侧内容 */
.ui-main {
  flex: 1;
  min-width: 0;
  padding: 24px 32px 48px;
  background: var(--el-fill-color-light);
}

.ui-title {
  margin: 0 0 8px;
  font-size: 20px;
  color: var(--el-text-color-primary);
}

.ui-summary {
  margin: 0 0 20px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.ui-section {
  margin-bottom: 24px;
  padding: 20px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.ui-section h3 {
  margin: 0 0 16px;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.ui-tip {
  margin-bottom: 12px;
}

.ui-demo {
  margin-bottom: 12px;
}

.ui-demo-result {
  margin: 12px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.ui-code {
  margin: 0;
  padding: 14px 16px;
  overflow-x: auto;
  border-radius: 4px;
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre;
}
</style>
