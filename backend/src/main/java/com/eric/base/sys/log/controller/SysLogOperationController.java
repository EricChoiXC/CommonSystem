package com.eric.base.sys.log.controller;

import com.eric.base.common.base.controller.BaseCrudController;
import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.cache.ModelCacheService;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.sys.log.model.SysLogOperation;
import com.eric.base.sys.log.request.SysLogOperationRequest;
import com.eric.base.sys.log.resolver.LogResolver;
import com.eric.base.sys.log.service.SysLogOperationService;
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

import static com.eric.base.sys.log.properties.SysLogConstants.ACTION_LIST;
import static com.eric.base.sys.log.properties.SysLogConstants.ACTION_LOAD;

/**
 * 日志管理接口
 *
 * @author Eric
 * @date 2026/8/8
 * @description 后台管理-日志查看（仅 SYS_LOG_ADMIN）：操作日志 / 登录日志分页查询与详情。
 *              日志只读，不提供删除、编辑；列表查询以日志类型为预判（列表页无单一文档 ID），
 *              详情接口以目标日志 ID 为文档 ID 完成最终鉴权。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/log/operation")
@Tag(name = "日志-操作日志", description = "后台管理：操作日志与登录日志查询（只读）；仅 SYS_LOG_ADMIN")
public class SysLogOperationController extends BaseCrudController<SysLogOperation, SysLogOperationService, LogResolver> {

    private final ModelCacheService modelCacheService;

    public SysLogOperationController(SysLogOperationService baseService, LogResolver baseResolver,
                                     ApplicationEventPublisher eventPublisher,
                                     ModelCacheService modelCacheService) {
        super(baseService, baseResolver, eventPublisher);
        this.modelCacheService = modelCacheService;
    }

    /**
     * 日志列表查询：POST /backend/sys/log/operation/list
     * body.fieldLogType 必填（1=操作日志 2=登录日志）；总数经 metadata.total 返回；
     * sortOrder 控制操作时间排序方向（asc / desc，默认降序）。
     *
     * @param request 日志列表请求，body.fieldLogType 必填，可选 operateTimeStart / operateTimeEnd
     * @return 统一响应，data 为日志列表（fieldModuleName 已按 MODEL_NAME 翻译），metadata.total 为总数
     */
    @PostMapping("/list")
    @Operation(summary = "日志列表查询", description = "鉴权动作 sys.log.operation.list（仅 SYS_LOG_ADMIN）；"
            + "body.fieldLogType 必填（1=操作日志 2=登录日志），sortOrder 控制操作时间方向"
            + "（asc / desc，默认降序），总数经 metadata.total 返回")
    public BaseResponse<List<SysLogOperation>> selectList(@RequestBody SysLogOperationRequest request) {
        authorize(new AuthorizationContext(ACTION_LIST));
        SysLogOperation criteria = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysLogOperation> list = getBaseService().listLog(criteria, pages,
                null == request ? null : request.getSortOrder(),
                null == request ? null : request.getOperateTimeStart(),
                null == request ? null : request.getOperateTimeEnd());
        long total = getBaseService().countLog(criteria,
                null == request ? null : request.getOperateTimeStart(),
                null == request ? null : request.getOperateTimeEnd());
        list.forEach(item -> item.setFieldModuleName(modelCacheService.resolveModelName(item.getFieldModule())));
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 日志详情：GET /backend/sys/log/operation/load?fieldId=${id}
     * 以目标日志 ID 为文档 ID 完成最终鉴权；返回含失败原因与变更内容（before / after）。
     *
     * @param fieldId 日志 ID
     * @return 统一响应，data 为日志详情（fieldModuleName 已翻译）；不存在返回 E_NOT_FOUND
     */
    @GetMapping("/load")
    @Operation(summary = "日志详情", description = "鉴权动作 sys.log.operation.load（仅 SYS_LOG_ADMIN）；"
            + "返回失败原因与变更内容（before / after），fieldModuleName 按 MODEL_NAME 翻译")
    public BaseResponse<SysLogOperation> load(
            @Parameter(description = "日志 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_LOAD, fieldId);
        SysLogOperation logEntry = getBaseService().selectById(fieldId);
        if (null == logEntry) {
            return BaseResponse.failure("E_NOT_FOUND", "未找到该日志");
        }
        logEntry.setFieldModuleName(modelCacheService.resolveModelName(logEntry.getFieldModule()));
        return BaseResponse.success(logEntry, null);
    }
}
