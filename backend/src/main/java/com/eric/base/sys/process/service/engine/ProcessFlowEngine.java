package com.eric.base.sys.process.service.engine;

import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.setting.service.CommonSettingService;
import com.eric.base.sys.process.dao.SysProcessAuditNoteDao;
import com.eric.base.sys.process.dao.SysProcessExampleDao;
import com.eric.base.sys.process.dao.SysProcessExampleNodeDao;
import com.eric.base.sys.process.dao.SysProcessExampleWorkitemDao;
import com.eric.base.sys.process.model.SysProcessAuditNote;
import com.eric.base.sys.process.model.SysProcessExample;
import com.eric.base.sys.process.model.SysProcessExampleNode;
import com.eric.base.sys.process.model.SysProcessExampleNodeRuntimeDo;
import com.eric.base.sys.process.model.SysProcessExampleWorkitem;
import com.eric.base.sys.process.model.SysProcessExampleWorkitemConfigDo;
import com.eric.base.sys.process.model.SysProcessTemplateNodeConfigDo;
import com.eric.base.sys.process.properties.SysProcessConstants;
import com.eric.base.sys.process.service.SysProcessDocumentFieldService;
import com.eric.base.sys.formula.service.engine.FormulaContext;
import com.eric.base.sys.formula.service.engine.FormulaEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 流程流转引擎（节点 + 连线图遍历）
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-003 交付物：把存量「按 {@code field_no} 顺延的 while 循环」重构为
 *              按连线的节点进入 / 流出引擎（依据 docs/v1_7-流程管理/功能实现方案-改造分析与方案设计.md
 *              §3.2 图模型与节点键、§3.3 节点类型全集、§3.4 流转引擎设计）。
 *
 *              <h3>一、流转唯一依据</h3>
 *              连线（{@code sys_process_example_line}，实例无快照时回退
 *              {@code sys_process_template_line}）是流转唯一依据；{@code field_node_no} /
 *              {@code field_no} 仅作展示序号与确定性排序。节点键 {@code field_node_key}：
 *              固定 {@code N1}(START) / {@code N2}(DRAFT) / {@code N3}(END)，其余 {@code N4}、{@code N5}…
 *
 *              <h3>二、10 类节点语义</h3>
 *              <table border="1">
 *              <caption>节点类型 × 引擎语义</caption>
 *              <tr><th>类型</th><th>语义</th></tr>
 *              <tr><td>START</td><td>直接沿唯一出边流转</td></tr>
 *              <tr><td>DRAFT</td><td>节点 PROCESSING，等待起草人提交</td></tr>
 *              <tr><td>END</td><td>进入即流程 FINISH</td></tr>
 *              <tr><td>APPROVE</td><td>解析审批人→单签/会签/串行→生成工作项；无需审批工作项则沿出边继续</td></tr>
 *              <tr><td>CC</td><td>解析抄送对象→留痕 CC_AUTO + 节点 FINISH + 继续（通知发送留给 P17-BE-012/BE-014）</td></tr>
 *              <tr><td>CONDITION</td><td>按 field_branch_order 升序求出边 field_condition，取第一个 true；无 true 走默认边</td></tr>
 *              <tr><td>FORK</td><td>逐出边求值启动条件，激活分支各自继续，汇聚上下文记入 field_runtime</td></tr>
 *              <tr><td>JOIN</td><td>记录到达分支，满足 joinMode（all/any）后流出，否则等待</td></tr>
 *              <tr><td>ROBORT</td><td>执行节点配置的流程任务→成功 FINISH 继续／失败转流程异常</td></tr>
 *              <tr><td>MANUAL</td><td>按最近一次人工指定流向流出，无指定走 defaultFlowKey</td></tr>
 *              </table>
 *
 *              <h3>三、并行汇聚（单层）</h3>
 *              FORK 激活的分支集合记于 FORK 节点 {@code field_runtime.activeBranchKeys}，
 *              JOIN 已到达的分支集合记于 JOIN 节点 {@code field_runtime.arrivedBranchKeys}，
 *              {@code joinMode} 冗余 FORK 配置便于 JOIN 判定不回溯上游；
 *              <b>嵌套并行本期不支持</b>（模板保存校验拒绝）。
 *
 *              <h3>四、流程异常</h3>
 *              公式解析失败、机器人任务失败、并行无可流转分支（FORK 配置报错）→ 实例转
 *              {@code PAUSE} + 专用留痕 {@code SYSTEM_ERROR}（错误摘要入留痕），
 *              <b>不回滚已完成节点</b>。
 */
@Service
public class ProcessFlowEngine {

    private static final Logger log = LoggerFactory.getLogger(ProcessFlowEngine.class);

    /** 单次流转步数上限（图存在环或连线异常时的死循环护栏） */
    private static final int MAX_FLOW_STEPS = 1000;

    private final SysProcessExampleDao exampleDao;
    private final SysProcessExampleNodeDao nodeDao;
    private final SysProcessExampleWorkitemDao workitemDao;
    private final SysProcessAuditNoteDao auditNoteDao;
    private final CommonSettingService settingService;
    private final ProcessFlowGraphLoader graphLoader;
    private final ProcessFlowSupport support;
    private final ProcessHandlerResolver handlerResolver;
    private final SysProcessDocumentFieldService documentFieldService;
    private final FormulaEngine formulaEngine;
    private final ObjectProvider<ProcessTaskExecutor> taskExecutorProvider;

    public ProcessFlowEngine(SysProcessExampleDao exampleDao,
                             SysProcessExampleNodeDao nodeDao,
                             SysProcessExampleWorkitemDao workitemDao,
                             SysProcessAuditNoteDao auditNoteDao,
                             CommonSettingService settingService,
                             ProcessFlowGraphLoader graphLoader,
                             ProcessFlowSupport support,
                             ProcessHandlerResolver handlerResolver,
                             SysProcessDocumentFieldService documentFieldService,
                             FormulaEngine formulaEngine,
                             ObjectProvider<ProcessTaskExecutor> taskExecutorProvider) {
        this.exampleDao = exampleDao;
        this.nodeDao = nodeDao;
        this.workitemDao = workitemDao;
        this.auditNoteDao = auditNoteDao;
        this.settingService = settingService;
        this.graphLoader = graphLoader;
        this.support = support;
        this.handlerResolver = handlerResolver;
        this.documentFieldService = documentFieldService;
        this.formulaEngine = formulaEngine;
        this.taskExecutorProvider = taskExecutorProvider;
    }

    /* ==================== 对外入口 ==================== */

    /**
     * 进入流转：从指定节点沿连线遍历，直到停留待人工处理（WAITING）、办结（FINISHED）
     * 或转流程异常（PAUSED）。
     *
     * @param example  流程实例（非空，引擎在出口落库实例状态）
     * @param entryKey 进入节点键（非空）
     * @return 流转终态
     */
    public ProcessFlowResult enterFlow(SysProcessExample example, String entryKey) {
        if (StringUtil.isEmpty(entryKey)) {
            throw ProcessFlowException.graph("流程流转缺少进入节点键");
        }
        FlowContext context = buildContext(example);
        context.queue.addLast(new FlowTask(entryKey.trim(), null));
        resumePendingBranches(context);
        return run(context, "进入流转");
    }

    /**
     * 审批人通过后继续流转（含串行模式生成下一顺序人工作项）。
     *
     * @param example  流程实例
     * @param nodeKey  已通过的节点键
     * @param nodeName 已通过的节点名称（异常文案用）
     * @return 流转终态
     */
    public ProcessFlowResult pass(SysProcessExample example, String nodeKey, String nodeName) {
        return pass(example, nodeKey, nodeName, null);
    }

