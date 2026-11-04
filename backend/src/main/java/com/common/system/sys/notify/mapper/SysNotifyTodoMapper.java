package com.common.system.sys.notify.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.notify.model.SysNotifyTodo;
import com.common.system.sys.notify.request.SysNotifyAdminQuery;
import com.common.system.sys.notify.response.SysNotifyAdminTodoVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 待办主表 Mapper
 *
 * @author Eric
 * @date 2026/8/12
 * @description 与 SysNotifyTodoMapper.xml 同名对应，一对一操作 sys_notify_todo；
 *              后台待办列表 JOIN sys_notify_todo_target 汇总接收人数与已办人数。
 */
@Mapper
public interface SysNotifyTodoMapper extends IBaseMapper<SysNotifyTodo> {

    /**
     * 后台待办列表（按 sys_notify_todo 可见所有待办，含接收人数/已办人数）。
     * sortField / sortOrder 必须为白名单值（createTime + asc / desc），由 Service 层归一化后传入。
     */
    List<SysNotifyAdminTodoVo> selectAdminList(@Param("model") SysNotifyAdminQuery model,
                                               @Param("pages") Pages pages,
                                               @Param("sortField") String sortField,
                                               @Param("sortOrder") String sortOrder);

    /**
     * 后台待办列表总数（与 selectAdminList 过滤条件一致，供分页 metadata.total 使用）
     */
    long selectAdminCount(@Param("model") SysNotifyAdminQuery model);

    /**
     * 按来源模块 + 文档id 查询待办（后端内部「自动转已办」服务使用）
     */
    List<SysNotifyTodo> selectByModel(@Param("modelName") String modelName,
                                      @Param("modelId") String modelId);
}
