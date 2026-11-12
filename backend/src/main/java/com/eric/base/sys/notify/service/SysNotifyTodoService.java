package com.eric.base.sys.notify.service;

import com.eric.base.common.base.model.Pages;
import com.eric.base.common.base.service.BaseService;
import com.eric.base.common.cache.ModelCacheService;
import com.eric.base.common.exception.AuthorizationException;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.notify.dao.SysNotifyTodoDao;
import com.eric.base.sys.notify.dao.SysNotifyTodoTargetDao;
import com.eric.base.sys.notify.enums.SysNotifyType;
import com.eric.base.sys.notify.event.NotifySendEvent;
import com.eric.base.sys.notify.model.SysNotifyTodo;
import com.eric.base.sys.notify.model.SysNotifyTodoTarget;
import com.eric.base.sys.notify.request.SysNotifyAdminQuery;
import com.eric.base.sys.notify.request.SysNotifyTodoQuery;
import com.eric.base.sys.notify.response.SysNotifyAdminTodoVo;
import com.eric.base.sys.notify.response.SysNotifyTodoVo;
import com.eric.base.sys.org.model.SysOrgUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 待办业务服务
 *
 * @author Eric
 * @date 2026/8/12
 * @description 承载通知待办核心业务：发送待办（前端接口与后端内部服务共用）、
 *              我的待办列表（只显示本人接收的待办）、转已办/转未办/星标/删除（含批量）、
 *              待阅详情自动转已办、后台待办列表（含接收人数/已办人数）、
 *              后端内部「自动转已办」服务（按来源模块+文档id 触发）。
 *              来源模块 field_model_name 存储 MODEL_KEY（如 SYS-ORG），
 *              展示时经 {@link ModelCacheService} 从 model:name:map 翻译为 MODEL_NAME（中文名）。
 *              业务规则依据 artificial/docs/设计文档/sys/notify/通知待办核心业务.md。
 */
@Service
public class SysNotifyTodoService extends BaseService<SysNotifyTodo, SysNotifyTodoDao> {

    private final SysNotifyTodoTargetDao targetDao;
    private final ModelCacheService modelCacheService;
    private final ApplicationEventPublisher eventPublisher;

    public SysNotifyTodoService(SysNotifyTodoDao baseDao, SysNotifyTodoTargetDao targetDao,
                                ModelCacheService modelCacheService,
                                ApplicationEventPublisher eventPublisher) {
        super(baseDao);
        this.targetDao = targetDao;
        this.modelCacheService = modelCacheService;
        this.eventPublisher = eventPublisher;
    }

    /* ==================== 发送待办 ==================== */

    /**
     * 发送待办：前端发送待办接口与后端内部服务共用的核心逻辑。
     * <p>写 sys_notify_todo 主表 + 按接收人批量写 sys_notify_todo_target（同事务），
     * 接收人列表去重且跳过空值。来源模块 field_model_name 必须为 model:name:map 中
     * 的合法 MODEL_KEY（如 SYS-ORG），由前端下拉选择，后端兜底校验。</p>
     *
     * @param todo    待办内容（标题/待办类型/来源模块/文档id/链接/内容）
     * @param userIds 接收人 id 列表（多选）
     * @return 保存后的待办主表对象
     */
    @Transactional
    public SysNotifyTodo send(SysNotifyTodo todo, List<String> userIds) {
        if (null == todo || StringUtil.isEmpty(todo.getFieldName())) {
            throw new IllegalArgumentException("待办标题不能为空");
        }
        if (SysNotifyType.of(todo.getFieldNotifyType()) == null) {
            throw new IllegalArgumentException("待办类型不合法");
        }
        if (StringUtil.isEmpty(todo.getFieldModelName())
                || !modelCacheService.getModelNames().containsKey(todo.getFieldModelName())) {
            throw new IllegalArgumentException("来源模块不合法，请从模块列表中选择");
        }
        if (null == userIds || userIds.isEmpty()) {
            throw new IllegalArgumentException("接收人不能为空");
        }
        SysOrgUser current = UserUtil.getCurrentUser();
        todo.initFieldId();
        todo.setFieldCreatorId(null == current ? null : current.getFieldId());
        todo.setFieldCreateTime(new Date());
        getBaseDao().insert(todo);
        Date now = new Date();
        for (String userId : userIds) {
            if (StringUtil.isEmpty(userId)) {
                continue;
            }
            SysNotifyTodoTarget target = new SysNotifyTodoTarget();
            target.initFieldId();
            target.setFieldTodoId(todo.getFieldId());
            target.setFieldUserId(userId);
            target.setFieldIsDone(false);
            target.setFieldIsStar(false);
            target.setFieldIsDel(false);
            target.setFieldReceiveTime(now);
            targetDao.insert(target);
        }
        // 发布事件，供 NotifySseEventListener 通过 SSE 推送实时通知
        eventPublisher.publishEvent(new NotifySendEvent(
                this,
                todo.getFieldId(),
                todo.getFieldName(),
                todo.getFieldNotifyType(),
                todo.getFieldModelName(),
                todo.getFieldModelId(),
                todo.getFieldUrl(),
                userIds
        ));
        return todo;
    }

