package com.eric.base.sys.process.service.engine;

import com.eric.base.sys.notify.enums.SysNotifyType;
import com.eric.base.sys.notify.model.SysNotifyTodo;
import com.eric.base.sys.org.model.SysOrgUser;
import com.eric.base.sys.process.model.SysProcessTemplate;
import com.eric.base.sys.process.properties.SysProcessConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_APPROVE;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_END;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程引擎通知收口单测（P17-BE-014）
 *
 * @author Eric
 * @date 2026/11/20
 * @description P17-BE-014 交付物：验证流程引擎统一发送审批待办（TODO，标题消费
 *              {@code notify_handle_subject}）与办结待阅（READ，标题消费
 *              {@code notify_finish_drafter_subject} / {@code notify_finish_handler_subject}，
 *              受公共配置 {@code finish_notify_handler} 控制），以及
 *              {@link ProcessNotifySender} 四个标题模板的占位符渲染与缺省回退。
 *              触发点口径（docs/设计文档/sys/process/流程核心业务.md「通知」章节「实现补充（P17-BE-014）」）：
 *              <ul>
 *              <li>审批待办：节点生成「实际需审批（PROCESSING）」工作项后发送——收件人=本次生成的
 *                  PROCESSING 工作项处理人（自动跳过者工作项为 FINISH，不在收件人），单签/会签/
 *                  串行各一次合并发送不重复；串行后续序位生成时再次发送（收件人=下一人）；</li>
 *              <li>办结待阅：进入 END 节点办结（FINISH）时发送——起草人待阅始终发送，
 *                  审批人待阅受 finish_notify_handler 控制（false 不发）；ABANDON 终态
 *                  （起草人废弃/管理员作废走 Service {@code moveToEndNode}，不经引擎 enterEnd）
 *                  不发办结待阅（Service 侧用例覆盖）。</li>
 *              </ul>
 */
class ProcessFlowEngineNotifyTest {

    /** 审批待办固定内容口径（等价口径，见 ProcessNotifySender#sendApproveTodo） */
    private static final String APPROVE_TODO_CONTENT = "您有流程审批待办，请及时处理";

    /** 办结待阅固定内容口径 */
    private static final String FINISH_READ_CONTENT = "流程已办结";