    /**
     * 审批人通过后继续流转（含串行模式生成下一顺序人工作项）。
     *
     * @param example    流程实例
     * @param nodeKey    已通过的节点键
     * @param nodeName   已通过的节点名称（异常文案用）
     * @param workitemId 通过的工作项 id（并行分支内节点用于回溯分支归属；可空）
     * @return 流转终态
     */
    public ProcessFlowResult pass(SysProcessExample example, String nodeKey, String nodeName,
                                  String workitemId) {
        FlowContext context = buildContext(example);
        SysProcessExampleNode node = context.graph.node(nodeKey);
        String name = null == node ? nodeName : node.getFieldNodeName();
        if (!advanceApprove(context, nodeKey, name)) {
            // 串行模式尚未走完或会签未全员完成：节点保持审批中
            return ProcessFlowResult.waiting();
        }
        // 节点已通过：从该节点沿出边继续流转（节点本身不再重新进入）
        enqueueOutgoing(context, nodeKey, resolveBranchKey(context, nodeKey, workitemId));
        resumePendingBranches(context);
        return run(context, "审批通过后流转");
    }

    /**
     * 解析节点所属并行分支键（跨调用恢复场景）：节点运行于并行分支内且分支键未知时，
     * 按节点工作项配置中记录的 flowBranchKey 回溯；非并行分支节点返回 null。
     */
    private String resolveBranchKey(FlowContext context, String nodeKey, String workitemId) {
        if (context.branchByNode.containsKey(nodeKey)) {
            return context.branchByNode.get(nodeKey);
        }
        SysProcessExampleNode node = context.graph.node(nodeKey);
        if (null == node) {
            return null;
        }
        for (SysProcessExampleWorkitem workitem : support.workitemsOfNode(node.getFieldId())) {
            if (StringUtil.isNotEmpty(workitemId) && !workitemId.equals(workitem.getFieldId())) {
                continue;
            }
            SysProcessExampleWorkitemConfigDo config = support.parseWorkitemConfig(workitem.getFieldConfig());
            if (null != config && StringUtil.isNotEmpty(config.getFlowBranchKey())) {
                return config.getFlowBranchKey();
            }
        }
        return null;
    }

    /**
     * 解析重新提交的流转起点：驳回后按工作项保存的 resumeMode 决定回到驳回节点（backToNode）
     * 或从草稿节点重新流转（restart / 无驳回记录）。
     *
     * @param example 流程实例（RETURN 或 DRAFT）
     * @return 重新提交的进入节点键
     */
    public String resolveResubmitEntryKey(SysProcessExample example) {
        List<SysProcessExampleNode> rejected = nodeDao.selectByExampleAndStatus(example.getFieldId(),
                SysProcessConstants.NODE_STATUS_REJECT);
        if (null != rejected && !rejected.isEmpty()) {
            SysProcessExampleNode rejectedNode = rejected.get(rejected.size() - 1);
            String resumeMode = readRejectedResumeMode(rejectedNode.getFieldId());
            if (SysProcessConstants.RESUME_MODE_BACK_TO_NODE.equals(resumeMode)) {
                return ProcessFlowGraph.nodeKey(rejectedNode);
            }
        }
        String draftKey = draftNodeKey(example);
        FlowContext context = buildContext(example);
        List<ProcessFlowLine> outgoing = context.graph.outgoing(draftKey);
        if (outgoing.isEmpty()) {
            throw ProcessFlowException.graph("草稿节点缺少出边，无法重新流转");
        }
        return outgoing.get(0).toKey();
    }

