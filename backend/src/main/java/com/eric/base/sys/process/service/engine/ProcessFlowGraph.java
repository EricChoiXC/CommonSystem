package com.eric.base.sys.process.service.engine;

import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.process.model.SysProcessExampleLine;
import com.eric.base.sys.process.model.SysProcessExampleNode;
import com.eric.base.sys.process.model.SysProcessTemplateLine;
import com.eric.base.sys.process.model.SysProcessTemplateNode;
import com.eric.base.sys.process.properties.SysProcessConstants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程流转图（节点 + 连线快照的只读索引）
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-003 交付物（依据 docs/v1_7-流程管理/功能实现方案-改造分析与方案设计.md
 *              §3.2「图模型与节点键」与 §3.3「节点类型全集」）：把实例节点列表与连线列表
 *              构建为按节点键索引的有向图，供 {@link ProcessFlowEngine} 按连线遍历；
 *              <b>连线是流转唯一依据</b>，{@code field_node_no} 仅作展示序号与确定性排序。
 *
 *              <h3>节点键（node_key）</h3>
 *              <ul>
 *              <li>固定节点键：{@code N1}(START) / {@code N2}(DRAFT) / {@code N3}(END)；</li>
 *              <li>其余节点键由模板生成（{@code N4}、{@code N5}…），实例创建时随节点快照复制；</li>
 *              <li>兼容兜底：存量实例或模板节点未标注 node_key 时，按节点编号生成
 *                  {@code N{no}}（固定节点按类型优先归位 N1/N2/N3），保证旧数据仍可按连线流转。</li>
 *              </ul>
 *
 *              <h3>连线来源</h3>
 *              连线取自 {@code sys_process_example_line}（实例创建时的连线快照，P17-BE-007 起
 *              {@link ProcessFlowGraphLoader} 不再回退模板连线，实例无连线快照时直接抛流程异常）。
 *              图构建完成后模板连线与实例连线在引擎视角完全同构，引擎不感知来源。
 */
public class ProcessFlowGraph {

    /** 实例节点（key = 节点键），保插入顺序（DAO 按节点编号升序返回） */
    private final Map<String, SysProcessExampleNode> nodeByKey = new LinkedHashMap<>();

    /** 实例节点（key = 节点 id） */
    private final Map<String, SysProcessExampleNode> nodeById = new LinkedHashMap<>();

    /** 出边（key = 起始节点键），按分支序号 + 连线序号保序 */
    private final Map<String, List<ProcessFlowLine>> outgoing = new LinkedHashMap<>();

    /** 入边（key = 目标节点键） */
    private final Map<String, List<ProcessFlowLine>> incoming = new LinkedHashMap<>();

    /** 连线来源：true=实例连线快照（当前唯一来源，保留标记供排障与旧模板回退期断言） */
    private final boolean fromInstanceSnapshot;

    /**
     * 构建流程图。
     *
     * @param exampleNodes    实例节点（非空）
     * @param lines           连线（元素已归一为 {@link ProcessFlowLine}）
     * @param instanceSnapshot true=连线取自实例快照
     */
    public ProcessFlowGraph(List<SysProcessExampleNode> exampleNodes, List<ProcessFlowLine> lines,
                            boolean instanceSnapshot) {
        this.fromInstanceSnapshot = instanceSnapshot;
        for (SysProcessExampleNode node : exampleNodes) {
            String key = nodeKey(node);
            node.setFieldNodeKey(key);
            nodeByKey.put(key, node);
            if (StringUtil.isNotEmpty(node.getFieldId())) {
                nodeById.put(node.getFieldId(), node);
            }
        }
        if (null != lines) {
            for (ProcessFlowLine line : lines) {
                if (StringUtil.isEmpty(line.fromKey()) || StringUtil.isEmpty(line.toKey())) {
                    continue;
                }
                outgoing.computeIfAbsent(line.fromKey(), k -> new ArrayList<>()).add(line);
                incoming.computeIfAbsent(line.toKey(), k -> new ArrayList<>()).add(line);
            }
            // 出边按分支序号升序（同序号按连线 id 保序）：条件分支/并行分支的求值顺序
            for (List<ProcessFlowLine> branch : outgoing.values()) {
                branch.sort(java.util.Comparator.comparingInt(ProcessFlowLine::order)
                        .thenComparing(line -> null == line.lineId() ? "" : line.lineId()));
            }
        }
    }

    /* ==================== 节点访问 ==================== */

    /** 全部节点（按节点编号升序） */
    public List<SysProcessExampleNode> nodes() {
        return List.copyOf(nodeByKey.values());
    }

    /** 按节点键取节点；不存在返回 null */
    public SysProcessExampleNode node(String nodeKey) {
        return nodeByKey.get(nodeKey);
    }

    /** 按节点 id 取节点；不存在返回 null */
    public SysProcessExampleNode nodeById(String nodeId) {
        return nodeById.get(nodeId);
    }

    /** 按节点类型取首个节点（节点编号升序）；不存在返回 null */
    public SysProcessExampleNode nodeByType(String nodeType) {
        for (SysProcessExampleNode node : nodeByKey.values()) {
            if (nodeType.equals(node.getFieldNodeType())) {
                return node;
            }
        }
        return null;
    }

