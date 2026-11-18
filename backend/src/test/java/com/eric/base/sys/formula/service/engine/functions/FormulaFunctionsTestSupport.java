package com.eric.base.sys.formula.service.engine.functions;

import com.eric.base.sys.formula.spi.FormulaFunctionDefinition;
import com.eric.base.sys.formula.spi.FormulaFunctionProvider;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Options;

import java.util.HashMap;
import java.util.Map;

/**
 * 内置函数测试支撑
 *
 * @author Eric
 * @date 2026/11/18
 * @description 构造注册了全部内置函数 Provider 的 Aviator 执行实例（对齐 F14-BE-004
 *              引擎装配形态：new AviatorEvaluatorInstance + addFunction），供各分类
 *              单测与集成性批量验证共用；mock SysDatasourceService 的 DataFunctionProvider
 *              由各测试自建（避免静态依赖）。
 */
public final class FormulaFunctionsTestSupport {

    private FormulaFunctionsTestSupport() {
    }

    /**
     * 注册全部给定 Provider 的函数到新 Aviator 实例（MAX_LOOP_COUNT 封顶对齐引擎配置）
     */
    public static AviatorEvaluatorInstance newEngine(FormulaFunctionProvider... providers) {
        AviatorEvaluatorInstance instance = AviatorEvaluator.newInstance();
        instance.setOption(Options.MAX_LOOP_COUNT, 10000L);
        for (FormulaFunctionProvider provider : providers) {
            for (FormulaFunctionDefinition definition : provider.getFunctions()) {
                instance.addFunction(definition.getFunction());
            }
        }
        return instance;
    }

    /**
     * 空环境求值
     */
    public static Object eval(AviatorEvaluatorInstance engine, String expression) {
        return engine.execute(expression, new HashMap<>());
    }

    /**
     * 变量环境求值
     */
    public static Object eval(AviatorEvaluatorInstance engine, String expression, Map<String, Object> env) {
        return engine.execute(expression, env);
    }
}
