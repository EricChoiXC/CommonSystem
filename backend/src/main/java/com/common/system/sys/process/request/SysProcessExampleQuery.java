package com.common.system.sys.process.request;

import lombok.Data;

import java.util.Date;

/**
 * 流程实例查询条件
 *
 * @author Eric
 * @date 2026/8/23
 * @description 流程实例列表页的查询条件：流程名称（模板名）/流程编号模糊、
 *              所属模块固定、发起人名称模糊、创建时间区间与流程状态筛选。
 */
@Data
public class SysProcessExampleQuery {

    /** 流程名称（模板名称）模糊匹配 */
    private String fieldName;

    /** 流程编号模糊匹配 */
    private String fieldCode;

    /** 所属模块（process_template 二级配置项，固定条件） */
    private String fieldModelName;

    /** 发起人名称模糊匹配 */
    private String fieldCreatorName;

    /** 创建时间起（含） */
    private Date fieldCreateTimeBegin;

    /** 创建时间止（含） */
    private Date fieldCreateTimeEnd;

    /** 流程状态：ABANDON / DRAFT / PROCESSING / PAUSE / FINISH / RETURN */
    private String fieldStatus;
}