    /* ==================== 我的待办列表 ==================== */

    /**
     * 我的待办列表：只显示当前用户接收到的待办（已删除的过滤）。
     * 待办类型默认「待办」（无"全部"）；是否办结默认「未办」（无"全部"）；
     * 排序字段白名单 receiveTime，默认接收时间降序。
     */
    public List<SysNotifyTodoVo> myList(SysNotifyTodoQuery query, Pages pages,
                                        String sortField, String sortOrder) {
        SysNotifyTodoQuery safe = null == query ? new SysNotifyTodoQuery() : query;
        if (StringUtil.isEmpty(safe.getFieldNotifyType())) {
            safe.setFieldNotifyType(SysNotifyType.TODO.getValue());
        }
        if (null == safe.getFieldIsDone()) {
            safe.setFieldIsDone(false);
        }
        List<SysNotifyTodoVo> list = targetDao.selectMyList(safe, requireCurrentUserId(),
                normalizePages(pages), normalizeMySortField(sortField), normalizeSortOrder(sortOrder));
        translateMyModelName(list);
        return list;
    }

    /**
     * 我的待办列表总数（与 {@link #myList} 过滤条件一致，供分页 metadata.total 使用）
     */
    public long myCount(SysNotifyTodoQuery query) {
        SysNotifyTodoQuery safe = null == query ? new SysNotifyTodoQuery() : query;
        if (StringUtil.isEmpty(safe.getFieldNotifyType())) {
            safe.setFieldNotifyType(SysNotifyType.TODO.getValue());
        }
        if (null == safe.getFieldIsDone()) {
            safe.setFieldIsDone(false);
        }
        return targetDao.selectMyCount(safe, requireCurrentUserId());
    }

    /**
     * 我的待办详情：限定本人且未删除；待阅类型点击详情触发跳转时自动转已办。
     */
    @Transactional
    public SysNotifyTodoVo loadDetail(String targetId) {
        String userId = requireCurrentUserId();
        String id = requireText(targetId, "fieldId");
        SysNotifyTodoVo vo = targetDao.selectMyVoById(id, userId);
        if (null == vo) {
            throw new IllegalArgumentException("待办不存在或无权查看");
        }
        vo.setFieldModelName(modelCacheService.resolveModelName(vo.getFieldModelName()));
        if (SysNotifyType.READ.getValue().equals(vo.getFieldNotifyType())
                && !Boolean.TRUE.equals(vo.getFieldIsDone())) {
            targetDao.updateDone(id, userId, true, new Date());
            vo.setFieldIsDone(true);
            vo.setFieldDoneTime(new Date());
        }
        return vo;
    }

    /* ==================== 单条操作（转已办/转未办/星标/删除） ==================== */

    /**
     * 转已办：仅限本人接收记录，记录办结时间。
     */
    @Transactional
    public void markDone(String targetId) {
        changeDone(requireText(targetId, "fieldId"), true);
    }

    /**
     * 转未办：仅限本人接收记录，清空办结时间。
     */
    @Transactional
    public void markUndone(String targetId) {
        changeDone(requireText(targetId, "fieldId"), false);
    }

    /**
     * 星标切换：仅限本人接收记录；未星标则置星标并记录收藏时间，已星标则取消。
     */
    @Transactional
    public void toggleStar(String targetId) {
        String userId = requireCurrentUserId();
        String id = requireText(targetId, "fieldId");
        SysNotifyTodoTarget target = requireOwnNotDeleted(id, userId);
        boolean star = !Boolean.TRUE.equals(target.getFieldIsStar());
        int rows = targetDao.updateStar(id, userId, star, new Date());
        if (rows != 1) {
            throw new IllegalStateException("星标操作失败");
        }
    }

