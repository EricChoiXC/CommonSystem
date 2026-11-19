package com.eric.base.sys.process.service.engine;

/**
 * 流程流转引擎异常（转流程异常信号）
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-003 交付物（依据 docs/v1_7-流程管理/功能实现方案-改造分析与方案设计.md
 *              §3.4 第 5 条与 docs/设计文档/sys/process/流程核心业务.md「流程异常（PAUSE + SYSTEM_ERROR）」）：
 *              引擎在流转过程中遇到「不可恢复且需管理员干预」的错误时抛出本异常，
 *              由 {@link ProcessFlowEngine} 的统一出口捕获后落流程异常——
 *              实例转 {@code PAUSE} + 专用留痕 {@code SYSTEM_ERROR}（错误摘要入留痕），
 *              <b>不回滚已完成节点</b>。
 *              <p>触发场景：</p>
 *              <ol>
 *              <li>审批人/抄送对象解析失败（{@link ProcessHandlerResolveException} 包装）；</li>
 *              <li>机器人节点流程任务执行失败（P17-BE-005 提供实现）；</li>
 *              <li>条件分支/并行分支求值失败（公式执行异常、结果非布尔）；</li>
 *              <li>并行分支无可流转分支且 FORK 配置为「报错」；</li>
 *              <li>其他引擎流转中不可恢复错误（如图结构缺失、无出边可走）。</li>
 *              </ol>
 */
public class ProcessFlowException extends RuntimeException {

    /** 错误摘要来源：审批人/抄送对象解析失败 */
    public static final String CODE_HANDLER_RESOLVE = "HANDLER_RESOLVE";

    /** 错误摘要来源：流程任务执行失败 */
    public static final String CODE_TASK_FAILED = "TASK_FAILED";

    /** 错误摘要来源：条件/分支求值失败 */
    public static final String CODE_CONDITION_EVAL = "CONDITION_EVAL";

    /** 错误摘要来源：并行分支无可流转分支 */
    public static final String CODE_NO_BRANCH = "NO_BRANCH";

    /** 错误摘要来源：图结构与流转规则错误（无出边、缺少固定节点等） */
    public static final String CODE_GRAPH = "GRAPH";

    /** 错误摘要来源：其他不可恢复错误 */
    public static final String CODE_SYSTEM = "SYSTEM";

    /** 错误码，供留痕摘要前缀与排障分流 */
    private final String code;

    public ProcessFlowException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ProcessFlowException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** 图结构/流转规则错误（无出边、缺少固定节点、默认流向非法等） */
    public static ProcessFlowException graph(String message) {
        return new ProcessFlowException(CODE_GRAPH, message);
    }

    /** 条件/并行分支求值失败 */
    public static ProcessFlowException condition(String message, Throwable cause) {
        return new ProcessFlowException(CODE_CONDITION_EVAL, message, cause);
    }

    /** 并行分支无可流转分支（FORK 配置为报错） */
    public static ProcessFlowException noBranch(String message) {
        return new ProcessFlowException(CODE_NO_BRANCH, message);
    }

    /** 流程任务执行失败 */
    public static ProcessFlowException task(String message, Throwable cause) {
        return new ProcessFlowException(CODE_TASK_FAILED, message, cause);
    }

    /** 其他不可恢复错误 */
    public static ProcessFlowException system(String message, Throwable cause) {
        return new ProcessFlowException(CODE_SYSTEM, message, cause);
    }

    /**
     * 无审批人（审批源解析结果为空）且节点配置 {@code emptyHandlerMode=error}（P17-BE-004）：
     * 引擎侧「emptyHandlerMode 为唯一出口」的判定结果，错误码沿用
     * {@link ProcessHandlerResolveException#CODE_EMPTY_HANDLER}，保证留痕摘要与解析器同码。
     */
    public static ProcessFlowException emptyHandler(String message) {
        return new ProcessFlowException(ProcessHandlerResolveException.CODE_EMPTY_HANDLER, message);
    }

    public String getCode() {
        return code;
    }
}
