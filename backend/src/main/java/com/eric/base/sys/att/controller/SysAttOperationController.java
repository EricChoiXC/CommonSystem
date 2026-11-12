package com.eric.base.sys.att.controller;

import com.eric.base.common.base.controller.BaseCrudController;
import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.sys.att.model.SysAttOperation;
import com.eric.base.sys.att.request.SysAttOperationRequest;
import com.eric.base.sys.att.resolver.AttResolver;
import com.eric.base.sys.att.service.SysAttOperationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.eric.base.sys.att.properties.SysAttConstants.ACTION_OPERATION_LIST;

/**
 * 附件操作日志接口
 *
 * @author Eric
 * @date 2026/8/7
 * @description 后台管理-附件操作日志查看：从附件列表跳转进入该附件的操作日志记录，
 *              按 field_att_id 查询，要求 SYS_ATT_MAIN 权限。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/att/operation")
@Tag(name = "附件-操作日志", description = "后台管理：按附件 ID 查看上传/下载/预览/删除等操作记录；需要 SYS_ATT_MAIN")
public class SysAttOperationController extends BaseCrudController<SysAttOperation, SysAttOperationService, AttResolver> {

    public SysAttOperationController(SysAttOperationService baseService, AttResolver baseResolver,
                                     ApplicationEventPublisher eventPublisher) {
        super(baseService, baseResolver, eventPublisher);
    }

    /**
     * 操作日志列表查询：POST /backend/sys/att/operation/list
     * 请求 body.fieldAttId 必填；总数经 metadata.total 返回；
     * sortOrder 控制操作时间排序方向（asc / desc，默认降序）。
     *
     * @param request 操作日志列表请求，body.fieldAttId 必填
     * @return 统一响应，data 为该附件的操作日志，metadata.total 为总数
     */
    @PostMapping("/list")
    @Operation(summary = "附件操作日志列表", description = "鉴权动作 sys.att.operation.list（需要 SYS_ATT_MAIN）；"
            + "请求体 body.fieldAttId 必填，sortOrder 控制操作时间排序方向（asc / desc，默认降序）")
    public BaseResponse<List<SysAttOperation>> selectList(@RequestBody SysAttOperationRequest request) {
        authorize(new AuthorizationContext(ACTION_OPERATION_LIST));
        SysAttOperation criteria = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysAttOperation> list = getBaseService().listByAttId(criteria, pages,
                null == request ? null : request.getSortOrder());
        long total = getBaseService().countByAttId(criteria);
        return BaseResponse.success(list, Map.of("total", total));
    }
}
