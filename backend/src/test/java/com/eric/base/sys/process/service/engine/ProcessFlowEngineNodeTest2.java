package com.eric.base.sys.process.service.engine;

import com.eric.base.sys.notify.enums.SysNotifyType;
import com.eric.base.sys.notify.model.SysNotifyTodo;
import com.eric.base.sys.process.model.SysProcessExampleNode;
import com.eric.base.sys.process.model.SysProcessTemplate;
import com.eric.base.sys.process.properties.SysProcessConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_APPROVE;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_CC;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_CONDITION;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_END;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_MANUAL;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_ROBORT;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_PROCESSING;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_WAITING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程流转引擎单测（二）：CC / ROBORT / CONDITION / MANUAL 节点语义
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-003 交付物：覆盖 §3.3 节点语义——抄送节点解析抄送对象逐人发 READ 待阅、
 *              留痕并自动办结（空抄送对象按跳过处理并留痕、公式源解析失败转流程异常，P17-BE-012）、
 *              机器人节点按流程任务执行（无实现 bean 时直接办结）、条件分支取首个 true 出边与默认兜底、
 *              人工决策按最近一次 flowChoice 或默认流向流出。
 */
class ProcessFlowEngineNodeTest2 {

    private FlowEngineFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new FlowEngineFixture();
    }

    /* ==================== CC ==================== */

    @Test
    @DisplayName("CC 抄送节点：逐人发 READ 待阅 + 留痕 CC_AUTO，节点 FINISH 并沿出边继续")
    void ccNodeRecordsAuditAndContinues() {
        fixture.node(3, "抄送节点", NODE_TYPE_CC,
                FlowEngineFixture.ccConfig(FlowEngineFixture.U1 + ";" + FlowEngineFixture.U2));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");
        // 标题模板取值：模板表命中 → ${fieldName}=模板名；notify_cc_subject 未配置 → 回退默认模板
        SysProcessTemplate template = new SysProcessTemplate();
        template.setFieldId(FlowEngineFixture.TEMPLATE_ID);
        template.setFieldName("采购订单审批模板");
        when(fixture.templateDao.selectById(FlowEngineFixture.TEMPLATE_ID)).thenReturn(template);

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isFinished());
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N3"));
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_CC_AUTO));
        assertTrue(fixture.auditContents(SysProcessConstants.AUDIT_TYPE_CC_AUTO).get(0)
                .contains(FlowEngineFixture.U1));
        // READ 待阅字段断言（P17-BE-012）：标题=notify_cc_subject 默认模板渲染、内容=「流程抄送：节点名」、
        // 收件人=解析抄送对象（去重保序）、来源模块/modelId/链接与通知类型
        verify(fixture.notifyTodoService).send(argThat(todo ->
                        "您被抄送的流程：采购订单审批模板".equals(todo.getFieldName())
                                && SysNotifyType.READ.getValue().equals(todo.getFieldNotifyType())
                                && SysProcessConstants.MODEL_KEY_SYS_PROCESS.equals(todo.getFieldModelName())
                                && FlowEngineFixture.EXAMPLE_ID.equals(todo.getFieldModelId())
                                && ("/processExample/detail/" + FlowEngineFixture.EXAMPLE_ID).equals(todo.getFieldUrl())
                                && "流程抄送：抄送节点".equals(todo.getFieldContent())),
                argThat(ids -> ids.size() == 2 && ids.get(0).equals(FlowEngineFixture.U1)
                        && ids.get(1).equals(FlowEngineFixture.U2)));
    }

    @Test
    @DisplayName("CC 抄送对象为空：按跳过处理并留痕，节点 FINISH 继续，不发送抄送待阅")
    void ccNodeWithoutTargetsSkipsWithAudit() {
        fixture.node(3, "抄送节点", NODE_TYPE_CC, FlowEngineFixture.ccConfig(""));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isFinished());
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N3"));
        // 空抄送对象按「跳过」处理并留痕（P17-BE-012 验收标准）
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_CC_AUTO));
        assertEquals("抄送对象为空，节点跳过",
                fixture.auditContents(SysProcessConstants.AUDIT_TYPE_CC_AUTO).get(0));
        // 不发送抄送待阅（P17-BE-012；模板未配置时抄送标题应为「您被抄送的流程：」前缀）；
        // 沿出边进入 END 办结后发送起草人待阅（P17-BE-014 通知收口，模板未配置时 ${fieldName} 渲染为空串）
        verify(fixture.notifyTodoService, never()).send(argThat(todo ->
                null != todo.getFieldName() && todo.getFieldName().startsWith("您被抄送")), anyList());
        verify(fixture.notifyTodoService).send(argThat(todo ->
                        SysNotifyType.READ.getValue().equals(todo.getFieldNotifyType())
                                && "您提交的流程已通过".equals(todo.getFieldName())),
                argThat(ids -> ids.size() == 1 && ids.get(0).equals(FlowEngineFixture.CREATOR)));
    }

    @Test
    @DisplayName("CC 抄送对象公式源解析失败：转流程异常（实例 PAUSE + SYSTEM_ERROR 留痕）")
    void ccNodeFormulaFailurePauses() {
        fixture.node(3, "抄送节点", NODE_TYPE_CC,
                "{\"ccSource\":\"formula\",\"ccFormula\":\"${fieldHandlerId}\"}");
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");
        when(fixture.handlerResolver.resolveCcIds(any(), any(), any())).thenThrow(
                ProcessHandlerResolveException.resolveFailed("节点「抄送节点」的抄送对象公式执行失败: 变量缺失"));

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isPaused());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PAUSE, fixture.example.getFieldStatus());
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR));
        assertTrue(fixture.auditContents(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR).get(0)
                .contains("抄送对象公式执行失败"));
        verify(fixture.notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N3"), "抄送节点解析失败不置完成");
    }

    /* ==================== ROBORT ==================== */

    @Test
    @DisplayName("ROBORT 无任务配置且无执行器（P17-BE-005 未交付）：节点 FINISH 并继续")
    void robortWithoutExecutorFinishes() {
        fixture.node(3, "机器人节点", NODE_TYPE_ROBORT, "{\"tasks\":[]}");
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isFinished());
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N3"));
    }

    @Test
    @DisplayName("ROBORT 有执行器：按任务明细顺序执行成功后节点 FINISH 并继续")
    void robortExecutesTasksAndContinues() {
        List<String> executed = new ArrayList<>();
        ProcessTaskExecutor executor = request -> executed.add(request.taskDetail().getTaskBean());
        fixture.node(3, "机器人节点", NODE_TYPE_ROBORT, FlowEngineFixture.robortConfig("myTask"));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");

        ProcessFlowResult result = fixture.engine(executor).enterFlow(fixture.example, "N3");

        assertTrue(result.isFinished());
        assertEquals(List.of("myTask"), executed);
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N3"));
    }

    @Test
    @DisplayName("ROBORT 任务执行失败：转流程异常（实例 PAUSE + SYSTEM_ERROR 留痕）")
    void robortTaskFailurePausesExample() {
        ProcessTaskExecutor executor = request -> {
            throw new IllegalStateException("任务连接超时");
        };
        fixture.node(3, "机器人节点", NODE_TYPE_ROBORT, FlowEngineFixture.robortConfig("myTask"));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");

        ProcessFlowResult result = fixture.engine(executor).enterFlow(fixture.example, "N3");

        assertTrue(result.isPaused());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PAUSE, fixture.example.getFieldStatus());
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR));
        assertTrue(fixture.auditContents(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR).get(0)
                .contains("任务连接超时"));
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N3"), "机器人节点失败不置完成");
    }

    /* ==================== CONDITION ==================== */

    @Test
    @DisplayName("CONDITION 按分支序号取第一个 true 的出边")
    void conditionTakesFirstTrueBranch() {
        fixture.node(3, "条件分支", NODE_TYPE_CONDITION);
        fixture.node(4, "分支A审批", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U1));
        fixture.node(5, "分支B审批", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U2));
        fixture.node(6, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N4", SysProcessConstants.LINE_TYPE_CONDITION, "amount > 100", false, 1);
        fixture.line("N3", "N5", SysProcessConstants.LINE_TYPE_CONDITION, "amount > 10", false, 2);
        // 条件公式：分支A 的表达式为 false、分支B 的表达式为 true（取首个 true 出边）
        when(fixture.formulaEngine.execute(any(), any(), any())).thenAnswer(invocation ->
                "amount > 10".equals(invocation.getArgument(0)));

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isWaiting());
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N3"));
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N4"), "首个条件为 false 不进入分支A");
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N5"), "第二个条件为 true 进入分支B");
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N6"));
    }

    @Test
    @DisplayName("CONDITION 无 true 分支时走默认分支")
    void conditionFallsBackToDefaultBranch() {
        fixture.node(3, "条件分支", NODE_TYPE_CONDITION);
        fixture.node(4, "分支A审批", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U1));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N4", SysProcessConstants.LINE_TYPE_CONDITION, "amount > 100", false, 1);
        fixture.line("N3", "N5", SysProcessConstants.LINE_TYPE_CONDITION, null, true, 9);
        when(fixture.formulaEngine.execute(any(), any(), any())).thenReturn(Boolean.FALSE);

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isFinished());
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N4"));
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N5"), "无 true 走默认分支到结束节点");
    }

    @Test
    @DisplayName("CONDITION 无 true 且无默认分支：转流程异常 PAUSE + SYSTEM_ERROR")
    void conditionWithoutDefaultBranchPauses() {
        fixture.node(3, "条件分支", NODE_TYPE_CONDITION);
        fixture.node(4, "分支A审批", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U1));
        fixture.line("N3", "N4", SysProcessConstants.LINE_TYPE_CONDITION, "amount > 100", false, 1);
        when(fixture.formulaEngine.execute(any(), any(), any())).thenReturn(Boolean.FALSE);

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isPaused());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PAUSE, fixture.example.getFieldStatus());
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR));
    }

    @Test
    @DisplayName("CONDITION 分支条件公式执行失败：转流程异常")
    void conditionFormulaFailurePauses() {
        fixture.node(3, "条件分支", NODE_TYPE_CONDITION);
        fixture.node(4, "分支A审批", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U1));
        fixture.line("N3", "N4", SysProcessConstants.LINE_TYPE_CONDITION, "amount > 100", false, 1);
        when(fixture.formulaEngine.execute(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("CS_FORMULA_EXECUTE_FAILED: 变量缺失"));

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isPaused());
        assertTrue(result.getErrorSummary().contains("分支条件执行失败"));
    }

    /* ==================== MANUAL ==================== */

    @Test
    @DisplayName("MANUAL 取最近一次 flowChoice 流出（后提交覆盖先提交）")
    void manualUsesLatestFlowChoice() {
        fixture.node(3, "人工决策", NODE_TYPE_MANUAL, FlowEngineFixture.manualConfig("N5"));
        fixture.node(4, "流向A审批", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U1));
        fixture.node(5, "流向B审批", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U2));
        fixture.line("N3", "N4", SysProcessConstants.LINE_TYPE_MANUAL, null, false, 1);
        fixture.line("N3", "N5", SysProcessConstants.LINE_TYPE_MANUAL, null, false, 2);
        // 先指定流向 A、后指定流向 B：以最近一次为准
        var first = fixture.workitem("node-3", "N4");
        fixture.auditNote(SysProcessConstants.AUDIT_TYPE_HANDLER_PASS, first.getFieldId());
        var second = fixture.workitem("node-3", "N5");
        fixture.auditNote(SysProcessConstants.AUDIT_TYPE_HANDLER_PASS, second.getFieldId());

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isWaiting());
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N4"));
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N5"), "后提交的 flowChoice 覆盖先提交");
    }

    @Test
    @DisplayName("MANUAL 无人工指定流向时走 defaultFlowKey")
    void manualFallsBackToDefaultFlow() {
        fixture.node(3, "人工决策", NODE_TYPE_MANUAL, FlowEngineFixture.manualConfig("N5"));
        fixture.node(4, "流向A审批", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U1));
        fixture.node(5, "流向B审批", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U2));
        fixture.line("N3", "N4", SysProcessConstants.LINE_TYPE_MANUAL, null, false, 1);
        fixture.line("N3", "N5", SysProcessConstants.LINE_TYPE_MANUAL, null, false, 2);

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isWaiting());
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N4"));
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N5"), "无指定流向走默认流向 N5");
    }

    @Test
    @DisplayName("MANUAL 指定流向不是本节点出边目标：转流程异常")
    void manualWithIllegalFlowChoicePauses() {
        fixture.node(3, "人工决策", NODE_TYPE_MANUAL, FlowEngineFixture.manualConfig("N4"));
        fixture.node(4, "流向A审批", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U1));
        fixture.line("N3", "N4", SysProcessConstants.LINE_TYPE_MANUAL, null, false, 1);
        var choice = fixture.workitem("node-3", "N9");
        fixture.auditNote(SysProcessConstants.AUDIT_TYPE_HANDLER_PASS, choice.getFieldId());

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isPaused());
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR));
    }
}