    /**
     * 管理员跳转：废弃在途工作项后从目标节点进入引擎（图模型语义不变）。
     *
     * @param example     流程实例
     * @param targetNodeId 目标节点 id（审批节点 / 起草节点 / 人工决策节点）
     * @return 流转终态
     */
    public ProcessFlowResult adminJump(SysProcessExample example, String targetNodeId) {
        FlowContext context = buildContext(example);
        SysProcessExampleNode target = context.graph.nodeById(targetNodeId);
        if (null == target) {
            throw new IllegalArgumentException("流程节点不存在");
        }
        String targetKey = ProcessFlowGraph.nodeKey(target);
        if (SysProcessConstants.NODE_TYPE_DRAFT.equals(target.getFieldNodeType())) {
            support.updateNodeStatus(target, SysProcessConstants.NODE_STATUS_PROCESSING);
            example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_RETURN);
            exampleDao.update(example);
            return ProcessFlowResult.waiting();
        }
        context.queue.addLast(new FlowTask(targetKey, null));
        return run(context, "管理员跳转后流转");
    }

    /* ==================== 流转遍历 ==================== */

    /** 装配流转上下文（加载节点与连线图 + 缓存公共配置与审批留痕） */
    private FlowContext buildContext(SysProcessExample example) {
        List<SysProcessExampleNode> nodes = nodeDao.selectListByExampleId(example.getFieldId());
        if (null == nodes || nodes.isEmpty()) {
            throw ProcessFlowException.graph("流程实例缺少节点定义: " + example.getFieldId());
        }
        FlowContext context = new FlowContext(example);
        context.graph = graphLoader.load(example, nodes);
        List<SysProcessAuditNote> notes = auditNoteDao.selectListByExampleId(example.getFieldId());
        context.auditNotes = null == notes ? List.of() : notes;
        List<SysProcessExampleWorkitem> workitems = workitemDao.selectListByExampleId(example.getFieldId());
        if (null != workitems) {
            for (SysProcessExampleWorkitem workitem : workitems) {
                if (null != workitem.getFieldId()) {
                    context.workitemCache.put(workitem.getFieldId(), workitem);
                }
            }
        }
        context.settingAutoPass = boolSetting(SysProcessConstants.SETTING_PROCESS_AUTO_HANDLE_PASS, true);
        context.settingAutoPassDrafter =
                boolSetting(SysProcessConstants.SETTING_PROCESS_AUTO_HANDLE_PASS_DRAFTER, true);
        context.settingAutoContinuePass =
                boolSetting(SysProcessConstants.SETTING_PROCESS_AUTO_CONTINUE_PASS, true);
        return context;
    }

    /** 读取布尔公共配置（缺失时返回默认值） */
    private boolean boolSetting(String key, boolean defaultValue) {
        Boolean value = settingService.getBooleanValue(SysProcessConstants.SETTING_TYPE_PROCESS, key);
        return null == value ? defaultValue : value;
    }

    /**
     * 恢复未完成的并行分支：找出仍有未到达分支的 FORK 节点，把「未到达分支的首节点键」重新入队。
     * <p>分支到达 JOIN 时该分支键写入 JOIN 运行态 {@code arrivedBranchKeys}，故
     * {@code activeBranchKeys - arrivedBranchKeys} 即待继续分支；分支内部节点已 PROCESSING 时
     * 重新进入会因「已有工作项」直接返回等待，不会重复创建工作项。</p>
     */
    private void resumePendingBranches(FlowContext context) {
        for (SysProcessExampleNode node : context.graph.nodes()) {
            if (!SysProcessConstants.NODE_TYPE_FORK.equals(node.getFieldNodeType())) {
                continue;
            }
            SysProcessExampleNodeRuntimeDo runtime = support.parseNodeRuntime(node.getFieldRuntime());
            if (null == runtime || null == runtime.getActiveBranchKeys() || runtime.getActiveBranchKeys().isEmpty()) {
                continue;
            }
            String forkKey = ProcessFlowGraph.nodeKey(node);
            Set<String> active = pendingBranchKeys(context, forkKey, runtime);
            for (String branchKey : active) {
                activateBranch(context, branchKey, findPairedJoinKey(context, forkKey));
            }
        }
    }

    /** 流转主循环：按队列顺序进入节点，直至等待、办结或转流程异常 */
    private ProcessFlowResult run(FlowContext context, String scene) {
        int guard = 0;
        try {
            while (!context.queue.isEmpty()) {
                if (++guard > MAX_FLOW_STEPS) {
                    throw ProcessFlowException.graph("流转步数超过上限 " + MAX_FLOW_STEPS + "（疑似图存在环）");
                }
                FlowTask task = context.queue.pollFirst();
                if (null == task || StringUtil.isEmpty(task.nodeKey)) {
                    continue;
                }
                // 注：不按节点键去重跳过——汇聚点/合并点可被多个分支依次进入（重复进入的节点自身幂等：
                // 审批节点已有在途工作项则不重复创建，JOIN 按到达集合累加），成环由 MAX_FLOW_STEPS 兜底
                if (!enterNode(context, task.nodeKey, task.branchKey)) {
                    if (context.finished) {
                        return ProcessFlowResult.finished();
                    }
                    // 该节点停留等待人工处理（审批工作项 / JOIN 等待汇聚）：记录停留节点，
                    // 队列中其余并行分支继续展开（不能整体中断，否则并行分支永不启动）
                    setNodeWaiting(context, task.nodeKey);
                }
                if (context.finished) {
                    return ProcessFlowResult.finished();
                }
            }
            if (context.finished) {
                return ProcessFlowResult.finished();
            }
            if (context.waitingNodeKey != null) {
                // 队列已清空但存在停留等待的节点（当前审批人工作项 / JOIN 等待汇聚）：实例保持审批中
                support.markProcessing(context.example);
                return ProcessFlowResult.waiting();
            }
            // 队列清空且无停留节点：流程无可继续流转的出口，停留审批中（可由管理员干预）
            log.warn("流程流转队列清空且无停留节点: example={}", context.example.getFieldId());
            support.markProcessing(context.example);
            return ProcessFlowResult.waiting();
        } catch (ProcessFlowException e) {
            return pauseOnError(context, e, scene);
        } catch (ProcessHandlerResolveException e) {
            return pauseOnError(context, new ProcessFlowException(ProcessFlowException.CODE_HANDLER_RESOLVE,
                    e.getMessage(), e), scene);
        } catch (RuntimeException e) {
            return pauseOnError(context, ProcessFlowException.system(
                    null == e.getMessage() ? e.getClass().getSimpleName() : e.getMessage(), e), scene);
        }
    }

    /**
     * 转流程异常：实例转 PAUSE + SYSTEM_ERROR 留痕（错误摘要入留痕），不抛出、不回滚已完成节点。
     */
    private ProcessFlowResult pauseOnError(FlowContext context, ProcessFlowException error, String scene) {
        String summary = "[" + error.getCode() + "] " + scene + "失败: " + error.getMessage();
        log.error("process flow exception: example={}, {}", context.example.getFieldId(), summary, error);
        support.addAudit(context.example.getFieldId(), null, null,
                SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR, summary);
        context.example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PAUSE);
        exampleDao.update(context.example);
        return ProcessFlowResult.paused(summary);
    }

    /**
     * 进入节点：按节点类型分发（10 类语义）。
     *
     * @return true=继续流转（已把后继节点入队）；false=停留等待人工处理或汇聚
     */
    private boolean enterNode(FlowContext context, String nodeKey, String branchKey) {
        SysProcessExampleNode node = context.graph.node(nodeKey);
        if (null == node) {
            throw ProcessFlowException.graph("连线指向不存在的节点: " + nodeKey);
        }
        String nodeType = node.getFieldNodeType();
        if (SysProcessConstants.NODE_TYPE_START.equals(nodeType)) {
            return enterStart(context, node);
        }
        if (SysProcessConstants.NODE_TYPE_DRAFT.equals(nodeType)) {
            return enterDraft(context, node);
        }
        if (SysProcessConstants.NODE_TYPE_APPROVE.equals(nodeType)) {
            return enterApprove(context, node);
        }
        if (SysProcessConstants.NODE_TYPE_END.equals(nodeType)) {
            return enterEnd(context, node);
        }
        if (SysProcessConstants.NODE_TYPE_CC.equals(nodeType)) {
            return enterCc(context, node);
        }
        if (SysProcessConstants.NODE_TYPE_ROBORT.equals(nodeType)) {
            return enterRobort(context, node);
        }
        if (SysProcessConstants.NODE_TYPE_CONDITION.equals(nodeType)) {
            return enterCondition(context, node);
        }
        if (SysProcessConstants.NODE_TYPE_FORK.equals(nodeType)) {
            return enterFork(context, node);
        }
        if (SysProcessConstants.NODE_TYPE_JOIN.equals(nodeType)) {
            return enterJoin(context, node, branchKey);
        }
        if (SysProcessConstants.NODE_TYPE_MANUAL.equals(nodeType)) {
            return enterManual(context, node);
        }
        throw ProcessFlowException.graph("节点「" + displayName(node.getFieldNodeName())
                + "」的节点类型不受支持: " + nodeType);
    }

    /** 沿节点出边继续流转（出边入队，按分支序号升序） */
    private void enqueueOutgoing(FlowContext context, String nodeKey, String branchKey) {
        List<ProcessFlowLine> outgoing = context.graph.outgoing(nodeKey);
        if (outgoing.isEmpty()) {
            SysProcessExampleNode node = context.graph.node(nodeKey);
            throw ProcessFlowException.graph("节点「" + displayName(null == node ? null : node.getFieldNodeName())
                    + "」缺少出边，流程无法继续流转");
        }
        for (ProcessFlowLine line : outgoing) {
            context.queue.addLast(new FlowTask(line.toKey(), branchKey));
        }
    }

    /** 草稿节点键（实例侧，兼容 node_key 缺失） */
    private String draftNodeKey(SysProcessExample example) {
        SysProcessExampleNode draftNode = nodeDao.selectByExampleAndType(example.getFieldId(),
                SysProcessConstants.NODE_TYPE_DRAFT);
        if (null == draftNode) {
            throw ProcessFlowException.graph("流程实例缺少草稿节点");
        }
        return ProcessFlowGraph.nodeKey(draftNode);
    }

    /** 读取驳回节点工作项中保存的「重新提交后流转方式」（驳回者工作项的 field_config） */
    private String readRejectedResumeMode(String nodeId) {
        for (SysProcessExampleWorkitem workitem : support.workitemsOfNode(nodeId)) {
            SysProcessExampleWorkitemConfigDo config = support.parseWorkitemConfig(workitem.getFieldConfig());
            if (null != config && StringUtil.isNotEmpty(config.getResumeMode())) {
                return config.getResumeMode();
            }
        }
        return null;
    }

    /* ==================== 并行汇聚（FORK / JOIN） ==================== */

    /** 待继续分支键：FORK 激活分支集合 - JOIN 已到达分支集合（JOIN 键与 FORK 键一致，单层并行一对一） */
    private Set<String> pendingBranchKeys(FlowContext context, String forkKey,
                                          SysProcessExampleNodeRuntimeDo runtime) {
        Set<String> pending = new LinkedHashSet<>(runtime.getActiveBranchKeys());
        SysProcessExampleNode joinNode = context.graph.node(forkKey);
        SysProcessExampleNodeRuntimeDo joinRuntime = null == joinNode
                ? null : support.parseNodeRuntime(joinNode.getFieldRuntime());
        if (null != joinRuntime && null != joinRuntime.getArrivedBranchKeys()) {
            pending.removeAll(joinRuntime.getArrivedBranchKeys());
        }
        return pending;
    }

    /**
     * 解析汇聚模式：优先取 JOIN 节点运行态冗余的 {@code joinMode}，其次取上游 FORK 的运行态，
     * 最后取 FORK 节点配置；均缺失时按缺省 {@code all}（全部激活分支到达后流出）。
     */
    private String resolveJoinMode(FlowContext context, String forkKey, String joinKey) {
        SysProcessExampleNode forkNode = context.graph.node(forkKey);
        SysProcessExampleNode joinNode = context.graph.node(joinKey);
        SysProcessExampleNodeRuntimeDo joinRuntime = null == joinNode
                ? null : support.parseNodeRuntime(joinNode.getFieldRuntime());
        if (null != joinRuntime && StringUtil.isNotEmpty(joinRuntime.getJoinMode())) {
            return joinRuntime.getJoinMode();
        }
        SysProcessExampleNodeRuntimeDo forkRuntime = null == forkNode
                ? null : support.parseNodeRuntime(forkNode.getFieldRuntime());
        if (null != forkRuntime && StringUtil.isNotEmpty(forkRuntime.getJoinMode())) {
            return forkRuntime.getJoinMode();
        }
        SysProcessTemplateNodeConfigDo config = null == forkNode
                ? null : support.parseNodeConfig(forkNode.getFieldNodeConfig());
        return null == config ? SysProcessConstants.JOIN_MODE_ALL : config.getJoinMode();
    }

    /**
     * 反向查找 JOIN 对应的上游 FORK：从 JOIN 沿入边向上广度遍历，首个 FORK 节点即为配对 FORK
     * （嵌套并行本期不支持，故一对一；查不到时回退 JOIN 自身键，按空激活集合处理）。
     */
    private String findUpstreamForkKey(FlowContext context, String joinKey) {
        Deque<String> pending = new ArrayDeque<>();
        Set<String> visitedKeys = new HashSet<>();
        pending.addLast(joinKey);
        visitedKeys.add(joinKey);
        while (!pending.isEmpty()) {
            String current = pending.pollFirst();
            for (ProcessFlowLine line : context.graph.incoming(current)) {
                if (!visitedKeys.add(line.fromKey())) {
                    continue;
                }
                SysProcessExampleNode from = context.graph.node(line.fromKey());
                if (null != from && SysProcessConstants.NODE_TYPE_FORK.equals(from.getFieldNodeType())) {
                    return line.fromKey();
                }
                pending.addLast(line.fromKey());
            }
        }
        return joinKey;
    }

    /**
     * 激活单个分支：把分支首节点入队，（分支成员登记：分支内部节点键 → 分支键），
     * 保证同一汇聚点可被多个分支依次进入（分支内部节点已办结时不重复执行，见
     * {@link #enterApprove} 与各节点的状态判定）。
     */
    private void activateBranch(FlowContext context, String branchKey, String joinKey) {
        context.branchByNode.put(branchKey, branchKey);
        context.queue.addLast(new FlowTask(branchKey, branchKey));
        Set<String> seen = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.addLast(branchKey);
        seen.add(branchKey);
        while (!pending.isEmpty()) {
            String current = pending.pollFirst();
            // 分支成员登记：分支内部节点（含中间节点）归属该分支，供 JOIN 到达计数回溯
            context.branchByNode.put(current, branchKey);
            for (ProcessFlowLine line : context.graph.outgoing(current)) {
                String next = line.toKey();
                if (joinKey.equals(next) || !seen.add(next)) {
                    continue;
                }
                pending.addLast(next);
            }
        }
    }

    /**
     * 定位到达 JOIN 的任务所属分支键（恢复流转场景）：在激活分支子图内正向查找到达节点，
     * 命中即返回该分支键；均未命中时返回 null（按无分支到达处理）。
     * <p>子图遍历在到达配对 JOIN 处剪枝（不跨越汇聚点），保证分支归属判定不串支。</p>
     */
    private String locateBranchKey(FlowContext context, Set<String> activeBranchKeys, String joinKey,
                                   SysProcessExampleNode arrivedNode) {
        String arrivedKey = ProcessFlowGraph.nodeKey(arrivedNode);
        for (String branchKey : activeBranchKeys) {
            Set<String> seen = new HashSet<>();
            Deque<String> pending = new ArrayDeque<>();
            pending.addLast(branchKey);
            seen.add(branchKey);
            while (!pending.isEmpty()) {
                String current = pending.pollFirst();
                if (arrivedKey.equals(current)) {
                    return branchKey;
                }
                for (ProcessFlowLine line : context.graph.outgoing(current)) {
                    if (joinKey.equals(line.toKey()) || !seen.add(line.toKey())) {
                        continue;
                    }
                    pending.addLast(line.toKey());
                }
            }
        }
        return null;
    }

    /* ==================== 节点进入（10 类） ==================== */

    /** START：直接沿唯一出边流转 */
    private boolean enterStart(FlowContext context, SysProcessExampleNode node) {
        String nodeKey = ProcessFlowGraph.nodeKey(node);
        if (!SysProcessConstants.NODE_STATUS_FINISH.equals(node.getFieldStatus())) {
            support.updateNodeStatus(node, SysProcessConstants.NODE_STATUS_FINISH);
        }
        ProcessFlowLine line = context.graph.singleOutgoing(nodeKey, node.getFieldNodeName());
        enqueueKey(context, line.toKey(), null);
        return true;
    }

    /** DRAFT：节点 PROCESSING，等待起草人提交（提交由 Service 驱动） */
    private boolean enterDraft(FlowContext context, SysProcessExampleNode node) {
        if (!SysProcessConstants.NODE_STATUS_PROCESSING.equals(node.getFieldStatus())) {
            support.updateNodeStatus(node, SysProcessConstants.NODE_STATUS_PROCESSING);
        }
        support.markProcessing(context.example);
        return false;
    }

    /** END：进入即流程办结 */
    private boolean enterEnd(FlowContext context, SysProcessExampleNode node) {
        support.updateNodeStatus(node, SysProcessConstants.NODE_STATUS_PROCESSING);
        support.markFinished(context.example);
        context.finished = true;
        return false;
    }

    /**
     * CC：解析抄送对象 → 留痕 {@code CC_AUTO} → 节点 FINISH → 沿出边继续。
     * <p>本任务只做「解析 + 留痕 + 自动办结」：抄送对象的 READ 待阅/通知发送由
     * <b>P17-BE-012 / P17-BE-014</b> 落地（见方法内 TODO）。</p>
     */
    private boolean enterCc(FlowContext context, SysProcessExampleNode node) {
        SysProcessTemplateNodeConfigDo config = support.parseNodeConfig(node.getFieldNodeConfig());
        List<String> ccIds = handlerResolver.resolveCcIds(config, node.getFieldNodeName(),
                formulaVariables(context, config));
        if (!ccIds.isEmpty()) {
            support.addAudit(context.example.getFieldId(), null, null,
                    SysProcessConstants.AUDIT_TYPE_CC_AUTO,
                    "抄送对象: " + String.join(";", ccIds));
        }
        // TODO(P17-BE-012/P17-BE-014)：按 ccIds 发送 READ 待阅（通知发送不在本任务范围内）
        support.updateNodeStatus(node, SysProcessConstants.NODE_STATUS_FINISH);
        enqueueKey(context, context.graph.singleOutgoing(ProcessFlowGraph.nodeKey(node),
                node.getFieldNodeName()).toKey(), null);
        return true;
    }

    /**
     * ROBORT：按节点配置的流程任务明细顺序执行 → 全部成功 FINISH 并继续；任一失败转流程异常。
     * <p>任务实现由 <b>P17-BE-005</b> 提供（{@link ProcessTaskExecutor}）；无实现 bean 时按
     * 「无任务则直接 FINISH」处理，保证图流转等价。</p>
     */
    private boolean enterRobort(FlowContext context, SysProcessExampleNode node) {
        SysProcessTemplateNodeConfigDo config = support.parseNodeConfig(node.getFieldNodeConfig());
        List<SysProcessTemplateNodeConfigDo.TaskDetail> tasks = null == config || null == config.getTasks()
                ? List.of() : config.getTasks();
        ProcessTaskExecutor executor = null == taskExecutorProvider
                ? null : taskExecutorProvider.getIfAvailable();
        if (!tasks.isEmpty() && null == executor) {
            log.warn("机器人节点配置了流程任务但无 ProcessTaskExecutor 实现（P17-BE-005 待交付），按无任务处理: node={}",
                    node.getFieldNodeName());
        }
        if (null != executor) {
            for (SysProcessTemplateNodeConfigDo.TaskDetail task : tasks) {
                try {
                    executor.execute(new ProcessTaskExecutor.ProcessTaskRequest(context.example.getFieldId(),
                            node.getFieldId(), node.getFieldNodeName(), task));
                } catch (ProcessFlowException e) {
                    throw e;
                } catch (RuntimeException e) {
                    throw ProcessFlowException.task("机器人节点「" + displayName(node.getFieldNodeName())
                            + "」流程任务执行失败: " + e.getMessage(), e);
                }
            }
        }
        support.updateNodeStatus(node, SysProcessConstants.NODE_STATUS_FINISH);
        enqueueKey(context, context.graph.singleOutgoing(ProcessFlowGraph.nodeKey(node),
                node.getFieldNodeName()).toKey(), null);
        return true;
    }

    /**
     * CONDITION：按 {@code field_branch_order} 升序求出边 {@code field_condition}，
     * 取<b>第一个 true</b> 的出边；无 true 走 {@code field_is_default=1} 的默认边。
     */
    private boolean enterCondition(FlowContext context, SysProcessExampleNode node) {
        String nodeKey = ProcessFlowGraph.nodeKey(node);
        List<ProcessFlowLine> outgoing = context.graph.outgoing(nodeKey);
        if (outgoing.isEmpty()) {
            throw ProcessFlowException.graph("条件分支节点「" + displayName(node.getFieldNodeName()) + "」缺少出边");
        }
        ProcessFlowLine defaultLine = null;
        for (ProcessFlowLine line : outgoing) {
            if (line.isDefaultLine()) {
                defaultLine = line;
                continue;
            }
            if (evaluateLineCondition(context, node, line)) {
                support.updateNodeStatus(node, SysProcessConstants.NODE_STATUS_FINISH);
                enqueueKey(context, line.toKey(), null);
                return true;
            }
        }
        if (null == defaultLine) {
            // 模板保存校验应保证存在默认分支；运行期缺失时按流程异常处理（不静默）
            throw ProcessFlowException.condition("条件分支节点「" + displayName(node.getFieldNodeName())
                    + "」无 true 分支且未配置默认分支", null);
        }
        support.updateNodeStatus(node, SysProcessConstants.NODE_STATUS_FINISH);
        enqueueKey(context, defaultLine.toKey(), null);
        return true;
    }

    /**
     * FORK：逐出边求值启动条件（{@code field_condition}），激活分支各自继续流转，激活集合与汇聚模式
     * 记入本节点 {@code field_runtime}；无可流转分支时按节点配置处理
     * （{@code error} → 流程异常 / {@code continue} → 全部出边激活）。
     */
    private boolean enterFork(FlowContext context, SysProcessExampleNode node) {
        String nodeKey = ProcessFlowGraph.nodeKey(node);
        List<ProcessFlowLine> outgoing = context.graph.outgoing(nodeKey);
        if (outgoing.isEmpty()) {
            throw ProcessFlowException.graph("并行开始节点「" + displayName(node.getFieldNodeName()) + "」缺少出边");
        }
        SysProcessTemplateNodeConfigDo config = support.parseNodeConfig(node.getFieldNodeConfig());
        String joinMode = null == config ? SysProcessConstants.JOIN_MODE_ALL : config.getJoinMode();
        String joinKey = findPairedJoinKey(context, nodeKey);
        List<String> activated = new ArrayList<>();
        List<String> fallback = new ArrayList<>();
        for (ProcessFlowLine line : outgoing) {
            fallback.add(line.toKey());
            if (evaluateLineCondition(context, node, line)) {
                activated.add(line.toKey());
            }
        }
        if (activated.isEmpty()) {
            String noBranchMode = null == config
                    ? SysProcessConstants.NO_BRANCH_MODE_ERROR : config.getNoBranchMode();
            if (SysProcessConstants.NO_BRANCH_MODE_CONTINUE.equals(noBranchMode)) {
                activated.addAll(fallback);
            } else {
                throw ProcessFlowException.noBranch("并行开始节点「" + displayName(node.getFieldNodeName())
                        + "」无可流转分支（节点配置为报错）");
            }
        }
        SysProcessExampleNodeRuntimeDo runtime = new SysProcessExampleNodeRuntimeDo();
        runtime.setActiveBranchKeys(new ArrayList<>(activated));
        runtime.setJoinMode(joinMode);
        support.saveNodeRuntime(node, runtime);
        support.updateNodeStatus(node, SysProcessConstants.NODE_STATUS_FINISH);
        for (String branchKey : activated) {
            activateBranch(context, branchKey, joinKey);
        }
        return true;
    }

    /**
     * JOIN：记录到达分支键（写入本节点 {@code field_runtime.arrivedBranchKeys}）；
     * 满足汇聚模式（{@code all} 全部激活分支 / {@code any} 任一）后沿唯一出边流出，否则等待。
     * <p>汇聚模式取本节点运行态冗余值（首次到达时由上游 FORK 补齐），不回溯上游配置。</p>
     */
    private boolean enterJoin(FlowContext context, SysProcessExampleNode node, String branchKey) {
        String joinKey = ProcessFlowGraph.nodeKey(node);
        SysProcessExampleNodeRuntimeDo runtime = support.parseNodeRuntime(node.getFieldRuntime());
        if (null == runtime) {
            runtime = new SysProcessExampleNodeRuntimeDo();
        }
        String forkKey = findUpstreamForkKey(context, joinKey);
        String joinMode = resolveJoinMode(context, forkKey, joinKey);
        runtime.setJoinMode(joinMode);
        SysProcessExampleNode forkNode = context.graph.node(forkKey);
        SysProcessExampleNodeRuntimeDo forkRuntime = null == forkNode
                ? null : support.parseNodeRuntime(forkNode.getFieldRuntime());
        Set<String> active = null == forkRuntime || null == forkRuntime.getActiveBranchKeys()
                ? new LinkedHashSet<>() : new LinkedHashSet<>(forkRuntime.getActiveBranchKeys());
        // 分支键：并行展开时随任务携带；恢复流转（审批通过后继续）时按激活分支子图回溯定位
        String arrivedBranch = StringUtil.isNotEmpty(branchKey) ? branchKey
                : context.branchByNode.get(ProcessFlowGraph.nodeKey(node));
        if (StringUtil.isEmpty(arrivedBranch)) {
            arrivedBranch = locateBranchKey(context, active, joinKey, node);
        }
        if (StringUtil.isNotEmpty(arrivedBranch) && !runtime.getArrivedBranchKeys().contains(arrivedBranch)) {
            runtime.getArrivedBranchKeys().add(arrivedBranch);
        }
        Set<String> arrived = new LinkedHashSet<>(runtime.getArrivedBranchKeys());
        arrived.retainAll(active);
        boolean satisfied = !active.isEmpty()
                && (SysProcessConstants.JOIN_MODE_ANY.equals(joinMode)
                ? !arrived.isEmpty() : arrived.size() >= active.size());
        if (!satisfied) {
            support.saveNodeRuntime(node, runtime);
            support.updateNodeStatus(node, SysProcessConstants.NODE_STATUS_PROCESSING);
            support.markProcessing(context.example);
            return false;
        }
        support.saveNodeRuntime(node, runtime);
        support.updateNodeStatus(node, SysProcessConstants.NODE_STATUS_FINISH);
        enqueueKey(context, context.graph.singleOutgoing(joinKey, node.getFieldNodeName()).toKey(), null);
        return true;
    }

    /**
     * MANUAL：按「最近一次人工指定流向」流出，无指定走 {@code defaultFlowKey}。
     * <p>取审批记录时间倒序最近一个带 {@code flowChoice} 的通过记录（后提交覆盖先提交，
     * D6）；候选流向须为本节点实际出边目标之一，否则回退默认流向。</p>
     */
    private boolean enterManual(FlowContext context, SysProcessExampleNode node) {
        String nodeKey = ProcessFlowGraph.nodeKey(node);
        SysProcessTemplateNodeConfigDo config = support.parseNodeConfig(node.getFieldNodeConfig());
        String choice = latestFlowChoice(context, nodeKey);
        if (StringUtil.isEmpty(choice) && null != config) {
            choice = config.getDefaultFlowKey();
        }
        if (StringUtil.isEmpty(choice)) {
            throw ProcessFlowException.graph("人工决策节点「" + displayName(node.getFieldNodeName())
                    + "」无人工指定流向且未配置默认流向");
        }
        ProcessFlowLine target = null;
        for (ProcessFlowLine line : context.graph.outgoing(nodeKey)) {
            if (choice.equals(line.toKey())) {
                target = line;
                break;
            }
        }
        if (null == target) {
            throw ProcessFlowException.graph("人工决策节点「" + displayName(node.getFieldNodeName())
                    + "」的流向目标 " + choice + " 不是本节点出边目标");
        }
        support.updateNodeStatus(node, SysProcessConstants.NODE_STATUS_FINISH);
        enqueueKey(context, target.toKey(), null);
        return true;
    }

    /**
     * 取最近一次人工指定流向：审批记录时间倒序，首个「通过（HANDLER_PASS）+ 关联工作项带 flowChoice」
     * 的记录的 flowChoice；无则返回 null（走默认流向）。
     */
    private String latestFlowChoice(FlowContext context, String nodeKey) {
        List<SysProcessAuditNote> notes = new ArrayList<>(context.auditNotes);
        notes.sort((a, b) -> {
            java.util.Date left = a.getFieldCreateTime();
            java.util.Date right = b.getFieldCreateTime();
            if (null == left || null == right) {
                return 0;
            }
            return right.compareTo(left);
        });
        for (SysProcessAuditNote note : notes) {
            if (!SysProcessConstants.AUDIT_TYPE_HANDLER_PASS.equals(note.getFieldHandleType())
                    || StringUtil.isEmpty(note.getFieldWorkitemId())) {
                continue;
            }
            SysProcessExampleWorkitem workitem = context.workitemById(note.getFieldWorkitemId());
            if (null == workitem) {
                continue;
            }
            SysProcessExampleWorkitemConfigDo config = support.parseWorkitemConfig(workitem.getFieldConfig());
            if (null != config && StringUtil.isNotEmpty(config.getFlowChoice())) {
                return config.getFlowChoice().trim();
            }
        }
        return null;
    }

    /**
     * 求值单条连线的分支条件：条件公式为空视为「无条件成立」（NORMAL / MANUAL 连线与
     * 未配置启动条件的并行分支）。
     */
    private boolean evaluateLineCondition(FlowContext context, SysProcessExampleNode node, ProcessFlowLine line) {
        if (StringUtil.isEmpty(line.condition())) {
            return true;
        }
        Object result;
        try {
            result = formulaEngine.execute(line.condition().trim(), new FormulaContext(formulaVariables(context,
                    support.parseNodeConfig(node.getFieldNodeConfig()))), null);
        } catch (RuntimeException e) {
            throw ProcessFlowException.condition("节点「" + displayName(node.getFieldNodeName())
                    + "」分支条件执行失败: " + e.getMessage(), e);
        }
        if (result instanceof Boolean value) {
            return value;
        }
        throw ProcessFlowException.condition("节点「" + displayName(node.getFieldNodeName())
                + "」分支条件结果不是布尔值: " + (null == result ? "null" : result.getClass().getSimpleName()), null);
    }

    /** 查找 FORK 配对的 JOIN 键：沿出边向下广度遍历，首个 JOIN 节点即为配对 JOIN（单层并行一对一） */
    private String findPairedJoinKey(FlowContext context, String forkKey) {
        Deque<String> pending = new ArrayDeque<>();
        Set<String> visitedKeys = new HashSet<>();
        for (ProcessFlowLine line : context.graph.outgoing(forkKey)) {
            pending.addLast(line.toKey());
        }
        while (!pending.isEmpty()) {
            String current = pending.pollFirst();
            if (!visitedKeys.add(current)) {
                continue;
            }
            SysProcessExampleNode node = context.graph.node(current);
            if (null != node && SysProcessConstants.NODE_TYPE_JOIN.equals(node.getFieldNodeType())) {
                return current;
            }
            for (ProcessFlowLine line : context.graph.outgoing(current)) {
                pending.addLast(line.toKey());
            }
        }
        throw ProcessFlowException.graph("并行开始节点缺少配对的并行结束节点: " + forkKey);
    }

    private static String displayName(String nodeName) {
        return StringUtil.isEmpty(nodeName) ? "未命名节点" : nodeName.trim();
    }

    /**
     * APPROVE：解析审批人 → 单签/会签/串行 → 生成工作项；无可实际审批的工作项（自动跳过／空审批人）
     * 则标记 PASS 并沿唯一出边继续。
     *
     * @return true=继续流转；false=停留等待审批
     */
    private boolean enterApprove(FlowContext context, SysProcessExampleNode node) {
        // 已在审批中的节点（恢复并行分支/重复入队）：不再重复创建工作项
        if (hasActiveWorkitem(node.getFieldId())
                && SysProcessConstants.NODE_STATUS_PROCESSING.equals(node.getFieldStatus())) {
            support.markProcessing(context.example);
            return false;
        }
        SysProcessTemplateNodeConfigDo config = support.parseNodeConfig(node.getFieldNodeConfig());
        return createApproveWorkitems(context, node, config);
    }

    /** 按审批模式（单签/会签/串行）创建工作项并落节点状态 */
    private boolean createApproveWorkitems(FlowContext context, SysProcessExampleNode node,
                                           SysProcessTemplateNodeConfigDo config) {
        String nodeKey = ProcessFlowGraph.nodeKey(node);
        // 审批人顺序解析结果同时用于「是否需创建审批工作项」判定与工作项生成（一次解析、一次比对）
        List<String> ordered = resolveHandlerOrder(context, config, node);
        if (ordered.isEmpty()) {
            support.updateNodeStatus(node, SysProcessConstants.NODE_STATUS_PASS);
            enqueueKey(context, context.graph.singleOutgoing(nodeKey, node.getFieldNodeName()).toKey(), null);
            return true;
        }
        Set<String> skipped = computeSkippedHandlers(context, config, new LinkedHashSet<>(ordered), nodeKey);
        String approveMode = approveMode(config);
        if (SysProcessConstants.APPROVE_MODE_SERIAL.equals(approveMode)) {
            return enterSerialApprove(context, node, ordered, skipped);
        }
        int processing = 0;
        for (String handlerId : ordered) {
            boolean skip = skipped.contains(handlerId);
            insertApproveWorkitem(context, node, handlerId,
                    skip ? SysProcessConstants.WORKITEM_STATUS_FINISH
                            : SysProcessConstants.WORKITEM_STATUS_PROCESSING);
            if (skip) {
                addAutoPassAudit(context, node, handlerId);
            } else {
                processing++;
            }
        }
        if (processing > 0) {
            support.updateNodeStatus(node, SysProcessConstants.NODE_STATUS_PROCESSING);
            support.markProcessing(context.example);
            return false;
        }
        support.updateNodeStatus(node, SysProcessConstants.NODE_STATUS_PASS);
        enqueueKey(context, context.graph.singleOutgoing(nodeKey, node.getFieldNodeName()).toKey(), null);
        return true;
    }

    /**
     * 串行审批（approveMode=serial）：仅生成首个「需实际审批」处理人的工作项，其余按序等待前一位通过后生成；
     * 被自动跳过的处理人不占串行序位。
     */
    private boolean enterSerialApprove(FlowContext context, SysProcessExampleNode node,
                                       List<String> ordered, Set<String> skipped) {
        String firstHandler = null;
        for (String handlerId : ordered) {
            if (skipped.contains(handlerId)) {
                insertApproveWorkitem(context, node, handlerId, SysProcessConstants.WORKITEM_STATUS_FINISH);
                addAutoPassAudit(context, node, handlerId);
                continue;
            }
            firstHandler = handlerId;
            break;
        }
        if (null == firstHandler) {
            // 全部处理人被自动跳过：节点跳过并继续
            support.updateNodeStatus(node, SysProcessConstants.NODE_STATUS_PASS);
            enqueueKey(context, context.graph.singleOutgoing(ProcessFlowGraph.nodeKey(node),
                    node.getFieldNodeName()).toKey(), null);
            return true;
        }
        insertApproveWorkitem(context, node, firstHandler, SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        support.updateNodeStatus(node, SysProcessConstants.NODE_STATUS_PROCESSING);
        support.markProcessing(context.example);
        return false;
    }

    /**
     * 创建审批工作项：并行分支内节点在工作项配置中记录所属分支键（{@code flowBranchKey}），
     * 供审批通过后跨调用恢复流转时回溯分支归属。
     */
    private void insertApproveWorkitem(FlowContext context, SysProcessExampleNode node,
                                       String handlerId, String status) {
        String branchKey = context.branchByNode.get(ProcessFlowGraph.nodeKey(node));
        SysProcessExampleWorkitemConfigDo config = null;
        if (StringUtil.isNotEmpty(branchKey)) {
            config = new SysProcessExampleWorkitemConfigDo();
            config.setFlowBranchKey(branchKey);
        }
        support.insertWorkitem(context.example, node, handlerId, status,
                null == config ? null : support.writeJson(config));
    }

    /**
     * 审批节点通过后的推进（由 {@link #pass} 调用，进入流转前执行）：
     * <ul>
     * <li>会签：仍有未完成工作项时返回 false（节点保持审批中）；</li>
     * <li>串行：按节点配置的审批人顺序生成下一位处理人的工作项，返回 false；</li>
     * <li>单签／串行队列已走完：节点置 FINISH，返回 true。</li>
     * </ul>
     *
     * @return true=节点已通过可继续流转；false=节点仍在审批中
     */
    private boolean advanceApprove(FlowContext context, String nodeKey, String nodeName) {
        SysProcessExampleNode node = context.graph.node(nodeKey);
        if (null == node) {
            return true;
        }
        SysProcessTemplateNodeConfigDo config = support.parseNodeConfig(node.getFieldNodeConfig());
        String approveMode = approveMode(config);
        List<SysProcessExampleWorkitem> workitems = support.workitemsOfNode(node.getFieldId());
        if (SysProcessConstants.APPROVE_MODE_SERIAL.equals(approveMode)) {
            if (!SysProcessConstants.NODE_STATUS_PROCESSING.equals(node.getFieldStatus())) {
                // 串行队列已走完（节点已置 FINISH）：可继续流出
                return true;
            }
            // 生成下一位处理人工作项；队列已空则节点置 FINISH 并允许流出
            return createNextSerialWorkitem(context, node, config,
                    resolveHandlerOrder(context, config, node));
        }
        if (SysProcessConstants.APPROVE_MODE_COUNTERSIGN.equals(approveMode)
                && SysProcessConstants.NODE_STATUS_PROCESSING.equals(node.getFieldStatus())
                && !allWorkitemsFinished(workitems)) {
            return false;
        }
        support.updateNodeStatus(node, SysProcessConstants.NODE_STATUS_FINISH);
        return true;
    }

    /**
     * 串行模式下生成下一位「需实际审批」处理人的工作项。
     *
     * @return true=队列已走完（节点置 FINISH，可继续流出）；false=已生成下一位处理人工作项（等待审批）
     */
    private boolean createNextSerialWorkitem(FlowContext context, SysProcessExampleNode node,
                                             SysProcessTemplateNodeConfigDo config,
                                             List<String> orderedHandlers) {
        Set<String> skipped = computeSkippedHandlers(context, config, new LinkedHashSet<>(orderedHandlers),
                ProcessFlowGraph.nodeKey(node));
        Set<String> issued = new HashSet<>();
        for (SysProcessExampleWorkitem workitem : support.workitemsOfNode(node.getFieldId())) {
            if (StringUtil.isNotEmpty(workitem.getFieldExpectHandlerId())) {
                issued.add(workitem.getFieldExpectHandlerId());
            } else if (StringUtil.isNotEmpty(workitem.getFieldHandlerId())) {
                issued.add(workitem.getFieldHandlerId());
            }
        }
        for (String handlerId : orderedHandlers) {
            // 已生成过工作项的处理人（含已通过者）不重复生成，串行序位继续向后推进
            if (issued.contains(handlerId)) {
                continue;
            }
            if (skipped.contains(handlerId)) {
                insertApproveWorkitem(context, node, handlerId, SysProcessConstants.WORKITEM_STATUS_FINISH);
                addAutoPassAudit(context, node, handlerId);
                continue;
            }
            insertApproveWorkitem(context, node, handlerId, SysProcessConstants.WORKITEM_STATUS_PROCESSING);
            return false;
        }
        support.updateNodeStatus(node, SysProcessConstants.NODE_STATUS_FINISH);
        return true;
    }

    /** 节点全部工作项是否均已完成 */
    private static boolean allWorkitemsFinished(List<SysProcessExampleWorkitem> workitems) {
        if (null == workitems || workitems.isEmpty()) {
            return true;
        }
        for (SysProcessExampleWorkitem workitem : workitems) {
            if (!SysProcessConstants.WORKITEM_STATUS_FINISH.equals(workitem.getFieldStatus())) {
                return false;
            }
        }
        return true;
    }

    /** 节点是否存在未完成（PROCESSING / PAUSE）工作项 */
    private boolean hasActiveWorkitem(String nodeId) {
        for (SysProcessExampleWorkitem workitem : support.workitemsOfNode(nodeId)) {
            if (!SysProcessConstants.WORKITEM_STATUS_FINISH.equals(workitem.getFieldStatus())
                    && !SysProcessConstants.WORKITEM_STATUS_ABANDON.equals(workitem.getFieldStatus())) {
                return true;
            }
        }
        return false;
    }

    /** 审批模式归一：serial / countersign / single（缺省 single） */
    private static String approveMode(SysProcessTemplateNodeConfigDo config) {
        if (null == config || StringUtil.isEmpty(config.getApproveMode())) {
            return SysProcessConstants.APPROVE_MODE_SINGLE;
        }
        String mode = config.getApproveMode().trim();
        if (SysProcessConstants.APPROVE_MODE_SERIAL.equals(mode)
                || SysProcessConstants.APPROVE_MODE_COUNTERSIGN.equals(mode)) {
            return mode;
        }
        return SysProcessConstants.APPROVE_MODE_SINGLE;
    }

    /** 自动跳过留痕（跳过者工作项已置 FINISH） */
    private void addAutoPassAudit(FlowContext context, SysProcessExampleNode node, String handlerId) {
        support.addAudit(context.example.getFieldId(), null, handlerId,
                SysProcessConstants.AUDIT_TYPE_AUTO_PASS, "自动跳过重复审批人");
    }

    /* ==================== 审批人与跳过解析 ==================== */

    /** 审批人顺序解析（去重保序）：地址本全类型 + 公式源，失败转流程异常 */
    private List<String> resolveHandlerOrder(FlowContext context, SysProcessTemplateNodeConfigDo config,
                                             SysProcessExampleNode node) {
        if (null == config) {
            return List.of();
        }
        return handlerResolver.resolveHandlerIds(config, node.getFieldNodeName(),
                formulaVariables(context, config));
    }

    /**
     * 公式变量表：审批人/抄送对象公式（handlerSource=formula / ccSource=formula）的 {@code ${field}}
     * 占位符取值来源。当前仅提供文档实体审批人字段值（存量 field 源语义），
     * 完整字段投影由接入模块按需补充（P17-BE-002 只读本表取值）。
     */
    private java.util.Map<String, Object> formulaVariables(FlowContext context,
                                                           SysProcessTemplateNodeConfigDo config) {
        java.util.Map<String, Object> variables = new java.util.HashMap<>();
        if (null == config || StringUtil.isEmpty(config.getHandlerField())) {
            return variables;
        }
        String value = documentFieldService.resolveFieldValue(context.example.getFieldModelName(),
                context.example.getFieldModelId(), config.getHandlerField());
        if (StringUtil.isNotEmpty(value)) {
            variables.put(config.getHandlerField().trim(), value);
        }
        return variables;
    }

    /**
     * 计算本节点的自动跳过处理人集合（沿用存量语义，依据流程核心业务.md「流程流转公共规范」）：
     * 上一有效节点审批人（及其后起草人）为重复审批人；是否跳过由公共配置 auto_handle_pass 与
     * 节点配置 autoSkip 共同决定，连续跳过由 autoContinueSkip 决定。
     */
    private Set<String> computeSkippedHandlers(FlowContext context, SysProcessTemplateNodeConfigDo config,
                                               Set<String> handlers, String nodeKey) {
        Set<String> skipped = new LinkedHashSet<>();
        PreviousNode prev = findPreviousEffectiveNode(context, nodeKey);
        String autoSkip = null == config || StringUtil.isEmpty(config.getAutoSkip())
                ? SysProcessConstants.AUTO_SKIP_SYSTEM : config.getAutoSkip();
        String autoContinueSkip = null == config || StringUtil.isEmpty(config.getAutoContinueSkip())
                ? SysProcessConstants.AUTO_CONTINUE_SKIP_SYSTEM : config.getAutoContinueSkip();
        boolean nodeAutoPass = SysProcessConstants.AUTO_SKIP_YES.equals(autoSkip)
                || (SysProcessConstants.AUTO_SKIP_SYSTEM.equals(autoSkip) && context.settingAutoPass);
        if (!nodeAutoPass) {
            return skipped;
        }
        boolean nodeContinue = SysProcessConstants.AUTO_CONTINUE_SKIP_YES.equals(autoContinueSkip)
                || (SysProcessConstants.AUTO_CONTINUE_SKIP_SYSTEM.equals(autoContinueSkip)
                && context.settingAutoContinuePass);
        Set<String> duplicate = new HashSet<>(prev.handlers);
        if (context.settingAutoPassDrafter) {
            duplicate.add(context.example.getFieldCreatorId());
        }
        for (String handlerId : handlers) {
            if (!duplicate.contains(handlerId)) {
                continue;
            }
            if (prev.skipped.contains(handlerId) && !nodeContinue) {
                continue;
            }
            skipped.add(handlerId);
        }
        return skipped;
    }

    /**
     * 查找指定节点在图上的最近「有效节点」（编号小者优先）：终态为 FINISH / REJECT 的审批节点；
     * 自动跳过（PASS）节点不作为有效节点。返回其审批人集合与被自动跳过的处理人集合。
     */
    private PreviousNode findPreviousEffectiveNode(FlowContext context, String nodeKey) {
        PreviousNode prev = new PreviousNode();
        SysProcessExampleNode effective = null;
        for (SysProcessExampleNode node : previousApproveCandidates(context, nodeKey)) {
            if (SysProcessConstants.NODE_TYPE_APPROVE.equals(node.getFieldNodeType())
                    && (SysProcessConstants.NODE_STATUS_FINISH.equals(node.getFieldStatus())
                    || SysProcessConstants.NODE_STATUS_REJECT.equals(node.getFieldStatus()))) {
                effective = node;
                break;
            }
        }
        if (null == effective) {
            return prev;
        }
        Set<String> autoPassWorkitemIds = autoPassWorkitemIds(context);
        for (SysProcessExampleWorkitem workitem : support.workitemsOfNode(effective.getFieldId())) {
            if (StringUtil.isNotEmpty(workitem.getFieldExpectHandlerId())) {
                prev.handlers.add(workitem.getFieldExpectHandlerId());
            } else if (StringUtil.isNotEmpty(workitem.getFieldHandlerId())) {
                prev.handlers.add(workitem.getFieldHandlerId());
            }
            if (autoPassWorkitemIds.contains(workitem.getFieldId())) {
                prev.skipped.add(workitem.getFieldHandlerId());
            }
        }
        return prev;
    }

    /** 图上「当前节点的上游审批节点」候选（按节点编号降序，即由近及远），用于取上一有效节点 */
    private List<SysProcessExampleNode> previousApproveCandidates(FlowContext context, String nodeKey) {
        List<SysProcessExampleNode> candidates = new ArrayList<>();
        SysProcessExampleNode current = context.graph.node(nodeKey);
        if (null == current || null == current.getFieldNodeNo()) {
            return candidates;
        }
        int currentNo = current.getFieldNodeNo();
        for (SysProcessExampleNode node : context.graph.nodes()) {
            if (null == node.getFieldNodeNo() || node.getFieldNodeNo() >= currentNo) {
                continue;
            }
            if (SysProcessConstants.NODE_TYPE_APPROVE.equals(node.getFieldNodeType())) {
                candidates.add(node);
            }
        }
        candidates.sort((a, b) -> Integer.compare(b.getFieldNodeNo(), a.getFieldNodeNo()));
        return candidates;
    }

    /** 收集实例内 AUTO_PASS 留痕对应的工作项 id 集合（判定「被自动跳过」的处理人） */
    private Set<String> autoPassWorkitemIds(FlowContext context) {
        Set<String> ids = new HashSet<>();
        for (var note : context.autoPassNotes()) {
            if (StringUtil.isNotEmpty(note.getFieldWorkitemId())) {
                ids.add(note.getFieldWorkitemId());
            }
        }
        return ids;
    }

    /** 上一有效节点信息：审批人集合与被自动跳过的处理人集合 */
    private static final class PreviousNode {

        private final Set<String> handlers = new HashSet<>();

        private final Set<String> skipped = new HashSet<>();
    }

    /** 单条后继入队 */
    private void enqueueKey(FlowContext context, String nodeKey, String branchKey) {
        if (StringUtil.isEmpty(nodeKey)) {
            throw ProcessFlowException.graph("连线缺少目标节点键");
        }
        context.queue.addLast(new FlowTask(nodeKey, branchKey));
    }

    /** 标记当前停留等待人工处理的节点（审批中 / JOIN 等待汇聚） */
    private void setNodeWaiting(FlowContext context, String nodeKey) {
        if (null == context.waitingNodeKey) {
            context.waitingNodeKey = nodeKey;
        }
    }

    /* ==================== 流转上下文 ==================== */

    /** 流转任务：进入节点键 + 所属并行分支键（非并行分支为 null） */
    private record FlowTask(String nodeKey, String branchKey) {
    }

    /** 单次流转的上下文（每次进入流转新建，跨调用状态从实例节点运行态重建） */
    private static final class FlowContext {

        private final SysProcessExample example;

        private ProcessFlowGraph graph;

        private List<SysProcessAuditNote> auditNotes = List.of();

        private final Deque<FlowTask> queue = new ArrayDeque<>();

        /** 分支成员索引：并行分支内节点键 → 分支键（JOIN 到达计数回溯用） */
        private final java.util.Map<String, String> branchByNode = new java.util.HashMap<>();
        private boolean finished;

        /** 停留等待人工处理的节点键（审批中 / JOIN 等待汇聚），为 null 表示无停留节点 */
        private String waitingNodeKey;

        private boolean settingAutoPass;

        private boolean settingAutoPassDrafter;

        private boolean settingAutoContinuePass;

        private FlowContext(SysProcessExample example) {
            this.example = example;
        }

        /** 实例内 AUTO_PASS 留痕集合 */
        private List<SysProcessAuditNote> autoPassNotes() {
            List<SysProcessAuditNote> notes = new ArrayList<>();
            for (SysProcessAuditNote note : auditNotes) {
                if (SysProcessConstants.AUDIT_TYPE_AUTO_PASS.equals(note.getFieldHandleType())) {
                    notes.add(note);
                }
            }
            return notes;
        }

        /** 按工作项 id 取工作项（实例级缓存，供人工决策流向回溯） */
        private SysProcessExampleWorkitem workitemById(String workitemId) {
            return StringUtil.isEmpty(workitemId) ? null : workitemCache.get(workitemId);
        }

        private final java.util.Map<String, SysProcessExampleWorkitem> workitemCache = new java.util.HashMap<>();
    }
}




