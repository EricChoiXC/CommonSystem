package com.eric.base.sys.process.service.engine;

import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.process.model.SysProcessAuditNote;
import com.eric.base.sys.process.model.SysProcessExampleNode;
import com.eric.base.sys.process.model.SysProcessExampleWorkitem;
import com.eric.base.sys.process.properties.SysProcessConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.eric.base.sys.process.properties.SysProcessConstants.APPROVE_MODE_COUNTERSIGN;
import static com.eric.base.sys.process.properties.SysProcessConstants.APPROVE_MODE_SINGLE;
import static com.eric.base.sys.process.properties.SysProcessConstants.EMPTY_HANDLER_MODE_ERROR;
import static com.eric.base.sys.process.properties.SysProcessConstants.EMPTY_HANDLER_MODE_SKIP;
import static com.eric.base.sys.process.properties.SysProcessConstants.EMPTY_HANDLER_MODE_SYSTEM;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_PASS;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_PROCESSING;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_WAITING;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_APPROVE;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_END;
import static com.eric.base.sys.process.properties.SysProcessConstants.SETTING_PROCESS_AUTO_HANDLE_PASS;
import static com.eric.base.sys.process.properties.SysProcessConstants.SETTING_TYPE_PROCESS;
import static com.eric.base.sys.process.properties.SysProcessConstants.SKIP_MODE_ANY;
import static com.eric.base.sys.process.properties.SysProcessConstants.SKIP_MODE_NONE;
import static com.eric.base.sys.process.properties.SysProcessConstants.SKIP_MODE_PREV;
import static com.eric.base.sys.process.properties.SysProcessConstants.SKIP_MODE_SYSTEM;
import static com.eric.base.sys.process.properties.SysProcessConstants.WORKITEM_STATUS_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.WORKITEM_STATUS_PROCESSING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 流程流转引擎单测（三）：skipMode 四值 × 单签/会签/串行
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-004 交付物：覆盖 docs/设计文档/sys/process/流程核心业务.md
 *              「审批节点配置」skipMode 四值（system/none/prev/any）与「流程流转公共规范」#6
 *              自动跳过体系的引擎落地——比对范围（上一有效节点 / 全实例已审批人）、
 *              系统配置门禁（none 不受系统允许影响；prev/any 不以系统配置为前置门禁）、
 *              跳过者不占串行序位、全员被跳过则节点跳过。
 */
class ProcessFlowEngineSkipModeTest {

