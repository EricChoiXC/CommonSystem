package com.common.system.sys.att.controller;

import com.common.system.common.base.controller.BaseCrudController;
import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.base.model.Pages;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.sys.att.model.SysAttOperation;
import com.common.system.sys.att.request.SysAttOperationRequest;
import com.common.system.sys.att.resolver.AttResolver;
import com.common.system.sys.att.service.SysAttOperationService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.common.system.sys.att.properties.SysAttConstants.ACTION_OPERATION_LIST;

/**
 * 附件操作日志接口
 *
 * @author Eric
 * @date 2026/8/7
 * @description 后台管理-附件操作日志查看：从附件列表跳转进入该附件的操作日志记录，
 *              按 field_att_id 查询，要求 SYS_ATT_MAIN 权限。
 */
@RestController
@RequestMapping("/backend/sys/att/operation")
public class SysAttOperationController extends BaseCrudController<SysAttOperation, SysAttOperationService, AttResolver> {

    public SysAttOperationController(SysAttOperationService baseService, AttResolver baseResolver,
                                     ApplicationEventPublisher eventPublisher) {
        super(baseService, baseResolver, eventPublisher);
    }

    /**
     * 操作日志列表查询：POST /backend/sys/att/operation/list
     * 请求 body.fieldAttId 必填；总数经 metadata.total 返回；
     * sortOrder 控制操作时间排序方向（asc / desc，默认降序）。
     */
    @PostMapping("/list")
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
