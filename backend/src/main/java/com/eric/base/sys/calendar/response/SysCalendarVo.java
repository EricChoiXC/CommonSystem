package com.eric.base.sys.calendar.response;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.DataSourceField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;

/**
 * 日程列表/详情响应对象
 *
 * @author Eric
 * @date 2026/8/21
 * @description 数据源返回字段经 {@link DataSourceField} 注解声明（A8，方案 §2.4）：
 *              公共字段 fieldId 继承自 BaseModel 无法标注，由扫描器内建补齐固定最前；
 *              persons 为嵌套接收人列表，不参与数据源字段定义，不标注。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysCalendarVo extends BaseModel {

    /** 创建人id */
    @DataSourceField(label = "创建人id", order = 1)
    private String fieldCreatorId;

    /** 创建时间 */
    @DataSourceField(label = "创建时间", sortable = true, order = 2)
    private Date fieldCreateTime;

    /** 更新时间 */
    @DataSourceField(label = "更新时间", order = 3)
    private Date fieldUpdateTime;

    /** 开始时间 */
    @DataSourceField(label = "开始时间", sortable = true, order = 4)
    private Date fieldStartTime;

    /** 结束时间 */
    @DataSourceField(label = "结束时间", order = 5)
    private Date fieldEndTime;

    /** 标题 */
    @DataSourceField(label = "标题", order = 6)
    private String fieldName;

    /** 内容 */
    @DataSourceField(label = "内容", order = 7)
    private String fieldContent;

    /** 类型：PERSONAL=个人日程 TEAM=团队日程 */
    @DataSourceField(label = "类型", order = 8)
    private String fieldType;

    /** 接收人列表（团队日程时显示）——不参与数据源字段定义，不标注 */
    private List<SysCalendarPersonVo> persons;
}