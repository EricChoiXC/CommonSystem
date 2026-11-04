package com.common.system.sys.notify.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.notify.model.SysNotifyTodoTarget;
import com.common.system.sys.notify.request.SysNotifyTodoQuery;
import com.common.system.sys.notify.response.SysNotifyTodoVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 待办接收人表 Mapper
 *
 * @author Eric
 * @date 2026/8/12
 * @description 与 SysNotifyTodoTargetMapper.xml 同名对应，一对一操作 sys_notify_todo_target；
 *              我的待办列表 JOIN sys_notify_todo 组装标题/来源模块/通知类型/链接；
 *              所有「本人操作」类更新均带 field_user_id 条件，防止越权操作他人待办。
 */
@Mapper
public interface SysNotifyTodoTargetMapper extends IBaseMapper<SysNotifyTodoTarget> {

    /**
     * 我的待办列表（只显示自己接收到的待办，过滤已删除记录）。
     * sortField / sortOrder 必须为白名单值（receiveTime + asc / desc），由 Service 层归一化后传入。
     */
    List<SysNotifyTodoVo> selectMyList(@Param("model") SysNotifyTodoQuery model,
                                       @Param("userId") String userId,
                                       @Param("pages") Pages pages,
                                       @Param("sortField") String sortField,
                                       @Param("sortOrder") String sortOrder);

    /**
     * 我的待办列表总数（与 selectMyList 过滤条件一致，供分页 metadata.total 使用）
     */
    long selectMyCount(@Param("model") SysNotifyTodoQuery model, @Param("userId") String userId);

    /**
     * 我的待办详情（限定本人且未删除）：JOIN sys_notify_todo 组装展示信息
     */
    SysNotifyTodoVo selectMyVoById(@Param("fieldId") String fieldId, @Param("userId") String userId);

    /**
     * 单条转已办/转未办（限定本人）：已办写入办结时间，转未办清空办结时间
     */
    int updateDone(@Param("fieldId") String fieldId, @Param("userId") String userId,
                   @Param("done") boolean done, @Param("time") Date time);

    /**
     * 单条星标/取消星标（限定本人）：星标写入收藏时间，取消清空收藏时间
     */
    int updateStar(@Param("fieldId") String fieldId, @Param("userId") String userId,
                   @Param("star") boolean star, @Param("time") Date time);

    /**
     * 单条软删除（限定本人）：置 field_is_del=true 并写入删除时间，不是真的删除记录
     */
    int updateDel(@Param("fieldId") String fieldId, @Param("userId") String userId,
                  @Param("del") boolean del, @Param("time") Date time);

    /**
     * 批量转已办/转未办（限定本人，跳过已删除记录）
     */
    int batchUpdateDone(@Param("ids") List<String> ids, @Param("userId") String userId,
                        @Param("done") boolean done, @Param("time") Date time);

    /**
     * 批量星标/取消星标（限定本人，跳过已删除记录）
     */
    int batchUpdateStar(@Param("ids") List<String> ids, @Param("userId") String userId,
                        @Param("star") boolean star, @Param("time") Date time);

    /**
     * 批量软删除（限定本人）：置 field_is_del=true 并写入删除时间
     */
    int batchUpdateDel(@Param("ids") List<String> ids, @Param("userId") String userId,
                       @Param("del") boolean del, @Param("time") Date time);

    /**
     * 按待办id + 接收人查询接收记录（后端内部「自动转已办」服务使用）
     */
    SysNotifyTodoTarget selectByTodoAndUser(@Param("todoId") String todoId, @Param("userId") String userId);

    /**
     * 某待办的接收人数（后台待办列表汇总用）
     */
    long countByTodoId(@Param("todoId") String todoId);

    /**
     * 某待办的已办人数（后台待办列表汇总用）
     */
    long countDoneByTodoId(@Param("todoId") String todoId);
}
