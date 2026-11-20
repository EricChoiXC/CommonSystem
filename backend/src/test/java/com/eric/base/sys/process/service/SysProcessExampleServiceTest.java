package com.eric.base.sys.process.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eric.base.common.exception.AuthorizationException;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.setting.service.CommonSettingService;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.notify.enums.SysNotifyType;
import com.eric.base.sys.notify.model.SysNotifyTodo;
import com.eric.base.sys.notify.service.SysNotifyTodoService;
import com.eric.base.sys.org.mapper.SysOrgUserMapper;
import com.eric.base.sys.org.model.SysOrgRole;
import com.eric.base.sys.org.model.SysOrgUser;
import com.eric.base.sys.process.dao.SysProcessAuditNoteDao;
import com.eric.base.sys.process.dao.SysProcessExampleDao;
import com.eric.base.sys.process.dao.SysProcessExampleLineDao;
import com.eric.base.sys.process.dao.SysProcessExampleNodeDao;
import com.eric.base.sys.process.dao.SysProcessExampleWorkitemDao;
import com.eric.base.sys.process.dao.SysProcessTemplateDao;
import com.eric.base.sys.process.dao.SysProcessTemplateLineDao;
import com.eric.base.sys.process.dao.SysProcessTemplateNodeDao;
import com.eric.base.sys.process.model.SysProcessAuditNote;
import com.eric.base.sys.process.model.SysProcessExample;
import com.eric.base.sys.process.model.SysProcessExampleLine;
import com.eric.base.sys.process.model.SysProcessExampleNode;
import com.eric.base.sys.process.model.SysProcessExampleWorkitem;
import com.eric.base.sys.process.model.SysProcessTemplate;
import com.eric.base.sys.process.model.SysProcessTemplateLine;
import com.eric.base.sys.process.model.SysProcessTemplateNode;
import com.eric.base.sys.process.model.SysProcessTemplateNodeConfigDo;
import com.eric.base.sys.process.properties.SysProcessConstants;
import com.eric.base.sys.process.request.SysProcessAdminOperateRequest;
import com.eric.base.sys.process.request.SysProcessOperateRequest;
import com.eric.base.sys.process.resolver.ProcessResolver;
import com.eric.base.sys.process.response.SysProcessExampleDetailVo;
import com.eric.base.sys.process.response.SysProcessExamplePanelVo;
import com.eric.base.sys.process.response.SysProcessNodeOptionVo;
import com.eric.base.sys.process.response.SysProcessPanelOperationVo;
import com.eric.base.sys.process.service.engine.ProcessFlowEngine;
import com.eric.base.sys.process.service.engine.ProcessFlowGraphLoader;
import com.eric.base.sys.process.service.engine.ProcessFlowSupport;
import com.eric.base.sys.process.service.engine.ProcessHandlerResolver;
import com.eric.base.sys.process.service.engine.ProcessNotifySender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
 * @description 覆盖流程流转引擎核心规则（依据 docs/设计文档/sys/process/流程核心业务.md「流程实例和流转」）：
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
    private SysNotifyTodoService notifyTodoService;
    private ProcessNotifySender notifySender;
    private SysOrgUserMapper userMapper;
    private ProcessResolver resolver;
    private SysProcessDocumentFieldService documentFieldService;
    private SysProcessExampleLineDao exampleLineDao;
    private SysProcessTemplateLineDao templateLineDao;
    private ProcessHandlerResolver handlerResolver;
    private ProcessFlowEngine flowEngine;
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
        notifyTodoService = mock(SysNotifyTodoService.class);
        userMapper = mock(SysOrgUserMapper.class);
        resolver = mock(ProcessResolver.class);
        documentFieldService = mock(SysProcessDocumentFieldService.class);
        exampleLineDao = mock(SysProcessExampleLineDao.class);
        templateLineDao = mock(SysProcessTemplateLineDao.class);
        handlerResolver = mock(ProcessHandlerResolver.class);
        notifySender = new ProcessNotifySender(notifyTodoService, userMapper, templateDao, settingService);
        ProcessFlowSupport flowSupport = new ProcessFlowSupport(exampleDao, nodeDao, workitemDao,
                auditNoteDao, new ObjectMapper());
        flowEngine = new ProcessFlowEngine(exampleDao, nodeDao, workitemDao, auditNoteDao,
                settingService, new ProcessFlowGraphLoader(exampleLineDao), flowSupport,
                handlerResolver, notifySender, documentFieldService, null, null);
        service = new SysProcessExampleService(exampleDao, nodeDao, exampleLineDao, workitemDao,
                auditNoteDao, templateDao, templateNodeDao, templateLineDao, settingService,
                userMapper, notifySender, resolver, documentFieldService,
                new ObjectMapper(), flowEngine, flowSupport);

        // 默认公共配置：自动跳过/包含起草人/连续跳过均开启
        when(settingService.getBooleanValue(eq(SysProcessConstants.SETTING_TYPE_PROCESS),
                eq(SysProcessConstants.SETTING_PROCESS_AUTO_HANDLE_PASS))).thenReturn(true);
        when(settingService.getBooleanValue(eq(SysProcessConstants.SETTING_TYPE_PROCESS),
                eq(SysProcessConstants.SETTING_PROCESS_AUTO_HANDLE_PASS_DRAFTER))).thenReturn(true);
        when(settingService.getBooleanValue(eq(SysProcessConstants.SETTING_TYPE_PROCESS),
                eq(SysProcessConstants.SETTING_PROCESS_AUTO_CONTINUE_PASS))).thenReturn(true);

        // 审批人解析服务桩：按节点配置的地址本/表单字段取值返回处理人（去重保序）
        when(handlerResolver.resolveHandlerIds(any(), any(), anyMap())).thenAnswer(invocation -> {
            SysProcessTemplateNodeConfigDo config = invocation.getArgument(0);
            if (null == config) {
                return List.of();
            }
            if (SysProcessConstants.HANDLER_SOURCE_FIELD.equals(config.getHandlerSource())) {
                // 引擎按「表单字段」源解析审批人（本桩中转文档字段读取，断言口径保持原语义）
                String value = documentFieldService.resolveFieldValue("sys_org", example.getFieldModelId(),
                        config.getHandlerField());
                return splitIds(value);
            }
            return splitIds(config.getHandlerIds());
        });
        when(handlerResolver.resolveCcIds(any(), any(), anyMap())).thenReturn(List.of());

        // 默认：未知用户返回 null（必须先注册，具体用户匹配在后生效）
        when(userMapper.selectById(anyString())).thenReturn(null);
        when(userMapper.selectById(CREATOR_ID)).thenReturn(user(CREATOR_ID, "起草人"));
        when(userMapper.selectById(U1)).thenReturn(user(U1, "张三"));
        when(userMapper.selectById(U2)).thenReturn(user(U2, "李四"));

        template = template();
        when(templateDao.selectById(TEMPLATE_ID)).thenReturn(template);
        // 默认模板节点/连线（createDraft 全量快照复制来源）
        when(templateNodeDao.selectListByTemplateId(TEMPLATE_ID)).thenReturn(templateNodes());
        when(templateLineDao.selectListByTemplateId(TEMPLATE_ID)).thenReturn(templateLines());

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
        // 图连线（实例连线快照优先：START→DRAFT→审批3→审批4→END）
        when(exampleLineDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(flowLines());
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
        verify(exampleLineDao).insertBatch(anyString(), argThat(lines -> lines.size() == 4));
    }

    @Test
    void createDraftCopiesTemplateLineSnapshotWithBranchFields() {
        when(templateNodeDao.selectListByTemplateId(TEMPLATE_ID)).thenReturn(templateNodes());
        List<SysProcessTemplateLine> templateLines = templateLines();
        when(templateLineDao.selectListByTemplateId(TEMPLATE_ID)).thenReturn(templateLines);
        org.mockito.ArgumentCaptor<String> exampleIdCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<List<SysProcessExampleLine>> linesCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);

        service.createDraft(TEMPLATE_ID, "model-1", null);

        // 连线快照逐条复制：起点/终点键、连线类型、分支序号、分支名、分支条件、默认分支标记；
        // 主键由 initFieldId() 生成（Mapper insertBatch 取 #{line.fieldId}，为空将插 NULL 主键失败）
        verify(exampleLineDao).insertBatch(exampleIdCaptor.capture(), linesCaptor.capture());
        String exampleId = exampleIdCaptor.getValue();
        assertTrue(null != exampleId && !exampleId.isBlank(), "实例 id 由 initFieldId() 生成");
        List<SysProcessExampleLine> copies = linesCaptor.getValue();
        assertEquals(templateLines.size(), copies.size());
        for (int i = 0; i < templateLines.size(); i++) {
            SysProcessTemplateLine source = templateLines.get(i);
            SysProcessExampleLine copy = copies.get(i);
            assertTrue(null != copy.getFieldId() && !copy.getFieldId().isBlank(),
                    "实例连线主键由 initFieldId() 生成");
            assertEquals(exampleId, copy.getFieldExampleId());
            assertEquals(source.getFieldFromKey(), copy.getFieldFromKey());
            assertEquals(source.getFieldToKey(), copy.getFieldToKey());
            assertEquals(source.getFieldLineType(), copy.getFieldLineType());
            assertEquals(source.getFieldBranchOrder(), copy.getFieldBranchOrder());
            assertEquals(source.getFieldBranchName(), copy.getFieldBranchName());
            assertEquals(source.getFieldCondition(), copy.getFieldCondition());
            assertEquals(source.getFieldIsDefault(), copy.getFieldIsDefault());
        }
    }

    @Test
    void createDraftFailsWhenTemplateHasNoLineDefinition() {
        when(templateNodeDao.selectListByTemplateId(TEMPLATE_ID)).thenReturn(templateNodes());
        when(templateLineDao.selectListByTemplateId(TEMPLATE_ID)).thenReturn(List.of());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.createDraft(TEMPLATE_ID, "model-1", null));
        assertEquals("流程模板缺少连线定义", error.getMessage());
        verify(exampleLineDao, never()).insertBatch(anyString(), anyList());
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

        verify(documentFieldService, atLeastOnce())
                .resolveFieldValue("sys_org", "model-1", "fieldManagerId");
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
    void submitResolvesHandlersFromDocumentFieldSourceReadsFieldPerParsePass() {
        // 表单字段源：引擎解析审批人时读取文档字段（审批人解析一次、公式变量表一次）
        example.setFieldModelId("model-1");
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_WAITING, CONFIG_FIELD));
        when(documentFieldService.resolveFieldValue("sys_org", "model-1", "fieldManagerId"))
                .thenReturn(U1);

        service.submit(EXAMPLE_ID, "请审批");

        verify(documentFieldService, times(2)).resolveFieldValue("sys_org", "model-1", "fieldManagerId");
    }

    @Test
    void submitRejectedWhenNotDrafter() {
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));
        // 身份不符（非起草人）升级为鉴权异常：FORBIDDEN + CS_PROCESS_OPERATE_FORBIDDEN 前缀（P17-BE-008）
        AuthorizationException error = assertThrows(AuthorizationException.class,
                () -> service.submit(EXAMPLE_ID, "请审批"));
        assertEquals("FORBIDDEN", error.getCode());
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_FORBIDDEN));
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

        // 身份不符（非工作项处理人）升级为鉴权异常：FORBIDDEN + CS_PROCESS_OPERATE_FORBIDDEN 前缀（P17-BE-008）
        AuthorizationException error = assertThrows(AuthorizationException.class,
                () -> service.pass(EXAMPLE_ID, WI_U1, "同意"));
        assertEquals("FORBIDDEN", error.getCode());
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_FORBIDDEN));
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
        // v1_7 起新操作统一留痕 HANDLER_REJECT（旧 4 参入口同样写新类型，HANDLER_RETURN 保留兼容历史数据）
        verify(auditNoteDao).insert(argThat(n -> SysProcessConstants.AUDIT_TYPE_HANDLER_REJECT.equals(n.getFieldHandleType())));
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

    /* ==================== 被驳回待办（P17-BE-014 通知收口） ==================== */

    @Test
    void rejectSendsRefuseTodoToDrafter() {
        // 驳回人（U1 张三）≠起草人：流程转 RETURN 后向起草人发送被驳回待办
        // （TODO，标题消费 notify_refuse_subject 默认模板、${fieldHandlerName}=驳回人姓名，内容=驳回意见）
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        when(workitemDao.selectById(WI_U1)).thenReturn(workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of());
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        service.reject(EXAMPLE_ID, WI_U1, "金额不对", SysProcessConstants.RESUME_MODE_RESTART);

        verify(notifyTodoService).send(argThat(todo ->
                        SysNotifyType.TODO.getValue().equals(todo.getFieldNotifyType())
                                && "您提交的流程被张三驳回，请修改".equals(todo.getFieldName())
                                && "金额不对".equals(todo.getFieldContent())
                                && SysProcessConstants.MODEL_KEY_SYS_PROCESS.equals(todo.getFieldModelName())
                                && ("/processExample/detail/" + EXAMPLE_ID).equals(todo.getFieldUrl())),
                argThat(ids -> ids.size() == 1 && ids.get(0).equals(CREATOR_ID)));
    }

    @Test
    void rejectByDrafterHimselfSendsNoRefuseTodo() {
        // 驳回人=起草人本人：不发被驳回待办
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        when(workitemDao.selectById(WI_U1)).thenReturn(workitem(WI_U1, NODE3_ID, CREATOR_ID,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of());
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(CREATOR_ID, "起草人"));

        service.reject(EXAMPLE_ID, WI_U1, "自查驳回", SysProcessConstants.RESUME_MODE_RESTART);

        verify(notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
    }

    /* ==================== 驳回目标校验（P17-BE-009） ==================== */

    /** 驳回目标配置：允许起草节点（N2）与上游审批节点 N3 */
    private static final String CONFIG_REJECT_TARGETS =
            "{\"handlerIds\":\"" + U1 + "\",\"approveMode\":\"single\",\"rejectTargets\":[\"N2\",\"N3\"]}";

    /** 驳回目标配置：含未流经节点 N5（校验「配置 ∩ 已流经」交集） */
    private static final String CONFIG_REJECT_TARGETS_WITH_WAITING =
            "{\"handlerIds\":\"" + U1 + "\",\"approveMode\":\"single\",\"rejectTargets\":[\"N2\",\"N5\"]}";

    private static final String NODE6_ID = "12345678901234567890123456789016";

    /**
     * 装配驳回目标校验图：N1(START)→N2(DRAFT)→N3(APPROVE,已流经位)→N4(APPROVE,驳回者所在)
     * →N5(APPROVE,未流经)→N6(END)。
     *
     * @param node3Status 已流经位节点 N3 的状态（FINISH / REJECT / PASS / WAITING）
     * @param node4Config 驳回者所在节点 N4 的节点配置（承载 rejectTargets）
     */
    private void setUpRejectTargetGraph(String node3Status, String node4Config) {
        exampleNodes.clear();
        exampleNodes.addAll(List.of(
                exampleNode("12345678901234567890123456789091", 1, "开始节点", "START",
                        SysProcessConstants.NODE_STATUS_FINISH, null),
                exampleNode("12345678901234567890123456789092", 2, "草稿节点", "DRAFT",
                        SysProcessConstants.NODE_STATUS_FINISH, null),
                exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE", node3Status, CONFIG_SINGLE),
                exampleNode(NODE4_ID, 4, "审批节点4", "APPROVE",
                        SysProcessConstants.NODE_STATUS_PROCESSING, node4Config),
                exampleNode(NODE5_ID, 5, "审批节点5", "APPROVE",
                        SysProcessConstants.NODE_STATUS_WAITING, CONFIG_SINGLE),
                exampleNode(NODE6_ID, 6, "结束节点", "END", SysProcessConstants.NODE_STATUS_WAITING, null)));
        // 连线：N1→N2→N3→N4→N5→N6
        List<SysProcessExampleLine> lines = new ArrayList<>();
        lines.add(exampleLine("L1", "N1", "N2"));
        lines.add(exampleLine("L2", "N2", "N3"));
        lines.add(exampleLine("L3", "N3", "N4"));
        lines.add(exampleLine("L4", "N4", "N5"));
        lines.add(exampleLine("L5", "N5", "N6"));
        when(exampleLineDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(lines);
        when(nodeDao.selectByExampleAndType(EXAMPLE_ID, SysProcessConstants.NODE_TYPE_DRAFT))
                .thenReturn(exampleNodeAt(2));
        when(nodeDao.selectByExampleAndType(EXAMPLE_ID, SysProcessConstants.NODE_TYPE_END))
                .thenReturn(exampleNodeAt(6));
    }

    /** 驳回场景桩：驳回者 U1 持有节点4（N4）的 PROCESSING 工作项 */
    private void stubRejecterWorkitem() {
        when(nodeDao.selectById(NODE4_ID)).thenReturn(exampleNodeAt(4));
        when(workitemDao.selectById(WI_U1)).thenReturn(workitem(WI_U1, NODE4_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        when(workitemDao.selectListByNodeId(NODE4_ID)).thenReturn(List.of(
                workitem(WI_U2, NODE4_ID, U2, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));
    }

    @Test
    void rejectWithConfiguredTargetKeepsTargetAndDraftNodesUntouched() {
        // 配置 [N2,N3]：N3 为已流经（FINISH）上游审批节点 → 允许；留痕 HANDLER_REJECT，
        // 目标节点（N3）与起草节点状态均不改动（重新提交时按 resumeMode 进入目标节点）
        setUpRejectTargetGraph(SysProcessConstants.NODE_STATUS_FINISH, CONFIG_REJECT_TARGETS);
        stubRejecterWorkitem();
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);

        service.reject(EXAMPLE_ID, WI_U1, "退回节点3", "N3", SysProcessConstants.RESUME_MODE_BACK_TO_NODE);

        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_HANDLER_REJECT.equals(n.getFieldHandleType())));
        verify(workitemDao).update(argThat(w -> WI_U1.equals(w.getFieldId())
                && SysProcessConstants.WORKITEM_STATUS_FINISH.equals(w.getFieldStatus())
                && w.getFieldConfig().contains("\"resumeMode\":\"backToNode\"")
                && w.getFieldConfig().contains("\"rejectTargetKey\":\"N3\"")));
        verify(workitemDao).update(argThat(w -> WI_U2.equals(w.getFieldId())
                && SysProcessConstants.WORKITEM_STATUS_FINISH.equals(w.getFieldStatus())));
        verify(nodeDao).update(argThat(n -> NODE4_ID.equals(n.getFieldId())
                && SysProcessConstants.NODE_STATUS_REJECT.equals(n.getFieldStatus())));
        verify(exampleDao).update(argThat(e -> SysProcessConstants.EXAMPLE_STATUS_RETURN.equals(e.getFieldStatus())));
        // 驳回目标=非起草节点：目标节点与起草节点均不置 PROCESSING
        verify(nodeDao, never()).update(argThat(n -> NODE3_ID.equals(n.getFieldId())));
        verify(nodeDao, never()).update(argThat(n -> n.getFieldNodeNo() == 2
                && SysProcessConstants.NODE_STATUS_PROCESSING.equals(n.getFieldStatus())));
    }

    @Test
    void rejectWithDraftTargetReturnsDraftNodeToProcessing() {
        // 配置 [N2,N3]：起草节点 N2 恒视为已流经 → 允许；起草节点转回 PROCESSING（现状行为不变）
        setUpRejectTargetGraph(SysProcessConstants.NODE_STATUS_FINISH, CONFIG_REJECT_TARGETS);
        stubRejecterWorkitem();
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);

        service.reject(EXAMPLE_ID, WI_U1, "退回起草节点", "N2", SysProcessConstants.RESUME_MODE_BACK_TO_NODE);

        verify(exampleDao).update(argThat(e -> SysProcessConstants.EXAMPLE_STATUS_RETURN.equals(e.getFieldStatus())));
        verify(nodeDao).update(argThat(n -> n.getFieldNodeNo() == 2
                && SysProcessConstants.NODE_STATUS_PROCESSING.equals(n.getFieldStatus())));
    }

    @Test
    void rejectRejectsTargetNotInConfigEvenIfTraversed() {
        // 配置为空（无 rejectTargets）→ 允许目标仅起草节点；已流经（FINISH）的 N3 也不允许
        setUpRejectTargetGraph(SysProcessConstants.NODE_STATUS_FINISH, CONFIG_SINGLE);
        stubRejecterWorkitem();
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.reject(EXAMPLE_ID, WI_U1, "退回节点3", "N3",
                        SysProcessConstants.RESUME_MODE_BACK_TO_NODE));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_NODE_TARGET_INVALID));
        assertTrue(error.getMessage().contains("审批节点3"), "拒绝原因须列出节点名");
        // 校验失败不产生任何流转写操作
        verify(auditNoteDao, never()).insert(any(SysProcessAuditNote.class));
        verify(exampleDao, never()).update(any(SysProcessExample.class));
    }

    @Test
    void rejectRejectsWaitingTargetEvenIfConfigured() {
        // 配置 [N2,N5]：N5 未流经（WAITING，不算已流经）→ 拒绝
        setUpRejectTargetGraph(SysProcessConstants.NODE_STATUS_FINISH, CONFIG_REJECT_TARGETS_WITH_WAITING);
        stubRejecterWorkitem();
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.reject(EXAMPLE_ID, WI_U1, "退回节点5", "N5",
                        SysProcessConstants.RESUME_MODE_BACK_TO_NODE));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_NODE_TARGET_INVALID));
        verify(auditNoteDao, never()).insert(any(SysProcessAuditNote.class));
    }

    @Test
    void rejectRejectsAutoPassTargetEvenIfConfigured() {
        // 配置 [N2,N3]：N3 为 PASS（自动跳过，不算已流经）→ 拒绝
        setUpRejectTargetGraph(SysProcessConstants.NODE_STATUS_PASS, CONFIG_REJECT_TARGETS);
        stubRejecterWorkitem();
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.reject(EXAMPLE_ID, WI_U1, "退回节点3", "N3",
                        SysProcessConstants.RESUME_MODE_BACK_TO_NODE));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_NODE_TARGET_INVALID));
    }

    @Test
    void rejectRejectsUnknownTargetKey() {
        // 目标 key 不是本实例节点（N9 不在实例节点集合）→ 拒绝
        setUpRejectTargetGraph(SysProcessConstants.NODE_STATUS_FINISH, CONFIG_REJECT_TARGETS);
        stubRejecterWorkitem();
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.reject(EXAMPLE_ID, WI_U1, "非法目标", "N9",
                        SysProcessConstants.RESUME_MODE_BACK_TO_NODE));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_NODE_TARGET_INVALID));
        assertTrue(error.getMessage().contains("N9"));
    }

    @Test
    void rejectPreservesExistingWorkitemConfigKeys() {
        // 驳回者工作项已有 flowChoice / flowBranchKey：先读回再写，不清掉既有键
        setUpRejectTargetGraph(SysProcessConstants.NODE_STATUS_FINISH, CONFIG_REJECT_TARGETS);
        stubRejecterWorkitem();
        SysProcessExampleWorkitem rejecter = workitem(WI_U1, NODE4_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        rejecter.setFieldConfig("{\"resumeMode\":\"restart\",\"flowChoice\":\"N8\",\"flowBranchKey\":\"N3\"}");
        when(workitemDao.selectById(WI_U1)).thenReturn(rejecter);
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);

        service.reject(EXAMPLE_ID, WI_U1, "退回节点3", "N3", SysProcessConstants.RESUME_MODE_BACK_TO_NODE);

        verify(workitemDao).update(argThat(w -> WI_U1.equals(w.getFieldId())
                && w.getFieldConfig().contains("\"resumeMode\":\"backToNode\"")
                && w.getFieldConfig().contains("\"rejectTargetKey\":\"N3\"")
                && w.getFieldConfig().contains("\"flowChoice\":\"N8\"")
                && w.getFieldConfig().contains("\"flowBranchKey\":\"N3\"")));
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

    @Test
    void resubmitBackToNodeEntersNonDraftRejectTarget() {
        // 流程 RETURN，驳回节点为编号 4（REJECT），其工作项配置 resumeMode=backToNode + rejectTargetKey=N3（非起草目标）
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_RETURN);
        SysProcessExampleNode node4 = exampleNodeAt(4);
        node4.setFieldStatus(SysProcessConstants.NODE_STATUS_REJECT);
        SysProcessExampleNode node3 = exampleNodeAt(3);
        node3.setFieldStatus(SysProcessConstants.NODE_STATUS_FINISH);
        when(nodeDao.selectByExampleAndStatus(EXAMPLE_ID, SysProcessConstants.NODE_STATUS_REJECT))
                .thenReturn(List.of(node4));
        SysProcessExampleWorkitem rejected = workitem(WI_U1, NODE4_ID, U1, SysProcessConstants.WORKITEM_STATUS_FINISH);
        rejected.setFieldConfig("{\"resumeMode\":\"backToNode\",\"rejectTargetKey\":\"N3\"}");
        when(workitemDao.selectListByNodeId(NODE4_ID)).thenReturn(List.of(rejected));

        SysProcessExampleDetailVo detail = service.submit(EXAMPLE_ID, "重新提交");

        // 重新提交进入驳回目标节点 N3（而非被驳回节点 N4）：目标节点重建工作项、节点 PROCESSING、流程 PROCESSING
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PROCESSING, detail.getExample().getFieldStatus());
        verify(workitemDao, times(2)).insert(argThat(w -> NODE3_ID.equals(w.getFieldNodeId())
                && SysProcessConstants.WORKITEM_STATUS_PROCESSING.equals(w.getFieldStatus())));
        verify(nodeDao).update(argThat(n -> NODE3_ID.equals(n.getFieldId())
                && SysProcessConstants.NODE_STATUS_PROCESSING.equals(n.getFieldStatus())));
        verify(workitemDao, never()).insert(argThat(w -> NODE4_ID.equals(w.getFieldNodeId())));
    }

    @Test
    void resubmitBackToNodeWithDraftTargetFallsBackToRejectedNode() {
        // 流程 RETURN，驳回节点为编号 4（REJECT），工作项配置 rejectTargetKey=N2（起草目标）→ 回退被驳回节点（现状回归）
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_RETURN);
        SysProcessExampleNode node4 = exampleNodeAt(4);
        node4.setFieldStatus(SysProcessConstants.NODE_STATUS_REJECT);
        when(nodeDao.selectByExampleAndStatus(EXAMPLE_ID, SysProcessConstants.NODE_STATUS_REJECT))
                .thenReturn(List.of(node4));
        SysProcessExampleWorkitem rejected = workitem(WI_U1, NODE4_ID, U1, SysProcessConstants.WORKITEM_STATUS_FINISH);
        rejected.setFieldConfig("{\"resumeMode\":\"backToNode\",\"rejectTargetKey\":\"N2\"}");
        when(workitemDao.selectListByNodeId(NODE4_ID)).thenReturn(List.of(rejected));

        service.submit(EXAMPLE_ID, "重新提交");

        // 起草目标回退被驳回节点：进入 N4 重建工作项（与既有 backToNode 行为一致）
        verify(workitemDao, times(2)).insert(argThat(w -> NODE4_ID.equals(w.getFieldNodeId())
                && SysProcessConstants.WORKITEM_STATUS_PROCESSING.equals(w.getFieldStatus())));
        verify(nodeDao).update(argThat(n -> NODE4_ID.equals(n.getFieldId())
                && SysProcessConstants.NODE_STATUS_PROCESSING.equals(n.getFieldStatus())));
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
        // ABANDON 终态不发办结待阅（P17-BE-014 通知收口：仅 FINISH 办结发送）
        verify(notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
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
    void adminFinishSendsFinishReadsToDrafterAndApprovedHandlers() {
        // P17-BE-014 通知收口：管理员终审通过不经引擎 enterEnd，由 Service 以同口径发送办结待阅——
        // 起草人待阅始终发送（收件人=起草人）；审批人待阅收件人=HANDLER_PASS 留痕关联工作项处理人
        // （finish_notify_handler 显式打桩 true，与生产「配置缺失回退 null → 缺省 true」同口径）
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectByExampleAndType(EXAMPLE_ID, SysProcessConstants.NODE_TYPE_END))
                .thenReturn(exampleNodeAt(5));
        when(settingService.getBooleanValue(eq(SysProcessConstants.SETTING_TYPE_PROCESS),
                eq(SysProcessConstants.SETTING_PROCESS_FINISH_NOTIFY_HANDLER))).thenReturn(true);
        when(workitemDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_FINISH)));
        when(auditNoteDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(List.of(
                auditNote("12345678901234567890123456789040", WI_U1,
                        SysProcessConstants.AUDIT_TYPE_HANDLER_PASS)));

        service.adminOperate(operate(SysProcessConstants.ADMIN_OPERATE_FINISH, null, "终审通过"));

        // 起草人待阅（READ）：标题=notify_finish_drafter_subject 默认模板渲染、内容=固定文案
        verify(notifyTodoService).send(argThat(todo ->
                        SysNotifyType.READ.getValue().equals(todo.getFieldNotifyType())
                                && "您提交的流程采购订单审批模板已通过".equals(todo.getFieldName())
                                && "流程已办结".equals(todo.getFieldContent())),
                argThat(ids -> ids.size() == 1 && ids.get(0).equals(CREATOR_ID)));
        // 审批人待阅（READ）：收件人=实际审批过的用户 [U1]
        verify(notifyTodoService).send(argThat(todo ->
                        SysNotifyType.READ.getValue().equals(todo.getFieldNotifyType())
                                && "您审批的流程采购订单审批模板已通过".equals(todo.getFieldName())),
                argThat(ids -> ids.size() == 1 && ids.get(0).equals(U1)));
        verify(notifyTodoService, times(2)).send(any(SysNotifyTodo.class), anyList());
    }

    @Test
    void adminAbandonMovesToEndNodeWithAbandonStatus() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectByExampleAndType(EXAMPLE_ID, SysProcessConstants.NODE_TYPE_END))
                .thenReturn(exampleNodeAt(5));

        service.adminOperate(operate(SysProcessConstants.ADMIN_OPERATE_ABANDON, null, "作废"));

        verify(auditNoteDao).insert(argThat(n -> SysProcessConstants.AUDIT_TYPE_ADMIN_ABANDON.equals(n.getFieldHandleType())));
        verify(exampleDao).update(argThat(e -> SysProcessConstants.EXAMPLE_STATUS_ABANDON.equals(e.getFieldStatus())));
        // ABANDON 终态不发办结待阅（P17-BE-014 通知收口：仅 FINISH 办结发送）
        verify(notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
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

    /* ==================== 管理员更换审批人 / 跳转按节点键（P17-BE-013） ==================== */

    @Test
    void adminChangeHandlerReplacesHandlerAndWritesAudit() {
        // 换人仅替换处理人与期望处理人（工作项仍是原序位、状态不变），不改节点/流程状态；留痕 ADMIN_CHANGE_HANDLER
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleWorkitem workitem = workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        when(workitemDao.selectById(WI_U1)).thenReturn(workitem);
        userUtil.when(UserUtil::getCurrentUser)
                .thenReturn(userWithRoles(U1, "张三", SysProcessConstants.ROLE_SYS_PROCESS_EXAMPLE_ADMIN));

        SysProcessAdminOperateRequest request =
                operate(SysProcessConstants.ADMIN_OPERATE_CHANGE_HANDLER, null, "更换审批人");
        request.setWorkitemId(WI_U1);
        request.setChangeToId(U2);

        service.adminOperate(request);

        verify(workitemDao).update(argThat(w -> WI_U1.equals(w.getFieldId())
                && U2.equals(w.getFieldHandlerId())
                && U2.equals(w.getFieldExpectHandlerId())
                && SysProcessConstants.WORKITEM_STATUS_PROCESSING.equals(w.getFieldStatus())));
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_ADMIN_CHANGE_HANDLER.equals(n.getFieldHandleType())
                        && WI_U1.equals(n.getFieldWorkitemId())
                        && U1.equals(n.getFieldHandlerId())
                        && "更换审批人".equals(n.getFieldContent())));
        verify(exampleDao, never()).update(any(SysProcessExample.class));
        verify(nodeDao, never()).update(any(SysProcessExampleNode.class));
    }

    @Test
    void adminChangeHandlerNewHandlerOperatesAndOriginalForbidden() {
        // 验收：换人后新处理人可操作（身份校验按 field_handler_id 自然放行）、原处理人失效（FORBIDDEN）
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleWorkitem workitem = workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        when(workitemDao.selectById(WI_U1)).thenReturn(workitem);
        userUtil.when(UserUtil::getCurrentUser)
                .thenReturn(userWithRoles(U1, "张三", SysProcessConstants.ROLE_SYS_PROCESS_EXAMPLE_ADMIN));

        SysProcessAdminOperateRequest request =
                operate(SysProcessConstants.ADMIN_OPERATE_CHANGE_HANDLER, null, "更换审批人");
        request.setWorkitemId(WI_U1);
        request.setChangeToId(U2);
        service.adminOperate(request);
        assertEquals(U2, workitem.getFieldHandlerId());

        // 原处理人 U1 失效：requireProcessingWorkitem 身份校验拒绝
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_SINGLE));
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        // 节点3 全体序位工作项（独立对象，expect 处理人 U1/U2 完整，保证 node4 自动跳过口径与原逻辑一致）
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING),
                workitem(WI_U2, NODE3_ID, U2, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));
        AuthorizationException forbidden = assertThrows(AuthorizationException.class,
                () -> service.pass(EXAMPLE_ID, WI_U1, "原处理人审批"));
        assertEquals("FORBIDDEN", forbidden.getCode());
        assertTrue(forbidden.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_FORBIDDEN));

        // 新处理人 U2 可操作：通过后节点办结并流转
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U2, "李四"));
        service.pass(EXAMPLE_ID, WI_U1, "新处理人审批");
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_HANDLER_PASS.equals(n.getFieldHandleType())
                        && U2.equals(n.getFieldHandlerId())));
        assertEquals(SysProcessConstants.NODE_STATUS_FINISH, exampleNodeAt(3).getFieldStatus());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_FINISH, example.getFieldStatus());
    }

    @Test
    void adminChangeHandlerRejectsInvalidStateWorkitemAndTarget() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);

        // 流程非审批中 → STATE_CONFLICT
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_DRAFT);
        IllegalArgumentException stateError = assertThrows(IllegalArgumentException.class,
                () -> service.adminOperate(changeHandlerRequest(WI_U1, U2)));
        assertTrue(stateError.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_STATE_CONFLICT));
        assertTrue(stateError.getMessage().contains("仅审批中的流程可更换审批人"));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);

        // 非 PROCESSING 工作项 → STATE_CONFLICT（仅处理中的工作项可更换处理人）
        when(workitemDao.selectById(WI_U1)).thenReturn(workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_FINISH));
        IllegalArgumentException workitemError = assertThrows(IllegalArgumentException.class,
                () -> service.adminOperate(changeHandlerRequest(WI_U1, U2)));
        assertTrue(workitemError.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_STATE_CONFLICT));
        assertTrue(workitemError.getMessage().contains("仅处理中的工作项可更换处理人"));

        // 工作项不属于本实例 → 工作项不存在
        SysProcessExampleWorkitem foreign = workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        foreign.setFieldExampleId("other-example-id");
        when(workitemDao.selectById(WI_U1)).thenReturn(foreign);
        IllegalArgumentException foreignError = assertThrows(IllegalArgumentException.class,
                () -> service.adminOperate(changeHandlerRequest(WI_U1, U2)));
        assertTrue(foreignError.getMessage().contains("工作项不存在"));

        // changeToId 不存在 → PARAM_MISSING（允许禁用用户，未注册用户拒绝）
        when(workitemDao.selectById(WI_U1)).thenReturn(workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        IllegalArgumentException targetError = assertThrows(IllegalArgumentException.class,
                () -> service.adminOperate(changeHandlerRequest(WI_U1, "no-such-user")));
        assertTrue(targetError.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(targetError.getMessage().contains("更换审批人目标用户不存在"));

        // 缺 workitemId / changeToId → PARAM_MISSING；缺 content → 意见必填
        IllegalArgumentException missingWorkitem = assertThrows(IllegalArgumentException.class,
                () -> service.adminOperate(changeHandlerRequest(null, U2)));
        assertTrue(missingWorkitem.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(missingWorkitem.getMessage().contains("workitemId"));
        IllegalArgumentException missingTarget = assertThrows(IllegalArgumentException.class,
                () -> service.adminOperate(changeHandlerRequest(WI_U1, null)));
        assertTrue(missingTarget.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(missingTarget.getMessage().contains("changeToId"));
        SysProcessAdminOperateRequest missingContent =
                operate(SysProcessConstants.ADMIN_OPERATE_CHANGE_HANDLER, null, null);
        missingContent.setWorkitemId(WI_U1);
        missingContent.setChangeToId(U2);
        assertThrows(IllegalArgumentException.class, () -> service.adminOperate(missingContent));
        verify(workitemDao, never()).update(any(SysProcessExampleWorkitem.class));
        verify(auditNoteDao, never()).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_ADMIN_CHANGE_HANDLER.equals(n.getFieldHandleType())));
    }

    /** 更换审批人请求（workitemId / changeToId 可空以便缺参用例）。 */
    private SysProcessAdminOperateRequest changeHandlerRequest(String workitemId, String changeToId) {
        SysProcessAdminOperateRequest request =
                operate(SysProcessConstants.ADMIN_OPERATE_CHANGE_HANDLER, null, "更换审批人");
        request.setWorkitemId(workitemId);
        request.setChangeToId(changeToId);
        return request;
    }

    @Test
    void adminJumpByTargetNodeKeyReentersFlow() {
        // JUMP 按 targetNodeKey 解析目标节点（targetNodeId 为空时按实例节点集合 nodeKey 匹配）
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectById(NODE4_ID)).thenReturn(exampleNodeAt(4));
        when(workitemDao.selectActiveByExample(EXAMPLE_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        SysProcessAdminOperateRequest request = operate(SysProcessConstants.ADMIN_OPERATE_JUMP, null, "按 key 跳转");
        request.setTargetNodeKey("N4");

        service.adminOperate(request);

        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_ADMIN_JUMP.equals(n.getFieldHandleType())));
        verify(workitemDao, times(2)).insert(argThat(w -> NODE4_ID.equals(w.getFieldNodeId())));
    }

    @Test
    void adminJumpRejectsInvalidTargetNodeKeyAndMissingBoth() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);

        // 非法 key（不是本实例节点）→ CS_PROCESS_NODE_TARGET_INVALID
        SysProcessAdminOperateRequest invalid = operate(SysProcessConstants.ADMIN_OPERATE_JUMP, null, "跳转");
        invalid.setTargetNodeKey("N9");
        IllegalArgumentException invalidError = assertThrows(IllegalArgumentException.class,
                () -> service.adminOperate(invalid));
        assertTrue(invalidError.getMessage().startsWith(SysProcessConstants.CS_PROCESS_NODE_TARGET_INVALID));
        assertTrue(invalidError.getMessage().contains("N9"));

        // targetNodeId 与 targetNodeKey 均为空 → PARAM_MISSING（跳转节点必填）
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> service.adminOperate(operate(SysProcessConstants.ADMIN_OPERATE_JUMP, null, "跳转")));
        assertTrue(missing.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(missing.getMessage().contains("跳转节点必填"));
        verify(auditNoteDao, never()).insert(any(SysProcessAuditNote.class));
    }

    @Test
    void adminJumpPrefersTargetNodeIdOverTargetNodeKey() {
        // targetNodeId 与 targetNodeKey 同时提供：targetNodeId 优先
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        when(nodeDao.selectById(NODE4_ID)).thenReturn(exampleNodeAt(4));
        when(workitemDao.selectActiveByExample(EXAMPLE_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        SysProcessAdminOperateRequest request =
                operate(SysProcessConstants.ADMIN_OPERATE_JUMP, NODE3_ID, "targetNodeId 优先");
        request.setTargetNodeKey("N4");

        service.adminOperate(request);

        verify(workitemDao, times(2)).insert(argThat(w -> NODE3_ID.equals(w.getFieldNodeId())));
        verify(workitemDao, never()).insert(argThat(w -> NODE4_ID.equals(w.getFieldNodeId())));
    }

    /* ==================== 统一审批操作通道（operation/operate，P17-BE-008） ==================== */

    private static final String NODE_MANUAL_ID = "12345678901234567890123456789013";
    private static final String NODE_END7_ID = "12345678901234567890123456789014";
    private static final String NODE_END8_ID = "12345678901234567890123456789015";

    /** 人工决策节点配置：流向决策来源含起草节点（N2）与审批节点3（N3），默认流向 N7 */
    private static final String CONFIG_MANUAL =
            "{\"defaultFlowKey\":\"N7\",\"flowDecisions\":[\"N2\",\"N3\"]}";

    private SysProcessOperateRequest op(String operateType) {
        SysProcessOperateRequest request = new SysProcessOperateRequest();
        request.setExampleId(EXAMPLE_ID);
        request.setOperateType(operateType);
        return request;
    }

    /** 装配含人工决策节点的实例图：N1(START)→N2(DRAFT)→N3(APPROVE)→N6(MANUAL)→N7/N8(END) */
    private void setUpManualGraph() {
        exampleNodes.clear();
        exampleNodes.addAll(List.of(
                exampleNode("12345678901234567890123456789091", 1, "开始节点", "START",
                        SysProcessConstants.NODE_STATUS_FINISH, null),
                exampleNode("12345678901234567890123456789092", 2, "草稿节点", "DRAFT",
                        SysProcessConstants.NODE_STATUS_FINISH, null),
                exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE", SysProcessConstants.NODE_STATUS_WAITING, CONFIG_SINGLE),
                exampleNode(NODE_MANUAL_ID, 6, "人工决策节点", "MANUAL",
                        SysProcessConstants.NODE_STATUS_WAITING, CONFIG_MANUAL),
                exampleNode(NODE_END7_ID, 7, "结束节点7", "END", SysProcessConstants.NODE_STATUS_WAITING, null),
                exampleNode(NODE_END8_ID, 8, "结束节点8", "END", SysProcessConstants.NODE_STATUS_WAITING, null)));
        when(exampleLineDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(manualFlowLines());
    }

    /** 人工决策图连线：N1→N2→N3→N6，N6 双出边至 N7/N8 */
    private static List<SysProcessExampleLine> manualFlowLines() {
        List<SysProcessExampleLine> lines = new ArrayList<>();
        lines.add(exampleLine("L1", "N1", "N2"));
        lines.add(exampleLine("L2", "N2", "N3"));
        lines.add(exampleLine("L3", "N3", "N6"));
        lines.add(exampleLine("L4", "N6", "N7"));
        lines.add(exampleLine("L5", "N6", "N8"));
        return lines;
    }

    private static SysOrgUser userWithRoles(String id, String name, String... roleCodes) {
        SysOrgUser user = user(id, name);
        List<SysOrgRole> roles = new ArrayList<>();
        for (String code : roleCodes) {
            SysOrgRole role = new SysOrgRole();
            role.setFieldCode(code);
            roles.add(role);
        }
        user.setRoles(roles);
        return user;
    }

    @Test
    void operateLegacyUppercaseDrafterBackStaysBackwardCompatible() {
        // 既有大写操作类型（audit 转发通道）分发逻辑不变，向后兼容
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(workitemDao.selectActiveByExample(EXAMPLE_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));

        SysProcessExample result = service.operate(EXAMPLE_ID, SysProcessConstants.AUDIT_TYPE_DRAFTER_BACK,
                null, "收回", null, null);

        assertNotNull(result);
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_DRAFT, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_DRAFTER_BACK.equals(n.getFieldHandleType())));
    }

    @Test
    void operateDispatchesDrafterSubmit() {
        SysProcessExample result = service.operate(EXAMPLE_ID, SysProcessConstants.OPERATE_TYPE_DRAFTER_SUBMIT,
                null, "请审批", null, null);

        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PROCESSING, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_DRAFTER_SUBMIT.equals(n.getFieldHandleType())));
        verify(workitemDao, times(2)).insert(argThat(w -> NODE3_ID.equals(w.getFieldNodeId())
                && SysProcessConstants.WORKITEM_STATUS_PROCESSING.equals(w.getFieldStatus())));
    }

    @Test
    void operateDispatchesDrafterCancel() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_RETURN);

        SysProcessExample result = service.operate(EXAMPLE_ID, SysProcessConstants.OPERATE_TYPE_DRAFTER_CANCEL,
                null, "不要了", null, null);

        assertEquals(SysProcessConstants.EXAMPLE_STATUS_ABANDON, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_DRAFTER_ABANDON.equals(n.getFieldHandleType())));
    }

    @Test
    void operateDispatchesDrafterRetract() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(workitemDao.selectActiveByExample(EXAMPLE_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));

        SysProcessExample result = service.operate(EXAMPLE_ID, SysProcessConstants.OPERATE_TYPE_DRAFTER_RETRACT,
                null, null, null, null);

        assertEquals(SysProcessConstants.EXAMPLE_STATUS_DRAFT, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_DRAFTER_BACK.equals(n.getFieldHandleType())));
    }

    @Test
    void operateDispatchesHandlerPass() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleNode node3 = exampleNodeAt(3);
        when(nodeDao.selectById(NODE3_ID)).thenReturn(node3);
        when(workitemDao.selectById(WI_U1)).thenReturn(workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_FINISH),
                workitem(WI_U2, NODE3_ID, U2, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        SysProcessExample result = service.operate(EXAMPLE_ID, SysProcessConstants.OPERATE_TYPE_HANDLER_PASS,
                WI_U1, "同意", null, null);

        assertEquals(SysProcessConstants.EXAMPLE_STATUS_FINISH, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_HANDLER_PASS.equals(n.getFieldHandleType())));
    }

    @Test
    void operateDispatchesHandlerRejectWithResumeMode() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleNode node3 = exampleNodeAt(3);
        when(nodeDao.selectById(NODE3_ID)).thenReturn(node3);
        when(workitemDao.selectById(WI_U1)).thenReturn(workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(
                workitem(WI_U2, NODE3_ID, U2, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));
        SysProcessOperateRequest request = op(SysProcessConstants.OPERATE_TYPE_HANDLER_REJECT);
        request.setWorkitemId(WI_U1);
        request.setRejectTargetKey("N2");
        request.setResumeMode(SysProcessConstants.RESUME_MODE_BACK_TO_NODE);
        request.setContent("金额不对");

        SysProcessExample result = service.operate(request);

        assertEquals(SysProcessConstants.EXAMPLE_STATUS_RETURN, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_HANDLER_REJECT.equals(n.getFieldHandleType())));
        verify(workitemDao).update(argThat(w -> WI_U1.equals(w.getFieldId())
                && null != w.getFieldConfig() && w.getFieldConfig().contains("backToNode")));
    }

    @Test
    void operateDispatchesAdminAbandonWithProcessAdminRole() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectByExampleAndType(EXAMPLE_ID, SysProcessConstants.NODE_TYPE_END))
                .thenReturn(exampleNodeAt(5));
        userUtil.when(UserUtil::getCurrentUser)
                .thenReturn(userWithRoles(U1, "张三", SysProcessConstants.ROLE_SYS_PROCESS_EXAMPLE_ADMIN));

        SysProcessExample result = service.operate(EXAMPLE_ID, SysProcessConstants.OPERATE_TYPE_ADMIN_ABANDON,
                null, "作废", null, null);

        assertEquals(SysProcessConstants.EXAMPLE_STATUS_ABANDON, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_ADMIN_ABANDON.equals(n.getFieldHandleType())));
    }

    @Test
    void operateDispatchesAdminJumpWithExtraAdminRole() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectById(NODE4_ID)).thenReturn(exampleNodeAt(4));
        when(workitemDao.selectActiveByExample(EXAMPLE_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        userUtil.when(UserUtil::getCurrentUser)
                .thenReturn(userWithRoles(U1, "张三", "SYS_ORG_BIZ_ADMIN"));

        SysProcessExample result = service.operate(EXAMPLE_ID, SysProcessConstants.OPERATE_TYPE_ADMIN_JUMP,
                null, "跳过节点3", null, NODE4_ID, "SYS_ORG_BIZ_ADMIN");

        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_ADMIN_JUMP.equals(n.getFieldHandleType())));
        // 跳转目标为审批节点：进入该节点重新创建工作项
        verify(workitemDao, times(2)).insert(argThat(w -> NODE4_ID.equals(w.getFieldNodeId())));
        assertNotNull(result);
    }

    @Test
    void operateAdminAbandonForbiddenWithoutAdminRole() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        AuthorizationException error = assertThrows(AuthorizationException.class,
                () -> service.operate(EXAMPLE_ID, SysProcessConstants.OPERATE_TYPE_ADMIN_ABANDON,
                        null, "作废", null, null));
        assertEquals("FORBIDDEN", error.getCode());
        assertTrue(error.getMessage().contains("无流程管理员权限"));
    }

    @Test
    void operateAdminJumpRequiresTargetNodeId() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        userUtil.when(UserUtil::getCurrentUser)
                .thenReturn(userWithRoles(U1, "张三", SysProcessConstants.ROLE_SYS_PROCESS_EXAMPLE_ADMIN));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.operate(EXAMPLE_ID, SysProcessConstants.OPERATE_TYPE_ADMIN_JUMP,
                        null, "跳转", null, null));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(error.getMessage().contains("targetNodeId"));
    }

    @Test
    void operateHandlerPassRequiresWorkitemId() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.operate(EXAMPLE_ID, SysProcessConstants.OPERATE_TYPE_HANDLER_PASS,
                        null, "同意", null, null));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(error.getMessage().contains("workitemId"));
    }

    @Test
    void operateHandlerRejectRequiresRejectTargetKeyAndResumeMode() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));
        SysProcessOperateRequest missingKey = op(SysProcessConstants.OPERATE_TYPE_HANDLER_REJECT);
        missingKey.setWorkitemId(WI_U1);
        missingKey.setResumeMode(SysProcessConstants.RESUME_MODE_RESTART);
        IllegalArgumentException keyError = assertThrows(IllegalArgumentException.class,
                () -> service.operate(missingKey));
        assertTrue(keyError.getMessage().contains("rejectTargetKey"));

        SysProcessOperateRequest missingMode = op(SysProcessConstants.OPERATE_TYPE_HANDLER_REJECT);
        missingMode.setWorkitemId(WI_U1);
        missingMode.setRejectTargetKey("N2");
        IllegalArgumentException modeError = assertThrows(IllegalArgumentException.class,
                () -> service.operate(missingMode));
        assertTrue(modeError.getMessage().contains("resumeMode"));
    }

    @Test
    void operateDispatchesDrafterExpediteAndProcessedExpedite() {
        // 统一操作通道分发催办（P17-BE-011）：drafter_expedite 起草人触发
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_COUNTERSIGN));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectByExampleAndStatus(EXAMPLE_ID, SysProcessConstants.NODE_STATUS_PROCESSING))
                .thenReturn(List.of(exampleNodeAt(3)));
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(CREATOR_ID, "起草人"));

        service.operate(EXAMPLE_ID, SysProcessConstants.OPERATE_TYPE_DRAFTER_EXPEDITE,
                null, "起草人催办", null, null);

        verify(notifyTodoService).send(any(SysNotifyTodo.class),
                argThat(ids -> ids.size() == 1 && ids.get(0).equals(U1)));
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_DRAFTER_EXPEDITE.equals(n.getFieldHandleType())));

        // processed_expedite 已审批人触发（本人 FINISH 工作项）
        when(workitemDao.selectById(WI_U2)).thenReturn(
                workitem(WI_U2, NODE3_ID, U2, SysProcessConstants.WORKITEM_STATUS_FINISH));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U2, "李四"));

        service.operate(EXAMPLE_ID, SysProcessConstants.OPERATE_TYPE_PROCESSED_EXPEDITE,
                WI_U2, "已审批人催办", null, null);

        verify(notifyTodoService, times(2)).send(any(SysNotifyTodo.class), anyList());
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_PROCESSED_EXPEDITE.equals(n.getFieldHandleType())
                        && WI_U2.equals(n.getFieldWorkitemId())));
    }

    @Test
    void operateExpediteRequiresContentAndProcessedExpediteRequiresWorkitemId() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);

        // drafter_expedite 缺 content → PARAM_MISSING
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.operate(EXAMPLE_ID, SysProcessConstants.OPERATE_TYPE_DRAFTER_EXPEDITE,
                        null, null, null, null));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(error.getMessage().contains("content"));

        // processed_expedite 缺 workitemId → PARAM_MISSING
        error = assertThrows(IllegalArgumentException.class,
                () -> service.operate(EXAMPLE_ID, SysProcessConstants.OPERATE_TYPE_PROCESSED_EXPEDITE,
                        null, "催办", null, null));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(error.getMessage().contains("workitemId"));

        // processed_expedite 缺 content → PARAM_MISSING
        error = assertThrows(IllegalArgumentException.class,
                () -> service.operate(EXAMPLE_ID, SysProcessConstants.OPERATE_TYPE_PROCESSED_EXPEDITE,
                        WI_U1, null, null, null));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(error.getMessage().contains("content"));
        verify(notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
    }

    @Test
    void operateUnknownTypeFails() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.operate(EXAMPLE_ID, "foo_bar", null, null, null, null));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_TYPE_INVALID));
        assertTrue(error.getMessage().contains("非法的流程操作类型"));
    }

    @Test
    void operateAllFourteenLowercaseTypesAreWired() {
        // 占位清单清零（P17-BE-013）：14 类小写操作类型全部接通真实引擎分发，
        // 任何一类都不再报「非法的流程操作类型/暂未开放」（TYPE_INVALID）；
        // 流程 FINISH 状态 + 非管理员用户下，各类型均以状态/参数/身份错误拦截
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_FINISH);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(CREATOR_ID, "起草人"));
        List<String> allTypes = List.of(
                SysProcessConstants.OPERATE_TYPE_DRAFTER_SUBMIT,
                SysProcessConstants.OPERATE_TYPE_DRAFTER_CANCEL,
                SysProcessConstants.OPERATE_TYPE_DRAFTER_RETRACT,
                SysProcessConstants.OPERATE_TYPE_DRAFTER_EXPEDITE,
                SysProcessConstants.OPERATE_TYPE_HANDLER_PASS,
                SysProcessConstants.OPERATE_TYPE_HANDLER_REJECT,
                SysProcessConstants.OPERATE_TYPE_HANDLER_CC,
                SysProcessConstants.OPERATE_TYPE_HANDLER_TRANSFER,
                SysProcessConstants.OPERATE_TYPE_HANDLER_TRANSFER_RETRACT,
                SysProcessConstants.OPERATE_TYPE_PROCESSED_RETRACT,
                SysProcessConstants.OPERATE_TYPE_PROCESSED_EXPEDITE,
                SysProcessConstants.OPERATE_TYPE_ADMIN_ABANDON,
                SysProcessConstants.OPERATE_TYPE_ADMIN_JUMP,
                SysProcessConstants.OPERATE_TYPE_ADMIN_CHANGE_HANDLER);
        for (String type : allTypes) {
            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> service.operate(EXAMPLE_ID, type, null, null, null, null),
                    "操作类型 " + type + " 应以状态/参数/身份错误拦截");
            assertFalse(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_TYPE_INVALID),
                    "操作类型 " + type + " 不应报「非法的流程操作类型/暂未开放」: " + error.getMessage());
        }
        // 未登记类型仍拒绝（TYPE_INVALID）
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> service.operate(EXAMPLE_ID, "foo_bar", null, null, null, null));
        assertTrue(unknown.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_TYPE_INVALID));
        assertTrue(unknown.getMessage().contains("非法的流程操作类型"));
    }

    @Test
    void operateMissingExampleIdOrOperateTypeFails() {
        SysProcessOperateRequest missingExample = new SysProcessOperateRequest();
        missingExample.setOperateType(SysProcessConstants.OPERATE_TYPE_DRAFTER_SUBMIT);
        IllegalArgumentException exampleError = assertThrows(IllegalArgumentException.class,
                () -> service.operate(missingExample));
        assertTrue(exampleError.getMessage().contains("exampleId"));

        SysProcessOperateRequest missingType = new SysProcessOperateRequest();
        missingType.setExampleId(EXAMPLE_ID);
        IllegalArgumentException typeError = assertThrows(IllegalArgumentException.class,
                () -> service.operate(missingType));
        assertTrue(typeError.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(typeError.getMessage().contains("operateType"));
    }

    @Test
    void operateExampleNotFoundFails() {
        when(exampleDao.selectById("not-exist-example-id")).thenReturn(null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.operate("not-exist-example-id", SysProcessConstants.OPERATE_TYPE_DRAFTER_SUBMIT,
                        null, null, null, null));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_EXAMPLE_NOT_FOUND));
    }

    @Test
    void operateDrafterSubmitForbiddenForNonDrafter() {
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        AuthorizationException error = assertThrows(AuthorizationException.class,
                () -> service.operate(EXAMPLE_ID, SysProcessConstants.OPERATE_TYPE_DRAFTER_SUBMIT,
                        null, null, null, null));
        assertEquals("FORBIDDEN", error.getCode());
        assertTrue(error.getMessage().contains("仅流程起草人可执行该操作"));
    }

    @Test
    void operateHandlerPassForbiddenForNonHandler() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        when(workitemDao.selectById(WI_U1)).thenReturn(workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U2, "李四"));

        AuthorizationException error = assertThrows(AuthorizationException.class,
                () -> service.operate(EXAMPLE_ID, SysProcessConstants.OPERATE_TYPE_HANDLER_PASS,
                        WI_U1, "同意", null, null));
        assertEquals("FORBIDDEN", error.getCode());
        assertTrue(error.getMessage().contains("仅工作项处理人可执行审批操作"));
    }

    @Test
    void operateStateConflictPrefixCarriedByOperateChannel() {
        // 状态前置条件失败：DRAFT 状态执行 drafter_retract（仅 PROCESSING 可收回）→ STATE_CONFLICT 前缀
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.operate(EXAMPLE_ID, SysProcessConstants.OPERATE_TYPE_DRAFTER_RETRACT,
                        null, null, null, null));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_STATE_CONFLICT));
    }

    @Test
    void operateHandlerPassWithValidFlowChoiceSavesWorkitemConfigAndFollowsChoice() {
        setUpManualGraph();
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        // 内存捕获留痕与工作项列表：引擎通过（enterManual）需读取 HANDLER_PASS 留痕与工作项 flowChoice
        List<SysProcessAuditNote> auditNotes = new ArrayList<>();
        when(auditNoteDao.insert(any(SysProcessAuditNote.class))).thenAnswer(invocation -> {
            auditNotes.add(invocation.getArgument(0));
            return 1;
        });
        when(auditNoteDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(auditNotes);
        SysProcessExampleWorkitem passed = workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        when(workitemDao.selectById(WI_U1)).thenReturn(passed);
        when(workitemDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(List.of(passed));
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_FINISH),
                workitem(WI_U2, NODE3_ID, U2, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));
        // flowChoice 为 v1_7 新增参数，经请求体（SysProcessOperateRequest）承载
        SysProcessOperateRequest request = op(SysProcessConstants.OPERATE_TYPE_HANDLER_PASS);
        request.setWorkitemId(WI_U1);
        request.setContent("同意");
        request.setFlowChoice("N8");

        SysProcessExample result = service.operate(request);

        // flowChoice 落库到工作项 field_config（保留既有字段），引擎按人工流向进入 N8（END）→ FINISH；
        // WI_U1 共 update 两次：finishWorkitem（置 FINISH）与 flowChoice 配置回写
        verify(workitemDao, times(2)).update(argThat(w -> WI_U1.equals(w.getFieldId())
                && null != w.getFieldConfig() && w.getFieldConfig().contains("\"flowChoice\":\"N8\"")));
        verify(nodeDao).update(argThat(n -> NODE_MANUAL_ID.equals(n.getFieldId())
                && SysProcessConstants.NODE_STATUS_FINISH.equals(n.getFieldStatus())));
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_FINISH, result.getFieldStatus());
    }

    @Test
    void operateHandlerPassWithInvalidFlowChoiceFails() {
        setUpManualGraph();
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        when(workitemDao.selectById(WI_U1)).thenReturn(workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));
        SysProcessOperateRequest request = op(SysProcessConstants.OPERATE_TYPE_HANDLER_PASS);
        request.setWorkitemId(WI_U1);
        request.setContent("同意");
        request.setFlowChoice("N9");

        // N9 不是人工决策节点（N6）的实际出边目标 → CS_PROCESS_NODE_TARGET_INVALID，且不产生任何流转写操作
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.operate(request));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_NODE_TARGET_INVALID));
        verify(auditNoteDao, never()).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_HANDLER_PASS.equals(n.getFieldHandleType())));
    }

    @Test
    void operateDrafterSubmitWithFlowChoiceWritesDraftRuntime() {
        setUpManualGraph();
        // 起草节点为决策来源（flowDecisions 含 N2）：提交携带 flowChoice → 写入起草节点运行态
        SysProcessOperateRequest request = op(SysProcessConstants.OPERATE_TYPE_DRAFTER_SUBMIT);
        request.setContent("请审批");
        request.setFlowChoice("N8");

        SysProcessExample result = service.operate(request);

        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PROCESSING, result.getFieldStatus());
        verify(nodeDao).update(argThat(n -> 2 == n.getFieldNodeNo()
                && null != n.getFieldRuntime() && n.getFieldRuntime().contains("\"flowChoice\":\"N8\"")));
        verify(workitemDao, times(2)).insert(argThat(w -> NODE3_ID.equals(w.getFieldNodeId())));
    }

    @Test
    void operateDrafterSubmitWithInvalidFlowChoiceFails() {
        setUpManualGraph();
        SysProcessOperateRequest request = op(SysProcessConstants.OPERATE_TYPE_DRAFTER_SUBMIT);
        request.setContent("请审批");
        request.setFlowChoice("N9");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.operate(request));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_NODE_TARGET_INVALID));
        verify(auditNoteDao, never()).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_DRAFTER_SUBMIT.equals(n.getFieldHandleType())));
    }

    /* ==================== 转办 / 转办收回 / 已审批人收回（P17-BE-010） ==================== */

    private static final String U3 = "12345678901234567890123456789006";
    private static final String WI_TRANSFERRED = "12345678901234567890123456789030";
    private static final String WI_CHAIN_B = "12345678901234567890123456789031";
    private static final String WI_CHAIN_C = "12345678901234567890123456789032";
    private static final String NODE_FORK_ID = "12345678901234567890123456789016";
    private static final String NODE_BR1_ID = "12345678901234567890123456789017";
    private static final String NODE_BR2_ID = "12345678901234567890123456789018";
    private static final String NODE_JOIN_ID = "12345678901234567890123456789019";
    private static final String NODE_END9_ID = "12345678901234567890123456789020";

    /** 串行审批节点配置：U1→U2 依次审批（autoSkip=no 隔离自动跳过干扰） */
    private static final String CONFIG_SERIAL =
            "{\"handlerIds\":\"" + U1 + ";" + U2 + "\",\"handlerNames\":\"张三;李四\","
                    + "\"approveMode\":\"serial\",\"triggerType\":\"fixed\","
                    + "\"autoSkip\":\"no\",\"autoContinueSkip\":\"system\"}";
    private static final String CONFIG_BRANCH_A =
            "{\"handlerIds\":\"" + U1 + "\",\"approveMode\":\"single\",\"autoSkip\":\"no\"}";
    private static final String CONFIG_BRANCH_B =
            "{\"handlerIds\":\"" + U2 + "\",\"approveMode\":\"single\",\"autoSkip\":\"no\"}";
    private static final String CONFIG_FORK = "{\"joinMode\":\"all\",\"noBranchMode\":\"continue\"}";

    /** 内存工作项仓库：insert 捕获 + 按 example/node 查询读同一份列表（对齐 FlowEngineFixture 口径） */
    private final List<SysProcessExampleWorkitem> liveWorkitems = new ArrayList<>();
    private final List<SysProcessExampleWorkitem> insertedWorkitems = new ArrayList<>();

    private void captureWorkitems() {
        liveWorkitems.clear();
        insertedWorkitems.clear();
        when(workitemDao.insert(any(SysProcessExampleWorkitem.class))).thenAnswer(invocation -> {
            SysProcessExampleWorkitem workitem = invocation.getArgument(0);
            if (null == workitem.getFieldCreateTime()) {
                workitem.setFieldCreateTime(new java.util.Date());
            }
            liveWorkitems.add(workitem);
            insertedWorkitems.add(workitem);
            return 1;
        });
        when(workitemDao.selectListByExampleId(EXAMPLE_ID)).thenAnswer(invocation -> List.copyOf(liveWorkitems));
        when(workitemDao.selectListByNodeId(anyString())).thenAnswer(invocation -> {
            String nodeId = invocation.getArgument(0);
            List<SysProcessExampleWorkitem> matched = new ArrayList<>();
            for (SysProcessExampleWorkitem workitem : liveWorkitems) {
                if (nodeId.equals(workitem.getFieldNodeId())) {
                    matched.add(workitem);
                }
            }
            return matched;
        });
    }

    /** 取引擎/服务已插入的指定处理人工作项（仅捕获 insert 后生成的，不含预置工作项） */
    private SysProcessExampleWorkitem insertedWorkitem(String handlerId) {
        for (SysProcessExampleWorkitem workitem : insertedWorkitems) {
            if (handlerId.equals(workitem.getFieldHandlerId())) {
                return workitem;
            }
        }
        throw new IllegalStateException("no inserted workitem for handler " + handlerId);
    }

    /** 装配并行分支实例图：N1(START)→N2(DRAFT)→N3(FORK)→[N4/N5 分支审批]→N6(JOIN)→N7(END) */
    private void setUpParallelGraph() {
        exampleNodes.clear();
        exampleNodes.addAll(List.of(
                exampleNode("12345678901234567890123456789091", 1, "开始节点", "START",
                        SysProcessConstants.NODE_STATUS_FINISH, null),
                exampleNode("12345678901234567890123456789092", 2, "草稿节点", "DRAFT",
                        SysProcessConstants.NODE_STATUS_FINISH, null),
                exampleNode(NODE_FORK_ID, 3, "并行开始", "FORK",
                        SysProcessConstants.NODE_STATUS_WAITING, CONFIG_FORK),
                exampleNode(NODE_BR1_ID, 4, "分支A审批", "APPROVE",
                        SysProcessConstants.NODE_STATUS_WAITING, CONFIG_BRANCH_A),
                exampleNode(NODE_BR2_ID, 5, "分支B审批", "APPROVE",
                        SysProcessConstants.NODE_STATUS_WAITING, CONFIG_BRANCH_B),
                exampleNode(NODE_JOIN_ID, 6, "并行结束", "JOIN",
                        SysProcessConstants.NODE_STATUS_WAITING, null),
                exampleNode(NODE_END9_ID, 7, "结束节点", "END",
                        SysProcessConstants.NODE_STATUS_WAITING, null)));
        List<SysProcessExampleLine> lines = new ArrayList<>();
        lines.add(exampleLine("L1", "N1", "N2"));
        lines.add(exampleLine("L2", "N2", "N3"));
        lines.add(exampleLine("L3", "N3", "N4"));
        lines.add(exampleLine("L4", "N3", "N5"));
        lines.add(exampleLine("L5", "N4", "N6"));
        lines.add(exampleLine("L6", "N5", "N6"));
        lines.add(exampleLine("L7", "N6", "N7"));
        when(exampleLineDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(lines);
        when(nodeDao.selectByExampleAndType(EXAMPLE_ID, SysProcessConstants.NODE_TYPE_DRAFT))
                .thenReturn(exampleNodeAt(2));
        when(nodeDao.selectByExampleAndType(EXAMPLE_ID, SysProcessConstants.NODE_TYPE_END))
                .thenReturn(exampleNodeAt(7));
    }

    @Test
    void transferSingleModeAbandonsOriginalAndTransfereeCanPassNode() {
        captureWorkitems();
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleWorkitem original = workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        liveWorkitems.add(original);
        when(workitemDao.selectById(WI_U1)).thenReturn(original);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        service.transfer(EXAMPLE_ID, WI_U1, U2, "转办给李四");

        // 原工作项 ABANDON（记完成时间）+ 新工作项关联 from_workitem_id
        assertEquals(SysProcessConstants.WORKITEM_STATUS_ABANDON, original.getFieldStatus());
        assertNotNull(original.getFieldFinishTime());
        SysProcessExampleWorkitem transferred = insertedWorkitem(U2);
        assertEquals(WI_U1, transferred.getFieldFromWorkitemId());
        assertEquals(NODE3_ID, transferred.getFieldNodeId());
        assertEquals(U2, transferred.getFieldHandlerId());
        assertEquals(U2, transferred.getFieldExpectHandlerId());
        assertEquals(SysProcessConstants.WORKITEM_STATUS_PROCESSING, transferred.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_HANDLER_TRANSFER.equals(n.getFieldHandleType())
                        && WI_U1.equals(n.getFieldWorkitemId())
                        && U1.equals(n.getFieldHandlerId())
                        && "转办给李四".equals(n.getFieldContent())));
        // 被转办人可通过并推进节点（单签通过即节点完成，流转到下一节点后经自动跳过办结）
        when(workitemDao.selectById(transferred.getFieldId())).thenReturn(transferred);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U2, "李四"));

        service.pass(EXAMPLE_ID, transferred.getFieldId(), "同意");

        assertEquals(SysProcessConstants.NODE_STATUS_FINISH, exampleNodeAt(3).getFieldStatus());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_FINISH, example.getFieldStatus());
    }

    @Test
    void transferCountersignKeepsBothPositionsAndNodePassesAfterTransfereeApproves() {
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_COUNTERSIGN));
        captureWorkitems();
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleWorkitem wiU1 = workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        SysProcessExampleWorkitem wiU2 = workitem(WI_U2, NODE3_ID, U2,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        liveWorkitems.add(wiU1);
        liveWorkitems.add(wiU2);
        when(workitemDao.selectById(WI_U1)).thenReturn(wiU1);
        when(workitemDao.selectById(WI_U2)).thenReturn(wiU2);
        when(userMapper.selectById(U3)).thenReturn(user(U3, "王五"));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        service.transfer(EXAMPLE_ID, WI_U1, U3, "转办给王五");

        // 会签多工作项并存：U2 原工作项与被转办人新工作项均 PROCESSING
        SysProcessExampleWorkitem transferred = insertedWorkitem(U3);
        assertEquals(WI_U1, transferred.getFieldFromWorkitemId());
        assertEquals(SysProcessConstants.WORKITEM_STATUS_PROCESSING, wiU2.getFieldStatus());

        // 被转办人先通过：U2 未通过，节点保持审批中
        when(workitemDao.selectById(transferred.getFieldId())).thenReturn(transferred);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U3, "王五"));
        service.pass(EXAMPLE_ID, transferred.getFieldId(), "同意");
        assertEquals(SysProcessConstants.NODE_STATUS_PROCESSING, exampleNodeAt(3).getFieldStatus());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PROCESSING, example.getFieldStatus());

        // U2 通过：转办弃置的原工作项（ABANDON）不阻塞会签通过判定，节点完成并流转办结
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U2, "李四"));
        service.pass(EXAMPLE_ID, WI_U2, "同意");
        assertEquals(SysProcessConstants.NODE_STATUS_FINISH, exampleNodeAt(3).getFieldStatus());
        assertEquals(SysProcessConstants.WORKITEM_STATUS_ABANDON, wiU1.getFieldStatus(),
                "转办弃置的原工作项保持 ABANDON，不随节点通过复活为 FINISH");
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_FINISH, example.getFieldStatus());
    }

    @Test
    void transferSerialCurrentPositionCreatesNextPositionAfterTransfereePasses() {
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_WAITING, CONFIG_SERIAL));
        captureWorkitems();
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        when(userMapper.selectById(U3)).thenReturn(user(U3, "王五"));
        // 提交进入串行节点：仅生成首序位（U1）工作项
        service.submit(EXAMPLE_ID, "请审批");
        SysProcessExampleWorkitem first = insertedWorkitem(U1);
        assertEquals(SysProcessConstants.WORKITEM_STATUS_PROCESSING, first.getFieldStatus());
        when(workitemDao.selectById(first.getFieldId())).thenReturn(first);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        service.transfer(EXAMPLE_ID, first.getFieldId(), U3, "转办给王五");

        // 当前序位转办：原序位工作项 ABANDON，被转办人新工作项承接（视为该序位实际处理人）
        assertEquals(SysProcessConstants.WORKITEM_STATUS_ABANDON, first.getFieldStatus());
        SysProcessExampleWorkitem transferred = insertedWorkitem(U3);
        assertEquals(first.getFieldId(), transferred.getFieldFromWorkitemId());
        assertEquals(SysProcessConstants.WORKITEM_STATUS_PROCESSING, transferred.getFieldStatus());

        // 被转办人通过后引擎照常推进下一序位（U2 工作项生成，节点保持审批中）
        when(workitemDao.selectById(transferred.getFieldId())).thenReturn(transferred);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U3, "王五"));
        service.pass(EXAMPLE_ID, transferred.getFieldId(), "同意");

        SysProcessExampleWorkitem second = insertedWorkitem(U2);
        assertEquals(SysProcessConstants.WORKITEM_STATUS_PROCESSING, second.getFieldStatus());
        assertEquals(SysProcessConstants.NODE_STATUS_PROCESSING, exampleNodeAt(3).getFieldStatus());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PROCESSING, example.getFieldStatus());
    }

    @Test
    void transferToMultipleUsersCreatesWorkitemPerTargetWithSingleAudit() {
        captureWorkitems();
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleWorkitem original = workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        liveWorkitems.add(original);
        when(workitemDao.selectById(WI_U1)).thenReturn(original);
        when(userMapper.selectById(U3)).thenReturn(user(U3, "王五"));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        service.transfer(EXAMPLE_ID, WI_U1, U2 + ";" + U2 + ";" + U3 + ";;", "转办给多人");

        // 去重保序逐人生成新工作项，均关联原工作项；转办给多人留痕一条
        SysProcessExampleWorkitem toU2 = insertedWorkitem(U2);
        SysProcessExampleWorkitem toU3 = insertedWorkitem(U3);
        assertEquals(WI_U1, toU2.getFieldFromWorkitemId());
        assertEquals(WI_U1, toU3.getFieldFromWorkitemId());
        assertEquals(SysProcessConstants.WORKITEM_STATUS_PROCESSING, toU2.getFieldStatus());
        assertEquals(SysProcessConstants.WORKITEM_STATUS_PROCESSING, toU3.getFieldStatus());
        assertEquals(2, insertedWorkitems.size());
        verify(auditNoteDao, times(1)).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_HANDLER_TRANSFER.equals(n.getFieldHandleType())));
    }

    @Test
    void transferRejectsMissingContentBlankTargetsOrUnknownTargetUser() {
        captureWorkitems();
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleWorkitem original = workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        liveWorkitems.add(original);
        when(workitemDao.selectById(WI_U1)).thenReturn(original);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        // 意见必填
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.transfer(EXAMPLE_ID, WI_U1, U2, "  "));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(error.getMessage().contains("content"));
        // transferToIds 空（含纯分隔符）拒绝
        error = assertThrows(IllegalArgumentException.class,
                () -> service.transfer(EXAMPLE_ID, WI_U1, " ; ;", "转办"));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(error.getMessage().contains("transferToIds"));
        // 不存在的转办目标用户拒绝（未注册用户桩默认返回 null）
        error = assertThrows(IllegalArgumentException.class,
                () -> service.transfer(EXAMPLE_ID, WI_U1, "no-such-user", "转办"));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(error.getMessage().contains("转办目标用户不存在"));
        // 流程非审批中拒绝
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_DRAFT);
        error = assertThrows(IllegalArgumentException.class,
                () -> service.transfer(EXAMPLE_ID, WI_U1, U2, "转办"));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_STATE_CONFLICT));
    }

    @Test
    void operateDispatchesHandlerTransferAndRequiresParams() {
        captureWorkitems();
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleWorkitem original = workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        liveWorkitems.add(original);
        when(workitemDao.selectById(WI_U1)).thenReturn(original);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        // workitemId / transferToIds 缺失：PARAM_MISSING（证明已接通真实分发而非暂未开放）
        SysProcessOperateRequest missingWorkitem = op(SysProcessConstants.OPERATE_TYPE_HANDLER_TRANSFER);
        missingWorkitem.setTransferToIds(U2);
        missingWorkitem.setContent("转办");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.operate(missingWorkitem));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(error.getMessage().contains("workitemId"));

        SysProcessOperateRequest missingTargets = op(SysProcessConstants.OPERATE_TYPE_HANDLER_TRANSFER);
        missingTargets.setWorkitemId(WI_U1);
        missingTargets.setContent("转办");
        error = assertThrows(IllegalArgumentException.class,
                () -> service.operate(missingTargets));
        assertTrue(error.getMessage().contains("transferToIds"));

        // 真实分发：转办成功（原工作项 ABANDON + 新工作项 + HANDLER_TRANSFER 留痕）
        SysProcessOperateRequest request = op(SysProcessConstants.OPERATE_TYPE_HANDLER_TRANSFER);
        request.setWorkitemId(WI_U1);
        request.setTransferToIds(U2);
        request.setContent("转办给李四");
        SysProcessExample result = service.operate(request);
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PROCESSING, result.getFieldStatus());
        assertEquals(SysProcessConstants.WORKITEM_STATUS_ABANDON, original.getFieldStatus());
        assertEquals(WI_U1, insertedWorkitem(U2).getFieldFromWorkitemId());
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_HANDLER_TRANSFER.equals(n.getFieldHandleType())));
    }

    @Test
    void operateTransferRetractAndProcessedRetractRequireWorkitemId() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));
        // 两类收回操作经 operate 通道分发：workitemId 缺失 → PARAM_MISSING（非「暂未开放」）
        for (String type : List.of(SysProcessConstants.OPERATE_TYPE_HANDLER_TRANSFER_RETRACT,
                SysProcessConstants.OPERATE_TYPE_PROCESSED_RETRACT)) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> service.operate(EXAMPLE_ID, type, null, "收回", null, null),
                    "操作类型 " + type + " 应已接通真实分发");
            assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING),
                    "操作类型 " + type + " 错误前缀不符");
            assertTrue(error.getMessage().contains("workitemId"));
        }
    }

    @Test
    void transferRetractAbandonsTransferredWorkitemAndRestoresOriginalHandler() {
        captureWorkitems();
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleWorkitem original = workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_ABANDON);
        original.setFieldConfig("{\"flowBranchKey\":\"N3\"}");
        SysProcessExampleWorkitem transferred = workitem(WI_TRANSFERRED, NODE3_ID, U2,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        transferred.setFieldFromWorkitemId(WI_U1);
        liveWorkitems.add(original);
        liveWorkitems.add(transferred);
        when(workitemDao.selectById(WI_U1)).thenReturn(original);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        service.transferRetract(EXAMPLE_ID, WI_U1, "收回转办");

        // 被转办工作项 ABANDON，为原审批人生成新工作项（PROCESSING、from 关联被收回工作项、config 继承）
        assertEquals(SysProcessConstants.WORKITEM_STATUS_ABANDON, transferred.getFieldStatus());
        assertNotNull(transferred.getFieldFinishTime());
        SysProcessExampleWorkitem restored = insertedWorkitem(U1);
        assertEquals(WI_TRANSFERRED, restored.getFieldFromWorkitemId());
        assertEquals(NODE3_ID, restored.getFieldNodeId());
        assertEquals(U1, restored.getFieldHandlerId());
        assertEquals(U1, restored.getFieldExpectHandlerId());
        assertEquals(SysProcessConstants.WORKITEM_STATUS_PROCESSING, restored.getFieldStatus());
        assertEquals("{\"flowBranchKey\":\"N3\"}", restored.getFieldConfig());
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_HANDLER_TRANSFER_RETRACT.equals(n.getFieldHandleType())
                        && WI_U1.equals(n.getFieldWorkitemId())
                        && "收回转办".equals(n.getFieldContent())));
    }

    @Test
    void transferRetractFollowsChainAndAbandonsWholeChain() {
        captureWorkitems();
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        // 转办链 A(U1)→B(U2)→C(U3)：B 已在转办时弃置，C 仍 PROCESSING
        SysProcessExampleWorkitem original = workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_ABANDON);
        SysProcessExampleWorkitem chainB = workitem(WI_CHAIN_B, NODE3_ID, U2,
                SysProcessConstants.WORKITEM_STATUS_ABANDON);
        chainB.setFieldFromWorkitemId(WI_U1);
        SysProcessExampleWorkitem chainC = workitem(WI_CHAIN_C, NODE3_ID, U3,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        chainC.setFieldFromWorkitemId(WI_CHAIN_B);
        liveWorkitems.add(original);
        liveWorkitems.add(chainB);
        liveWorkitems.add(chainC);
        when(workitemDao.selectById(WI_U1)).thenReturn(original);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        service.transferRetract(EXAMPLE_ID, WI_U1, "整链收回");

        // 整链 ABANDON，新工作项回链最新一代（C），链路可逐级回溯 A→B→C→收回→A
        assertEquals(SysProcessConstants.WORKITEM_STATUS_ABANDON, chainB.getFieldStatus());
        assertEquals(SysProcessConstants.WORKITEM_STATUS_ABANDON, chainC.getFieldStatus());
        SysProcessExampleWorkitem restored = insertedWorkitem(U1);
        assertEquals(WI_CHAIN_C, restored.getFieldFromWorkitemId());
        assertEquals(WI_CHAIN_B, chainC.getFieldFromWorkitemId());
        assertEquals(WI_U1, chainB.getFieldFromWorkitemId());
        assertEquals(SysProcessConstants.WORKITEM_STATUS_PROCESSING, restored.getFieldStatus());
    }

    @Test
    void transferRetractFailsWhenTransferredWorkitemAlreadyProcessed() {
        captureWorkitems();
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleWorkitem original = workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_ABANDON);
        SysProcessExampleWorkitem transferred = workitem(WI_TRANSFERRED, NODE3_ID, U2,
                SysProcessConstants.WORKITEM_STATUS_FINISH);
        transferred.setFieldFromWorkitemId(WI_U1);
        liveWorkitems.add(original);
        liveWorkitems.add(transferred);
        when(workitemDao.selectById(WI_U1)).thenReturn(original);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.transferRetract(EXAMPLE_ID, WI_U1, "收回"));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_STATE_CONFLICT));
        assertTrue(error.getMessage().contains("被转办工作项已处理"));
        verify(workitemDao, never()).insert(any(SysProcessExampleWorkitem.class));
    }

    @Test
    void transferRetractForbiddenForNonInitiator() {
        captureWorkitems();
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleWorkitem original = workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_ABANDON);
        SysProcessExampleWorkitem transferred = workitem(WI_TRANSFERRED, NODE3_ID, U2,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        transferred.setFieldFromWorkitemId(WI_U1);
        liveWorkitems.add(original);
        liveWorkitems.add(transferred);
        when(workitemDao.selectById(WI_U1)).thenReturn(original);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U2, "李四"));

        AuthorizationException error = assertThrows(AuthorizationException.class,
                () -> service.transferRetract(EXAMPLE_ID, WI_U1, "收回"));
        assertEquals("FORBIDDEN", error.getCode());
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_FORBIDDEN));
        assertTrue(error.getMessage().contains("仅转办发起人可收回"));
    }

    @Test
    void processedRetractRestoresFinishedWorkitemWhenNodeStillProcessing() {
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_COUNTERSIGN));
        captureWorkitems();
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleWorkitem wiU1 = workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_FINISH);
        wiU1.setFieldFinishTime(new java.util.Date());
        SysProcessExampleWorkitem wiU2 = workitem(WI_U2, NODE3_ID, U2,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        liveWorkitems.add(wiU1);
        liveWorkitems.add(wiU2);
        when(workitemDao.selectById(WI_U1)).thenReturn(wiU1);
        when(workitemDao.selectById(WI_U2)).thenReturn(wiU2);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        service.processedRetract(EXAMPLE_ID, WI_U1, "收回重审");

        // 会签未齐（节点仍 PROCESSING）：本人已办结工作项重新置 PROCESSING（处理人不变）+ 留痕
        assertEquals(SysProcessConstants.WORKITEM_STATUS_PROCESSING, wiU1.getFieldStatus());
        assertEquals(U1, wiU1.getFieldHandlerId());
        assertEquals(SysProcessConstants.NODE_STATUS_PROCESSING, exampleNodeAt(3).getFieldStatus());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PROCESSING, example.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_PROCESSED_RETRACT.equals(n.getFieldHandleType())
                        && WI_U1.equals(n.getFieldWorkitemId())
                        && "收回重审".equals(n.getFieldContent())));

        // 收回后重新通过按 approveMode 自然参与节点通过判定：U1 先通过仍需等待 U2
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));
        service.pass(EXAMPLE_ID, WI_U1, "再次同意");
        assertEquals(SysProcessConstants.NODE_STATUS_PROCESSING, exampleNodeAt(3).getFieldStatus());

        // U2 通过后会签完成，节点流出并办结
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U2, "李四"));
        service.pass(EXAMPLE_ID, WI_U2, "同意");
        assertEquals(SysProcessConstants.NODE_STATUS_FINISH, exampleNodeAt(3).getFieldStatus());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_FINISH, example.getFieldStatus());
    }

    @Test
    void processedRetractRejectsWhenNodeLeftOrWorkitemNotFinished() {
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_FINISH, CONFIG_COUNTERSIGN));
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleWorkitem wiU1 = workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_FINISH);
        when(workitemDao.selectById(WI_U1)).thenReturn(wiU1);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        // 节点已流出（FINISH）：不得用于已流过的节点
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.processedRetract(EXAMPLE_ID, WI_U1, "收回"));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_STATE_CONFLICT));
        assertTrue(error.getMessage().contains("节点已办结"));

        // 流程非审批中
        exampleNodeAt(3).setFieldStatus(SysProcessConstants.NODE_STATUS_PROCESSING);
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_RETURN);
        error = assertThrows(IllegalArgumentException.class,
                () -> service.processedRetract(EXAMPLE_ID, WI_U1, "收回"));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_STATE_CONFLICT));

        // 工作项非已办结（仍 PROCESSING 无需收回）
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        wiU1.setFieldStatus(SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        error = assertThrows(IllegalArgumentException.class,
                () -> service.processedRetract(EXAMPLE_ID, WI_U1, "收回"));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_STATE_CONFLICT));
        assertTrue(error.getMessage().contains("仅已办结工作项可收回"));
    }

    @Test
    void processedRetractForbiddenForNonHandler() {
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_COUNTERSIGN));
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleWorkitem wiU1 = workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_FINISH);
        when(workitemDao.selectById(WI_U1)).thenReturn(wiU1);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U2, "李四"));

        AuthorizationException error = assertThrows(AuthorizationException.class,
                () -> service.processedRetract(EXAMPLE_ID, WI_U1, "收回"));
        assertEquals("FORBIDDEN", error.getCode());
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_FORBIDDEN));
        assertTrue(error.getMessage().contains("仅工作项处理人"));
    }

    @Test
    void transferInParallelBranchInheritsFlowBranchKeyAndJoinSucceeds() {
        setUpParallelGraph();
        captureWorkitems();
        when(nodeDao.selectById(NODE_BR1_ID)).thenReturn(exampleNodeAt(4));
        when(nodeDao.selectById(NODE_BR2_ID)).thenReturn(exampleNodeAt(5));
        when(userMapper.selectById(U3)).thenReturn(user(U3, "王五"));
        // 提交进入并行分支：两个分支各生成工作项（flowBranchKey 记录分支归属）
        service.submit(EXAMPLE_ID, "请审批");
        SysProcessExampleWorkitem branchA = insertedWorkitem(U1);
        SysProcessExampleWorkitem branchB = insertedWorkitem(U2);
        assertTrue(branchA.getFieldConfig().contains("\"flowBranchKey\":\"N4\""));
        assertTrue(branchB.getFieldConfig().contains("\"flowBranchKey\":\"N5\""));
        when(workitemDao.selectById(branchA.getFieldId())).thenReturn(branchA);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        service.transfer(EXAMPLE_ID, branchA.getFieldId(), U3, "分支内转办");

        // 分支内转办：flowBranchKey 随原工作项配置继承，保证 JOIN 汇聚归属不乱
        SysProcessExampleWorkitem transferred = insertedWorkitem(U3);
        assertEquals(branchA.getFieldId(), transferred.getFieldFromWorkitemId());
        assertTrue(transferred.getFieldConfig().contains("\"flowBranchKey\":\"N4\""));

        // 被转办人通过：分支 A 到达 JOIN（按继承的 flowBranchKey 归属），等待分支 B
        when(workitemDao.selectById(transferred.getFieldId())).thenReturn(transferred);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U3, "王五"));
        service.pass(EXAMPLE_ID, transferred.getFieldId(), "同意");
        assertEquals(SysProcessConstants.NODE_STATUS_PROCESSING, exampleNodeAt(6).getFieldStatus());
        List<SysProcessExampleNode> joinNodes = exampleNodes;
        SysProcessExampleNode joinNode = joinNodes.get(5);
        assertTrue(joinNode.getFieldRuntime().contains("\"arrivedBranchKeys\":[\"N4\"]"),
                "分支 A 经被转办人通过后到达 JOIN: " + joinNode.getFieldRuntime());

        // 分支 B 通过后全部汇聚，JOIN 流出并办结
        when(workitemDao.selectById(branchB.getFieldId())).thenReturn(branchB);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U2, "李四"));
        service.pass(EXAMPLE_ID, branchB.getFieldId(), "同意");
        assertEquals(SysProcessConstants.NODE_STATUS_FINISH, exampleNodeAt(6).getFieldStatus());
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_FINISH, example.getFieldStatus());
    }

    /* ==================== 起草人 / 已审批人催办（P17-BE-011） ==================== */

    @Test
    void drafterExpediteSendsReadTodoToCurrentNodeHandlers() {
        // 配置读取缺省（mock 未打桩返回 null）回退默认模板——本测试同时覆盖「缺省回退默认模板」口径
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_COUNTERSIGN));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectByExampleAndStatus(EXAMPLE_ID, SysProcessConstants.NODE_STATUS_PROCESSING))
                .thenReturn(List.of(exampleNodeAt(3)));
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING),
                workitem(WI_U2, NODE3_ID, U2, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(CREATOR_ID, "起草人"));

        service.drafterExpedite(EXAMPLE_ID, "请尽快处理");

        // READ 待阅字段断言：默认模板渲染标题 + 通知类型/来源模块/modelId/链接/内容
        verify(notifyTodoService).send(argThat(todo ->
                        "请尽快处理起草人提交的流程：采购订单审批模板".equals(todo.getFieldName())
                                && SysNotifyType.READ.getValue().equals(todo.getFieldNotifyType())
                                && SysProcessConstants.MODEL_KEY_SYS_PROCESS.equals(todo.getFieldModelName())
                                && EXAMPLE_ID.equals(todo.getFieldModelId())
                                && ("/processExample/detail/" + EXAMPLE_ID).equals(todo.getFieldUrl())
                                && "请尽快处理".equals(todo.getFieldContent())),
                argThat(ids -> ids.size() == 2 && ids.get(0).equals(U1) && ids.get(1).equals(U2)));
        // 留痕 DRAFTER_EXPEDITE：起草人催办无工作项（workitemId=null），content=催办意见
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_DRAFTER_EXPEDITE.equals(n.getFieldHandleType())
                        && CREATOR_ID.equals(n.getFieldHandlerId())
                        && null == n.getFieldWorkitemId()
                        && "请尽快处理".equals(n.getFieldContent())));
        // 催办不改流程状态
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PROCESSING, example.getFieldStatus());
    }

    @Test
    void drafterExpediteMergesParallelNodesDedupAndExcludesInitiator() {
        // 并行分支：两个 PROCESSING 节点，起草人本身也是其中节点处理人，U1 跨节点重复出现
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_COUNTERSIGN));
        exampleNodes.set(3, exampleNode(NODE4_ID, 4, "审批节点4", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_SINGLE));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectByExampleAndStatus(EXAMPLE_ID, SysProcessConstants.NODE_STATUS_PROCESSING))
                .thenReturn(List.of(exampleNodeAt(3), exampleNodeAt(4)));
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING),
                workitem(WI_U2, NODE3_ID, CREATOR_ID, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        when(workitemDao.selectListByNodeId(NODE4_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE4_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING),
                workitem(WI_U2, NODE4_ID, U2, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(CREATOR_ID, "起草人"));

        service.drafterExpedite(EXAMPLE_ID, "催办");

        // 收件人跨节点合并去重保序，并排除催办发起人（起草人）本人
        verify(notifyTodoService).send(any(SysNotifyTodo.class),
                argThat(ids -> ids.size() == 2 && ids.get(0).equals(U1) && ids.get(1).equals(U2)));
    }

    @Test
    void drafterExpediteWithoutRecipientsOnlyWritesAudit() {
        // 当前节点唯一处理人为发起人本人：收件人为空 → 仅留痕不发通知
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_COUNTERSIGN));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectByExampleAndStatus(EXAMPLE_ID, SysProcessConstants.NODE_STATUS_PROCESSING))
                .thenReturn(List.of(exampleNodeAt(3)));
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, CREATOR_ID, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(CREATOR_ID, "起草人"));

        service.drafterExpedite(EXAMPLE_ID, "催办");

        verify(notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_DRAFTER_EXPEDITE.equals(n.getFieldHandleType())));
    }

    @Test
    void drafterExpediteForbiddenForNonDrafter() {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        AuthorizationException error = assertThrows(AuthorizationException.class,
                () -> service.drafterExpedite(EXAMPLE_ID, "催办"));
        assertEquals("FORBIDDEN", error.getCode());
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_FORBIDDEN));
        assertTrue(error.getMessage().contains("仅流程起草人"));
        verify(notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
        verify(auditNoteDao, never()).insert(any(SysProcessAuditNote.class));
    }

    @Test
    void drafterExpediteRejectsNonProcessingState() {
        // setUp 默认 DRAFT 状态：仅审批中的流程可催办
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.drafterExpedite(EXAMPLE_ID, "催办"));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_STATE_CONFLICT));
        assertTrue(error.getMessage().contains("仅审批中的流程可催办"));
        verify(auditNoteDao, never()).insert(any(SysProcessAuditNote.class));
        verify(notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
    }

    @Test
    void drafterExpediteRendersConfiguredSubjectTemplateWithFallbacks() {
        // 配置模板渲染：${fieldName} 模板缺失回退流程编号、${fieldCreatorName} 查询失败回退空串
        when(settingService.getValue(eq(SysProcessConstants.SETTING_TYPE_NOTIFY),
                eq(SysProcessConstants.SETTING_NOTIFY_EXPEDITE_SUBJECT)))
                .thenReturn("加急：${fieldName}（${fieldCreatorName}）");
        when(userMapper.selectById(CREATOR_ID)).thenReturn(null);
        when(templateDao.selectById(TEMPLATE_ID)).thenReturn(null);
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_COUNTERSIGN));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectByExampleAndStatus(EXAMPLE_ID, SysProcessConstants.NODE_STATUS_PROCESSING))
                .thenReturn(List.of(exampleNodeAt(3)));
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(CREATOR_ID, "起草人"));

        service.drafterExpedite(EXAMPLE_ID, "加急处理");

        // 待阅标题按配置模板渲染（回退：流程编号 P202601010001、起草人空串）
        verify(notifyTodoService).send(argThat(todo ->
                "加急：P202601010001（）".equals(todo.getFieldName())), anyList());
    }

    @Test
    void processedExpediteSendsToCurrentHandlersAndWritesWorkitemIdInAudit() {
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_COUNTERSIGN));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectByExampleAndStatus(EXAMPLE_ID, SysProcessConstants.NODE_STATUS_PROCESSING))
                .thenReturn(List.of(exampleNodeAt(3)));
        // 发起人 U1 已办结（FINISH）工作项可催办；收件人排除发起人本人
        when(workitemDao.selectById(WI_U1)).thenReturn(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_FINISH));
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_FINISH),
                workitem(WI_U2, NODE3_ID, U2, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        service.processedExpedite(EXAMPLE_ID, WI_U1, "请尽快审批");

        verify(notifyTodoService).send(argThat(todo ->
                        SysNotifyType.READ.getValue().equals(todo.getFieldNotifyType())
                                && "请尽快审批".equals(todo.getFieldContent())),
                argThat(ids -> ids.size() == 1 && ids.get(0).equals(U2)));
        // 留痕 PROCESSED_EXPEDITE：workitemId=发起人持有工作项 id
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_PROCESSED_EXPEDITE.equals(n.getFieldHandleType())
                        && WI_U1.equals(n.getFieldWorkitemId())
                        && U1.equals(n.getFieldHandlerId())
                        && "请尽快审批".equals(n.getFieldContent())));
    }

    @Test
    void processedExpediteAllowsProcessingWorkitemOfFormerHandler() {
        // 已审批人收回后重新持有 PROCESSING 工作项（FINISH 或 PROCESSING 均可催办）
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_COUNTERSIGN));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(nodeDao.selectByExampleAndStatus(EXAMPLE_ID, SysProcessConstants.NODE_STATUS_PROCESSING))
                .thenReturn(List.of(exampleNodeAt(3)));
        when(workitemDao.selectById(WI_U2)).thenReturn(
                workitem(WI_U2, NODE3_ID, U2, SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        when(workitemDao.selectListByNodeId(NODE3_ID)).thenReturn(List.of(
                workitem(WI_U2, NODE3_ID, U2, SysProcessConstants.WORKITEM_STATUS_PROCESSING),
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U2, "李四"));

        service.processedExpedite(EXAMPLE_ID, WI_U2, "催办");

        verify(notifyTodoService).send(any(SysNotifyTodo.class),
                argThat(ids -> ids.size() == 1 && ids.get(0).equals(U1)));
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_PROCESSED_EXPEDITE.equals(n.getFieldHandleType())
                        && WI_U2.equals(n.getFieldWorkitemId())));
    }

    @Test
    void processedExpediteRejectsAbandonedWorkitem() {
        // 转办弃置（ABANDON）的原工作项不算已处理人 → STATE_CONFLICT，不发通知不留痕
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_COUNTERSIGN));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(workitemDao.selectById(WI_U1)).thenReturn(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_ABANDON));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.processedExpedite(EXAMPLE_ID, WI_U1, "催办"));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_STATE_CONFLICT));
        assertTrue(error.getMessage().contains("转办弃置"));
        verify(notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
        verify(auditNoteDao, never()).insert(any(SysProcessAuditNote.class));
    }

    @Test
    void processedExpediteForbiddenForNonHandler() {
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_COUNTERSIGN));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(workitemDao.selectById(WI_U1)).thenReturn(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_FINISH));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U2, "李四"));

        AuthorizationException error = assertThrows(AuthorizationException.class,
                () -> service.processedExpedite(EXAMPLE_ID, WI_U1, "催办"));
        assertEquals("FORBIDDEN", error.getCode());
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_FORBIDDEN));
        assertTrue(error.getMessage().contains("仅工作项处理人可催办"));
        verify(notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
    }

    /* ==================== 审批人抄送（P17-BE-012） ==================== */

    @Test
    void handlerCcSendsReadTodoToExplicitTargetsAndWritesAudit() {
        // notify_cc_subject 未配置（mock 未打桩返回 null）回退默认模板——同时覆盖「缺省回退默认模板」口径
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_SINGLE));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleWorkitem processing = workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        when(workitemDao.selectById(WI_U1)).thenReturn(processing);
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        // ccToIds 含重复项且包含发起人本人：去重保序 [U2, U1]，抄送对象为显式指定不排除发起人
        service.handlerCc(EXAMPLE_ID, WI_U1, U2 + ";" + U1 + ";" + U2, "请知悉");

        // READ 待阅字段断言：默认模板渲染标题 + 通知类型/来源模块/modelId/链接/内容=审批意见
        verify(notifyTodoService).send(argThat(todo ->
                        "您被抄送的流程：采购订单审批模板".equals(todo.getFieldName())
                                && SysNotifyType.READ.getValue().equals(todo.getFieldNotifyType())
                                && SysProcessConstants.MODEL_KEY_SYS_PROCESS.equals(todo.getFieldModelName())
                                && EXAMPLE_ID.equals(todo.getFieldModelId())
                                && ("/processExample/detail/" + EXAMPLE_ID).equals(todo.getFieldUrl())
                                && "请知悉".equals(todo.getFieldContent())),
                argThat(ids -> ids.size() == 2 && ids.get(0).equals(U2) && ids.get(1).equals(U1)));
        // 留痕 HANDLER_CC：workitemId=当前工作项 id，handlerId=发起人，content=审批意见
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_HANDLER_CC.equals(n.getFieldHandleType())
                        && WI_U1.equals(n.getFieldWorkitemId())
                        && U1.equals(n.getFieldHandlerId())
                        && "请知悉".equals(n.getFieldContent())));
        // 抄送是在途告知：不改工作项/节点/流程状态
        verify(workitemDao, never()).update(any(SysProcessExampleWorkitem.class));
        verify(nodeDao, never()).update(any(SysProcessExampleNode.class));
        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PROCESSING, example.getFieldStatus());
        assertEquals(SysProcessConstants.WORKITEM_STATUS_PROCESSING, processing.getFieldStatus());
    }

    @Test
    void handlerCcRequiresWorkitemIdCcToIdsAndContent() {
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_SINGLE));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(workitemDao.selectById(WI_U1)).thenReturn(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        // 缺 workitemId → PARAM_MISSING
        IllegalArgumentException missingWorkitem = assertThrows(IllegalArgumentException.class,
                () -> service.handlerCc(EXAMPLE_ID, null, U2, "请知悉"));
        assertTrue(missingWorkitem.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(missingWorkitem.getMessage().contains("workitemId"));

        // 缺 ccToIds → PARAM_MISSING
        IllegalArgumentException missingTargets = assertThrows(IllegalArgumentException.class,
                () -> service.handlerCc(EXAMPLE_ID, WI_U1, "  ", "请知悉"));
        assertTrue(missingTargets.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(missingTargets.getMessage().contains("ccToIds"));

        // 缺 content → PARAM_MISSING（§6.2 handler_cc 必填参数含 content）
        IllegalArgumentException missingContent = assertThrows(IllegalArgumentException.class,
                () -> service.handlerCc(EXAMPLE_ID, WI_U1, U2, null));
        assertTrue(missingContent.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(missingContent.getMessage().contains("content"));

        verify(notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
        verify(auditNoteDao, never()).insert(any(SysProcessAuditNote.class));
    }

    @Test
    void handlerCcForbiddenForNonHandler() {
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_SINGLE));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(workitemDao.selectById(WI_U1)).thenReturn(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U2, "李四"));

        AuthorizationException error = assertThrows(AuthorizationException.class,
                () -> service.handlerCc(EXAMPLE_ID, WI_U1, U2, "请知悉"));
        assertEquals("FORBIDDEN", error.getCode());
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_FORBIDDEN));
        assertTrue(error.getMessage().contains("仅工作项处理人"));
        verify(notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
        verify(auditNoteDao, never()).insert(any(SysProcessAuditNote.class));
    }

    @Test
    void handlerCcRejectsNonProcessingState() {
        // setUp 默认 DRAFT 状态：仅审批中的流程可抄送
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.handlerCc(EXAMPLE_ID, WI_U1, U2, "请知悉"));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_STATE_CONFLICT));
        assertTrue(error.getMessage().contains("仅审批中的流程可执行抄送"));
        verify(notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
        verify(auditNoteDao, never()).insert(any(SysProcessAuditNote.class));
    }

    @Test
    void handlerCcRejectsFinishedNode() {
        // 节点已办结（FINISH）：抄送是在途告知，节点流出后不可再发起
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_FINISH, CONFIG_SINGLE));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(workitemDao.selectById(WI_U1)).thenReturn(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.handlerCc(EXAMPLE_ID, WI_U1, U2, "请知悉"));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_STATE_CONFLICT));
        assertTrue(error.getMessage().contains("节点已办结，无法抄送"));
        verify(notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
        verify(auditNoteDao, never()).insert(any(SysProcessAuditNote.class));
    }

    @Test
    void handlerCcRejectsUnknownTarget() {
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_SINGLE));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(workitemDao.selectById(WI_U1)).thenReturn(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));
        // setUp 默认：未知用户返回 null
        String unknown = "12345678901234567890123456789999";

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.handlerCc(EXAMPLE_ID, WI_U1, U2 + ";" + unknown, "请知悉"));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(error.getMessage().contains("抄送对象用户不存在"));
        assertTrue(error.getMessage().contains(unknown));
        verify(notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
        verify(auditNoteDao, never()).insert(any(SysProcessAuditNote.class));
    }

    @Test
    void operateDispatchesHandlerCc() {
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_SINGLE));
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(workitemDao.selectById(WI_U1)).thenReturn(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING));
        when(nodeDao.selectById(NODE3_ID)).thenReturn(exampleNodeAt(3));
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));

        // operate 通道正向分发：workitemId / ccToIds / content 齐备
        SysProcessOperateRequest request = op(SysProcessConstants.OPERATE_TYPE_HANDLER_CC);
        request.setWorkitemId(WI_U1);
        request.setCcToIds(U2 + ";" + U2 + ";" + CREATOR_ID);
        request.setContent("抄送知悉");
        service.operate(request);

        verify(notifyTodoService).send(any(SysNotifyTodo.class),
                argThat(ids -> ids.size() == 2 && ids.get(0).equals(U2) && ids.get(1).equals(CREATOR_ID)));
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_HANDLER_CC.equals(n.getFieldHandleType())
                        && WI_U1.equals(n.getFieldWorkitemId())
                        && "抄送知悉".equals(n.getFieldContent())));

        // operate 通道缺 ccToIds → PARAM_MISSING
        SysProcessOperateRequest missingTargets = op(SysProcessConstants.OPERATE_TYPE_HANDLER_CC);
        missingTargets.setWorkitemId(WI_U1);
        missingTargets.setContent("抄送知悉");
        IllegalArgumentException missingError = assertThrows(IllegalArgumentException.class,
                () -> service.operate(missingTargets));
        assertTrue(missingError.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(missingError.getMessage().contains("ccToIds"));
    }

    @Test
    void operateDispatchesAdminChangeHandlerAndRequiresParamsAndAdminRole() {
        // 统一操作通道分发更换审批人（P17-BE-013，占位清零）：需流程管理员权限，语义与特权弹窗入口一致
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        SysProcessExampleWorkitem workitem = workitem(WI_U1, NODE3_ID, U1,
                SysProcessConstants.WORKITEM_STATUS_PROCESSING);
        when(workitemDao.selectById(WI_U1)).thenReturn(workitem);
        userUtil.when(UserUtil::getCurrentUser)
                .thenReturn(userWithRoles(U1, "张三", SysProcessConstants.ROLE_SYS_PROCESS_EXAMPLE_ADMIN));

        SysProcessOperateRequest request = op(SysProcessConstants.OPERATE_TYPE_ADMIN_CHANGE_HANDLER);
        request.setWorkitemId(WI_U1);
        request.setChangeToId(U2);
        request.setContent("更换审批人");
        SysProcessExample result = service.operate(request);

        assertEquals(SysProcessConstants.EXAMPLE_STATUS_PROCESSING, result.getFieldStatus());
        assertEquals(U2, workitem.getFieldHandlerId());
        assertEquals(U2, workitem.getFieldExpectHandlerId());
        verify(auditNoteDao).insert(argThat(n ->
                SysProcessConstants.AUDIT_TYPE_ADMIN_CHANGE_HANDLER.equals(n.getFieldHandleType())
                        && WI_U1.equals(n.getFieldWorkitemId())
                        && U1.equals(n.getFieldHandlerId())));

        // 缺 workitemId / changeToId → PARAM_MISSING（证明已接通真实分发而非「暂未开放」）
        SysProcessOperateRequest missingWorkitem = op(SysProcessConstants.OPERATE_TYPE_ADMIN_CHANGE_HANDLER);
        missingWorkitem.setChangeToId(U2);
        missingWorkitem.setContent("更换审批人");
        IllegalArgumentException missingItemError = assertThrows(IllegalArgumentException.class,
                () -> service.operate(missingWorkitem));
        assertTrue(missingItemError.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(missingItemError.getMessage().contains("workitemId"));

        SysProcessOperateRequest missingTarget = op(SysProcessConstants.OPERATE_TYPE_ADMIN_CHANGE_HANDLER);
        missingTarget.setWorkitemId(WI_U1);
        missingTarget.setContent("更换审批人");
        IllegalArgumentException missingTargetError = assertThrows(IllegalArgumentException.class,
                () -> service.operate(missingTarget));
        assertTrue(missingTargetError.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(missingTargetError.getMessage().contains("changeToId"));

        // 非管理员 → FORBIDDEN（requireProcessAdmin），工作项不被改动
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U2, "李四"));
        SysProcessOperateRequest forbiddenRequest = op(SysProcessConstants.OPERATE_TYPE_ADMIN_CHANGE_HANDLER);
        forbiddenRequest.setWorkitemId(WI_U1);
        forbiddenRequest.setChangeToId(U1);
        forbiddenRequest.setContent("更换审批人");
        AuthorizationException forbidden = assertThrows(AuthorizationException.class,
                () -> service.operate(forbiddenRequest));
        assertEquals("FORBIDDEN", forbidden.getCode());
        assertTrue(forbidden.getMessage().contains("无流程管理员权限"));
        verify(workitemDao, never()).update(argThat(w ->
                U1.equals(w.getFieldHandlerId()) && U1.equals(w.getFieldExpectHandlerId())));
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

    /* ==================== 审批面板按钮集（P17-BE-013，operations 升级为按钮对象数组） ==================== */

    /** v1_7 审批操作明细配置：通过（含操作名/默认意见）+ 拒绝（空操作名取默认名）/抄送/转办 + 驳回目标（含未流经节点 N3） */
    private static final String CONFIG_OPERATIONS_FULL =
            "{\"handlerIds\":\"" + U1 + "\",\"approveMode\":\"single\","
                    + "\"operations\":["
                    + "{\"operateType\":\"handler_pass\",\"operateName\":\"同意并归档\",\"defaultContent\":\"情况属实，同意\"},"
                    + "{\"operateType\":\"handler_reject\",\"operateName\":\"\"},"
                    + "{\"operateType\":\"handler_cc\"},"
                    + "{\"operateType\":\"handler_transfer\"}"
                    + "],\"rejectTargets\":[\"N2\",\"N3\"]}";

    /** v1_7 审批操作明细配置（仅通过行，供 flowChoices 用例） */
    private static final String CONFIG_OPERATIONS_PASS_ONLY =
            "{\"handlerIds\":\"" + U1 + "\",\"approveMode\":\"single\","
                    + "\"operations\":[{\"operateType\":\"handler_pass\"}]}";

    /** v1_7 审批操作明细配置（已处理人段：收回/催办） */
    private static final String CONFIG_OPERATIONS_PROCESSED =
            "{\"handlerIds\":\"" + U1 + "\",\"approveMode\":\"countersign\","
                    + "\"operations\":["
                    + "{\"operateType\":\"processed_retract\"},"
                    + "{\"operateType\":\"processed_expedite\"}"
                    + "]}";

    /** v1_7 审批操作明细配置 + 操作标识开关（nodeOps 并存回归） */
    private static final String CONFIG_OPERATIONS_WITH_OPS =
            "{\"handlerIds\":\"" + U1 + "\",\"approveMode\":\"single\","
                    + "\"operations\":[{\"operateType\":\"handler_pass\",\"operateName\":\"同意\"}],"
                    + "\"ops\":{\"attachment\":true}}";

    /** 以指定用户视角取审批面板数据 */
    private SysProcessExamplePanelVo panelAs(String userId) {
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(userId,
                CREATOR_ID.equals(userId) ? "起草人" : "用户"));
        return service.panel(EXAMPLE_ID);
    }

    /** 面板按钮集的 operateType 列表（按钮对象数组断言口径） */
    private static List<String> operationTypes(SysProcessExamplePanelVo panel) {
        return panel.getOperations().stream().map(SysProcessPanelOperationVo::getOperateType).toList();
    }

    /** 节点候选行的 nodeKey 列表 */
    private static List<String> nodeOptionKeys(List<SysProcessNodeOptionVo> options) {
        return null == options ? List.of() : options.stream().map(SysProcessNodeOptionVo::getNodeKey).toList();
    }

    @Test
    void panelOperationsFallbackForLegacyTemplatePerDrafterState() {
        // 旧模板（节点无 operations 配置）兜底：起草人 DRAFT/RETURN → 起草人取消，PROCESSING → 起草人收回（默认名）
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_DRAFT);
        SysProcessExamplePanelVo panel = panelAs(CREATOR_ID);
        assertEquals(List.of(SysProcessConstants.OPERATE_TYPE_DRAFTER_CANCEL), operationTypes(panel));
        assertEquals("起草人取消", panel.getOperations().get(0).getOperateName());

        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_RETURN);
        panel = panelAs(CREATOR_ID);
        assertEquals(List.of(SysProcessConstants.OPERATE_TYPE_DRAFTER_CANCEL), operationTypes(panel));

        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        panel = panelAs(CREATOR_ID);
        assertEquals(List.of(SysProcessConstants.OPERATE_TYPE_DRAFTER_RETRACT), operationTypes(panel));
        assertEquals("起草人收回", panel.getOperations().get(0).getOperateName());
    }

    @Test
    void panelOperationsFallbackHandlerButtonsWithDefaultRejectTargets() {
        // 旧模板兜底：当前处理人按钮（通过/拒绝，默认名）；handler_reject 无 rejectTargets 配置 → 默认起草节点候选；
        // handler_pass 无决策来源关系 → flowChoices 为空；nodeOps 无配置为空集
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_SINGLE));
        when(nodeDao.selectByExampleAndStatus(EXAMPLE_ID, SysProcessConstants.NODE_STATUS_PROCESSING))
                .thenReturn(List.of(exampleNodeAt(3)));
        when(workitemDao.selectByExampleAndHandler(EXAMPLE_ID, U1)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));

        SysProcessExamplePanelVo panel = panelAs(U1);

        assertEquals(List.of(SysProcessConstants.OPERATE_TYPE_HANDLER_PASS,
                SysProcessConstants.OPERATE_TYPE_HANDLER_REJECT), operationTypes(panel));
        assertEquals("审批人通过", panel.getOperations().get(0).getOperateName());
        assertEquals("审批人拒绝", panel.getOperations().get(1).getOperateName());
        // rejectTargets：配置为空默认仅起草节点（key+名称）
        assertEquals(List.of(SysProcessConstants.FIXED_NODE_KEY_DRAFT),
                nodeOptionKeys(panel.getOperations().get(1).getRejectTargets()));
        assertEquals("DRAFT", panel.getOperations().get(1).getRejectTargets().get(0).getNodeName());
        assertTrue(panel.getOperations().get(0).getFlowChoices().isEmpty());
        assertTrue(panel.getNodeOps().isEmpty());
    }

    @Test
    void panelOperationsFromNodeConfigWithNamesAndRejectTargetIntersection() {
        // v1_7 配置：操作名/默认意见按节点配置回显（空操作名取默认名）；rejectTargets = 配置 ∩ 已流经
        // （N3 为当前在途节点不算已流经 → 仅 N2）；抄送/转办行按身份放行（默认名）
        setUpManualGraph();
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_OPERATIONS_FULL));
        when(nodeDao.selectByExampleAndStatus(EXAMPLE_ID, SysProcessConstants.NODE_STATUS_PROCESSING))
                .thenReturn(List.of(exampleNodeAt(3)));
        when(workitemDao.selectByExampleAndHandler(EXAMPLE_ID, U1)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));

        SysProcessExamplePanelVo panel = panelAs(U1);

        assertEquals(List.of(SysProcessConstants.OPERATE_TYPE_HANDLER_PASS,
                SysProcessConstants.OPERATE_TYPE_HANDLER_REJECT,
                SysProcessConstants.OPERATE_TYPE_HANDLER_CC,
                SysProcessConstants.OPERATE_TYPE_HANDLER_TRANSFER), operationTypes(panel));
        assertEquals("同意并归档", panel.getOperations().get(0).getOperateName());
        assertEquals("情况属实，同意", panel.getOperations().get(0).getDefaultContent());
        assertEquals("审批人拒绝", panel.getOperations().get(1).getOperateName());
        assertEquals(List.of(SysProcessConstants.FIXED_NODE_KEY_DRAFT),
                nodeOptionKeys(panel.getOperations().get(1).getRejectTargets()));
        assertEquals("审批人抄送", panel.getOperations().get(2).getOperateName());
        assertEquals("审批人转办", panel.getOperations().get(3).getOperateName());
    }

    @Test
    void panelOperationsFlowChoicesFromManualDecisionNode() {
        // flowChoices（仅 handler_pass 且存在决策来源关系）：MANUAL 节点 flowDecisions 含被通过节点 N3
        // → 输出该决策节点全部出边目标（N7/N8，key+名称）
        setUpManualGraph();
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_OPERATIONS_PASS_ONLY));
        when(nodeDao.selectByExampleAndStatus(EXAMPLE_ID, SysProcessConstants.NODE_STATUS_PROCESSING))
                .thenReturn(List.of(exampleNodeAt(3)));
        when(workitemDao.selectByExampleAndHandler(EXAMPLE_ID, U1)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));

        SysProcessExamplePanelVo panel = panelAs(U1);

        assertEquals(List.of(SysProcessConstants.OPERATE_TYPE_HANDLER_PASS), operationTypes(panel));
        List<SysProcessNodeOptionVo> flowChoices = panel.getOperations().get(0).getFlowChoices();
        assertEquals(List.of("N7", "N8"), nodeOptionKeys(flowChoices));
        assertEquals("结束节点7", flowChoices.get(0).getNodeName());
        assertEquals("结束节点8", flowChoices.get(1).getNodeName());
    }

    @Test
    void panelOperationsProcessedRowsForFinishedWorkitemHandler() {
        // 已处理人按钮：本人 FINISH 工作项 + 节点配置 processed_* 行 → 收回/催办按钮（默认名，无候选数据）
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_OPERATIONS_PROCESSED));
        when(nodeDao.selectByExampleAndStatus(EXAMPLE_ID, SysProcessConstants.NODE_STATUS_PROCESSING))
                .thenReturn(List.of(exampleNodeAt(3)));
        when(workitemDao.selectByExampleAndHandler(EXAMPLE_ID, U1)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_FINISH)));

        SysProcessExamplePanelVo panel = panelAs(U1);

        assertEquals(List.of(SysProcessConstants.OPERATE_TYPE_PROCESSED_RETRACT,
                SysProcessConstants.OPERATE_TYPE_PROCESSED_EXPEDITE), operationTypes(panel));
        assertEquals("已审批人收回", panel.getOperations().get(0).getOperateName());
        assertEquals("已审批人催办", panel.getOperations().get(1).getOperateName());
        assertNull(panel.getOperations().get(0).getRejectTargets());
        assertNull(panel.getOperations().get(0).getFlowChoices());
    }

    @Test
    void panelOperationsAdminButtonsIncludeChangeHandlerUnderProcessing() {
        // 管理员按钮：resolver 准入后按状态 switch（JUMP/FINISH/ABANDON/PAUSE），PROCESSING 新增 CHANGE_HANDLER；
        // 与旧模板兜底的起草人按钮并存
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        when(resolver.resolve(any(AuthorizationContext.class))).thenReturn(true);

        SysProcessExamplePanelVo panel = panelAs(CREATOR_ID);

        List<String> types = operationTypes(panel);
        assertTrue(types.contains(SysProcessConstants.OPERATE_TYPE_DRAFTER_RETRACT));
        assertTrue(types.contains(SysProcessConstants.ADMIN_OPERATE_JUMP));
        assertTrue(types.contains(SysProcessConstants.ADMIN_OPERATE_FINISH));
        assertTrue(types.contains(SysProcessConstants.ADMIN_OPERATE_ABANDON));
        assertTrue(types.contains(SysProcessConstants.ADMIN_OPERATE_PAUSE));
        assertTrue(types.contains(SysProcessConstants.ADMIN_OPERATE_CHANGE_HANDLER));
        assertEquals("管理员更换审批人", panel.getOperations().stream()
                .filter(op -> SysProcessConstants.ADMIN_OPERATE_CHANGE_HANDLER.equals(op.getOperateType()))
                .findFirst().orElseThrow().getOperateName());
    }

    @Test
    void panelNodeOpsUnchangedWithOperationsUpgrade() {
        // nodeOps 业务标识与 operations 并存且语义不变：ops 开关照旧解析（仅当前节点持工作项的审批人）
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        exampleNodes.set(2, exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE",
                SysProcessConstants.NODE_STATUS_PROCESSING, CONFIG_OPERATIONS_WITH_OPS));
        when(nodeDao.selectByExampleAndStatus(EXAMPLE_ID, SysProcessConstants.NODE_STATUS_PROCESSING))
                .thenReturn(List.of(exampleNodeAt(3)));
        when(workitemDao.selectByExampleAndHandler(EXAMPLE_ID, U1)).thenReturn(List.of(
                workitem(WI_U1, NODE3_ID, U1, SysProcessConstants.WORKITEM_STATUS_PROCESSING)));

        SysProcessExamplePanelVo panel = panelAs(U1);

        assertEquals(List.of(SysProcessConstants.OPERATE_TYPE_HANDLER_PASS), operationTypes(panel));
        assertEquals("同意", panel.getOperations().get(0).getOperateName());
        assertEquals(Set.of(SysProcessConstants.OP_ATTACHMENT), panel.getNodeOps());
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

    /** 模板连线：START(N1)→DRAFT(N2)→审批3(N3)→审批4(N4)→END(N5)，首条为默认分支以覆盖分支字段复制 */
    private List<SysProcessTemplateLine> templateLines() {
        List<SysProcessTemplateLine> lines = new ArrayList<>();
        for (SysProcessTemplateNode node : templateNodes()) {
            if (node.getFieldNo() < 5) {
                lines.add(templateLine("N" + node.getFieldNo(), "N" + (node.getFieldNo() + 1),
                        node.getFieldNo() == 1));
            }
        }
        return lines;
    }

    private static SysProcessTemplateLine templateLine(String fromKey, String toKey, boolean isDefault) {
        SysProcessTemplateLine line = new SysProcessTemplateLine();
        line.setFieldId("12345678901234567890123456789" + fromKey);
        line.setFieldTemplateId(TEMPLATE_ID);
        line.setFieldFromKey(fromKey);
        line.setFieldToKey(toKey);
        line.setFieldLineType(SysProcessConstants.LINE_TYPE_NORMAL);
        line.setFieldBranchOrder(isDefault ? 1 : 2);
        line.setFieldBranchName(isDefault ? "默认分支" : null);
        line.setFieldCondition(null);
        line.setFieldIsDefault(isDefault);
        return line;
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
        node.setFieldNodeKey("N" + no);
        return node;
    }

    /** 实例连线快照：START(N1)→DRAFT(N2)→审批3(N3)→审批4(N4)→END(N5) */
    private static List<SysProcessExampleLine> flowLines() {
        List<SysProcessExampleLine> lines = new ArrayList<>();
        lines.add(exampleLine("L1", "N1", "N2"));
        lines.add(exampleLine("L2", "N2", "N3"));
        lines.add(exampleLine("L3", "N3", "N4"));
        lines.add(exampleLine("L4", "N4", "N5"));
        return lines;
    }

    private static SysProcessExampleLine exampleLine(String id, String fromKey, String toKey) {
        SysProcessExampleLine line = new SysProcessExampleLine();
        line.setFieldId(id);
        line.setFieldExampleId(EXAMPLE_ID);
        line.setFieldFromKey(fromKey);
        line.setFieldToKey(toKey);
        line.setFieldLineType(SysProcessConstants.LINE_TYPE_NORMAL);
        line.setFieldBranchOrder(1);
        line.setFieldIsDefault(false);
        return line;
    }

    private static List<String> splitIds(String value) {
        if (null == value || value.isBlank()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (String id : value.split(";")) {
            if (!id.isBlank() && !ids.contains(id.trim())) {
                ids.add(id.trim());
            }
        }
        return ids;
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