    private FlowEngineFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new FlowEngineFixture();
    }

    /* ==================== emptyHandlerMode 三值 ==================== */

    @Test
    @DisplayName("emptyHandlerMode=skip：审批人解析为空，节点 PASS 并沿出边继续（不生成工作项）")
    void emptyHandlerModeSkipPassesNode() {
        emptyNode(3, EMPTY_HANDLER_MODE_SKIP);
        fixture.line("N3", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isFinished());
        assertEquals(NODE_STATUS_PASS, fixture.statusOf("N3"));
        assertTrue(fixture.workitemsOf("N3").isEmpty());
    }

    @Test
    @DisplayName("emptyHandlerMode=system（缺省）：同现状跳过该节点并继续")
    void emptyHandlerModeSystemSkipsNode() {
        emptyNode(3, EMPTY_HANDLER_MODE_SYSTEM);
        fixture.line("N3", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isFinished());
        assertEquals(NODE_STATUS_PASS, fixture.statusOf("N3"));
        assertTrue(fixture.workitemsOf("N3").isEmpty());
    }

    @Test
    @DisplayName("emptyHandlerMode=error：转流程异常（实例 PAUSE + SYSTEM_ERROR，节点不跳过）")
    void emptyHandlerModeErrorPausesExample() {
        emptyNode(3, EMPTY_HANDLER_MODE_ERROR);
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isPaused());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PAUSE, fixture.example.getFieldStatus());
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR));
        assertTrue(result.getErrorSummary().contains("无审批人"));
        assertTrue(result.getErrorSummary().contains("本节点"));
        // 节点未被置跳过，也未继续流转到结束节点
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N3"));
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N5"));
        assertTrue(fixture.workitemsOf("N3").isEmpty());
    }

    @Test
    @DisplayName("emptyHandlerMode 非法取值归一为 system：跳过该节点而非转流程异常")
    void emptyHandlerModeIllegalFallsBackToSystem() {
        emptyNode(3, "illegal");
        fixture.line("N3", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isFinished());
        assertEquals(NODE_STATUS_PASS, fixture.statusOf("N3"));
        assertEquals(0L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR));
    }

    @Test
    @DisplayName("emptyHandlerMode=skip 时解析器提前抛空审批人异常：引擎捕获并按跳过继续（单一出口）")
    void emptyHandlerModeSkipToleratesResolverEmptyHandlerException() {
        fixture.node(3, "审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.configOf("handlerIds", fixture.U1, "approveMode", APPROVE_MODE_SINGLE,
                        "emptyHandlerMode", EMPTY_HANDLER_MODE_SKIP));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");
        when(fixture.handlerResolver.resolveHandlerIds(any(), any(), any()))
                .thenThrow(ProcessHandlerResolveException.emptyHandler("节点未解析出任何审批人"));

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isFinished(), "空审批人异常在跳过的空审批人语义下被吞掉，按跳过继续");
        assertEquals(NODE_STATUS_PASS, fixture.statusOf("N3"));
        assertEquals(0L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_SYSTEM_ERROR));
    }

    /* ==================== 辅助 ==================== */

    /** 构造「审批人解析结果为空」的审批节点（handlerIds 为空串 → 解析器返回空列表）与后继结束节点 */
    private void emptyNode(int no, String emptyHandlerMode) {
        fixture.node(no, "本节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.configOf("handlerIds", "", "approveMode", APPROVE_MODE_SINGLE,
                        "skipMode", SKIP_MODE_NONE, "emptyHandlerMode", emptyHandlerMode));
        fixture.node(no + 2, "结束节点", NODE_TYPE_END);
    }

    /** 上游终态审批节点（默认 FINISH） */
    private SysProcessExampleNode effectiveUpstream(int nodeNo, String handlerIds) {
        return effectiveUpstream(nodeNo, handlerIds, NODE_STATUS_FINISH);
    }

    /** 上游终态审批节点：status 为 FINISH（已通过）或 REJECT（已驳回），处理人按 handlerIds 逐人生成办结工作项 */
    private SysProcessExampleNode effectiveUpstream(int nodeNo, String handlerIds, String status) {
        SysProcessExampleNode node = fixture.node(nodeNo, "上游审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.configOf("handlerIds", handlerIds, "approveMode", APPROVE_MODE_SINGLE,
                        "skipMode", SKIP_MODE_NONE));
        node.setFieldStatus(status);
        for (String handlerId : handlerIds.split(";")) {
            if (StringUtil.isEmpty(handlerId)) {
                continue;
            }
            SysProcessExampleWorkitem workitem = new SysProcessExampleWorkitem();
            workitem.setFieldId("wi-" + nodeNo + "-" + handlerId);
            workitem.setFieldNodeId(node.getFieldId());
            workitem.setFieldHandlerId(handlerId.trim());
            workitem.setFieldExpectHandlerId(handlerId.trim());
            workitem.setFieldStatus(WORKITEM_STATUS_FINISH);
            fixture.workitems.add(workitem);
        }
        return node;
    }

    /* ==================== skipMode 四值 ==================== */

    @Test
    @DisplayName("skipMode=none：上游处理人相同也不跳过（不产生 AUTO_PASS 留痕）")
    void skipModeNoneNeverSkips() {
        effectiveUpstream(3, fixture.U1 + ";" + fixture.U2);
        fixture.node(4, "本节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.configOf("handlerIds", fixture.U1 + ";" + fixture.U2,
                        "approveMode", APPROVE_MODE_SINGLE, "skipMode", SKIP_MODE_NONE));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N4");
        fixture.line("N4", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N4");

        assertTrue(result.isWaiting(), "none 模式不跳过：本节点生成处理中工作项并停留");
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N4"));
        for (SysProcessExampleWorkitem workitem : fixture.workitemsOf("N4")) {
            assertEquals(WORKITEM_STATUS_PROCESSING, workitem.getFieldStatus());
        }
        assertEquals(0L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_AUTO_PASS));
    }

    @Test
    @DisplayName("skipMode=system 且公共配置允许自动跳过：与上一有效节点处理人相同则跳过")
    void skipModeSystemFollowsSystemSettingOn() {
        effectiveUpstream(3, fixture.U1 + ";" + fixture.U2);
        fixture.node(4, "本节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.configOf("handlerIds", fixture.U1 + ";" + fixture.U2,
                        "approveMode", APPROVE_MODE_SINGLE, "skipMode", SKIP_MODE_SYSTEM));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N4");
        fixture.line("N4", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N4");

        assertTrue(result.isFinished(), "全员被跳过：节点 PASS 后继续到结束节点");
        assertEquals(NODE_STATUS_PASS, fixture.statusOf("N4"));
        assertEquals(2, fixture.workitemsOf("N4").size());
        for (SysProcessExampleWorkitem workitem : fixture.workitemsOf("N4")) {
            assertEquals(WORKITEM_STATUS_FINISH, workitem.getFieldStatus());
        }
        assertEquals(2L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_AUTO_PASS));
    }

    @Test
    @DisplayName("skipMode=system 且公共配置否：不跳过（系统配置为 system 值的前置门禁）")
    void skipModeSystemFollowsSystemSettingOff() {
        when(fixture.settingService.getBooleanValue(eq(SETTING_TYPE_PROCESS), eq(SETTING_PROCESS_AUTO_HANDLE_PASS)))
                .thenReturn(false);
        effectiveUpstream(3, fixture.U1 + ";" + fixture.U2);
        fixture.node(4, "本节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.configOf("handlerIds", fixture.U1 + ";" + fixture.U2,
                        "approveMode", APPROVE_MODE_SINGLE, "skipMode", SKIP_MODE_SYSTEM));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N4");
        fixture.line("N4", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N4");

        assertTrue(result.isWaiting());
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N4"));
        assertEquals(0L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_AUTO_PASS));
    }

    @Test
    @DisplayName("skipMode=prev 不以系统配置为门禁：系统配置否时仍按上一有效节点处理人跳过")
    void skipModePrevIgnoresSystemSetting() {
        when(fixture.settingService.getBooleanValue(eq(SETTING_TYPE_PROCESS), eq(SETTING_PROCESS_AUTO_HANDLE_PASS)))
                .thenReturn(false);
        effectiveUpstream(3, fixture.U1);
        fixture.node(4, "本节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.configOf("handlerIds", fixture.U1, "approveMode", APPROVE_MODE_SINGLE,
                        "skipMode", SKIP_MODE_PREV));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N4");
        fixture.line("N4", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N4");

        assertTrue(result.isFinished());
        assertEquals(NODE_STATUS_PASS, fixture.statusOf("N4"));
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_AUTO_PASS));
    }

    @Test
    @DisplayName("skipMode=prev 只比对上一有效审批节点：非上一节点处理人不跳过")
    void skipModePrevComparesOnlyPreviousNode() {
        effectiveUpstream(3, fixture.U2);
        fixture.node(4, "本节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.configOf("handlerIds", fixture.U1, "approveMode", APPROVE_MODE_SINGLE,
                        "skipMode", SKIP_MODE_PREV));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N4");
        fixture.line("N4", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N4");

        assertTrue(result.isWaiting(), "U1 不在上一有效节点处理人中：本节点需实际审批");
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N4"));
        assertEquals(0L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_AUTO_PASS));
    }

    @Test
    @DisplayName("skipMode=any：本实例已审批人（非上一有效节点）相同时也跳过")
    void skipModeAnyComparesWholeInstance() {
        // N3 被驳回（终态 REJECT，处理人 U1）→ 有效节点；N4 已通过（处理人 U2）
        effectiveUpstream(3, fixture.U1, SysProcessConstants.NODE_STATUS_REJECT);
        effectiveUpstream(4, fixture.U2);
        fixture.node(5, "本节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.configOf("handlerIds", fixture.U1, "approveMode", APPROVE_MODE_SINGLE,
                        "skipMode", SKIP_MODE_ANY));
        fixture.node(6, "结束节点", NODE_TYPE_END);
        fixture.line("N4", "N5");
        fixture.line("N5", "N6");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N5");

        assertTrue(result.isFinished(), "U1 属全实例已审批人并集：跳过");
        assertEquals(NODE_STATUS_PASS, fixture.statusOf("N5"));
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_AUTO_PASS));
    }

    @Test
    @DisplayName("skipMode=any 与 prev 的差异：prev 不跳过、any 跳过（同一图两种配置）")
    void skipModeAnyVersusPrevDifference() {
        effectiveUpstream(3, fixture.U1, SysProcessConstants.NODE_STATUS_REJECT);
        effectiveUpstream(4, fixture.U2);
        fixture.node(5, "本节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.configOf("handlerIds", fixture.U1, "approveMode", APPROVE_MODE_SINGLE,
                        "skipMode", SKIP_MODE_PREV));
        fixture.node(6, "结束节点", NODE_TYPE_END);
        fixture.line("N4", "N5");
        fixture.line("N5", "N6");

        ProcessFlowResult prevResult = fixture.engine(null).enterFlow(fixture.example, "N5");

        assertTrue(prevResult.isWaiting(), "prev 只比对最近有效节点（U2）：U1 需实际审批");
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N5"));
        assertEquals(0L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_AUTO_PASS));
    }

    /* ==================== skipMode × 串行审批序位 ==================== */

    @Test
    @DisplayName("串行×skipMode=prev：首位处理人被跳过不占序位，唯一 PROCESSING 工作项属于第 2 位")
    void skipModePrevSerialFirstHandlerSkippedKeepsNextPosition() {
        // 上一有效节点处理人 {U1,U3}：本节点序位 U1(跳过) → U2(需审批) → U3(跳过)
        effectiveUpstream(3, fixture.U1 + ";" + fixture.U3);
        approveNode(4, "serial", fixture.U1 + ";" + fixture.U2 + ";" + fixture.U3, SKIP_MODE_PREV);
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N4");
        fixture.line("N4", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N4");

        assertTrue(result.isWaiting(), "第 2 位处理人需实际审批：节点停留审批中");
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N4"));
        assertEquals(1, processingWorkitemsOf("N4").size(), "被跳过者不占串行序位，仅生成 1 条 PROCESSING 工作项");
        assertEquals(fixture.U2, processingWorkitemsOf("N4").get(0).getFieldHandlerId(),
                "PROCESSING 工作项属于第 2 位处理人");
        assertEquals(2, fixture.workitemsOf("N4").size(),
                "跳过的 U1 建 1 条办结工作项，U2 建 1 条 PROCESSING；U3 待 U2 通过后按序位生成");
        assertEquals(fixture.U1, fixture.workitemsOf("N4").get(0).getFieldHandlerId());
        assertEquals(WORKITEM_STATUS_FINISH, fixture.workitemsOf("N4").get(0).getFieldStatus(),
                "被跳过者工作项直接办结");
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_AUTO_PASS));
    }

    @Test
    @DisplayName("串行×skipMode=none 反例：首位处理人占位，PROCESSING 工作项属于第 1 位")
    void skipModeNoneSerialFirstHandlerKeepsPosition() {
        effectiveUpstream(3, fixture.U1);
        approveNode(4, "serial", fixture.U1 + ";" + fixture.U2, SKIP_MODE_NONE);
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N4");
        fixture.line("N4", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N4");

        assertTrue(result.isWaiting());
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N4"));
        assertEquals(1, processingWorkitemsOf("N4").size());
        assertEquals(fixture.U1, processingWorkitemsOf("N4").get(0).getFieldHandlerId(),
                "none 模式首位处理人需实际审批");
        assertEquals(1, fixture.workitemsOf("N4").size(), "none 模式不建跳过办结工作项");
        assertEquals(0L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_AUTO_PASS));
    }

    @Test
    @DisplayName("串行跳过首位后：其办结工作项不阻塞序位，首位有效审批人通过即走完队列")
    void skipModePrevSerialAdvancesToSecondAfterFirstSkips() {
        effectiveUpstream(3, fixture.U1);
        approveNode(4, "serial", fixture.U1 + ";" + fixture.U2, SKIP_MODE_PREV);
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N4");
        fixture.line("N4", "N5");

        ProcessFlowResult entered = fixture.engine(null).enterFlow(fixture.example, "N4");

        assertTrue(entered.isWaiting());
        assertEquals(2, fixture.workitemsOf("N4").size(), "跳过的 U1 建办结工作项，U2 生成 PROCESSING");
        assertEquals(1, processingWorkitemsOf("N4").size());

        ProcessFlowResult passed = fixture.passNode("N4");

        assertTrue(passed.isFinished(), "串行队列走完：节点流出至结束节点");
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N4"));
        assertEquals(2, fixture.workitemsOf("N4").size(), "2 位处理人各 1 条工作项");
        assertTrue(processingWorkitemsOf("N4").isEmpty(), "全部工作项均已办结");
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_AUTO_PASS));
    }

    @Test
    @DisplayName("串行×skipMode=prev：全部处理人命中跳过，节点 PASS 沿出边继续且无 PROCESSING 工作项")
    void skipModePrevSerialAllSkippedPassesNode() {
        effectiveUpstream(3, fixture.U1 + ";" + fixture.U2);
        approveNode(4, "serial", fixture.U1 + ";" + fixture.U2, SKIP_MODE_PREV);
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N4");
        fixture.line("N4", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N4");

        assertTrue(result.isFinished(), "全员被跳过：节点 PASS 后继续流转到结束节点");
        assertEquals(NODE_STATUS_PASS, fixture.statusOf("N4"));
        assertEquals(2, fixture.workitemsOf("N4").size(), "跳过者仍建办结工作项");
        assertTrue(processingWorkitemsOf("N4").isEmpty(), "无 PROCESSING 工作项");
        assertEquals(2L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_AUTO_PASS));
    }

    /* ==================== 会签 × skipMode ==================== */

    @Test
    @DisplayName("会签×skipMode=prev：命中跳过者工作项 FINISH + AUTO_PASS 留痕，其余处理人 PROCESSING")
    void skipModePrevCountersignSkipsMatchedHandlers() {
        effectiveUpstream(3, fixture.U1 + ";" + fixture.U3);
        approveNode(4, "countersign", fixture.U1 + ";" + fixture.U2 + ";" + fixture.U3, SKIP_MODE_PREV);
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N4");
        fixture.line("N4", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N4");

        assertTrue(result.isWaiting(), "会签仍有处理中工作项：节点停留审批中");
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N4"));
        assertEquals(1, processingWorkitemsOf("N4").size(), "未命中跳过的 U2 生成 PROCESSING 工作项");
        assertEquals(fixture.U2, processingWorkitemsOf("N4").get(0).getFieldHandlerId());
        assertEquals(3, fixture.workitemsOf("N4").size(), "3 位处理人各 1 条工作项");

        SysProcessExampleWorkitem skipped = fixture.workitemsOf("N4").get(0);
        assertEquals(fixture.U1, skipped.getFieldHandlerId());
        assertEquals(WORKITEM_STATUS_FINISH, skipped.getFieldStatus(), "命中跳过者工作项直接办结");
        assertEquals(WORKITEM_STATUS_FINISH, fixture.workitemsOf("N4").get(2).getFieldStatus(),
                "另一位命中跳过者工作项同样直接办结");
        assertEquals(2L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_AUTO_PASS),
                "本节点跳过 2 人各 1 条 AUTO_PASS 留痕（含上一节点构造的 1 条）");
    }

    /* ==================== 连续跳过（autoContinueSkip） ==================== */

    @Test
    @DisplayName("autoContinueSkip=no：上一有效节点中被自动跳过的处理人在本节点需实际审批")
    void autoContinueSkipNoForcesRealApprove() {
        effectiveUpstream(3, fixture.U1 + ";" + fixture.U2);
        // N5 为上游之后的有效审批节点：U1 在其中被自动跳过（AUTO_PASS 留痕 + 办结工作项）
        SysProcessExampleNode skippedNode = fixture.node(5, "中间审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.configOf("handlerIds", fixture.U1 + ";" + fixture.U2,
                        "approveMode", "single", "skipMode", SKIP_MODE_PREV));
        skippedNode.setFieldStatus(SysProcessConstants.NODE_STATUS_FINISH);
        autoPassWorkitem(skippedNode, fixture.U1);
        approveNodeWithContinueSkip(6, "serial", fixture.U1 + ";" + fixture.U2, SKIP_MODE_PREV, "no");
        fixture.node(7, "结束节点", NODE_TYPE_END);
        fixture.line("N5", "N6");
        fixture.line("N6", "N7");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N6");

        assertTrue(result.isWaiting(), "no：U1 在本节点需实际审批，节点停留审批中");
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N6"));
        assertEquals(1, processingWorkitemsOf("N6").size());
        assertEquals(fixture.U1, processingWorkitemsOf("N6").get(0).getFieldHandlerId());
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_AUTO_PASS),
                "本节点不再自动跳过：AUTO_PASS 留痕仅为构造的上一节点留痕");
        assertEquals(1, fixture.workitemsOf("N6").size(), "本节点未生成跳过者办结工作项");
    }

    @Test
    @DisplayName("autoContinueSkip=yes：上一有效节点中被自动跳过的处理人在本节点继续被跳过（AUTO_PASS 留痕 +1）")
    void autoContinueSkipYesKeepsSkipping() {
        effectiveUpstream(3, fixture.U1 + ";" + fixture.U2);
        SysProcessExampleNode skippedNode = fixture.node(5, "中间审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.configOf("handlerIds", fixture.U1 + ";" + fixture.U2,
                        "approveMode", "single", "skipMode", SKIP_MODE_PREV));
        skippedNode.setFieldStatus(SysProcessConstants.NODE_STATUS_FINISH);
        autoPassWorkitem(skippedNode, fixture.U1);
        approveNodeWithContinueSkip(6, "serial", fixture.U1, SKIP_MODE_PREV, "yes");
        fixture.node(7, "结束节点", NODE_TYPE_END);
        fixture.line("N5", "N6");
        fixture.line("N6", "N7");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N6");

        assertTrue(result.isFinished(), "yes：唯一处理人继续被跳过，节点 PASS 后继续到结束节点");
        assertEquals(NODE_STATUS_PASS, fixture.statusOf("N6"));
        assertTrue(processingWorkitemsOf("N6").isEmpty(), "无 PROCESSING 工作项");
        assertEquals(1, fixture.workitemsOf("N6").size());
        assertEquals(WORKITEM_STATUS_FINISH, fixture.workitemsOf("N6").get(0).getFieldStatus());
        assertEquals(2L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_AUTO_PASS),
                "上一节点留痕 1 条 + 本节点继续跳过新增 1 条");
    }

    @Test
    @DisplayName("autoContinueSkip=system 且公共配置 auto_continue_pass=false：该处理人在本节点需实际审批")
    void autoContinueSkipSystemFollowsSettingOff() {
        when(fixture.settingService.getBooleanValue(eq(SETTING_TYPE_PROCESS),
                eq(SysProcessConstants.SETTING_PROCESS_AUTO_CONTINUE_PASS))).thenReturn(false);
        effectiveUpstream(3, fixture.U1 + ";" + fixture.U2);
        SysProcessExampleNode skippedNode = fixture.node(5, "中间审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.configOf("handlerIds", fixture.U1 + ";" + fixture.U2,
                        "approveMode", "single", "skipMode", SKIP_MODE_PREV));
        skippedNode.setFieldStatus(SysProcessConstants.NODE_STATUS_FINISH);
        autoPassWorkitem(skippedNode, fixture.U1);
        approveNodeWithContinueSkip(6, "serial", fixture.U1, SKIP_MODE_PREV, "system");
        fixture.node(7, "结束节点", NODE_TYPE_END);
        fixture.line("N5", "N6");
        fixture.line("N6", "N7");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N6");

        assertTrue(result.isWaiting(), "system 跟随公共配置 false：需实际审批");
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N6"));
        assertEquals(1, processingWorkitemsOf("N6").size());
        assertEquals(fixture.U1, processingWorkitemsOf("N6").get(0).getFieldHandlerId());
        assertEquals(1L, fixture.auditCount(SysProcessConstants.AUDIT_TYPE_AUTO_PASS),
                "本节点不再自动跳过：AUTO_PASS 留痕仅为构造的上一节点留痕");
    }

    /* ==================== 辅助：跳过后新增 ==================== */

    /** 审批节点配置（skipMode 按参数指定，autoContinueSkip 缺省 system） */
    private void approveNode(int no, String approveMode, String handlerIds, String skipMode) {
        fixture.node(no, "本节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.configOf("handlerIds", handlerIds, "approveMode", approveMode,
                        "skipMode", skipMode));
    }

    /**
     * 审批节点配置（显式指定 autoContinueSkip）：用于「上一有效节点被自动跳过的处理人
     * 在本节点是否继续跳过」的二次判定。
     */
    private void approveNodeWithContinueSkip(int no, String approveMode, String handlerIds, String skipMode,
                                             String autoContinueSkip) {
        fixture.node(no, "本节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.configOf("handlerIds", handlerIds, "approveMode", approveMode,
                        "skipMode", skipMode, "autoContinueSkip", autoContinueSkip));
    }

    /**
     * 把指定节点的处理人标记为「上一有效节点中被自动跳过」：建 1 条办结工作项 +
     * 关联该工作项 id 的 AUTO_PASS 留痕（引擎以此反查 prev.skipped）。
     */
    private void autoPassWorkitem(SysProcessExampleNode node, String handlerId) {
        SysProcessExampleWorkitem workitem = new SysProcessExampleWorkitem();
        workitem.initFieldId();
        workitem.setFieldExampleId(FlowEngineFixture.EXAMPLE_ID);
        workitem.setFieldNodeId(node.getFieldId());
        workitem.setFieldHandlerId(handlerId);
        workitem.setFieldExpectHandlerId(handlerId);
        workitem.setFieldStatus(WORKITEM_STATUS_FINISH);
        fixture.workitems.add(workitem);

        SysProcessAuditNote note = new SysProcessAuditNote();
        note.setFieldId("audit-auto-" + workitem.getFieldId());
        note.setFieldExampleId(FlowEngineFixture.EXAMPLE_ID);
        note.setFieldWorkitemId(workitem.getFieldId());
        note.setFieldHandlerId(handlerId);
        note.setFieldHandleType(SysProcessConstants.AUDIT_TYPE_AUTO_PASS);
        note.setFieldContent("自动跳过重复审批人");
        note.setFieldCreateTime(new java.util.Date());
        fixture.auditNotes.add(note);
    }

    /** 节点内处理中（PROCESSING）工作项列表 */
    private List<SysProcessExampleWorkitem> processingWorkitemsOf(String nodeKey) {
        List<SysProcessExampleWorkitem> matched = new java.util.ArrayList<>();
        for (SysProcessExampleWorkitem workitem : fixture.workitemsOf(nodeKey)) {
            if (WORKITEM_STATUS_PROCESSING.equals(workitem.getFieldStatus())) {
                matched.add(workitem);
            }
        }
        return matched;
    }
}
