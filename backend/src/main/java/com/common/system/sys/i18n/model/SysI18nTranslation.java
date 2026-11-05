package com.common.system.sys.i18n.model;

import com.common.system.common.base.model.BaseModel;
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
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysI18nTranslation extends BaseModel {

    /** 关键字（sys_i18n_key.field_key） */
    private String fieldKey;

    /** 语言码（sys_i18n_language.field_code，如 zh_CN、en_US） */
    private String fieldLanguage;

    /** 翻译文本 */
    private String fieldMessage;
}
