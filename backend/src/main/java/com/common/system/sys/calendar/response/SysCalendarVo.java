package com.common.system.sys.calendar.response;

import com.common.system.common.base.model.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;

/**
 * 日程列表/详情响应对象
 *
 * @author Eric
 * @date 2026/8/21
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysCalendarVo extends BaseModel {

    /** 创建人id */
    private String fieldCreatorId;

    /** 创建时间 */
    private Date fieldCreateTime;

    /** 更新时间 */
    private Date fieldUpdateTime;

    /** 开始时间 */
    private Date fieldStartTime;

    /** 结束时间 */
    private Date fieldEndTime;

    /** 标题 */
    private String fieldName;

    /** 内容 */
    private String fieldContent;

    /** 类型：PERSONAL=个人日程 TEAM=团队日程 */
    private String fieldType;

    /** 接收人列表（团队日程时显示） */
    private List<SysCalendarPersonVo> persons;
}