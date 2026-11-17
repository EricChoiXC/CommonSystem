package com.eric.base.sys.att.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
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
 *              <p>2026/11/17 F19-DB-001 存量标注批一：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField，列定义与 dev 库 information_schema 逐列一致；
 *              fieldOperatorName 为 JOIN 带出的展示字段，不标注；公共列（field_id 主键）
 *              由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_att_operation", comment = "附件操作日志表",
        indexes = {
                @TableIndex(name = "idx_att_operation_att", columns = "field_att_id")})
public class SysAttOperation extends BaseModel {

    /** 操作附件id */
    @ColumnField(name = "field_att_id", comment = "操作附件id", type = ColumnType.STRING, length = 32)
    private String fieldAttId;

    /** 操作人id */
    @ColumnField(name = "field_user_id", comment = "操作人id", type = ColumnType.STRING, length = 32, nullable = true)
    private String fieldUserId;

    /** 操作时间 */
    @ColumnField(name = "field_operate_time", comment = "操作时间",
            type = ColumnType.TIMESTAMP, defaultFunction = DefaultFunction.CURRENT_TIMESTAMP)
    private Date fieldOperateTime;

    /** 操作类型: 1=上传 2=修改 3=删除 4=后台删除 5=下载 6=浏览 */
    @ColumnField(name = "field_operate_type", comment = "操作类型: 1=上传 2=修改 3=删除 4=后台删除 5=下载 6=浏览",
            type = ColumnType.STRING, length = 1)
    private String fieldOperateType;

    /** 操作备注 */
    @ColumnField(name = "field_remark", comment = "操作备注", type = ColumnType.STRING, length = 200, nullable = true)
    private String fieldRemark;

    /** 操作人姓名(来自 sys_org_element，展示字段不入库) */
    private String fieldOperatorName;
}
