package com.common.system.sys.process.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.sys.process.model.SysProcessExampleWorkitem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程工作项 Mapper
 *
 * @author Eric
 * @date 2026/8/23
 * @description 与 sys_process_example_workitem 表一对一对接；进入审批节点时为每个审批人
 *              创建工作项（含自动跳过的处理人），流转过程中按工作项维护状态与配置。
 */
@Mapper
public interface SysProcessExampleWorkitemMapper extends IBaseMapper<SysProcessExampleWorkitem> {

    /**
     * 按实例查询全部工作项（按创建时间升序）
     */
    List<SysProcessExampleWorkitem> selectListByExampleId(@Param("exampleId") String exampleId);

    /**
     * 按节点查询全部工作项（按创建时间升序）
     */
    List<SysProcessExampleWorkitem> selectListByNodeId(@Param("nodeId") String nodeId);

    /**
     * 按实例 + 处理人查询工作项（判断当前用户对当前节点的可执行审批操作）
     */
    List<SysProcessExampleWorkitem> selectByExampleAndHandler(@Param("exampleId") String exampleId,
                                                              @Param("handlerId") String handlerId);

    /**
     * 按实例查询处理中（PROCESSING / PAUSE）的工作项（暂停/恢复/收回/作废时批量置终态）
     */
    List<SysProcessExampleWorkitem> selectActiveByExample(@Param("exampleId") String exampleId);
}
