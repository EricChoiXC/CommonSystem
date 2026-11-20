package com.eric.base.sys.process.controller;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.resolver.AuthorizationHelper;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.process.request.SysProcessOperateRequest;
import com.eric.base.sys.process.resolver.ProcessResolver;
import com.eric.base.sys.process.service.SysProcessExampleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_OPERATE;

/**
 * 流程审批操作接口
 *
 * @author Eric
 * @date 2026/11/20
 * @description 14 类审批操作的统一入口（依据 docs/设计文档/sys/process/流程v1_7接口清单.md §6）：
 *              resolver 层准入为 SYS_PROCESS_EXAMPLE_VIEWER 及以上（鉴权动作
 *              sys.process.example.operate），另需引擎内操作人身份校验
 *              （起草人 / 当前处理人 / 已处理人 / 流程管理员，双层鉴权）。
 *              业务模块不经本 HTTP 入口：其 audit 转发通道为服务内调用
 *              {@link SysProcessExampleService#operate(SysProcessOperateRequest, String...)}。
 *              接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/process/operation")
@Tag(name = "流程-审批操作", description = "后台管理/业务单据-流程审批操作：14 类统一操作入口；"
        + "鉴权动作 sys.process.example.operate，另需操作人身份校验")
public class SysProcessOperationController {

    private final SysProcessExampleService baseService;
    private final ProcessResolver baseResolver;
    private final AuthorizationHelper authorizationHelper;

    public SysProcessOperationController(SysProcessExampleService baseService,
                                         ProcessResolver baseResolver,
                                         AuthorizationHelper authorizationHelper) {
        this.baseService = baseService;
        this.baseResolver = baseResolver;
        this.authorizationHelper = authorizationHelper;
    }

    /**
     * 流程审批统一操作：POST /backend/sys/process/operation/operate
     * 请求体为 { exampleId, operateType, workitemId, content, targetNodeId, rejectTargetKey,
     * flowChoice, transferToIds, changeToId, resumeMode, ccToIds }；14 类操作统一入口，
     * operateType 取值见流程v1_7接口清单.md §6.2 操作矩阵。
     *
     * @param body 统一操作请求
     * @return 统一响应（成功即流转已完成）；缺 exampleId 返回 BAD_REQUEST
     */
    @PostMapping("/operate")
    @Operation(summary = "流程审批统一操作", description = "鉴权动作 sys.process.example.operate（SYS_PROCESS_EXAMPLE_VIEWER "
            + "及以上 + 引擎内操作人身份校验）；operateType 取 14 类操作标识")
    public BaseResponse<Void> operate(@RequestBody SysProcessOperateRequest body) {
        if (null == body || StringUtil.isEmpty(body.getExampleId())) {
            return BaseResponse.failure("BAD_REQUEST", "exampleId is required");
        }
        authorizationHelper.authorize(baseResolver, ACTION_PROCESS_EXAMPLE_OPERATE, body.getExampleId());
        baseService.operate(body);
        return BaseResponse.success(null, null);
    }
}
