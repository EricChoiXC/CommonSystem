package com.eric.base.sys.formula.service;

import com.eric.base.sys.formula.model.SysFormulaErrorDo;
import com.eric.base.sys.formula.properties.SysFormulaConstants;
import com.eric.base.sys.formula.request.SysFormulaValidateRequest;
import com.eric.base.sys.formula.response.SysFormulaValidateResponse;
import com.eric.base.sys.formula.service.engine.FormulaEngine;
import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公式校验服务
 *
 * @author Eric
 * @date 2026/11/18
 * @description /validate 接口的校验编排（方案 §5.2 / DOC-002 §4.11.4，F14-BE-007）：
 *              纯静态分析无副作用——语法 / 函数注册 / 变量码约束 / 严格模式声明核对，
 *              校验失败以 {@link SysFormulaValidateResponse#getErrors() errors} 数组承载、
 *              HTTP 仍 200（不抛 HTTP 错误），errors 非空即未通过。
 *              <ul>
 *              <li>宽松模式（vars 缺省或空）：变量视为未知，仅做语法与函数校验——
 *                  引用未声明变量不报错（方案 §5.2），命名公式 save / update 保存期
 *                  强制校验即取本模式（引用失效变量不阻断保存，方案 §3.3，由消费方
 *                  依 dependencies 决定处置）；</li>
 *              <li>严格模式（vars 非空）：按声明 vars 核对「引用 ⇄ 声明」一致性与
 *                  类型声明合法性（不符报 CS_FORMULA_VARIABLE_INVALID /
 *                  CS_FORMULA_TYPE_MISMATCH）；</li>
 *              <li>响应 {@code syntax} 与 errors 同步（errors 非空即 false）；
 *                  {@code returnType} 为规整后的期望返回类型（请求未声明时为 null——
 *                  FormulaEngine 未提供静态返回类型推断面，见偏差说明：推断职责由
 *                  执行期 adaptResult 承担）；</li>
 *              <li>引擎失败首错即返回（{@link FormulaEngine#validate} 以
 *                  IllegalArgumentException(错误码 + ": " + message) 抛出），此处翻译为
 *                  {@link SysFormulaErrorDo}；依赖提取另行兜底（语法失败也尽量给出
 *                  dependencies 供前端展示变量引用面）。</li>
 *              </ul>
 *              管理侧 SysFormulaService（save / update 保存期阻断）与组件侧 Controller
 *              （/validate 透出）共用本服务，保证「保存期校验」与「validate 接口」同源。
 */
@Service
public class SysFormulaValidateService {

    private final FormulaEngine engine;

    public SysFormulaValidateService(FormulaEngine engine) {
        this.engine = engine;
    }

    /**
     * 公式校验（宽松 / 严格模式，方案 §5.2 契约）：
     * 响应 {@code syntax} / {@code returnType} / {@code dependencies} / {@code errors}，
     * 校验失败不抛异常、以 errors 数组承载（HTTP 仍 200）。
     *
     * @param request 校验请求（null 按空表达式处理，报 CS_FORMULA_SYNTAX_INVALID）
     * @return 校验结果（四个字段恒非 null；errors 无错误为空数组）
     */
    public SysFormulaValidateResponse validate(SysFormulaValidateRequest request) {
        SysFormulaValidateResponse response = new SysFormulaValidateResponse();
        String expression = null == request ? null : request.getExpression();
        // 期望返回类型规整：非空且不合法直接短路报 CS_FORMULA_TYPE_MISMATCH（不进入语法校验）
        SysFormulaReturnType expected = resolveExpectedReturnType(request, response);
        if (!response.getErrors().isEmpty()) {
            return response;
        }
        Map<String, String> declaredVars = toDeclaredVars(request);
        boolean strict = null != declaredVars && !declaredVars.isEmpty();
        try {
            List<String> dependencies = engine.validate(expression, declaredVars, expected, strict);
            response.setDependencies(dependencies);
            response.setSyntax(true);
            response.setReturnType(null == expected ? null : expected.name());
        } catch (IllegalArgumentException e) {
            // 引擎首错即返回：翻译为 errors 数组元素（CS_FORMULA_* + 中文消息），HTTP 仍 200
            response.getErrors().add(toError(e));
        }
        if (!response.getErrors().isEmpty()) {
            response.setSyntax(false);
            fillDependenciesBestEffort(response, expression);
        }
        return response;
    }

    /**
     * 期望返回类型规整：null / 空白 → null（跳过返回类型比对）；非空且不在
     * SysFormulaReturnType 枚举面 → errors 追加 CS_FORMULA_TYPE_MISMATCH 并返回 null
     * （由调用方短路返回，不进入语法校验）
     */
    private SysFormulaReturnType resolveExpectedReturnType(SysFormulaValidateRequest request,
                                                           SysFormulaValidateResponse response) {
        String raw = null == request ? null : request.getExpectedReturnType();
        if (null == raw || raw.isBlank()) {
            return null;
        }
        SysFormulaReturnType expected = SysFormulaReturnType.of(raw);
        if (null == expected) {
            response.getErrors().add(new SysFormulaErrorDo(
                    SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH, "期望返回类型不合法: " + raw));
        }
        return expected;
    }

    /**
     * 严格模式变量声明装配（code → 类型名）：vars 缺省或空返回 null（宽松模式）；
     * 元素或变量码为空跳过（缺失声明由引擎严格核对报「声明的变量未被表达式引用」）
     */
    private static Map<String, String> toDeclaredVars(SysFormulaValidateRequest request) {
        if (null == request || null == request.getVars() || request.getVars().isEmpty()) {
            return null;
        }
        Map<String, String> declaredVars = new LinkedHashMap<>();
        for (SysFormulaValidateRequest.Var var : request.getVars()) {
            if (null == var || null == var.getCode() || var.getCode().isBlank()) {
                continue;
            }
            declaredVars.put(var.getCode(), var.getType());
        }
        return declaredVars;
    }

    /**
     * 语法失败时兜底填充依赖（前端失败态仍可展示变量引用面）：extractDependencies
     * 仅在变量码不合法时抛 CS_FORMULA_VARIABLE_INVALID，此时维持空数组
     */
    private void fillDependenciesBestEffort(SysFormulaValidateResponse response, String expression) {
        if (!response.getDependencies().isEmpty()) {
            return;
        }
        try {
            response.setDependencies(engine.extractDependencies(expression));
        } catch (RuntimeException ignored) {
            // 变量码不合法等场景维持空数组（错误已在 errors 中承载）
        }
    }

    /**
     * 引擎错误翻译：IllegalArgumentException(message = 错误码 + ": " + 中文消息)
     * 拆解为 {@link SysFormulaErrorDo}；非 CS_FORMULA_ 前缀的意外形态统一收口
     * CS_FORMULA_EXECUTE_FAILED（不裸透出引擎内部异常形态）
     */
    private static SysFormulaErrorDo toError(IllegalArgumentException e) {
        String message = null == e.getMessage() ? "" : e.getMessage();
        if (message.startsWith("CS_FORMULA_")) {
            int split = message.indexOf(": ");
            if (split > 0) {
                return new SysFormulaErrorDo(message.substring(0, split), message.substring(split + 2));
            }
        }
        return new SysFormulaErrorDo(SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED, message);
    }
}
