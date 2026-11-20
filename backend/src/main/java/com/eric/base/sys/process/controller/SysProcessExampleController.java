package com.eric.base.sys.process.controller;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.resolver.AuthorizationHelper;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.process.request.SysProcessAdminOperateRequest;
import com.eric.base.sys.process.request.SysProcessExampleQuery;
import com.eric.base.sys.process.request.SysProcessExampleRequest;
import com.eric.base.sys.process.resolver.ProcessResolver;
import com.eric.base.sys.process.response.SysProcessExampleDetailVo;
import com.eric.base.sys.process.response.SysProcessExamplePanelVo;
import com.eric.base.sys.process.response.SysProcessExampleVo;
import com.eric.base.sys.process.response.SysProcessTemplateModelVo;
import com.eric.base.sys.process.service.SysProcessExampleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_ADMIN_OPERATE;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_LIST;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_LOAD;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_MODELS;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_MONITOR_LIST;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_PANEL;

/**
 * 流程实例接口
 *
 * @author Eric
 * @date 2026/8/23
 * @description 后台管理-流程管理-流程实例的后端入口（依据 docs/设计文档/sys/process/流程核心业务.md「流程实例和流转」）：
 *              <ul>
 *              <li>查看类操作（list/load/panel）：SYS_PROCESS_EXAMPLE_VIEWER 及以上；</li>
 *              <li>特权操作（adminOperate）与流程监控列表（monitorList，v1_7 新增）：仅 SYS_PROCESS_EXAMPLE_ADMIN。</li>
 *              </ul>
 *              流程的创建/提交/审批由实例所属业务模块调用 {@link SysProcessExampleService} 完成，
 *              本 Controller 不暴露（依据「流程创建和提交」「流程审批面板」）。所有接口执行核心业务逻辑前
 *              先通过 ProcessResolver 完成鉴权；接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/process/example")
@Tag(name = "流程-流程实例", description = "后台管理-流程管理-流程实例：所属模块、列表、详情、审批面板、特权操作、流程监控列表；查看需 SYS_PROCESS_EXAMPLE_VIEWER 及以上，特权操作与监控列表仅 SYS_PROCESS_EXAMPLE_ADMIN")
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
     *
     * @return 统一响应，data 为所属模块（process_template 配置项）列表
     */
    @GetMapping("/models")
    @Operation(summary = "所属模块选项", description = "鉴权动作 sys.process.example.models（SYS_PROCESS_EXAMPLE_VIEWER "
            + "及以上）；返回 process_template 配置项，供流程实例页「所属模块」筛选下拉")
    public BaseResponse<List<SysProcessTemplateModelVo>> models() {
        authorizationHelper.authorize(baseResolver, new AuthorizationContext(ACTION_PROCESS_EXAMPLE_MODELS));
        return BaseResponse.success(baseService.listModels(), null);
    }

    /**
     * 流程实例列表：POST /backend/sys/process/example/list
     * 请求体为 { body: { fieldName, fieldCode, fieldModelName, fieldCreatorName,
     *           fieldCreateTimeBegin, fieldCreateTimeEnd, fieldStatus, scope }, pages: { pageNo, pageSize, needPage },
     *           sortField, sortOrder }，总数经 metadata.total 返回。
     * v1_7 扩展数据范围 scope（P17-BE-015）：admin（默认全量）/ myCreated（我发起的）/
     * myJoined（我参与的）；缺省或非法值一律按 admin 处理（归一在 Service 层），
     * my* 维度由 Mapper 强制注入会话用户本人过滤条件（不信任请求体传入的用户标识）。
     *
     * @param request 列表请求（条件 + 分页 + 单条件排序），排序字段取 createTime
     * @return 统一响应，data 为流程实例列表，metadata.total 为总数
     */
    @PostMapping("/list")
    @Operation(summary = "流程实例列表", description = "鉴权动作 sys.process.example.list（SYS_PROCESS_EXAMPLE_VIEWER "
            + "及以上）；请求体 body.scope 取 admin/myCreated/myJoined，my* 强制本人数据过滤")
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
     * 流程监控列表：POST /backend/sys/process/example/monitorList（v1_7 新增，P17-BE-015）
     * 请求体同列表（body 含列表筛选字段 + inFlightOnly / exceptionOnly 监控筛选 + pages / sortField / sortOrder），
     * 总数经 metadata.total 返回。管理员全量视图（不做本人数据过滤），行内透出 exceptionFlag（是否异常实例：
     * 存在 SYSTEM_ERROR 审批留痕或状态 PAUSE）与当前节点名称。
     *
     * @param request 监控列表请求（条件 + 分页 + 单条件排序），排序字段取 createTime
     * @return 统一响应，data 为流程实例列表，metadata.total 为总数
     */
    @PostMapping("/monitorList")
    @Operation(summary = "流程监控列表", description = "鉴权动作 sys.process.example.monitorList（仅 "
            + "SYS_PROCESS_EXAMPLE_ADMIN）；流程监控列表，支持在途/异常筛选")
    public BaseResponse<List<SysProcessExampleVo>> monitorList(@RequestBody SysProcessExampleRequest request) {
        authorizationHelper.authorize(baseResolver, new AuthorizationContext(ACTION_PROCESS_EXAMPLE_MONITOR_LIST));
        SysProcessExampleQuery query = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysProcessExampleVo> list = baseService.monitorList(query, pages,
                null == request ? null : request.getSortField(),
                null == request ? null : request.getSortOrder());
        long total = baseService.monitorCount(query);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 流程实例详情：GET /backend/sys/process/example/load?fieldId=${id}
     * 返回流程实例基本字段 + 流程实例节点 + 工作项 + 审批记录。
     *
     * @param fieldId 流程实例 ID
     * @return 统一响应，data 为流程实例详情；不存在返回 E_NOT_FOUND
     */
    @GetMapping("/load")
    @Operation(summary = "流程实例详情", description = "鉴权动作 sys.process.example.load（SYS_PROCESS_EXAMPLE_VIEWER "
            + "及以上）；返回流程实例基本字段 + 节点 + 工作项 + 审批记录")
    public BaseResponse<SysProcessExampleDetailVo> load(
            @Parameter(description = "流程实例 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorizationHelper.authorize(baseResolver, ACTION_PROCESS_EXAMPLE_LOAD, fieldId);
        SysProcessExampleDetailVo vo = baseService.load(fieldId);
        if (null == vo) {
            return BaseResponse.failure("E_NOT_FOUND", "未找到该流程实例");
        }
        return BaseResponse.success(vo, null);
    }

    /**
     * 流程审批面板数据：GET /backend/sys/process/example/panel?fieldId=${id}
     * 返回流程基本信息 + 节点 + 审批记录 + 当前用户可执行审批操作按钮集（供业务文档页嵌入审批组件展示）。
     * v1_7 起 operations 升级为按钮对象数组（按节点 operations 配置 ∩ 用户身份 ∩ 状态前置计算），
     * nodeOps 业务标识语义不变。
     *
     * @param fieldId 流程实例 ID
     * @return 统一响应，data 为审批面板数据；不存在返回 E_NOT_FOUND
     */
    @GetMapping("/panel")
    @Operation(summary = "流程审批面板数据", description = "鉴权动作 sys.process.example.panel"
            + "（SYS_PROCESS_EXAMPLE_VIEWER 及以上）；返回面板数据，operations 为按节点配置 ∩ 用户身份"
            + "计算的按钮集（按钮对象数组，元素含 operateType/operateName/defaultContent，"
            + "handler_reject 附 rejectTargets、handler_pass/drafter_submit 附 flowChoices），"
            + "nodeOps 语义不变")
    public BaseResponse<SysProcessExamplePanelVo> panel(
            @Parameter(description = "流程实例 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorizationHelper.authorize(baseResolver, ACTION_PROCESS_EXAMPLE_PANEL, fieldId);
        SysProcessExamplePanelVo vo = baseService.panel(fieldId);
        if (null == vo) {
            return BaseResponse.failure("E_NOT_FOUND", "未找到该流程实例");
        }
        return BaseResponse.success(vo, null);
    }

    /**
     * 流程实例特权操作：POST /backend/sys/process/example/adminOperate
     * 请求体为 { fieldId, operateType, targetNodeId, targetNodeKey, workitemId, changeToId, content }；
     * 仅 SYS_PROCESS_EXAMPLE_ADMIN 可执行。
     * operateType 取 JUMP / FINISH / ABANDON / PAUSE / RESUME / CHANGE_HANDLER（v1_7 新增）；
     * JUMP 时 targetNodeId 与 targetNodeKey 二者其一必填（targetNodeId 优先）；
     * CHANGE_HANDLER 时 workitemId / changeToId 必填。
     *
     * @param body 特权操作请求 { fieldId, operateType, targetNodeId, targetNodeKey, workitemId, changeToId, content }
     * @return 统一响应；缺 fieldId 返回 BAD_REQUEST
     */
    @PostMapping("/adminOperate")
    @Operation(summary = "流程实例特权操作", description = "鉴权动作 sys.process.example.adminOperate（仅 "
            + "SYS_PROCESS_EXAMPLE_ADMIN）；operateType 取 JUMP/FINISH/ABANDON/PAUSE/RESUME/CHANGE_HANDLER；"
            + "JUMP 时 targetNodeId 与 targetNodeKey 二者其一必填（targetNodeId 优先），"
            + "CHANGE_HANDLER 时 workitemId/changeToId 必填，意见必填")
    public BaseResponse<Void> adminOperate(@RequestBody SysProcessAdminOperateRequest body) {
        if (null == body || StringUtil.isEmpty(body.getFieldId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId is required");
        }
        authorizationHelper.authorize(baseResolver, ACTION_PROCESS_EXAMPLE_ADMIN_OPERATE, body.getFieldId());
        baseService.adminOperate(body);
        return BaseResponse.success(null, null);
    }
}
