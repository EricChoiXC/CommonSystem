package com.eric.base.sys.formula.controller;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.module.annotation.ModuleGroup;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.resolver.AuthorizationHelper;
import com.eric.base.sys.formula.request.SysFormulaSimulateRequest;
import com.eric.base.sys.formula.request.SysFormulaValidateRequest;
import com.eric.base.sys.formula.resolver.FormulaResolver;
import com.eric.base.sys.formula.response.SysFormulaFunctionVo;
import com.eric.base.sys.formula.response.SysFormulaOptionVo;
import com.eric.base.sys.formula.response.SysFormulaSimulateResponse;
import com.eric.base.sys.formula.response.SysFormulaValidateResponse;
import com.eric.base.sys.formula.service.SysFormulaService;
import com.eric.base.sys.formula.service.SysFormulaSimulateService;
import com.eric.base.sys.formula.service.SysFormulaValidateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.eric.base.sys.formula.properties.SysFormulaConstants.*;

/**
 * 公式组件接口
 *
 * @author Eric
 * @date 2026/11/18
 * @description 公式定义器组件面（FormulaDesigner 弹窗与 v1.5 / v1.7 内嵌消费方）的请求入口
 *              （任务 F14-BE-008，方案 §5.1 / §5.2 / API 文档 4.11，组件四动作组）：
 *              只负责接收请求、执行最终鉴权并调用 Service 返回统一响应，校验与试算编排
 *              （宽松 / 严格模式、资源限制检查、沙箱受限执行、类型适配）全部在
 *              {@link SysFormulaValidateService} / {@link SysFormulaSimulateService}，
 *              组件读取聚合（options / functions）在 {@link SysFormulaService}。
 *              本模块 Service 为非 IBaseService 形态，不继承 BaseCrudController 骨架，
 *              最终鉴权委托 {@link AuthorizationHelper}（先例 SysDatasourceController）：
 *              组件四动作（options / functions / validate / simulate）登录且账号启用即可
 *              （方案 A7 鉴权分层——公式组件被各业务页面内嵌使用，v1.5 表单设计者、
 *              v1.7 流程配置者未必持有公式管理角色，经 {@link FormulaResolver}
 *              {@code resolveLoggedIn()} 判定；validate 纯静态分析无副作用、simulate
 *              在资源限制内沙箱执行，取数数据边界由 DS_QUERY 委托数据源执行期鉴权承担）。
 *              命名公式管理六动作（仅 SYS_FORMULA_ADMIN）由 {@link SysFormulaController} 承载。
 *              接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}；
 *              类级 {@code @ModuleGroup(moduleGroupKey = "formula")} 使 4 接口受模块组
 *              开关统一控制（key 登记于 properties/sys/formula/moduleGroup.yml）。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/formula")
@ModuleGroup(moduleGroupKey = "formula")
@Tag(name = "公式定义器", description = "公式组件接口：FormulaDesigner 弹窗与业务页面内嵌消费"
        + "（options / functions / validate / simulate，登录即可）；命名公式管理六动作"
        + "（仅 SYS_FORMULA_ADMIN）见命名公式管理接口")
public class SysFormulaDesignerController {

    private final SysFormulaService formulaService;

    private final SysFormulaValidateService validateService;

    private final SysFormulaSimulateService simulateService;

    private final FormulaResolver formulaResolver;

    private final AuthorizationHelper authorizationHelper;

    public SysFormulaDesignerController(SysFormulaService formulaService,
                                        SysFormulaValidateService validateService,
                                        SysFormulaSimulateService simulateService,
                                        FormulaResolver formulaResolver,
                                        AuthorizationHelper authorizationHelper) {
        this.formulaService = formulaService;
        this.validateService = validateService;
        this.simulateService = simulateService;
        this.formulaResolver = formulaResolver;
        this.authorizationHelper = authorizationHelper;
    }

    /**
     * 可用公式下拉：GET /backend/sys/formula/options
     * 返回启用中的命名公式条目（仅消费所需元数据，不暴露表达式原文与变量依赖），
     * 供组件插入命名公式（按 field_code 引用）与结果适配预判；左树「变量」「业务模型」
     * 「函数」三组节点由前端组装（变量组 = 系统变量 + availableVars；业务模型组 = 数据源
     * targets 聚合，方案 A6 不另建字段台账；函数组 = /functions），本接口提供命名公式条目
     * （SysFormulaOptionVo 契约，F14-BE-007 落地口径）。
     *
     * @return 统一响应，data 为命名公式下拉条目（启用中的命名公式，创建时间降序）
     */
    @GetMapping("/options")
    @Operation(summary = "可用公式下拉", description = "鉴权动作 " + ACTION_OPTIONS + "（登录即可）；"
            + "返回启用中的命名公式下拉条目 { fieldCode, fieldName, fieldType, fieldReturnType }"
            + "（创建时间降序），不暴露表达式原文与变量依赖；供组件插入命名公式（按 field_code "
            + "引用）与结果适配预判，左树「变量」「业务模型」「函数」三组节点由前端组装"
            + "（系统变量 + availableVars / 数据源 targets / functions）")
    public BaseResponse<List<SysFormulaOptionVo>> options() {
        authorize(new AuthorizationContext(ACTION_OPTIONS));
        return BaseResponse.success(formulaService.options(), null);
    }

    /**
     * 函数注册中心清单：GET /backend/sys/formula/functions
     * 注册中心全量函数（内置九类 + SPI 扩展）导出为 SysFormulaFunctionVo——同一份元数据
     * 驱动前端函数面板 / 插入模板与后端参数类型校验（方案 §2.2「前后端共用函数定义」）；
     * 函数名全局唯一，category / returnType 以枚举名透出。
     *
     * @return 统一响应，data 为函数元数据列表（按注册顺序）
     */
    @GetMapping("/functions")
    @Operation(summary = "函数注册中心清单", description = "鉴权动作 " + ACTION_FUNCTIONS + "（登录即可）；"
            + "返回函数注册中心全量清单 { functionCode, functionName, category, description, "
            + "parameters: [{ name, type, required, defaultValue }], returnType }（内置九类 + "
            + "SPI 扩展一并透出，category / returnType 以枚举名透出，函数名全局唯一），"
            + "同一份元数据驱动前端函数面板 / 插入模板与后端参数类型校验")
    public BaseResponse<List<SysFormulaFunctionVo>> functions() {
        authorize(new AuthorizationContext(ACTION_FUNCTIONS));
        return BaseResponse.success(formulaService.functions(), null);
    }

    /**
     * 公式校验：POST /backend/sys/formula/validate
     * 纯静态分析无副作用——语法 / 函数注册 / 参数与返回类型 / 变量码约束校验；
     * vars 缺省 = 宽松模式（变量视为未知，仅做语法与函数校验），非空 = 严格模式
     * （按声明 vars 核对「引用 ⇄ 声明」一致性与类型声明）；校验失败以 errors 数组承载、
     * HTTP 仍 200（errors 非空即未通过，不抛 HTTP 错误）；returnType 为请求
     * expectedReturnType 规整回显（缺省 null），dependencies 为提取的变量码数组
     * （路径投影取根码、去重保序），供消费方存储并自建依赖图（方案 A8）。
     *
     * @param request 校验请求（expression + expectedReturnType + vars）
     * @return 统一响应，data 为校验结果（syntax / returnType / dependencies / errors）
     */
    @PostMapping("/validate")
    @Operation(summary = "公式校验", description = "鉴权动作 " + ACTION_VALIDATE + "（登录即可）；"
            + "纯静态分析无副作用——语法 / 函数注册 / 参数与返回类型 / 变量码约束校验，"
            + "vars 缺省 = 宽松模式（变量视为未知，仅做语法与函数校验）、非空 = 严格模式"
            + "（核对声明与引用一致性、类型声明合法性）；响应 { syntax, returnType, "
            + "dependencies, errors }，校验失败以 errors 数组承载、HTTP 仍 200；returnType 为"
            + "请求 expectedReturnType 规整回显（缺省 null），dependencies 为提取的变量码数组"
            + "（去重保序，供消费方自建依赖图）")
    public BaseResponse<SysFormulaValidateResponse> validate(@RequestBody SysFormulaValidateRequest request) {
        authorize(new AuthorizationContext(ACTION_VALIDATE));
        return BaseResponse.success(validateService.validate(request), null);
    }

    /**
     * 公式模拟计算：POST /backend/sys/formula/simulate
     * 受限沙箱试算——表达式长度 / 变量个数 / 变量值序列化体积 / 循环次数封顶 /
     * 独立线程超时资源限制内执行（常量收口 SysFormulaConstants），系统变量（SYS_CURRENT_*）
     * 服务端注入、前端同名覆盖被忽略；模拟赋值仅用于沙箱试算、入参由调用方自负（A7）；
     * 成功 success=true + result + resultType（期望类型缺省时按结果 Java 类型推断），
     * 失败 success=false + errors 数组、HTTP 仍 200（失败不阻断弹窗操作）；
     * 管理员预判命名公式复用本接口（管理页 load 取表达式原文后走内联路径，不单设预览接口）。
     *
     * @param request 试算请求（expression + vars + expectedReturnType）
     * @return 统一响应，data 为试算结果（success / result / resultType / errors）
     */
    @PostMapping("/simulate")
    @Operation(summary = "公式模拟计算", description = "鉴权动作 " + ACTION_SIMULATE + "（登录即可）；"
            + "受限沙箱试算——表达式长度 / 变量个数 / 变量值序列化体积 / 循环次数封顶 / "
            + "独立线程超时资源限制内执行，系统变量服务端注入（前端同名覆盖被忽略），"
            + "模拟赋值入参由调用方自负；请求 { expression, vars, expectedReturnType }，"
            + "成功返回 result + resultType，失败以 errors 数组承载（code ∈ CS_FORMULA_* 九类）、"
            + "HTTP 仍 200，失败不阻断弹窗操作")
    public BaseResponse<SysFormulaSimulateResponse> simulate(@RequestBody SysFormulaSimulateRequest request) {
        authorize(new AuthorizationContext(ACTION_SIMULATE));
        return BaseResponse.success(simulateService.simulate(request), null);
    }

    /* ==================== 鉴权（AuthorizationHelper 统一入口） ==================== */

    /**
     * 执行最终鉴权：未登录或凭证已失效抛 UNAUTHORIZED，已登录但缺少权限时
     * 发布鉴权拒绝事件并抛 FORBIDDEN，由全局异常处理器统一转换。
     */
    private void authorize(AuthorizationContext context) {
        authorizationHelper.authorize(formulaResolver, context);
    }
}
