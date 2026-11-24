package com.eric.base.sys.form.enums;

import java.util.Arrays;

/**
 * 字段级权限元数据取值
 *
 * @author Eric
 * @date 2026/11/24
 * @description Schema 内字段级权限元数据 props.permission 的取值域（功能设计 §2.1 约定项 /
 *              数据库分册 §8.2 附注——Schema 内取值域、非数据库列）：editable（可编辑）/
 *              readonly（只读）/ hidden（隐藏）。随 Schema 存储并即期生效：一期提交 / 读取
 *              裁剪即执行（§3.6 / §6.6——hidden 字段无论渲染态一律裁剪不落库不下发，
 *              readonly 编辑提交值变更拒绝）；v1.8 BPM 消费形态为流程节点经
 *              fieldPermissions 外部覆盖注入（外部覆盖优先于 Schema 内静态值，§4.2）。
 *              与规则动态显隐严格区分：permission=hidden 是权限裁剪（值不落库），
 *              show/hide 是渲染可见性切换（隐藏不删值，ADR-08）。
 */
public enum SysFieldPermission {

    /** 可编辑（默认） */
    EDITABLE("editable"),
    /** 只读（组件置灰；编辑提交值变更拒绝——一期新增 save 无基线可比对，挂二期实例更新接口） */
    READONLY("readonly"),
    /** 隐藏（不渲染；提交裁剪不落库、读取不下发——后端不信任前端） */
    HIDDEN("hidden");

    /** 权限取值（Schema props.permission 键取值） */
    private final String code;

    SysFieldPermission(String code) {
        this.code = code;
    }

    /** 权限取值（如 editable） */
    public String getCode() {
        return code;
    }

    /**
     * 解析权限取值（code，不区分大小写，兼容枚举名匹配）；非法返回 null
     * （结构校验器按非法取值报 CS_FORM_SCHEMA_INVALID）
     */
    public static SysFieldPermission of(String value) {
        if (null == value || value.isEmpty()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(permission -> permission.code.equalsIgnoreCase(value) || permission.name().equalsIgnoreCase(value))
                .findFirst().orElse(null);
    }
}
