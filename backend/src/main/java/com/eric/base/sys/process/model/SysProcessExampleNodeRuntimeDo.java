package com.eric.base.sys.process.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 流程实例节点运行态文档对象
 *
 * @author Eric
 * @date 2026/11/19
 * @description 对应 sys_process_example_node.field_runtime（JSON 字符串）的结构化映射，
 *              由 Jackson 序列化/反序列化，禁止手工拼串（依据
 *              docs/设计文档/sys/process/流程v1_7数据库设计分册.md §4.4 与 §8.4「实例节点运行态 JSON」）。
 *              <p>结构（v1 冻结）：</p>
 *              <pre>{@code
 * {"activeBranchKeys": ["N5","N7"], "arrivedBranchKeys": ["N5"], "joinMode": "all"}
 *              }</pre>
 *              <ul>
 *              <li>{@code activeBranchKeys}：FORK 激活的分支首节点键集合（记于 FORK 节点运行态）；</li>
 *              <li>{@code arrivedBranchKeys}：JOIN 已到达的分支键集合（记于 JOIN 节点运行态，
 *                  与 {@code activeBranchKeys} 取交集判定汇聚是否满足）；</li>
 *              <li>{@code joinMode}：冗余 FORK 配置的汇聚模式（{@code all} 全部 / {@code any} 任一），
 *                  便于 JOIN 判定不回溯上游。</li>
 *              </ul>
 *              <p>仅 FORK / JOIN 节点有值，其余节点为 NULL（视为空运行态）；嵌套并行本期不支持，
 *              同一实例内 FORK 与 JOIN 一对一。本对象为引擎内部运行态，不透出到
 *              {@code SysProcessExampleNodeVo}。</p>
 */
@Data
public class SysProcessExampleNodeRuntimeDo {

    /** FORK 激活的分支首节点键集合（按激活顺序） */
    private List<String> activeBranchKeys = new ArrayList<>();

    /** JOIN 已到达的分支键集合（按到达顺序） */
    private List<String> arrivedBranchKeys = new ArrayList<>();

    /** 汇聚模式：all=全部激活分支到达后流出 / any=任一到达即流出（冗余 FORK 配置） */
    private String joinMode;
}
