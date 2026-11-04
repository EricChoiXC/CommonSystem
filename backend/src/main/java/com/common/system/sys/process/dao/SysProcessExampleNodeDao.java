package com.common.system.sys.process.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.sys.process.mapper.SysProcessExampleNodeMapper;
import com.common.system.sys.process.model.SysProcessExampleNode;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 流程实例节点数据访问对象
 *
 * @author Eric
 * @date 2026/8/23
 * @description 与 SysProcessExampleNodeMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysProcessExampleNodeDao extends BaseDao<SysProcessExampleNode, SysProcessExampleNodeMapper> {

    public SysProcessExampleNodeDao(SysProcessExampleNodeMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 按实例查询全部节点（按节点编号升序）
     */
    public List<SysProcessExampleNode> selectListByExampleId(String exampleId) {
        return getBaseMapper().selectListByExampleId(exampleId);
    }

    /**
     * 批量插入实例节点（创建实例时从模板节点快照）
     */
    public int insertBatch(String exampleId, List<SysProcessExampleNode> nodes) {
        return getBaseMapper().insertBatch(exampleId, nodes);
    }

    /**
     * 按实例 + 节点编号查询单个节点
     */
    public SysProcessExampleNode selectByExampleAndNo(String exampleId, Integer nodeNo) {
        return getBaseMapper().selectByExampleAndNo(exampleId, nodeNo);
    }

    /**
     * 按实例 + 节点状态查询（如当前处理中的节点）
     */
    public List<SysProcessExampleNode> selectByExampleAndStatus(String exampleId, String status) {
        return getBaseMapper().selectByExampleAndStatus(exampleId, status);
    }

    /**
     * 按实例 + 节点类型查询（如草稿节点 / 结束节点）
     */
    public SysProcessExampleNode selectByExampleAndType(String exampleId, String nodeType) {
        return getBaseMapper().selectByExampleAndType(exampleId, nodeType);
    }
}
