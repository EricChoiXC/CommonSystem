package com.eric.base.sys.process.service.engine;

import com.eric.base.sys.process.model.SysProcessExampleWorkitem;
import com.eric.base.sys.process.properties.SysProcessConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.eric.base.sys.process.properties.SysProcessConstants.APPROVE_MODE_SINGLE;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_APPROVE;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_DRAFT;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_END;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_START;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_PASS;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_PROCESSING;
import static com.eric.base.sys.process.properties.SysProcessConstants.WORKITEM_STATUS_PROCESSING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程流转引擎单测（五）：旧模板 / 在途实例等价回归
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-QA-001 交付物：覆盖研发任务清单 §4.18「旧模板/在途实例等价回归」——
 *              存量线性模板经 P17-DB-005 迁移（DM-20261119-001 节点键按 field_no 顺延、
 *              DM-20261119-002/003 线性连线快照）后，在 v1_7 图遍历引擎下的流转结果与改造前
 *              「按 field_no 顺延取下一节点」的线性串行语义等价。
 *              <p>存量形态特征（与新建模板的 N1/N2/N3 固定键约定不同）：
 *              <ul>
 *              <li>节点键按 field_no 顺延 {@code N1..Nn}——**结束节点键不一定是 N3**，
 *                  含中间节点的模板结束节点为 {@code Nn}；</li>
 *              <li>连线为线性链 {@code N1→N2→…→Nn}（field_line_type=NORMAL、field_branch_order=0）；</li>
 *              <li>节点配置为旧版 JSON（仅 {@code handlerIds}/{@code approveMode}/{@code autoSkip}/
 *                  {@code autoContinueSkip}，无 v2 字段）；旧 {@code autoSkip} 读取时归一等价于
 *                  {@code skipMode}（{@code yes}→{@code prev}、{@code no}→{@code none}）。</li>
 *              </ul>
 */
class ProcessFlowLegacyCompatibilityTest {

    /** 旧版节点配置 JSON（仅存量字段，无 v2 字段） */
    private static String legacyApproveConfig(String handlerIds, String autoSkip) {
        return "{\"handlerIds\":\"" + handlerIds + "\",\"approveMode\":\"" + APPROVE_MODE_SINGLE
                + "\",\"triggerType\":\"fixed\",\"autoSkip\":\"" + autoSkip
                + "\",\"autoContinueSkip\":\"system\"}";
    }

