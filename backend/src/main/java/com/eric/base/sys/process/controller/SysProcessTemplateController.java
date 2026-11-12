package com.eric.base.sys.process.controller;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.exception.AuthorizationException;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.resolver.AuthorizationRejectedEvent;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import com.eric.base.sys.process.request.SysProcessTemplateQuery;
import com.eric.base.sys.process.request.SysProcessTemplateRequest;
import com.eric.base.sys.process.resolver.ProcessResolver;
import com.eric.base.sys.process.response.SysProcessTemplateModelVo;
import com.eric.base.sys.process.response.SysProcessTemplateVo;
import com.eric.base.sys.process.service.SysProcessTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_MODULE_ENABLE;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_MODULE_LIST;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_MODULE_LOAD;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_MODULE_MODELS;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_MODULE_SAVE;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_MODULE_SET_DEFAULT;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_MODULE_UPDATE;

/**
 * 流程模板接口
 *
 * @author Eric
 * @date 2026/8/22
 * @description 后台管理-流程管理-模板管理的后端入口（依据 artificial/docs/设计文档/sys/process/流程核心业务.md「流程模板」）：
 *              <ul>
 *              <li>查看类操作（models/list/load）：SYS_PROCESS_TEMPLATE_VIEWER 及以上；</li>
 *              <li>写操作（save/update/enable）：仅 SYS_PROCESS_TEMPLATE_ADMIN。</li>
 *              </ul>
 *              所有接口执行核心业务逻辑前先通过 ProcessResolver 完成鉴权；
 *              接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/process/module")
@Tag(name = "流程-模板管理", description = "后台管理-流程管理-模板管理：分类、列表、详情、新建/编辑/启停用/置为默认；查看需 SYS_PROCESS_TEMPLATE_VIEWER 及以上，写操作仅 SYS_PROCESS_TEMPLATE_ADMIN")
public class SysProcessTemplateController {

    private final SysProcessTemplateService baseService;
    private final ProcessResolver baseResolver;
    private final ApplicationEventPublisher eventPublisher;

    public SysProcessTemplateController(SysProcessTemplateService baseService,
                                        ProcessResolver baseResolver,
                                        ApplicationEventPublisher eventPublisher) {
        this.baseService = baseService;
        this.baseResolver = baseResolver;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 模板分类列表：GET /backend/sys/process/module/models
     * 返回 process_template 配置项列表（左侧分类展示，选中项作为 field_model_name 固定放入查询）。
     *
     * @return 统一响应，data 为模板分类（process_template 配置项）列表
     */
    @GetMapping("/models")
    @Operation(summary = "模板分类列表", description = "鉴权动作 sys.process.module.models（SYS_PROCESS_TEMPLATE_VIEWER "
            + "及以上）；返回 process_template 配置项，供左侧分类展示")
    public BaseResponse<List<SysProcessTemplateModelVo>> models() {
        authorize(new AuthorizationContext(ACTION_PROCESS_MODULE_MODELS));
        return BaseResponse.success(baseService.listModels(), null);
    }

    /**
     * 模板列表：POST /backend/sys/process/module/list
     * 请求体为 { body: { fieldModelName, fieldName, fieldCode, enable }, pages: { pageNo, pageSize, needPage },
     *           sortField, sortOrder }，总数经 metadata.total 返回。
     *
     * @param request 列表请求（条件 + 分页 + 单条件排序），排序字段取 createTime / updateTime
     * @return 统一响应，data 为模板列表，metadata.total 为总数
     */
    @PostMapping("/list")
    @Operation(summary = "模板列表", description = "鉴权动作 sys.process.module.list（SYS_PROCESS_TEMPLATE_VIEWER "
            + "及以上）；请求体 { body: { fieldModelName, fieldName, fieldCode, enable }, pages, sortField, "
            + "sortOrder }，排序字段取 createTime / updateTime")
    public BaseResponse<List<SysProcessTemplateVo>> list(@RequestBody SysProcessTemplateRequest request) {
        authorize(new AuthorizationContext(ACTION_PROCESS_MODULE_LIST));
        SysProcessTemplateQuery query = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysProcessTemplateVo> list = baseService.list(query, pages,
                null == request ? null : request.getSortField(),
                null == request ? null : request.getSortOrder());
        long total = baseService.count(query);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 模板详情：GET /backend/sys/process/module/load?fieldId=${id}
     * 返回模板基本字段（含版本号）+ 节点列表（含审批节点配置），供详情/编辑页回填。
     *
     * @param fieldId 流程模板 ID
     * @return 统一响应，data 为模板详情（含节点与审批配置）；不存在返回 E_NOT_FOUND
     */
    @GetMapping("/load")
    @Operation(summary = "模板详情", description = "鉴权动作 sys.process.module.load（SYS_PROCESS_TEMPLATE_VIEWER "
            + "及以上）；返回模板基本字段（含版本号）与节点列表（含审批节点配置）")
    public BaseResponse<SysProcessTemplateVo> load(
            @Parameter(description = "流程模板 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_PROCESS_MODULE_LOAD, fieldId);
        SysProcessTemplateVo vo = baseService.load(fieldId);
        if (null == vo) {
            return BaseResponse.failure("E_NOT_FOUND", "未找到该流程模板");
        }
        return BaseResponse.success(vo, null);
    }

    /**
     * 新建模板：POST /backend/sys/process/module/save
     * 请求体为模板对象（含 nodes 节点列表），初始版本号 1。
     *
     * @param body 模板对象（含 nodes 节点列表）
     * @return 统一响应，data 为保存后的模板（版本号 1）
     */
    @PostMapping("/save")
    @Operation(summary = "新建模板", description = "鉴权动作 sys.process.module.save（仅 "
            + "SYS_PROCESS_TEMPLATE_ADMIN）；请求体为模板对象（含 nodes 节点列表），初始版本号 1")
    public BaseResponse<SysProcessTemplateVo> save(@RequestBody SysProcessTemplateVo body) {
        authorize(new AuthorizationContext(ACTION_PROCESS_MODULE_SAVE));
        return BaseResponse.success(baseService.save(body), null);
    }

    /**
     * 编辑模板：PATCH /backend/sys/process/module/update
     * 请求体为模板对象（必须携带 fieldId）；历史版本归档到 sys_process_template_history，
     * 现版本号 +1 并全量替换节点。
     *
     * @param body 模板对象，必须携带 fieldId
     * @return 统一响应，data 为更新后的模板；缺 fieldId 返回 BAD_REQUEST
     */
    @PatchMapping("/update")
    @Operation(summary = "编辑模板", description = "鉴权动作 sys.process.module.update（仅 "
            + "SYS_PROCESS_TEMPLATE_ADMIN）；历史版本归档到 sys_process_template_history，现版本号 +1 "
            + "并全量替换节点")
    public BaseResponse<SysProcessTemplateVo> update(@RequestBody SysProcessTemplateVo body) {
        if (null == body || StringUtil.isEmpty(body.getFieldId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId is required");
        }
        authorize(ACTION_PROCESS_MODULE_UPDATE, body.getFieldId());
        return BaseResponse.success(baseService.update(body), null);
    }

    /**
     * 启停用：PATCH /backend/sys/process/module/enable
     * 请求体为 { fieldId, fieldEnable }，仅更新启用状态与修改人/修改时间。
     *
     * @param body 请求体 { fieldId, fieldEnable }，fieldId 必填
     * @return 统一响应；缺 fieldId 返回 BAD_REQUEST
     */
    @PatchMapping("/enable")
    @Operation(summary = "启停用模板", description = "鉴权动作 sys.process.module.enable（仅 "
            + "SYS_PROCESS_TEMPLATE_ADMIN）；请求体 { fieldId, fieldEnable }，仅更新启用状态与修改人/修改时间")
    public BaseResponse<Void> enable(@RequestBody SysProcessTemplateVo body) {
        if (null == body || StringUtil.isEmpty(body.getFieldId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId is required");
        }
        authorize(ACTION_PROCESS_MODULE_ENABLE, body.getFieldId());
        baseService.updateEnable(body.getFieldId(), body.getFieldEnable());
        return BaseResponse.success(null, null);
    }

    /**
     * 置为默认模板：PATCH /backend/sys/process/module/setDefault
     * 请求体为 { fieldId }，替换同分类现有默认模板（先取消旧默认再设置新默认）。
     *
     * @param body 请求体 { fieldId }
     * @return 统一响应；缺 fieldId 返回 BAD_REQUEST
     */
    @PatchMapping("/setDefault")
    @Operation(summary = "置为默认模板", description = "鉴权动作 sys.process.module.setDefault（仅 "
            + "SYS_PROCESS_TEMPLATE_ADMIN）；请求体 { fieldId }，替换同分类现有默认模板"
            + "（先取消旧默认再设置新默认）")
    public BaseResponse<Void> setDefault(@RequestBody SysProcessTemplateVo body) {
        if (null == body || StringUtil.isEmpty(body.getFieldId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId is required");
        }
        authorize(ACTION_PROCESS_MODULE_SET_DEFAULT, body.getFieldId());
        baseService.setDefault(body.getFieldId());
        return BaseResponse.success(null, null);
    }

    /**
     * 最终鉴权：未登录或凭证失效时抛出 UNAUTHORIZED；已登录但缺少权限时
     * 发布鉴权拒绝事件（供日志模块记录安全审计）并抛出 FORBIDDEN。
     */
    private void authorize(String action, String documentId) {
        authorize(new AuthorizationContext(action, documentId));
    }

    private void authorize(AuthorizationContext context) {
        SysOrgUser currentUser = UserUtil.getCurrentUser();
        if (null == currentUser) {
            throw new AuthorizationException("UNAUTHORIZED", "登录已过期，请重新登录");
        }
        if (!baseResolver.resolve(context)) {
            publishAuthorizationRejected(currentUser.getFieldId(), context);
            throw new AuthorizationException("FORBIDDEN", "no permission for action: " + context.getAction());
        }
    }

    /**
     * 发布鉴权拒绝事件（安全审计）；审计日志记录失败不影响鉴权流程本身。
     */
    private void publishAuthorizationRejected(String userId, AuthorizationContext context) {
        try {
            eventPublisher.publishEvent(new AuthorizationRejectedEvent(userId, context.getAction(),
                    context.getDocumentId(), "no permission for action: " + context.getAction()));
        } catch (RuntimeException e) {
            // 忽略：审计失败不得阻断鉴权
        }
    }
}
