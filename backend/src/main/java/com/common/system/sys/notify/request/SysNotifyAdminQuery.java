package com.common.system.sys.notify.request;

import lombok.Data;

import java.util.Date;

/**
 * 后台待办列表筛选条件
 *
 * @author Eric
 * @date 2026/8/12
 * @description 对应「待办管理」页面的筛选区：待办类型（默认待办，无"全部"）、
 *              来源模块、标题、创建时间范围（限年月日）。
 */
@Data
public class SysNotifyAdminQuery {

    /** 待办类型: 1=待办 2=待阅（默认待办，不可"全部"） */
    private String fieldNotifyType;

    /** 来源模块 */
    private String fieldModelName;

    /** 标题 */
    private String fieldName;

    /** 创建时间起（含） */
    private Date fieldCreateTimeStart;

    /** 创建时间止（含） */
    private Date fieldCreateTimeEnd;
}
