package com.eric.base.sys.task.controller;

import com.eric.base.common.base.controller.BaseCrudController;
import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.sys.task.model.SysTaskHistory;
import com.eric.base.sys.task.request.SysTaskHistoryQuery;
import com.eric.base.sys.task.request.SysTaskHistoryRequest;
import com.eric.base.sys.task.resolver.TaskResolver;
import com.eric.base.sys.task.response.SysTaskHistoryVo;
import com.eric.base.sys.task.service.SysTaskHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.eric.base.sys.task.properties.SysTaskConstants.ACTION_HISTORY_LIST;
import static com.eric.base.sys.task.properties.SysTaskConstants.ACTION_HISTORY_LOAD;

/**
 * 任务执行记录接口
 *
 * @author Eric
 * @date 2026/8/15
 * @description 后台管理-执行记录（仅 SYS_TASK_ADMIN）：执行记录分页查询与详情，只读。
 *              列表接口以权限动作鉴权，详情接口以目标记录 ID 为文档 ID 完成最终鉴权。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/task/history")
@Tag(name = "定时任务-执行记录", description = "后台管理-执行记录（仅 SYS_TASK_ADMIN）：执行记录分页查询与详情，只读")
public class SysTaskHistoryController extends BaseCrudController<SysTaskHistory, SysTaskHistoryService, TaskResolver> {

    public SysTaskHistoryController(SysTaskHistoryService baseService, TaskResolver baseResolver,
                                    ApplicationEventPublisher eventPublisher) {
        super(baseService, baseResolver, eventPublisher);
    }

    /**
     * 执行记录列表：POST /backend/sys/task/history/list
     * 请求体 { body: { fieldTaskName, fieldResult, fieldStartTimeStart, fieldStartTimeEnd,
     *                  fieldEndTimeStart, fieldEndTimeEnd },
     *           pages: { pageNo, pageSize, needPage }, sortField, sortOrder }；
     * 排序字段 startTime / endTime（默认开始时间降序），总数经 metadata.total 返回。
     *
     * @param request 列表请求（条件 + 分页 + 单条件排序），排序字段取 startTime / endTime
     * @return 统一响应，data 为执行记录列表，metadata.total 为总数
     */
    @PostMapping("/list")
    @Operation(summary = "执行记录列表", description = "鉴权动作 sys.task.history.list（仅 SYS_TASK_ADMIN）；请求体 "
            + "{ body: { fieldTaskName, fieldResult, fieldStartTimeStart, fieldStartTimeEnd, fieldEndTimeStart, "
            + "fieldEndTimeEnd }, pages, sortField, sortOrder }，排序字段取 startTime / endTime，默认开始时间降序")
    public BaseResponse<List<SysTaskHistoryVo>> list(@RequestBody SysTaskHistoryRequest request) {
        authorize(new AuthorizationContext(ACTION_HISTORY_LIST));
        SysTaskHistoryQuery query = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysTaskHistoryVo> list = getBaseService().listHistory(query, pages,
                null == request ? null : request.getSortField(),
                null == request ? null : request.getSortOrder());
        long total = getBaseService().countHistory(query);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 执行记录详情：GET /backend/sys/task/history/load?fieldId=${id}
     * 以目标记录 ID 为文档 ID 完成最终鉴权；返回任务名、结果、起止时间、执行组、参数与日志。
     *
     * @param fieldId 执行记录 ID
     * @return 统一响应，data 为执行记录详情；不存在返回 E_NOT_FOUND
     */
    @GetMapping("/load")
    @Operation(summary = "执行记录详情", description = "鉴权动作 sys.task.history.load（仅 SYS_TASK_ADMIN）；"
            + "返回任务名、结果、起止时间、执行组、参数与日志")
    public BaseResponse<SysTaskHistoryVo> load(
            @Parameter(description = "执行记录 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_HISTORY_LOAD, fieldId);
        SysTaskHistoryVo detail = getBaseService().detail(fieldId);
        if (null == detail) {
            return BaseResponse.failure("E_NOT_FOUND", "未找到该执行记录");
        }
        return BaseResponse.success(detail, null);
    }
}
