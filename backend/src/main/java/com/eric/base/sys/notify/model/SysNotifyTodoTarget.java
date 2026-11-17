package com.eric.base.sys.notify.model;

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
 * 待办接收人表模型
 *
 * @author Eric
 * @date 2026/8/12
 * @description 对应 sys_notify_todo_target；field_todo_id 关联 sys_notify_todo，
 *              一条记录代表一个接收人的一份待办（已办/星标/删除状态按人独立维护）。
 *              <p>2026/11/17 F19-DB-002 存量标注批二：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField，列定义与 dev 库 information_schema 逐列一致；
 *              公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_notify_todo_target", comment = "待办接收人表",
        indexes = {
                @TableIndex(name = "idx_notify_target_todo", columns = "field_todo_id"),
                @TableIndex(name = "idx_notify_target_user", columns = "field_user_id")})
public class SysNotifyTodoTarget extends BaseModel {

    /** 待办id */
    @ColumnField(name = "field_todo_id", comment = "待办id", type = ColumnType.STRING, length = 32)
    private String fieldTodoId;

    /** 接收者id */
    @ColumnField(name = "field_user_id", comment = "接收者id", type = ColumnType.STRING, length = 32)
    private String fieldUserId;

    /** 是否已办 */
    @ColumnField(name = "field_is_done", comment = "是否已办", type = ColumnType.BOOLEAN, defaultValue = "b'0'")
    private Boolean fieldIsDone;

    /** 是否收藏 */
    @ColumnField(name = "field_is_star", comment = "是否收藏", type = ColumnType.BOOLEAN, defaultValue = "b'0'")
    private Boolean fieldIsStar;

    /** 是否删除（软删除标记，删除只是置 true，不是真的删除记录） */
    @ColumnField(name = "field_is_del", comment = "是否删除", type = ColumnType.BOOLEAN, defaultValue = "b'0'")
    private Boolean fieldIsDel;

    /** 接收时间 */
    @ColumnField(name = "field_receive_time", comment = "接收时间",
            type = ColumnType.TIMESTAMP, defaultFunction = DefaultFunction.CURRENT_TIMESTAMP)
    private Date fieldReceiveTime;

    /** 办结时间 */
    @ColumnField(name = "field_done_time", comment = "办结时间", type = ColumnType.TIMESTAMP, nullable = true)
    private Date fieldDoneTime;

    /** 收藏时间 */
    @ColumnField(name = "field_star_time", comment = "收藏时间", type = ColumnType.TIMESTAMP, nullable = true)
    private Date fieldStarTime;

    /** 删除时间 */
    @ColumnField(name = "field_del_time", comment = "删除时间", type = ColumnType.TIMESTAMP, nullable = true)
    private Date fieldDelTime;
}
