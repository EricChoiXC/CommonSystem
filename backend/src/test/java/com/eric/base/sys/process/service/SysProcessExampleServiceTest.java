package com.eric.base.sys.process.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.setting.service.CommonSettingService;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.mapper.SysOrgUserMapper;
import com.eric.base.sys.org.model.SysOrgUser;
import com.eric.base.sys.process.dao.SysProcessAuditNoteDao;
import com.eric.base.sys.process.dao.SysProcessExampleDao;
import com.eric.base.sys.process.dao.SysProcessExampleNodeDao;
import com.eric.base.sys.process.dao.SysProcessExampleWorkitemDao;
import com.eric.base.sys.process.dao.SysProcessTemplateDao;
import com.eric.base.sys.process.dao.SysProcessTemplateNodeDao;
import com.eric.base.sys.process.model.SysProcessAuditNote;
import com.eric.base.sys.process.model.SysProcessExample;
import com.eric.base.sys.process.model.SysProcessExampleNode;
import com.eric.base.sys.process.model.SysProcessExampleWorkitem;
import com.eric.base.sys.process.model.SysProcessTemplate;
import com.eric.base.sys.process.model.SysProcessTemplateNode;
import com.eric.base.sys.process.properties.SysProcessConstants;
import com.eric.base.sys.process.request.SysProcessAdminOperateRequest;
import com.eric.base.sys.process.resolver.ProcessResolver;
import com.eric.base.sys.process.response.SysProcessExampleDetailVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程实例业务服务测试
 *
 * @author Eric
 * @date 2026/8/23
 * @description 覆盖流程流转引擎核心规则（依据 artificial/docs/设计文档/sys/process/流程核心业务.md「流程实例和流转」）：
 *              创建草稿快照、提交进入审批节点并创建工作项、单签/会审通过、驳回与重新提交（重新流转/转回本节点）、
 *              自动跳过重复审批人（含是否连续跳过）、起草人收回/废弃、管理员跳转/暂停/恢复/作废/办结、
 *              以及越权/非法状态拦截。
 */
class SysProcessExampleServiceTest {

    private static final String EXAMPLE_ID = "12345678901234567890123456789001";
    private static final String CREATOR_ID = "12345678901234567890123456789002";
    private static final String U1 = "12345678901234567890123456789003";
    private static final String U2 = "12345678901234567890123456789004";
    private static final String TEMPLATE_ID = "12345678901234567890123456789005";
    private static final String NODE3_ID = "12345678901234567890123456789010";
    private static final String NODE4_ID = "12345678901234567890123456789011";
    private static final String NODE5_ID = "12345678901234567890123456789012";
    private static final String WI_U1 = "12345678901234567890123456789020";
    private static final String WI_U2 = "12345678901234567890123456789021";

    private static final String CONFIG_SINGLE =
            "{\"handlerIds\":\"" + U1 + ";" + U2 + "\",\"handlerNames\":\"张三;李四\","
                    + "\"approveMode\":\"single\",\"triggerType\":\"fixed\","
                    + "\"autoSkip\":\"system\",\"autoContinueSkip\":\"system\"}";
    private static final String CONFIG_COUNTERSIGN =
            "{\"handlerIds\":\"" + U1 + ";" + U2 + "\",\"handlerNames\":\"张三;李四\","
                    + "\"approveMode\":\"countersign\",\"triggerType\":\"fixed\","
                    + "\"autoSkip\":\"system\",\"autoContinueSkip\":\"system\"}";
    private static final String CONFIG_NO_CONTINUE =
            "{\"handlerIds\":\"" + U1 + ";" + U2 + "\",\"handlerNames\":\"张三;李四\","
                    + "\"approveMode\":\"single\",\"triggerType\":\"fixed\","
                    + "\"autoSkip\":\"system\",\"autoContinueSkip\":\"no\"}";
    private static final String CONFIG_FIELD =
            "{\"handlerSource\":\"field\",\"handlerField\":\"fieldManagerId\","
                    + "\"approveMode\":\"single\",\"triggerType\":\"fixed\","
                    + "\"autoSkip\":\"system\",\"autoContinueSkip\":\"system\"}";

    private SysProcessExampleDao exampleDao;
    private SysProcessExampleNodeDao nodeDao;
    private SysProcessExampleWorkitemDao workitemDao;
    private SysProcessAuditNoteDao auditNoteDao;
    private SysProcessTemplateDao templateDao;
    private SysProcessTemplateNodeDao templateNodeDao;
    private CommonSettingService settingService;
    private SysOrgUserMapper userMapper;
    private ProcessResolver resolver;
    private SysProcessDocumentFieldService documentFieldService;
    private SysProcessExampleService service;
    private MockedStatic<UserUtil> userUtil;

    private SysProcessTemplate template;
    private SysProcessExample example;
    private List<SysProcessExampleNode> exampleNodes;

