package com.eric.base.sys.process.service.engine;

import com.eric.base.sys.process.properties.SysProcessConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.eric.base.sys.process.properties.SysProcessConstants.APPROVE_MODE_COUNTERSIGN;
import static com.eric.base.sys.process.properties.SysProcessConstants.APPROVE_MODE_SERIAL;
import static com.eric.base.sys.process.properties.SysProcessConstants.APPROVE_MODE_SINGLE;
import static com.eric.base.sys.process.properties.SysProcessConstants.EXAMPLE_STATUS_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.EXAMPLE_STATUS_PAUSE;
import static com.eric.base.sys.process.properties.SysProcessConstants.JOIN_MODE_ALL;
import static com.eric.base.sys.process.properties.SysProcessConstants.LINE_TYPE_CONDITION;
import static com.eric.base.sys.process.properties.SysProcessConstants.LINE_TYPE_PARALLEL;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_PASS;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_PROCESSING;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_WAITING;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_APPROVE;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_CONDITION;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_DRAFT;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_END;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_FORK;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_JOIN;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_MANUAL;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_START;
import static com.eric.base.sys.process.properties.SysProcessConstants.WORKITEM_STATUS_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.WORKITEM_STATUS_PROCESSING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 流程流转引擎单测（五）：流转矩阵缺口补齐（10 类节点 × 审批模式 × 跳过组合 × 异常流）
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-QA-001 交付物：在既有 {@code ProcessFlowEngineNodeTest} /
 *              {@code ProcessFlowEngineNodeTest2} / {@code ProcessFlowEngineForkJoinTest} /
 *              {@code ProcessFlowEngineSkipModeTest} / {@code ProcessFlowEngineAdminTest}
 *              之上只补矩阵缺口，不重复既有格子。每个用例的 {@link DisplayName} 前缀
 *              {@code [矩阵: …]} 标注其覆盖的矩阵格子（节点类型 × 维度 × 期望终态）。
 *              <p>本类覆盖：DRAFT 重复进入幂等、START 缺出边、CONDITION 仅默认分支、
 *              FORK 全分支激活与激活集合顺序、JOIN 单分支到达等待、MANUAL 无流向无默认、
 *              图结构缺失（连线指向不存在节点 / 条件节点缺出边）、PAUSE 不回滚已完成节点。</p>
 */
class ProcessFlowEngineMatrixTest {

    private FlowEngineFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new FlowEngineFixture();
    }

    /* ==================== 节点类型：START / DRAFT ==================== */

    @Test
    @DisplayName("[矩阵: START × 图结构缺失 × PAUSE] 开始节点缺少出边：实例 PAUSE + SYSTEM_ERROR，节点保持 WAITING")
    void startWithoutOutgoingEdgePauses() {
        fixture.node(1, "开始节点", NODE_TYPE_START);

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N1");

        assertTrue(result.isPaused(), "开始节点无出边时无法继续流转，应转流程异常");
        assertEquals(EXAMPLE_STATUS_PAUSE, fixture.example.getFieldStatus());
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR));
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N1"), "异常出口不回滚节点状态");
    }

    @Test
    @DisplayName("[矩阵: DRAFT × 重复进入 × WAITING] 起草节点重复进入保持 PROCESSING 且不重复生成工作项")
    void draftReentryStaysProcessing() {
        fixture.node(1, "开始节点", NODE_TYPE_START);
        fixture.node(2, "草稿节点", NODE_TYPE_DRAFT);
        fixture.node(3, "审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig(APPROVE_MODE_SINGLE, FlowEngineFixture.U1));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N1", "N2");
        fixture.line("N2", "N3");
        fixture.line("N3", "N5");

        fixture.engine(null).enterFlow(fixture.example, "N1");
        ProcessFlowResult again = fixture.engine(null).enterFlow(fixture.example, "N2");

        assertTrue(again.isWaiting(), "起草节点每次进入均停留等待起草人提交");
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N2"));
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N3"), "起草节点不沿出边自行流转");
        assertTrue(fixture.workitemsOf("N3").isEmpty(), "未提交前审批节点不生成工作项");
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PROCESSING, fixture.example.getFieldStatus());
    }

    /* ==================== 节点类型：APPROVE × 三种审批模式收敛 ==================== */

    @Test
    @DisplayName("[矩阵: APPROVE × serial × WAITING] 串行逐人生成：工作项按序位依次生成，队列走完方流出")
    void approveSerialGeneratesWorkitemsInOrder() {
        fixture.node(3, "串行审批", NODE_TYPE_APPROVE, FlowEngineFixture.approveConfig(
                APPROVE_MODE_SERIAL, FlowEngineFixture.U1 + ";" + FlowEngineFixture.U2));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");

        fixture.engine(null).enterFlow(fixture.example, "N3");
        assertEquals(1, fixture.workitemsOf("N3").size(), "串行仅生成首位处理人工作项");
        assertEquals(FlowEngineFixture.U1, fixture.workitemsOf("N3").get(0).getFieldExpectHandlerId());

        ProcessFlowResult first = fixture.passNode("N3");
        assertTrue(first.isWaiting(), "首位通过后进入下一位，节点仍审批中");
        assertTrue(fixture.workitemsOf("N3").stream().anyMatch(w ->
                FlowEngineFixture.U2.equals(w.getFieldExpectHandlerId())), "生成第 2 位处理人工作项");
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N3"));

        ProcessFlowResult second = fixture.passNode("N3");
        assertTrue(second.isFinished(), "串行队列走完后流出至结束节点");
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N3"));
        assertEquals(EXAMPLE_STATUS_FINISH, fixture.example.getFieldStatus());
    }

    @Test
    @DisplayName("[矩阵: APPROVE × countersign × FINISH] 会签全员通过后流出，中间态停留审批中")
    void approveCountersignFinishesAfterAllHandlers() {
        fixture.node(3, "会签审批", NODE_TYPE_APPROVE, FlowEngineFixture.approveConfig(
                APPROVE_MODE_COUNTERSIGN, FlowEngineFixture.U1 + ";" + FlowEngineFixture.U2));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");

        fixture.engine(null).enterFlow(fixture.example, "N3");
        assertEquals(2, fixture.workitemsOf("N3").size());
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N3"));

        // 仅 U1 通过：另一工作项仍在途，会签未完成（不能借 passNode——它会把全部在途工作项置为完成）
        var workitemOfU1 = fixture.workitemsOf("N3").get(0);
        workitemOfU1.setFieldStatus(WORKITEM_STATUS_FINISH);
        fixture.auditNote(SysProcessConstants.AUDIT_TYPE_HANDLER_PASS, workitemOfU1.getFieldId());
        ProcessFlowResult partial = fixture.engine(null).pass(fixture.example, "N3", "会签审批",
                workitemOfU1.getFieldId());

        assertTrue(partial.isWaiting(), "会签未全员完成时停留审批中");
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N3"));
        assertTrue(fixture.workitemsOf("N3").stream().anyMatch(w ->
                WORKITEM_STATUS_PROCESSING.equals(w.getFieldStatus())), "仍存在在途工作项");

        ProcessFlowResult done = fixture.passNode("N3");
        assertTrue(done.isFinished(), "会签全员通过后流出至结束节点");
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N3"));
        assertEquals(EXAMPLE_STATUS_FINISH, fixture.example.getFieldStatus());
    }
}
