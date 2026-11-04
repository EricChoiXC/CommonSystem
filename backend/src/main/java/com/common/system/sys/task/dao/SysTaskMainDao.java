package com.common.system.sys.task.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.sys.task.mapper.SysTaskMainMapper;
import com.common.system.sys.task.model.SysTaskMain;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 定时任务主表数据访问对象
 *
 * @author Eric
 * @date 2026/8/15
 * @description 与 SysTaskMainMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysTaskMainDao extends BaseDao<SysTaskMain, SysTaskMainMapper> {

    public SysTaskMainDao(SysTaskMainMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 按任务类名查询（系统启动扫描写入时用于判断是否已登记）
     */
    public SysTaskMain selectByClass(String fieldClass) {
        return getBaseMapper().selectByClass(fieldClass);
    }

    /**
     * 批量更新是否启用（批量停用/启用）
     */
    public int updateEnableBatch(List<String> ids, boolean enable) {
        return getBaseMapper().updateEnableBatch(ids, enable);
    }

    /**
     * 任务列表总数（与列表查询过滤条件一致，供分页 metadata.total 使用）
     */
    public long selectCount(SysTaskMain criteria) {
        return getBaseMapper().selectCount(criteria);
    }
}
