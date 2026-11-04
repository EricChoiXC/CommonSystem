package com.common.system.sys.task.controller;

import com.common.system.common.base.controller.BaseCrudController;
import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.base.model.Pages;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.sys.task.model.SysTaskMain;
import com.common.system.sys.task.request.SysTaskBatchRequest;
import com.common.system.sys.task.request.SysTaskMainRequest;
import com.common.system.sys.task.request.SysTaskMainQuery;
import com.common.system.sys.task.resolver.TaskResolver;
import com.common.system.sys.task.service.SysTaskMainService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.common.system.sys.task.properties.SysTaskConstants.*;

/**
 * 定时任务管理接口
 *
 * @author Eric
 * @date 2026/8/15
 * @description 后台管理-定时任务管理（仅 SYS_TASK_ADMIN）：任务列表、编辑回填、编辑、
 *              启用/停用、批量启用/停用、手动执行、中止当前任务。
 *              所有接口执行核心业务逻辑前先通过 TaskResolver 完成鉴权；
 *              接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}。
 */
@RestController
@RequestMapping("/backend/sys/task/main")
public class SysTaskMainController extends BaseCrudController<SysTaskMain, SysTaskMainService, TaskResolver> {

    public SysTaskMainController(SysTaskMainService baseService, TaskResolver baseResolver,
                                 ApplicationEventPublisher eventPublisher) {
        super(baseService, baseResolver, eventPublisher);
    }

    /**
     * 任务列表：POST /backend/sys/task/main/list
     * 请求体 { body: { fieldName, fieldStatus, fieldEnable }, pages: { pageNo, pageSize, needPage } }；
     * 按登记时间排序（默认降序），总数经 metadata.total 返回。
     */
    @PostMapping("/list")
    public BaseResponse<List<SysTaskMain>> list(@RequestBody SysTaskMainRequest request) {
        authorize(new AuthorizationContext(ACTION_LIST));
        SysTaskMainQuery query = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysTaskMain> list = getBaseService().list(query, pages);
        long total = getBaseService().count(query);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 任务详情：GET /backend/sys/task/main/load?fieldId=${id}（编辑页面回填）
     */
    @GetMapping("/load")
    public BaseResponse<SysTaskMain> load(@RequestParam("fieldId") String fieldId) {
        authorize(ACTION_LOAD, fieldId);
        return BaseResponse.success(getBaseService().selectById(fieldId), null);
    }

    /**
     * 编辑任务：PATCH /backend/sys/task/main/update
     * 请求体 { fieldId, fieldName, fieldCron, fieldParams }；提交时校验 cron 合法性；
     * 仅更新 名称 / cron / 参数列表值 三个字段。
     */
    @PatchMapping("/update")
    public BaseResponse<SysTaskMain> updateTask(@RequestBody SysTaskMain main) {
        authorize(ACTION_UPDATE, null == main ? null : main.getFieldId());
        return BaseResponse.success(getBaseService().updateTask(main), null);
    }

    /**
     * 启用：PATCH /backend/sys/task/main/enable?fieldId=${id}（仅更新是否启用并注册调度）
     */
    @PatchMapping("/enable")
    public BaseResponse<Void> enable(@RequestParam("fieldId") String fieldId) {
        authorize(ACTION_ENABLE, fieldId);
        getBaseService().setEnable(fieldId, true);
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 停用：PATCH /backend/sys/task/main/disable?fieldId=${id}（仅更新是否启用并取消调度）
     */
    @PatchMapping("/disable")
    public BaseResponse<Void> disable(@RequestParam("fieldId") String fieldId) {
        authorize(ACTION_DISABLE, fieldId);
        getBaseService().setEnable(fieldId, false);
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 批量启用：POST /backend/sys/task/main/batchEnable，请求体 { ids: string[] }
     */
    @PostMapping("/batchEnable")
    public BaseResponse<Void> batchEnable(@RequestBody SysTaskBatchRequest request) {
        authorize(new AuthorizationContext(ACTION_BATCH_ENABLE));
        getBaseService().batchSetEnable(idsOf(request), true);
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 批量停用：POST /backend/sys/task/main/batchDisable，请求体 { ids: string[] }
     */
    @PostMapping("/batchDisable")
    public BaseResponse<Void> batchDisable(@RequestBody SysTaskBatchRequest request) {
        authorize(new AuthorizationContext(ACTION_BATCH_DISABLE));
        getBaseService().batchSetEnable(idsOf(request), false);
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 手动执行：POST /backend/sys/task/main/execute?fieldId=${id}
     * cron 调度之外立即执行一次；任务正在运行则拒绝。
     */
    @PostMapping("/execute")
    public BaseResponse<Void> execute(@RequestParam("fieldId") String fieldId) {
        authorize(ACTION_EXECUTE, fieldId);
        getBaseService().executeNow(fieldId);
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 中止当前任务：POST /backend/sys/task/main/stop?fieldId=${id}
     * 停止当前任务实例，当前任务按「失败」结果记录。
     */
    @PostMapping("/stop")
    public BaseResponse<Void> stop(@RequestParam("fieldId") String fieldId) {
        authorize(ACTION_STOP, fieldId);
        getBaseService().stop(fieldId);
        return BaseResponse.<Void>success("success", null, null);
    }

    private static List<String> idsOf(SysTaskBatchRequest request) {
        return null == request ? null : request.getIds();
    }
}
