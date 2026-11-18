<template>
  <div ref="containerRef" class="formula-code-editor" :class="{ 'is-readonly': readonly }"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Compartment, EditorState, StateEffect, StateField } from '@codemirror/state'
import {
  Decoration,
  EditorView,
  drawSelection,
  highlightActiveLine,
  highlightActiveLineGutter,
  highlightSpecialChars,
  keymap,
  lineNumbers,
  placeholder as cmPlaceholder
} from '@codemirror/view'
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands'
import { HighlightStyle, StreamLanguage, bracketMatching, syntaxHighlighting } from '@codemirror/language'
import { autocompletion } from '@codemirror/autocomplete'
import { tags } from '@lezer/highlight'

/**
 * 公式代码编辑器组件（FormulaCodeEditor）
 *
 * 公式定义器（v1.14，任务 F14-FE-002）的编辑器底座，为 F14-FE-003 公式设计器
 * 弹窗 FormulaDesigner.vue 提供内嵌编辑能力。
 *
 * 定位边界（方案 A1）：本组件只做输入辅助——语法高亮、节点插入写入、token 补全提示、
 * 错误行打标；不做任何前端语法解析与校验，表达式合法性权威裁决在后端 /validate，
 * 校验返回的错误行号由宿主经 defineExpose.setErrorLine() 回打。
 *
 * CodeMirror 6 封装边界：
 * - 基于 EditorView + StreamLanguage 自定义 tokenizer 做高亮（${} 变量占位符、函数名、
 *   字符串、数字、关键字 true/false/null/nil、注释、运算符与括号），无独立语法解析树；
 * - 依赖仅 codemirror meta 包（state/view/language/commands/autocomplete 经其传递引入），
 *   不引入 Math.js，不新增其他独立 CM 包；
 * - 未启用 closeBrackets：与 `${` 补全的 `}` 回填叠加会产生双右括号，故关闭。
 *
 * 契约：
 * - props.modelValue：表达式文本（String，v-model，`${}` 形态原样存取）；
 * - props.readonly：只读（Boolean，默认 false）；
 * - props.placeholder：空态占位提示（String，可选）；
 * - props.variableNames：变量码数组，`${` 触发后补全提示（Array，可选）；
 * - props.functionNames：函数名数组，元素为字符串或 { label, detail, apply }（Array，可选）；
 * - emit update:modelValue：编辑内容变化时同步；
 * - defineExpose.insertText(text)：在当前光标处插入文本并保持光标其后（节点插入唯一通道）；
 * - defineExpose.focus()：聚焦编辑器；
 * - defineExpose.setErrorLine(lineNumber) / clearErrorLine()：错误行打标/清除
 *   （行号 1 起，超界静默忽略；用 StateEffect + Decoration.line 实现行背景色）；
 * - defineExpose.getLineCount()：当前内容行数（供宿主定位错误行）。
 *
 * 高度定制：默认 180px，外部通过 CSS 变量 --fce-height 或覆盖容器高度调整。
 */
const props = defineProps({
  /** 表达式文本（v-model），`${}` 形态原样存取 */
  modelValue: { type: String, default: '' },
  /** 只读模式 */
  readonly: { type: Boolean, default: false },
  /** 空态占位提示 */
  placeholder: { type: String, default: '' },
  /** 变量码数组：`${` 触发后补全，应用时插入 `${码}`（或行内补 `}`） */
  variableNames: { type: Array, default: () => [] },
  /** 函数名数组：元素为字符串或 { label, detail, apply } */
  functionNames: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])

/* ==================== 语法高亮（StreamLanguage tokenizer） ==================== */

/** 关键字：Aviator 布尔与空值字面量 */
const FORMULA_KEYWORDS = new Set(['true', 'false', 'null', 'nil'])

