package com.common.system.sys.process.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.sys.process.mapper.SysProcessExampleWorkitemMapper;
import com.common.system.sys.process.model.SysProcessExampleWorkitem;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 流程工作项数据访问对象
 *
 * @author Eric
 * @date 2026/8/23
 * @description 与 SysProcessExampleWorkitemMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysProcessExampleWorkitemDao extends BaseDao<SysProcessExampleWorkitem, SysProcessExampleWorkitemMapper> {

    public SysProcessExampleWorkitemDao(SysProcessExampleWorkitemMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 按实例查询全部工作项（按创建时间升序）
     */
    public List<SysProcessExampleWorkitem> selectListByExampleId(String exampleId) {
        return getBaseMapper().selectListByExampleId(exampleId);
    }

    /**
     * 按节点查询全部工作项（按创建时间升序）
     */
    public List<SysProcessExampleWorkitem> selectListByNodeId(String nodeId) {
        return getBaseMapper().selectListByNodeId(nodeId);
    }

    /**
     * 按实例 + 处理人查询工作项（判断当前用户对当前节点的可执行审批操作）
     */
    public List<SysProcessExampleWorkitem> selectByExampleAndHandler(String exampleId, String handlerId) {
        return getBaseMapper().selectByExampleAndHandler(exampleId, handlerId);
    }

    /**
     * 按实例查询处理中（PROCESSING / PAUSE）的工作项（暂停/恢复/收回/作废时批量置终态）
     */
    public List<SysProcessExampleWorkitem> selectActiveByExample(String exampleId) {
        return getBaseMapper().selectActiveByExample(exampleId);
    }
}
