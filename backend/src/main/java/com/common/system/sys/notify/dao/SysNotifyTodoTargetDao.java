package com.common.system.sys.notify.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.notify.mapper.SysNotifyTodoTargetMapper;
import com.common.system.sys.notify.model.SysNotifyTodoTarget;
import com.common.system.sys.notify.request.SysNotifyTodoQuery;
import com.common.system.sys.notify.response.SysNotifyTodoVo;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * 待办接收人表数据访问对象
 *
 * @author Eric
 * @date 2026/8/12
 * @description 与 SysNotifyTodoTargetMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysNotifyTodoTargetDao extends BaseDao<SysNotifyTodoTarget, SysNotifyTodoTargetMapper> {

    public SysNotifyTodoTargetDao(SysNotifyTodoTargetMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 我的待办列表（只显示自己接收到的待办）
     */
    public List<SysNotifyTodoVo> selectMyList(SysNotifyTodoQuery model, String userId, Pages pages,
                                              String sortField, String sortOrder) {
        return getBaseMapper().selectMyList(model, userId, pages, sortField, sortOrder);
    }

    /**
     * 我的待办列表总数（与 selectMyList 过滤条件一致，供分页 metadata.total 使用）
     */
    public long selectMyCount(SysNotifyTodoQuery model, String userId) {
        return getBaseMapper().selectMyCount(model, userId);
    }

    /**
     * 我的待办详情（限定本人且未删除）
     */
    public SysNotifyTodoVo selectMyVoById(String fieldId, String userId) {
        return getBaseMapper().selectMyVoById(fieldId, userId);
    }

    /**
     * 单条转已办/转未办（限定本人）
     */
    public int updateDone(String fieldId, String userId, boolean done, Date time) {
        return getBaseMapper().updateDone(fieldId, userId, done, time);
    }

    /**
     * 单条星标/取消星标（限定本人）
     */
    public int updateStar(String fieldId, String userId, boolean star, Date time) {
        return getBaseMapper().updateStar(fieldId, userId, star, time);
    }

    /**
     * 单条软删除（限定本人）
     */
    public int updateDel(String fieldId, String userId, boolean del, Date time) {
        return getBaseMapper().updateDel(fieldId, userId, del, time);
    }

    /**
     * 批量转已办/转未办（限定本人）
     */
    public int batchUpdateDone(List<String> ids, String userId, boolean done, Date time) {
        return getBaseMapper().batchUpdateDone(ids, userId, done, time);
    }

    /**
     * 批量星标/取消星标（限定本人）
     */
    public int batchUpdateStar(List<String> ids, String userId, boolean star, Date time) {
        return getBaseMapper().batchUpdateStar(ids, userId, star, time);
    }

    /**
     * 批量软删除（限定本人）
     */
    public int batchUpdateDel(List<String> ids, String userId, boolean del, Date time) {
        return getBaseMapper().batchUpdateDel(ids, userId, del, time);
    }

    /**
     * 按待办id + 接收人查询接收记录（后端内部「自动转已办」服务使用）
     */
    public SysNotifyTodoTarget selectByTodoAndUser(String todoId, String userId) {
        return getBaseMapper().selectByTodoAndUser(todoId, userId);
    }

    /**
     * 某待办的接收人数（后台待办列表汇总用）
     */
    public long countByTodoId(String todoId) {
        return getBaseMapper().countByTodoId(todoId);
    }

    /**
     * 某待办的已办人数（后台待办列表汇总用）
     */
    public long countDoneByTodoId(String todoId) {
        return getBaseMapper().countDoneByTodoId(todoId);
    }
}
