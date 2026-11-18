package com.eric.base.sys.formula.service.engine;

import com.eric.base.sys.formula.spi.FormulaFunctionDefinition;
import com.eric.base.sys.formula.spi.FormulaFunctionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公式函数注册中心
 *
 * @author Eric
 * @date 2026/11/18
 * @description 收集容器内全部 {@link FormulaFunctionProvider}（方案 §2.2：内置函数九类与
 *              SPI 扩展函数同一注册通道），构建进程内只读索引 functionCode → 定义
 *              （元数据 + Aviator 执行体），三类消费方共用同一份元数据：
 *              <ul>
 *              <li>/functions 接口（F14-BE-008）：getAllFunctions 导出元数据，
 *                  前端函数面板与插入模板按 category 分组渲染；</li>
 *              <li>引擎（F14-BE-004）：getFunction(code).getFunction() 取执行体，
 *                  经 AviatorEvaluatorInstance#addFunction 注册到执行实例；</li>
 *              <li>参数类型校验（F14-BE-007）：按注册元数据校验实参类型与个数。</li>
 *              </ul>
 *              functionCode 全局唯一：重复注册输出 WARN 且首个定义生效，不阻断启动
 *              （对齐 SysDatasourceRegistry 先例）；无编码或编码空白的定义告警跳过。
 *              扩展方式：实现 {@link FormulaFunctionProvider} 并注册为 Spring Bean——
 *              内置函数（F14-BE-003 九类，落 service/engine/functions 包）与业务模块
 *              扩展（org ORG_*、datasource DS_QUERY 等）走同一通道；不提供构造后注册入口
 *              （索引只读，避免运行期变更），不新增 ApplicationRunner（启动期统一收口
 *              SysStartupTask，本类为被动组件，构造期完成收集即可）。
 */
@Component
public class FormulaFunctionRegistry {

    private static final Logger log = LoggerFactory.getLogger(FormulaFunctionRegistry.class);

    /** 函数编码 → 定义（LinkedHashMap 保序，供 /functions 按注册顺序稳定展示） */
    private final Map<String, FormulaFunctionDefinition> functionsByCode;

    public FormulaFunctionRegistry(List<FormulaFunctionProvider> providerList) {
        Map<String, FormulaFunctionDefinition> index = new LinkedHashMap<>();
        for (FormulaFunctionProvider provider : providerList) {
            for (FormulaFunctionDefinition definition : provider.getFunctions()) {
                if (null == definition || null == definition.getFunctionCode()
                        || definition.getFunctionCode().isEmpty()) {
                    log.warn("formula function definition missing or blank code, skipped");
                    continue;
                }
                if (null != index.putIfAbsent(definition.getFunctionCode(), definition)) {
                    log.warn("duplicate formula function code [{}], first definition wins",
                            definition.getFunctionCode());
                }
            }
        }
        this.functionsByCode = Collections.unmodifiableMap(index);
        log.info("formula function registry built: {} functions from {} providers",
                index.size(), providerList.size());
    }

    /** 按函数编码取定义（元数据 + 执行体）；不存在返回 null */
    public FormulaFunctionDefinition getFunction(String functionCode) {
        return null == functionCode ? null : functionsByCode.get(functionCode);
    }

    /**
     * 全量函数定义（保注册顺序，不可变；/functions 元数据导出与后端参数类型校验共用同一份）
     */
    public List<FormulaFunctionDefinition> getAllFunctions() {
        return List.copyOf(functionsByCode.values());
    }
}
