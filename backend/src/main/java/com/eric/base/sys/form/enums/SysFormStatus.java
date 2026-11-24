package com.eric.base.sys.form.enums;

import java.util.Arrays;

/**
 * 表单主档状态
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 sys_form.field_status 列（枚举名与列 COMMENT 取值域逐字一致——
 *              VALIDATE 比对对象，数据库分册 §8.2 / §9.2）：DRAFT（从未发布）──首次发布──▶
 *              PUBLISHED（存在当前发布版本），仅此单向流转、无撤销发布语义（修正走
 *              「再编辑 → 新版本」，功能设计 §5.2）。启停用独立走 field_enable
 *              （updateEnable 只更新自身一行，停用不改变当前版本指针语义）。
 */
public enum SysFormStatus {

    /** 从未发布（field_current_version_id 为空，填写与提交入口不可用） */
    DRAFT,
    /** 存在当前发布版本 */
    PUBLISHED;

    /**
     * 解析状态文本，不区分大小写；非法返回 null（调用方按业务语义报错）
     */
    public static SysFormStatus of(String value) {
        if (null == value || value.isEmpty()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(value))
                .findFirst().orElse(null);
    }
}
