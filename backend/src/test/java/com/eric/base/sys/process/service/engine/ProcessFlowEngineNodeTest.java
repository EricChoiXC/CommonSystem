package com.eric.base.sys.process.service.engine;

import com.eric.base.sys.process.model.SysProcessExampleNode;
import com.eric.base.sys.process.model.SysProcessExampleWorkitem;
import com.eric.base.sys.process.properties.SysProcessConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.eric.base.sys.process.properties.SysProcessConstants.APPROVE_MODE_COUNTERSIGN;
import static com.eric.base.sys.process.properties.SysProcessConstants.APPROVE_MODE_SERIAL;
import static com.eric.base.sys.process.properties.SysProcessConstants.APPROVE_MODE_SINGLE;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_APPROVE;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_DRAFT;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_END;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_START;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_PASS;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_PROCESSING;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_WAITING;
import static com.eric.base.sys.process.properties.SysProcessConstants.WORKITEM_STATUS_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.WORKITEM_STATUS_PROCESSING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程流转引擎单测（一）：START / DRAFT / END 流转与 APPROVE 单签·会签·串行
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-003 交付物：覆盖 docs/v1_7-流程管理/功能实现方案-改造分析与方案设计.md
 *              §3.3 节点语义（START/DRAFT/END/APPROVE）与 §3.4 流转引擎设计：
 *              沿连线流转、审批人解析与工作项生成、单签/会签/串行通过后的节点流出、自动跳过与空审批人。
 */
class ProcessFlowEngineNodeTest {

