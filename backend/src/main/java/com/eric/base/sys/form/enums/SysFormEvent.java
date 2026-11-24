package com.eric.base.sys.form.enums;

import java.util.Arrays;

/**
 * 表单规则事件
 *
 * @author Eric
 * @date 2026/11/24
 * @description 规则 DSL 的 event 取值域（功能设计 §3.4 事件清单，一期定稿 8 个），
 *              同时是渲染器内置事件总线的事件全集（§4.3 事件统一分发——字段组件只 emit
 *              原始事件，不感知规则）。值即事件名（Schema JSON 中 event 键的取值）。
 *              消费范围：onChange 为主力事件（显隐 / 联动 / 动态校验）；onFocus / onBlur
 *              仅事件透传给物料组件、声明式规则一期不开放配置（防规则风暴）；onSubmit /
 *              onValidate 载荷约束同——仅支持 setRequired / setValidate / show / hide，
 *              禁止 setValue / clearValue（防提交竞态，{@link #isSubmitStage()} 判定）；
 *              onAdd / onDelete 为明细表行事件（triggerField = 明细表字段码，行上下文注入）。
 */
public enum SysFormEvent {

    /** 表单初始化 / 回显完成后（初始化联动） */
    ON_LOAD("onLoad"),
    /** 字段值变化（主力事件：显隐 / 联动 / 动态校验） */
    ON_CHANGE("onChange"),
    /** 字段获得焦点（仅事件透传，声明式规则一期不开放配置） */
    ON_FOCUS("onFocus"),
    /** 字段失去焦点（仅事件透传，声明式规则一期不开放配置） */
    ON_BLUR("onBlur"),
    /** 明细表新增行（行初始化动作，行上下文 row / rowIndex 注入） */
    ON_ADD("onAdd"),
    /** 明细表删除行（行后处理；合计刷新由统计行内置，无需规则） */
    ON_DELETE("onDelete"),
    /** 表单提交前（仅 setRequired / setValidate / show / hide，禁 setValue / clearValue） */
    ON_SUBMIT("onSubmit"),
    /** 表单校验阶段（载荷约束同 onSubmit；服务端校验链消费） */
    ON_VALIDATE("onValidate");

    /** 事件名（Schema DSL event 键取值） */
    private final String code;

    SysFormEvent(String code) {
        this.code = code;
    }

    /** 事件名（如 onLoad） */
    public String getCode() {
        return code;
    }

    /**
     * 是否提交 / 校验阶段事件（onSubmit / onValidate）：载荷约束同 §3.4——
     * 仅支持 setRequired / setValidate / show / hide，禁止 setValue / clearValue
     * （结构校验器按此拒绝，防提交竞态）
     */
    public boolean isSubmitStage() {
        return this == ON_SUBMIT || this == ON_VALIDATE;
    }

    /**
     * 是否一期开放声明式规则配置（§3.4 一期消费范围）：onFocus / onBlur 仅事件透传给
     * 物料组件，声明式规则一期不开放配置（焦点类联动场景极少，防规则风暴）——
     * 结构校验器按此拒绝 event=onFocus/onBlur 的规则
     */
    public boolean isRuleConfigurable() {
        return this != ON_FOCUS && this != ON_BLUR;
    }

    /**
     * 解析事件名（code，不区分大小写，兼容枚举名匹配）；非法返回 null
     * （结构校验器按非法事件报 CS_FORM_SCHEMA_INVALID）
     */
    public static SysFormEvent of(String value) {
        if (null == value || value.isEmpty()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(event -> event.code.equalsIgnoreCase(value) || event.name().equalsIgnoreCase(value))
                .findFirst().orElse(null);
    }
}
