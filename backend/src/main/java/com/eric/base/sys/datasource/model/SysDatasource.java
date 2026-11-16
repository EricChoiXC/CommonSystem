package com.eric.base.sys.datasource.model;

import com.eric.base.common.base.model.MainModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.DataSource;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据源实体（自定义数据源）
 *
 * @author Eric
 * @date 2026/11/13
 * @description 对应 sys_datasource：自定义数据源配置主信息。默认数据源由代码 Provider
 *              注册不入库（见 SysDatasourceRegistry）。fieldTarget 保存后锁定不可改
 *              （改目标会使既有条件/字段白名单悬空，对齐 sys_number_rule.field_model_id D3 先例）；
 *              fieldConditions / fieldFields 双形态承载（先例 sys_number_rule.field_rule）：
 *              存储态与列表响应为原文 JSON 字符串，load 响应由 Service 解析为对象返回，
 *              save/update 请求体传配置对象，禁止手工拼串。公共字段继承 MainModel。
 *              <p>2026/11/16 F19-BE-005 试点标注：类级 @TableSource / @DataSource + 六个
 *              业务列 @ColumnField（field_code / field_name 重声明遮蔽 MainModel 继承列，
 *              D5 规则），列定义与 V1_01_046__sys_datasource_schema.sql 逐列一致；
 *              fieldTargetName 为不入库的展示字段，不标注；公共列（field_id 主键 +
 *              审计五列）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "数据源实体：对应 sys_datasource；fieldConditions（固定条件对象列表）与 "
        + "fieldFields（返回字段字符串数组）在 load 响应与 save/update 请求体中为解析后的对象，"
        + "列表响应中为原文 JSON 字符串")
@TableSource(name = "sys_datasource", comment = "数据源表（自定义数据源配置）",
        indexes = {
                @TableIndex(name = "uk_datasource_code", columns = "field_code", unique = true),
                @TableIndex(name = "idx_datasource_target", columns = "field_target")})
@DataSource(name = "datasource")
public class SysDatasource extends MainModel {

    /** 重声明：数据源编码，全局唯一（与默认数据源共用命名空间），Service 层查重（D5 遮蔽 MainModel.fieldCode） */
    @ColumnField(name = "field_code", comment = "数据源编码，全局唯一（与默认数据源共用命名空间），Service 层查重",
            type = ColumnType.STRING, length = 100)
    @Schema(description = "数据源编码，全局唯一（与默认数据源共用命名空间），Service 层查重")
    private String fieldCode;

    /** 重声明：数据源名称（D5 遮蔽 MainModel.fieldName） */
    @ColumnField(name = "field_name", comment = "数据源名称", type = ColumnType.STRING, length = 200)
    @Schema(description = "数据源名称")
    private String fieldName;

    /** 目标查询能力编码（Provider code，如 calendar.list），保存后锁定不可改 */
    @ColumnField(name = "field_target", comment = "目标查询能力编码（Provider code，如 calendar.list），保存后锁定",
            type = ColumnType.STRING, length = 100)
    @Schema(description = "目标查询能力编码（Provider code，如 calendar.list），保存后锁定不可改")
    private String fieldTarget;

    /** 目标能力名称（下拉带出，仅展示，不入库） */
    @Schema(description = "目标能力名称（由 targets 下拉带出，编辑时不受理变更）")
    private String fieldTargetName;

    /**
     * 固定条件 JSON（对应列 field_conditions JSON）：[{key,op,value}]。
     * 双形态承载：存储态与列表响应为原文 JSON 字符串（列表页不解析）；
     * load 响应由 Service 解析为对象数组返回（前端直接消费）；
     * save/update 请求体传配置对象，Service 校验后经 Jackson 序列化为字符串落库。
     * value 支持 ${CURRENT_USER_ID} 等运行时变量；NULL=无固定条件。禁止手工拼串。
     */
    @ColumnField(name = "field_conditions", comment = "固定条件 JSON：[{key,op,value}]，value 支持 ${CURRENT_USER_ID} 等运行时变量",
            type = ColumnType.JSON, nullable = true)
    @Schema(description = "固定条件 JSON：[{key,op,value}]，value 支持 ${CURRENT_USER_ID} 等运行时变量；"
            + "存储态 / 列表响应为原文 JSON 字符串，load 响应为解析后的对象数组，"
            + "save/update 请求体传配置对象；NULL=无固定条件")
    private Object fieldConditions;

    /**
     * 返回字段白名单 JSON（对应列 field_fields JSON）：字段名字符串数组。
     * 双形态承载同 fieldConditions；NULL=目标能力默认全字段。禁止手工拼串。
     */
    @ColumnField(name = "field_fields", comment = "返回字段白名单 JSON：字符串数组，NULL=目标能力默认全字段",
            type = ColumnType.JSON, nullable = true)
    @Schema(description = "返回字段白名单 JSON：字段名字符串数组；存储态 / 列表响应为原文 JSON 字符串，"
            + "load 响应为解析后的字符串数组，save/update 请求体传配置对象；"
            + "NULL=目标能力默认全字段")
    private Object fieldFields;

    /** 备注 */
    @ColumnField(name = "field_remark", comment = "备注", type = ColumnType.STRING, length = 500, nullable = true)
    @Schema(description = "备注")
    private String fieldRemark;
}
