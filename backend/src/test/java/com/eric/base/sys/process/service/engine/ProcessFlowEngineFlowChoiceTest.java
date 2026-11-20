package com.eric.base.sys.process.service.engine;

import com.eric.base.sys.process.properties.SysProcessConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_DRAFTER_SUBMIT;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_HANDLER_PASS;
import static com.eric.base.sys.process.properties.SysProcessConstants.APPROVE_MODE_SINGLE;
import static com.eric.base.sys.process.properties.SysProcessConstants.EXAMPLE_STATUS_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_PROCESSING;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_WAITING;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_APPROVE;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_DRAFT;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_END;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_MANUAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程流转引擎单测：人工决策流向的双侧来源（D6，P17-BE-008）
 *
 * @author Eric
 * @date 2026/11/20
 * @description 覆盖 MANUAL 节点人工流向的「后提交覆盖先提交」双侧比较：
 *              审批人通过侧（HANDLER_PASS 工作项 {@code field_config.flowChoice}）与
 *              起草人提交侧（起草节点运行态 {@code flowChoice}，P17-BE-008 补全）按审批记录时间
 *              取较晚者；flowDecisions 不含起草节点时忽略提交侧流向。
 */
class ProcessFlowEngineFlowChoiceTest {

    private FlowEngineFixture fixture;

    /** 人工决策节点配置：决策来源含起草节点（N2）与审批节点（N3），默认流向 N7 */
    private static final String MANUAL_WITH_DRAFT_SOURCE =
            "{\"defaultFlowKey\":\"N7\",\"flowDecisions\":[\"N2\",\"N3\"]}";

    /** 人工决策节点配置：决策来源仅审批节点（N3），不含起草节点 */
    private static final String MANUAL_WITHOUT_DRAFT_SOURCE =
            "{\"defaultFlowKey\":\"N7\",\"flowDecisions\":[\"N3\"]}";

    @BeforeEach
    void setUp() {
        fixture = new FlowEngineFixture();
    }

