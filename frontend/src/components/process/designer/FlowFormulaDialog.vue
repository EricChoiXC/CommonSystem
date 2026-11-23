<template>
  <div class="flow-formula-field">
    <!-- 分册 §5.4 冻结交互：入口为不可编辑输入框 +【编辑】按钮，不允许在表单里直接手改公式 -->
    <el-input :model-value="modelValue" readonly :placeholder="placeholder" :disabled="disabled">
      <template #append>
        <el-button :disabled="disabled" @click="openDesigner">编辑</el-button>
      </template>
    </el-input>
    <div v-if="tip" class="flow-formula-tip">{{ tip }}</div>

    <!-- 弹窗托管公共公式设计器（expectedReturnType 随场景传入；表达式合法性权威校验在后端 validate） -->
    <FormulaDesigner
      v-model:visible="designerVisible"
      :expression="modelValue || ''"
      :expected-return-type="expectedReturnType"
      :available-vars="formulaVars"
      @confirm="handleFormulaConfirm"
    />
  </div>
</template>

<script setup>
/**
 * 流程设计器 · 统一公式编辑包装（v1_7 / P17-FE-003，分册 §5.4 路径 A）
 *
 * 供节点配置弹窗的 handlerFormula（审批人公式）/ ccFormula（抄送对象公式）使用：
 * 不可编辑 el-input +【编辑】打开公共 FormulaDesigner 弹窗，【确认回写】回填 v-model、
 * 取消/关闭放弃本次编辑。条件分支（FlowConditionNodeConfigDialog）、并行启动条件
 * （FlowForkNodeConfigDialog，V1.12 起均为节点级配置）均经本组件直连 FormulaDesigner
 * （expectedReturnType=BOOLEAN），与本组件口径一致：
 * 入口形态（只读输入框 + 编辑按钮）、确认回填 / 取消放弃契约相同（分册 §5.4 冻结两条
 * 实现路径 A/B 的交互契约一致，FE-003 交付登记保留分支弹窗直连形态、节点公式走本组件）。
 *
 * expectedReturnType 取值：审批人/抄送对象公式按解析语义传 STRING 或留空（宽松模式）——
 * 本组件默认留空走宽松模式（公式解析结果可为文本/Map/List，STRING 约束过严易误报），
 * 权威校验在后端公式定义器 validate；条件分支场景若复用本组件可传 BOOLEAN。
 *
 * 设计器内部组件：不进 components/index.js 统一出口（分册 §11.2 约定）。
 */
import { ref } from 'vue'
import FormulaDesigner from '@/components/formula/FormulaDesigner.vue'

const props = defineProps({
  /** 公式文本（v-model） */
  modelValue: { type: String, default: '' },
  /** 期望返回类型（NUMBER/STRING/BOOLEAN/DATE/ARRAY），空 = 宽松模式（不传跳过比对） */
  expectedReturnType: { type: String, default: '' },
  /** 公式上下文变量声明（元素 { code, type?, label? }，宿主页传分类文档实体字段字典） */
  formulaVars: { type: Array, default: () => [] },
  /** 输入框占位文案 */
  placeholder: { type: String, default: '未配置公式' },
  /** 输入框下方说明文案（可空） */
  tip: { type: String, default: '' },
  /** 是否禁用（只读展示不可打开编辑弹窗） */
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])

const designerVisible = ref(false)

function openDesigner() {
  if (props.disabled) {
    return
  }
  designerVisible.value = true
}

/** 【确认回写】：把设计器最终表达式回填到 v-model（取消/关闭不回写，FormulaDesigner 契约） */
function handleFormulaConfirm(expression) {
  emit('update:modelValue', String(expression || ''))
}
</script>

<style scoped>
.flow-formula-field {
  width: 100%;
}

.flow-formula-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
