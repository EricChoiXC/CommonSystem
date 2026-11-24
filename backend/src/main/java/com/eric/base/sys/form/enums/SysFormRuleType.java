package com.eric.base.sys.form.enums;

/**
 * 表单规则分类标记
 *
 * @author Eric
 * @date 2026/11/24
 * @description 按功能设计 §3.1 四类规则登记的分类标记（显隐 / 联动 / 动态校验 / 自定义
 *              响应占位）。注意：§3.1 统一 DSL 的定稿结构为
 *              { ruleId, event, triggerField, condition, actions[] }——【不含 ruleType 键】，
 *              规则类型由动作组合隐式表达（show/hide → 显隐，setValue/clearValue → 联动，
 *              setRequired/setValidate → 动态校验）。故本枚举一期不随 Schema 序列化入库、
 *              不参与 DSL 解析，仅作服务端分类辅助（如校验结果归类、管理端统计筛选），
 *              是否入 Schema 由设计定稿为准；一期规则协议以 §3.1 DSL 键为准。
 */
public enum SysFormRuleType {

    /** 显隐规则：show / hide 动作（隐藏不删值，ADR-08 渲染层口径） */
    SHOW_HIDE,
    /** 联动规则：setValue / clearValue 动作（赋值为字面量或 ${字段码} 引用） */
    LINKAGE,
    /** 动态校验规则：setRequired / setValidate 动作（与 props.required 静态基础必填在服务端合并裁决） */
    DYNAMIC_VALIDATION,
    /** 自定义响应占位：R02 裁决一期不落地 JS 执行引擎（仅预留协议位，二期 GraalJS/受限解释器，功能设计 §3.7） */
    RESPONSE
}
