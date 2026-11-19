package com.eric.base.sys.process.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.process.model.SysProcessTaskLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程任务执行日志 Mapper
 *
 * @author Eric
 * @date 2026/11/19
 * @description 与 sys_process_task_log 表一对一对接；本表为 @ProcessTask 每次执行的流水留痕，
 *              <b>只增不改</b>（对齐 sys_task_history / sys_process_audit_note 先例）：
 *              业务代码只调用 {@code insert}，XML 中的 {@code update} / {@code deleteById}
 *              仅为满足 {@link IBaseMapper} 接口完整性保留，业务代码不得调用。
 *              查询主路径恒为「按实例检索」（走 idx_task_log_example，按创建时间、主键升序）。
 */
@Mapper
public interface SysProcessTaskLogMapper extends IBaseMapper<SysProcessTaskLog> {

    /**
     * 按实例查询全部任务执行留痕（按创建时间、主键升序）
     */
    List<SysProcessTaskLog> selectListByExampleId(@Param("exampleId") String exampleId);

    /**
     * 按「实例 + 执行步骤」查询任务执行留痕（分册 §3.3.6 第③条，按创建时间、主键升序）
     */
    List<SysProcessTaskLog> selectListByExampleIdAndTaskType(@Param("exampleId") String exampleId,
                                                             @Param("taskType") String taskType);
}
