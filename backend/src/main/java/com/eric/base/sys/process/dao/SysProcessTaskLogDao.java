package com.eric.base.sys.process.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.sys.process.mapper.SysProcessTaskLogMapper;
import com.eric.base.sys.process.model.SysProcessTaskLog;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 流程任务执行日志数据访问对象
 *
 * @author Eric
 * @date 2026/11/19
 * @description 与 SysProcessTaskLogMapper 一对一对接，不承载业务逻辑。
 *              本表为 @ProcessTask 每次执行的流水留痕，<b>只增不改</b>
 *              （对齐 sys_task_history / sys_process_audit_note 先例）：本类只提供写入留痕
 *              与查询方法，不提供任何 UPDATE / DELETE 业务方法；继承 {@code BaseDao} 带入的
 *              {@code update} / {@code deleteById} 转发能力仅为基类接口契约（同表族先例如此），
 *              业务代码不得调用。留痕写入与流程流转同事务，写失败不影响流转（记 WARN）。
 */
@Repository
public class SysProcessTaskLogDao extends BaseDao<SysProcessTaskLog, SysProcessTaskLogMapper> {

    public SysProcessTaskLogDao(SysProcessTaskLogMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 写入单条任务执行留痕（与流程流转同事务；field_params / field_result 传 JSON 字符串）
     */
    public int insertLog(SysProcessTaskLog log) {
        return getBaseMapper().insert(log);
    }

    /**
     * 按实例查询全部任务执行留痕（按创建时间、主键升序）
     */
    public List<SysProcessTaskLog> selectListByExampleId(String exampleId) {
        return getBaseMapper().selectListByExampleId(exampleId);
    }

    /**
     * 按「实例 + 执行步骤」查询任务执行留痕（按创建时间、主键升序）
     */
    public List<SysProcessTaskLog> selectListByExampleIdAndTaskType(String exampleId, String taskType) {
        return getBaseMapper().selectListByExampleIdAndTaskType(exampleId, taskType);
    }
}
