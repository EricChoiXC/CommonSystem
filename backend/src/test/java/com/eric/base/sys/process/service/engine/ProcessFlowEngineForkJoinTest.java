package com.eric.base.sys.process.service.engine;

import com.eric.base.sys.process.model.SysProcessExampleNodeRuntimeDo;
import com.eric.base.sys.process.properties.SysProcessConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_APPROVE;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_END;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_FORK;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_JOIN;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_START;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_PROCESSING;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_WAITING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 流程流转引擎单测（三）：FORK/JOIN 并行汇聚、实例连线快照与流程异常
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-003 交付物、P17-BE-007 收敛：覆盖 §3.4 第 2 条并行汇聚（field_runtime
 *              记录激活分支集合与到达进度，all/any 汇聚模式）、实例连线快照为流转唯一依据
 *              （引擎只读实例连线，缺失即流程异常）、流程异常（公式解析失败/无可流转分支 →
 *              实例 PAUSE + SYSTEM_ERROR，已完成节点不回滚）。
 */
class ProcessFlowEngineForkJoinTest {

    private FlowEngineFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new FlowEngineFixture();
    }

    /** 并行结构：FORK(N3) → 分支N4 / 分支N5 → JOIN(N6) → END(N7) */
    private void buildParallel(String joinMode, String noBranchMode) {
        fixture.node(3, "并行开始", NODE_TYPE_FORK, FlowEngineFixture.forkConfig(joinMode, noBranchMode));
        fixture.node(4, "分支A审批", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U1));
        fixture.node(5, "分支B审批", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U2));
        fixture.node(6, "并行结束", NODE_TYPE_JOIN);
        fixture.node(7, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N4", SysProcessConstants.LINE_TYPE_PARALLEL, "branch == 'A'", false, 1);
        fixture.line("N3", "N5", SysProcessConstants.LINE_TYPE_PARALLEL, "branch == 'B'", false, 2);
        fixture.line("N4", "N6");
        fixture.line("N5", "N6");
        fixture.line("N6", "N7");
    }

    /* ==================== FORK 激活与分支流转 ==================== */

    @Test
    @DisplayName("FORK 逐出边求值启动条件：仅激活条件为 true 的分支，汇聚上下文记入 field_runtime")
    void forkActivatesMatchingBranchesOnly() throws Exception {
        buildParallel(SysProcessConstants.JOIN_MODE_ALL, SysProcessConstants.NO_BRANCH_MODE_ERROR);
        when(fixture.formulaEngine.execute(any(), any(), any())).thenAnswer(invocation ->
                "branch == 'A'".equals(invocation.getArgument(0)));

        fixture.engine(null).enterFlow(fixture.example, "N3");

        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N3"));
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N4"), "条件为 true 的分支被激活");
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N5"), "条件为 false 的分支不激活");
        SysProcessExampleNodeRuntimeDo runtime = fixture.runtimeOf("N3");
        assertEquals(List.of("N4"), runtime.getActiveBranchKeys());
        assertEquals(SysProcessConstants.JOIN_MODE_ALL, runtime.getJoinMode());
    }

    @Test
    @DisplayName("JOIN 全部汇聚：两条分支均到达后流出")
    void joinAllWaitsUntilEveryBranchArrives() throws Exception {
        buildParallel(SysProcessConstants.JOIN_MODE_ALL, SysProcessConstants.NO_BRANCH_MODE_ERROR);
        when(fixture.formulaEngine.execute(any(), any(), any())).thenReturn(Boolean.TRUE);
        ProcessFlowResult enterResult = fixture.engine(null).enterFlow(fixture.example, "N3");
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N4"));
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N5"));

        // 分支A 到达 JOIN：仅记录到达，节点等待另一分支
        ProcessFlowResult firstArrival = fixture.passNode("N4");
        assertTrue(firstArrival.isWaiting());
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N6"), "未满足汇聚条件时 JOIN 等待");
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PROCESSING, fixture.example.getFieldStatus());
        assertEquals(List.of("N4"), fixture.runtimeOf("N6").getArrivedBranchKeys());

        // 分支B 到达 JOIN：全部激活分支到达后流出到结束节点
        ProcessFlowResult secondArrival = fixture.passNode("N5");

        assertTrue(secondArrival.isFinished());
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N6"));
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_FINISH, fixture.example.getFieldStatus());
        SysProcessExampleNodeRuntimeDo joinRuntime = fixture.runtimeOf("N6");
        assertTrue(joinRuntime.getArrivedBranchKeys().containsAll(List.of("N4", "N5")));
    }

    @Test
    @DisplayName("JOIN 任一汇聚：任一激活分支到达即流出")
    void joinAnyFlowsOnFirstArrival() {
        buildParallel(SysProcessConstants.JOIN_MODE_ANY, SysProcessConstants.NO_BRANCH_MODE_ERROR);
        when(fixture.formulaEngine.execute(any(), any(), any())).thenReturn(Boolean.TRUE);
        fixture.engine(null).enterFlow(fixture.example, "N3");
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N4"));
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N5"));

        ProcessFlowResult result = fixture.passNode("N4");

        assertTrue(result.isFinished(), "any 模式下首个分支到达即满足汇聚条件");
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N6"));
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_FINISH, fixture.example.getFieldStatus());
    }

    @Test
    @DisplayName("并行分支末端为无工作项节点（抄送/机器人）时分支键随流出传递，JOIN 正常汇聚（P17-INT-003 修复回归）")
    void joinConvergesWhenBranchEndsWithWorkitemLessNodes() throws Exception {
        // FORK(N3) → 分支A: CC(N4) → JOIN(N6)；分支B: ROBORT(N5) → JOIN(N6) → END(N7)
        // 修复前：CC/ROBORT 流出时分支键丢失（enqueue 分支键为 null），JOIN 到达计数回溯不到
        // 归属分支（locateBranchKey 子图遍历在 JOIN 处剪枝），arrivedBranchKeys 恒空、流程卡在汇聚等待
        fixture.node(3, "并行开始", NODE_TYPE_FORK,
                FlowEngineFixture.forkConfig(SysProcessConstants.JOIN_MODE_ALL, SysProcessConstants.NO_BRANCH_MODE_ERROR));
        fixture.node(4, "抄送通知", SysProcessConstants.NODE_TYPE_CC,
                "{\"ccSource\":\"org\",\"ccIds\":\"" + FlowEngineFixture.U1 + "\"}");
        fixture.node(5, "机器人节点", SysProcessConstants.NODE_TYPE_ROBORT);
        fixture.node(6, "并行结束", NODE_TYPE_JOIN);
        fixture.node(7, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N4", SysProcessConstants.LINE_TYPE_PARALLEL, "", false, 1);
        fixture.line("N3", "N5", SysProcessConstants.LINE_TYPE_PARALLEL, "", false, 2);
        fixture.line("N4", "N6");
        fixture.line("N5", "N6");
        fixture.line("N6", "N7");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isFinished(), "两分支末端均为无工作项节点时应在同一流转内汇聚流出");
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N4"));
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N5"));
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N6"));
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_FINISH, fixture.example.getFieldStatus());
        assertTrue(fixture.runtimeOf("N6").getArrivedBranchKeys().containsAll(List.of("N4", "N5")),
                "JOIN 到达计数按分支键记录");
    }

    @Test
    @DisplayName("FORK 无可流转分支且配置为报错：转流程异常 PAUSE + SYSTEM_ERROR")
    void forkWithoutBranchPausesWhenConfiguredAsError() {
        buildParallel(SysProcessConstants.JOIN_MODE_ALL, SysProcessConstants.NO_BRANCH_MODE_ERROR);
        when(fixture.formulaEngine.execute(any(), any(), any())).thenReturn(Boolean.FALSE);

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isPaused());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PAUSE, fixture.example.getFieldStatus());
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR));
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N4"));
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N5"));
    }

    @Test
    @DisplayName("FORK 无可流转分支且配置为继续：全部出边激活后流转")
    void forkWithoutBranchActivatesAllWhenConfiguredAsContinue() {
        buildParallel(SysProcessConstants.JOIN_MODE_ALL, SysProcessConstants.NO_BRANCH_MODE_CONTINUE);
        when(fixture.formulaEngine.execute(any(), any(), any())).thenReturn(Boolean.FALSE);

        fixture.engine(null).enterFlow(fixture.example, "N3");

        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N4"), "继续模式激活全部出边");
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N5"));
    }

    /* ==================== 实例连线快照为流转唯一依据（P17-BE-007 移除模板连线回退） ==================== */

    @Test
    @DisplayName("实例无连线快照时抛流程异常（不回退模板连线）")
    void loadGraphFailsWhenInstanceHasNoLineSnapshot() {
        fixture.node(1, "开始节点", NODE_TYPE_START);
        fixture.node(2, "草稿节点", NODE_TYPE_START);

        ProcessFlowGraphLoader loader = new ProcessFlowGraphLoader(fixture.exampleLineDao);

        ProcessFlowException error = assertThrows(ProcessFlowException.class,
                () -> loader.load(fixture.example, fixture.nodes));
        assertEquals(ProcessFlowException.CODE_GRAPH, error.getCode());
        assertEquals("流程实例缺少连线定义: " + FlowEngineFixture.EXAMPLE_ID, error.getMessage());
    }

    @Test
    @DisplayName("实例连线快照缺失：进入流转转流程异常 PAUSE + SYSTEM_ERROR")
    void enterFlowPausesWhenInstanceHasNoLineSnapshot() {
        fixture.node(1, "开始节点", NODE_TYPE_START);
        fixture.node(2, "草稿节点", NODE_TYPE_START);

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N1");

        assertTrue(result.isPaused());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PAUSE, fixture.example.getFieldStatus());
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR));
        assertTrue(result.getErrorSummary().contains("流程实例缺少连线定义"));
    }

    @Test
    @DisplayName("引擎按实例连线快照流转：模板连线不同也不影响")
    void engineFlowsByInstanceLineSnapshotOnly() {
        fixture.node(1, "开始节点", NODE_TYPE_START);
        fixture.node(2, "草稿节点", NODE_TYPE_START);
        fixture.node(3, "结束节点", NODE_TYPE_END);
        // 实例连线快照：N1→N3；模板侧若被读取将走 N1→N2（实例快照与模板不一致）
        fixture.line("N1", "N3");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N1");

        assertTrue(result.isFinished());
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N1"));
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N2"), "模板连线路径上的节点不应被进入");
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N3"));
    }

    /* ==================== 流程异常与不回滚 ==================== */

    @Test
    @DisplayName("审批人解析失败：转流程异常 PAUSE + SYSTEM_ERROR，已完成节点不回滚")
    void handlerResolveFailurePausesWithoutRollback() {
        fixture.node(1, "开始节点", NODE_TYPE_START);
        fixture.node(3, "审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U1));
        fixture.line("N1", "N3");
        when(fixture.handlerResolver.resolveHandlerIds(any(), any(), any()))
                .thenThrow(ProcessHandlerResolveException.resolveFailed("审批人对象不存在: dept-x"));

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N1");

        assertTrue(result.isPaused());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PAUSE, fixture.example.getFieldStatus());
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR));
        assertTrue(result.getErrorSummary().contains("审批人对象不存在"));
        // 已完成节点（START）状态与留痕保持不变
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N1"));
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N3"));
    }

    @Test
    @DisplayName("无审批人且配置不跳过：转流程异常 PAUSE + SYSTEM_ERROR")
    void emptyHandlerWithErrorModePauses() {
        fixture.node(3, "审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U1));
        fixture.line("N3", "N3");
        when(fixture.handlerResolver.resolveHandlerIds(any(), any(), any()))
                .thenThrow(ProcessHandlerResolveException.emptyHandler("节点未解析出任何审批人"));

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isPaused());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PAUSE, fixture.example.getFieldStatus());
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR));
    }
}


