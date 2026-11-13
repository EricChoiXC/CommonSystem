package com.eric.base.sys.number.controller;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.resolver.AuthorizationHelper;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.number.model.SysNumberModelDo;
import com.eric.base.sys.number.model.SysNumberRule;
import com.eric.base.sys.number.request.SysNumberRuleQuery;
import com.eric.base.sys.number.request.SysNumberRuleRequest;
import com.eric.base.sys.number.resolver.NumberResolver;
import com.eric.base.sys.number.service.SysNumberRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.eric.base.sys.number.properties.SysNumberConstants.*;

/**
 * 编号规则维护接口
 *
 * @author Eric
 * @date 2026/11/13
 * @description 编号机制模块规则维护面的请求入口（任务 NB3-BE-003，方案 §3.1/§3.2 rule 组）：
 *              只负责接收请求、基础校验、执行最终鉴权并调用 Service 返回统一响应，业务逻辑全部
 *              在 {@link SysNumberRuleService}。每个接口在执行核心业务逻辑前先经
 *              {@link NumberResolver} 完成鉴权：列表与接入项下拉登录即可（需求 4.2.2「查询不设权」），
 *              load / save / update / enable / delete 仅 SYS_NUMBER_ADMIN 或
 *              SYS_NUMBER_RULE_EDITOR（SYS_ADMIN 由 BaseResolver 自动放行）。
 *              本 Service 未实现 IBaseService，不走 BaseCrudController 骨架，
 *              鉴权经 {@link AuthorizationHelper} 统一入口（未登录抛 UNAUTHORIZED、
 *              无权限抛 FORBIDDEN 并发布鉴权拒绝事件，先例 CommonModuleController）。
 *              接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/number/rule")
@Tag(name = "编号机制", description = "编号规则维护、预览、取号与特权管理")
public class SysNumberRuleController {

    private final SysNumberRuleService ruleService;

    private final NumberResolver numberResolver;

    private final AuthorizationHelper authorizationHelper;

    public SysNumberRuleController(SysNumberRuleService ruleService, NumberResolver numberResolver,
                                   AuthorizationHelper authorizationHelper) {
        this.ruleService = ruleService;
        this.numberResolver = numberResolver;
        this.authorizationHelper = authorizationHelper;
    }

    /**
     * 规则列表：POST /backend/sys/number/rule/list
     * 请求体 { body, pages: { pageNo, pageSize, needPage } }；条件 fieldName / fieldCode 模糊、
     * fieldModelId / fieldModelField / fieldEnable 精确；field_rule 原文返回不解析（列表页不解析
     * JSON，A4），排序固定创建时间降序；needPage=false 返回全部时仍随 metadata.total 返回总数
     * （对齐 org element / log operation 先例）。
     *
     * @param request 列表请求（条件 + 分页）
     * @return 统一响应，data 为规则列表，metadata.total 为满足条件的总数
     */
    @PostMapping("/list")
    @Operation(summary = "编号规则列表", description = "鉴权动作 " + ACTION_RULE_LIST + "（登录即可，需求 4.2.2「查询不设权」）；"
            + "请求体 { body, pages }，条件 fieldName / fieldCode 模糊、fieldModelId / fieldModelField / "
            + "fieldEnable 精确，field_rule 原文返回不解析，排序固定创建时间降序，总数经 metadata.total 返回")
    public BaseResponse<List<SysNumberRule>> list(@RequestBody SysNumberRuleRequest request) {
        authorize(new AuthorizationContext(ACTION_RULE_LIST));
        SysNumberRuleQuery query = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysNumberRule> list = ruleService.list(query, pages);
        long total = ruleService.count(query);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 规则详情：GET /backend/sys/number/rule/load?fieldId=${id}
     * field_rule 已由 Service 解析为 {@link com.eric.base.sys.number.model.SysNumberRuleConfigDo}
     * 对象返回，前端 NumberRuleComposer 直接消费。
     *
     * @param fieldId 规则 ID
     * @return 统一响应，data 为规则详情（fieldRule 为解析后的配置对象）；不存在返回参数异常
     */
    @GetMapping("/load")
    @Operation(summary = "编号规则详情", description = "鉴权动作 " + ACTION_RULE_LOAD + "（SYS_NUMBER_ADMIN 或 "
            + ROLE_SYS_NUMBER_RULE_EDITOR + "）；按 ID 读取规则完整信息，field_rule 解析为规则配置对象"
            + "（SysNumberRuleConfigDo）返回，供前端 NumberRuleComposer 直接消费")
    public BaseResponse<SysNumberRule> load(
            @Parameter(description = "规则 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_RULE_LOAD, fieldId);
        return BaseResponse.success(ruleService.load(fieldId), null);
    }

    /**
     * 新建保存：POST /backend/sys/number/rule/save
     * 请求体为规则对象（fieldCode / fieldName / fieldModelId / fieldRule 必填，fieldEnable 不受理）；
     * C1~C5 校验 + 编码查重（D4）+ 接入项存在性（R6），默认启用并同事务切换式停用
     * 同接入项原已启用规则（D1），返回保存后的规则详情（field_rule 已解析）。
     *
     * @param body 规则对象（fieldId 由前端经 getInitID 预取）
     * @return 统一响应，data 为保存后的规则详情
     */
    @PostMapping("/save")
    @Operation(summary = "新建编号规则", description = "鉴权动作 " + ACTION_RULE_SAVE + "（SYS_NUMBER_ADMIN 或 "
            + ROLE_SYS_NUMBER_RULE_EDITOR + "）；请求体为规则对象（fieldCode / fieldName / fieldModelId / "
            + "fieldRule 必填），C1~C5 校验 + 编码查重（D4）+ 接入项存在性（R6），默认启用并同事务切换式"
            + "停用同接入项原已启用规则（D1），返回保存后的规则详情（field_rule 已解析）")
    public BaseResponse<SysNumberRule> save(@RequestBody SysNumberRule body) {
        authorize(new AuthorizationContext(ACTION_RULE_SAVE));
        return BaseResponse.success(ruleService.save(body), null);
    }

    /**
     * 编辑保存：PATCH /backend/sys/number/rule/update
     * 请求体为规则对象（必须携带 fieldId / fieldCode / fieldName / fieldRule）；
     * C1~C5 + C7 校验（seq 项 id 锁定库中旧值，D5）+ 编码查重排除自身；模型三字段
     * （field_model_id / field_model_name / field_model_field）不受理变更、一律以库中旧值为准（D3），
     * 启用态保持不变（启停走 enable 接口），返回更新后的规则详情。
     *
     * @param body 规则对象，必须携带 fieldId
     * @return 统一响应，data 为更新后的规则详情
     */
    @PatchMapping("/update")
    @Operation(summary = "编辑编号规则", description = "鉴权动作 " + ACTION_RULE_UPDATE + "（SYS_NUMBER_ADMIN 或 "
            + ROLE_SYS_NUMBER_RULE_EDITOR + "）；请求体为规则对象（必须携带 fieldId），C1~C5 + C7 校验"
            + "（seq 项 id 锁定库中旧值，D5）+ 编码查重排除自身；field_model_id / field_model_name / "
            + "field_model_field 不受理变更（一律以库中旧值为准，D3），启用态保持不变，返回更新后的规则详情")
    public BaseResponse<SysNumberRule> update(@RequestBody SysNumberRule body) {
        authorize(new AuthorizationContext(ACTION_RULE_UPDATE));
        return BaseResponse.success(ruleService.update(body), null);
    }

    /**
     * 启停用：PATCH /backend/sys/number/rule/enable
     * 请求体仅为 { fieldId, fieldEnable } 两字段（对齐 org element enable 以实体作 body 的先例，
     * 其余字段不消费）：启用（true）时以库中 field_rule 执行 C1~C5 校验并同事务切换式停用
     * 同接入项其他已启用规则（D1）；停用（false）仅更新自身启用状态。
     *
     * @param body 请求体 { fieldId, fieldEnable }，两字段均必填
     * @return 统一响应；缺 fieldId 返回 BAD_REQUEST
     */
    @PatchMapping("/enable")
    @Operation(summary = "启停用编号规则", description = "鉴权动作 " + ACTION_RULE_ENABLE + "（SYS_NUMBER_ADMIN 或 "
            + ROLE_SYS_NUMBER_RULE_EDITOR + "）；请求体 { fieldId, fieldEnable }，仅携带这两个字段："
            + "启用（true）时校验 C1~C5 并同事务切换式停用同接入项其他已启用规则（D1），"
            + "停用（false）仅更新自身启用状态，缺 fieldId 返回 BAD_REQUEST")
    public BaseResponse<Void> enable(@RequestBody SysNumberRule body) {
        authorize(new AuthorizationContext(ACTION_RULE_ENABLE));
        if (null == body || StringUtil.isEmpty(body.getFieldId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId is required");
        }
        ruleService.enable(body.getFieldId(), body.getFieldEnable());
        return BaseResponse.success(null, null);
    }

    /**
     * 删除规则：DELETE /backend/sys/number/rule/delete?fieldId=${id}
     * 删除保护（D9）：已产生取号明细的规则拒绝删除（NumberRuleInUseException，仅可停用），
     * 无明细时物理删除。
     *
     * @param fieldId 规则 ID
     * @return 统一响应
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除编号规则", description = "鉴权动作 " + ACTION_RULE_DELETE + "（SYS_NUMBER_ADMIN 或 "
            + ROLE_SYS_NUMBER_RULE_EDITOR + "）；已产生取号明细的规则拒绝删除"
            + "（CRM_NUMBER_RULE_IN_USE，仅可停用，D9），无明细时物理删除")
    public BaseResponse<Void> delete(
            @Parameter(description = "规则 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_RULE_DELETE, fieldId);
        ruleService.delete(fieldId);
        return BaseResponse.success(null, null);
    }

    /**
     * 接入项下拉选项：GET /backend/sys/number/rule/modelOptions
     * 读取 numberRule.yml 接入项缓存（NB2-BE-001 进程内缓存）返回全量接入项，
     * 供规则编辑页「所属接入项」下拉使用（W5 补充接口）。
     *
     * @return 统一响应，data 为接入项列表（key / name / modelName / modelField）
     */
    @GetMapping("/modelOptions")
    @Operation(summary = "接入项下拉选项", description = "鉴权动作 " + ACTION_RULE_MODEL_OPTIONS + "（登录即可，"
            + "W5 补充接口）；读取 numberRule.yml 接入项缓存返回全量接入项"
            + "（key / name / modelName / modelField），供规则编辑页「所属接入项」下拉使用")
    public BaseResponse<List<SysNumberModelDo>> modelOptions() {
        authorize(new AuthorizationContext(ACTION_RULE_MODEL_OPTIONS));
        return BaseResponse.success(ruleService.modelOptions(), null);
    }

    /* ==================== 鉴权（AuthorizationHelper 统一入口） ==================== */

    /**
     * 执行最终鉴权：未登录或凭证已失效抛 UNAUTHORIZED，已登录但缺少权限时
     * 发布鉴权拒绝事件并抛 FORBIDDEN，由全局异常处理器统一转换。
     */
    private void authorize(AuthorizationContext context) {
        authorizationHelper.authorize(numberResolver, context);
    }

    private void authorize(String action, String documentId) {
        authorizationHelper.authorize(numberResolver, action, documentId);
    }
}
