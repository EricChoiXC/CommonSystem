package com.common.system.common.setting.model;

import com.common.system.common.base.model.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 公共配置信息模型
 *
 * @author Eric
 * @date 2026/8/19
 * @description 对应 common_setting；保存系统配置级信息，包含从公共配置到各模块专有配置在内的所有内容。
 *              配置以 (field_type, field_key) 为唯一键，field_default_value 来自各模块 application.yml 的
 *              SETTING 段（SETTING.${field_type}.${field_key}: ${field_default_value}），系统启动时增量维护；
 *              field_value 为当前生效的配置值，可由内部维护接口变更，变更后不受默认值同步覆盖。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CommonSetting extends BaseModel {

    /** 模块 */
    private String fieldModelName;

    /** 配置字段 */
    private String fieldKey;

    /** 配置值 */
    private String fieldValue;

    /** 类型 */
    private String fieldType;

    /** 更新时间 */
    private Date fieldUpdateTime;

    /** 默认值 */
    private String fieldDefaultValue;
}
