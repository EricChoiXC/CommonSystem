package com.eric.base.sys.form.enums;

import java.util.Arrays;

/**
 * 表单版本行状态
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 sys_form_version.field_status 列（枚举名与列 COMMENT 取值域逐字一致——
 *              VALIDATE 比对对象，数据库分册 §8.2 / §9.2）。草稿行与发布行同表承载：
 *              DRAFT（草稿，任意时刻每表单至多一行）──发布──▶ PUBLISHED（当前发布）──
 *              被新版本替代──▶ DEPRECATED（被新版本替代）。状态机与非法流转清单见
 *              功能设计 §5.2 / API 分册 §3.11，非法流转确定性拒绝
 *              （CS_FORM_VERSION_STATE_INVALID / CS_FORM_NO_DRAFT）：
 *              <ul>
 *              <li>无草稿行时直接发布（前置校验 P1）→ CS_FORM_NO_DRAFT；</li>
 *              <li>终态行（PUBLISHED / DEPRECATED）内容更新：契约层 /define/update
 *                  只写 DRAFT 行不可达 + 实现层防御性拒绝（双保险）→
 *                  CS_FORM_VERSION_STATE_INVALID；</li>
 *              <li>PUBLISHED → DRAFT：发布不可撤销，不设撤销发布接口（修正走「再编辑 → 新版本」）；</li>
 *              <li>DEPRECATED → PUBLISHED：DEPRECATED 不可逆，恢复仅经 /define/rollback
 *                  （回滚 = 重发布历史版本副本，产生新版本行而非指针回拨）。</li>
 *              </ul>
 */
public enum SysFormVersionStatus {

    /** 草稿（任意时刻每表单至多一行；field_version 为 NULL，发布时原地冻结回填） */
    DRAFT,
    /** 当前发布（内容不可变；被新版本发布 / 回滚替代时转 DEPRECATED） */
    PUBLISHED,
    /** 被新版本替代（终态不可逆；内容只读，恢复走回滚 = 重发布副本） */
    DEPRECATED;

    /**
     * 判断是否终态行（PUBLISHED / DEPRECATED）：终态行拒绝任何内容更新与状态回退
     * （功能设计 §5.1 原则 4 / API 分册 §3.11，实现层防御性拒绝落点）
     */
    public boolean isTerminal() {
        return this != DRAFT;
    }

    /**
     * 判断本状态是否允许流转到目标状态（行级状态机，功能设计 §5.2 / API 分册 §3.11）：
     * 仅 DRAFT → PUBLISHED（发布冻结）与 PUBLISHED → DEPRECATED（被新版本替代）合法；
     * PUBLISHED → DRAFT（发布不可撤销）与 DEPRECATED → 任意（不可逆）均非法。
     */
    public boolean canTransitionTo(SysFormVersionStatus target) {
        if (null == target) {
            return false;
        }
        if (this == DRAFT) {
            return target == PUBLISHED;
        }
        if (this == PUBLISHED) {
            return target == DEPRECATED;
        }
        return false;
    }

    /**
     * 解析状态文本，不区分大小写；非法返回 null（调用方按业务语义报错）
     */
    public static SysFormVersionStatus of(String value) {
        if (null == value || value.isEmpty()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(value))
                .findFirst().orElse(null);
    }
}
