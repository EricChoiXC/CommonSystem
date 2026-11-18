package com.eric.base.sys.formula.service.engine;

import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.properties.SysFormulaConstants;
import com.eric.base.sys.formula.spi.FormulaFunctionDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Feature;
import com.googlecode.aviator.Options;
import com.googlecode.aviator.exception.ExpressionSyntaxErrorException;
import com.googlecode.aviator.exception.ExpressionRuntimeException;
import com.googlecode.aviator.exception.FunctionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 公式统一执行入口（F14-BE-004，方案 §2.1 / §3.2 / §6.3）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 编排链（方案 §3.2 时序 4~7 步）：`${}` 解析替换（显示/存储层原样保留
 *              `${}` 形态，执行前替换为 Aviator 标识符并构建双向映射；字符串字面量内的
 *              `${}` 属展示文本，不替换不投影）→ 路径投影（`${list.field}` 在求值前投影为
 *              元素字段值数组，供集合函数消费）→ Aviator 编译（独立实例 + LRU 编译缓存，
 *              错误行号与原因原样透出）→ 受限执行（formula-engine-executor 独立线程 +
 *              Future 超时，超时取消并抛 CS_FORMULA_EXECUTE_TIMEOUT）→ 结果类型适配
 *              （按 expectedReturnType 校验，NUMBER → BigDecimal 规整）。
 *              <p>核心约定：</p>
 *              <ul>
 *              <li>「校验不执行、执行必受限」：{@link #validate} 仅编译 + 静态检查，
 *                  不求值；{@link #execute} 必经独立线程 + 超时上限的受限执行；</li>
 *              <li>错误抛出约定（对齐 SysDatasourceService#datasourceError 先例）：
 *                  统一 {@code IllegalArgumentException(错误码 + ": " + message)}，
 *                  错误码取 {@link SysFormulaConstants} 的 CS_FORMULA_* 常量；
 *                  引擎不引入受检异常，不裸抛 Aviator 原生异常；</li>
 *              <li>变量标识符映射为无歧义可逆替换：`${code}` →
 *                  {@code __f<序号>_<code>}（如 `${ROUND}` → `__f0_ROUND`），
 *                  序号保证同码多次出现可逆映射唯一；带序号前缀不与任何函数名/保留字
 *                  形态冲突（`${if}`、`${ROUND}` 与 ROUND 函数共存，R03 风险收口）；
 *                  变量码约束 {@code [a-zA-Z_][a-zA-Z0-9_]*}，两级路径投影
 *                  `${list.field}` 中 list 同约束、field 为元素 Map 键；</li>
 *              <li>函数存在性（validate）：调用名在函数注册中心或 Aviator 实例内置面
 *                  （math.round / seq.map 等带点形态）即放行，均不存在的调用名报
 *                  CS_FORMULA_FUNCTION_NOT_FOUND——Aviator 5.4.3 函数调用编译期不报错
 *                  （实测 NOPE(1) 可编译、执行期才 FunctionNotFoundException），校验语义
 *                  必须前置收口；execute 阶段由运行期异常翻译兜底；</li>
 *              <li>引擎不直连任何 Mapper/HTTP（数据面只经 DS_QUERY 函数委托），函数执行体
 *                  经 {@link FormulaFunctionRegistry} 注册到独立 Aviator 实例；</li>
 *              <li>Aviator 5.4.3 无 NIL_WHEN_MISSING_VARIABLE（Options 实测枚举面不含），
 *                  缺失变量语义实测为：读引用得 null（nil），参与算术/比较运算时
 *                  ExpressionRuntimeException 确定性失败——由引擎捕获转
 *                  CS_FORMULA_EXECUTE_FAILED；宽松校验对未知变量放行（见 validate Javadoc）；</li>
 *              <li>沙箱裁剪（F14-BE-005，方案 §6.1，Aviator 5.4.3 实测裁决）：
 *                  FEATURE_SET 关闭 StaticMethods / StaticFields / NewInstance / Use /
 *                  Module 五个 Java 调用面 Feature（编译期即抛
 *                  UnsupportedFeatureException → CS_FORMULA_SYNTAX_INVALID）；
 *                  ALLOWED_CLASS_SET 收口 {@link SysFormulaConstants#SANDBOX_ALLOWED_CLASSES}
 *                  最小白名单（System/Runtime/IO/网络/反射类一律不在集内）；
 *                  ENABLE_PROPERTY_SYNTAX_SUGAR 关闭——属性语法糖（a.b）可对任意
 *                  env 对象做反射式 getter 链访问（实测 rt.class 可取得 Class 对象），
 *                  关闭后 a.b 走 Map 键访问语义（对 Map 变量取键、非 Map/缺失键得 nil），
 *                  与公式引擎「变量值进表达式统一由 ${} 替换为扁平标识符」的形态一致，
 *                  深层取值由内置函数（路径投影/集合函数）承担；无 ALLOW_JAVA_CALL
 *                  选项（5.4.3 枚举面不含），Java 调用面裁剪以 Feature + 类白名单组合收口；</li>
 *              <li>资源限制前置检查链（F14-BE-005，方案 §3.2 时序 3 / §6.2）：
 *                  执行入口先于编译求值按序检查——表达式长度（CS_FORMULA_EXPRESSION_TOO_LONG）
 *                  → 引用变量个数（CS_FORMULA_EXECUTE_FAILED，消息说明变量个数超限）
 *                  → 变量值序列化体积（Jackson 序列化测长，超限 CS_FORMULA_EXECUTE_FAILED），
 *                  不合格表达式在编译/求值前即被拒绝。</li>
 *              </ul>
 */
@Component
public class FormulaEngine implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(FormulaEngine.class);

    /** `${}` 引用形态（变量码约束 [a-zA-Z_][a-zA-Z0-9_]*；路径投影仅两级 list.field） */
    private static final Pattern VAR_PATTERN =
            Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)(?:\\.([a-zA-Z_][a-zA-Z0-9_]*))?}");

    /** 函数调用形态（允许一层带点命名空间，如 seq.map / math.round） */
    private static final Pattern FUNCTION_CALL_PATTERN =
            Pattern.compile("((?:[a-zA-Z_][a-zA-Z0-9_]*\\.)?[a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(");

    /** 标识符映射前缀（带序号，替换可逆且不与函数名/保留字冲突） */
    private static final String IDENT_PREFIX = "__f";

    /** 表达式内行分隔（编译错误行号透出按行定位） */
    private static final String LINE_SEPARATOR = "\n";

    /**
     * 变量值序列化测长用 ObjectMapper（进程内复用，不随执行构造）：注册
     * JavaTimeModule 兼容 LocalDate/LocalDateTime 变量值，FAIL_ON_EMPTY_BEANS
     * 关闭——无法序列化的对象（如内部句柄类）按空串计长，测长失败由异常分支按 0
     * 字节放行（体积门禁只在可序列化值上生效，不可序列化值交由执行期兜底）
     */
    private static final ObjectMapper VARS_SIZE_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    /**
     * 沙箱裁剪（F14-BE-005，方案 §6.1；Aviator 5.4.3 实测裁决，探测结论固化）：
     * <ul>
     * <li>FEATURE_SET 关闭五个 Java 调用面 Feature——StaticMethods（静态方法调用
     *     编译期抛 UnsupportedFeatureException）、StaticFields（#类.字段 静态引用
     *     执行期拒绝）、NewInstance（new 构造编译期拒绝）、Use（use 语句动态引类）、
     *     Module（模块系统）；保留 If/ForLoop/WhileLoop/Let/Lambda/Fn/Assignment/
     *     Return/LexicalScope/InternalVars/ExceptionHandle/StringInterpolation
     *     （表达式语义必需：if 分支、for/while 循环、赋值与 lambda 均在方案语法面内）；</li>
     * <li>ALLOWED_CLASS_SET 收口常量白名单（SysFormulaConstants.SANDBOX_ALLOWED_CLASSES），
     *     System/Runtime/IO/网络/反射类不在集内；</li>
     * <li>ENABLE_PROPERTY_SYNTAX_SUGAR 关闭——属性语法糖可对任意 env 对象做
     *     getter 链反射访问（实测 Runtime 实例 .class 可取 Class 对象），
     *     关闭后点访问仅剩 Map 键语义，公式内深层取值由路径投影与函数承担。</li>
     * </ul>
     * 实测拒绝行为（5.4.3）：System.currentTimeMillis() → FunctionNotFoundException、
     * new java.io.File(...) → UnsupportedFeatureException（编译期）、#Long.MAX_VALUE →
     * Could not find variable（ExpressionRuntimeException，执行期）、
     * use java.lang.Runtime → UnsupportedFeatureException（编译期）、Class.forName →
     * FunctionNotFoundException——全部确定性失败，无一处可达执行体。
     */
    private static void applySandboxOptions(AviatorEvaluatorInstance instance) {
        instance.setOption(Options.FEATURE_SET, EnumSet.complementOf(EnumSet.of(
                Feature.StaticMethods,
                Feature.StaticFields,
                Feature.NewInstance,
                Feature.Use,
                Feature.Module)));
        instance.setOption(Options.ALLOWED_CLASS_SET, SysFormulaConstants.SANDBOX_ALLOWED_CLASSES);
        instance.setOption(Options.ENABLE_PROPERTY_SYNTAX_SUGAR, false);
    }

    /** 独立 Aviator 执行实例（不污染 AviatorEvaluator 全局默认实例；构造期一次性装配） */
    private final AviatorEvaluatorInstance aviator;

    /** LRU 编译缓存：Aviator 文本 → 编译产物（synchronized LinkedHashMap removeEldest） */
    private final Map<String, Expression> compileCache;

    /** 受限执行线程池（模块命名 formula-engine-executor，优雅关闭） */
    private final ExecutorService executor;

    /** 执行超时上限（毫秒）；生产取常量，测试经构造注入小值（R04 风险缓解） */
    private final long executeTimeoutMillis;

    /** 注册中心全部函数（validate 函数存在性检查与 Aviator 装配共用） */
    private final FormulaFunctionRegistry functionRegistry;

    /**
     * 生产构造：装配注册中心全部函数、LRU 编译缓存（容量 COMPILE_CACHE_CAPACITY）、
     * 受限执行线程池与常量超时上限
     */
    @org.springframework.beans.factory.annotation.Autowired
    public FormulaEngine(FormulaFunctionRegistry functionRegistry) {
        this(functionRegistry, SysFormulaConstants.EXECUTE_TIMEOUT_MILLIS);
    }

    /**
     * 测试构造：注入超时上限毫秒数（R04 风险缓解——超时用例以小值触发，不留真实等待）
     */
    FormulaEngine(FormulaFunctionRegistry functionRegistry, long executeTimeoutMillis) {
        this.functionRegistry = functionRegistry;
        this.aviator = AviatorEvaluator.newInstance();
        applySandboxOptions(this.aviator);
        this.aviator.setOption(Options.MAX_LOOP_COUNT, SysFormulaConstants.MAX_LOOP_COUNT);
        for (FormulaFunctionDefinition definition : functionRegistry.getAllFunctions()) {
            if (null != definition.getFunction()) {
                this.aviator.addFunction(definition.getFunction());
            }
        }
        int capacity = SysFormulaConstants.COMPILE_CACHE_CAPACITY;
        this.compileCache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Expression> eldest) {
                return size() > capacity;
            }
        };
        this.executeTimeoutMillis = executeTimeoutMillis;
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "formula-engine-executor");
            thread.setDaemon(true);
            return thread;
        });
        log.info("formula engine initialized: {} functions, compile cache capacity {}, execute timeout {}ms",
                functionRegistry.getAllFunctions().size(), capacity, executeTimeoutMillis);
    }

    @Override
    public void destroy() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /* ==================== 变量依赖提取 ==================== */

    /**
     * 提取表达式变量依赖（save/validate 共用，命名公式落 sys_formula.field_dependencies）：
     * 提取 `${}` 变量码数组——普通变量取码本身，路径投影取根码（`${details.amount}` →
     * details），去重保序；字符串字面量内的 `${}` 属展示文本不收集。变量码不合法
     * （不符合标识符约束 / 路径投影格式错误）抛 CS_FORMULA_VARIABLE_INVALID——save/update
     * 前强制 validate 通过（方案 §5.1），依赖提取只会发生在校验通过之后。
     *
     * @param expression 公式文本（${} 形态原样）
     * @return 变量码数组（去重保序；无引用返回空数组）
     */
    public List<String> extractDependencies(String expression) {
        if (null == expression || expression.isBlank()) {
            return List.of();
        }
        Set<String> dependencies = new LinkedHashSet<>();
        for (VarRef ref : findReferences(expression)) {
            dependencies.add(ref.rootCode());
        }
        return List.copyOf(dependencies);
    }

    /* ==================== 校验（不执行） ==================== */

    /**
     * 公式静态校验（校验不执行，方案 §5.2 validate 契约的引擎层落点）：
     * <ol>
     * <li>表达式非空与长度上限（CS_FORMULA_EXPRESSION_TOO_LONG）；</li>
     * <li>${} 变量码合法性（CS_FORMULA_VARIABLE_INVALID）与引用变量个数上限
     *     （CS_FORMULA_EXECUTE_FAILED，F14-BE-005 资源限制口径——个数超限归
     *     方案 §6.2 资源限制，不再占用 VARIABLE_INVALID 语义）；</li>
     * <li>函数存在性——代码段（字符串字面量外）中以「函数名 + (」形态出现的函数名
     *     须在注册中心或 Aviator 实例内置面存在（CS_FORMULA_FUNCTION_NOT_FOUND），
     *     同时覆盖嵌套与连续调用形态；</li>
     * <li>Aviator 编译（词法/语法错误抛 CS_FORMULA_SYNTAX_INVALID，
     *     引擎错误信息含行号与原因，原样透出）；</li>
     * <li>strict 时类型/必填检查：declaredVars 中声明的变量必须在表达式出现、
     *     表达式引用的变量必须已声明（CS_FORMULA_VARIABLE_INVALID）、类型声明非法
     *     （CS_FORMULA_TYPE_MISMATCH）。</li>
     * </ol>
     * <p>宽松模式（strict=false 或 declaredVars 为 null/空）：变量视为未知，
     * 不做声明核对（Aviator 5.4.3 编译期不解析变量存在性——无 Options.NIL_WHEN_MISSING_VARIABLE
     * 可配，缺失变量执行期才确定性报错，与「宽松仅语法与函数校验」契约对齐）；
     * 引用未声明变量不阻断。</p>
     *
     * @param expression        公式文本（${} 形态原样）
     * @param declaredVars      变量声明（code → 类型名 NUMBER/STRING/BOOLEAN/DATE/ARRAY，
     *                          大小写不敏感）；null 或空即宽松模式
     * @param expectedReturnType 期望返回类型，null 跳过返回类型检查
     * @param strict            严格模式开关（true 时核对 declaredVars 与引用一致性）
     * @return 变量依赖（去重保序，与 extractDependencies 同口径）
     */
    public List<String> validate(String expression, Map<String, String> declaredVars,
                                 SysFormulaReturnType expectedReturnType, boolean strict) {
        requireExpressionText(expression);
        List<VarRef> references = findReferences(expression);
        requireReferenceCount(references);
        validateFunctionCalls(expression);
        compile(parseAndReplace(expression, references).aviatorText(), expression);
        boolean strictCheck = strict && null != declaredVars && !declaredVars.isEmpty();
        if (strictCheck) {
            Set<String> dependencies = new LinkedHashSet<>();
            references.forEach(ref -> dependencies.add(ref.rootCode()));
            for (String code : dependencies) {
                if (!declaredVars.containsKey(code)) {
                    throw formulaError(SysFormulaConstants.CS_FORMULA_VARIABLE_INVALID,
                            "表达式引用了未声明变量: " + code);
                }
            }
            for (String code : declaredVars.keySet()) {
                if (!dependencies.contains(code)) {
                    throw formulaError(SysFormulaConstants.CS_FORMULA_VARIABLE_INVALID,
                            "声明的变量未被表达式引用: " + code);
                }
            }
            for (Map.Entry<String, String> entry : declaredVars.entrySet()) {
                if (null == SysFormulaReturnType.of(entry.getValue())) {
                    throw formulaError(SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH,
                            "变量类型声明不合法: " + entry.getKey() + " -> " + entry.getValue());
                }
            }
        }
        return extractDependencies(expression);
    }

    /* ==================== 执行（必受限） ==================== */

    /**
     * 受限执行（方案 §3.2 时序 4~7 步 + F14-BE-005 资源限制前置检查链）：
     * 资源限制检查（表达式长度 → 引用变量个数 → 变量值序列化体积，方案 §3.2 时序 3，
     * 先于编译求值拒绝不合格输入）→ ${} 解析替换 → 路径投影 → 编译（缓存命中复用）
     * → 注入上下文变量 → formula-engine-executor 独立线程执行 + Future 超时 →
     * 结果类型适配。任何失败以 IllegalArgumentException(CS_FORMULA_*: message) 抛出：
     * <ul>
     * <li>资源超限：长度超限 CS_FORMULA_EXPRESSION_TOO_LONG；变量个数超限 /
     *     变量值序列化体积超限 CS_FORMULA_EXECUTE_FAILED（消息说明具体上限）；</li>
     * <li>缺失变量：读引用得 nil，参与运算时 ExpressionRuntimeException →
     *     CS_FORMULA_EXECUTE_FAILED（携带 Aviator 确定性失败信息）；</li>
     * <li>未知函数：FunctionNotFoundException → CS_FORMULA_FUNCTION_NOT_FOUND；</li>
     * <li>函数内确定性错误（除零/空值拒绝等）：函数按约定抛
     *     CS_FORMULA_EXECUTE_FAILED 前缀 IllegalArgumentException，原样透出；</li>
     * <li>超时：Future 超时后 cancel(true) 中断执行线程 → CS_FORMULA_EXECUTE_TIMEOUT。</li>
     * </ul>
     * 执行异常日志只含表达式摘要与错误码，不落变量值明文（方案 §6.5）。
     *
     * @param expression         公式文本（${} 形态原样）
     * @param context            只读执行上下文（调用方变量 + 服务端系统变量）
     * @param expectedReturnType 期望返回类型，null 跳过结果类型校验
     * @return 类型适配后的执行结果
     */
    public Object execute(String expression, FormulaContext context, SysFormulaReturnType expectedReturnType) {
        requireExpressionText(expression);
        List<VarRef> references = findReferences(expression);
        requireReferenceCount(references);
        checkVarsSize(context);
        Parsed parsed = parseAndReplace(expression, references);
        Expression compiled = compile(parsed.aviatorText(), expression);
        Map<String, Object> env = buildEnv(parsed, context);
        Object raw = executeRestricted(compiled, env, expression);
        return adaptResult(raw, expectedReturnType);
    }

    /* ==================== 资源限制前置检查链（F14-BE-005，方案 §3.2 时序 3 / §6.2） ==================== */

    /**
     * 引用变量个数检查（检查链第二道）：引用变量个数超 MAX_VARIABLE_COUNT 抛
     * CS_FORMULA_EXECUTE_FAILED（消息说明变量个数超限）——个数超限属资源限制
     * 语义（方案 §6.2），不占用 CS_FORMULA_VARIABLE_INVALID（该码留给变量码
     * 合法性错误）；validate 与 execute 共用同一道门禁，口径一致
     */
    private static void requireReferenceCount(List<VarRef> references) {
        if (references.size() > SysFormulaConstants.MAX_VARIABLE_COUNT) {
            throw formulaError(SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED,
                    "引用变量个数超上限 " + SysFormulaConstants.MAX_VARIABLE_COUNT
                            + ": " + references.size());
        }
    }

    /**
     * 变量值序列化体积检查（检查链第三道）：上下文变量表经 Jackson 序列化
     * （ObjectMapper 进程内复用，注册 JavaTimeModule 兼容日期类型）测长，字节
     * 体积超 MAX_VARS_SERIALIZE_BYTES 抛 CS_FORMULA_EXECUTE_FAILED；序列化
     * 自身失败按 0 字节放行——体积门禁只对可序列化值生效，不可序列化值
     * （如内部句柄对象）交由执行期函数空值/类型拒绝兜底，不在此处误伤
     */
    private static void checkVarsSize(FormulaContext context) {
        if (null == context || context.getVars().isEmpty()) {
            return;
        }
        try {
            byte[] payload = VARS_SIZE_MAPPER.writeValueAsBytes(context.getVars());
            if (payload.length > SysFormulaConstants.MAX_VARS_SERIALIZE_BYTES) {
                throw formulaError(SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED,
                        "变量值序列化体积超上限 " + SysFormulaConstants.MAX_VARS_SERIALIZE_BYTES
                                + " 字节: " + payload.length);
            }
        } catch (JsonProcessingException ignored) {
            // 不可序列化变量表不阻断执行：体积门禁只对可序列化值生效（方法 Javadoc）
        }
    }

    /* ==================== ${} 解析与路径投影 ==================== */

    /** ${} 引用定位（字符串字面量外）：起止偏移 + 根变量码 + 路径投影字段键（可空） */
    private record VarRef(int start, int end, String rootCode, String fieldKey) {
    }

    /**
     * 解析替换产物：aviatorText 为替换后的可编译文本；identToCode 为
     * 替换标识符 → 变量码映射；projections 为路径投影声明
     */
    private record Parsed(String aviatorText,
                          Map<String, String> identToCode,
                          Map<String, PathProjection> projections) {
    }

    /** 路径投影声明（`${list.field}`）：根变量码 + 元素 Map 键 */
    private record PathProjection(String rootCode, String fieldKey) {
    }

    /**
     * 定位代码段（字符串字面量外）内的全部 `${}` 引用：'…' 与 "…" 字面量以内的
     * `${}` 属展示文本不收集不替换；`${` 后不是合法引用形态（不符合
     * `[a-zA-Z_][a-zA-Z0-9_]*` 或路径投影格式）抛 CS_FORMULA_VARIABLE_INVALID
     */
    private List<VarRef> findReferences(String expression) {
        List<VarRef> references = new ArrayList<>();
        for (int[] segment : codeSegments(expression)) {
            int cursor = segment[0];
            while (cursor < segment[1]) {
                int open = expression.indexOf("${", cursor);
                if (open < 0 || open >= segment[1]) {
                    break;
                }
                Matcher matcher = VAR_PATTERN.matcher(expression);
                matcher.region(open, segment[1]);
                if (!matcher.lookingAt()) {
                    throw formulaError(SysFormulaConstants.CS_FORMULA_VARIABLE_INVALID,
                            "变量码不合法（约束 [a-zA-Z_][a-zA-Z0-9_]*，路径投影仅两级 list.field）"
                                    + "，位置 " + open + " 附近: " + snippet(expression, open));
                }
                references.add(new VarRef(open, matcher.end(), matcher.group(1), matcher.group(2)));
                cursor = matcher.end();
            }
        }
        return references;
    }

    /**
     * 代码段扫描：'…' 与 "…" 字符串字面量以外的区间集合（含转义引号 \\' \\" 识别），
     * 供 ${} 引用定位与函数调用检查共用（字面量内的 `${}` / 函数名字样不参与解析）
     */
    private static List<int[]> codeSegments(String expression) {
        List<int[]> segments = new ArrayList<>();
        int length = expression.length();
        int segmentStart = 0;
        int i = 0;
        while (i < length) {
            char current = expression.charAt(i);
            if ('\'' == current || '"' == current) {
                if (i > segmentStart) {
                    segments.add(new int[]{segmentStart, i});
                }
                char quote = current;
                i++;
                while (i < length) {
                    char inner = expression.charAt(i);
                    if ('\\' == inner && i + 1 < length) {
                        i += 2;
                        continue;
                    }
                    i++;
                    if (inner == quote) {
                        break;
                    }
                }
                segmentStart = i;
            } else {
                i++;
            }
        }
        if (segmentStart < length) {
            segments.add(new int[]{segmentStart, length});
        }
        return segments;
    }

    /**
     * ${} 解析替换：代码段内 `${code}` → `__f<序号>_<code>`（可逆映射），`${list.field}`
     * 记录投影声明并整体替换为根码标识符；字符串字面量与其他文本原样保留
     */
    private Parsed parseAndReplace(String expression, List<VarRef> references) {
        StringBuilder replaced = new StringBuilder(expression.length() + 64);
        Map<String, String> identToCode = new LinkedHashMap<>();
        Map<String, PathProjection> projections = new LinkedHashMap<>();
        int index = 0;
        int cursor = 0;
        for (VarRef ref : references) {
            replaced.append(expression, cursor, ref.start());
            String ident = IDENT_PREFIX + index++ + "_" + ref.rootCode();
            identToCode.put(ident, ref.rootCode());
            if (null != ref.fieldKey()) {
                projections.put(ident, new PathProjection(ref.rootCode(), ref.fieldKey()));
            }
            replaced.append(ident);
            cursor = ref.end();
        }
        replaced.append(expression, cursor, expression.length());
        return new Parsed(replaced.toString(), identToCode, projections);
    }

    /**
     * 函数存在性静态检查（CS_FORMULA_FUNCTION_NOT_FOUND）：扫描代码段（字面量外）
     * 中「函数名 + (」调用形态，函数名不在注册中心且非 Aviator 实例内置面即报错。
     * Aviator 5.4.3 函数调用编译期不报错（执行期才 FunctionNotFoundException），
     * 校验语义必须在编译层前置收口（方案 §5.2 validate 需报告函数错误）
     */
    private void validateFunctionCalls(String expression) {
        for (int[] segment : codeSegments(expression)) {
            Matcher matcher = FUNCTION_CALL_PATTERN.matcher(expression);
            matcher.region(segment[0], segment[1]);
            while (matcher.find()) {
                String name = matcher.group(1);
                if (name.startsWith(IDENT_PREFIX)) {
                    continue;
                }
                if (null == functionRegistry.getFunction(name) && !aviator.containsFunction(name)) {
                    throw formulaError(SysFormulaConstants.CS_FORMULA_FUNCTION_NOT_FOUND,
                            "调用未注册函数: " + name);
                }
            }
        }
    }

    /**
     * 构建求值环境：替换标识符逐一绑定变量值——普通变量直取上下文值，路径投影项
     * 在求值前投影为元素字段值数组供集合函数消费；同码多次出现绑定同一值
     */
    private Map<String, Object> buildEnv(Parsed parsed, FormulaContext context) {
        Map<String, Object> vars = null == context ? Map.of() : context.getVars();
        Map<String, Object> env = new LinkedHashMap<>(vars);
        for (Map.Entry<String, String> entry : parsed.identToCode().entrySet()) {
            String ident = entry.getKey();
            PathProjection projection = parsed.projections().get(ident);
            if (null != projection) {
                env.put(ident, projectPath(vars, projection));
            } else {
                env.put(ident, vars.get(entry.getValue()));
            }
        }
        return env;
    }

    /**
     * 路径投影：根变量须为数组/集合且元素为 Map（否则 CS_FORMULA_EXECUTE_FAILED
     * 确定性报错），取元素 Map 的 field 键组成数组供集合函数消费（方案 §2.1 明细汇总
     * 场景）；根变量缺失（nil 语义）按空数组投影
     */
    private List<Object> projectPath(Map<String, Object> vars, PathProjection projection) {
        List<Object> projected = new ArrayList<>();
        Object root = vars.get(projection.rootCode());
        if (null == root) {
            return projected;
        }
        List<?> elements = sequenceOfRoot(root, projection.rootCode());
        for (Object element : elements) {
            if (!(element instanceof Map<?, ?> elementMap)) {
                throw formulaError(SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED,
                        "路径投影 " + projection.rootCode() + "." + projection.fieldKey()
                                + " 的元素不是 Map（实际 " + typeName(element) + "），无法投影字段 "
                                + projection.fieldKey());
            }
            projected.add(elementMap.get(projection.fieldKey()));
        }
        return projected;
    }

    /** 根变量集合化：数组/集合放行，其他类型确定性报错（null 根码按空数组——缺失变量 nil 语义） */
    private List<?> sequenceOfRoot(Object root, String rootCode) {
        if (root instanceof Collection<?> collection) {
            return List.copyOf(collection);
        }
        if (root.getClass().isArray()) {
            List<Object> list = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(root);
            for (int i = 0; i < length; i++) {
                list.add(java.lang.reflect.Array.get(root, i));
            }
            return list;
        }
        throw formulaError(SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED,
                "路径投影根变量 " + rootCode + " 不是数组或集合（实际 " + typeName(root) + "）");
    }

    /* ==================== 编译（LRU 缓存） ==================== */

    /**
     * 编译（LRU 缓存命中复用，容量 COMPILE_CACHE_CAPACITY）；缓存键为替换后文本
     * （函数注册面进程内不变，键不含函数集指纹）；词法/语法错误抛
     * CS_FORMULA_SYNTAX_INVALID，Aviator 错误信息（含偏移与行号，实测形如
     * 「Syntax error: ... at 4, lineNumber: 2, ...」）原样透出，并附原始文本出错行内容
     */
    private Expression compile(String aviatorText, String originalExpression) {
        Expression compiled;
        synchronized (compileCache) {
            compiled = compileCache.get(aviatorText);
        }
        if (null != compiled) {
            return compiled;
        }
        try {
            // cache=false：编译产物由本类 LRU 缓存收口，不写入 Aviator 实例内部缓存
            compiled = aviator.compile(aviatorText, false);
        } catch (ExpressionSyntaxErrorException e) {
            throw formulaError(SysFormulaConstants.CS_FORMULA_SYNTAX_INVALID,
                    syntaxMessage(e.getMessage(), originalExpression));
        } catch (RuntimeException e) {
            throw formulaError(SysFormulaConstants.CS_FORMULA_SYNTAX_INVALID,
                    "表达式编译失败: " + e.getMessage());
        }
        synchronized (compileCache) {
            compileCache.put(aviatorText, compiled);
        }
        return compiled;
    }

    /** 编译错误消息组装：引擎原始信息（含行号）原样保留 + 原始文本对应行内容辅助定位 */
    private String syntaxMessage(String aviatorMessage, String originalExpression) {
        String message = null == aviatorMessage ? "" : aviatorMessage;
        Matcher lineMatcher = Pattern.compile("lineNumber:\\s*(\\d+)").matcher(message);
        if (lineMatcher.find()) {
            try {
                int lineNumber = Integer.parseInt(lineMatcher.group(1));
                String[] lines = originalExpression.split(Pattern.quote(LINE_SEPARATOR), -1);
                if (lineNumber >= 1 && lineNumber <= lines.length) {
                    return message + "；出错行内容: " + lines[lineNumber - 1].trim();
                }
            } catch (NumberFormatException ignored) {
                // 行号解析失败时原样透出引擎信息
            }
        }
        return message;
    }

    /* ==================== 受限执行 ==================== */

    /**
     * 受限执行：formula-engine-executor 线程池提交求值任务，Future.get(超时上限)
     * 等待——超时 cancel(true) 中断执行线程并抛 CS_FORMULA_EXECUTE_TIMEOUT；
     * 执行失败统一转 CS_FORMULA_* 语义（不再裸抛 Aviator 原生异常）：求值任务抛出的
     * 任何 Throwable（除零 ArithmeticException、Aviator 运行时异常、StackOverflowError
     * 等 Error）经 ExecutionException.cause 进入统一翻译（translateExecutionFailure）
     * 转确定性错误码，不允许裸异常穿透到方法外；任务提交失败（线程池已关闭等）
     * 同样兜底为 CS_FORMULA_EXECUTE_FAILED。异常日志统一为
     * 「表达式摘要 + 错误类型/码」，不落变量值明文（方案 §6.5/§6.6）
     */
    private Object executeRestricted(Expression compiled, Map<String, Object> env, String expression) {
        Future<Object> future;
        try {
            future = executor.submit(() -> compiled.execute(env));
        } catch (RuntimeException rejection) {
            log.warn("formula execute submit rejected [{}], expression digest: {}",
                    rejection.getClass().getSimpleName(), digest(expression));
            throw formulaError(SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED,
                    "公式执行任务提交失败: " + rejection.getMessage());
        }
        try {
            return future.get(executeTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            log.warn("formula execute timeout ({}ms), expression digest: {}", executeTimeoutMillis, digest(expression));
            throw formulaError(SysFormulaConstants.CS_FORMULA_EXECUTE_TIMEOUT,
                    "公式执行超时（上限 " + executeTimeoutMillis + "ms），已中断执行线程");
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = null == e.getCause() ? e : e.getCause();
            throw translateExecutionFailure(cause, expression);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw formulaError(SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED,
                    "公式执行等待被中断");
        }
    }

    /**
     * 执行失败翻译：CS_FORMULA_* 前缀的业务错误（函数确定性错误）原样透出；
     * FunctionNotFoundException → CS_FORMULA_FUNCTION_NOT_FOUND（含沙箱裁剪后
     * 静态方法调用形态——StaticMethods 关闭使 System.currentTimeMillis 等
     * Java 方法调用面在函数表无注册）；ArithmeticException（除零/数值溢出）→
     * CS_FORMULA_EXECUTE_FAILED；其余 ExpressionRuntimeException（缺失变量参与
     * 运算、循环超限等）与其余任何 Throwable（含 UnsupportedFeatureException
     * 漏网形态、Error）→ CS_FORMULA_EXECUTE_FAILED；日志只记表达式摘要与错误码，
     * 不落变量值
     */
    private RuntimeException translateExecutionFailure(Throwable cause, String expression) {
        if (cause instanceof IllegalArgumentException illegal
                && null != illegal.getMessage() && illegal.getMessage().startsWith("CS_FORMULA_")) {
            return illegal;
        }
        if (cause instanceof FunctionNotFoundException functionNotFound) {
            log.warn("formula execute failed [CS_FORMULA_FUNCTION_NOT_FOUND], expression digest: {}",
                    digest(expression));
            return formulaError(SysFormulaConstants.CS_FORMULA_FUNCTION_NOT_FOUND,
                    "调用未注册函数: " + functionNotFound.getMessage());
        }
        if (cause instanceof ArithmeticException arithmetic) {
            log.warn("formula execute failed [CS_FORMULA_EXECUTE_FAILED], expression digest: {}",
                    digest(expression));
            return formulaError(SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED,
                    "公式算术错误（除零或数值溢出）"
                            + (null == arithmetic.getMessage() ? "" : ": " + arithmetic.getMessage()));
        }
        if (cause instanceof ExpressionRuntimeException runtimeException) {
            log.warn("formula execute failed [CS_FORMULA_EXECUTE_FAILED], expression digest: {}",
                    digest(expression));
            return formulaError(SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED,
                    null == runtimeException.getMessage() ? "公式执行失败" : runtimeException.getMessage());
        }
        log.warn("formula execute failed [CS_FORMULA_EXECUTE_FAILED], expression digest: {}", digest(expression));
        return formulaError(SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED,
                "公式执行失败: " + cause.getClass().getSimpleName()
                        + (null == cause.getMessage() ? "" : ": " + cause.getMessage()));
    }

    /* ==================== 结果类型适配 ==================== */

    /**
     * 结果类型适配（方案 §3.2 第 7 步）：expectedReturnType 为 null 跳过校验；
     * NUMBER → BigDecimal 规整（规则见 {@link #adaptNumber}）；STRING → String；
     * BOOLEAN → Boolean；DATE → Date/LocalDate/LocalDateTime；ARRAY → List。
     * 不匹配抛 CS_FORMULA_TYPE_MISMATCH。
     */
    private Object adaptResult(Object result, SysFormulaReturnType expectedReturnType) {
        if (null == expectedReturnType) {
            return result;
        }
        return switch (expectedReturnType) {
            case NUMBER -> adaptNumber(result);
            case STRING -> adaptString(result);
            case BOOLEAN -> adaptBoolean(result);
            case DATE -> adaptDate(result);
            case ARRAY -> adaptArray(result);
        };
    }

    /**
     * NUMBER 规整规则（小数位随计算结果原样保留，展示层格式化是消费方职责）：
     * <ul>
     * <li>null → CS_FORMULA_EXECUTE_FAILED（数值结果缺失视为执行失败，不以 null 冒充数值）；</li>
     * <li>BigDecimal 原样；BigInteger → BigDecimal；</li>
     * <li>整型（Byte/Short/Integer/Long）→ BigDecimal.valueOf(longValue)；内置数值函数
     *     整值结果经 FormulaFunctions#wrapNumber 以整型形态承载（如 ROUND(130.0, 2) →
     *     Long 130），规整后 scale = 0，§3.2 样例的 130.00 展示形态由消费方格式化；</li>
     * <li>浮点（Float/Double）→ new BigDecimal(Double.toString(value))——十进制最短表示
     *     （0.1 stays 0.1，不经二进制近似尾巴）；Float 同理经 Float.toString；</li>
     * <li>其他 Number（原子类等）→ new BigDecimal(String.valueOf(value))；</li>
     * <li>数值文本 → new BigDecimal(text.trim())（宽容收编），解析失败
     *     CS_FORMULA_TYPE_MISMATCH；布尔/日期/集合等非数值形态 →
     *     CS_FORMULA_TYPE_MISMATCH。</li>
     * </ul>
     */
    private BigDecimal adaptNumber(Object result) {
        if (null == result) {
            throw formulaError(SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED,
                    "数值结果为空（公式未产生数值，可用 IFNULL 兜底）");
        }
        try {
            if (result instanceof BigDecimal bigDecimal) {
                return bigDecimal;
            }
            if (result instanceof java.math.BigInteger bigInteger) {
                return new BigDecimal(bigInteger);
            }
            if (result instanceof Long || result instanceof Integer
                    || result instanceof Short || result instanceof Byte) {
                return BigDecimal.valueOf(((Number) result).longValue());
            }
            if (result instanceof Double doubleValue) {
                return new BigDecimal(Double.toString(doubleValue));
            }
            if (result instanceof Float floatValue) {
                return new BigDecimal(Float.toString(floatValue));
            }
            if (result instanceof Number number) {
                return new BigDecimal(String.valueOf(number));
            }
            if (result instanceof String text && !text.isBlank()) {
                return new BigDecimal(text.trim());
            }
        } catch (NumberFormatException e) {
            throw formulaError(SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH,
                    "结果无法规整为数值: " + describe(result));
        }
        throw formulaError(SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH,
                "结果不是数值: " + describe(result));
    }

    private String adaptString(Object result) {
        if (null == result) {
            throw formulaError(SysFormulaConstants.CS_FORMULA_EXECUTE_FAILED,
                    "字符串结果为空（可用 IFNULL 兜底）");
        }
        if (result instanceof String text) {
            return text;
        }
        throw formulaError(SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH,
                "结果不是字符串: " + describe(result));
    }

    private Boolean adaptBoolean(Object result) {
        if (result instanceof Boolean bool) {
            return bool;
        }
        throw formulaError(SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH,
                "结果不是布尔值: " + describe(null == result ? "null" : result));
    }

    private Object adaptDate(Object result) {
        if (result instanceof Date || result instanceof LocalDate || result instanceof LocalDateTime) {
            return result;
        }
        throw formulaError(SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH,
                "结果不是日期: " + describe(null == result ? "null" : result));
    }

    private List<?> adaptArray(Object result) {
        if (result instanceof List<?> list) {
            return list;
        }
        throw formulaError(SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH,
                "结果不是数组: " + describe(null == result ? "null" : result));
    }

    /* ==================== 公共小件 ==================== */

    /** 表达式基础检查：null/空白（CS_FORMULA_SYNTAX_INVALID）与长度上限（CS_FORMULA_EXPRESSION_TOO_LONG） */
    private void requireExpressionText(String expression) {
        if (null == expression || expression.isBlank()) {
            throw formulaError(SysFormulaConstants.CS_FORMULA_SYNTAX_INVALID, "表达式不能为空");
        }
        if (expression.length() > SysFormulaConstants.MAX_EXPRESSION_LENGTH) {
            throw formulaError(SysFormulaConstants.CS_FORMULA_EXPRESSION_TOO_LONG,
                    "表达式长度超上限 " + SysFormulaConstants.MAX_EXPRESSION_LENGTH + ": " + expression.length());
        }
    }

    /** CS_* 错误码异常构造（对齐 SysDatasourceService#datasourceError 先例） */
    private static IllegalArgumentException formulaError(String code, String message) {
        return new IllegalArgumentException(code + ": " + message);
    }

    /** 表达式摘要（日志用，截断防刷屏；不落变量值，只落文本形态） */
    private static String digest(String expression) {
        String flat = expression.replaceAll("\\s+", " ").trim();
        return flat.length() <= 120 ? flat : flat.substring(0, 120) + "...";
    }

    /** 出错位置附近片段（变量码错误提示用） */
    private static String snippet(String expression, int position) {
        int end = Math.min(expression.length(), position + 40);
        return expression.substring(position, end);
    }

    /** 结果类型描述（错误提示用，不输出值本身防敏感数据进日志） */
    private static String describe(Object value) {
        return null == value ? "null" : value.getClass().getSimpleName();
    }

    /** 类型名描述 */
    private static String typeName(Object value) {
        return null == value ? "null" : value.getClass().getSimpleName();
    }
}
