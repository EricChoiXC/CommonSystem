package com.eric.base.sys.calendar.request;

import com.eric.base.common.source.annotation.DataSourceField;
import lombok.Data;

import java.util.Date;

/**
 * 日程列表查询条件
 *
 * @author Eric
 * @date 2026/8/21
 */
@Data
public class SysCalendarQuery {

    /** 标题（模糊查询） */
    @DataSourceField(label = "标题", ops = "LIKE", order = 1)
    private String fieldName;

    /** 类型：PERSONAL/TEAM */
    @DataSourceField(label = "类型", ops = "EQ", order = 2)
    private String fieldType;

    /** 是否接收筛选：ACCEPTED=已接收 REJECTED=已拒绝 UNCONFIRMED=未确认 */
    @DataSourceField(label = "接收状态", ops = "EQ", order = 3)
    private String fieldAccept;

    /** 日程开始时间-起始 */
    @DataSourceField(label = "开始时间-起", ops = "GE", order = 4)
    private Date fieldStartTimeStart;

    /** 日程开始时间-结束 */
    @DataSourceField(label = "开始时间-止", ops = "LE", order = 5)
    private Date fieldStartTimeEnd;

    /** 月份查询-年（不参与数据源条件定义，不标注） */
    private Integer year;

    /** 月份查询-月（不参与数据源条件定义，不标注） */
    private Integer month;

    /** 创建人（数据源通道专用：自定义数据源「我创建的」固定条件；原 list 接口不受理该字段） */
    @DataSourceField(label = "创建人", ops = "EQ", literalAllowed = false, order = 6)
    private String fieldCreatorId;
}