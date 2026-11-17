package com.eric.base.sys.i18n.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 多语言-翻译模型
 *
 * @author Eric
 * @date 2026/11/5
 * @description 对应 sys_i18n_translation（V1_01_040）：翻译文本 = (field_key, field_language)，
 *              业务唯一由代码层保证、不建唯一约束（便于重置删插）。
 *              field_update_time 是「人工编辑标记」：非空 = 管理员改过（启动/部署同步不覆盖），
 *              空 = 来自资源文件（可被覆盖）——整个覆盖机制围绕该字段。
 *              本表为流水语义（只增改文本不迁移状态），与 sys_log_operation /
 *              sys_task_history 先例一致，不设 field_enable / field_creator_id 等审计列，
 *              不继承 MainModel。列清单与附录 A DDL 保持一致。
 *              <p>2026/11/17 F19-DB-003 存量标注批三：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField（field_update_time 为表中存在的部分审计列，按业务列标注，
 *              datetime 类型保持 DATETIME 语义不做转换），列定义与 dev 库 information_schema
 *              逐列一致；公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_i18n_translation", comment = "多语言-翻译表",
        indexes = {
                @TableIndex(name = "idx_i18n_translation_key_language", columns = {"field_key", "field_language"})})
public class SysI18nTranslation extends BaseModel {

    /** 关键字（sys_i18n_key.field_key） */
    @ColumnField(name = "field_key", comment = "关键字（sys_i18n_key.field_key）", type = ColumnType.STRING, length = 200)
    private String fieldKey;

    /** 语言码（sys_i18n_language.field_code，如 zh_CN、en_US） */
    @ColumnField(name = "field_language", comment = "语言码（sys_i18n_language.field_code）",
            type = ColumnType.STRING, length = 20)
    private String fieldLanguage;

    /** 翻译文本 */
    @ColumnField(name = "field_message", comment = "翻译文本", type = ColumnType.TEXT, nullable = true)
    private String fieldMessage;

    /**
     * 人工编辑标记（field_update_time）：非空 = 人工已编辑（启动/部署同步不覆盖），
     * 空 = 来自资源文件（可被覆盖）；与 MainModel.fieldUpdateTime 语义不同，本表无 MainModel 继承。
     * （表中存在的部分审计列，按业务列标注；datetime 保持 DATETIME 语义）
     */
    @ColumnField(name = "field_update_time",
            comment = "更新时间（非空=人工已编辑，启动/部署同步不覆盖；为空=资源文件导入）",
            type = ColumnType.DATETIME, nullable = true)
    private Date fieldUpdateTime;
}
