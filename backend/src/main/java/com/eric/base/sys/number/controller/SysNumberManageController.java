package com.eric.base.sys.number.controller;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.resolver.AuthorizationHelper;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.number.model.SysNumberAcquireResultDo;
import com.eric.base.sys.number.model.SysNumberRecordDetail;
import com.eric.base.sys.number.request.SysNumberFieldIdRequest;
import com.eric.base.sys.number.request.SysNumberManageQuery;
import com.eric.base.sys.number.request.SysNumberManageRequest;
import com.eric.base.sys.number.request.SysNumberResetRequest;
import com.eric.base.sys.number.request.SysNumberVoidRequest;
import com.eric.base.sys.number.resolver.NumberResolver;
import com.eric.base.sys.number.service.SysNumberManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.eric.base.sys.number.properties.SysNumberConstants.*;

/**
 * 编号取号明细管理与特权操作接口
 *
 * @author Eric
 * @date 2026/11/13
 * @description 编号机制模块特权管理面的请求入口（任务 NB3-BE-005，方案 §3.1/§3.2 manage 组）：
 *              只负责接收请求、基础校验、执行最终鉴权并调用 {@link SysNumberManageService}
 *              返回统一响应，业务逻辑全部在 Service。四个接口均为特权操作，仅
 *              SYS_NUMBER_ADMIN 可用（§4.3：重置会造成计数器回退风险，作废 / 补号改变编号
 *              可用性，收敛编号管理员；SYS_ADMIN 由 BaseResolver 自动放行）；
 *              reset / void / reissue 三操作在 Service 内落操作日志（sys_log_operation，
 *              §4.3 审计联动）。
 *              本 Service 未实现 IBaseService，不走 BaseCrudController 骨架，
 *              鉴权经 {@link AuthorizationHelper} 统一入口（未登录抛 UNAUTHORIZED、
 *              无权限抛 FORBIDDEN 并发布鉴权拒绝事件，先例 {@link SysNumberRuleController}）。
 *              接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/number/manage")
@Tag(name = "编号机制", description = "编号规则维护、预览、取号与特权管理")
public class SysNumberManageController {

    private final SysNumberManageService manageService;

    private final NumberResolver numberResolver;

    private final AuthorizationHelper authorizationHelper;

    public SysNumberManageController(SysNumberManageService manageService, NumberResolver numberResolver,
                                     AuthorizationHelper authorizationHelper) {
        this.manageService = manageService;
        this.numberResolver = numberResolver;
        this.authorizationHelper = authorizationHelper;
    }

    /**
     * 取号明细列表：POST /backend/sys/number/manage/recordList
     * 请求体 { body, pages: { pageNo, pageSize, needPage } }；条件 fieldScopeKey（含
     * fieldRuleId 翻译）/ fieldPeriod 精确、voidFlag 三态、recordTimeBegin / recordTimeEnd
     * 时间范围（含边界）；排序固定取号时间降序；needPage=false 返回全部时仍随
     * metadata.total 返回总数（对齐 rule/list 先例）。
     *
     * @param request 列表请求（条件 + 分页）
     * @return 统一响应，data 为取号明细列表，metadata.total 为满足条件的总数
     */
    @PostMapping("/recordList")
    @Operation(summary = "取号明细列表", description = "鉴权动作 " + ACTION_MANAGE_RECORD_LIST + "（仅 "
            + ROLE_SYS_NUMBER_ADMIN + "）；请求体 { body, pages }，条件 fieldScopeKey（含 fieldRuleId "
            + "翻译：明细表无 field_rule_id 列，固定接入作用域即规则 ID）/ fieldPeriod 精确、"
            + "voidFlag 三态（true 仅已作废 / false 仅未作废 / null 全部）、recordTimeBegin / "
            + "recordTimeEnd 取号时间范围（含边界），排序固定取号时间降序，总数经 metadata.total 返回")
    public BaseResponse<List<SysNumberRecordDetail>> recordList(@RequestBody SysNumberManageRequest request) {
        authorize(new AuthorizationContext(ACTION_MANAGE_RECORD_LIST));
        SysNumberManageQuery query = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysNumberRecordDetail> list = manageService.recordList(query, pages);
        long total = manageService.count(query);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 重置计数器：POST /backend/sys/number/manage/reset
     * 作用域二选一（fieldRuleId 固定接入 / fieldScopeKey 非固定接入，同时传入以 fieldRuleId
     * 优先），可限定 fieldSerialId / fieldPeriod（可空 = 全部）；封存作用域使用中计数行，
     * 下次取号从 start 重开；返回封存行数。
     *
     * @param request 重置请求
     * @return 统一响应，data 为封存行数（0 或 1）；请求体为空返回 BAD_REQUEST
     */
    @PostMapping("/reset")
    @Operation(summary = "重置计数器", description = "鉴权动作 " + ACTION_MANAGE_RESET + "（仅 "
            + ROLE_SYS_NUMBER_ADMIN + "）；封存作用域当前使用中计数行（可限定流水项 fieldSerialId / "
            + "周期 fieldPeriod，可空 = 全部），下次取号从 start 重开；<b>会造成重号</b>（方案 §5.4 "
            + "预期语义，旧号明细保留审计），需前端二次确认后调用；作用域二选一：fieldRuleId"
            + "（固定接入，校验规则存在）或 fieldScopeKey，同时传入以 fieldRuleId 优先；"
            + "返回封存行数，无任何限定且无可重置使用中计数行时返回错误；操作落操作日志")
    public BaseResponse<Integer> reset(@RequestBody SysNumberResetRequest request) {
        authorize(new AuthorizationContext(ACTION_MANAGE_RESET));
        if (null == request) {
            return BaseResponse.failure("BAD_REQUEST", "fieldScopeKey or fieldRuleId is required");
        }
        return BaseResponse.success(manageService.reset(request), null);
    }

    /**
     * 作废取号明细：PATCH /backend/sys/number/manage/void
     * 请求体 { fieldId, fieldVoidReason }（fieldId 必填）；仅更新 void 两字段，不回退计数器；
     * 已作废明细重复作废幂等返回原状态；返回作废后明细状态。
     *
     * @param request 作废请求
     * @return 统一响应，data 为作废后明细状态；缺 fieldId 返回 BAD_REQUEST
     */
    @PatchMapping("/void")
    @Operation(summary = "作废取号明细", description = "鉴权动作 " + ACTION_MANAGE_VOID + "（仅 "
            + ROLE_SYS_NUMBER_ADMIN + "）；请求体 { fieldId, fieldVoidReason }（fieldId 必填），"
            + "仅更新 void 两字段（field_void_time / field_void_reason，D8 唯一例外更新），"
            + "不回退计数器（避免重号）；已作废明细重复作废幂等返回原状态，不重复更新；"
            + "操作落操作日志")
    public BaseResponse<SysNumberRecordDetail> voidDetail(@RequestBody SysNumberVoidRequest request) {
        authorize(new AuthorizationContext(ACTION_MANAGE_VOID));
        if (null == request || StringUtil.isEmpty(request.getFieldId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId is required");
        }
        return BaseResponse.success(
                manageService.voidDetail(request.getFieldId(), request.getFieldVoidReason()), null);
    }

    /**
     * 补号：POST /backend/sys/number/manage/reissue
     * 请求体 { fieldId }（原取号明细 ID，须已作废）；为原 field_model_id 对应单据按计数器
     * 顺延取新号，返回同 generate/acquire；旧号保留作废记录供审计。
     *
     * @param request 补号请求
     * @return 统一响应，data 为取号结果 { code, recordDetailId, reused }；缺 fieldId 返回 BAD_REQUEST
     */
    @PostMapping("/reissue")
    @Operation(summary = "补号", description = "鉴权动作 " + ACTION_MANAGE_REISSUE + "（仅 "
            + ROLE_SYS_NUMBER_ADMIN + "）；请求体 { fieldId }（原取号明细 ID，须已作废），"
            + "为原 field_model_id 对应单据按计数器<b>顺延取新号</b>，返回同 generate/acquire"
            + "（{ code, recordDetailId, reused }）；旧号保留作废记录供审计；仅支持固定接入明细"
            + "（作用域命中编号规则），原单据归属由业务模块自行校验；操作落操作日志")
    public BaseResponse<SysNumberAcquireResultDo> reissue(@RequestBody SysNumberFieldIdRequest request) {
        authorize(new AuthorizationContext(ACTION_MANAGE_REISSUE));
        if (null == request || StringUtil.isEmpty(request.getFieldId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId is required");
        }
        return BaseResponse.success(manageService.reissue(request.getFieldId()), null);
    }

    /* ==================== 鉴权（AuthorizationHelper 统一入口） ==================== */

    /**
     * 执行最终鉴权：未登录或凭证已失效抛 UNAUTHORIZED，已登录但缺少权限时
     * 发布鉴权拒绝事件并抛 FORBIDDEN，由全局异常处理器统一转换。
     */
    private void authorize(AuthorizationContext context) {
        authorizationHelper.authorize(numberResolver, context);
    }
}
