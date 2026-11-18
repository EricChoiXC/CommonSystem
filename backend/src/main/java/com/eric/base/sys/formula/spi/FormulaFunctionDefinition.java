package com.eric.base.sys.formula.spi;

import com.eric.base.sys.formula.model.SysFormulaFunctionDo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.googlecode.aviator.runtime.type.AviatorFunction;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 公式函数定义（元数据 + Aviator 执行体）
 *
 * @author Eric
 * @date 2026/11/18
 * @description SPI 注册与注册中心索引的基本单元（方案 §2.2）：继承 {@link SysFormulaFunctionDo}
 *              携带函数元数据（/functions 接口透出与后端参数类型校验共用同一份），
 *              并持有 {@link AviatorFunction} 执行体（Aviator 5.4.3 AbstractFunction 子类，
 *              由 FormulaEngine 注册到执行实例后供表达式调用）。function 字段经
 *              {@code @JsonIgnore} 排除序列化——元数据可整体 Jackson 输出且不泄漏执行体。
 *              functionCode 全局唯一：重复注册由注册中心告警且首个定义生效。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FormulaFunctionDefinition extends SysFormulaFunctionDo {

    /** Aviator 执行体（引擎经 AviatorEvaluatorInstance#addFunction 注册后供表达式调用）；不参与 Jackson 序列化 */
    @JsonIgnore
    private AviatorFunction function;
}