/** token 名 → 高亮 tag 映射（显式声明，不依赖 StreamLanguage 默认表） */
const formulaTokenTable = {
  variable: tags.variableName,
  function: tags.function(tags.variableName),
  atom: tags.atom,
  number: tags.number,
  string: tags.string,
  comment: tags.comment,
  operator: tags.operator,
  bracket: tags.bracket
}

/** `${` 之后到行内 `}`（或行尾）整体作为一个 token，两级路径投影 `${list.field}` 天然覆盖 */
function readVariableBody(stream, state) {
  if (stream.skipTo('}')) {
    stream.next()
  } else {
    stream.skipToEnd()
  }
  state.inVariable = false
  return 'variable'
}

/** 字符串续段：处理 `\` 转义；未在行内闭合则到行尾终止（按单行字面量处理） */
function readStringBody(stream, state) {
  const quote = state.stringQuote
  while (!stream.eol()) {
    const ch = stream.next()
    if (ch === '\\') {
      stream.next()
      continue
    }
    if (ch === quote) break
  }
  state.stringQuote = null
  return 'string'
}

const formulaStream = {
  name: 'formula',
  startState: () => ({ inVariable: false, stringQuote: null, inBlockComment: false }),
  token(stream, state) {
    /* 块注释续段 */
    if (state.inBlockComment) {
      if (stream.skipTo('*/')) {
        stream.next()
        stream.next()
        state.inBlockComment = false
      } else {
        stream.skipToEnd()
      }
      return 'comment'
    }
    /* 字符串续段 */
    if (state.stringQuote) return readStringBody(stream, state)
    /* ${} 变量占位符续段 */
    if (state.inVariable) return readVariableBody(stream, state)

    /* 注释：斜杠斜杠行注释，斜杠星号块注释（Aviator 支持，下同） */
    if (stream.match('//')) {
      stream.skipToEnd()
      return 'comment'
    }
    if (stream.match('/*')) {
      state.inBlockComment = true
      return 'comment'
    }
    /* ${} 变量占位符（含两级路径投影） */
    if (stream.match('${')) {
      state.inVariable = true
      return readVariableBody(stream, state)
    }

    const ch = stream.peek()

    /* 字符串字面量（单/双引号） */
    if (ch === '"' || ch === "'") {
      stream.next()
      state.stringQuote = ch
      return readStringBody(stream, state)
    }
    /* 数字 */
    if (ch !== null && ch >= '0' && ch <= '9') {
      stream.match(/^\d+(\.\d+)?([eE][+-]?\d+)?/)
      return 'number'
    }
    /* 标识符 / 关键字 / 函数调用名（标识符后紧跟 `(`） */
    if (ch !== null && /[A-Za-z_$]/.test(ch)) {
      stream.eatWhile(/[\w$]/)
      const word = stream.current()
      if (FORMULA_KEYWORDS.has(word)) return 'atom'
      if (stream.peek() === '(') return 'function'
      return null
    }
    /* 括号 */
    if (ch !== null && /[{}[\]()]/.test(ch)) {
      stream.next()
      return 'bracket'
    }
    /* 运算符 */
    if (ch !== null && /[+\-*/%<>=!&|?:^~]/.test(ch)) {
      stream.next()
      return 'operator'
    }
    /* 其余（逗号、空白、属性点等）默认色 */
    stream.next()
    return null
  },
  languageData: {
    commentTokens: { line: '//', block: { open: '/*', close: '*/' } }
  },
  tokenTable: formulaTokenTable
}

/** 配色：取 Element Plus 默认色系，与工程浅色风格协调 */
const formulaHighlight = HighlightStyle.define([
  { tag: tags.variableName, color: '#409eff' },
  { tag: tags.function(tags.variableName), color: '#9254de' },
  { tag: tags.atom, color: '#f56c6c' },
  { tag: tags.number, color: '#e6a23c' },
  { tag: tags.string, color: '#67c23a' },
  { tag: tags.comment, color: '#909399', fontStyle: 'italic' },
  { tag: tags.operator, color: '#606266' },
  { tag: tags.bracket, color: '#606266' }
])

