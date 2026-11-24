package com.eric.base.sys.form.model;

import com.eric.base.common.base.model.MainModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 表单版本实体
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 sys_form_version：表单的不可变版本行（草稿行与发布行同表承载，
 *              field_status 区分 DRAFT/PUBLISHED/DEPRECATED）。继承 MainModel（分册 §4.3
 *              基类归属裁决）：草稿行在发布前经 /define/update 原地反复重写、发布动作对
 *              草稿行原地冻结回填（version/status/published_at），存在确定性更新语义，
 *              不满足 AGENTS §4「流水只增不改」前提；发布后内容不可变由应用层状态机
 *              拒绝（终态行拒绝内容更新，契约层只写 DRAFT 行 + 实现层防御性拒绝双保险）。
 *              field_enable 为 MainModel 内建列，版本行无启停用业务语义，恒 b'1' 不参与
 *              业务判定。field_version 发布时回填（草稿行为 NULL，不参与
 *              uk_form_version_form_version 唯一约束——并发发布防护见分册 §5.2.4）；
 *              field_schema_json 为 JSON 列双形态承载（先例 sys_number_rule.field_rule），
 *              禁止手工拼串。公共列（field_id 主键 + 审计五列）由引擎公共列模板内建，
 *              无需标注。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "表单版本实体：对应 sys_form_version；fieldSchemaJson 存储态为原文 JSON 字符串"
        + "（SysFormSchemaDo 协议，顶层键 formId/formCode/formName/version/fields[]，规则内嵌"
        + " rules[]、字段级权限内嵌 props.permission），load 响应由 Service 解析为对象返回")
@TableSource(name = "sys_form_version", comment = "表单版本表（不可变版本快照）",
        indexes = {
                @TableIndex(name = "idx_form_version_form", columns = "field_form_id"),
                @TableIndex(name = "uk_form_version_form_version",
                        columns = {"field_form_id", "field_version"}, unique = true)})
public class SysFormVersion extends MainModel {

    /** 表单id（sys_form.field_id） */
    @ColumnField(name = "field_form_id", comment = "表单id", type = ColumnType.STRING, length = 32)
    private String fieldFormId;

    /** 整数版本号（发布动作回填，单调递增 1,2,3…；草稿行为 NULL） */
    @ColumnField(name = "field_version", comment = "整数版本号(发布动作回填,单调递增;草稿行为NULL)",
            type = ColumnType.INT, nullable = true)
    private Integer fieldVersion;

    /** 表单 Schema 快照 JSON（组件树 + 规则内嵌，发布后不可变；结构见功能设计 §2） */
    @ColumnField(name = "field_schema_json", comment = "表单Schema快照JSON(组件树+规则内嵌,发布后不可变)",
            type = ColumnType.JSON)
    @Schema(description = "表单 Schema 快照 JSON：存储态为原文 JSON 字符串，load 响应为解析后的"
            + " SysFormSchemaDo 对象；发布后该行内容不可变")
    private Object fieldSchemaJson;

    /** 版本状态：DRAFT=草稿(至多一行) PUBLISHED=当前发布 DEPRECATED=被新版本替代 */
    @ColumnField(name = "field_status",
            comment = "版本状态: DRAFT=草稿(至多一行) PUBLISHED=当前发布 DEPRECATED=被新版本替代",
            type = ColumnType.STRING, length = 20, defaultValue = "DRAFT")
    @Schema(description = "版本状态：DRAFT=草稿（任意时刻每表单至多一行）PUBLISHED=当前发布 "
            + "DEPRECATED=被新版本替代；非法流转拒绝见 API 分册 §3.11")
    private String fieldStatus;

    /** 发布/回滚时刻（发布冻结或回滚动作回填；草稿行为 NULL） */
    @ColumnField(name = "field_published_at", comment = "发布/回滚时刻(草稿行为NULL)",
            type = ColumnType.TIMESTAMP, nullable = true)
    private Date fieldPublishedAt;
}
