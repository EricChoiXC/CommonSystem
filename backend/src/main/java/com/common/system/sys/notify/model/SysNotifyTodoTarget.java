package com.common.system.sys.notify.model;

import com.common.system.common.base.model.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 待办接收人表模型
 *
 * @author Eric
 * @date 2026/8/12
 * @description 对应 sys_notify_todo_target；field_todo_id 关联 sys_notify_todo，
 *              一条记录代表一个接收人的一份待办（已办/星标/删除状态按人独立维护）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysNotifyTodoTarget extends BaseModel {

    /** 待办id */
    private String fieldTodoId;

    /** 接收者id */
    private String fieldUserId;

    /** 是否已办 */
    private Boolean fieldIsDone;

    /** 是否收藏 */
    private Boolean fieldIsStar;

    /** 是否删除（软删除标记，删除只是置 true，不是真的删除记录） */
    private Boolean fieldIsDel;

    /** 接收时间 */
    private Date fieldReceiveTime;

    /** 办结时间 */
    private Date fieldDoneTime;

    /** 收藏时间 */
    private Date fieldStarTime;

    /** 删除时间 */
    private Date fieldDelTime;
}