    @BeforeEach
    void setUp() {
        exampleDao = mock(SysProcessExampleDao.class);
        nodeDao = mock(SysProcessExampleNodeDao.class);
        workitemDao = mock(SysProcessExampleWorkitemDao.class);
        auditNoteDao = mock(SysProcessAuditNoteDao.class);
        templateDao = mock(SysProcessTemplateDao.class);
        templateNodeDao = mock(SysProcessTemplateNodeDao.class);
        settingService = mock(CommonSettingService.class);
        userMapper = mock(SysOrgUserMapper.class);
        resolver = mock(ProcessResolver.class);
        documentFieldService = mock(SysProcessDocumentFieldService.class);
        service = new SysProcessExampleService(exampleDao, nodeDao, workitemDao, auditNoteDao,
                templateDao, templateNodeDao, settingService, userMapper, resolver, documentFieldService,
                new ObjectMapper());

        // 默认公共配置：自动跳过/包含起草人/连续跳过均开启
        when(settingService.getBooleanValue(eq(SysProcessConstants.SETTING_TYPE_PROCESS),
                eq(SysProcessConstants.SETTING_PROCESS_AUTO_HANDLE_PASS))).thenReturn(true);
        when(settingService.getBooleanValue(eq(SysProcessConstants.SETTING_TYPE_PROCESS),
                eq(SysProcessConstants.SETTING_PROCESS_AUTO_HANDLE_PASS_DRAFTER))).thenReturn(true);
        when(settingService.getBooleanValue(eq(SysProcessConstants.SETTING_TYPE_PROCESS),
                eq(SysProcessConstants.SETTING_PROCESS_AUTO_CONTINUE_PASS))).thenReturn(true);

        // 默认：未知用户返回 null（必须先注册，具体用户匹配在后生效）
        when(userMapper.selectById(anyString())).thenReturn(null);
        when(userMapper.selectById(CREATOR_ID)).thenReturn(user(CREATOR_ID, "起草人"));
        when(userMapper.selectById(U1)).thenReturn(user(U1, "张三"));
        when(userMapper.selectById(U2)).thenReturn(user(U2, "李四"));

        template = template();
        when(templateDao.selectById(TEMPLATE_ID)).thenReturn(template);

        example = example(SysProcessConstants.EXAMPLE_STATUS_DRAFT);
        when(exampleDao.selectById(EXAMPLE_ID)).thenReturn(example);

        exampleNodes = new ArrayList<>(List.of(
                exampleNode(NODE3_ID, 1, "START", "START", SysProcessConstants.NODE_STATUS_FINISH, null),
                exampleNode("12345678901234567890123456789031", 2, "DRAFT", "DRAFT",
                        SysProcessConstants.NODE_STATUS_FINISH, null),
                exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE", SysProcessConstants.NODE_STATUS_WAITING, CONFIG_SINGLE),
                exampleNode(NODE4_ID, 4, "审批节点4", "APPROVE", SysProcessConstants.NODE_STATUS_WAITING, CONFIG_SINGLE),
                exampleNode(NODE5_ID, 5, "结束节点", "END", SysProcessConstants.NODE_STATUS_WAITING, null)));
        when(nodeDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(exampleNodes);
        when(nodeDao.selectByExampleAndStatus(anyString(), eq(SysProcessConstants.NODE_STATUS_REJECT)))
                .thenReturn(List.of());
        when(nodeDao.selectByExampleAndType(EXAMPLE_ID, SysProcessConstants.NODE_TYPE_DRAFT))
                .thenReturn(exampleNodeAt(2));
        when(nodeDao.selectByExampleAndType(EXAMPLE_ID, SysProcessConstants.NODE_TYPE_END))
                .thenReturn(exampleNodeAt(5));

        when(workitemDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(List.of());
        when(workitemDao.selectListByNodeId(anyString())).thenReturn(List.of());
        when(auditNoteDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(List.of());
        when(exampleDao.insert(any(SysProcessExample.class))).thenReturn(1);
        when(nodeDao.insertBatch(anyString(), any())).thenReturn(1);
        when(workitemDao.insert(any(SysProcessExampleWorkitem.class))).thenReturn(1);
        when(auditNoteDao.insert(any(SysProcessAuditNote.class))).thenReturn(1);

        userUtil = org.mockito.Mockito.mockStatic(UserUtil.class);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(CREATOR_ID, "起草人"));
    }

    @AfterEach
    void tearDown() {
        userUtil.close();
    }

    /* ==================== 创建草稿 ==================== */

    @Test
    void createDraftSnapshotsTemplateNodesWithDraftNodeProcessing() {
        when(templateNodeDao.selectListByTemplateId(TEMPLATE_ID)).thenReturn(templateNodes());

        var vo = service.createDraft(TEMPLATE_ID, "model-1", null);

        assertNotNull(vo.getFieldId());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_DRAFT, vo.getFieldStatus());
        assertEquals(Integer.valueOf(1), vo.getFieldTemplateVersion());
        verify(exampleDao).insert(argThat(e -> SysProcessConstants.EXAMPLE_STATUS_DRAFT.equals(e.getFieldStatus())
                && TEMPLATE_ID.equals(e.getFieldTemplateId())
                && "sys_org".equals(e.getFieldModelName())));
        verify(nodeDao).insertBatch(anyString(), argThat(nodes ->
                nodes.get(0).getFieldNodeNo() == 1 && SysProcessConstants.NODE_STATUS_FINISH.equals(nodes.get(0).getFieldStatus())
                        && nodes.get(1).getFieldNodeNo() == 2 && SysProcessConstants.NODE_STATUS_PROCESSING.equals(nodes.get(1).getFieldStatus())
                        && nodes.get(2).getFieldNodeNo() == 3 && SysProcessConstants.NODE_STATUS_WAITING.equals(nodes.get(2).getFieldStatus())));
    }

    @Test
    void createDraftRejectsDisabledTemplate() {
        template.setFieldEnable(false);
        assertThrows(IllegalArgumentException.class, () -> service.createDraft(TEMPLATE_ID, null, null));
    }

    /* ==================== 启动模板选择（默认模板） ==================== */

    @Test
    void startByModelWithoutDefaultTemplateFails() {
        // 未指定模板且分类无默认模板：流程启动失败（在进入 submit 前抛出）
        when(templateDao.selectDefaultByModelName("sys_org")).thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> service.startByModel("sys_org", "model-1", null, "提交", "重新提交"));
        verify(templateDao).selectDefaultByModelName("sys_org");
    }

