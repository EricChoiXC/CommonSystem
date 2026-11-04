package com.common.system.sys.att.model;

import com.common.system.common.base.model.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 附件操作日志模型
 *
 * @author Eric
 * @date 2026/8/7
 * @description 对应 sys_att_operation；查询时 LEFT JOIN sys_org_element
 *              带出操作人姓名 fieldOperatorName 供日志列表展示。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysAttOperation extends BaseModel {

    /** 操作附件id */
    private String fieldAttId;

    /** 操作人id */
    private String fieldUserId;

    /** 操作时间 */
    private Date fieldOperateTime;

    /** 操作类型: 1=上传 2=修改 3=删除 4=后台删除 5=下载 6=浏览 */
    private String fieldOperateType;

    /** 操作备注 */
    private String fieldRemark;

    /** 操作人姓名(来自 sys_org_element) */
    private String fieldOperatorName;
}
