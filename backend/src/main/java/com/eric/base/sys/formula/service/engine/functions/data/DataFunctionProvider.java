package com.eric.base.sys.formula.service.engine.functions.data;

import com.eric.base.sys.datasource.service.SysDatasourceService;
import com.eric.base.sys.formula.enums.SysFormulaFunctionCategory;
import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.service.engine.functions.FormulaFunctions;
import com.eric.base.sys.formula.spi.FormulaFunctionDefinition;
import com.eric.base.sys.formula.spi.FormulaFunctionProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DATA 取数函数 Provider（方案 §2.2 首批：DS_QUERY）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 内置函数注册单元之一。DS_QUERY 是取数收口函数（方案 §6.4 A4）：
 *              不出现 HTTP/SQL 调用，仅委托 {@link SysDatasourceService#execute} 统一
 *              执行入口——委托目标 action 鉴权、条件白名单、字段投影、超时与缓存策略
 *              全部随数据源模块既有语义，本函数不放宽任何边界。
 *              实参透传语义：code 须为非空文本；body 为 Map（Aviator 表达式内以
 *              seq.map(...) 构造或变量传入）或 JSON 对象文本，null 视为无调用方条件；
 *              调用方条件受数据源条件白名单与字面量限制约束（固定条件不可覆盖）。
 *              返回投影后行数组（List&lt;Map&gt;，R04 列头以 fields 为准，如需计数用 COUNT）。
 *              鉴权异常（UNAUTHORIZED/FORBIDDEN）与数据源错误码异常原样上抛，
 *              由引擎统一捕获转错误响应（§6.5）。
 */
@Component
public class DataFunctionProvider implements FormulaFunctionProvider {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> BODY_MAP_TYPE = new TypeReference<>() {
    };

    /** 数据源统一执行入口（DS_QUERY 唯一数据通道，A4 收口） */
    private final SysDatasourceService datasourceService;

    public DataFunctionProvider(SysDatasourceService datasourceService) {
        this.datasourceService = datasourceService;
    }

    @Override
    public List<FormulaFunctionDefinition> getFunctions() {
        List<FormulaFunctionDefinition> functions = new ArrayList<>();
        functions.add(FormulaFunctions.definition("DS_QUERY", "数据源取数", SysFormulaFunctionCategory.DATA,
                "DS_QUERY(code, body)：经数据源统一执行入口取数（A4 收口）——委托"
                        + "SysDatasourceService#execute，鉴权/超时/缓存随数据源既有语义，"
                        + "未登录/无委托目标权限分别抛 UNAUTHORIZED/FORBIDDEN；"
                        + "code 为数据源编码，body 为调用方条件（Map 经 seq.map 构造或变量传入，"
                        + "JSON 对象文本亦可，null 视为无条件，固定条件不可覆盖）；"
                        + "返回投影后行数组（List&lt;Map&gt;），可交 COUNT/SUM 等集合函数消费",
                SysFormulaReturnType.ARRAY, new DsQueryFunction(datasourceService),
                FormulaFunctions.param("code", "STRING", true, null),
                FormulaFunctions.param("body", "MAP", false, null)));
        return functions;
    }

    /** DS_QUERY(code, body)：委托 SysDatasourceService#execute 取行数组 */
    public static class DsQueryFunction extends AbstractFunction {

        private final SysDatasourceService datasourceService;

        public DsQueryFunction(SysDatasourceService datasourceService) {
            this.datasourceService = datasourceService;
        }

        @Override
        public String getName() {
            return "DS_QUERY";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject code, AviatorObject body) {
            String sourceCode = requireCode(code.getValue(env));
            Map<String, Object> conditionBody = resolveBody(body.getValue(env));
            SysDatasourceService.SysDatasourceExecution execution =
                    datasourceService.execute(sourceCode, conditionBody, null, null, null);
            List<Map<String, Object>> rows = null == execution.rows()
                    ? List.of() : execution.rows();
            return AviatorRuntimeJavaType.valueOf(rows);
        }

        /** code 须为非空文本（数据源编码） */
        private String requireCode(Object value) {
            if (value instanceof String text && !text.isBlank()) {
                return text.trim();
            }
            throw FormulaFunctions.executeError("DS_QUERY 参数 code 须为非空文本");
        }

        /**
         * body 宽容解析：null → 空条件；Map 透传；JSON 对象文本经 Jackson 解析
         * （确定性强，无 eval 面）；其余类型拒绝
         */
        @SuppressWarnings("unchecked")
        private Map<String, Object> resolveBody(Object value) {
            if (null == value) {
                return new HashMap<>();
            }
            if (value instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            if (value instanceof String text) {
                try {
                    return JSON_MAPPER.readValue(text, BODY_MAP_TYPE);
                } catch (Exception e) {
                    throw FormulaFunctions.executeError("DS_QUERY 参数 body JSON 解析失败");
                }
            }
            throw FormulaFunctions.executeError(
                    "DS_QUERY 参数 body 须为 Map 或 JSON 对象文本: " + value.getClass().getSimpleName());
        }
    }
}
