package com.eric.base.sys.process.service.engine;

/**
 * 流程节点处理人/抄送对象解析异常
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-002 解析服务对「转流程异常」的统一承载（依据
 *              docs/v1_7-流程管理/功能实现方案-改造分析与方案设计.md §5.6 第 4 条与
 *              §3.4 第 5 条）：审批人/抄送对象无法解析，或审批源解析结果为空且节点配置
 *              emptyHandlerMode = error 时抛出，由流程引擎（P17-BE-003）捕获后落
 *              流程异常（实例 PAUSE + SYSTEM_ERROR 留痕）。
 *              <p>错误码语义：</p>
 *              <ul>
 *              <li>{@link #CODE_EMPTY_HANDLER}：审批源解析结果为空且配置为不跳过；</li>
 *              <li>{@link #CODE_RESOLVE_FAILED}：地址本对象不存在、公式结果类型不受支持
 *                  或公式执行失败。</li>
 *              </ul>
 *              消息为中文可读文案（含节点名与原因），供流程异常摘要直接留痕。
 */
public class ProcessHandlerResolveException extends RuntimeException {

    /** 错误码：审批源解析结果为空且节点配置为不跳过（转流程异常） */
    public static final String CODE_EMPTY_HANDLER = "PROCESS_EMPTY_HANDLER";

    /** 错误码：地址本对象不存在 / 公式结果类型非法 / 公式执行失败 */
    public static final String CODE_RESOLVE_FAILED = "PROCESS_HANDLER_RESOLVE_FAILED";

    /** 错误码，供流程引擎按类型分流（留痕与提示文案） */
    private final String code;

    public ProcessHandlerResolveException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ProcessHandlerResolveException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** 审批源解析结果为空且节点配置 emptyHandlerMode = error */
    public static ProcessHandlerResolveException emptyHandler(String message) {
        return new ProcessHandlerResolveException(CODE_EMPTY_HANDLER, message);
    }

    /** 地址本对象不存在 / 公式结果类型非法（message 为中文可读原因） */
    public static ProcessHandlerResolveException resolveFailed(String message) {
        return new ProcessHandlerResolveException(CODE_RESOLVE_FAILED, message);
    }

    /** 公式执行失败（引擎抛出的 CS_FORMULA_* 语义经此包装，cause 保留原始异常） */
    public static ProcessHandlerResolveException formulaFailed(String message, Throwable cause) {
        return new ProcessHandlerResolveException(CODE_RESOLVE_FAILED, message, cause);
    }

    public String getCode() {
        return code;
    }
}
