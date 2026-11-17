package com.eric.base.sys.calendar.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 日程人员表模型
 *
 * @author Eric
 * @date 2026/8/21
 * @description 对应 sys_calendar_persons
 *              <p>2026/11/17 F19-DB-003 存量标注批三：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField，列定义与 dev 库 information_schema 逐列一致；
 *              公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_calendar_persons", comment = "日程人员表",
        indexes = {
                @TableIndex(name = "idx_calendar_id", columns = "field_calendar_id"),
                @TableIndex(name = "idx_person_id", columns = "field_person_id")})
public class SysCalendarPersons extends BaseModel {

    /** 日程id */
    @ColumnField(name = "field_calendar_id", comment = "日程id", type = ColumnType.STRING, length = 32)
    private String fieldCalendarId;

    /** 人员id */
    @ColumnField(name = "field_person_id", comment = "人员id", type = ColumnType.STRING, length = 32)
    private String fieldPersonId;

    /** 是否接收（可为空，NULL=未确认 1=接收 0=拒绝） */
    @ColumnField(name = "field_accept", comment = "是否接收（可为空，NULL=未确认 1=接收 0=拒绝）",
            type = ColumnType.BOOLEAN, nullable = true)
    private Boolean fieldAccept;
}