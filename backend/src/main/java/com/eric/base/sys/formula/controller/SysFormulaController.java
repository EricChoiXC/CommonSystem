package com.eric.base.sys.formula.controller;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.module.annotation.ModuleGroup;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.resolver.AuthorizationHelper;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.formula.model.SysFormula;
import com.eric.base.sys.formula.request.SysFormulaQuery;
import com.eric.base.sys.formula.request.SysFormulaSaveRequest;
import com.eric.base.sys.formula.resolver.FormulaResolver;
import com.eric.base.sys.formula.response.SysFormulaVo;
import com.eric.base.sys.formula.service.SysFormulaService;
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

import static com.eric.base.sys.formula.properties.SysFormulaConstants.*;

/**
 * 命名公式管理接口
 *
 * @author Eric
 * @date 2026/11/18
 * @description 公式定义器命名公式管理面的请求入口（任务 F14-BE-008，方案 §5.1 / API 文档 4.11，
 *              管理六动作组）：只负责接收请求、基础校验、执行最终鉴权并调用
 *              {@link SysFormulaService} 返回统一响应，管理编排（编码查重、保存期强制
 *              validate 同源校验、依赖解析落 field_dependencies、启停用）全部在 Service。
 *              本模块 Service 为非 IBaseService 形态，不继承 BaseCrudController 骨架，
 *              最终鉴权委托 {@link AuthorizationHelper}（与 BaseCrudController 鉴权口径
 *              一致并附鉴权拒绝审计事件，先例 SysNumberRuleController / SysDatasourceController）：
 *              管理六动作（list / load / save / update / enable / delete）仅公式管理员
 *              SYS_FORMULA_ADMIN（SYS_ADMIN 由 BaseResolver 自动放行，经
 *              {@link FormulaResolver} 按动作判定）。组件四动作（options / functions /
 *              validate / simulate，登录即可）由 {@link SysFormulaDesignerController} 承载。
 *              接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}；
 *              类级 {@code @ModuleGroup(moduleGroupKey = "formula")} 使 6 接口受模块组
 *              开关统一控制（key 登记于 properties/sys/formula/moduleGroup.yml）。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/formula")
@ModuleGroup(moduleGroupKey = "formula")
@Tag(name = "公式定义器", description = "命名公式管理：sys_formula 单表管理六动作"
        + "（列表 / 详情 / 新建 / 编辑 / 启停用 / 删除，仅 SYS_FORMULA_ADMIN）；"
        + "组件四动作（options / functions / validate / simulate，登录即可）见公式组件接口")
public class SysFormulaController {

    private final SysFormulaService formulaService;

    private final FormulaResolver formulaResolver;

    private final AuthorizationHelper authorizationHelper;

    public SysFormulaController(SysFormulaService formulaService, FormulaResolver formulaResolver,
                                AuthorizationHelper authorizationHelper) {
        this.formulaService = formulaService;
        this.formulaResolver = formulaResolver;
        this.authorizationHelper = authorizationHelper;
    }

    /**
     * 命名公式管理列表：POST /backend/sys/formula/list
     * 请求体 { body, pages: { pageNo, pageSize, needPage } }；条件 fieldName / fieldCode 模糊、
     * fieldType / fieldEnable 精确；fieldDependencies 原文 JSON 字符串返回不解析（列表页不解析
     * JSON，A4 先例，前端仅取数组长度展示「变量依赖数」），排序固定创建时间降序；
     * needPage=false 返回全部时仍随 metadata.total 返回总数（对齐 org element / datasource 先例）。
     *
     * @param request 列表请求（条件 + 分页）
     * @return 统一响应，data 为公式列表，metadata.total 为满足条件的总数
     */
    @PostMapping("/list")
    @Operation(summary = "命名公式列表", description = "鉴权动作 " + ACTION_LIST + "（仅 "
            + ROLE_SYS_FORMULA_ADMIN + "）；请求体 { body: { fieldName, fieldCode, fieldType, "
            + "fieldEnable }, pages }，fieldName / fieldCode 模糊、fieldType / fieldEnable 精确，"
            + "默认创建时间降序，fieldDependencies 原文 JSON 字符串返回（列表页不解析，"
            + "前端仅取数组长度展示变量依赖数），总数经 metadata.total 返回")
    public BaseResponse<List<SysFormulaVo>> list(@RequestBody SysFormulaQuery request) {
        authorize(new AuthorizationContext(ACTION_LIST));
        SysFormula body = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysFormulaVo> list = formulaService.list(body, pages);
        long total = formulaService.count(body);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 命名公式详情：GET /backend/sys/formula/load?fieldId=${id}
     * fieldDependencies 由 Service 解析为变量码字符串数组返回，fieldExpression 表达式原文返回
     * （${} 形态原样保留），供编辑 / 查看回填；管理员预判命名公式复用经 load 取表达式
     * 原文后走 /simulate 内联路径，不单设预览接口（方案 §6.6）。
     *
     * @param fieldId 公式 ID
     * @return 统一响应，data 为公式详情（依赖解析后形态）；不存在返回参数异常
     */
    @GetMapping("/load")
    @Operation(summary = "命名公式详情", description = "鉴权动作 " + ACTION_LOAD + "（仅 "
            + ROLE_SYS_FORMULA_ADMIN + "）；按 ID 读取公式完整信息，fieldExpression 表达式原文返回"
            + "（变量占位符形态原样保留）、fieldDependencies 解析为变量码字符串数组返回，供编辑 / 查看"
            + "回填；管理员预判命名公式复用经本接口取表达式原文后走 /simulate 内联路径")
    public BaseResponse<SysFormulaVo> load(
            @Parameter(description = "公式 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_LOAD, fieldId);
        return BaseResponse.success(formulaService.load(fieldId), null);
    }

    /**
     * 新建命名公式：POST /backend/sys/formula/save
     * 请求体 body 承载保存体（fieldCode / fieldName / fieldType / fieldExpression /
     * fieldReturnType 必填，fieldRemark 可选，fieldId 由前端经 /backend/common/initID 预取）；
     * 保存期强制 validate 同源校验通过（宽松模式，校验不过拒绝保存）+ 编码全局查重
     * （CS_FORMULA_CODE_DUPLICATED）+ 依赖解析落 field_dependencies（请求传入值一律忽略），
     * 新建默认启用（表单无启用字段），返回保存后的详情（依赖解析后形态）。
     *
     * @param request 保存请求（body 承载保存体）
     * @return 统一响应，data 为保存后的公式详情
     */
    @PostMapping("/save")
    @Operation(summary = "新建命名公式", description = "鉴权动作 " + ACTION_SAVE + "（仅 "
            + ROLE_SYS_FORMULA_ADMIN + "）；请求体 { body: { fieldId, fieldCode, fieldName, "
            + "fieldType, fieldExpression, fieldReturnType, fieldRemark } }，fieldId 经 "
            + "/backend/common/initID 预取；保存期强制 validate 同源校验通过（宽松模式，"
            + "校验不过拒绝保存）+ 编码全局查重（CS_FORMULA_CODE_DUPLICATED），变量依赖由"
            + "保存期解析提取落 field_dependencies（请求传入值忽略），新建默认启用，"
            + "返回保存后的详情（fieldDependencies 已解析）")
    public BaseResponse<SysFormulaVo> save(@RequestBody SysFormulaSaveRequest request) {
        authorize(new AuthorizationContext(ACTION_SAVE));
        return BaseResponse.success(formulaService.save(null == request ? null : request.getBody()), null);
    }

    /**
     * 编辑命名公式：PATCH /backend/sys/formula/update
     * 请求体 body 必须携带 fieldId；fieldExpression 变更后重新强制 validate 并重解析依赖，
     * fieldCode 可改但须查重（排除自身）；fieldDependencies 不受理请求传入值（以保存期
     * 解析提取结果为准），fieldEnable 不受理（启停走 /enable 通道，保持库值不变），
     * 返回更新后的详情。
     *
     * @param request 保存请求（body 必须携带 fieldId）
     * @return 统一响应，data 为更新后的公式详情
     */
    @PatchMapping("/update")
    @Operation(summary = "编辑命名公式", description = "鉴权动作 " + ACTION_UPDATE + "（仅 "
            + ROLE_SYS_FORMULA_ADMIN + "）；请求体 { body: { fieldId, fieldCode, fieldName, "
            + "fieldType, fieldExpression, fieldReturnType, fieldRemark } }，fieldId 必填；"
            + "同 save 强制 validate 校验并重解析依赖（fieldDependencies 随表达式更新），"
            + "fieldCode 可改但须查重（排除自身），fieldEnable 不受理（启停走 /enable 通道），"
            + "返回更新后的详情（fieldDependencies 已解析）")
    public BaseResponse<SysFormulaVo> update(@RequestBody SysFormulaSaveRequest request) {
        authorize(new AuthorizationContext(ACTION_UPDATE));
        return BaseResponse.success(formulaService.update(null == request ? null : request.getBody()), null);
    }

    /**
     * 启停用：PATCH /backend/sys/formula/enable
     * 请求体仅为 { fieldId, fieldEnable } 两字段（对齐 org element / number rule enable
     * 以实体作 body 的先例，其余字段不消费）：仅更新启用状态与修改人/修改时间（本表无
     * 树形语义，不级联）；停用后命名公式对执行期引用不可见（消费方按 field_code 执行报
     * CS_FORMULA_NOT_FOUND），管理列表仍可查询与重新启用。
     *
     * @param body 请求体 { fieldId, fieldEnable }，两字段均必填
     * @return 统一响应；缺 fieldId 返回 BAD_REQUEST
     */
    @PatchMapping("/enable")
    @Operation(summary = "启停用命名公式", description = "鉴权动作 " + ACTION_ENABLE + "（仅 "
            + ROLE_SYS_FORMULA_ADMIN + "）；请求体 { fieldId, fieldEnable }，仅携带这两个字段："
            + "仅更新启用状态与修改人/修改时间（不级联——本表无树形语义），停用后消费方按 "
            + "field_code 执行报 CS_FORMULA_NOT_FOUND，管理列表仍可查询与重新启用，"
            + "缺 fieldId 返回 BAD_REQUEST")
    public BaseResponse<Void> enable(@RequestBody SysFormula body) {
        authorize(new AuthorizationContext(ACTION_ENABLE));
        if (null == body || StringUtil.isEmpty(body.getFieldId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId is required");
        }
        formulaService.updateEnable(body.getFieldId(), body.getFieldEnable());
        return BaseResponse.success(null, null);
    }

    /**
     * 删除命名公式：DELETE /backend/sys/formula/delete?fieldId=${id}
     * 按 ID 物理删除，无附加在用校验——命名公式属公共公式库元数据，删除后消费方按
     * field_code 引用执行报 CS_FORMULA_NOT_FOUND，前端对删除须二次确认（列表页通用口径）。
     *
     * @param fieldId 公式 ID
     * @return 统一响应
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除命名公式", description = "鉴权动作 " + ACTION_DELETE + "（仅 "
            + ROLE_SYS_FORMULA_ADMIN + "）；按 ID 物理删除，无附加在用校验——删除后消费方按 "
            + "field_code 引用执行报 CS_FORMULA_NOT_FOUND，前端删除须二次确认（列表页通用口径）")
    public BaseResponse<Void> delete(
            @Parameter(description = "公式 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_DELETE, fieldId);
        formulaService.delete(fieldId);
        return BaseResponse.success(null, null);
    }

    /* ==================== 鉴权（AuthorizationHelper 统一入口） ==================== */

    /**
     * 执行最终鉴权：未登录或凭证已失效抛 UNAUTHORIZED，已登录但缺少权限时
     * 发布鉴权拒绝事件并抛 FORBIDDEN，由全局异常处理器统一转换。
     */
    private void authorize(AuthorizationContext context) {
        authorizationHelper.authorize(formulaResolver, context);
    }

    private void authorize(String action, String documentId) {
        authorizationHelper.authorize(formulaResolver, action, documentId);
    }
}
