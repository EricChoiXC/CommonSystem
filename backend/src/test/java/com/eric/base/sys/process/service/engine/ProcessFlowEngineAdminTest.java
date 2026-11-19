package com.eric.base.sys.process.service.engine;

import com.eric.base.sys.process.model.SysProcessExampleWorkitem;
import com.eric.base.sys.process.properties.SysProcessConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_APPROVE;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_DRAFT;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_END;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_MANUAL;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_START;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_PROCESSING;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_WAITING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程流转引擎单测（四）：管理员跳转的图模型语义
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-007 交付物（依据 docs/v1_7-流程管理/功能实现方案-改造分析与方案设计.md
 *              §3.4-6 与 docs/设计文档/sys/process/流程核心业务.md「管理员特权操作」）：
 *              管理员跳转 = 废弃在途工作项后从目标节点进入引擎；目标节点扩展为
 *              审批节点 / 起草节点 / 人工决策节点三类，三者在图模型下语义分别为
 *              「重新解析审批人并生成工作项继续流转」「停留起草人重新提交（实例 RETURN）」
 *              「按人工指定流向或默认流向流出」。
 */
class ProcessFlowEngineAdminTest {

    private FlowEngineFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new FlowEngineFixture();
    }

    /** 公共图：START(N1)→DRAFT(N2)→审批(N3)→目标(N4)→END(N5) */
    private void buildGraph() {
        fixture.node(1, "开始节点", NODE_TYPE_START);
        fixture.node(2, "草稿节点", NODE_TYPE_DRAFT);
        fixture.node(3, "审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U1));
        fixture.line("N1", "N2");
        fixture.line("N2", "N3");
    }

    @Test
    @DisplayName("管理员跳转到起草节点：节点 PROCESSING + 实例 RETURN，停留等待重新提交")
    void adminJumpToDraftNodeStaysForResubmit() {
        buildGraph();
        fixture.example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        fixture.example.setFieldCreatorId(FlowEngineFixture.U1);

        ProcessFlowResult result = fixture.engine(null).adminJump(fixture.example, fixture.nodeId(2));

        assertTrue(result.isWaiting());
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N2"));
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_RETURN, fixture.example.getFieldStatus());
        assertEquals(0, fixture.workitems.size(), "起草节点不生成工作项");
    }

    @Test
    @DisplayName("管理员跳转到审批节点：从目标节点进入引擎并生成工作项")
    void adminJumpToApproveNodeEntersFlowFromTarget() {
        buildGraph();
        fixture.node(4, "目标审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U1));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N4");
        fixture.line("N4", "N5");
        fixture.example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);

        ProcessFlowResult result = fixture.engine(null).adminJump(fixture.example, fixture.nodeId(4));

        assertTrue(result.isWaiting());
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N4"));
        assertEquals(NODE_STATUS_WAITING, fixture.statusOf("N3"), "跳转目标之外的审批节点保持等待");
        assertEquals(1, fixture.workitemsOf("N4").size(), "目标审批节点按配置创建处理中工作项");
        assertEquals(SysProcessConstants.WORKITEM_STATUS_PROCESSING,
                fixture.workitemsOf("N4").get(0).getFieldStatus());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PROCESSING, fixture.example.getFieldStatus());
    }

    @Test
    @DisplayName("管理员跳转到人工决策节点：按节点默认流向流出并办结")
    void adminJumpToManualNodeFlowsByDefaultFlowKey() {
        buildGraph();
        fixture.node(4, "人工决策节点", NODE_TYPE_MANUAL, FlowEngineFixture.manualConfig("N5"));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N4");
        fixture.line("N4", "N5", SysProcessConstants.LINE_TYPE_MANUAL, null, false, 1);
        fixture.example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);

        ProcessFlowResult result = fixture.engine(null).adminJump(fixture.example, fixture.nodeId(4));

        assertTrue(result.isFinished(), "人工决策节点按默认流向 N5 流出后办结");
        assertEquals(NODE_STATUS_FINISH, fixture.statusOf("N4"));
        assertEquals(NODE_STATUS_PROCESSING, fixture.statusOf("N5"));
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_FINISH, fixture.example.getFieldStatus());
    }

    @Test
    @DisplayName("管理员跳转前：引擎只做图流转，废弃在途工作项由 Service 层编排")
    void adminJumpDoesNotTouchWorkitemsItself() {
        buildGraph();
        fixture.node(4, "目标审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U1));
        fixture.line("N3", "N4");
        SysProcessExampleWorkitem stuck = new SysProcessExampleWorkitem();
        stuck.initFieldId();
        stuck.setFieldExampleId(FlowEngineFixture.EXAMPLE_ID);
        stuck.setFieldNodeId(fixture.nodeId(3));
        stuck.setFieldHandlerId(FlowEngineFixture.U1);
        stuck.setFieldExpectHandlerId(FlowEngineFixture.U1);
        stuck.setFieldStatus(SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        fixture.workitems.add(stuck);
        fixture.example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);

        fixture.engine(null).adminJump(fixture.example, fixture.nodeId(4));

        // 在途工作项废弃与当前节点转等待由 SysProcessExampleService#adminJump 编排（见服务层用例）
        assertEquals(SysProcessConstants.WORKITEM_STATUS_PROCESSING, stuck.getFieldStatus());
        assertEquals(1, fixture.workitemsOf("N4").size(), "目标节点仍按图进入引擎");
    }
}
