package com.common.system.sys.process.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.sys.process.model.SysProcessExampleNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程实例节点 Mapper
 *
 * @author Eric
 * @date 2026/8/23
 * @description 与 sys_process_example_node 表一对一对接；创建流程实例时从模板节点批量快照，
 *              流转过程中按实例维护节点状态。
 */
@Mapper
public interface SysProcessExampleNodeMapper extends IBaseMapper<SysProcessExampleNode> {

    /**
     * 按实例查询全部节点（按节点编号升序）
     */
    List<SysProcessExampleNode> selectListByExampleId(@Param("exampleId") String exampleId);

    /**
     * 批量插入实例节点（创建实例时从模板节点快照）
     */
    int insertBatch(@Param("exampleId") String exampleId,
                    @Param("nodes") List<SysProcessExampleNode> nodes);

    /**
     * 按实例 + 节点编号查询单个节点
     */
    SysProcessExampleNode selectByExampleAndNo(@Param("exampleId") String exampleId,
                                               @Param("nodeNo") Integer nodeNo);

    /**
     * 按实例 + 节点状态查询（如当前处理中的节点）
     */
    List<SysProcessExampleNode> selectByExampleAndStatus(@Param("exampleId") String exampleId,
                                                         @Param("status") String status);

    /**
     * 按实例 + 节点类型查询（如草稿节点 / 结束节点）
     */
    SysProcessExampleNode selectByExampleAndType(@Param("exampleId") String exampleId,
                                                 @Param("nodeType") String nodeType);
}
