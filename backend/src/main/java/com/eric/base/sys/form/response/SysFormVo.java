package com.eric.base.sys.form.response;

import com.eric.base.sys.form.model.SysFormSchemaDo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 表单定义响应
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 API 分册 §3.2 /list 行字段与 §3.4 /load 详情契约（F15-BE-002）：两者同构，
 *              差异为两个可空 Schema 载荷——列表行不携带 Schema 内容（体积大），仅经 /load 下发；
 *              行字段不使用 MainModel 继承列名（主档业务列为 fieldFormCode / fieldFormName，
 *              与 MainModel 的 fieldCode / fieldName 不同名），故不继承基类、显式声明全部行字段
 *              与分册字段表逐字对齐。fieldCurrentVersionId 指向最新发布版本行（从未发布为 null，
 *              停用不改变语义）；fieldCurrentVersion 为当前发布版本号；hasDraft 供管理页
 *              「设计 / 发布」按钮预判。
 */
@Data
@Schema(description = "表单定义响应：/list 行字段与 /load 详情同构，差异为 draftSchema / publishedSchema"
        + "两个可空 Schema 载荷（列表行不携带 Schema 内容）")
public class SysFormVo {

    /** 表单主档 ID（即 Schema 协议头 formId 同值） */
    @Schema(description = "表单主档 ID（即 Schema 协议头 formId 同值）")
    private String fieldId;

    /** 表单编码（全局唯一） */
    @Schema(description = "表单编码，全局唯一")
    private String fieldFormCode;

    /** 表单名称 */
    @Schema(description = "表单名称")
    private String fieldFormName;

    /** 主档状态：DRAFT（从未发布）/ PUBLISHED（存在当前发布版本） */
    @Schema(description = "主档状态：DRAFT（从未发布）/ PUBLISHED（存在当前发布版本）；"
            + "启停用走 fieldEnable（独立字段）")
    private String fieldStatus;

    /** 启用状态（停用关闭填写入口与数据提交，定义面与数据查询保留） */
    @Schema(description = "启用状态：false 停用——填写入口与数据提交关闭，定义面与数据查询保留")
    private Boolean fieldEnable;

    /** 当前发布版本行 ID；从未发布为 null */
    @Schema(description = "当前发布版本行 ID；始终指向最新发布版本（回滚后指向回滚产生的新版本行），"
            + "从未发布为 null")
    private String fieldCurrentVersionId;

    /** 当前发布版本号；从未发布为 null */
    @Schema(description = "当前发布版本号；从未发布为 null")
    private Integer fieldCurrentVersion;

    /** 是否存在 DRAFT 版本行（管理页「设计 / 发布」按钮预判） */
    @Schema(description = "是否存在 DRAFT 版本行（管理页「设计 / 发布」按钮预判）")
    private Boolean hasDraft;

    /** DRAFT 版本行内容（仅 /load 下发；无草稿行为 null） */
    @Schema(description = "DRAFT 版本行内容（仅 /load 下发；已发布未再编辑为 null；"
            + "协议头 version 恒为 null——版本号仅发布动作分配）")
    private SysFormSchemaDo draftSchema;

    /** 当前发布版本行内容（仅 /load 下发；从未发布为 null） */
    @Schema(description = "field_current_version_id 指向的当前发布版本行内容（仅 /load 下发；"
            + "从未发布为 null；协议头 version 回填发布版本号）")
    private SysFormSchemaDo publishedSchema;

    /** 创建者 ID */
    @Schema(description = "创建者 ID")
    private String fieldCreatorId;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private Date fieldCreateTime;

    /** 修改者 ID */
    @Schema(description = "修改者 ID")
    private String fieldUpdaterId;

    /** 修改时间 */
    @Schema(description = "修改时间")
    private Date fieldUpdateTime;
}