    /** 装配图：N3(审批)→N6(MANUAL)→N7/N8(END)，另含 N1/N2（起草节点运行态承载提交侧流向） */
    private void buildManualGraph(String manualConfig) {
        fixture.node(1, "开始节点", "START");
        fixture.node(2, "草稿节点", NODE_TYPE_DRAFT);
        fixture.node(3, "审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig(APPROVE_MODE_SINGLE, FlowEngineFixture.U1));
        fixture.node(6, "人工决策节点", NODE_TYPE_MANUAL, manualConfig);
        fixture.node(7, "结束节点7", NODE_TYPE_END);
        fixture.node(8, "结束节点8", NODE_TYPE_END);
        fixture.line("N1", "N2");
        fixture.line("N2", "N3");
        fixture.line("N3", "N6");
        fixture.line("N6", "N7");
        fixture.line("N6", "N8");
    }

    @Test
    @DisplayName("[D6: 提交侧较晚 → 起草节点 flowChoice 覆盖通过侧] 后提交覆盖先提交")
    void manualUsesDraftChoiceWhenSubmitIsLatest() {
        buildManualGraph(MANUAL_WITH_DRAFT_SOURCE);
        // 审批人通过侧：工作项携带 flowChoice=N7，其 PASS 留痕时间较早
        var passed = fixture.workitem(fixture.nodeId(3), "N7");
        fixture.auditNote(AUDIT_TYPE_HANDLER_PASS, passed.getFieldId());
        // 起草人提交侧：起草节点运行态携带 flowChoice=N8，其 DRAFTER_SUBMIT 留痕时间较晚
        fixture.nodeByKey("N2").setFieldRuntime("{\"flowChoice\":\"N8\"}");
        fixture.auditNote(AUDIT_TYPE_DRAFTER_SUBMIT, null);

        fixture.engine(null).enterFlow(fixture.example, "N6");

        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N8"), "提交侧较晚时应按起草节点 flowChoice 流出");
        assertEquals(EXAMPLE_STATUS_FINISH, fixture.example.getFieldStatus());
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N7"), "未选中的出边目标保持等待");
    }

    @Test
    @DisplayName("[D6: 通过侧较晚 → 通过侧 flowChoice 覆盖提交侧] 后提交覆盖先提交")
    void manualUsesPassChoiceWhenPassIsLatest() {
        buildManualGraph(MANUAL_WITH_DRAFT_SOURCE);
        // 起草人提交侧在前（时间较早），审批人通过侧在后（时间较晚）
        fixture.nodeByKey("N2").setFieldRuntime("{\"flowChoice\":\"N8\"}");
        fixture.auditNote(AUDIT_TYPE_DRAFTER_SUBMIT, null);
        var passed = fixture.workitem(fixture.nodeId(3), "N7");
        fixture.auditNote(AUDIT_TYPE_HANDLER_PASS, passed.getFieldId());

        fixture.engine(null).enterFlow(fixture.example, "N6");

        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N7"), "通过侧较晚时应按工作项 flowChoice 流出");
        assertEquals(EXAMPLE_STATUS_FINISH, fixture.example.getFieldStatus());
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N8"), "未选中的出边目标保持等待");
    }

    @Test
    @DisplayName("[D6: flowDecisions 不含起草节点 → 忽略提交侧流向]")
    void manualIgnoresDraftChoiceWhenFlowDecisionsExcludesDraft() {
        buildManualGraph(MANUAL_WITHOUT_DRAFT_SOURCE);
        // 提交侧留痕时间较晚，但 flowDecisions 不含起草节点：提交侧流向不生效
        fixture.nodeByKey("N2").setFieldRuntime("{\"flowChoice\":\"N8\"}");
        fixture.auditNote(AUDIT_TYPE_DRAFTER_SUBMIT, null);
        var passed = fixture.workitem(fixture.nodeId(3), "N7");
        fixture.auditNote(AUDIT_TYPE_HANDLER_PASS, passed.getFieldId());

        fixture.engine(null).enterFlow(fixture.example, "N6");

        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N7"), "flowDecisions 不含起草节点时按通过侧流向流出");
        assertEquals(EXAMPLE_STATUS_FINISH, fixture.example.getFieldStatus());
    }

    @Test
    @DisplayName("[D6: 仅提交侧指定 → 无通过侧 flowChoice 时走起草节点 flowChoice]")
    void manualUsesDraftChoiceWithoutPassChoice() {
        buildManualGraph(MANUAL_WITH_DRAFT_SOURCE);
        fixture.nodeByKey("N2").setFieldRuntime("{\"flowChoice\":\"N8\"}");
        fixture.auditNote(AUDIT_TYPE_DRAFTER_SUBMIT, null);

        fixture.engine(null).enterFlow(fixture.example, "N6");

        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N8"), "仅提交侧指定时按起草节点 flowChoice 流出");
        assertEquals(EXAMPLE_STATUS_FINISH, fixture.example.getFieldStatus());
    }

    @Test
    @DisplayName("[D6: 提交侧较晚但运行态无 flowChoice → 回退默认流向]")
    void manualFallsBackToDefaultWhenDraftRuntimeHasNoChoice() {
        buildManualGraph(MANUAL_WITH_DRAFT_SOURCE);
        // 起草节点运行态无 flowChoice（如最近一次提交未指定流向）：回退默认流向 N7
        fixture.auditNote(AUDIT_TYPE_DRAFTER_SUBMIT, null);

        fixture.engine(null).enterFlow(fixture.example, "N6");

        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N7"), "无人工指定流向时走默认流向");
        assertEquals(EXAMPLE_STATUS_FINISH, fixture.example.getFieldStatus());
        assertTrue(fixture.workitemsOf("N8").isEmpty());
    }
}
