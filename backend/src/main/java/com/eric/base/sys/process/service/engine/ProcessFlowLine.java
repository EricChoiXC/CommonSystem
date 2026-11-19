package com.eric.base.sys.process.service.engine;

import com.eric.base.sys.process.properties.SysProcessConstants;

/**
 * 流程图连线（实例连线快照与模板连线回退的统一视图）
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-003 交付物：把 {@code sys_process_example_line}（实例快照）与
 *              {@code sys_process_template_line}（回退桥梁）归一为同一只读视图，
 *              引擎只依赖本视图，不感知连线来源（依据
 *              docs/设计文档/sys/process/流程v1_7数据库设计分册.md §3.1.3 / §3.2.3）。
 *
 * @param lineId       连线 id（同名属性保序兜底）
 * @param fromKey      起始节点键
 * @param toKey        目标节点键
 * @param lineType     连线类型：NORMAL / CONDITION / PARALLEL / MANUAL
 * @param branchOrder  分支序号（条件/并行分支判断顺序，升序求值；null 视为 0）
 * @param branchName   分支名称（可空）
 * @param condition    分支条件公式（条件分支判断条件 / 并行分支启动条件，可空）
 * @param defaultLine  是否默认分支（条件分支节点有且仅有一条）
 */
public record ProcessFlowLine(String lineId,
                              String fromKey,
                              String toKey,
                              String lineType,
                              Integer branchOrder,
                              String branchName,
                              String condition,
                              boolean defaultLine) {

    /** 分支序号归一（null 视为 0，保证升序排序稳定） */
    public int order() {
        return null == branchOrder ? 0 : branchOrder;
    }

    /** 是否条件分支连线 */
    public boolean isConditionLine() {
        return SysProcessConstants.LINE_TYPE_CONDITION.equals(lineType);
    }

    /** 是否默认分支连线（defaultLine=true 的连线，条件分支节点的兜底出边） */
    public boolean isDefaultLine() {
        return defaultLine;
    }
}