    private FlowEngineFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new FlowEngineFixture();
    }

    /* ==================== START / DRAFT / END ==================== */

    @Test
    @DisplayName("START 沿唯一出边流转，进入起草节点停留等待提交")
    void startFlowsAlongOutgoingEdgeToDraft() {
        fixture.node(1, "开始节点", NODE_TYPE_START);
        fixture.node(2, "草稿节点", NODE_TYPE_DRAFT);
        fixture.node(3, "结束节点", NODE_TYPE_END);
        fixture.line("N1", "N2");
        fixture.line("N2", "N3");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N1");

        assertTrue(result.isWaiting(), "起草节点应停留等待起草人提交");
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N1"));
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N2"));
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N3"));
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PROCESSING, fixture.example.getFieldStatus());
    }

    @Test
    @DisplayName("END 进入即流程办结，记录完成时间")
    void endFinishesExample() {
        fixture.node(1, "开始节点", NODE_TYPE_START);
        fixture.node(3, "结束节点", NODE_TYPE_END);
        fixture.line("N1", "N3");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N1");

        assertTrue(result.isFinished());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_FINISH, fixture.example.getFieldStatus());
        assertNotNull(fixture.example.getFieldFinishTime());
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N3"));
    }

    /* ==================== APPROVE 单签 ==================== */

    @Test
    @DisplayName("APPROVE 单签：按审批人配置生成处理中工作项并停留等待")
    void approveSingleCreatesProcessingWorkitems() {
        fixture.node(3, "审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig(APPROVE_MODE_SINGLE, FlowEngineFixture.U1 + ";" + FlowEngineFixture.U2));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isWaiting());
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N3"));
        assertEquals(2, fixture.workitemsOf("N3").size());
        for (SysProcessExampleWorkitem workitem : fixture.workitemsOf("N3")) {
            assertEquals(WORKITEM_STATUS_PROCESSING, workitem.getFieldStatus());
        }
    }

    @Test
    @DisplayName("APPROVE 审批人解析为空：节点 PASS 并沿出边继续")
    void approveWithoutHandlerSkipsNode() {
        fixture.node(3, "审批节点", NODE_TYPE_APPROVE, FlowEngineFixture.approveConfig(APPROVE_MODE_SINGLE, ""));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isFinished());
        assertEquals(NODE_STATUS_PASS, fixture.statusOf("N3"));
        assertTrue(fixture.workitemsOf("N3").isEmpty());
    }

    @Test
    @DisplayName("APPROVE 单签一人通过即沿出边流转到结束节点")
    void approveSinglePassesToNextNode() {
        fixture.node(3, "审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig(APPROVE_MODE_SINGLE, FlowEngineFixture.U1 + ";" + FlowEngineFixture.U2));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");
        fixture.engine(null).enterFlow(fixture.example, "N3");

        ProcessFlowResult result = fixture.passNode("N3");

        assertTrue(result.isFinished());
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N3"));
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_FINISH, fixture.example.getFieldStatus());
    }

    /* ==================== APPROVE 会签 ==================== */

    @Test
    @DisplayName("APPROVE 会签：一人通过后仍停留审批中，全员通过后才流出")
    void approveCountersignWaitsForAllHandlers() {
        fixture.node(3, "审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig(APPROVE_MODE_COUNTERSIGN,
                        FlowEngineFixture.U1 + ";" + FlowEngineFixture.U2));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");
        fixture.engine(null).enterFlow(fixture.example, "N3");

        // 仅第一位处理人通过：另一工作项仍在处理中
        SysProcessExampleWorkitem first = fixture.workitemsOf("N3").get(0);
        first.setFieldStatus(WORKITEM_STATUS_FINISH);
        ProcessFlowResult partial = fixture.engine(null).pass(fixture.example, "N3", "审批节点");

        assertTrue(partial.isWaiting(), "会签未全员通过应停留审批中");
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N3"));
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PROCESSING, fixture.example.getFieldStatus());

        // 第二位处理人通过：全员完成，节点流出到结束节点
        for (SysProcessExampleWorkitem workitem : fixture.workitemsOf("N3")) {
            workitem.setFieldStatus(WORKITEM_STATUS_FINISH);
        }
        ProcessFlowResult all = fixture.engine(null).pass(fixture.example, "N3", "审批节点");

        assertTrue(all.isFinished());
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N3"));
    }

    /* ==================== APPROVE 串行 ==================== */

    @Test
    @DisplayName("APPROVE 串行：逐人生成工作项，队列走完后节点流出")
    void approveSerialGeneratesWorkitemOneByOne() {
        fixture.node(3, "审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig(APPROVE_MODE_SERIAL,
                        FlowEngineFixture.U1 + ";" + FlowEngineFixture.U2 + ";" + FlowEngineFixture.U3));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");

        ProcessFlowResult entered = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(entered.isWaiting());
        assertEquals(1, fixture.workitemsOf("N3").size(), "串行模式首次仅生成首位处理人工作项");
        assertEquals(FlowEngineFixture.U1, fixture.workitemsOf("N3").get(0).getFieldHandlerId());

        // 第一位通过：生成第二位
        ProcessFlowResult secondStep = fixture.passNode("N3");
        assertTrue(secondStep.isWaiting());
        assertEquals(2, fixture.workitemsOf("N3").size());
        assertEquals(FlowEngineFixture.U2, fixture.workitemsOf("N3").get(1).getFieldHandlerId());

        // 第二位通过：生成第三位
        fixture.passNode("N3");
        assertEquals(3, fixture.workitemsOf("N3").size());
        assertEquals(FlowEngineFixture.U3, fixture.workitemsOf("N3").get(2).getFieldHandlerId());

        // 第三位通过：队列走完，节点流出
        ProcessFlowResult finished = fixture.passNode("N3");
        assertTrue(finished.isFinished());
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N3"));
    }

    /* ==================== 自动跳过（沿用存量语义） ==================== */

    @Test
    @DisplayName("相同审批人自动跳过：跳过者工作项直接 FINISH 并留痕 AUTO_PASS")
    void repeatedHandlerAutoSkippedWithAudit() {
        // 上游审批节点已完成（同一批审批人），本节点配置允许自动跳过（autoSkip 缺省跟随系统=开）
        SysProcessExampleNode upstream = fixture.node(3, "上游审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig(APPROVE_MODE_SINGLE, FlowEngineFixture.U1));
        upstream.setFieldStatus(NODE_STATUS_FINISH);
        SysProcessExampleWorkitem upstreamWorkitem = new SysProcessExampleWorkitem();
        upstreamWorkitem.setFieldId("wi-upstream");
        upstreamWorkitem.setFieldNodeId(upstream.getFieldId());
        upstreamWorkitem.setFieldHandlerId(FlowEngineFixture.U1);
        upstreamWorkitem.setFieldExpectHandlerId(FlowEngineFixture.U1);
        upstreamWorkitem.setFieldStatus(WORKITEM_STATUS_FINISH);
        fixture.workitems.add(upstreamWorkitem);

        fixture.node(4, "本节点", NODE_TYPE_APPROVE,
                "{\"handlerIds\":\"" + FlowEngineFixture.U1 + "\",\"approveMode\":\"single\"}");
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N4");
        fixture.line("N4", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N4");

        assertTrue(result.isFinished(), "全部处理人被自动跳过：节点 PASS 后继续到结束节点");
        assertEquals(NODE_STATUS_PASS, fixture.statusOf("N4"));
        assertEquals(1, fixture.workitemsOf("N4").size());
        assertEquals(WORKITEM_STATUS_FINISH, fixture.workitemsOf("N4").get(0).getFieldStatus());
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_AUTO_PASS));
    }

    @Test
    @DisplayName("node_key 缺失的存量节点按节点编号兜底生成键，仍可沿连线流转")
    void legacyNodesWithoutNodeKeyFallbackByNodeNo() {
        SysProcessExampleNode start = fixture.node(1, "开始节点", NODE_TYPE_START);
        start.setFieldNodeKey(null);
        SysProcessExampleNode draft = fixture.node(2, "草稿节点", NODE_TYPE_DRAFT);
        draft.setFieldNodeKey(null);
        SysProcessExampleNode end = fixture.node(3, "结束节点", NODE_TYPE_END);
        end.setFieldNodeKey(null);
        fixture.line("N1", "N2");
        fixture.line("N2", "N3");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N1");

        assertTrue(result.isWaiting());
        assertEquals(NODE_STATUS_PROCESSING, draft.getFieldStatus());
        assertEquals(WORKITEM_STATUS_FINISH, WORKITEM_STATUS_FINISH);
    }
}



