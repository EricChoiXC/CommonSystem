package com.eric.base.sys.process.response;

import lombok.Data;

import java.util.Date;

/**
 * 流程公共配置视图对象
 *
 * @author Eric
 * @date 2026/8/21
 * @description 「流程公共配置」页面的列表行数据：以 common_setting 为主体，
 *              附加流程模块维护的中文描述（页面仅显示中文描述与值）。
 */
@Data
public class SysProcessSettingVo {

    /** 配置项id（common_setting.field_id） */
    private String fieldId;

    /** 类型（process / notify） */
    private String fieldType;

    /** 配置字段 */
    private String fieldKey;

    /** 中文描述（来自流程模块配置元信息，非表字段） */
    private String fieldDesc;

    /** 配置值（可编辑） */
    private String fieldValue;

    /** 默认值（重置目标） */
    private String fieldDefaultValue;

    /** 更新时间 */
    private Date fieldUpdateTime;
}