    @Test
    void startByModelWithUnknownSpecifiedTemplateFails() {
        // 显式指定的模板不存在：启动失败（不允许静默回退默认模板）
        when(templateDao.selectById(TEMPLATE_ID)).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> service.startByModel("sys_org", "model-1", TEMPLATE_ID, null, "提交", "重新提交"));
        verify(templateDao, never()).selectDefaultByModelName(anyString());
    }

    @Test
    void defaultTemplateQueriesEnabledDefault() {
        SysProcessTemplate def = new SysProcessTemplate();
        def.setFieldId(TEMPLATE_ID);
        def.setFieldModelName("sys_org");
        def.setFieldEnable(true);
        def.setFieldDefault(true);
        when(templateDao.selectDefaultByModelName("sys_org")).thenReturn(def);

        assertEquals(def, service.defaultTemplate("sys_org"));
        verify(templateDao).selectDefaultByModelName("sys_org");
    }

    /* ==================== 提交与流转 ==================== */

    @Test
    void submitFromDraftEntersFirstApproveNodeAndCreatesWorkitems() {
        SysProcessExampleDetailVo detail = service.submit(EXAMPLE_ID, "请审批");

        verify(exampleDao, atLeastOnce()).update(argThat(e -> SysProcessConstants.EXAMPLE_STATUS_PROCESSING.equals(e.getFieldStatus())));
        verify(auditNoteDao).insert(argThat(n -> SysProcessConstants.AUDIT_TYPE_DRAFTER_SUBMIT.equals(n.getFieldHandleType())
                && "请审批".equals(n.getFieldContent())));
        // 首个审批节点（编号3）创建 2 个处理中工作项
        verify(workitemDao, times(2)).insert(argThat(w -> NODE3_ID.equals(w.getFieldNodeId())
                && SysProcessConstants.WORKITEM_STATUS_PROCESSING.equals(w.getFieldStatus())));
        verify(nodeDao).update(argThat(n -> NODE3_ID.equals(n.getFieldId())
                && SysProcessConstants.NODE_STATUS_PROCESSING.equals(n.getFieldStatus())));
        assertNotNull(detail.getExample());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PROCESSING, detail.getExample().getFieldStatus());
    }

    @Test
    void submitResolvesHandlersFromDocumentFieldSource() {
        // 审批来源=表单字段：读取文档实体字段值解析出组织架构作为审批人
        example.setFieldModelId("model-1");
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_WAITING, CONFIG_FIELD));
        when(documentFieldService.resolveFieldValue("sys_org", "model-1", "fieldManagerId"))
                .thenReturn(U1 + ";" + U2);

        service.submit(EXAMPLE_ID, "请审批");

        verify(documentFieldService).resolveFieldValue("sys_org", "model-1", "fieldManagerId");
        verify(workitemDao, times(2)).insert(argThat(w -> NODE3_ID.equals(w.getFieldNodeId())
                && SysProcessConstants.WORKITEM_STATUS_PROCESSING.equals(w.getFieldStatus())));
    }

    @Test
    void submitWithEmptyDocumentFieldSkipsNode() {
        // 表单字段值为空：实际处理人为空，节点跳过（PASS）后继续流转
        example.setFieldModelId("model-1");
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_WAITING, CONFIG_FIELD));
        when(documentFieldService.resolveFieldValue("sys_org", "model-1", "fieldManagerId"))
                .thenReturn(null);

        service.submit(EXAMPLE_ID, "请审批");

        verify(nodeDao).update(argThat(n -> NODE3_ID.equals(n.getFieldId())
                && SysProcessConstants.NODE_STATUS_PASS.equals(n.getFieldStatus())));
    }

    @Test
    void submitRejectedWhenNotDrafter() {
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));
        assertThrows(IllegalArgumentException.class, () -> service.submit(EXAMPLE_ID, "请审批"));
    }

    /* ==================== 审批人通过（单签/会审） ==================== */

    @Test
    void passSingleModeFinishesNodeAndMovesToEnd() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleNode node3 = exampleNodeAt(3);
        when(nodeDao.selectById(NODE3_ID)).thenReturn(node3);
        when(workitemDao.selectById(WI_U1)).thenReturn(workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        // 单签：一人通过即通过节点，其余工作项转 FINISH
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_FINISH),
                workitem(WI_U2, NODE3_ID, U2, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        service.pass(EXAMPLE_ID, WI_U1, "同意");

        verify(workitemDao).update(argThat(w -> WI_U1.equals(w.getFieldId())
                && SysProcessConstants.WORKITEM_STATUS_FINISH.equals(w.getFieldStatus())));
        verify(workitemDao).update(argThat(w -> WI_U2.equals(w.getFieldId())
                && SysProcessConstants.WORKITEM_STATUS_FINISH.equals(w.getFieldStatus())));
        verify(auditNoteDao).insert(argThat(n -> SysProcessConstants.AUDIT_TYPE_HANDLER_PASS.equals(n.getFieldHandleType())));
        // 节点3通过后进入结束节点（编号5）：流程 FINISH
        verify(exampleDao).update(argThat(e -> SysProcessConstants.EXAMPLE_STATUS_FINISH.equals(e.getFieldStatus())));
    }

    @Test
    void passCountersignRequiresAllWorkitemsFinished() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleNode node3 = exampleNodeAt(3);
        node3.setFieldNodeConfig(CONFIG_COUNTERSIGN);
        when(nodeDao.selectById(NODE3_ID)).thenReturn(node3);
        when(workitemDao.selectById(WI_U1)).thenReturn(workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        // 第一次：U2 还未通过，节点保持审批中
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_FINISH),
                workitem(WI_U2, NODE3_ID, U2, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        service.pass(EXAMPLE_ID, WI_U1, "同意");
        // 未全员通过：不进入下一节点，流程仍为 PROCESSING
        verify(exampleDao, never()).update(argThat(e ->
                SysProcessConstants.EXAMPLE_STATUS_FINISH.equals(e.getFieldStatus())));

        // 第二次：U2 通过，全员完成，进入结束节点
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_FINISH),
                workitem(WI_U2, NODE3_ID, U2, SysProcessConstants.WORKITEM_STATUS_FINISH)));
        when(workitemDao.selectById(WI_U2)).thenReturn(workitem(WI_U2, NODE3_ID, U2,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U2, "李四"));

        service.pass(EXAMPLE_ID, WI_U2, "同意");

        verify(exampleDao).update(argThat(e -> SysProcessConstants.EXAMPLE_STATUS_FINISH.equals(e.getFieldStatus())));
    }

    @Test
    void passRejectedWhenNotWorkitemHandler() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(workitemDao.selectById(WI_U1)).thenReturn(workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U2, "李四"));

        assertThrows(IllegalArgumentException.class, () -> service.pass(EXAMPLE_ID, WI_U1, "同意"));
    }

    /* ==================== 驳回与重新提交 ==================== */

    @Test
    void rejectMovesToReturnAndSavesResumeMode() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleNode node3 = exampleNodeAt(3);
        when(nodeDao.selectById(NODE3_ID)).thenReturn(node3);
        when(workitemDao.selectById(WI_U1)).thenReturn(workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(
                workitem(WI_U2, NODE3_ID, U2, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        service.reject(EXAMPLE_ID, WI_U1, "金额不对", SysProcessConstants.RESUME_MODE_BACK_TO_NODE);

        verify(workitemDao).update(argThat(w -> WI_U1.equals(w.getFieldId())
                && SysProcessConstants.WORKITEM_STATUS_FINISH.equals(w.getFieldStatus())
                && w.getFieldConfig().contains("\"resumeMode\":\"backToNode\"")));
        // 其他审批人工作项转为 FINISH
        verify(workitemDao).update(argThat(w -> WI_U2.equals(w.getFieldId())
                && SysProcessConstants.WORKITEM_STATUS_FINISH.equals(w.getFieldStatus())));
        verify(auditNoteDao).insert(argThat(n -> SysProcessConstants.AUDIT_TYPE_HANDLER_RETURN.equals(n.getFieldHandleType())));
        verify(nodeDao).update(argThat(n -> NODE3_ID.equals(n.getFieldId())
                && SysProcessConstants.NODE_STATUS_REJECT.equals(n.getFieldStatus())));
        verify(exampleDao).update(argThat(e -> SysProcessConstants.EXAMPLE_STATUS_RETURN.equals(e.getFieldStatus())));
        // 草稿节点转回 PROCESSING
        verify(nodeDao).update(argThat(n -> n.getFieldNodeNo() == 2
                && SysProcessConstants.NODE_STATUS_PROCESSING.equals(n.getFieldStatus())));
    }

    @Test
    void rejectRequiresResumeMode() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        assertThrows(IllegalArgumentException.class,
                () -> service.reject(EXAMPLE_ID, WI_U1, "不同意", null));
    }

    @Test
    void resubmitBackToNodeReentersRejectedNode() {
        // 流程 RETURN，驳回节点为编号 3，其工作项保存 resumeMode=backToNode
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_RETURN);
        SysProcessExampleNode node3 = exampleNodeAt(3);
        node3.setFieldStatus(SysProcessConstants.NODE_STATUS_REJECT);
        when(nodeDao.selectByExampleAndStatus(EXAMPLE_ID, SysProcessConstants.NODE_STATUS_REJECT))
                .thenReturn(List.of(node3));
        SysProcessExampleWorkitem rejected = workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_FINISH);
        rejected.setFieldConfig("{\"resumeMode\":\"backToNode\"}");
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(rejected));

        service.submit(EXAMPLE_ID, "重新提交");

        // 转回本节点：重新进入驳回节点并创建工作项（旧工作项不参与上一有效节点判断）
        verify(workitemDao, times(2)).insert(argThat(w -> NODE3_ID.equals(w.getFieldNodeId())
                && SysProcessConstants.WORKITEM_STATUS_PROCESSING.equals(w.getFieldStatus())));
        verify(nodeDao).update(argThat(n -> NODE3_ID.equals(n.getFieldId())
                && SysProcessConstants.NODE_STATUS_PROCESSING.equals(n.getFieldStatus())));
    }

    @Test
    void resubmitRestartFlowsFromFirstApproveNode() {
        // 流程 RETURN，驳回节点为编号 3，其工作项保存 resumeMode=restart
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_RETURN);
        SysProcessExampleNode node3 = exampleNodeAt(3);
        node3.setFieldStatus(SysProcessConstants.NODE_STATUS_REJECT);
        when(nodeDao.selectByExampleAndStatus(EXAMPLE_ID, SysProcessConstants.NODE_STATUS_REJECT))
                .thenReturn(List.of(node3));
        SysProcessExampleWorkitem rejected = workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_FINISH);
        rejected.setFieldConfig("{\"resumeMode\":\"restart\"}");
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(rejected));

        service.submit(EXAMPLE_ID, "重新提交");

        // 重新流转：仍从草稿节点后的首个审批节点（编号3）开始
        verify(workitemDao, times(2)).insert(argThat(w -> NODE3_ID.equals(w.getFieldNodeId())
                && SysProcessConstants.WORKITEM_STATUS_PROCESSING.equals(w.getFieldStatus())));
    }

    /* ==================== 自动跳过 ==================== */

    @Test
    void autoSkipRepeatedHandlersFromPreviousEffectiveNode() {
        // 节点3（上一有效节点）已单签通过：U1 实际审批、U2 工作项被置 FINISH；
        // 进入节点4时 U1/U2 均为上一有效节点审批人 → 全部自动跳过 → 节点4 PASS → 流程 FINISH
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleNode node3 = exampleNodeAt(3);
        node3.setFieldStatus(SysProcessConstants.NODE_STATUS_FINISH);
        when(nodeDao.selectById(NODE3_ID)).thenReturn(node3);
        when(workitemDao.selectById(WI_U1)).thenReturn(workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_FINISH),
                workitem(WI_U2, NODE3_ID, U2, SysProcessConstants.WORKITEM_STATUS_FINISH)));
        // 节点3的审批记录：U1 HANDLER_PASS（无 AUTO_PASS），U2 无记录（非自动跳过）
        when(auditNoteDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(List.of(
                auditNote("12345678901234567890123456789040", WI_U1, SysProcessConstants.AUDIT_TYPE_HANDLER_PASS)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        service.pass(EXAMPLE_ID, WI_U1, "同意");

        // 节点3通过后进入节点4：U1/U2 均被自动跳过（FINISH 工作项 + AUTO_PASS 记录）
        verify(workitemDao, times(2)).insert(argThat(w -> NODE4_ID.equals(w.getFieldNodeId())
                && SysProcessConstants.WORKITEM_STATUS_FINISH.equals(w.getFieldStatus())));
        verify(auditNoteDao, times(2)).insert(argThat(n -> SysProcessConstants.AUDIT_TYPE_AUTO_PASS.equals(n.getFieldHandleType())));
        // 节点4 实际处理人为空：PASS 后继续流转到结束节点，流程 FINISH
        verify(nodeDao).update(argThat(n -> NODE4_ID.equals(n.getFieldId())
                && SysProcessConstants.NODE_STATUS_PASS.equals(n.getFieldStatus())));
        verify(exampleDao).update(argThat(e -> SysProcessConstants.EXAMPLE_STATUS_FINISH.equals(e.getFieldStatus())));
    }

    @Test
    void noContinueSkipRequiresReapproval() {
        // node3 为上一有效节点（FINISH），其中 U1 被自动跳过（AUTO_PASS）、U2 实际审批；
        // node4 配置 autoContinueSkip=no → 上一有效节点被跳过的 U1 需重新审批，U2 仍为重复审批人自动跳过
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleNode node3 = exampleNodeAt(3);
        node3.setFieldStatus(SysProcessConstants.NODE_STATUS_FINISH);
        SysProcessExampleNode node4 = exampleNodeAt(4);
        node4.setFieldNodeConfig(CONFIG_NO_CONTINUE);
        when(nodeDao.selectById(NODE4_ID)).thenReturn(node4);
        when(workitemDao.selectActiveByExample(EXAMPLE_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_FINISH),
                workitem(WI_U2, NODE3_ID, U2, SysProcessConstants.WORKITEM_STATUS_FINISH)));
        when(auditNoteDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(List.of(
                auditNote("12345678901234567890123456789040", WI_U1, SysProcessConstants.AUDIT_TYPE_AUTO_PASS),
                auditNote("12345678901234567890123456789041", WI_U2, SysProcessConstants.AUDIT_TYPE_HANDLER_PASS)));

        service.adminOperate(operate(SysProcessConstants.ADMIN_OPERATE_JUMP, NODE4_ID, "跳转"));

        // U1 上一有效节点被自动跳过且本节点不允许连续跳过 → 需审批（PROCESSING）；
        // U2 命中重复审批人 → 自动跳过（FINISH + AUTO_PASS）
        verify(workitemDao).insert(argThat(w -> NODE4_ID.equals(w.getFieldNodeId())
                && U1.equals(w.getFieldHandlerId())
                && SysProcessConstants.WORKITEM_STATUS_PROCESSING.equals(w.getFieldStatus())));
        verify(workitemDao).insert(argThat(w -> NODE4_ID.equals(w.getFieldNodeId())
                && U2.equals(w.getFieldHandlerId())
                && SysProcessConstants.WORKITEM_STATUS_FINISH.equals(w.getFieldStatus())));
        verify(auditNoteDao).insert(argThat(n -> SysProcessConstants.AUDIT_TYPE_AUTO_PASS.equals(n.getFieldHandleType())));
        verify(nodeDao).update(argThat(n -> NODE4_ID.equals(n.getFieldId())
                && SysProcessConstants.NODE_STATUS_PROCESSING.equals(n.getFieldStatus())));
    }

    /* ==================== 起草人收回 / 废弃 ==================== */

    @Test
    void backByDrafterAbandonsActiveWorkitemsAndMovesToDraft() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(workitemDao.selectActiveByExample(EXAMPLE_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));

        service.back(EXAMPLE_ID, "先收回");

        verify(workitemDao).update(argThat(w -> WI_U1.equals(w.getFieldId())
                && SysProcessConstants.WORKITEM_STATUS_ABANDON.equals(w.getFieldStatus())));
        verify(auditNoteDao).insert(argThat(n -> SysProcessConstants.AUDIT_TYPE_DRAFTER_BACK.equals(n.getFieldHandleType())));
        verify(exampleDao).update(argThat(e -> SysProcessConstants.EXAMPLE_STATUS_DRAFT.equals(e.getFieldStatus())));
        verify(nodeDao).update(argThat(n -> n.getFieldNodeNo() == 2
                && SysProcessConstants.NODE_STATUS_PROCESSING.equals(n.getFieldStatus())));
    }

    @Test
    void backRejectedWhenNotProcessing() {
        assertThrows(IllegalArgumentException.class, () -> service.back(EXAMPLE_ID, "收回"));
    }

    @Test
    void abandonByDrafterMovesToEndNode() {
        // RETURN 状态可废弃
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_RETURN);
        service.abandonByDrafter(EXAMPLE_ID, "不要了");

        verify(auditNoteDao).insert(argThat(n -> SysProcessConstants.AUDIT_TYPE_DRAFTER_ABANDON.equals(n.getFieldHandleType())));
        verify(exampleDao).update(argThat(e -> SysProcessConstants.EXAMPLE_STATUS_ABANDON.equals(e.getFieldStatus())
                && null != e.getFieldFinishTime()));
        verify(nodeDao).update(argThat(n -> n.getFieldNodeNo() == 5
                && SysProcessConstants.NODE_STATUS_PROCESSING.equals(n.getFieldStatus())));
    }

    /* ==================== 管理员特权操作 ==================== */

    @Test
    void adminPauseTogglesWorkitemStatus() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleWorkitem wi = workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        when(workitemDao.selectActiveByExample(EXAMPLE_ID)).thenReturn(List.of(wi));

        service.adminOperate(operate(SysProcessConstants.ADMIN_OPERATE_PAUSE, null, "暂停核对"));

        verify(auditNoteDao).insert(argThat(n -> SysProcessConstants.AUDIT_TYPE_ADMIN_PAUSE.equals(n.getFieldHandleType())));
        verify(exampleDao).update(argThat(e -> SysProcessConstants.EXAMPLE_STATUS_PAUSE.equals(e.getFieldStatus())));
        verify(workitemDao).update(argThat(w -> SysProcessConstants.WORKITEM_STATUS_PAUSE.equals(w.getFieldStatus())));
    }

    @Test
    void adminResumeTogglesWorkitemStatus() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PAUSE);
        SysProcessExampleWorkitem wi = workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PAUSE);
        when(workitemDao.selectActiveByExample(EXAMPLE_ID)).thenReturn(List.of(wi));

        service.adminOperate(operate(SysProcessConstants.ADMIN_OPERATE_RESUME, null, "恢复"));

        verify(auditNoteDao).insert(argThat(n -> SysProcessConstants.AUDIT_TYPE_ADMIN_RESUME.equals(n.getFieldHandleType())));
        verify(exampleDao).update(argThat(e -> SysProcessConstants.EXAMPLE_STATUS_PROCESSING.equals(e.getFieldStatus())));
        verify(workitemDao).update(argThat(w -> SysProcessConstants.WORKITEM_STATUS_PROCESSING.equals(w.getFieldStatus())));
    }

    @Test
    void adminFinishMovesToEndNodeWithFinishStatus() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectByExampleAndType(EXAMPLE_ID, SysProcessConstants.NODE_TYPE_END))
                .thenReturn(exampleNodeAt(5));

        service.adminOperate(operate(SysProcessConstants.ADMIN_OPERATE_FINISH, null, "终审通过"));

        verify(auditNoteDao).insert(argThat(n -> SysProcessConstants.AUDIT_TYPE_ADMIN_FINISH.equals(n.getFieldHandleType())));
        verify(exampleDao).update(argThat(e -> SysProcessConstants.EXAMPLE_STATUS_FINISH.equals(e.getFieldStatus())));
        verify(nodeDao).update(argThat(n -> n.getFieldNodeNo() == 5
                && SysProcessConstants.NODE_STATUS_PROCESSING.equals(n.getFieldStatus())));
    }

    @Test
    void adminAbandonMovesToEndNodeWithAbandonStatus() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectByExampleAndType(EXAMPLE_ID, SysProcessConstants.NODE_TYPE_END))
                .thenReturn(exampleNodeAt(5));

        service.adminOperate(operate(SysProcessConstants.ADMIN_OPERATE_ABANDON, null, "作废"));

        verify(auditNoteDao).insert(argThat(n -> SysProcessConstants.AUDIT_TYPE_ADMIN_ABANDON.equals(n.getFieldHandleType())));
        verify(exampleDao).update(argThat(e -> SysProcessConstants.EXAMPLE_STATUS_ABANDON.equals(e.getFieldStatus())));
    }

    @Test
    void adminAbandonRejectedWhenFinished() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_FINISH);
        assertThrows(IllegalArgumentException.class,
                () -> service.adminOperate(operate(SysProcessConstants.ADMIN_OPERATE_ABANDON, null, "作废")));
    }

    @Test
    void adminJumpToApproveNodeReentersFlow() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectById(NODE4_ID)).thenReturn(exampleNodeAt(4));
        when(workitemDao.selectActiveByExample(EXAMPLE_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));

        service.adminOperate(operate(SysProcessConstants.ADMIN_OPERATE_JUMP, NODE4_ID, "跳过节点3"));

        verify(auditNoteDao).insert(argThat(n -> SysProcessConstants.AUDIT_TYPE_ADMIN_JUMP.equals(n.getFieldHandleType())));
        // 跳转目标为审批节点：进入该节点重新创建工作项
        verify(workitemDao, times(2)).insert(argThat(w -> NODE4_ID.equals(w.getFieldNodeId())));
    }

    @Test
    void adminJumpRequiresContent() {
        assertThrows(IllegalArgumentException.class,
                () -> service.adminOperate(operate(SysProcessConstants.ADMIN_OPERATE_FINISH, null, null)));
    }

    /* ==================== 详情 / 面板 ==================== */

    @Test
    void loadReturnsNullWhenExampleAbsent() {
        when(exampleDao.selectById(anyString())).thenReturn(null);
        assertNull(service.load("not-exist"));
    }

    @Test
    void loadAssemblesDetailWithNodesWorkitemsAndAuditNotes() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(workitemDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        when(auditNoteDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(List.of(
                auditNote("12345678901234567890123456789040", WI_U1, SysProcessConstants.AUDIT_TYPE_DRAFTER_SUBMIT)));

        SysProcessExampleDetailVo detail = service.load(EXAMPLE_ID);

        assertNotNull(detail.getExample());
        assertEquals("采购订单审批模板", detail.getExample().getFieldName());
        assertEquals("起草人", detail.getExample().getFieldCreatorName());
        assertEquals(5, detail.getNodes().size());
        assertEquals(1, detail.getWorkitems().size());
        assertEquals("张三", detail.getWorkitems().get(0).getFieldHandlerName());
        assertEquals(1, detail.getAuditNotes().size());
    }

    /* ==================== 工具方法 ==================== */

    private SysProcessAdminOperateRequest operate(String operateType, String targetNodeId, String content) {
        SysProcessAdminOperateRequest request = new SysProcessAdminOperateRequest();
        request.setFieldId(EXAMPLE_ID);
        request.setOperateType(operateType);
        request.setTargetNodeId(targetNodeId);
        request.setContent(content);
        return request;
    }

    private SysProcessTemplate template() {
        SysProcessTemplate t = new SysProcessTemplate();
        t.setFieldId(TEMPLATE_ID);
        t.setFieldName("采购订单审批模板");
        t.setFieldModelName("sys_org");
        t.setFieldVersion(1);
        t.setFieldEnable(true);
        return t;
    }

    private List<SysProcessTemplateNode> templateNodes() {
        List<SysProcessTemplateNode> nodes = new ArrayList<>();
        nodes.add(templateNode(1, "开始节点", "START", null));
        nodes.add(templateNode(2, "草稿节点", "DRAFT", null));
        nodes.add(templateNode(3, "审批节点3", "APPROVE", CONFIG_SINGLE));
        nodes.add(templateNode(4, "审批节点4", "APPROVE", CONFIG_SINGLE));
        nodes.add(templateNode(5, "结束节点", "END", null));
        return nodes;
    }

    private static SysProcessTemplateNode templateNode(int no, String name, String type, String config) {
        SysProcessTemplateNode node = new SysProcessTemplateNode();
        node.setFieldId("12345678901234567890123456789" + (100 + no));
        node.setFieldTemplateId(TEMPLATE_ID);
        node.setFieldNo(no);
        node.setFieldNodeName(name);
        node.setFieldNodeType(type);
        node.setFieldNodeConfig(config);
        return node;
    }

    private SysProcessExample example(String status) {
        SysProcessExample e = new SysProcessExample();
        e.setFieldId(EXAMPLE_ID);
        e.setFieldTemplateId(TEMPLATE_ID);
        e.setFieldTemplateVersion(1);
        e.setFieldModelName("sys_org");
        e.setFieldCode("P202601010001");
        e.setFieldCreatorId(CREATOR_ID);
        e.setFieldCreateTime(new java.util.Date());
        e.setFieldStatus(status);
        return e;
    }

    private SysProcessExampleNode exampleNodeAt(int no) {
        for (SysProcessExampleNode node : exampleNodes) {
            if (node.getFieldNodeNo() == no) {
                return node;
            }
        }
        throw new IllegalStateException("no example node with no=" + no);
    }

    private static SysProcessExampleNode exampleNode(String id, int no, String name, String type,
                                                     String status, String config) {
        SysProcessExampleNode node = new SysProcessExampleNode();
        node.setFieldId(id);
        node.setFieldExampleId(EXAMPLE_ID);
        node.setFieldNodeNo(no);
        node.setFieldNodeName(name);
        node.setFieldNodeType(type);
        node.setFieldStatus(status);
        node.setFieldNodeConfig(config);
        return node;
    }

    private static SysProcessExampleWorkitem workitem(String id, String nodeId, String handlerId, String status) {
        SysProcessExampleWorkitem workitem = new SysProcessExampleWorkitem();
        workitem.setFieldId(id);
        workitem.setFieldExampleId(EXAMPLE_ID);
        workitem.setFieldNodeId(nodeId);
        workitem.setFieldHandlerId(handlerId);
        workitem.setFieldExpectHandlerId(handlerId);
        workitem.setFieldStatus(status);
        workitem.setFieldCreateTime(new java.util.Date());
        return workitem;
    }

    private static SysProcessAuditNote auditNote(String id, String workitemId, String handleType) {
        SysProcessAuditNote note = new SysProcessAuditNote();
        note.setFieldId(id);
        note.setFieldExampleId(EXAMPLE_ID);
        note.setFieldWorkitemId(workitemId);
        note.setFieldHandlerId(U1);
        note.setFieldHandleType(handleType);
        note.setFieldCreateTime(new java.util.Date());
        return note;
    }

    private static SysOrgUser user(String id, String name) {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(id);
        user.setFieldName(name);
        user.setFieldEnable(true);
        return user;
    }
}
