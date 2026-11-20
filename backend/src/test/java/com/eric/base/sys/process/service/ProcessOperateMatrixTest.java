package com.eric.base.sys.process.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eric.base.common.exception.AuthorizationException;
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
import com.eric.base.sys.process.request.SysProcessOperateRequest;
import com.eric.base.sys.process.resolver.ProcessResolver;
import com.eric.base.sys.process.service.engine.ProcessFlowEngine;
import com.eric.base.sys.process.service.engine.ProcessFlowGraphLoader;
import com.eric.base.sys.process.service.engine.ProcessFlowSupport;
import com.eric.base.sys.process.service.engine.ProcessHandlerResolver;
import com.eric.base.sys.process.service.engine.ProcessNotifySender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_ADMIN_ABANDON;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_ADMIN_CHANGE_HANDLER;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_ADMIN_JUMP;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_DRAFTER_ABANDON;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_DRAFTER_BACK;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_DRAFTER_EXPEDITE;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_DRAFTER_SUBMIT;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_HANDLER_CC;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_HANDLER_PASS;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_HANDLER_REJECT;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_HANDLER_TRANSFER;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_HANDLER_TRANSFER_RETRACT;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_PROCESSED_EXPEDITE;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_PROCESSED_RETRACT;
import static com.eric.base.sys.process.properties.SysProcessConstants.CS_PROCESS_OPERATE_FORBIDDEN;
import static com.eric.base.sys.process.properties.SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING;
import static com.eric.base.sys.process.properties.SysProcessConstants.CS_PROCESS_OPERATE_STATE_CONFLICT;
import static com.eric.base.sys.process.properties.SysProcessConstants.EXAMPLE_STATUS_ABANDON;
import static com.eric.base.sys.process.properties.SysProcessConstants.EXAMPLE_STATUS_DRAFT;
import static com.eric.base.sys.process.properties.SysProcessConstants.EXAMPLE_STATUS_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.EXAMPLE_STATUS_PAUSE;
import static com.eric.base.sys.process.properties.SysProcessConstants.EXAMPLE_STATUS_PROCESSING;
import static com.eric.base.sys.process.properties.SysProcessConstants.EXAMPLE_STATUS_RETURN;
import static com.eric.base.sys.process.properties.SysProcessConstants.FIXED_NODE_KEY_DRAFT;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_PROCESSING;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_REJECT;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_WAITING;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_DRAFT;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_END;
import static com.eric.base.sys.process.properties.SysProcessConstants.OPERATE_TYPE_ADMIN_ABANDON;
import static com.eric.base.sys.process.properties.SysProcessConstants.OPERATE_TYPE_ADMIN_CHANGE_HANDLER;
import static com.eric.base.sys.process.properties.SysProcessConstants.OPERATE_TYPE_ADMIN_JUMP;
import static com.eric.base.sys.process.properties.SysProcessConstants.OPERATE_TYPE_DRAFTER_CANCEL;
import static com.eric.base.sys.process.properties.SysProcessConstants.OPERATE_TYPE_DRAFTER_EXPEDITE;
import static com.eric.base.sys.process.properties.SysProcessConstants.OPERATE_TYPE_DRAFTER_RETRACT;
import static com.eric.base.sys.process.properties.SysProcessConstants.OPERATE_TYPE_DRAFTER_SUBMIT;
import static com.eric.base.sys.process.properties.SysProcessConstants.OPERATE_TYPE_HANDLER_CC;
import static com.eric.base.sys.process.properties.SysProcessConstants.OPERATE_TYPE_HANDLER_PASS;
import static com.eric.base.sys.process.properties.SysProcessConstants.OPERATE_TYPE_HANDLER_REJECT;
import static com.eric.base.sys.process.properties.SysProcessConstants.OPERATE_TYPE_HANDLER_TRANSFER;
import static com.eric.base.sys.process.properties.SysProcessConstants.OPERATE_TYPE_HANDLER_TRANSFER_RETRACT;
import static com.eric.base.sys.process.properties.SysProcessConstants.OPERATE_TYPE_PROCESSED_EXPEDITE;
import static com.eric.base.sys.process.properties.SysProcessConstants.OPERATE_TYPE_PROCESSED_RETRACT;
import static com.eric.base.sys.process.properties.SysProcessConstants.RESUME_MODE_RESTART;
import static com.eric.base.sys.process.properties.SysProcessConstants.ROLE_SYS_PROCESS_EXAMPLE_ADMIN;
import static com.eric.base.sys.process.properties.SysProcessConstants.WORKITEM_STATUS_ABANDON;
import static com.eric.base.sys.process.properties.SysProcessConstants.WORKITEM_STATUS_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.WORKITEM_STATUS_PROCESSING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 14 类审批操作矩阵测试（P17-QA-002，docs/v1_7-流程管理 任务卡）
 *
 * @author Eric
 * @date 2026/11/21
 * @description 以 14 类小写操作类型为行、身份 × 状态 × 必填参数为列的系统性矩阵收口
 *              （期望口径 = docs/设计文档/sys/process/流程v1_7接口清单.md §6.2 操作矩阵、
 *              §6.3 操作人身份校验与 §10 错误码；既有分散用例见 SysProcessExampleServiceTest，
 *              本类聚焦缺口补齐与全矩阵回归，不重写已有用例）。
 *              <p><b>矩阵总表（行 = 操作类型；身份列取值：起草人/当前处理人/已处理人（转办发起人
 *              单列）/流程管理员/无关用户/未登录；状态列取值：DRAFT/RETURN/PROCESSING/PAUSE/
 *              FINISH/ABANDON；✓=放行，F=FORBIDDEN（CS_PROCESS_OPERATE_FORBIDDEN），
 *              S=STATE_CONFLICT，P=PARAM_MISSING）</b></p>
 * <pre>
 * 操作                        合法身份        合法状态                 越权身份格        状态冲突格              参数缺失格
 * drafter_submit              起草人          DRAFT/RETURN            其余全部 F        PROCESSING/PAUSE/FINISH/ABANDON→S  exampleId/operateType
 * drafter_cancel              起草人          DRAFT/RETURN            其余全部 F        同上                    content
 * drafter_retract             起草人          PROCESSING              其余全部 F        DRAFT/RETURN/PAUSE/FINISH/ABANDON→S  exampleId/operateType
 * drafter_expedite            起草人          PROCESSING              其余全部 F        同上                    content
 * handler_pass                当前处理人      PROCESSING              其余全部 F        同上                    workitemId
 * handler_reject              当前处理人      PROCESSING              其余全部 F        同上                    workitemId/rejectTargetKey/resumeMode/content
 * handler_cc                  当前处理人      PROCESSING              其余全部 F        同上                    workitemId/ccToIds/content
 * handler_transfer            当前处理人      PROCESSING              其余全部 F        同上                    workitemId/transferToIds/content
 * handler_transfer_retract    转办发起人      PROCESSING              其余全部 F        同上                    workitemId/content
 * processed_retract           已处理人        PROCESSING              其余全部 F        同上                    workitemId/content
 * processed_expedite          已处理人        PROCESSING              其余全部 F        同上                    workitemId/content
 * admin_abandon               流程管理员      PROCESSING/RETURN/DRAFT/PAUSE  其余全部 F  FINISH/ABANDON→S       content
 * admin_jump                  流程管理员      PROCESSING/RETURN       其余全部 F        DRAFT/PAUSE/FINISH/ABANDON→S  targetNodeId/content
 * admin_change_handler        流程管理员      PROCESSING              其余全部 F        同上                    workitemId/changeToId/content
 * </pre>
 * <p>未登录列：起草人/处理人/已处理人身份类操作在引擎内 {@code requireCurrentUser} 处
 * IllegalStateException（接口层由 resolver 先行 UNAUTHORIZED，引擎内为防御性兜底）；
 * 管理员身份类操作经 {@code requireProcessAdmin} 抛 AuthorizationException（FORBIDDEN）。
 * 通知副作用统一断言 {@link SysNotifyTodoService#send} 调用与否（Mock 桩，不真发）。</p>
 */
class ProcessOperateMatrixTest {

    private static final String EXAMPLE_ID = "12345678901234567890123456789001";
    private static final String CREATOR_ID = "12345678901234567890123456789002";
    private static final String U1 = "12345678901234567890123456789003";
    private static final String U2 = "12345678901234567890123456789004";
    private static final String U3 = "12345678901234567890123456789006";
    private static final String ADMIN_ID = "12345678901234567890123456789007";
    private static final String STRANGER_ID = "12345678901234567890123456789008";
    private static final String TEMPLATE_ID = "12345678901234567890123456789005";
    private static final String NODE3_ID = "12345678901234567890123456789010";
    private static final String NODE4_ID = "12345678901234567890123456789011";
    private static final String NODE5_ID = "12345678901234567890123456789012";
    private static final String WI_U1 = "12345678901234567890123456789020";
    private static final String WI_U2 = "12345678901234567890123456789021";
    private static final String WI_U3 = "12345678901234567890123456789022";
    private static final String WI_TRANS = "12345678901234567890123456789023";

    /** 单签审批节点配置（处理人 U1/U2，skipMode=system 与公共配置联动，对齐既有用例口径） */
    private static final String CONFIG_SINGLE =
            "{\"handlerIds\":\"" + U1 + ";" + U2 + "\",\"handlerNames\":\"张三;李四\","
                    + "\"approveMode\":\"single\",\"triggerType\":\"fixed\","
                    + "\"autoSkip\":\"system\",\"autoContinueSkip\":\"system\"}";

    /* ==================== 矩阵数据（常量形式落在测试类内，与类头总表一一对应） ==================== */

    /** 14 类操作类型全集 */
    private static final List<String> ALL_OPERATIONS = List.of(
            OPERATE_TYPE_DRAFTER_SUBMIT, OPERATE_TYPE_DRAFTER_CANCEL, OPERATE_TYPE_DRAFTER_RETRACT,
            OPERATE_TYPE_DRAFTER_EXPEDITE, OPERATE_TYPE_HANDLER_PASS, OPERATE_TYPE_HANDLER_REJECT,
            OPERATE_TYPE_HANDLER_CC, OPERATE_TYPE_HANDLER_TRANSFER, OPERATE_TYPE_HANDLER_TRANSFER_RETRACT,
            OPERATE_TYPE_PROCESSED_RETRACT, OPERATE_TYPE_PROCESSED_EXPEDITE, OPERATE_TYPE_ADMIN_ABANDON,
            OPERATE_TYPE_ADMIN_JUMP, OPERATE_TYPE_ADMIN_CHANGE_HANDLER);

    /** 六个流程状态全集 */
    private static final List<String> ALL_STATUSES = List.of(
            EXAMPLE_STATUS_DRAFT, EXAMPLE_STATUS_RETURN, EXAMPLE_STATUS_PROCESSING,
            EXAMPLE_STATUS_PAUSE, EXAMPLE_STATUS_FINISH, EXAMPLE_STATUS_ABANDON);

    /** 各操作的合法状态（矩阵「状态列」放行格） */
    private static final Map<String, List<String>> LEGAL_STATUSES = Map.ofEntries(
            Map.entry(OPERATE_TYPE_DRAFTER_SUBMIT, List.of(EXAMPLE_STATUS_DRAFT, EXAMPLE_STATUS_RETURN)),
            Map.entry(OPERATE_TYPE_DRAFTER_CANCEL, List.of(EXAMPLE_STATUS_DRAFT, EXAMPLE_STATUS_RETURN)),
            Map.entry(OPERATE_TYPE_DRAFTER_RETRACT, List.of(EXAMPLE_STATUS_PROCESSING)),
            Map.entry(OPERATE_TYPE_DRAFTER_EXPEDITE, List.of(EXAMPLE_STATUS_PROCESSING)),
            Map.entry(OPERATE_TYPE_HANDLER_PASS, List.of(EXAMPLE_STATUS_PROCESSING)),
            Map.entry(OPERATE_TYPE_HANDLER_REJECT, List.of(EXAMPLE_STATUS_PROCESSING)),
            Map.entry(OPERATE_TYPE_HANDLER_CC, List.of(EXAMPLE_STATUS_PROCESSING)),
            Map.entry(OPERATE_TYPE_HANDLER_TRANSFER, List.of(EXAMPLE_STATUS_PROCESSING)),
            Map.entry(OPERATE_TYPE_HANDLER_TRANSFER_RETRACT, List.of(EXAMPLE_STATUS_PROCESSING)),
            Map.entry(OPERATE_TYPE_PROCESSED_RETRACT, List.of(EXAMPLE_STATUS_PROCESSING)),
            Map.entry(OPERATE_TYPE_PROCESSED_EXPEDITE, List.of(EXAMPLE_STATUS_PROCESSING)),
            Map.entry(OPERATE_TYPE_ADMIN_ABANDON, List.of(EXAMPLE_STATUS_PROCESSING, EXAMPLE_STATUS_RETURN,
                    EXAMPLE_STATUS_DRAFT, EXAMPLE_STATUS_PAUSE)),
            Map.entry(OPERATE_TYPE_ADMIN_JUMP, List.of(EXAMPLE_STATUS_PROCESSING, EXAMPLE_STATUS_RETURN)),
            Map.entry(OPERATE_TYPE_ADMIN_CHANGE_HANDLER, List.of(EXAMPLE_STATUS_PROCESSING)));

    /** 操作人身份（矩阵「身份列」） */
    private enum Identity {
        DRAFTER, HANDLER, PROCESSED, TRANSFER_INITIATOR, PROCESS_ADMIN, STRANGER, ANONYMOUS
    }

    /** 各操作的合法执行身份（矩阵「身份列」放行格；转办发起人单列） */
    private static final Map<String, Identity> LEGAL_IDENTITY = Map.ofEntries(
            Map.entry(OPERATE_TYPE_DRAFTER_SUBMIT, Identity.DRAFTER),
            Map.entry(OPERATE_TYPE_DRAFTER_CANCEL, Identity.DRAFTER),
            Map.entry(OPERATE_TYPE_DRAFTER_RETRACT, Identity.DRAFTER),
            Map.entry(OPERATE_TYPE_DRAFTER_EXPEDITE, Identity.DRAFTER),
            Map.entry(OPERATE_TYPE_HANDLER_PASS, Identity.HANDLER),
            Map.entry(OPERATE_TYPE_HANDLER_REJECT, Identity.HANDLER),
            Map.entry(OPERATE_TYPE_HANDLER_CC, Identity.HANDLER),
            Map.entry(OPERATE_TYPE_HANDLER_TRANSFER, Identity.HANDLER),
            Map.entry(OPERATE_TYPE_HANDLER_TRANSFER_RETRACT, Identity.TRANSFER_INITIATOR),
            Map.entry(OPERATE_TYPE_PROCESSED_RETRACT, Identity.PROCESSED),
            Map.entry(OPERATE_TYPE_PROCESSED_EXPEDITE, Identity.PROCESSED),
            Map.entry(OPERATE_TYPE_ADMIN_ABANDON, Identity.PROCESS_ADMIN),
            Map.entry(OPERATE_TYPE_ADMIN_JUMP, Identity.PROCESS_ADMIN),
            Map.entry(OPERATE_TYPE_ADMIN_CHANGE_HANDLER, Identity.PROCESS_ADMIN));

    /** 各操作的越权身份格（合法身份之外的全体身份，含流程管理员对起草人/处理人类操作同样越权） */
    private static final Map<String, List<Identity>> FORBIDDEN_IDENTITIES = Map.ofEntries(
            Map.entry(OPERATE_TYPE_DRAFTER_SUBMIT, forbiddenExcept(Identity.DRAFTER)),
            Map.entry(OPERATE_TYPE_DRAFTER_CANCEL, forbiddenExcept(Identity.DRAFTER)),
            Map.entry(OPERATE_TYPE_DRAFTER_RETRACT, forbiddenExcept(Identity.DRAFTER)),
            Map.entry(OPERATE_TYPE_DRAFTER_EXPEDITE, forbiddenExcept(Identity.DRAFTER)),
            Map.entry(OPERATE_TYPE_HANDLER_PASS, forbiddenExcept(Identity.HANDLER)),
            Map.entry(OPERATE_TYPE_HANDLER_REJECT, forbiddenExcept(Identity.HANDLER)),
            Map.entry(OPERATE_TYPE_HANDLER_CC, forbiddenExcept(Identity.HANDLER)),
            Map.entry(OPERATE_TYPE_HANDLER_TRANSFER, forbiddenExcept(Identity.HANDLER)),
            Map.entry(OPERATE_TYPE_HANDLER_TRANSFER_RETRACT, forbiddenExcept(Identity.TRANSFER_INITIATOR)),
            Map.entry(OPERATE_TYPE_PROCESSED_RETRACT, forbiddenExcept(Identity.PROCESSED)),
            Map.entry(OPERATE_TYPE_PROCESSED_EXPEDITE, forbiddenExcept(Identity.PROCESSED)),
            Map.entry(OPERATE_TYPE_ADMIN_ABANDON, forbiddenExcept(Identity.PROCESS_ADMIN)),
            Map.entry(OPERATE_TYPE_ADMIN_JUMP, forbiddenExcept(Identity.PROCESS_ADMIN)),
            Map.entry(OPERATE_TYPE_ADMIN_CHANGE_HANDLER, forbiddenExcept(Identity.PROCESS_ADMIN)));

    /** 各操作的必填参数（除 exampleId/operateType 外，矩阵「参数缺失格」数据源） */
    private static final Map<String, List<String>> REQUIRED_PARAMS = Map.ofEntries(
            Map.entry(OPERATE_TYPE_DRAFTER_SUBMIT, List.<String>of()),
            Map.entry(OPERATE_TYPE_DRAFTER_CANCEL, List.of("content")),
            Map.entry(OPERATE_TYPE_DRAFTER_RETRACT, List.<String>of()),
            Map.entry(OPERATE_TYPE_DRAFTER_EXPEDITE, List.of("content")),
            Map.entry(OPERATE_TYPE_HANDLER_PASS, List.of("workitemId")),
            Map.entry(OPERATE_TYPE_HANDLER_REJECT, List.of("workitemId", "rejectTargetKey", "resumeMode", "content")),
            Map.entry(OPERATE_TYPE_HANDLER_CC, List.of("workitemId", "ccToIds", "content")),
            Map.entry(OPERATE_TYPE_HANDLER_TRANSFER, List.of("workitemId", "transferToIds", "content")),
            Map.entry(OPERATE_TYPE_HANDLER_TRANSFER_RETRACT, List.of("workitemId", "content")),
            Map.entry(OPERATE_TYPE_PROCESSED_RETRACT, List.of("workitemId", "content")),
            Map.entry(OPERATE_TYPE_PROCESSED_EXPEDITE, List.of("workitemId", "content")),
            Map.entry(OPERATE_TYPE_ADMIN_ABANDON, List.of("content")),
            Map.entry(OPERATE_TYPE_ADMIN_JUMP, List.of("targetNodeId", "content")),
            Map.entry(OPERATE_TYPE_ADMIN_CHANGE_HANDLER, List.of("workitemId", "changeToId", "content")));

    private static List<Identity> forbiddenExcept(Identity legal) {
        return List.of(Identity.DRAFTER, Identity.HANDLER, Identity.PROCESSED,
                Identity.TRANSFER_INITIATOR, Identity.PROCESS_ADMIN, Identity.STRANGER).stream()
                .filter(identity -> identity != legal)
                .toList();
    }

    /* ==================== 夹具（Mockito 桩 DAO + MockedStatic<UserUtil>，对齐 SysProcessExampleServiceTest） ==================== */

    private SysProcessExampleDao exampleDao;
    private SysProcessExampleNodeDao nodeDao;
    private SysProcessExampleWorkitemDao workitemDao;
    private SysProcessAuditNoteDao auditNoteDao;
    private SysProcessTemplateDao templateDao;
    private SysProcessTemplateNodeDao templateNodeDao;
    private SysProcessTemplateLineDao templateLineDao;
    private CommonSettingService settingService;
    private SysNotifyTodoService notifyTodoService;
    private SysOrgUserMapper userMapper;
    private ProcessResolver resolver;
    private SysProcessDocumentFieldService documentFieldService;
    private SysProcessExampleLineDao exampleLineDao;
    private ProcessHandlerResolver handlerResolver;
    private ProcessFlowEngine flowEngine;
    private SysProcessExampleService service;
    private MockedStatic<UserUtil> userUtil;

    private SysProcessExample example;
    private List<SysProcessExampleNode> exampleNodes;
    private List<SysProcessExampleWorkitem> allWorkitems;
    /** insert 捕获列表（转办/收回/流转生成的工作项断言） */
    private List<SysProcessExampleWorkitem> insertedWorkitems;

    @BeforeEach
    void setUp() {
        exampleDao = mock(SysProcessExampleDao.class);
        nodeDao = mock(SysProcessExampleNodeDao.class);
        workitemDao = mock(SysProcessExampleWorkitemDao.class);
        auditNoteDao = mock(SysProcessAuditNoteDao.class);
        templateDao = mock(SysProcessTemplateDao.class);
        templateNodeDao = mock(SysProcessTemplateNodeDao.class);
        templateLineDao = mock(SysProcessTemplateLineDao.class);
        settingService = mock(CommonSettingService.class);
        notifyTodoService = mock(SysNotifyTodoService.class);
        userMapper = mock(SysOrgUserMapper.class);
        resolver = mock(ProcessResolver.class);
        documentFieldService = mock(SysProcessDocumentFieldService.class);
        exampleLineDao = mock(SysProcessExampleLineDao.class);
        handlerResolver = mock(ProcessHandlerResolver.class);
        ProcessNotifySender notifySender =
                new ProcessNotifySender(notifyTodoService, userMapper, templateDao, settingService);
        ProcessFlowSupport flowSupport = new ProcessFlowSupport(exampleDao, nodeDao, workitemDao,
                auditNoteDao, new ObjectMapper());
        flowEngine = new ProcessFlowEngine(exampleDao, nodeDao, workitemDao, auditNoteDao,
                settingService, new ProcessFlowGraphLoader(exampleLineDao), flowSupport,
                handlerResolver, notifySender, documentFieldService, null, null);
        service = new SysProcessExampleService(exampleDao, nodeDao, exampleLineDao, workitemDao,
                auditNoteDao, templateDao, templateNodeDao, templateLineDao, settingService,
                userMapper, notifySender, resolver, documentFieldService,
                new ObjectMapper(), flowEngine, flowSupport);

        when(userMapper.selectById(anyString())).thenReturn(null);
        when(userMapper.selectById(CREATOR_ID)).thenReturn(user(CREATOR_ID, "起草人"));
        when(userMapper.selectById(U1)).thenReturn(user(U1, "张三"));
        when(userMapper.selectById(U2)).thenReturn(user(U2, "李四"));
        when(userMapper.selectById(U3)).thenReturn(user(U3, "王五"));
        when(userMapper.selectById(ADMIN_ID)).thenReturn(user(ADMIN_ID, "管理员"));
        when(userMapper.selectById(STRANGER_ID)).thenReturn(user(STRANGER_ID, "路人"));

        // 默认公共配置：自动跳过/包含起草人/连续跳过均开启（skipMode=system 联动口径，对齐既有用例）
        when(settingService.getBooleanValue(eq(SysProcessConstants.SETTING_TYPE_PROCESS),
                eq(SysProcessConstants.SETTING_PROCESS_AUTO_HANDLE_PASS))).thenReturn(true);
        when(settingService.getBooleanValue(eq(SysProcessConstants.SETTING_TYPE_PROCESS),
                eq(SysProcessConstants.SETTING_PROCESS_AUTO_HANDLE_PASS_DRAFTER))).thenReturn(true);
        when(settingService.getBooleanValue(eq(SysProcessConstants.SETTING_TYPE_PROCESS),
                eq(SysProcessConstants.SETTING_PROCESS_AUTO_CONTINUE_PASS))).thenReturn(true);

        // 审批人解析服务桩：按节点配置 handlerIds 返回处理人（去重保序），抄送对象为空
        when(handlerResolver.resolveHandlerIds(any(), any(), anyMap())).thenAnswer(invocation -> {
            SysProcessTemplateNodeConfigDo config = invocation.getArgument(0);
            return null == config ? List.of() : splitIds(config.getHandlerIds());
        });
        when(handlerResolver.resolveCcIds(any(), any(), anyMap())).thenReturn(List.of());

        templateDaoTemplateStubs();

        // 矩阵默认在途实例：PROCESSING + 审批节点3 在途（各用例按需覆写状态）
        example = example(EXAMPLE_STATUS_PROCESSING);
        when(exampleDao.selectById(EXAMPLE_ID)).thenReturn(example);
        exampleNodes = new ArrayList<>(List.of(
                exampleNode("12345678901234567890123456789091", 1, "开始节点", "START",
                        SysProcessConstants.NODE_STATUS_FINISH, null),
                exampleNode("12345678901234567890123456789092", 2, "草稿节点", "DRAFT",
                        SysProcessConstants.NODE_STATUS_FINISH, null),
                exampleNode(NODE3_ID, 3, "审批节点3", "APPROVE", NODE_STATUS_WAITING, CONFIG_SINGLE),
                exampleNode(NODE4_ID, 4, "审批节点4", "APPROVE", NODE_STATUS_WAITING, CONFIG_SINGLE),
                exampleNode(NODE5_ID, 5, "结束节点", "END", NODE_STATUS_WAITING, null)));
        when(nodeDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(exampleNodes);
        // 按节点 id 精确查找（requireNode / 引擎节点定位用）
        when(nodeDao.selectById(anyString())).thenAnswer(invocation -> {
            String nodeId = invocation.getArgument(0);
            for (SysProcessExampleNode node : exampleNodes) {
                if (nodeId.equals(node.getFieldId())) {
                    return node;
                }
            }
            return null;
        });
        when(nodeDao.selectByExampleAndStatus(anyString(), eq(NODE_STATUS_REJECT))).thenReturn(List.of());
        when(nodeDao.selectByExampleAndType(EXAMPLE_ID, NODE_TYPE_DRAFT)).thenReturn(exampleNodeAt(2));
        when(nodeDao.selectByExampleAndType(EXAMPLE_ID, NODE_TYPE_END)).thenReturn(exampleNodeAt(5));
        when(exampleLineDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(flowLines());

        // 工作项全集（矩阵四类身份的工作项落点）：
        // WI_U1=当前处理人 U1（PROCESSING）/ WI_U2=已处理人 U2（FINISH）/
        // WI_U3=转办发起人 U3 的原工作项（ABANDON）/ WI_TRANS=被转办人 U1 的在途工作项（链回 WI_U3）
        allWorkitems = new ArrayList<>(List.of(
                workitem(WI_U1, NODE3_ID, U1, WORKITEM_STATUS_PROCESSING),
                workitem(WI_U2, NODE3_ID, U2, WORKITEM_STATUS_FINISH),
                workitem(WI_U3, NODE3_ID, U3, WORKITEM_STATUS_ABANDON)));
        SysProcessExampleWorkitem transferred = workitem(WI_TRANS, NODE3_ID, U1, WORKITEM_STATUS_PROCESSING);
        transferred.setFieldFromWorkitemId(WI_U3);
        allWorkitems.add(transferred);
        when(workitemDao.selectById(WI_U1)).thenReturn(allWorkitems.get(0));
        when(workitemDao.selectById(WI_U2)).thenReturn(allWorkitems.get(1));
        when(workitemDao.selectById(WI_U3)).thenReturn(allWorkitems.get(2));
        when(workitemDao.selectById(WI_TRANS)).thenReturn(transferred);
        when(workitemDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(allWorkitems);
        when(workitemDao.selectListByNodeId(anyString())).thenAnswer(invocation -> {
            String nodeId = invocation.getArgument(0);
            List<SysProcessExampleWorkitem> matched = new ArrayList<>();
            for (SysProcessExampleWorkitem item : allWorkitems) {
                if (nodeId.equals(item.getFieldNodeId())) {
                    matched.add(item);
                }
            }
            return matched;
        });
        when(workitemDao.insert(any(SysProcessExampleWorkitem.class))).thenAnswer(invocation -> {
            SysProcessExampleWorkitem created = invocation.getArgument(0);
            if (null == created.getFieldCreateTime()) {
                created.setFieldCreateTime(new java.util.Date());
            }
            insertedWorkitems.add(created);
            return 1;
        });
        when(auditNoteDao.insert(any(SysProcessAuditNote.class))).thenReturn(1);
        when(exampleDao.update(any(SysProcessExample.class))).thenReturn(1);

        insertedWorkitems = new ArrayList<>();
        userUtil = mockStatic(UserUtil.class);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(CREATOR_ID, "起草人"));
    }

    @AfterEach
    void tearDown() {
        userUtil.close();
    }

    /** 模板桩（标题渲染兜底与 submit 引擎流转需要） */
    private void templateDaoTemplateStubs() {
        SysProcessTemplate template = new SysProcessTemplate();
        template.setFieldId(TEMPLATE_ID);
        template.setFieldName("采购订单审批模板");
        template.setFieldModelName("sys_org");
        template.setFieldVersion(1);
        template.setFieldEnable(true);
        when(templateDao.selectById(TEMPLATE_ID)).thenReturn(template);
        when(templateNodeDao.selectListByTemplateId(TEMPLATE_ID)).thenReturn(List.of(
                templateNode(1, "开始节点", "START", null),
                templateNode(2, "草稿节点", "DRAFT", null),
                templateNode(3, "审批节点3", "APPROVE", CONFIG_SINGLE),
                templateNode(4, "审批节点4", "APPROVE", CONFIG_SINGLE),
                templateNode(5, "结束节点", "END", null)));
    }

    /* ==================== 矩阵工具 ==================== */

    private void loginAs(Identity identity) {
        switch (identity) {
            case DRAFTER -> userUtil.when(UserUtil::getCurrentUser).thenReturn(user(CREATOR_ID, "起草人"));
            case HANDLER -> userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U1, "张三"));
            case PROCESSED -> userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U2, "李四"));
            case TRANSFER_INITIATOR -> userUtil.when(UserUtil::getCurrentUser).thenReturn(user(U3, "王五"));
            case PROCESS_ADMIN -> userUtil.when(UserUtil::getCurrentUser)
                    .thenReturn(userWithRoles(ADMIN_ID, "管理员", ROLE_SYS_PROCESS_EXAMPLE_ADMIN));
            case STRANGER -> userUtil.when(UserUtil::getCurrentUser).thenReturn(user(STRANGER_ID, "路人"));
            case ANONYMOUS -> userUtil.when(UserUtil::getCurrentUser).thenReturn(null);
        }
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

    /** 解析审批人 id 列表（';' 分隔，去重保序） */
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

    private static SysOrgUser user(String id, String name) {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(id);
        user.setFieldName(name);
        user.setFieldEnable(true);
        return user;
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

    /**
     * 构造某操作类型的「全参数就绪」请求（各类型只消费自己关心的字段，多带参数无副作用）。
     * workitemId 按操作语义落点选择：转办收回=转办发起人原工作项 WI_U3；已处理人类操作=
     * 已处理人 U2 的工作项 WI_U2；其余审批人类/管理员换人操作=当前处理人工作项 WI_U1。
     */
    private SysProcessOperateRequest validRequest(String operateType) {
        SysProcessOperateRequest request = new SysProcessOperateRequest();
        request.setExampleId(EXAMPLE_ID);
        request.setOperateType(operateType);
        String semanticWorkitemId = WI_U1;
        if (OPERATE_TYPE_HANDLER_TRANSFER_RETRACT.equals(operateType)) {
            semanticWorkitemId = WI_U3;
        } else if (OPERATE_TYPE_PROCESSED_RETRACT.equals(operateType)
                || OPERATE_TYPE_PROCESSED_EXPEDITE.equals(operateType)) {
            semanticWorkitemId = WI_U2;
        }
        request.setWorkitemId(semanticWorkitemId);
        request.setContent("矩阵测试意见");
        request.setRejectTargetKey(FIXED_NODE_KEY_DRAFT);
        request.setResumeMode(RESUME_MODE_RESTART);
        request.setTransferToIds(U2);
        request.setCcToIds(U2);
        request.setChangeToId(U2);
        request.setTargetNodeId(NODE4_ID);
        return request;
    }

    /** 产出缺省指定参数的请求（参数缺失格数据构造） */
    private SysProcessOperateRequest requestMissingParam(String operateType, String missingParam) {
        SysProcessOperateRequest request = validRequest(operateType);
        switch (missingParam) {
            case "workitemId" -> request.setWorkitemId(null);
            case "content" -> request.setContent(null);
            case "rejectTargetKey" -> request.setRejectTargetKey(null);
            case "resumeMode" -> request.setResumeMode(null);
            case "transferToIds" -> request.setTransferToIds(null);
            case "ccToIds" -> request.setCcToIds(null);
            case "changeToId" -> request.setChangeToId(null);
            case "targetNodeId" -> request.setTargetNodeId(null);
            default -> throw new IllegalArgumentException("unknown matrix param: " + missingParam);
        }
        return request;
    }

    /** 审批节点3 置在途（在途操作矩阵格的现实前置） */
    private void node3Processing() {
        exampleNodeAt(3).setFieldStatus(NODE_STATUS_PROCESSING);
        when(nodeDao.selectByExampleAndStatus(EXAMPLE_ID, NODE_STATUS_PROCESSING))
                .thenReturn(List.of(exampleNodeAt(3)));
    }

    /* ==================== 合法路径格（身份 × 合法状态 → 成功，断言状态迁移/留痕/通知副作用） ==================== */

    @Test
    @DisplayName("[矩阵: drafter_submit] 起草人 × DRAFT → 提交成功：DRAFT→PROCESSING + DRAFTER_SUBMIT 留痕 + 首审批节点建工作项")
    void drafterSubmitLegalPathFromDraft() {
        example.setFieldStatus(EXAMPLE_STATUS_DRAFT);
        loginAs(Identity.DRAFTER);

        SysProcessExample result = service.operate(validRequest(OPERATE_TYPE_DRAFTER_SUBMIT));

        assertEquals(EXAMPLE_STATUS_PROCESSING, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n -> AUDIT_TYPE_DRAFTER_SUBMIT.equals(n.getFieldHandleType())
                && CREATOR_ID.equals(n.getFieldHandlerId())));
        verify(workitemDao, times(2)).insert(argThat(w -> NODE3_ID.equals(w.getFieldNodeId())
                && WORKITEM_STATUS_PROCESSING.equals(w.getFieldStatus())));
    }

    @Test
    @DisplayName("[矩阵: drafter_submit] 起草人 × RETURN → 驳回后重新提交成功")
    void drafterSubmitLegalPathFromReturn() {
        example.setFieldStatus(EXAMPLE_STATUS_RETURN);
        loginAs(Identity.DRAFTER);

        SysProcessExample result = service.operate(validRequest(OPERATE_TYPE_DRAFTER_SUBMIT));

        assertEquals(EXAMPLE_STATUS_PROCESSING, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n -> AUDIT_TYPE_DRAFTER_SUBMIT.equals(n.getFieldHandleType())));
    }

    @Test
    @DisplayName("[矩阵: drafter_cancel] 起草人 × DRAFT → 取消成功：DRAFT→ABANDON + DRAFTER_ABANDON 留痕")
    void drafterCancelLegalPathFromDraft() {
        example.setFieldStatus(EXAMPLE_STATUS_DRAFT);
        loginAs(Identity.DRAFTER);

        SysProcessExample result = service.operate(validRequest(OPERATE_TYPE_DRAFTER_CANCEL));

        assertEquals(EXAMPLE_STATUS_ABANDON, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n -> AUDIT_TYPE_DRAFTER_ABANDON.equals(n.getFieldHandleType())));
        verify(nodeDao).update(argThat(n -> NODE5_ID.equals(n.getFieldId())
                && NODE_STATUS_PROCESSING.equals(n.getFieldStatus())));
    }

    @Test
    @DisplayName("[矩阵: drafter_cancel] 起草人 × RETURN → 取消成功")
    void drafterCancelLegalPathFromReturn() {
        example.setFieldStatus(EXAMPLE_STATUS_RETURN);
        loginAs(Identity.DRAFTER);

        SysProcessExample result = service.operate(validRequest(OPERATE_TYPE_DRAFTER_CANCEL));

        assertEquals(EXAMPLE_STATUS_ABANDON, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n -> AUDIT_TYPE_DRAFTER_ABANDON.equals(n.getFieldHandleType())));
    }

    @Test
    @DisplayName("[矩阵: drafter_retract] 起草人 × PROCESSING → 收回成功：PROCESSING→DRAFT + DRAFTER_BACK 留痕 + 在途工作项废弃")
    void drafterRetractLegalPath() {
        node3Processing();
        loginAs(Identity.DRAFTER);

        SysProcessExample result = service.operate(validRequest(OPERATE_TYPE_DRAFTER_RETRACT));

        assertEquals(EXAMPLE_STATUS_DRAFT, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n -> AUDIT_TYPE_DRAFTER_BACK.equals(n.getFieldHandleType())));
        verify(nodeDao).update(argThat(n -> 2 == n.getFieldNodeNo()
                && NODE_STATUS_PROCESSING.equals(n.getFieldStatus())));
    }

    @Test
    @DisplayName("[矩阵: drafter_expedite] 起草人 × PROCESSING → 催办成功：留痕 DRAFTER_EXPEDITE + READ 待阅发当前处理人（排除发起人）+ 状态不变")
    void drafterExpediteLegalPath() {
        node3Processing();
        loginAs(Identity.DRAFTER);

        SysProcessExample result = service.operate(validRequest(OPERATE_TYPE_DRAFTER_EXPEDITE));

        assertEquals(EXAMPLE_STATUS_PROCESSING, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n -> AUDIT_TYPE_DRAFTER_EXPEDITE.equals(n.getFieldHandleType())
                && CREATOR_ID.equals(n.getFieldHandlerId())));
        verify(notifyTodoService).send(argThat(todo ->
                        SysNotifyType.READ.getValue().equals(todo.getFieldNotifyType())),
                argThat(ids -> ids.size() == 1 && ids.get(0).equals(U1)));
    }

    @Test
    @DisplayName("[矩阵: handler_pass] 当前处理人 × PROCESSING → 通过成功：HANDLER_PASS 留痕 + 工作项 FINISH + 流程办结")
    void handlerPassLegalPath() {
        node3Processing();
        loginAs(Identity.HANDLER);

        SysProcessExample result = service.operate(validRequest(OPERATE_TYPE_HANDLER_PASS));

        assertEquals(EXAMPLE_STATUS_FINISH, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n -> AUDIT_TYPE_HANDLER_PASS.equals(n.getFieldHandleType())
                && WI_U1.equals(n.getFieldWorkitemId()) && U1.equals(n.getFieldHandlerId())));
        assertEquals(WORKITEM_STATUS_FINISH, workitemDao.selectById(WI_U1).getFieldStatus());
    }

    @Test
    @DisplayName("[矩阵: handler_reject] 当前处理人 × PROCESSING → 驳回成功：HANDLER_REJECT 留痕 + PROCESSING→RETURN + 被驳回待办发起草人")
    void handlerRejectLegalPath() {
        node3Processing();
        loginAs(Identity.HANDLER);
        SysProcessOperateRequest request = validRequest(OPERATE_TYPE_HANDLER_REJECT);
        request.setRejectTargetKey(FIXED_NODE_KEY_DRAFT);

        SysProcessExample result = service.operate(request);

        assertEquals(EXAMPLE_STATUS_RETURN, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n -> AUDIT_TYPE_HANDLER_REJECT.equals(n.getFieldHandleType())
                && WI_U1.equals(n.getFieldWorkitemId())));
        verify(notifyTodoService).send(any(SysNotifyTodo.class),
                argThat(ids -> ids.size() == 1 && ids.get(0).equals(CREATOR_ID)));
        assertEquals(WORKITEM_STATUS_FINISH, workitemDao.selectById(WI_U1).getFieldStatus());
        assertEquals(NODE_STATUS_PROCESSING, exampleNodeAt(2).getFieldStatus());
    }

    @Test
    @DisplayName("[矩阵: handler_cc] 当前处理人 × PROCESSING → 抄送成功：HANDLER_CC 留痕 + READ 待阅发抄送对象 + 状态不变")
    void handlerCcLegalPath() {
        node3Processing();
        loginAs(Identity.HANDLER);
        SysProcessOperateRequest request = validRequest(OPERATE_TYPE_HANDLER_CC);
        request.setCcToIds(U2);

        SysProcessExample result = service.operate(request);

        assertEquals(EXAMPLE_STATUS_PROCESSING, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n -> AUDIT_TYPE_HANDLER_CC.equals(n.getFieldHandleType())
                && WI_U1.equals(n.getFieldWorkitemId()) && "矩阵测试意见".equals(n.getFieldContent())));
        verify(notifyTodoService).send(argThat(todo ->
                        SysNotifyType.READ.getValue().equals(todo.getFieldNotifyType())),
                argThat(ids -> ids.size() == 1 && ids.get(0).equals(U2)));
        // 抄送是在途告知：不改工作项/节点/流程状态
        verify(workitemDao, never()).update(any(SysProcessExampleWorkitem.class));
        assertEquals(WORKITEM_STATUS_PROCESSING, workitemDao.selectById(WI_U1).getFieldStatus());
    }

    @Test
    @DisplayName("[矩阵: handler_transfer] 当前处理人 × PROCESSING → 转办成功：原工作项 ABANDON + 生成被转办人工作项（链回原工作项）+ 留痕一条")
    void handlerTransferLegalPath() {
        node3Processing();
        loginAs(Identity.HANDLER);
        SysProcessOperateRequest request = validRequest(OPERATE_TYPE_HANDLER_TRANSFER);
        request.setWorkitemId(WI_U1);
        request.setTransferToIds(U2);

        SysProcessExample result = service.operate(request);

        assertEquals(EXAMPLE_STATUS_PROCESSING, result.getFieldStatus());
        assertEquals(WORKITEM_STATUS_ABANDON, workitemDao.selectById(WI_U1).getFieldStatus());
        verify(auditNoteDao).insert(argThat(n -> AUDIT_TYPE_HANDLER_TRANSFER.equals(n.getFieldHandleType())
                && WI_U1.equals(n.getFieldWorkitemId()) && U1.equals(n.getFieldHandlerId())));
        assertEquals(1, insertedWorkitems.size());
        SysProcessExampleWorkitem created = insertedWorkitems.get(0);
        assertEquals(U2, created.getFieldHandlerId());
        assertEquals(WI_U1, created.getFieldFromWorkitemId());
        assertEquals(WORKITEM_STATUS_PROCESSING, created.getFieldStatus());
    }

    @Test
    @DisplayName("[矩阵: handler_transfer_retract] 转办发起人 × PROCESSING → 收回成功：被转办工作项 ABANDON + 为发起人生成回链工作项 + 留痕")
    void handlerTransferRetractLegalPath() {
        node3Processing();
        loginAs(Identity.TRANSFER_INITIATOR);
        SysProcessOperateRequest request = validRequest(OPERATE_TYPE_HANDLER_TRANSFER_RETRACT);
        request.setWorkitemId(WI_U3);

        SysProcessExample result = service.operate(request);

        assertEquals(EXAMPLE_STATUS_PROCESSING, result.getFieldStatus());
        assertEquals(WORKITEM_STATUS_ABANDON, workitemDao.selectById(WI_TRANS).getFieldStatus());
        verify(auditNoteDao).insert(argThat(n ->
                AUDIT_TYPE_HANDLER_TRANSFER_RETRACT.equals(n.getFieldHandleType())
                        && WI_U3.equals(n.getFieldWorkitemId()) && U3.equals(n.getFieldHandlerId())));
        assertEquals(1, insertedWorkitems.size());
        SysProcessExampleWorkitem created = insertedWorkitems.get(0);
        assertEquals(U3, created.getFieldHandlerId());
        assertEquals(WI_TRANS, created.getFieldFromWorkitemId());
        assertEquals(WORKITEM_STATUS_PROCESSING, created.getFieldStatus());
    }

    @Test
    @DisplayName("[矩阵: processed_retract] 已处理人 × PROCESSING → 收回成功：FINISH 工作项重回 PROCESSING + 留痕 PROCESSED_RETRACT")
    void processedRetractLegalPath() {
        node3Processing();
        loginAs(Identity.PROCESSED);
        SysProcessOperateRequest request = validRequest(OPERATE_TYPE_PROCESSED_RETRACT);
        request.setWorkitemId(WI_U2);

        SysProcessExample result = service.operate(request);

        assertEquals(EXAMPLE_STATUS_PROCESSING, result.getFieldStatus());
        assertEquals(WORKITEM_STATUS_PROCESSING, workitemDao.selectById(WI_U2).getFieldStatus());
        verify(auditNoteDao).insert(argThat(n -> AUDIT_TYPE_PROCESSED_RETRACT.equals(n.getFieldHandleType())
                && WI_U2.equals(n.getFieldWorkitemId()) && U2.equals(n.getFieldHandlerId())));
    }

    @Test
    @DisplayName("[矩阵: processed_expedite] 已处理人 × PROCESSING → 催办成功：留痕 PROCESSED_EXPEDITE + READ 待阅发在途处理人（排除发起人）")
    void processedExpediteLegalPath() {
        node3Processing();
        loginAs(Identity.PROCESSED);
        SysProcessOperateRequest request = validRequest(OPERATE_TYPE_PROCESSED_EXPEDITE);
        request.setWorkitemId(WI_U2);

        SysProcessExample result = service.operate(request);

        assertEquals(EXAMPLE_STATUS_PROCESSING, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n -> AUDIT_TYPE_PROCESSED_EXPEDITE.equals(n.getFieldHandleType())
                && WI_U2.equals(n.getFieldWorkitemId()) && U2.equals(n.getFieldHandlerId())));
        verify(notifyTodoService).send(argThat(todo ->
                        SysNotifyType.READ.getValue().equals(todo.getFieldNotifyType())),
                argThat(ids -> ids.size() == 1 && ids.get(0).equals(U1)));
    }

    @Test
    @DisplayName("[矩阵: admin_abandon] 流程管理员 × PROCESSING → 废弃成功：PROCESSING→ABANDON + ADMIN_ABANDON 留痕")
    void adminAbandonLegalPath() {
        node3Processing();
        loginAs(Identity.PROCESS_ADMIN);

        SysProcessExample result = service.operate(validRequest(OPERATE_TYPE_ADMIN_ABANDON));

        assertEquals(EXAMPLE_STATUS_ABANDON, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n -> AUDIT_TYPE_ADMIN_ABANDON.equals(n.getFieldHandleType())
                && ADMIN_ID.equals(n.getFieldHandlerId())));
    }

    @Test
    @DisplayName("[矩阵: admin_abandon] 流程管理员 × RETURN/DRAFT/PAUSE → 均废弃成功（合法状态全集回归）")
    void adminAbandonLegalPathOtherStatuses() {
        loginAs(Identity.PROCESS_ADMIN);
        List<String> legalStatuses = LEGAL_STATUSES.get(OPERATE_TYPE_ADMIN_ABANDON);
        for (String status : List.of(EXAMPLE_STATUS_RETURN, EXAMPLE_STATUS_DRAFT, EXAMPLE_STATUS_PAUSE)) {
            assertTrue(legalStatuses.contains(status));
            example.setFieldStatus(status);

            SysProcessExample result = service.operate(validRequest(OPERATE_TYPE_ADMIN_ABANDON));

            assertEquals(EXAMPLE_STATUS_ABANDON, result.getFieldStatus(), "status=" + status);
        }
        verify(auditNoteDao, times(3)).insert(argThat(n ->
                AUDIT_TYPE_ADMIN_ABANDON.equals(n.getFieldHandleType())));
    }

    @Test
    @DisplayName("[矩阵: admin_jump] 流程管理员 × PROCESSING → 跳转成功：ADMIN_JUMP 留痕 + 目标审批节点重建工作项")
    void adminJumpLegalPath() {
        node3Processing();
        SysProcessExampleWorkitem active = workitemDao.selectById(WI_U1);
        when(workitemDao.selectActiveByExample(EXAMPLE_ID)).thenReturn(List.of(active));
        loginAs(Identity.PROCESS_ADMIN);
        SysProcessOperateRequest request = validRequest(OPERATE_TYPE_ADMIN_JUMP);
        request.setTargetNodeId(NODE4_ID);

        SysProcessExample result = service.operate(request);

        assertEquals(EXAMPLE_STATUS_PROCESSING, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n -> AUDIT_TYPE_ADMIN_JUMP.equals(n.getFieldHandleType())
                && ADMIN_ID.equals(n.getFieldHandlerId())));
        verify(workitemDao, times(2)).insert(argThat(w -> NODE4_ID.equals(w.getFieldNodeId())));
    }

    @Test
    @DisplayName("[矩阵: admin_jump] 流程管理员 × RETURN → 跳转成功（第二个合法状态回归）")
    void adminJumpLegalPathFromReturn() {
        // RETURN 状态跳转：状态门禁放行后按目标节点重新进入流转（在途节点由 markCurrentNodeWaiting 收敛）
        example.setFieldStatus(EXAMPLE_STATUS_RETURN);
        loginAs(Identity.PROCESS_ADMIN);
        SysProcessOperateRequest request = validRequest(OPERATE_TYPE_ADMIN_JUMP);
        request.setTargetNodeId(NODE4_ID);

        SysProcessExample result = service.operate(request);

        assertEquals(EXAMPLE_STATUS_PROCESSING, result.getFieldStatus());
        verify(auditNoteDao).insert(argThat(n -> AUDIT_TYPE_ADMIN_JUMP.equals(n.getFieldHandleType())));
        verify(workitemDao, times(2)).insert(argThat(w -> NODE4_ID.equals(w.getFieldNodeId())));
    }

    @Test
    @DisplayName("[矩阵: admin_change_handler] 流程管理员 × PROCESSING → 换人成功：处理人替换 + ADMIN_CHANGE_HANDLER 留痕 + 状态不变")
    void adminChangeHandlerLegalPath() {
        node3Processing();
        loginAs(Identity.PROCESS_ADMIN);
        SysProcessOperateRequest request = validRequest(OPERATE_TYPE_ADMIN_CHANGE_HANDLER);
        request.setWorkitemId(WI_U1);
        request.setChangeToId(U2);

        SysProcessExample result = service.operate(request);

        assertEquals(EXAMPLE_STATUS_PROCESSING, result.getFieldStatus());
        assertEquals(U2, workitemDao.selectById(WI_U1).getFieldHandlerId());
        assertEquals(U2, workitemDao.selectById(WI_U1).getFieldExpectHandlerId());
        verify(auditNoteDao).insert(argThat(n ->
                AUDIT_TYPE_ADMIN_CHANGE_HANDLER.equals(n.getFieldHandleType())
                        && WI_U1.equals(n.getFieldWorkitemId()) && ADMIN_ID.equals(n.getFieldHandlerId())));
    }

    /* ==================== 越权身份格（验收红线：全部 AuthorizationException + FORBIDDEN + CS_PROCESS_OPERATE_FORBIDDEN） ==================== */

    @Test
    @DisplayName("[矩阵: 越权身份格全集] 14 类操作 × 非法身份 → AuthorizationException(FORBIDDEN, CS_PROCESS_OPERATE_FORBIDDEN 前缀)，且不留痕")
    void forbiddenIdentityCellsForAllOperations() {
        for (String operateType : ALL_OPERATIONS) {
            example.setFieldStatus(LEGAL_STATUSES.get(operateType).get(0));
            node3Processing();
            for (Identity identity : FORBIDDEN_IDENTITIES.get(operateType)) {
                loginAs(identity);
                SysProcessOperateRequest request = validRequest(operateType);

                AuthorizationException error = assertThrows(AuthorizationException.class,
                        () -> service.operate(request),
                        "矩阵格 " + operateType + " × " + identity + " 应拒绝");
                assertEquals("FORBIDDEN", error.getCode(),
                        "矩阵格 " + operateType + " × " + identity + " code 应为 FORBIDDEN");
                assertTrue(error.getMessage().contains(CS_PROCESS_OPERATE_FORBIDDEN),
                        "矩阵格 " + operateType + " × " + identity + " message 应含 CS_PROCESS_OPERATE_FORBIDDEN 前缀: "
                                + error.getMessage());
            }
        }
        // 越权格全程零留痕（验收红线：拒绝路径无任何副作用）
        verify(auditNoteDao, never()).insert(any(SysProcessAuditNote.class));
        verify(notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
    }

    /* ==================== 状态冲突格（合法身份 × 非法状态 → CS_PROCESS_OPERATE_STATE_CONFLICT 前缀） ==================== */

    @Test
    @DisplayName("[矩阵: 状态冲突格全集] 14 类操作 × 非法状态（合法身份 + 全参数） → CS_PROCESS_OPERATE_STATE_CONFLICT 前缀，且不留痕")
    void stateConflictCellsForAllOperations() {
        for (String operateType : ALL_OPERATIONS) {
            loginAs(LEGAL_IDENTITY.get(operateType));
            List<String> legalStatuses = LEGAL_STATUSES.get(operateType);
            for (String status : ALL_STATUSES) {
                if (legalStatuses.contains(status)) {
                    continue;
                }
                example.setFieldStatus(status);

                IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                        () -> service.operate(validRequest(operateType)),
                        "矩阵格 " + operateType + " × 状态 " + status + " 应拒绝");
                assertTrue(error.getMessage().startsWith(CS_PROCESS_OPERATE_STATE_CONFLICT),
                        "矩阵格 " + operateType + " × 状态 " + status + " 应为 CS_PROCESS_OPERATE_STATE_CONFLICT 前缀: "
                                + error.getMessage());
            }
        }
        // 状态冲突格全程零留痕
        verify(auditNoteDao, never()).insert(any(SysProcessAuditNote.class));
    }

    /* ==================== 参数缺失格（合法身份 × 合法状态 × 缺必填参数 → CS_PROCESS_OPERATE_PARAM_MISSING 前缀） ==================== */

    @Test
    @DisplayName("[矩阵: 参数缺失格全集] 14 类操作逐必填参数缺失 → CS_PROCESS_OPERATE_PARAM_MISSING 前缀 + 参数名提示，且不留痕")
    void paramMissingCellsForAllOperations() {
        // exampleId / operateType 为全类型公共必填（drafter_submit / drafter_retract 无其他必填参数）
        SysProcessOperateRequest missingExample = validRequest(OPERATE_TYPE_DRAFTER_SUBMIT);
        missingExample.setExampleId(null);
        IllegalArgumentException exampleError = assertThrows(IllegalArgumentException.class,
                () -> service.operate(missingExample));
        assertTrue(exampleError.getMessage().startsWith(CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(exampleError.getMessage().contains("exampleId"));

        SysProcessOperateRequest missingType = validRequest(null);
        IllegalArgumentException typeError = assertThrows(IllegalArgumentException.class,
                () -> service.operate(missingType));
        assertTrue(typeError.getMessage().startsWith(CS_PROCESS_OPERATE_PARAM_MISSING));
        assertTrue(typeError.getMessage().contains("operateType"));

        for (String operateType : ALL_OPERATIONS) {
            example.setFieldStatus(LEGAL_STATUSES.get(operateType).get(0));
            node3Processing();
            loginAs(LEGAL_IDENTITY.get(operateType));
            for (String missingParam : REQUIRED_PARAMS.get(operateType)) {
                SysProcessOperateRequest request = requestMissingParam(operateType, missingParam);

                IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                        () -> service.operate(request),
                        "矩阵格 " + operateType + " 缺参数 " + missingParam + " 应拒绝");
                assertTrue(error.getMessage().startsWith(CS_PROCESS_OPERATE_PARAM_MISSING),
                        "矩阵格 " + operateType + " 缺参数 " + missingParam + " 应为 CS_PROCESS_OPERATE_PARAM_MISSING 前缀: "
                                + error.getMessage());
                assertTrue(error.getMessage().contains(missingParam),
                        "矩阵格 " + operateType + " 缺参数 " + missingParam + " 提示应含参数名: " + error.getMessage());
            }
        }
        // 参数缺失格全程零留痕
        verify(auditNoteDao, never()).insert(any(SysProcessAuditNote.class));
        verify(notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
    }

    /* ==================== 未登录格（接口层由 resolver UNAUTHORIZED 前置，引擎内为防御性兜底） ==================== */

    @Test
    @DisplayName("[矩阵: 未登录格全集] 14 类操作 × 未登录 → 非管理员操作 IllegalStateException；管理员操作 AuthorizationException(FORBIDDEN)")
    void anonymousCellsForAllOperations() {
        for (String operateType : ALL_OPERATIONS) {
            example.setFieldStatus(LEGAL_STATUSES.get(operateType).get(0));
            node3Processing();
            loginAs(Identity.ANONYMOUS);

            if (Identity.PROCESS_ADMIN.equals(LEGAL_IDENTITY.get(operateType))) {
                // 流程管理员身份类操作：requireProcessAdmin 兜底 FORBIDDEN（message 含 CS_PROCESS_OPERATE_FORBIDDEN 前缀）
                AuthorizationException error = assertThrows(AuthorizationException.class,
                        () -> service.operate(validRequest(operateType)),
                        "矩阵格 " + operateType + " × 未登录 应拒绝");
                assertEquals("FORBIDDEN", error.getCode());
                assertTrue(error.getMessage().contains(CS_PROCESS_OPERATE_FORBIDDEN),
                        "矩阵格 " + operateType + " × 未登录 message 应含 CS_PROCESS_OPERATE_FORBIDDEN: "
                                + error.getMessage());
            } else {
                // 起草人/处理人/已处理人身份类操作：requireCurrentUser 兜底拦截（登录态由接口层 resolver 保证）
                IllegalStateException error = assertThrows(IllegalStateException.class,
                        () -> service.operate(validRequest(operateType)),
                        "矩阵格 " + operateType + " × 未登录 应拦截");
                assertTrue(error.getMessage().contains("登录已过期"),
                        "矩阵格 " + operateType + " × 未登录 应提示登录过期: " + error.getMessage());
            }
        }
        verify(auditNoteDao, never()).insert(any(SysProcessAuditNote.class));
        verify(notifyTodoService, never()).send(any(SysNotifyTodo.class), anyList());
    }

    /* ==================== 未登记操作类型格（矩阵外兜底） ==================== */

    @Test
    @DisplayName("[矩阵: 未登记类型] operateType 不在 14 类取值内 → CS_PROCESS_OPERATE_TYPE_INVALID 前缀")
    void unknownOperateTypeRejected() {
        loginAs(Identity.DRAFTER);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.operate(validRequest("foo_bar")));
        assertTrue(error.getMessage().startsWith(SysProcessConstants.CS_PROCESS_OPERATE_TYPE_INVALID));
        verify(auditNoteDao, never()).insert(any(SysProcessAuditNote.class));
    }
}
