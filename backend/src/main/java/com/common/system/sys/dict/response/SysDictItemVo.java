package com.common.system.sys.dict.response;

import lombok.Data;

/**
 * 码值项视图对象
 *
 * @author Eric
 * @date 2026/9/2
 * @description 「通过类型编号获取码值项」接口的返回对象：仅返回使用方关心的
 *              field_value / field_name / field_enable / field_system，不携带类型明细。
 */
@Data
public class SysDictItemVo {

    /** id */
    private String fieldId;

    /** 类型id */
    private String fieldTypeId;

    /** 码值 */
    private String fieldValue;

    /** 显示值 */
    private String fieldName;

    /** 是否启用 */
    private Boolean fieldEnable;

    /** 是否系统码值 */
    private Boolean fieldSystem;
}
