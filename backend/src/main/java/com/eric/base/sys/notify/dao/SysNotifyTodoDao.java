package com.eric.base.sys.notify.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.notify.mapper.SysNotifyTodoMapper;
import com.eric.base.sys.notify.model.SysNotifyTodo;
import com.eric.base.sys.notify.request.SysNotifyAdminQuery;
import com.eric.base.sys.notify.response.SysNotifyAdminTodoVo;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 待办主表数据访问对象
 *
 * @author Eric
 * @date 2026/8/12
 * @description 与 SysNotifyTodoMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysNotifyTodoDao extends BaseDao<SysNotifyTodo, SysNotifyTodoMapper> {

    public SysNotifyTodoDao(SysNotifyTodoMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 后台待办列表（含接收人数/已办人数）
     */
    public List<SysNotifyAdminTodoVo> selectAdminList(SysNotifyAdminQuery model, Pages pages,
                                                      String sortField, String sortOrder) {
        return getBaseMapper().selectAdminList(model, pages, sortField, sortOrder);
    }

    /**
     * 后台待办列表总数（与 selectAdminList 过滤条件一致，供分页 metadata.total 使用）
     */
    public long selectAdminCount(SysNotifyAdminQuery model) {
        return getBaseMapper().selectAdminCount(model);
    }

    /**
     * 按来源模块 + 文档id 查询待办（后端内部「自动转已办」服务使用）
     */
    public List<SysNotifyTodo> selectByModel(String modelName, String modelId) {
        return getBaseMapper().selectByModel(modelName, modelId);
    }
}
