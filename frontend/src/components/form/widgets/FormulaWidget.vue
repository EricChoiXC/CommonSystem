<template>
  <!--
    编辑/详情态：只读展示计算结果（值来源仅两处——回显落库值 / 保存或 validate 响应
    回带的最终数据由渲染器或宿主刷新；ADR-09 红线：前端一律不本地计算、不 eval 公式，
    前端值仅预览性质，服务端重算结果为权威落库值，功能详细设计 §6.3）。
    设计态（mode='design'，设计器画布内部复用）：expression 只读预览 +【设计公式】按钮
    打开内嵌 FormulaDesigner 弹窗（UI 分册 §4.5.2 formula 行交互形态）。
  -->
  <span v-if="mode !== 'design'" class="formula-preview">
    <template v-if="displayValue !== null">{{ displayValue }}</template>
    <span v-else class="formula-empty">暂无计算值，以服务端重算为准</span>
  </span>
  <div v-else class="formula-design">
    <el-input :model-value="expression" readonly placeholder="未配置公式">
      <template #append>
        <el-button @click="openDesigner">设计公式</el-button>
      </template>
    </el-input>
    <!--
      复用 F14 FormulaDesigner 契约（components/formula/FormulaDesigner.vue）：visible /
      availableVars（{ code, type?, label? } 变量声明，渲染器传入表单字段码与明细数组变量
      ${list.field} 投影）/ expectedReturnType / expression；confirm 载荷为最终表达式文本。
      :expression 单向传入（弹窗内部编辑文本由其自管理，打开时以 props.expression 重新同步），
      编辑过程中的 update:expression 不回接——仅 confirm 确认后 emit 本组件的
      update:expression 新表达式（画布数据绑定旁路：widget 只 emit、不直接改 props，
      设计器 Wave 4 决定是否回写 field.props.expression）。
    -->
    <FormulaDesigner
      :visible="designerVisible"
      :expression="expression"
      :expected-return-type="expectedReturnType"
      :available-vars="availableVars"
      @update:visible="designerVisible = $event"
      @confirm="handleDesignerConfirm"
    />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import FormulaDesigner from '../../formula/FormulaDesigner.vue'

/**
 * 公式计算 widget（type=formula，功能详细设计 §1.3 formula 行；§6.3 ADR-09）。
 *
 * 统一契约（渲染器 F15-FE-005 按此消费，§4.3）：
 * - props：modelValue（v-model 显式声明，计算结果值 NUMBER/STRING/BOOLEAN/DATE 随
 *   expectedReturnType）+ 扩展属性项 expression/expectedReturnType/mode/availableVars
 *   + 统一控制位 disabled/readonly；
 * - emits：update:modelValue / update:expression（design 态 confirm 回带新表达式，
 *   edit/detail 态不触发）/ change / focus / blur（公式值只读展示、无聚焦控件，
 *   focus/blur 无对应触发时机，按契约保留声明）；
 * - 值契约：前端一律不本地计算/不 eval 公式（ADR-09 红线），edit/detail 态只读展示
 *   modelValue，空值展示「以服务端重算为准」占位；值来源仅两处——回显落库值 /
 *   保存或 validate 响应回带的最终数据（渲染器/宿主刷新），widget 自身不产出值，
 *   update:modelValue 保留声明以对齐统一契约、实际不触发；
 * - 三态 mode：'edit'（缺省）/ 'detail' 只读展示计算结果，'design' 设计器画布配置态
 *   （expression 只读预览 +【设计公式】按钮，disabled/readonly 控制位不作用于配置入口）；
 * - 公式编辑在设计器属性面板另有 propSchema editor='formula' 通道（UI 分册 §4.5.2），
 *   widget 内嵌弹窗为画布配置态入口，两者共用 FormulaDesigner 契约（F14，只消费不修改）；
 * - 固定属性项由渲染器层承载，本组件只渲染控件本体，不感知规则（架构原则 2）。
 */
const props = defineProps({
  /** 计算结果值（回显落库值 / 保存响应回带的最终数据；空值 null；前端不产出） */
  modelValue: { type: [String, Number, Boolean], default: null },
  /** 公式表达式（${code} 形态，design 态只读预览 + 弹窗回显） */
  expression: { type: String, default: '' },
  /** 期望返回类型 NUMBER/STRING/BOOLEAN/DATE（透传 FormulaDesigner 校验/试算入参） */
  expectedReturnType: { type: String, default: 'NUMBER' },
  /** 渲染形态：'edit'（缺省）/ 'detail' 只读展示计算结果，'design' 设计器画布配置态 */
  mode: { type: String, default: 'edit' },
  /** design 态变量声明 [{ code, type?, label? }]（渲染器传表单字段码声明，缺省 []） */
  availableVars: { type: Array, default: () => [] },
  /** 统一控制位：禁用（公式值本身只读展示，仅契约统一保留） */
  disabled: { type: Boolean, default: false },
  /** 统一控制位：只读（同上，仅契约统一保留） */
  readonly: { type: Boolean, default: false },
})

// update:modelValue/change/focus/blur 为统一契约保留位（值来源仅服务端重算回写，见头注）
const emit = defineEmits(['update:modelValue', 'update:expression', 'change', 'focus', 'blur'])

/** 内嵌 FormulaDesigner 弹窗显隐（design 态） */
const designerVisible = ref(false)

/**
 * 展示值归一：空值（null/undefined/空串）→ null 走占位文案；BOOLEAN 结果展示为
 * 是/否（实现期细化登记：设计文档未定稿展示格式，随 expectedReturnType=BOOLEAN 语义）；
 * 其余（NUMBER/STRING/DATE 格式化串）String 化展示。
 */
const displayValue = computed(() => {
  const value = props.modelValue
  if (value === null || value === undefined || value === '') return null
  if (typeof value === 'boolean') return value ? '是' : '否'
  return String(value)
})

function openDesigner() {
  designerVisible.value = true
}

/** confirm 载荷 = 最终表达式文本，经 update:expression 上抛（设计器决定是否回写 props） */
function handleDesignerConfirm(expression) {
  emit('update:expression', expression)
}
</script>

<style scoped>
.formula-preview {
  color: var(--el-text-color-regular);
}

.formula-empty {
  color: var(--el-text-color-placeholder);
}

.formula-design {
  width: 100%;
}
</style>