    /**
     * 删除（软删除）：仅限本人接收记录，置 field_is_del=true 并记录删除时间，不是真的删除记录。
     */
    @Transactional
    public void deleteByTarget(String targetId) {
        String userId = requireCurrentUserId();
        String id = requireText(targetId, "fieldId");
        int rows = targetDao.updateDel(id, userId, true, new Date());
        if (rows != 1) {
            throw new IllegalStateException("待办不存在或无权删除");
        }
    }

    /* ==================== 批量操作 ==================== */

    /**
     * 批量已办
     */
    @Transactional
    public void batchDone(List<String> ids) {
        batchChangeDone(ids, true);
    }

    /**
     * 批量转未办
     */
    @Transactional
    public void batchUndone(List<String> ids) {
        batchChangeDone(ids, false);
    }

    /**
     * 批量星标（全部置为星标并记录收藏时间）
     */
    @Transactional
    public void batchStar(List<String> ids) {
        requireIds(ids);
        int rows = targetDao.batchUpdateStar(ids, requireCurrentUserId(), true, new Date());
        if (rows == 0) {
            throw new IllegalStateException("没有可操作的待办");
        }
    }

    /**
     * 批量删除（软删除）：置 field_is_del=true 并记录删除时间。
     */
    @Transactional
    public void batchDelete(List<String> ids) {
        requireIds(ids);
        int rows = targetDao.batchUpdateDel(ids, requireCurrentUserId(), true, new Date());
        if (rows == 0) {
            throw new IllegalStateException("没有可操作的待办");
        }
    }

    /* ==================== 后台待办列表 ==================== */

    /**
     * 后台待办列表（按 sys_notify_todo 可见所有待办，含接收人数/已办人数）。
     * 待办类型默认「待办」（无"全部"）；排序字段白名单 createTime，默认创建时间降序。
     */
    public List<SysNotifyAdminTodoVo> adminList(SysNotifyAdminQuery query, Pages pages,
                                                String sortField, String sortOrder) {
        SysNotifyAdminQuery safe = null == query ? new SysNotifyAdminQuery() : query;
        if (StringUtil.isEmpty(safe.getFieldNotifyType())) {
            safe.setFieldNotifyType(SysNotifyType.TODO.getValue());
        }
        List<SysNotifyAdminTodoVo> list = getBaseDao().selectAdminList(safe, normalizePages(pages),
                normalizeAdminSortField(sortField), normalizeSortOrder(sortOrder));
        translateAdminModelName(list);
        return list;
    }

    /**
     * 后台待办列表总数（与 {@link #adminList} 过滤条件一致，供分页 metadata.total 使用）
     */
    public long adminCount(SysNotifyAdminQuery query) {
        SysNotifyAdminQuery safe = null == query ? new SysNotifyAdminQuery() : query;
        if (StringUtil.isEmpty(safe.getFieldNotifyType())) {
            safe.setFieldNotifyType(SysNotifyType.TODO.getValue());
        }
        return getBaseDao().selectAdminCount(safe);
    }

    /* ==================== 来源模块选项 ==================== */

    /**
     * 来源模块选项（MODEL_KEY → MODEL_NAME）：读取 model:name:map 缓存，
     * 供发送待办页与列表筛选区的「来源模块」下拉使用。
     *
     * @return 模块映射；缓存缺失或读取失败时返回空 Map
     */
    public Map<String, String> modelOptions() {
        return modelCacheService.getModelNames();
    }

    /* ==================== 后端内部服务（自动转已办） ==================== */

    /**
     * 按来源模块 + 文档id 自动转已办（待办类型通知，由具体业务在触发点上调用）。
     * <p>将该待办对应接收人的未办记录置为已办；待阅类型请走
     * {@link #loadDetail} 点击详情自动转已办。</p>
     *
     * @param modelName 来源模块标识（MODEL_KEY，与发送待办时的 field_model_name 一致）
     * @param modelId   文档 id（与发送待办时的 field_model_id 一致）
     * @param userId    接收人（业务操作人）id
     */
    @Transactional
    public void autoDoneByModel(String modelName, String modelId, String userId) {
        if (StringUtil.isEmpty(modelName) || StringUtil.isEmpty(modelId) || StringUtil.isEmpty(userId)) {
            throw new IllegalArgumentException("modelName/modelId/userId 不能为空");
        }
        List<SysNotifyTodo> todos = getBaseDao().selectByModel(modelName, modelId);
        for (SysNotifyTodo todo : todos) {
            SysNotifyTodoTarget target = targetDao.selectByTodoAndUser(todo.getFieldId(), userId);
            if (null != target && !Boolean.TRUE.equals(target.getFieldIsDone())) {
                targetDao.updateDone(target.getFieldId(), userId, true, new Date());
            }
        }
    }

