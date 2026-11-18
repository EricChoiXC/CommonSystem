package com.eric.base.sys.formula.service;

import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.model.SysFormula;
import com.eric.base.sys.formula.model.SysFormulaErrorDo;
import com.eric.base.sys.formula.properties.SysFormulaConstants;
import com.eric.base.sys.formula.request.SysFormulaSimulateRequest;
import com.eric.base.sys.formula.response.SysFormulaSimulateResponse;
import com.eric.base.sys.formula.service.engine.FormulaContext;
import com.eric.base.sys.formula.service.engine.FormulaEngine;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * 公式模拟计算服务
 *
 * @author Eric
 * @date 2026/11/18
 * @description /simulate 接口的试算编排（方案 §3.2 时序 / §5.2 / §6.6，F14-BE-007）：
 *              <ul>
 *              <li>资源限制检查编排：表达式长度 / 引用变量个数 / 变量值序列化体积 /
 *                  循环次数封顶 / 独立线程执行超时，全部由 {@link FormulaEngine#execute}
 *                  内置前置检查链承担（常量收口 {@link SysFormulaConstants}，本服务不重复
 *                  检查、不散落魔数）；本服务编排动作是构造 {@link FormulaContext}——
 *                  调用方 vars 防御性拷贝为只读 + 服务端注入系统变量（{@code SYS_CURRENT_*}
 *                  出自 UserUtil 会话，前端同名传值被忽略，方案 §6.3）；</li>
 *              <li>受限执行：{@link FormulaEngine#execute}（编译缓存 + 沙箱裁剪 +
 *                  独立线程 Future 超时），结果按 expectedReturnType 适配
 *                  （NUMBER → BigDecimal 规整）；</li>
 *              <li>响应形态：成功 {@code success=true} + {@code result} +
 *                  {@code resultType}（期望类型缺省时按结果 Java 类型推断）；失败
 *                  {@code success=false} + {@code errors: [{ code, message }]}
 *                  （code ∈ CS_FORMULA_*），HTTP 仍 200（方案 §5.2，失败不阻断弹窗操作）；</li>
 *              <li>两条试算路径：内联表达式（HTTP /simulate 契约，request.expression）
 *                  与命名公式编码（{@link #simulateByCode}，服务内执行入口——方案 §8
 *                  v1.7 流程引擎等服务内消费方按 field_code 调用，不经 HTTP；执行前经
 *                  {@link SysFormulaService#requireEnabledFormula} 校验存在且启用，
 *                  不存在 / 已停用抛 CS_FORMULA_NOT_FOUND，错误以异常承载供执行侧
 *                  程序化判定）；管理员预判命名公式复用 /simulate：管理页 load 取
 *                  表达式原文后走内联路径，不单设预览接口（方案 §6.6）。</li>
 *              </ul>
 *              模拟赋值仅用于沙箱试算、入参由调用方自负，不代表任何真实业务数据授权
 *              （方案 §5.2 / A7）；取数数据边界由 DS_QUERY 委托数据源执行期鉴权承担。
 */
@Service
public class SysFormulaSimulateService {

    private final FormulaEngine engine;

    private final SysFormulaService formulaService;

    public SysFormulaSimulateService(FormulaEngine engine, SysFormulaService formulaService) {
        this.engine = engine;
        this.formulaService = formulaService;
    }

    /**
     * 内联公式试算（HTTP /simulate 契约，登录即可）：request.expression 沙箱受限执行，
     * 成功返回 result + resultType，失败以 errors 数组承载、HTTP 仍 200。
     *
     * @param request 试算请求（null 按空表达式处理，报 CS_FORMULA_SYNTAX_INVALID）
     * @return 试算结果（success / result / resultType / errors 恒非 null）
     */
    public SysFormulaSimulateResponse simulate(SysFormulaSimulateRequest request) {
        String expression = null == request ? null : request.getExpression();
        Map<String, Object> vars = null == request ? null : request.getVars();
        String expectedReturnType = null == request ? null : request.getExpectedReturnType();
        return executeInternal(expression, vars, expectedReturnType);
    }

    /**
     * 命名公式试算（服务内执行入口，方案 §8：v1.5 表单引擎 / v1.7 流程引擎等服务内
     * 消费方按 field_code 调用，不经 HTTP）：先经
     * {@link SysFormulaService#requireEnabledFormula} 校验公式存在且启用——
     * 编码不存在或已停用抛 CS_FORMULA_NOT_FOUND（供执行 / 试算侧程序化判定，
     * 方案 §3.3 / §5.3），随后按其存储表达式原文走与内联路径完全相同的
     * 资源限制 + 受限执行 + 类型适配链路。
     *
     * @param fieldCode          公式编码（消费方引用键）
     * @param vars               变量赋值表（可空；系统变量服务端注入优先）
     * @param expectedReturnType 期望返回类型文本（可空；不合法抛 CS_FORMULA_TYPE_MISMATCH）
     * @return 试算结果（success / result / resultType / errors）
     */
    public SysFormulaSimulateResponse simulateByCode(String fieldCode, Map<String, Object> vars,
                                                     String expectedReturnType) {
        SysFormula formula = formulaService.requireEnabledFormula(fieldCode);
        return executeInternal(formula.getFieldExpression(), vars, expectedReturnType);
    }

    /**
     * 试算公共链路：期望返回类型规整（不合法以 errors 承载 CS_FORMULA_TYPE_MISMATCH、
     * success=false）→ FormulaContext 装配（vars 只读包装 + 服务端系统变量注入）→
     * 引擎受限执行 → 结果装配。引擎抛出的 IllegalArgumentException（CS_FORMULA_*:
     * message）翻译为 errors 数组元素，不向调用方抛出（HTTP 仍 200 契约）。
     */
    private SysFormulaSimulateResponse executeInternal(String expression, Map<String, Object> vars,
                                                       String expectedReturnType) {
        SysFormulaSimulateResponse response = new SysFormulaSimulateResponse();
        SysFormulaReturnType expected = resolveExpectedReturnType(expectedReturnType, response);
        if (!response.getErrors().isEmpty()) {
            return response;
        }
        try {
            FormulaContext context = new FormulaContext(null == vars ? Map.of() : vars);
            Object result = engine.execute(expression, context, expected);
            response.setSuccess(true);
            response.setResult(result);
            response.setResultType(resolveResultType(result, expected));
        } catch (IllegalArgumentException e) {
            response.getErrors().add(toError(e));
        }
        return response;
    }

    /**
     * 期望返回类型规整：null / 空白 → null（跳过结果类型适配）；非空且不在
     * SysFormulaReturnType 枚举面 → errors 追加 CS_FORMULA_TYPE_MISMATCH 并返回 null
     * （由调用方短路返回，不进入执行）
     */
    private SysFormulaReturnType resolveExpectedReturnType(String expectedReturnType,
                                                           SysFormulaSimulateResponse response) {
        if (null == expectedReturnType || expectedReturnType.isBlank()) {
            return null;
        }
        SysFormulaReturnType expected = SysFormulaReturnType.of(expectedReturnType);
        if (null == expected) {
            response.getErrors().add(new SysFormulaErrorDo(
                    SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH, "期望返回类型不合法: " + expectedReturnType));
        }
        return expected;
    }

    /**
     * 实际返回类型：期望类型已声明时即其名（结果已经引擎按其适配）；未声明时按结果
     * Java 类型推断（NUMBER/STRING/BOOLEAN/DATE/ARRAY，方案 §5.2 resultType 语义），
     * 无法识别（null 等）返回 null
     */
    private static String resolveResultType(Object result, SysFormulaReturnType expected) {
        if (null != expected) {
            return expected.name();
        }
        if (null == result) {
            return null;
        }
        if (result instanceof Number) {
            return SysFormulaReturnType.NUMBER.name();
        }
        if (result instanceof String) {
            return SysFormulaReturnType.STRING.name();
        }
        if (result instanceof Boolean) {
            return SysFormulaReturnType.BOOLEAN.name();
        }
        if (result instanceof Date || result instanceof LocalDate || result instanceof LocalDateTime) {
            return SysFormulaReturnType.DATE.name();
        }
        if (result instanceof Collection || result.getClass().isArray()) {
            return SysFormulaReturnType.ARRAY.name();
        }
        return null;
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
