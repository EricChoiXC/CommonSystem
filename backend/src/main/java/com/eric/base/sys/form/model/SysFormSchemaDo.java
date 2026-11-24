package com.eric.base.sys.form.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 表单 Schema 协议文档对象
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 sys_form_version.field_schema_json 的结构化映射（功能设计 §2.1 Schema
 *              协议定稿，任务 F15-BE-001），由 Jackson 序列化 / 反序列化——存储态字符串 ↔
 *              协议对象互转一律经 Jackson，禁止手工拼串（双形态承载先例
 *              sys_number_rule.field_rule）。R01 裁决定稿 JSON Schema 序列化格式（ADR-01）。
 *              发布快照单列完整承载组件树（含容器 children 递归）+ 组件属性 + 字段级权限
 *              元数据（props.permission）+ 规则（字段对象 rules[] 内嵌——ADR-06，不设独立
 *              rules 列 / 表）。向后兼容：未知顶层键反序列化忽略（ignoreUnknown）、
 *              null 属性不输出（NON_NULL），字段增删不破坏旧版本 Schema 解析。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "表单 Schema 协议对象：顶层键 formId/formCode/formName/version/fields[]，"
        + "规则内嵌于字段对象 rules[]（ADR-06）、字段级权限内嵌 props.permission；"
        + "存储于 sys_form_version.field_schema_json（JSON 列双形态承载）")
public class SysFormSchemaDo {

    /** 表单 id（sys_form.field_id，IDUtil.initID 生成；导入场景允许缺省——导入即新表单） */
    @Schema(description = "表单 id（sys_form.field_id）；导入场景允许缺省（导入即新表单）")
    private String formId;

    /** 表单编码（全局唯一，保存期查重，先例 formula 查重） */
    @Schema(description = "表单编码，全局唯一（保存期查重）")
    private String formCode;

    /** 表单名称 */
    @Schema(description = "表单名称")
    private String formName;

    /** 发布版本号（整数单调序号，仅发布动作分配；草稿导出为 null） */
    @Schema(description = "发布版本号（整数，仅发布动作分配）；草稿导出为 null")
    private Integer version;

    /** 字段对象数组（顶层字段 = 嵌套深度 1；group/grid/detailTable 经 children 递归） */
    @Schema(description = "字段对象数组（顶层字段 = 嵌套深度 1；容器组件经 children 递归，"
            + "grid 的 children 为列对象数组 { span, fields[] }，detailTable 的 children 为行内字段对象数组）")
    private List<SysFormFieldDo> fields;
}