    /** 是否存在指定节点键 */
    public boolean contains(String nodeKey) {
        return nodeByKey.containsKey(nodeKey);
    }

    /** 连线是否取自实例快照（P17-BE-007 起实例连线为唯一来源，正常流转恒为 true） */
    public boolean fromInstanceSnapshot() {
        return fromInstanceSnapshot;
    }

    /* ==================== 连线访问 ==================== */

    /** 节点出边（按分支序号升序、分支序号相同按连线 id 保序）；无出边返回空表 */
    public List<ProcessFlowLine> outgoing(String nodeKey) {
        return outgoing.getOrDefault(nodeKey, List.of());
    }

    /** 节点入边；无入边返回空表 */
    public List<ProcessFlowLine> incoming(String nodeKey) {
        return incoming.getOrDefault(nodeKey, List.of());
    }

    /** 唯一出边；无出边或多于一条出边时抛 {@link ProcessFlowException}（节点类型出边数约束） */
    public ProcessFlowLine singleOutgoing(String nodeKey, String nodeName) {
        List<ProcessFlowLine> lines = outgoing(nodeKey);
        if (lines.isEmpty()) {
            throw ProcessFlowException.graph("节点「" + displayName(nodeName) + "」缺少出边，流程无法继续流转");
        }
        if (lines.size() > 1) {
            throw ProcessFlowException.graph("节点「" + displayName(nodeName) + "」存在多条出边，"
                    + "该节点类型仅允许一条出边");
        }
        return lines.get(0);
    }

    /* ==================== 节点键解析 ==================== */

    /**
     * 解析实例节点的节点键（实例侧兜底）：已标注 {@code field_node_key} 时直接使用；
     * 否则固定节点按类型归位 N1/N2/N3，其余按节点编号生成 {@code N{no}}。
     */
    public static String nodeKey(SysProcessExampleNode node) {
        if (StringUtil.isNotEmpty(node.getFieldNodeKey())) {
            return node.getFieldNodeKey().trim();
        }
        String nodeType = node.getFieldNodeType();
        if (SysProcessConstants.NODE_TYPE_START.equals(nodeType)) {
            return SysProcessConstants.FIXED_NODE_KEY_START;
        }
        if (SysProcessConstants.NODE_TYPE_DRAFT.equals(nodeType)) {
            return SysProcessConstants.FIXED_NODE_KEY_DRAFT;
        }
        if (SysProcessConstants.NODE_TYPE_END.equals(nodeType)) {
            return SysProcessConstants.FIXED_NODE_KEY_END;
        }
        Integer no = node.getFieldNodeNo();
        return null == no ? null : "N" + no;
    }

    /**
     * 解析模板节点的节点键（模板回退兜底，口径与实例侧一致）：已标注 {@code field_node_key}
     * 时直接使用；否则固定节点按类型归位 N1/N2/N3，其余按节点编号生成 {@code N{no}}。
     */
    public static String nodeKey(SysProcessTemplateNode node) {
        if (StringUtil.isNotEmpty(node.getFieldNodeKey())) {
            return node.getFieldNodeKey().trim();
        }
        String nodeType = node.getFieldNodeType();
        if (SysProcessConstants.NODE_TYPE_START.equals(nodeType)) {
            return SysProcessConstants.FIXED_NODE_KEY_START;
        }
        if (SysProcessConstants.NODE_TYPE_DRAFT.equals(nodeType)) {
            return SysProcessConstants.FIXED_NODE_KEY_DRAFT;
        }
        if (SysProcessConstants.NODE_TYPE_END.equals(nodeType)) {
            return SysProcessConstants.FIXED_NODE_KEY_END;
        }
        Integer no = node.getFieldNo();
        return null == no ? null : "N" + no;
    }

    /* ==================== 连线归一 ==================== */

    /** 实例连线 → 引擎连线（快照来源） */
    public static ProcessFlowLine ofInstanceLine(SysProcessExampleLine line) {
        return new ProcessFlowLine(line.getFieldId(), line.getFieldFromKey(), line.getFieldToKey(),
                line.getFieldLineType(), line.getFieldBranchOrder(), line.getFieldBranchName(),
                line.getFieldCondition(), Boolean.TRUE.equals(line.getFieldIsDefault()));
    }

    /**
     * 模板连线 → 引擎连线（模板侧图校验/预览归一，字段口径与实例连线一致）。
     * <p>P17-BE-007 起流转引擎连线只来自实例快照（{@link #ofInstanceLine}），
     * 本方法不再作为实例无连线时的回退来源。</p>
     */
    public static ProcessFlowLine ofTemplateLine(SysProcessTemplateLine line) {
        return new ProcessFlowLine(line.getFieldId(), line.getFieldFromKey(), line.getFieldToKey(),
                line.getFieldLineType(), line.getFieldBranchOrder(), line.getFieldBranchName(),
                line.getFieldCondition(), Boolean.TRUE.equals(line.getFieldIsDefault()));
    }

    private static String displayName(String nodeName) {
        return StringUtil.isEmpty(nodeName) ? "未命名节点" : nodeName.trim();
    }
}