/* ==================== 错误行打标（StateEffect + Decoration.line） ==================== */

const setErrorLineEffect = StateEffect.define()
const clearErrorLineEffect = StateEffect.define()
const errorLineMark = Decoration.line({ class: 'cm-errorLine' })

const errorLineField = StateField.define({
  create: () => Decoration.none,
  update(deco, tr) {
    deco = deco.map(tr.changes)
    for (const effect of tr.effects) {
      if (effect.is(setErrorLineEffect)) {
        const no = effect.value
        /* 行号 1 起；非整数或超界静默忽略（保持现状） */
        if (Number.isInteger(no) && no >= 1 && no <= tr.state.doc.lines) {
          const line = tr.state.doc.line(no)
          deco = Decoration.set([errorLineMark.range(line.from)])
        }
      } else if (effect.is(clearErrorLineEffect)) {
        deco = Decoration.none
      }
    }
    return deco
  },
  provide: (field) => EditorView.decorations.from(field)
})

/* ==================== 补全提示（简版 source） ==================== */

/** 变量补全项：固定按 `${码}` 形态插入；行内 `${` 之后补全时只回填 `}` */
function variableOption(item, withWrapping) {
  const label = typeof item === 'string' ? item : item.label
  return {
    label,
    type: 'variable',
    detail: '变量',
    apply: withWrapping ? '${' + label + '}' : label + '}'
  }
}

/** 函数补全项：字符串或 { label, detail, apply }，字符串只插入函数名 */
function functionOption(item) {
  if (typeof item === 'string') {
    return { label: item, type: 'function', detail: '函数', apply: item }
  }
  return {
    label: item.label,
    type: 'function',
    detail: item.detail || '函数',
    apply: item.apply || item.label
  }
}

