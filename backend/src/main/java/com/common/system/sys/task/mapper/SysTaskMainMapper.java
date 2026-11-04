package com.common.system.sys.task.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.sys.task.model.SysTaskMain;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 定时任务主表数据访问映射
 *
 * @author Eric
 * @date 2026/8/15
 * @description 与 sys_task_main 一对一对接，不承载业务逻辑。
 */
@Mapper
public interface SysTaskMainMapper extends IBaseMapper<SysTaskMain> {

    /**
     * 按任务类名查询（系统启动扫描写入时用于判断是否已登记）
     */
    SysTaskMain selectByClass(@Param("fieldClass") String fieldClass);

    /**
     * 批量更新是否启用（批量停用/启用）
     */
    int updateEnableBatch(@Param("ids") List<String> ids, @Param("enable") Boolean enable);

    /**
     * 任务列表总数（与 selectList 过滤条件一致，供分页 metadata.total 使用）
     */
    long selectCount(@Param("model") SysTaskMain criteria);
}