    private FlowEngineFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new FlowEngineFixture();
    }

    /* ==================== 审批待办（TODO） ==================== */

    @Test
    @DisplayName("审批节点进入：PROCESSING 工作项生成后发送审批待办，起草人自动跳过者不在收件人")
    void approveTodoSentToProcessingHandlersOnNodeEntry() {
        stubUser(FlowEngineFixture.CREATOR, "起草人");
        stubTemplate("采购订单审批模板");
        // 审批人 = 起草人;U1：起草人命中 auto_handle_pass_drafter 自动跳过（工作项 FINISH），U1 实际审批
        fixture.node(3, "审批节点", NODE_TYPE_APPROVE, skipSystemConfig(
                FlowEngineFixture.CREATOR + ";" + FlowEngineFixture.U1));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isWaiting());
        assertEquals(1, fixture.workitemsOf("N3").stream()
                .filter(w -> SysProcessConstants.WORKITEM_STATUS_PROCESSING.equals(w.getFieldStatus()))
                .count(), "起草人应被自动跳过，仅 U1 持有 PROCESSING 工作项");
        // 审批待办（TODO）：标题 = notify_handle_subject 默认模板渲染、内容 = 固定文案、
        // 收件人 = 本次 PROCESSING 工作项处理人（U1，自动跳过的起草人不在收件人）
        verify(fixture.notifyTodoService).send(argThat(todo ->
                        SysNotifyType.TODO.getValue().equals(todo.getFieldNotifyType())
                                && "请审批起草人提交的流程：采购订单审批模板".equals(todo.getFieldName())
                                && APPROVE_TODO_CONTENT.equals(todo.getFieldContent())
                                && SysProcessConstants.MODEL_KEY_SYS_PROCESS.equals(todo.getFieldModelName())
                                && FlowEngineFixture.EXAMPLE_ID.equals(todo.getFieldModelId())
                                && ("/processExample/detail/" + FlowEngineFixture.EXAMPLE_ID)
                                .equals(todo.getFieldUrl())),
                argThat(ids -> ids.size() == 1 && ids.get(0).equals(FlowEngineFixture.U1)));
        // 单次节点进入只发送一次（同一工作项只发一次）
        verify(fixture.notifyTodoService, times(1)).send(any(SysNotifyTodo.class), anyList());
    }

    @Test
    @DisplayName("会签节点进入：两个 PROCESSING 工作项合并为一次审批待办发送（收件人=两人）")
    void approveTodoSentOncePerCountersignEntry() {
        stubTemplate("采购订单审批模板");
        fixture.node(3, "会签节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("countersign", FlowEngineFixture.U1 + ";" + FlowEngineFixture.U2));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");

        ProcessFlowResult result = fixture.engine(null).enterFlow(fixture.example, "N3");

        assertTrue(result.isWaiting());
        // 会签两人各生成 PROCESSING 工作项，审批待办合并为一次发送（同一节点进入只发一次）
        verify(fixture.notifyTodoService, times(1)).send(any(SysNotifyTodo.class),
                argThat(ids -> ids.size() == 2 && ids.get(0).equals(FlowEngineFixture.U1)
                        && ids.get(1).equals(FlowEngineFixture.U2)));
    }

    @Test
    @DisplayName("串行节点：首序位与后续序位各发送一次审批待办（收件人=当序处理人，不重复）")
    void serialApproveSendsTodoPerPositionWithoutDuplication() {
        stubTemplate("采购订单审批模板");
        fixture.node(3, "串行节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("serial", FlowEngineFixture.U1 + ";" + FlowEngineFixture.U2));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");

        fixture.engine(null).enterFlow(fixture.example, "N3");
        // 串行首序位：仅 U1 生成 PROCESSING 工作项 → 审批待办收件人 [U1]
        verify(fixture.notifyTodoService, times(1)).send(any(SysNotifyTodo.class), anyList());
        verify(fixture.notifyTodoService).send(any(SysNotifyTodo.class),
                argThat(ids -> ids.size() == 1 && ids.get(0).equals(FlowEngineFixture.U1)));

        fixture.passNode("N3");
        // 串行后续序位：U1 通过后生成 U2 工作项 → 再次发送，收件人 [U2]（不与首序位重复）
        verify(fixture.notifyTodoService, times(2)).send(any(SysNotifyTodo.class), anyList());
        verify(fixture.notifyTodoService).send(any(SysNotifyTodo.class),
                argThat(ids -> ids.size() == 1 && ids.get(0).equals(FlowEngineFixture.U2)));
    }

    /* ==================== 办结待阅（READ） ==================== */

    @Test
    @DisplayName("引擎办结（enterEnd）：起草人待阅 + 审批人待阅（收件人=HANDLER_PASS 留痕关联处理人）")
    void finishReadsSentToDrafterAndHandlersOnEnterEnd() {
        stubUser(FlowEngineFixture.CREATOR, "起草人");
        stubTemplate("采购订单审批模板");
        fixture.node(3, "审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U1));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");

        fixture.engine(null).enterFlow(fixture.example, "N3");
        // U1 通过（HANDLER_PASS 留痕）→ 引擎沿出边进入 END 办结
        fixture.passNode("N3");

        // 起草人待阅（READ）：标题 = notify_finish_drafter_subject 默认模板渲染，收件人 = 起草人
        verify(fixture.notifyTodoService).send(argThat(todo ->
                        SysNotifyType.READ.getValue().equals(todo.getFieldNotifyType())
                                && "您提交的流程采购订单审批模板已通过".equals(todo.getFieldName())
                                && FINISH_READ_CONTENT.equals(todo.getFieldContent())
                                && SysProcessConstants.MODEL_KEY_SYS_PROCESS.equals(todo.getFieldModelName())
                                && ("/processExample/detail/" + FlowEngineFixture.EXAMPLE_ID)
                                .equals(todo.getFieldUrl())),
                argThat(ids -> ids.size() == 1 && ids.get(0).equals(FlowEngineFixture.CREATOR)));
        // 审批人待阅（READ）：finish_notify_handler 缺省 true → 收件人 = 实际审批过的用户 [U1]
        verify(fixture.notifyTodoService).send(argThat(todo ->
                        SysNotifyType.READ.getValue().equals(todo.getFieldNotifyType())
                                && "您审批的流程采购订单审批模板已通过".equals(todo.getFieldName())),
                argThat(ids -> ids.size() == 1 && ids.get(0).equals(FlowEngineFixture.U1)));
        // 审批待办 1 + 办结待阅 2
        verify(fixture.notifyTodoService, times(3)).send(any(SysNotifyTodo.class), anyList());
    }

    @Test
    @DisplayName("finish_notify_handler=false：仅发送起草人待阅，不发审批人待阅")
    void finishHandlerReadSkippedWhenSettingDisabled() {
        stubUser(FlowEngineFixture.CREATOR, "起草人");
        stubTemplate("采购订单审批模板");
        when(fixture.settingService.getBooleanValue(eq(SysProcessConstants.SETTING_TYPE_PROCESS),
                eq(SysProcessConstants.SETTING_PROCESS_FINISH_NOTIFY_HANDLER))).thenReturn(false);
        fixture.node(3, "审批节点", NODE_TYPE_APPROVE,
                FlowEngineFixture.approveConfig("single", FlowEngineFixture.U1));
        fixture.node(5, "结束节点", NODE_TYPE_END);
        fixture.line("N3", "N5");

        fixture.engine(null).enterFlow(fixture.example, "N3");
        fixture.passNode("N3");

        // finish_notify_handler=false：审批待办 1 + 起草人待阅 1，不发审批人待阅
        verify(fixture.notifyTodoService, times(2)).send(any(SysNotifyTodo.class), anyList());
        verify(fixture.notifyTodoService).send(argThat(todo ->
                        SysNotifyType.READ.getValue().equals(todo.getFieldNotifyType())
                                && "您提交的流程采购订单审批模板已通过".equals(todo.getFieldName())),
                argThat(ids -> ids.size() == 1 && ids.get(0).equals(FlowEngineFixture.CREATOR)));
        verify(fixture.notifyTodoService, never()).send(argThat(todo ->
                "您审批的流程采购订单审批模板已通过".equals(todo.getFieldName())), anyList());
    }

    /* ==================== 标题模板渲染（4 个既有模板消费） ==================== */

    @Test
    @DisplayName("标题渲染：既有两个占位符行为不变，新增 ${fieldHandlerName} 支持传入驳回人")
    void renderNotifySubjectSupportsHandlerPlaceholderAndKeepsExistingBehavior() {
        stubUser(FlowEngineFixture.CREATOR, "起草人");
        stubTemplate("采购订单审批模板");
        // 既有两个占位符（${fieldCreatorName} / ${fieldName}）行为不变（2 参重载）
        assertEquals("A起草人B采购订单审批模板C", fixture.notifySender.renderNotifySubject(
                "A${fieldCreatorName}B${fieldName}C", fixture.example));
        // 新增 ${fieldHandlerName}（3 参重载）：传入驳回人姓名
        assertEquals("张三驳回了您的采购订单审批模板", fixture.notifySender.renderNotifySubject(
                "${fieldHandlerName}驳回了您的${fieldName}", fixture.example, "张三"));
        // 未传入驳回人姓名（null）：占位符渲染为空串
        assertEquals("您提交的流程被驳回，请修改", fixture.notifySender.renderNotifySubject(
                SysProcessConstants.DEFAULT_NOTIFY_REFUSE_SUBJECT, fixture.example, null));
    }

    @Test
    @DisplayName("被驳回待办：配置模板按 ${fieldHandlerName} 渲染，TODO 类型 + 驳回意见内容")
    void refuseTodoRendersConfiguredSubjectWithHandlerName() {
        stubUser(FlowEngineFixture.CREATOR, "起草人");
        stubTemplate("采购订单审批模板");
        when(fixture.settingService.getValue(eq(SysProcessConstants.SETTING_TYPE_NOTIFY),
                eq(SysProcessConstants.SETTING_NOTIFY_REFUSE_SUBJECT)))
                .thenReturn("${fieldHandlerName}驳回了您的${fieldName}");

        fixture.notifySender.sendRefuseTodo(fixture.example, "张三", "金额不对",
                List.of(FlowEngineFixture.CREATOR));

        verify(fixture.notifyTodoService).send(argThat(todo ->
                        SysNotifyType.TODO.getValue().equals(todo.getFieldNotifyType())
                                && "张三驳回了您的采购订单审批模板".equals(todo.getFieldName())
                                && "金额不对".equals(todo.getFieldContent())
                                && SysProcessConstants.MODEL_KEY_SYS_PROCESS.equals(todo.getFieldModelName())
                                && ("/processExample/detail/" + FlowEngineFixture.EXAMPLE_ID)
                                .equals(todo.getFieldUrl())),
                argThat(ids -> ids.size() == 1 && ids.get(0).equals(FlowEngineFixture.CREATOR)));
    }

    @Test
    @DisplayName("被驳回待办：配置缺失回退默认模板（未携带驳回人姓名时占位符渲染为空串）")
    void refuseTodoFallsBackToDefaultSubjectWhenConfigMissing() {
        // notify_refuse_subject 未配置（mock 未打桩返回 null）→ 回退默认模板；
        // 3 参入口（未携带驳回人姓名）${fieldHandlerName} 渲染为空串
        fixture.notifySender.sendRefuseTodo(fixture.example, "请修改后重新提交",
                List.of(FlowEngineFixture.CREATOR));

        verify(fixture.notifyTodoService).send(argThat(todo ->
                        SysNotifyType.TODO.getValue().equals(todo.getFieldNotifyType())
                                && "您提交的流程被驳回，请修改".equals(todo.getFieldName())
                                && "请修改后重新提交".equals(todo.getFieldContent())),
                anyList());
    }

    @Test
    @DisplayName("审批待办与办结待阅：配置缺失回退 4 个默认标题模板")
    void approveAndFinishSubjectsFallBackToDefaultsWhenConfigMissing() {
        stubUser(FlowEngineFixture.CREATOR, "起草人");
        stubTemplate("采购订单审批模板");

        fixture.notifySender.sendApproveTodo(fixture.example, List.of(FlowEngineFixture.U1));
        fixture.notifySender.sendFinishDrafterRead(fixture.example, List.of(FlowEngineFixture.CREATOR));
        fixture.notifySender.sendFinishHandlerRead(fixture.example, List.of(FlowEngineFixture.U1));

        // notify_handle_subject 缺省回退 + 审批待办固定内容
        verify(fixture.notifyTodoService).send(argThat(todo ->
                        SysNotifyType.TODO.getValue().equals(todo.getFieldNotifyType())
                                && "请审批起草人提交的流程：采购订单审批模板".equals(todo.getFieldName())
                                && APPROVE_TODO_CONTENT.equals(todo.getFieldContent())),
                anyList());
        // notify_finish_drafter_subject 缺省回退 + 办结固定内容
        verify(fixture.notifyTodoService).send(argThat(todo ->
                        SysNotifyType.READ.getValue().equals(todo.getFieldNotifyType())
                                && "您提交的流程采购订单审批模板已通过".equals(todo.getFieldName())
                                && FINISH_READ_CONTENT.equals(todo.getFieldContent())),
                anyList());
        // notify_finish_handler_subject 缺省回退
        verify(fixture.notifyTodoService).send(argThat(todo ->
                        SysNotifyType.READ.getValue().equals(todo.getFieldNotifyType())
                                && "您审批的流程采购订单审批模板已通过".equals(todo.getFieldName())),
                anyList());
        verify(fixture.notifyTodoService, times(3)).send(any(SysNotifyTodo.class), anyList());
    }

    @Test
    @DisplayName("收件人为空：审批待办/办结待阅静默不发送")
    void notifySenderSkipsEmptyRecipients() {
        fixture.notifySender.sendApproveTodo(fixture.example, List.of());
        fixture.notifySender.sendFinishDrafterRead(fixture.example, List.of());
        fixture.notifySender.sendFinishHandlerRead(fixture.example, List.of());

        verify(fixture.notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
    }

    /* ==================== 桩辅助 ==================== */

    /** 审批节点配置：skipMode=system（起草人自动跳过启用）+ 单签 */
    private static String skipSystemConfig(String handlerIds) {
        return "{\"handlerIds\":\"" + handlerIds + "\",\"approveMode\":\"single\","
                + "\"autoSkip\":\"system\",\"autoContinueSkip\":\"system\"}";
    }

    private void stubUser(String id, String name) {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(id);
        user.setFieldName(name);
        when(fixture.userMapper.selectById(id)).thenReturn(user);
    }

    private void stubTemplate(String name) {
        SysProcessTemplate template = new SysProcessTemplate();
        template.setFieldId(FlowEngineFixture.TEMPLATE_ID);
        template.setFieldName(name);
        when(fixture.templateDao.selectById(FlowEngineFixture.TEMPLATE_ID)).thenReturn(template);
    }
}