/** 补全 source：`${` 后只补变量；标识符处补变量 + 函数名（动态读 props，随外部变化） */
function buildCompletionSource() {
  return (context) => {
    const varPrefix = context.matchBefore(/\$\{[\w$]*/)
    if (varPrefix) {
      return {
        from: varPrefix.from + 2,
        options: props.variableNames.map((item) => variableOption(item, false)),
        validFor: /^[\w$]*$/
      }
    }
    const word = context.matchBefore(/[\w$]+/)
    if (!word && !context.explicit) return null
    return {
      from: word ? word.from : context.pos,
      options: [
        ...props.variableNames.map((item) => variableOption(item, true)),
        ...props.functionNames.map((item) => functionOption(item))
      ],
      validFor: /^[\w$]*$/
    }
  }
}

/* ==================== 编辑器装配 ==================== */

const containerRef = ref(null)
let editorView = null

/* 只读与占位提示经 Compartment 惰性重配置，切换不重建编辑器、不丢历史 */
const readonlyCompartment = new Compartment()
const placeholderCompartment = new Compartment()

function buildExtensions() {
  return [
    lineNumbers(),
    highlightActiveLineGutter(),
    highlightActiveLine(),
    highlightSpecialChars(),
    history(),
    drawSelection(),
    bracketMatching(),
    autocompletion({ override: [buildCompletionSource()] }),
    keymap.of([...historyKeymap, ...defaultKeymap]),
    StreamLanguage.define(formulaStream),
    syntaxHighlighting(formulaHighlight),
    EditorView.lineWrapping,
    errorLineField,
    EditorView.updateListener.of((update) => {
      if (update.docChanged) {
        emit('update:modelValue', update.state.doc.toString())
      }
    }),
    readonlyCompartment.of([
      EditorState.readOnly.of(props.readonly),
      EditorView.editable.of(!props.readonly)
    ]),
    placeholderCompartment.of(props.placeholder ? cmPlaceholder(props.placeholder) : [])
  ]
}

onMounted(() => {
  editorView = new EditorView({
    parent: containerRef.value,
    state: EditorState.create({ doc: props.modelValue ?? '', extensions: buildExtensions() })
  })
})

onBeforeUnmount(() => {
  editorView?.destroy()
  editorView = null
})

/* 外部 modelValue 变化（非本组件编辑产生）时整篇替换 */
watch(
  () => props.modelValue,
  (val) => {
    if (!editorView) return
    const next = val ?? ''
    const current = editorView.state.doc.toString()
    if (next !== current) {
      editorView.dispatch({ changes: { from: 0, to: current.length, insert: next } })
    }
  }
)

watch(
  () => props.readonly,
  (val) => {
    editorView?.dispatch({
      effects: readonlyCompartment.reconfigure([
        EditorState.readOnly.of(val),
        EditorView.editable.of(!val)
      ])
    })
  }
)

watch(
  () => props.placeholder,
  (val) => {
    editorView?.dispatch({
      effects: placeholderCompartment.reconfigure(val ? cmPlaceholder(val) : [])
    })
  }
)

/* ==================== 对外契约（供 FormulaDesigner 弹窗调用） ==================== */

/** 在当前光标处插入文本并保持光标其后；只读时静默忽略 */
function insertText(text) {
  if (!editorView || props.readonly) return
  const pos = editorView.state.selection.main.head
  const insert = text ?? ''
  editorView.dispatch({
    changes: { from: pos, insert },
    selection: { anchor: pos + insert.length },
    scrollIntoView: true
  })
}

/** 聚焦编辑器 */
function focus() {
  editorView?.focus()
}

/** 按行号高亮错误行（1 起，超界静默忽略） */
function setErrorLine(lineNumber) {
  editorView?.dispatch({ effects: setErrorLineEffect.of(lineNumber) })
}

/** 清除错误行高亮 */
function clearErrorLine() {
  editorView?.dispatch({ effects: clearErrorLineEffect.of(null) })
}

/** 当前内容行数 */
function getLineCount() {
  return editorView ? editorView.state.doc.lines : 0
}

defineExpose({ insertText, focus, setErrorLine, clearErrorLine, getLineCount })
</script>

<style scoped>
.formula-code-editor {
  /* 高度可调入口：外部以 CSS 变量 --fce-height 或覆盖容器高度调整 */
  --fce-height: 180px;
  height: var(--fce-height);
  min-height: 60px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
  transition: border-color 0.2s;
  text-align: left;
}

.formula-code-editor:focus-within {
  border-color: #409eff;
}

.formula-code-editor.is-readonly {
  background-color: #f5f7fa;
}

/* CodeMirror 动态生成的 DOM 无 scoped 属性，须经 :deep() 穿透 */
.formula-code-editor :deep(.cm-editor) {
  height: 100%;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.formula-code-editor :deep(.cm-editor.cm-focused) {
  outline: none;
}

.formula-code-editor :deep(.cm-scroller) {
  overflow: auto;
}

.formula-code-editor :deep(.cm-content) {
  padding: 6px 0;
  caret-color: #409eff;
}

.formula-code-editor :deep(.cm-line) {
  padding: 0 10px;
}

.formula-code-editor :deep(.cm-placeholder) {
  color: #a8abb2;
}

.formula-code-editor :deep(.cm-gutters) {
  background-color: #fafbfc;
  color: #c0c4cc;
  border-right: 1px solid #ebeef5;
}

.formula-code-editor :deep(.cm-activeLine) {
  background-color: #f5f7fa;
}

.formula-code-editor :deep(.cm-activeLineGutter) {
  background-color: #f0f2f5;
  color: #909399;
}

.formula-code-editor :deep(.cm-errorLine) {
  background-color: #fef0f0;
}

.formula-code-editor :deep(.cm-tooltip) {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background-color: #ffffff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  font-family: Consolas, Monaco, 'Courier New', monospace;
}
</style>