    /* ==================== 私有工具方法 ==================== */

    /**
     * 我的待办列表的「来源模块」批量翻译：将 fieldModelName（MODEL_KEY）经
     * model:name:map 翻译为 MODEL_NAME（中文名），未知 key 回退原值，保证历史数据仍可展示。
     */
    private void translateMyModelName(List<SysNotifyTodoVo> list) {
        if (null == list || list.isEmpty()) {
            return;
        }
        Map<String, String> modelNames = modelCacheService.getModelNames();
        for (SysNotifyTodoVo vo : list) {
            vo.setFieldModelName(resolveModelName(vo.getFieldModelName(), modelNames));
        }
    }

    /**
     * 后台待办列表的「来源模块」批量翻译：逻辑同 {@link #translateMyModelName}。
     */
    private void translateAdminModelName(List<SysNotifyAdminTodoVo> list) {
        if (null == list || list.isEmpty()) {
            return;
        }
        Map<String, String> modelNames = modelCacheService.getModelNames();
        for (SysNotifyAdminTodoVo vo : list) {
            vo.setFieldModelName(resolveModelName(vo.getFieldModelName(), modelNames));
        }
    }

    /**
     * 用给定的模块映射翻译来源模块：命中返回中文名，未命中回退原值。
     */
    private static String resolveModelName(String modelName, Map<String, String> modelNames) {
        if (StringUtil.isEmpty(modelName)) {
            return modelName;
        }
        return modelNames.getOrDefault(modelName, modelName);
    }

    private void changeDone(String targetId, boolean done) {
        String userId = requireCurrentUserId();
        String id = requireText(targetId, "fieldId");
        requireOwnNotDeleted(id, userId);
        int rows = targetDao.updateDone(id, userId, done, new Date());
        if (rows != 1) {
            throw new IllegalStateException("待办不存在或无权操作");
        }
    }

    private void batchChangeDone(List<String> ids, boolean done) {
        requireIds(ids);
        int rows = targetDao.batchUpdateDone(ids, requireCurrentUserId(), done, new Date());
        if (rows == 0) {
            throw new IllegalStateException("没有可操作的待办");
        }
    }

    private void requireIds(List<String> ids) {
        if (null == ids || ids.isEmpty()) {
            throw new IllegalArgumentException("ids 不能为空");
        }
    }

    /**
     * 校验接收记录属于当前用户且未删除；不存在/他人/已删除时抛参数异常
     */
    private SysNotifyTodoTarget requireOwnNotDeleted(String targetId, String userId) {
        SysNotifyTodoTarget target = targetDao.selectById(targetId);
        if (null == target || !userId.equals(target.getFieldUserId())
                || Boolean.TRUE.equals(target.getFieldIsDel())) {
            throw new IllegalArgumentException("待办不存在或无权操作");
        }
        return target;
    }

    private String requireText(String value, String fieldName) {
        if (StringUtil.isEmpty(value)) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }

    private String requireCurrentUserId() {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (null == user) {
            throw new AuthorizationException("UNAUTHORIZED", "登录已过期，请重新登录");
        }
        return user.getFieldId();
    }

    private static Pages normalizePages(Pages pages) {
        Pages safe = null == pages ? new Pages() : pages;
        if (safe.getPageNo() < 1) {
            safe.setPageNo(1);
        }
        if (safe.getPageSize() < 1) {
            safe.setPageSize(10);
        }
        return safe;
    }

    /**
     * 我的待办排序字段白名单：仅 receiveTime，其余回退 null（XML &lt;otherwise&gt; 兜底默认列）
     */
    private static String normalizeMySortField(String sortField) {
        return "receiveTime".equals(sortField) ? sortField : null;
    }

    /**
     * 后台待办排序字段白名单：仅 createTime，其余回退 null（XML &lt;otherwise&gt; 兜底默认列）
     */
    private static String normalizeAdminSortField(String sortField) {
        return "createTime".equals(sortField) ? sortField : null;
    }

    private static String normalizeSortOrder(String sortOrder) {
        return "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
    }
}
