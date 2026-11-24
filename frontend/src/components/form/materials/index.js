/**
 * 自定义表单物料库聚合注册入口（F15-FE-002）
 *
 * 统一执行 R03 一期组件全集 18 个物料的注册（数据类 12 + 内容类 4 + 布局类 2，功能
 * 详细设计 §1.3）；控制类 permission 仅协议位——不注册物料、物料栏不展示（§1.2/§1.3，
 * v1.8 BPM 接入时按同一 registerComponent 协议补注册即可）。
 *
 * 渲染器（F15-FE-005）与设计器（F15-FE-008）在入口处 import 本文件一次即完成注册；
 * registerAllMaterials 幂等（已注册的 type 跳过），防 HMR / 多入口重复执行触发
 * registerComponent 的重复 type fail-fast。
 *
 * 运行时组件（material.component）按 F15-FE-003（基础数据七件）/ F15-FE-004（业务数据
 * 四件）/ F15-FE-007（内容与明细）/ F15-FE-010（级联）分批注册，cascade 等未接入 type
 * 的 component 仍为 null；渲染器对 component 为 null 的 type 按未知 type 兜底占位
 * （ADR-10，不阻断整表渲染）。
 */

import { componentRegistry, registerComponent } from '../registry.js'
import DATA_MATERIALS from './dataMaterials.js'
import CONTENT_MATERIALS from './contentMaterials.js'
import LAYOUT_MATERIALS from './layoutMaterials.js'

/** 一期全部物料（18 个，§1.3 顺序） */
export const FORM_MATERIALS = [...DATA_MATERIALS, ...CONTENT_MATERIALS, ...LAYOUT_MATERIALS]

/**
 * 注册全部物料（幂等：已注册 type 跳过；新物料首次注册仍走 registerComponent 的
 * fail-fast 完整性断言）。
 */
export function registerAllMaterials() {
  for (const material of FORM_MATERIALS) {
    if (!componentRegistry.has(material.type)) {
      registerComponent(material)
    }
  }
}

// 模块加载即注册（渲染器 / 设计器 / 冒烟脚本 import 本文件即可用）
registerAllMaterials()
