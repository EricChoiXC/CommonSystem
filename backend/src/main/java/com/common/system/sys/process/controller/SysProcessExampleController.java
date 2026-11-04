package com.common.system.sys.process.controller;

import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.base.model.Pages;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.resolver.AuthorizationHelper;
import com.common.system.common.utils.StringUtil;
import com.common.system.sys.process.request.SysProcessAdminOperateRequest;
import com.common.system.sys.process.request.SysProcessExampleQuery;
import com.common.system.sys.process.request.SysProcessExampleRequest;
import com.common.system.sys.process.resolver.ProcessResolver;
import com.common.system.sys.process.response.SysProcessExampleDetailVo;
import com.common.system.sys.process.response.SysProcessExamplePanelVo;
import com.common.system.sys.process.response.SysProcessExampleVo;
import com.common.system.sys.process.response.SysProcessTemplateModelVo;
import com.common.system.sys.process.service.SysProcessExampleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.common.system.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_ADMIN_OPERATE;
import static com.common.system.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_LIST;
import static com.common.system.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_LOAD;
import static com.common.system.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_MODELS;
import static com.common.system.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_PANEL;

/**
 * 流程实例接口
 *
 * @author Eric
 * @date 2026/8/23
 * @description 后台管理-流程管理-流程实例的后端入口（依据 artificial/docs/设计文档/sys/process/流程核心业务.md「流程实例和流转」）：
 *              <ul>
 *              <li>查看类操作（list/load/panel）：SYS_PROCESS_EXAMPLE_VIEWER 及以上；</li>
 *              <li>特权操作（adminOperate）：仅 SYS_PROCESS_EXAMPLE_ADMIN。</li>
 *              </ul>
 *              流程的创建/提交/审批由实例所属业务模块调用 {@link SysProcessExampleService} 完成，
 *              本 Controller 不暴露（依据「流程创建和提交」「流程审批面板」）。所有接口执行核心业务逻辑前
 *              先通过 ProcessResolver 完成鉴权；接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}。
 */
@RestController
@RequestMapping("/backend/sys/process/example")
public class SysProcessExampleController {

    private final SysProcessExampleService baseService;
    private final ProcessResolver baseResolver;
    private final AuthorizationHelper authorizationHelper;

    public SysProcessExampleController(SysProcessExampleService baseService,
                                       ProcessResolver baseResolver,
                                       AuthorizationHelper authorizationHelper) {
        this.baseService = baseService;
        this.baseResolver = baseResolver;
        this.authorizationHelper = authorizationHelper;
    }

    /**
     * 流程实例所属模块选项：GET /backend/sys/process/example/models
     * 返回 process_template 配置项列表（流程实例页「所属模块」筛选下拉）。
     */
    @GetMapping("/models")
    public BaseResponse<List<SysProcessTemplateModelVo>> models() {
        authorizationHelper.authorize(baseResolver, new AuthorizationContext(ACTION_PROCESS_EXAMPLE_MODELS));
        return BaseResponse.success(baseService.listModels(), null);
    }

    /**
     * 流程实例列表：POST /backend/sys/process/example/list
     * 请求体为 { body: { fieldName, fieldCode, fieldModelName, fieldCreatorName,
     *           fieldCreateTimeBegin, fieldCreateTimeEnd, fieldStatus }, pages: { pageNo, pageSize, needPage },
     *           sortField, sortOrder }，总数经 metadata.total 返回。
     */
    @PostMapping("/list")
    public BaseResponse<List<SysProcessExampleVo>> list(@RequestBody SysProcessExampleRequest request) {
        authorizationHelper.authorize(baseResolver, new AuthorizationContext(ACTION_PROCESS_EXAMPLE_LIST));
        SysProcessExampleQuery query = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysProcessExampleVo> list = baseService.list(query, pages,
                null == request ? null : request.getSortField(),
                null == request ? null : request.getSortOrder());
        long total = baseService.count(query);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 流程实例详情：GET /backend/sys/process/example/load?fieldId=${id}
     * 返回流程实例基本字段 + 流程实例节点 + 工作项 + 审批记录。
     */
    @GetMapping("/load")
    public BaseResponse<SysProcessExampleDetailVo> load(@RequestParam("fieldId") String fieldId) {
        authorizationHelper.authorize(baseResolver, ACTION_PROCESS_EXAMPLE_LOAD, fieldId);
        SysProcessExampleDetailVo vo = baseService.load(fieldId);
        if (null == vo) {
            return BaseResponse.failure("E_NOT_FOUND", "未找到该流程实例");
        }
        return BaseResponse.success(vo, null);
    }

    /**
     * 流程审批面板数据：GET /backend/sys/process/example/panel?fieldId=${id}
     * 返回流程基本信息 + 节点 + 审批记录 + 当前用户可执行审批操作（供业务文档页嵌入审批组件展示）。
     */
    @GetMapping("/panel")
    public BaseResponse<SysProcessExamplePanelVo> panel(@RequestParam("fieldId") String fieldId) {
        authorizationHelper.authorize(baseResolver, ACTION_PROCESS_EXAMPLE_PANEL, fieldId);
        SysProcessExamplePanelVo vo = baseService.panel(fieldId);
        if (null == vo) {
            return BaseResponse.failure("E_NOT_FOUND", "未找到该流程实例");
        }
        return BaseResponse.success(vo, null);
    }

    /**
     * 流程实例特权操作：POST /backend/sys/process/example/adminOperate
     * 请求体为 { fieldId, operateType, targetNodeId, content }；仅 SYS_PROCESS_EXAMPLE_ADMIN 可执行。
     */
    @PostMapping("/adminOperate")
    public BaseResponse<Void> adminOperate(@RequestBody SysProcessAdminOperateRequest body) {
        if (null == body || StringUtil.isEmpty(body.getFieldId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId is required");
        }
        authorizationHelper.authorize(baseResolver, ACTION_PROCESS_EXAMPLE_ADMIN_OPERATE, body.getFieldId());
        baseService.adminOperate(body);
        return BaseResponse.success(null, null);
    }
}
