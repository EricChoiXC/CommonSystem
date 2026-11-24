<template>
  <!--
    静态图片展示（F15-FE-007，§1.3 image 行：src 图片地址 / width 展示宽度 px；
    不承载数据）。el-image 展示，加载失败经 error 插槽占位；未配置地址展示占位块
    （design 形态画布可识别占位范围）。
  -->
  <el-image
    v-if="hasSrc"
    class="image-widget"
    :src="src"
    :style="imageStyle"
    fit="scale-down"
    :preview-src-list="previewDisabled ? undefined : [src]"
    :hide-on-click-modal="true"
  >
    <template #placeholder>
      <div class="image-widget-holder" :style="holderStyle">图片加载中…</div>
    </template>
    <template #error>
      <div class="image-widget-holder image-widget-error" :style="holderStyle">图片加载失败</div>
    </template>
  </el-image>
  <div v-else class="image-widget-holder image-widget-empty" :style="holderStyle">
    {{ disabled ? '图片（未配置地址）' : '未配置图片地址' }}
  </div>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 图片 widget（type=image，功能详细设计 §1.3 内容类 image 行）。
 *
 * 统一契约（渲染器 F15-FE-005 按此消费，§4.3）：
 * - props：modelValue（v-model 显式声明——内容类不承载数据、无数据绑定语义，保留声明
 *   对齐统一契约、不触发回写）+ 扩展属性项 src/width（像素，留空自适应，UI 分册 §4.5.2）
 *   + 统一控制位 disabled/readonly（静态图片无交互，控制位仅契约保留；design 形态经
 *   disabled 识别展示未配置占位）；
 * - emits：update:modelValue / change / focus / blur（无交互时机，保留声明、不触发）；
 * - 值形态：不进数据载荷（物料 dataType=null，§1.3）；
 * - 固定属性项由渲染器层承载，本组件只渲染内容本体，不感知规则（架构原则 2）。
 */
const props = defineProps({
  /** 字段值（内容类不承载数据，保留声明对齐统一契约） */
  modelValue: { type: null, default: null },
  /** 图片地址（空 = 未配置占位） */
  src: { type: String, default: '' },
  /** 展示宽度（px；null/undefined 留空自适应容器宽度） */
  width: { type: [Number, String], default: null },
  /** 统一控制位：禁用（design 形态经此识别；点击预览禁用） */
  disabled: { type: Boolean, default: false },
  /** 统一控制位：只读（静态图片无交互，契约保留） */
  readonly: { type: Boolean, default: false },
})

// 统一契约四事件保留声明；静态图片无交互时机，不触发
const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur'])
void emit

const hasSrc = computed(() => props.src !== null && props.src !== undefined && String(props.src).trim() !== '')

/** 展示宽度：width 配置为权威（px）；未配置自适应 100% 容器宽 */
const imageStyle = computed(() => ({
  width: props.width !== null && props.width !== undefined && props.width !== '' ? `${Number(props.width)}px` : '100%',
}))

const holderStyle = computed(() => ({
  width: props.width !== null && props.width !== undefined && props.width !== '' ? `${Number(props.width)}px` : '100%',
}))

/** design 形态禁用点击预览（画布占位不需要大图预览交互） */
const previewDisabled = computed(() => props.disabled || props.readonly)
</script>

<style scoped>
.image-widget {
  display: block;
  max-width: 100%;
  border-radius: 4px;
}

.image-widget :deep(img) {
  max-width: 100%;
}

.image-widget-holder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 96px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-placeholder);
  font-size: 13px;
  box-sizing: border-box;
}

.image-widget-error {
  color: var(--el-color-danger);
}

.image-widget-empty {
  color: var(--el-text-color-placeholder);
}
</style>
