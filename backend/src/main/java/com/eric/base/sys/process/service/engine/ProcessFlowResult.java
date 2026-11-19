package com.eric.base.sys.process.service.engine;

import lombok.Getter;

/**
 * 流程流转引擎流转结果
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-003 交付物：{@link ProcessFlowEngine} 每次「进入流转」的终态信号，
 *              供调用方（{@code SysProcessExampleService}）决定流程实例状态的落库口径：
 *              {@link #WAITING}=停留待人工处理（实例 PROCESSING）、
 *              {@link #FINISHED}=已到达结束节点（实例 FINISH）、
 *              {@link #PAUSED}=转流程异常（实例 PAUSE + SYSTEM_ERROR 留痕）。
 *              <p>引擎在每个流转出口负责写实例状态（PROCESSING / FINISH），调用方只需按终态
 *              决定是否回写业务文档状态，无需重复判定节点。</p>
 */
public class ProcessFlowResult {

    /** 流转终态：停留待人工处理（当前节点 PROCESSING 或 JOIN 等待汇聚） */
    public static final String WAITING = "WAITING";

    /** 流转终态：流程已办结（进入结束节点） */
    public static final String FINISHED = "FINISHED";

    /** 流转终态：转流程异常（实例 PAUSE，已完成节点不回滚） */
    public static final String PAUSED = "PAUSED";

    /** 流转终态标识 */
    @Getter
    private final String state;

    /** 转流程异常的错误摘要（仅 {@link #PAUSED} 非空），已写入 SYSTEM_ERROR 留痕 */
    @Getter
    private final String errorSummary;

    private ProcessFlowResult(String state, String errorSummary) {
        this.state = state;
        this.errorSummary = errorSummary;
    }

    /** 停留待人工处理 */
    public static ProcessFlowResult waiting() {
        return new ProcessFlowResult(WAITING, null);
    }

    /** 流程已办结 */
    public static ProcessFlowResult finished() {
        return new ProcessFlowResult(FINISHED, null);
    }

    /** 转流程异常（errorSummary 为错误摘要，已入留痕） */
    public static ProcessFlowResult paused(String errorSummary) {
        return new ProcessFlowResult(PAUSED, errorSummary);
    }

    /** 是否停留待人工处理 */
    public boolean isWaiting() {
        return WAITING.equals(state);
    }

    /** 是否已办结 */
    public boolean isFinished() {
        return FINISHED.equals(state);
    }

    /** 是否转流程异常 */
    public boolean isPaused() {
        return PAUSED.equals(state);
    }
}
