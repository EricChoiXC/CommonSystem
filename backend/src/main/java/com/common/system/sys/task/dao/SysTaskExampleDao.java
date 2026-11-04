package com.common.system.sys.task.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.sys.task.mapper.SysTaskExampleMapper;
import com.common.system.sys.task.model.SysTaskExample;
import org.springframework.stereotype.Repository;

/**
 * 任务执行实例数据访问对象
 *
 * @author Eric
 * @date 2026/8/15
 * @description 与 SysTaskExampleMapper 一对一对接，不承载业务逻辑；基础 CRUD 由父类提供。
 */
@Repository
public class SysTaskExampleDao extends BaseDao<SysTaskExample, SysTaskExampleMapper> {

    public SysTaskExampleDao(SysTaskExampleMapper baseMapper) {
        super(baseMapper);
    }
}
