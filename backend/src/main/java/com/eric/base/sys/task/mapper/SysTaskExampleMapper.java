package com.eric.base.sys.task.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.task.model.SysTaskExample;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务执行实例数据访问映射
 *
 * @author Eric
 * @date 2026/8/15
 * @description 与 sys_task_example 一对一对接，不承载业务逻辑；基础 CRUD 由父接口提供。
 */
@Mapper
public interface SysTaskExampleMapper extends IBaseMapper<SysTaskExample> {
}