    private FlowEngineFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new FlowEngineFixture();
    }

    /**
     * 存量线性模板形态：节点键 N1..N5（START/DRAFT/APPROVE/APPROVE/END），线性连线。
     * <p>注意结束节点键为 N5 而非 N3——这是存量 field_no 顺延回填的真实形态。</p>
     */
    private void buildLegacyLinearTemplate(String autoSkip) {
        fixture.node(1, "开始节点", NODE_TYPE_START);
        fixture.node(2, "草稿节点", NODE_TYPE_DRAFT);
        fixture.node(3, "审批节点1", NODE_TYPE_APPROVE, legacyApproveConfig(FlowEngineFixture.U1, autoSkip));
        fixture.node(4, "审批节点2", NODE_TYPE_APPROVE, legacyApproveConfig(FlowEngineFixture.U1, autoSkip));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N1", "N2");
        fixture.line("N2", "N3");
        fixture.line("N3", "N4");
        fixture.line("N4", "N5");
    }

    @Test
    @DisplayName("旧线性模板（键 N1..N5，结束节点为 N5）按连线逐节点流转，语义与改造前线性串行等价")
    void legacyLinearTemplateFlowsNodeByNode() {
        buildLegacyLinearTemplate(SysProcessConstants.AUTO_SKIP_NO);

        ProcessFlowResult entered = fixture.engine(null).enterFlow(fixture.example, "N1");

        assertTrue(entered.isWaiting(), "开始节点后进入起草节点等待提交");
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N1"));
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N2"));

        // 起草人提交：草稿节点办结后从审批节点 1 继续（改造前即「按 field_no 取下一节点」）
        fixture.nodeByKey("N2").setFieldStatus(NODE_STATUS_FINISH);
        ProcessFlowResult atFirstApprove = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(atFirstApprove.isWaiting());
        assertEquals(1, fixture.workitemsOf("N3").size());
        assertEquals(FlowEngineFixture.U1, fixture.workitemsOf("N3").get(0).getFieldHandlerId());
        assertEquals(WORKITEM_STATUS_PROCESSING, fixture.workitemsOf("N3").get(0).getFieldStatus());

        // 审批节点 1 通过：沿连线流入审批节点 2
        ProcessFlowResult afterFirst = fixture.passNode("N3");

        assertTrue(afterFirst.isWaiting(), "审批节点 2 生成工作项后停留");
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N3"));
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N4"));
        assertEquals(1, fixture.workitemsOf("N4").size());

        // 审批节点 2 通过：流入结束节点并办结
        ProcessFlowResult afterSecond = fixture.passNode("N4");

        assertTrue(afterSecond.isFinished());
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N4"));
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_FINISH, fixture.example.getFieldStatus());
    }

    @Test
    @DisplayName("旧配置 autoSkip=yes 读取归一等价于 skipMode=prev：与上一有效节点同一处理人时自动跳过")
    void legacyAutoSkipYesEqualsPrevMode() {
        buildLegacyLinearTemplate(SysProcessConstants.AUTO_SKIP_YES);

        fixture.engine(null).enterFlow(fixture.example, "N1");
        fixture.nodeByKey("N2").setFieldStatus(NODE_STATUS_FINISH);
        fixture.engine(null).enterFlow(fixture.example, "N3");
        fixture.passNode("N3");

        // 审批节点 2 与上一有效节点（审批节点 1）处理人同为 U1：旧 autoSkip=yes 归一为 prev → 跳过
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_AUTO_PASS),
                "旧 autoSkip=yes 应等价 skipMode=prev 产生 AUTO_PASS 留痕");
        assertEquals(NODE_STATUS_PASS, fixture.statusOf("N4"));
        assertEquals(SysProcessConstants.WORKITEM_STATUS_FINISH, fixture.workitemsOf("N4").get(0).getFieldStatus());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_FINISH, fixture.example.getFieldStatus(), "跳过节点后继续流入结束节点");
    }

    @Test
    @DisplayName("旧配置 autoSkip=no 读取归一等价于 skipMode=none：同一处理人也不跳过")
    void legacyAutoSkipNoEqualsNoneMode() {
        buildLegacyLinearTemplate(SysProcessConstants.AUTO_SKIP_NO);

        fixture.engine(null).enterFlow(fixture.example, "N1");
        fixture.nodeByKey("N2").setFieldStatus(NODE_STATUS_FINISH);
        fixture.engine(null).enterFlow(fixture.example, "N3");
        ProcessFlowResult afterFirst = fixture.passNode("N3");

        assertEquals(0L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_AUTO_PASS),
                "旧 autoSkip=no 应等价 skipMode=none：不产生自动跳过留痕");
        assertTrue(afterFirst.isWaiting(), "本节点需实际审批，停留等待");
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N4"));
        assertEquals(WORKITEM_STATUS_PROCESSING, fixture.workitemsOf("N4").get(0).getFieldStatus());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PROCESSING, fixture.example.getFieldStatus());
    }

    @Test
    @DisplayName("在途存量实例（DM-003 回填形态：已办结前置节点 + 当前审批节点 PROCESSING）恢复流转不重复生成工作项")
    void inFlightLegacyInstanceResumesFromSnapshotLine() {
        buildLegacyLinearTemplate(SysProcessConstants.AUTO_SKIP_NO);

        // 在途形态：开始/草稿/审批节点1 已办结，审批节点1 留有已办结工作项，当前停留在审批节点2
        fixture.nodeByKey("N1").setFieldStatus(NODE_STATUS_FINISH);
        fixture.nodeByKey("N2").setFieldStatus(NODE_STATUS_FINISH);
        fixture.nodeByKey("N3").setFieldStatus(NODE_STATUS_FINISH);
        fixture.workitem(fixture.nodeId(3), null);
        fixture.nodeByKey("N4").setFieldStatus(NODE_STATUS_PROCESSING);
        SysProcessExampleWorkitem processing = new SysProcessExampleWorkitem();
        processing.initFieldId();
        processing.setFieldExampleId(FlowEngineFixture.EXAMPLE_ID);
        processing.setFieldNodeId(fixture.nodeId(4));
        processing.setFieldHandlerId(FlowEngineFixture.U1);
        processing.setFieldExpectHandlerId(FlowEngineFixture.U1);
        processing.setFieldStatus(WORKITEM_STATUS_PROCESSING);
        fixture.workitems.add(processing);
        fixture.example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);

        ProcessFlowResult resumed = fixture.passNode("N4");

        assertTrue(resumed.isFinished(), "在途实例按实例连线快照继续流转至结束");
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N4"));
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_FINISH, fixture.example.getFieldStatus());
        assertEquals(1, fixture.workitemsOf("N3").size(), "已办结节点的历史工作项不被改动");
        assertEquals(1, fixture.workitemsOf("N4").size(), "在途工作项办结后不重复生成");
    }
}
