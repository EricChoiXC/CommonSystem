package com.common.system.sys.task.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.common.system.common.base.model.Pages;
import com.common.system.common.base.service.BaseService;
import com.common.system.common.utils.StringUtil;
import com.common.system.sys.task.dao.SysTaskMainDao;
import com.common.system.sys.task.model.SysTaskMain;
import com.common.system.sys.task.request.SysTaskMainQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 定时任务管理业务服务
 *
 * @author Eric
 * @date 2026/8/15
 * @description 承载「定时任务管理」页面的业务：列表查询、编辑（名称/cron/参数列表值，
 *              提交时校验 cron 合法性）、单条/批量启停用、手动执行、中止当前任务。
 *              调度注册/取消委托 {@link SysTaskSchedulerService}。
 */
@Service
public class SysTaskMainService extends BaseService<SysTaskMain, SysTaskMainDao> {

    private final SysTaskSchedulerService schedulerService;

    private final ObjectMapper objectMapper;

    public SysTaskMainService(SysTaskMainDao baseDao, SysTaskSchedulerService schedulerService,
                              ObjectMapper objectMapper) {
        super(baseDao);
        this.schedulerService = schedulerService;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询任务列表：筛选名称（模糊）、状态、是否启用；按登记时间排序（默认降序）。
     */
    public List<SysTaskMain> list(SysTaskMainQuery query, Pages pages) {
        if (null == pages) {
            pages = new Pages();
        }
        if (pages.getPageNo() < 1) {
            pages.setPageNo(1);
        }
        if (pages.getPageSize() < 1) {
            pages.setPageSize(10);
        }
        return getBaseDao().selectList(toCriteria(query), pages);
    }

    /**
     * 统计任务总数（与 {@link #list} 条件一致，供分页 metadata.total 使用）
     */
    public long count(SysTaskMainQuery query) {
        return getBaseDao().selectCount(toCriteria(query));
    }

    private static SysTaskMain toCriteria(SysTaskMainQuery query) {
        SysTaskMain criteria = new SysTaskMain();
        if (null != query) {
            criteria.setFieldName(trimToNull(query.getFieldName()));
            criteria.setFieldStatus(trimToNull(query.getFieldStatus()));
            criteria.setFieldEnable(query.getFieldEnable());
        }
        return criteria;
    }

    /**
     * 编辑定时任务：仅维护 名称、cron表达式、参数列表值 三个字段；
     * 提交时校验 cron 合法性；cron 变更后若任务启用则刷新调度。
     */
    @Transactional
    public SysTaskMain updateTask(SysTaskMain main) {
        if (null == main || StringUtil.isEmpty(main.getFieldId())) {
            throw new IllegalArgumentException("fieldId 不能为空");
        }
        SysTaskMain exist = getBaseDao().selectById(main.getFieldId());
        if (null == exist) {
            throw new IllegalArgumentException("任务不存在: " + main.getFieldId());
        }
        if (StringUtil.isEmpty(main.getFieldName())) {
            throw new IllegalArgumentException("任务名不能为空");
        }
        schedulerService.validateCron(main.getFieldCron());

        SysTaskMain update = new SysTaskMain();
        update.setFieldId(exist.getFieldId());
        update.setFieldName(main.getFieldName().trim());
        update.setFieldCron(main.getFieldCron().trim());
        // 参数列表值以 JSON 对象字符串保存（值均为字符串），空则置为 '{}'，提交时校验合法性
        update.setFieldParams(StringUtil.isEmpty(main.getFieldParams()) ? "{}" : requireValidParamsJson(main.getFieldParams()));
        update(update);

        // cron 变更后刷新调度（启用状态下）
        if (Boolean.TRUE.equals(exist.getFieldEnable())) {
            schedulerService.schedule(exist.getFieldId());
        }
        return getBaseDao().selectById(exist.getFieldId());
    }

    /**
     * 启用/停用：仅更新「是否启用」状态并同步调度。
     */
    @Transactional
    public void setEnable(String fieldId, boolean enable) {
        if (StringUtil.isEmpty(fieldId)) {
            throw new IllegalArgumentException("fieldId 不能为空");
        }
        SysTaskMain exist = getBaseDao().selectById(fieldId);
        if (null == exist) {
            throw new IllegalArgumentException("任务不存在: " + fieldId);
        }
        SysTaskMain update = new SysTaskMain();
        update.setFieldId(fieldId);
        update.setFieldEnable(enable);
        update(update);
        if (enable) {
            schedulerService.schedule(fieldId);
        } else {
            schedulerService.cancel(fieldId);
        }
    }

    /**
     * 批量启用/停用：仅更新「是否启用」状态并逐个同步调度。
     */
    @Transactional
    public void batchSetEnable(List<String> ids, boolean enable) {
        if (null == ids || ids.isEmpty()) {
            throw new IllegalArgumentException("ids 不能为空");
        }
        getBaseDao().updateEnableBatch(ids, enable);
        for (String id : ids) {
            if (enable) {
                schedulerService.schedule(id);
            } else {
                schedulerService.cancel(id);
            }
        }
    }

    /**
     * 手动执行：cron 调度之外立即执行一次；任务正在运行则拒绝。
     */
    public void executeNow(String fieldId) {
        requireId(fieldId);
        schedulerService.executeNow(fieldId);
    }

    /**
     * 中止当前任务实例：停止当前任务实例，任务按「失败」结果记录。
     */
    public void stop(String fieldId) {
        requireId(fieldId);
        schedulerService.stop(fieldId);
    }

    private static void requireId(String fieldId) {
        if (StringUtil.isEmpty(fieldId)) {
            throw new IllegalArgumentException("fieldId 不能为空");
        }
    }

    /**
     * 校验参数 JSON：必须是合法的 JSON 对象（值按字符串保存），非法时抛出异常。
     */
    private String requireValidParamsJson(String paramsJson) {
        try {
            JsonNode node = objectMapper.readTree(paramsJson);
            if (null == node || !node.isObject()) {
                throw new IllegalArgumentException("参数必须为 JSON 对象格式");
            }
            return paramsJson;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("参数不是合法的 JSON 格式: " + e.getMessage());
        }
    }

    private static String trimToNull(String value) {
        return StringUtil.isEmpty(value) ? null : value.trim();
    }
}
