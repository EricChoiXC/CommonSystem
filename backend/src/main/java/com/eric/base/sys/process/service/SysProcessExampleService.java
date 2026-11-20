package com.eric.base.sys.process.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.exception.AuthorizationException;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.setting.service.CommonSettingService;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.utils.UserUtil;
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
import com.eric.base.sys.process.model.SysProcessExampleNodeRuntimeDo;
import com.eric.base.sys.process.model.SysProcessExampleWorkitem;
import com.eric.base.sys.process.model.SysProcessExampleWorkitemConfigDo;
import com.eric.base.sys.process.model.SysProcessTemplate;
import com.eric.base.sys.process.model.SysProcessTemplateLine;
import com.eric.base.sys.process.model.SysProcessTemplateNode;
import com.eric.base.sys.process.model.SysProcessTemplateNodeConfigDo;
import com.eric.base.sys.process.properties.SysProcessConstants;
import com.eric.base.sys.process.properties.SysProcessTemplateProperties;
import com.eric.base.sys.process.request.SysProcessAdminOperateRequest;
import com.eric.base.sys.process.request.SysProcessExampleQuery;
import com.eric.base.sys.process.request.SysProcessOperateRequest;
import com.eric.base.sys.process.resolver.ProcessResolver;
import com.eric.base.sys.process.service.engine.ProcessFlowEngine;
import com.eric.base.sys.process.service.engine.ProcessFlowGraph;
import com.eric.base.sys.process.service.engine.ProcessFlowResult;
import com.eric.base.sys.process.service.engine.ProcessFlowSupport;
import com.eric.base.sys.process.service.engine.ProcessNotifySender;
import com.eric.base.sys.process.response.SysProcessAuditNoteVo;
import com.eric.base.sys.process.response.SysProcessExampleDetailVo;
import com.eric.base.sys.process.response.SysProcessExampleNodeVo;
import com.eric.base.sys.process.response.SysProcessExamplePanelVo;
import com.eric.base.sys.process.response.SysProcessExampleVo;
import com.eric.base.sys.process.response.SysProcessExampleWorkitemVo;
import com.eric.base.sys.process.response.SysProcessNodeOptionVo;
import com.eric.base.sys.process.response.SysProcessPanelOperationVo;
import com.eric.base.sys.process.response.SysProcessTemplateModelVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static com.eric.base.common.properties.CommonProperties.ROLE_ADMIN;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_EXAMPLE_ADMIN_OPERATE;
import static com.eric.base.sys.process.properties.SysProcessConstants.ADMIN_OPERATE_ABANDON;
import static com.eric.base.sys.process.properties.SysProcessConstants.ADMIN_OPERATE_CHANGE_HANDLER;
import static com.eric.base.sys.process.properties.SysProcessConstants.ADMIN_OPERATE_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.ADMIN_OPERATE_JUMP;
import static com.eric.base.sys.process.properties.SysProcessConstants.ADMIN_OPERATE_PAUSE;
import static com.eric.base.sys.process.properties.SysProcessConstants.ADMIN_OPERATE_RESUME;
import static com.eric.base.sys.process.properties.SysProcessConstants.APPROVE_MODE_COUNTERSIGN;
import static com.eric.base.sys.process.properties.SysProcessConstants.APPROVE_MODE_SERIAL;
import static com.eric.base.sys.process.properties.SysProcessConstants.APPROVE_MODE_SINGLE;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_ADMIN_ABANDON;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_ADMIN_CHANGE_HANDLER;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_ADMIN_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_ADMIN_JUMP;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_ADMIN_PAUSE;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_ADMIN_RESUME;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_AUTO_PASS;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_DRAFTER_ABANDON;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_DRAFTER_BACK;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_DRAFTER_EXPEDITE;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_DRAFTER_SUBMIT;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_HANDLER_CC;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_HANDLER_PASS;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_HANDLER_REJECT;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_HANDLER_RETURN;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_HANDLER_TRANSFER;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_HANDLER_TRANSFER_RETRACT;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_PROCESSED_EXPEDITE;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_PROCESSED_RETRACT;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUTO_CONTINUE_SKIP_SYSTEM;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUTO_CONTINUE_SKIP_YES;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUTO_SKIP_SYSTEM;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUTO_SKIP_YES;
import static com.eric.base.sys.process.properties.SysProcessConstants.CS_PROCESS_EXAMPLE_NOT_FOUND;
import static com.eric.base.sys.process.properties.SysProcessConstants.CS_PROCESS_NODE_TARGET_INVALID;
import static com.eric.base.sys.process.properties.SysProcessConstants.CS_PROCESS_OPERATE_FORBIDDEN;
import static com.eric.base.sys.process.properties.SysProcessConstants.CS_PROCESS_OPERATE_PARAM_MISSING;
import static com.eric.base.sys.process.properties.SysProcessConstants.CS_PROCESS_OPERATE_STATE_CONFLICT;
import static com.eric.base.sys.process.properties.SysProcessConstants.CS_PROCESS_OPERATE_TYPE_INVALID;
import static com.eric.base.sys.process.properties.SysProcessConstants.EXAMPLE_STATUS_ABANDON;
import static com.eric.base.sys.process.properties.SysProcessConstants.EXAMPLE_STATUS_DRAFT;
import static com.eric.base.sys.process.properties.SysProcessConstants.EXAMPLE_STATUS_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.EXAMPLE_STATUS_PAUSE;
import static com.eric.base.sys.process.properties.SysProcessConstants.EXAMPLE_STATUS_PROCESSING;
import static com.eric.base.sys.process.properties.SysProcessConstants.EXAMPLE_STATUS_RETURN;
import static com.eric.base.sys.process.properties.SysProcessConstants.HANDLER_SOURCE_FIELD;
import static com.eric.base.sys.process.properties.SysProcessConstants.HANDLER_SOURCE_ORG;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_CONFIG_HANDLER_IDS;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_PASS;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_PROCESSING;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_REJECT;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_STATUS_WAITING;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_APPROVE;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_DRAFT;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_END;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_MANUAL;
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
import static com.eric.base.sys.process.properties.SysProcessConstants.RESUME_MODE_BACK_TO_NODE;
import static com.eric.base.sys.process.properties.SysProcessConstants.RESUME_MODE_RESTART;
import static com.eric.base.sys.process.properties.SysProcessConstants.SETTING_PROCESS_AUTO_CONTINUE_PASS;
import static com.eric.base.sys.process.properties.SysProcessConstants.SETTING_PROCESS_AUTO_HANDLE_PASS;
import static com.eric.base.sys.process.properties.SysProcessConstants.SETTING_PROCESS_AUTO_HANDLE_PASS_DRAFTER;
import static com.eric.base.sys.process.properties.SysProcessConstants.SETTING_PROCESS_FINISH_NOTIFY_HANDLER;
import static com.eric.base.sys.process.properties.SysProcessConstants.SETTING_TYPE_PROCESS;
import static com.eric.base.sys.process.properties.SysProcessConstants.WORKITEM_CONFIG_RESUME_MODE;
import static com.eric.base.sys.process.properties.SysProcessConstants.WORKITEM_STATUS_ABANDON;
import static com.eric.base.sys.process.properties.SysProcessConstants.WORKITEM_STATUS_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.WORKITEM_STATUS_PAUSE;
import static com.eric.base.sys.process.properties.SysProcessConstants.WORKITEM_STATUS_PROCESSING;

/**
 * 流程实例业务服务（流程流转引擎）
 *
 * @author Eric
 * @date 2026/8/23
 * @description 承载「流程实例」页面的后端能力与流程流转核心规则
 *              （依据 docs/设计文档/sys/process/流程核心业务.md「流程实例和流转」）：
 *              <ul>
 *              <li>创建/提交：由实例所属模块负责创建（保存 DRAFT、提交 PROCESSING 并开始流转），
 *                  生成 DRAFT_SUBMIT 审批记录；</li>
 *              <li>起草人收回/废弃：PROCESSING 收回转 DRAFT、DRAFT/RETURN 废弃转 ABANDON；</li>
 *              <li>审批人通过/驳回：单签一人通过即通过节点、会审需全员通过；
 *                  驳回按「节点配置 rejectTargets ∩ 已流经节点」校验目标（空默认起草节点）转 RETURN，
 *                  留痕 HANDLER_REJECT，目标与「重新提交后流转方式」保存到驳回者工作项配置；</li>
 *              <li>审批人转办/转办收回/已审批人收回（v1_7 新增，P17-BE-010）：转办为被转办人生成
 *                  from_workitem_id 关联的新工作项（被转办人视为该序位实际处理人）、转办收回沿
 *                  from_workitem_id 链整链回退到原审批人、节点未流出时已办结工作项可收回到本人；</li>
 *              <li>审批人抄送（v1_7 新增，P17-BE-012）：当前处理人在途抄送其他用户（ccToIds
 *                  地址本多选），逐人发 READ 待阅（标题消费 notify_cc_subject），留痕 HANDLER_CC，
 *                  不改变工作项/节点/流程状态；</li>
 *              <li>起草人/已审批人催办（v1_7 新增，P17-BE-011）：向当前节点全部 PROCESSING 工作项
 *                  处理人发催办待阅（READ，标题消费公共配置 notify_expedite_subject），
 *                  留痕 DRAFTER_EXPEDITE / PROCESSED_EXPEDITE，不改流程/节点/工作项状态；
 *                  抄送与催办的 READ 待阅发送统一经共享组件
 *                  {@link com.eric.base.sys.process.service.engine.ProcessNotifySender} 收口
 *                  （P17-BE-014 通知收口载体）；</li>
 *              <li>审批人解析：按节点「审批来源」解析——组织架构直接取配置审批人，表单字段读取文档实体
 *                  对应字段值解析出组织架构（岗位/用户）作为审批人，结果为空时跳过节点；</li>
 *              <li>自动跳过：按公共配置（auto_handle_pass / auto_handle_pass_drafter / auto_continue_pass）
 *                  与节点配置（autoSkip / autoContinueSkip）跳过上一有效节点的重复审批人/起草人，
 *                  生成办结的工作项与 AUTO_PASS 审批记录，实际处理人为空时跳过节点；</li>
 *              <li>管理员特权操作：跳转/终审通过/作废/暂停/恢复（生成 ADMIN_* 审批记录）；
 *                  更换审批人（v1_7 新增，P17-BE-013）：指定 PROCESSING 工作项换人，
 *                  仅替换处理人与期望处理人（仅本实例有效，不改节点/流程状态，不影响串行序位
 *                  与会签计数），留痕 ADMIN_CHANGE_HANDLER；</li>
 *              <li>通知收口（v1_7，P17-BE-014）：审批待办/被驳回待办/办结待阅的发送统一经
 *                  {@link com.eric.base.sys.process.service.engine.ProcessNotifySender}——驳回动作
 *                  发被驳回待办（起草人，标题消费 notify_refuse_subject）、管理员终审通过后按
 *                  引擎 enterEnd 同口径发办结待阅（起草人待阅始终发送，审批人待阅受
 *                  finish_notify_handler 控制）；</li>
 *              <li>列表/详情/审批面板：流程实例分页查询、实例+节点+工作项+审批记录详情、
 *                  可嵌入审批组件的面板数据；面板按钮集按「节点 operations 配置 ∩ 用户身份 ∩
 *                  状态前置」计算（v1_7 升级为按钮对象数组，P17-BE-013），与 nodeOps
 *                  业务标识并存（语义不变）。</li>
 *              </ul>
 */
@Service
public class SysProcessExampleService {

    private static final Logger log = LoggerFactory.getLogger(SysProcessExampleService.class);

    private final SysProcessExampleDao exampleDao;
    private final SysProcessExampleNodeDao nodeDao;
    private final SysProcessExampleLineDao exampleLineDao;
    private final SysProcessExampleWorkitemDao workitemDao;
    private final SysProcessAuditNoteDao auditNoteDao;
    private final SysProcessTemplateDao templateDao;
    private final SysProcessTemplateNodeDao templateNodeDao;
    private final SysProcessTemplateLineDao templateLineDao;
    private final CommonSettingService settingService;
    private final SysOrgUserMapper userMapper;
    private final ProcessNotifySender notifySender;
    private final ProcessResolver resolver;
    private final SysProcessDocumentFieldService documentFieldService;
    private final ObjectMapper objectMapper;
    private final ProcessFlowEngine flowEngine;
    private final ProcessFlowSupport flowSupport;

    public SysProcessExampleService(SysProcessExampleDao exampleDao,
                                    SysProcessExampleNodeDao nodeDao,
                                    SysProcessExampleLineDao exampleLineDao,
                                    SysProcessExampleWorkitemDao workitemDao,
                                    SysProcessAuditNoteDao auditNoteDao,
                                    SysProcessTemplateDao templateDao,
                                    SysProcessTemplateNodeDao templateNodeDao,
                                    SysProcessTemplateLineDao templateLineDao,
                                    CommonSettingService settingService,
                                    SysOrgUserMapper userMapper,
                                    ProcessNotifySender notifySender,
                                    ProcessResolver resolver,
                                    SysProcessDocumentFieldService documentFieldService,
                                    ObjectMapper objectMapper,
                                    ProcessFlowEngine flowEngine,
                                    ProcessFlowSupport flowSupport) {
        this.exampleDao = exampleDao;
        this.nodeDao = nodeDao;
        this.exampleLineDao = exampleLineDao;
        this.workitemDao = workitemDao;
        this.auditNoteDao = auditNoteDao;
        this.templateDao = templateDao;
        this.templateNodeDao = templateNodeDao;
        this.templateLineDao = templateLineDao;
        this.settingService = settingService;
        this.userMapper = userMapper;
        this.notifySender = notifySender;
        this.resolver = resolver;
        this.documentFieldService = documentFieldService;
        this.objectMapper = objectMapper;
        this.flowEngine = flowEngine;
        this.flowSupport = flowSupport;
    }

    /* ==================== 流程创建与提交（由实例所属模块调用） ==================== */

    /**
     * 创建流程实例（保存操作）：从模板快照创建实例、实例节点与实例连线，
     * 流程状态 DRAFT，当前节点为草稿节点。流程编号为空时自动生成。
     * <p>节点与连线均<b>全量快照复制</b>（含节点配置与连线分支配置），随后流转只读实例快照，
     * 模板后续变更不影响本实例（依据 docs/v1_7-流程管理/功能实现方案-改造分析与方案设计.md
     * §3.2「实例快照」与 docs/设计文档/sys/process/流程核心业务.md「实例快照」）。</p>
     *
     * @param templateId 模板 id（必须存在且启用）
     * @param modelId    所属模块（业务文档）id，可为空
     * @param code       流程编号，为空时自动生成
     * @return 流程实例基本字段
     */
    @Transactional
    public SysProcessExampleVo createDraft(String templateId, String modelId, String code) {
        if (StringUtil.isEmpty(templateId)) {
            throw new IllegalArgumentException("templateId 不能为空");
        }
        SysProcessTemplate template = templateDao.selectById(templateId.trim());
        if (null == template) {
            throw new IllegalArgumentException("流程模板不存在或已删除");
        }
        if (!Boolean.TRUE.equals(template.getFieldEnable())) {
            throw new IllegalArgumentException("流程模板已停用，无法发起流程");
        }
        SysOrgUser current = requireCurrentUser();
        SysProcessExample example = new SysProcessExample();
        example.initFieldId();
        example.setFieldTemplateId(template.getFieldId());
        example.setFieldTemplateVersion(template.getFieldVersion());
        example.setFieldModelId(StringUtil.isEmpty(modelId) ? null : modelId.trim());
        example.setFieldModelName(template.getFieldModelName());
        example.setFieldCode(StringUtil.isEmpty(code) ? generateCode() : code.trim());
        example.setFieldCreatorId(current.getFieldId());
        example.setFieldCreateTime(new Date());
        example.setFieldStatus(EXAMPLE_STATUS_DRAFT);
        exampleDao.insert(example);

        List<SysProcessTemplateNode> templateNodes = templateNodeDao.selectListByTemplateId(template.getFieldId());
        if (null == templateNodes || templateNodes.isEmpty()) {
            throw new IllegalStateException("流程模板缺少节点定义");
        }
        List<SysProcessExampleNode> nodes = new ArrayList<>();
        for (SysProcessTemplateNode templateNode : templateNodes) {
            SysProcessExampleNode node = new SysProcessExampleNode();
            node.initFieldId();
            node.setFieldExampleId(example.getFieldId());
            node.setFieldNodeNo(templateNode.getFieldNo());
            node.setFieldNodeName(templateNode.getFieldNodeName());
            node.setFieldNodeType(templateNode.getFieldNodeType());
            node.setFieldNodeConfig(templateNode.getFieldNodeConfig());
            node.setFieldNodeKey(ProcessFlowGraph.nodeKey(templateNode));
            // 开始节点与草稿节点：开始节点已走过（FINISH），草稿节点为当前节点（PROCESSING），其余等待
            if (SysProcessConstants.NODE_TYPE_START.equals(templateNode.getFieldNodeType())) {
                node.setFieldStatus(NODE_STATUS_FINISH);
            } else if (NODE_TYPE_DRAFT.equals(templateNode.getFieldNodeType())) {
                node.setFieldStatus(NODE_STATUS_PROCESSING);
            } else {
                node.setFieldStatus(NODE_STATUS_WAITING);
            }
            nodes.add(node);
        }
        nodeDao.insertBatch(example.getFieldId(), nodes);

        // 连线快照：与节点快照同事务全量复制（模板后续变更不影响本实例流转）
        List<SysProcessTemplateLine> templateLines = templateLineDao.selectListByTemplateId(template.getFieldId());
        if (null == templateLines || templateLines.isEmpty()) {
            throw new IllegalStateException("流程模板缺少连线定义");
        }
        List<SysProcessExampleLine> lines = new ArrayList<>();
        for (SysProcessTemplateLine templateLine : templateLines) {
            SysProcessExampleLine line = new SysProcessExampleLine();
            line.initFieldId();
            line.setFieldExampleId(example.getFieldId());
            line.setFieldFromKey(templateLine.getFieldFromKey());
            line.setFieldToKey(templateLine.getFieldToKey());
            line.setFieldLineType(templateLine.getFieldLineType());
            line.setFieldBranchOrder(templateLine.getFieldBranchOrder());
            line.setFieldBranchName(templateLine.getFieldBranchName());
            line.setFieldCondition(templateLine.getFieldCondition());
            line.setFieldIsDefault(templateLine.getFieldIsDefault());
            lines.add(line);
        }
        exampleLineDao.insertBatch(example.getFieldId(), lines);
        log.info("process example created: id={}, template={}, model={}, code={}, nodes={}, lines={}",
                example.getFieldId(), template.getFieldId(), template.getFieldModelName(),
                example.getFieldCode(), nodes.size(), lines.size());
        return toExampleVo(example);
    }

    /**
     * 提交流程（提交操作）：仅创建人可在 DRAFT / RETURN 状态提交；
     * 生成 DRAFTER_SUBMIT 审批记录，草稿节点完成，进入流转。
     * RETURN 状态重新提交时按驳回工作项保存的「重新提交后流转方式」决定重新流转或转回本节点。
     *
     * @return 提交后的流程实例详情
     */
    @Transactional
    public SysProcessExampleDetailVo submit(String exampleId, String content) {
        return submit(exampleId, content, null);
    }

    /**
     * 提交流程（提交操作，可携带人工决策流向）：语义同 {@link #submit(String, String)}，
     * flowChoice 可选（D6 人工决策流向，drafter_submit）：起草节点为某人工决策节点
     * flowDecisions 的决策来源时，可指定该决策节点流向（须为其出边目标之一，否则
     * {@code CS_PROCESS_NODE_TARGET_INVALID}）；落库到起草节点运行态（后提交覆盖先提交）。
     *
     * @param exampleId 流程实例 id
     * @param content   审批意见（可空）
     * @param flowChoice 人工决策流向（目标节点 key，可空）
     * @return 提交后的流程实例详情
     */
    @Transactional
    public SysProcessExampleDetailVo submit(String exampleId, String content, String flowChoice) {
        SysProcessExample example = requireExample(exampleId);
        requireDrafter(example);
        if (!EXAMPLE_STATUS_DRAFT.equals(example.getFieldStatus())
                && !EXAMPLE_STATUS_RETURN.equals(example.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：仅草稿或驳回状态的流程可提交");
        }
        SysProcessExampleNode draftNode = nodeDao.selectByExampleAndType(example.getFieldId(), NODE_TYPE_DRAFT);
        if (StringUtil.isNotEmpty(flowChoice)) {
            // 人工决策流向校验（D6 提交侧）：当前节点=起草节点，须为某决策节点的流向决策来源且为目标出边之一
            requireFlowChoiceValid(example.getFieldId(),
                    null == draftNode ? SysProcessConstants.FIXED_NODE_KEY_DRAFT : ProcessFlowGraph.nodeKey(draftNode),
                    flowChoice);
        }
        SysOrgUser current = requireCurrentUser();
        addAudit(example.getFieldId(), null, current.getFieldId(),
                AUDIT_TYPE_DRAFTER_SUBMIT, trimToNull(content));
        // 草稿节点完成（图遍历下由引擎按连线进入后继节点）
        if (null != draftNode && !NODE_STATUS_FINISH.equals(draftNode.getFieldStatus())) {
            draftNode.setFieldStatus(NODE_STATUS_FINISH);
            nodeDao.update(draftNode);
        }
        if (StringUtil.isNotEmpty(flowChoice) && null != draftNode) {
            // flowChoice 落库到起草节点运行态（后提交覆盖先提交，引擎 MANUAL 读取）
            SysProcessExampleNodeRuntimeDo runtime = flowSupport.parseNodeRuntime(draftNode.getFieldRuntime());
            if (null == runtime) {
                runtime = new SysProcessExampleNodeRuntimeDo();
            }
            runtime.setFlowChoice(flowChoice.trim());
            flowSupport.saveNodeRuntime(draftNode, runtime);
        }
        example.setFieldStatus(EXAMPLE_STATUS_PROCESSING);
        exampleDao.update(example);
        // 流转起点：驳回后按「重新提交后流转方式」选择，否则草稿节点的出边目标
        String entryKey = flowEngine.resolveResubmitEntryKey(example);
        flowEngine.enterFlow(example, entryKey);
        log.info("process example submitted: id={}, status={}", example.getFieldId(), example.getFieldStatus());
        return load(example.getFieldId());
    }

    /* ==================== 起草人操作 ==================== */

    /**
     * 起草人收回：流程 PROCESSING 时创建人可收回；流程转 DRAFT，节点转回起草节点，
     * 当前节点工作项废弃，生成 DRAFTER_BACK 审批记录。
     */
    @Transactional
    public void back(String exampleId, String content) {
        SysProcessExample example = requireExample(exampleId);
        requireDrafter(example);
        if (!EXAMPLE_STATUS_PROCESSING.equals(example.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：仅审批中的流程可收回");
        }
        SysOrgUser current = requireCurrentUser();
        example.setFieldStatus(EXAMPLE_STATUS_DRAFT);
        exampleDao.update(example);
        addAudit(example.getFieldId(), null, current.getFieldId(),
                AUDIT_TYPE_DRAFTER_BACK, trimToNull(content));

        abandonActiveWorkitems(example.getFieldId());
        SysProcessExampleNode draftNode = nodeDao.selectByExampleAndType(example.getFieldId(), NODE_TYPE_DRAFT);
        if (null != draftNode) {
            draftNode.setFieldStatus(NODE_STATUS_PROCESSING);
            nodeDao.update(draftNode);
        }
        flowSupport.markCurrentNodeWaiting(example.getFieldId());
        log.info("process example backed by drafter: id={}", example.getFieldId());
    }

    /**
     * 起草人废弃：流程 DRAFT / RETURN 时创建人可废弃；流程转 ABANDON，节点转入结束节点，
     * 生成 DRAFTER_ABANDON 审批记录。
     */
    @Transactional
    public void abandonByDrafter(String exampleId, String content) {
        SysProcessExample example = requireExample(exampleId);
        requireDrafter(example);
        if (!EXAMPLE_STATUS_DRAFT.equals(example.getFieldStatus())
                && !EXAMPLE_STATUS_RETURN.equals(example.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：仅草稿或驳回状态的流程可废弃");
        }
        SysOrgUser current = requireCurrentUser();
        abandonExample(example, current.getFieldId(), AUDIT_TYPE_DRAFTER_ABANDON, content);
        log.info("process example abandoned by drafter: id={}", example.getFieldId());
    }

    /* ==================== 审批人操作 ==================== */

    /**
     * 审批人通过：流程 PROCESSING 时当前工作项处理人可通过；生成 HANDLER_PASS 审批记录。
     * 单签一人通过即通过节点（其他工作项转 FINISH）；会审需全部工作项通过后才通过节点并进入下一节点。
     */
    @Transactional
    public void pass(String exampleId, String workitemId, String content) {
        pass(exampleId, workitemId, content, null);
    }

    /**
     * 审批人通过（可携带人工决策流向）：语义同 {@link #pass(String, String, String)}，
     * flowChoice 可选（D6 人工决策流向，handler_pass）：被通过节点为某人工决策节点
     * flowDecisions 的决策来源时，可指定该决策节点流向（须为其出边目标之一，否则
     * {@code CS_PROCESS_NODE_TARGET_INVALID}）；落库到本工作项 field_config
     * （保留既有 resumeMode / flowBranchKey，后提交覆盖先提交）。
     *
     * @param exampleId  流程实例 id
     * @param workitemId 工作项 id（必填）
     * @param content    审批意见
     * @param flowChoice 人工决策流向（目标节点 key，可空）
     */
    @Transactional
    public void pass(String exampleId, String workitemId, String content, String flowChoice) {
        SysProcessExample example = requireExample(exampleId);
        if (!EXAMPLE_STATUS_PROCESSING.equals(example.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：仅审批中的流程可执行审批操作");
        }
        SysProcessExampleWorkitem workitem = requireProcessingWorkitem(example.getFieldId(), workitemId);
        SysOrgUser current = requireCurrentUser();
        SysProcessExampleNode node = requireNode(workitem.getFieldNodeId());
        if (StringUtil.isNotEmpty(flowChoice)) {
            // 人工决策流向校验（D6）：当前节点须为某决策节点的流向决策来源且为目标出边之一
            requireFlowChoiceValid(example.getFieldId(), ProcessFlowGraph.nodeKey(node), flowChoice);
        }
        finishWorkitem(workitem);
        addAudit(example.getFieldId(), workitem.getFieldId(), current.getFieldId(),
                AUDIT_TYPE_HANDLER_PASS, trimToNull(content));
        if (StringUtil.isNotEmpty(flowChoice)) {
            // flowChoice 落库到本工作项配置（先读回再写，保留 resumeMode / flowBranchKey）
            SysProcessExampleWorkitemConfigDo config = flowSupport.parseWorkitemConfig(workitem.getFieldConfig());
            if (null == config) {
                config = new SysProcessExampleWorkitemConfigDo();
            }
            config.setFlowChoice(flowChoice.trim());
            workitem.setFieldConfig(flowSupport.writeJson(config));
            workitemDao.update(workitem);
        }
        if (isNodePassed(node, workitem.getFieldId())) {
            // 节点标记完成（通过语义），随后由引擎沿出边继续流转
            node.setFieldStatus(NODE_STATUS_FINISH);
            nodeDao.update(node);
            finishRemainingWorkitems(node.getFieldId(), workitem.getFieldId());
            log.info("process example node passed: example={}, node={}", example.getFieldId(), node.getFieldNodeName());
            flowEngine.pass(example, ProcessFlowGraph.nodeKey(node), node.getFieldNodeName(), workitem.getFieldId());
        } else if (isSerialApproveNode(node)) {
            // 串行（P17-BE-010 补齐 Service 通道串行推进）：不在此判定通过、不置节点 FINISH，
            // 交引擎推进下一序位（advanceApprove 生成下一顺序人工作项；队列走完时由引擎置节点 FINISH 并沿出边流转）
            if (NODE_STATUS_FINISH.equals(node.getFieldStatus())) {
                // 串行队列已走完（节点已流出）：在途工作项为历史残留（如收回后未重新处理），不可再审批
                throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：节点已办结，工作项不可再审批");
            }
            flowEngine.pass(example, ProcessFlowGraph.nodeKey(node), node.getFieldNodeName(), workitem.getFieldId());
        }
        log.info("process example workitem passed: example={}, workitem={}", example.getFieldId(), workitem.getFieldId());
    }

    /**
     * 审批人驳回（旧入口，向后兼容）：语义同 {@link #reject(String, String, String, String, String)}
     * 且驳回目标按起草节点处理（rejectTargetKey 传 null，不做目标校验），业务模块旧调用语义不变。
     */
    @Transactional
    public void reject(String exampleId, String workitemId, String content, String resumeMode) {
        reject(exampleId, workitemId, content, null, resumeMode);
    }

    /**
     * 审批人驳回（全参，P17-BE-009 驳回目标改造）：流程 PROCESSING 时当前工作项处理人可驳回；
     * 生成 {@link SysProcessConstants#AUDIT_TYPE_HANDLER_REJECT HANDLER_REJECT} 审批记录
     * （{@code HANDLER_RETURN} 保留兼容历史数据），被驳回节点其他工作项转 FINISH，流程转 RETURN。
     * <ul>
     * <li><b>驳回目标校验</b>：rejectTargetKey 非空时，允许目标 = 驳回节点配置 {@code rejectTargets}
     *     ∩ 已流经节点（配置为空或交集为空时默认仅起草节点；已流经判定：起草节点恒可，
     *     审批节点 FINISH / REJECT 算已流经，PASS / WAITING / PROCESSING 不算）；
     *     目标不在允许集合或不是本实例节点时抛 {@code CS_PROCESS_NODE_TARGET_INVALID}；</li>
     * <li><b>驳回目标=起草节点</b>（rejectTargetKey 为空按起草节点处理，旧入口语义不变）：
     *     起草节点置 PROCESSING（现状行为不变）；</li>
     * <li><b>驳回目标=非起草节点（上游审批节点）</b>：目标节点状态不改动（保持 FINISH）、
     *     起草节点不置 PROCESSING，流程仍转 RETURN；由起草人重新提交时按 resumeMode
     *     进入目标节点（引擎 {@code resolveResubmitEntryKey} 解析入口）。</li>
     * </ul>
     * 驳回者工作项 field_config 写入 {resumeMode, rejectTargetKey}（经 ProcessFlowSupport 先读回再写，
     * 保留既有 flowChoice / flowBranchKey）。
     *
     * @param exampleId       流程实例 id
     * @param workitemId      工作项 id（必填）
     * @param content         审批意见
     * @param rejectTargetKey 驳回目标节点 key（可空：按起草节点目标处理，不做校验）
     * @param resumeMode      重新提交后流转方式（restart=重新流转 / backToNode=转回驳回目标，必填）
     */
    @Transactional
    public void reject(String exampleId, String workitemId, String content,
                       String rejectTargetKey, String resumeMode) {
        if (StringUtil.isEmpty(resumeMode)
                || (!RESUME_MODE_RESTART.equals(resumeMode) && !RESUME_MODE_BACK_TO_NODE.equals(resumeMode))) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_PARAM_MISSING
                    + "：请选择重新提交后流转方式（重新流转或转回本节点）");
        }
        SysProcessExample example = requireExample(exampleId);
        if (!EXAMPLE_STATUS_PROCESSING.equals(example.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：仅审批中的流程可执行审批操作");
        }
        SysProcessExampleWorkitem workitem = requireProcessingWorkitem(example.getFieldId(), workitemId);
        SysProcessExampleNode node = requireNode(workitem.getFieldNodeId());
        // 驳回目标校验（旧入口 rejectTargetKey=null 按起草节点目标处理，不做校验）
        String targetKey = trimToNull(rejectTargetKey);
        SysProcessExampleNode targetNode = null == targetKey ? null
                : requireRejectTarget(example, node, targetKey);
        SysOrgUser current = requireCurrentUser();
        workitem.setFieldStatus(WORKITEM_STATUS_FINISH);
        workitem.setFieldFinishTime(new Date());
        // {resumeMode, rejectTargetKey} 写入驳回者工作项配置（先读回再写，不清掉 flowChoice / flowBranchKey）
        SysProcessExampleWorkitemConfigDo config = flowSupport.parseWorkitemConfig(workitem.getFieldConfig());
        if (null == config) {
            config = new SysProcessExampleWorkitemConfigDo();
        }
        config.setResumeMode(resumeMode);
        config.setRejectTargetKey(targetKey);
        workitem.setFieldConfig(flowSupport.writeJson(config));
        workitemDao.update(workitem);
        // 留痕：v1_7 起新操作统一 HANDLER_REJECT（HANDLER_RETURN 保留兼容历史数据）
        addAudit(example.getFieldId(), workitem.getFieldId(), current.getFieldId(),
                AUDIT_TYPE_HANDLER_REJECT, trimToNull(content));

        node.setFieldStatus(NODE_STATUS_REJECT);
        nodeDao.update(node);
        // 该节点其他审批人工作项转为 FINISH
        finishRemainingWorkitems(node.getFieldId(), workitem.getFieldId());

        example.setFieldStatus(EXAMPLE_STATUS_RETURN);
        exampleDao.update(example);
        // 起草节点仅驳回目标为起草节点（或旧入口未指定目标）时转回 PROCESSING；非起草目标保持原状态
        if (null == targetNode || NODE_TYPE_DRAFT.equals(targetNode.getFieldNodeType())) {
            SysProcessExampleNode draftNode = nodeDao.selectByExampleAndType(example.getFieldId(), NODE_TYPE_DRAFT);
            if (null != draftNode) {
                draftNode.setFieldStatus(NODE_STATUS_PROCESSING);
                nodeDao.update(draftNode);
            }
        }
        // 被驳回待办（P17-BE-014 通知收口）：流程转 RETURN 后向起草人发送被驳回待办
        // （TODO，标题消费 notify_refuse_subject，占位符 ${fieldHandlerName}=驳回人姓名，
        // 内容=驳回意见）；驳回人与起草人为同一人时不发送
        if (StringUtil.isNotEmpty(example.getFieldCreatorId())
                && !current.getFieldId().equals(example.getFieldCreatorId())) {
            notifySender.sendRefuseTodo(example, current.getFieldName(), trimToNull(content),
                    List.of(example.getFieldCreatorId()));
        }
        log.info("process example rejected: example={}, node={}, rejectTargetKey={}, resumeMode={}",
                example.getFieldId(), node.getFieldNodeName(), targetKey, resumeMode);
    }

    /**
     * 驳回目标校验（P17-BE-009，依据 docs/设计文档/sys/process/流程核心业务.md「驳回与重新提交（D3）」）：
     * 允许目标 = 驳回节点配置 {@code rejectTargets} ∩ 已流经节点（配置为空或交集为空时默认仅起草节点），
     * 允许集合口径见 {@link #resolveAllowedRejectTargetKeys}。
     * 目标键必须是本实例节点键（按 nodeKey 匹配实例节点集合），否则同样拒绝。
     *
     * @param example    流程实例
     * @param rejectNode 驳回者所在节点（rejectTargets 配置来源）
     * @param targetKey  驳回目标节点 key（非空）
     * @return 命中的目标实例节点（非空）
     */
    private SysProcessExampleNode requireRejectTarget(SysProcessExample example, SysProcessExampleNode rejectNode,
                                                      String targetKey) {
        List<SysProcessExampleNode> nodes = nodeDao.selectListByExampleId(example.getFieldId());
        SysProcessExampleNode target = null;
        if (null != nodes) {
            for (SysProcessExampleNode node : nodes) {
                if (ProcessFlowGraph.nodeKey(node).equals(targetKey)) {
                    target = node;
                }
            }
        }
        if (null == target) {
            throw new IllegalArgumentException(CS_PROCESS_NODE_TARGET_INVALID
                    + "：驳回目标节点 " + targetKey + " 不是本流程实例的节点");
        }
        Set<String> allowed = resolveAllowedRejectTargetKeys(example, rejectNode);
        if (!allowed.contains(targetKey)) {
            throw new IllegalArgumentException(CS_PROCESS_NODE_TARGET_INVALID + "：驳回目标「"
                    + (StringUtil.isEmpty(target.getFieldNodeName()) ? targetKey : target.getFieldNodeName())
                    + "」不在允许的驳回目标范围内（允许目标 = 节点配置 rejectTargets ∩ 已流经节点）");
        }
        return target;
    }

    /**
     * 解析允许的驳回目标节点键集合（P17-BE-009 允许目标口径，操作时校验与面板候选共用）：
     * 节点配置 {@code rejectTargets} ∩ 已流经节点（已流经判定见 {@link #isTraversedNode}：
     * 起草节点恒可；审批节点 FINISH / REJECT 算已流经，PASS / WAITING / PROCESSING 不算）；
     * 配置为空或交集为空时默认仅起草节点。
     *
     * @param example    流程实例
     * @param rejectNode 驳回者所在节点（rejectTargets 配置来源）
     * @return 允许的驳回目标节点键集合（非空，保序）
     */
    private Set<String> resolveAllowedRejectTargetKeys(SysProcessExample example, SysProcessExampleNode rejectNode) {
        List<SysProcessExampleNode> nodes = nodeDao.selectListByExampleId(example.getFieldId());
        Set<String> traversedKeys = new LinkedHashSet<>();
        if (null != nodes) {
            for (SysProcessExampleNode node : nodes) {
                if (isTraversedNode(node)) {
                    traversedKeys.add(ProcessFlowGraph.nodeKey(node));
                }
            }
        }
        Set<String> allowed = new LinkedHashSet<>();
        SysProcessTemplateNodeConfigDo config = parseNodeConfig(rejectNode.getFieldNodeConfig());
        List<String> rejectTargets = null == config ? null : config.getRejectTargets();
        if (null != rejectTargets && !rejectTargets.isEmpty()) {
            for (String key : rejectTargets) {
                if (StringUtil.isNotEmpty(key) && traversedKeys.contains(key.trim())) {
                    allowed.add(key.trim());
                }
            }
        }
        if (allowed.isEmpty()) {
            allowed.add(SysProcessConstants.FIXED_NODE_KEY_DRAFT);
        }
        return allowed;
    }

    /** 已流经节点判定（驳回目标候选口径）：起草节点恒可；审批节点 FINISH / REJECT 算已流经，其余不算。 */
    private static boolean isTraversedNode(SysProcessExampleNode node) {
        if (NODE_TYPE_DRAFT.equals(node.getFieldNodeType())) {
            return true;
        }
        return NODE_TYPE_APPROVE.equals(node.getFieldNodeType())
                && (NODE_STATUS_FINISH.equals(node.getFieldStatus())
                || NODE_STATUS_REJECT.equals(node.getFieldStatus()));
    }

    /* ==================== 审批人转办 / 转办收回 / 已审批人收回（P17-BE-010） ==================== */

    /**
     * 审批人转办（handler_transfer，P17-BE-010）：当前处理人将 PROCESSING 工作项转办给一个或多个被转办人。
     * <ul>
     * <li>原工作项转 ABANDON（记完成时间）；按 transferToIds 顺序逐人生成新工作项：处理人/期望处理人
     *     =被转办人（视为该序位实际处理人）、状态 PROCESSING、{@code field_from_workitem_id=原工作项 id}
     *     （链路 A→B 可逐级追溯）、{@code field_config} 继承原工作项配置（保留 resumeMode / flowChoice /
     *     flowBranchKey——并行分支内转办时 flowBranchKey 必须继承，保证 JOIN 汇聚归属不乱）；</li>
     * <li>转办仅替换「人」不重排审批：单签/会签下新工作项视为原序位的工作项（节点通过判定按工作项状态
     *     自然成立）；串行模式转办时本序位即最新工作项（串行「上一人通过后才生成下一人待办」），
     *     被转办人通过后由引擎照常推进下一序位，不涉及后续序位；</li>
     * <li>不影响节点/流程状态（仍 PROCESSING）；留痕 {@link SysProcessConstants#AUDIT_TYPE_HANDLER_TRANSFER}
     *     一条（workitemId=原工作项 id，转办给多人亦只留痕一条）。</li>
     * </ul>
     * 转办目标用户经用户表按 id 校验须存在（允许禁用状态，对齐审批人解析语义），不存在的目标拒绝。
     *
     * @param exampleId     流程实例 id（必填）
     * @param workitemId    原工作项 id（必填，须为当前处理人 PROCESSING 工作项）
     * @param transferToIds 被转办用户 id（';' 分隔或多值，去重保序，必填）
     * @param content       转办意见（必填）
     */
    @Transactional
    public void transfer(String exampleId, String workitemId, String transferToIds, String content) {
        SysProcessExample example = requireExample(exampleId);
        if (!EXAMPLE_STATUS_PROCESSING.equals(example.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：仅审批中的流程可执行转办");
        }
        requireOperateParam(workitemId, "workitemId");
        if (StringUtil.isEmpty(content)) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_PARAM_MISSING + "：content 必填");
        }
        SysProcessExampleWorkitem workitem = requireProcessingWorkitem(example.getFieldId(), workitemId);
        List<String> targets = parseTransferToIds(transferToIds);
        requireUserTargetsExist(targets, "转办目标用户");
        SysOrgUser current = requireCurrentUser();
        Date now = new Date();
        // 原工作项弃置（记完成时间）；新工作项按被转办人顺序逐人生成（config 继承原工作项）
        workitem.setFieldStatus(WORKITEM_STATUS_ABANDON);
        workitem.setFieldFinishTime(now);
        workitemDao.update(workitem);
        for (String targetId : targets) {
            workitemDao.insert(buildTransferWorkitem(example, workitem, targetId, workitem.getFieldId()));
        }
        addAudit(example.getFieldId(), workitem.getFieldId(), current.getFieldId(),
                AUDIT_TYPE_HANDLER_TRANSFER, content.trim());
        log.info("process workitem transferred: example={}, workitem={}, to={}",
                example.getFieldId(), workitem.getFieldId(), String.join(";", targets));
    }

    /**
     * 转办收回（handler_transfer_retract，P17-BE-010）：转办发起人（原审批人）在被转办工作项
     * 仍 PROCESSING 时收回，链路回退到本人（A→B→收回→A）。
     * <ul>
     * <li>入参 workitemId = 原工作项 id（转办发起人持有的已 ABANDON 工作项）；身份校验：当前用户必须为
     *     原工作项 {@code field_handler_id}（不符抛 FORBIDDEN）；</li>
     * <li>前置：原工作项状态 ABANDON，且沿 {@code field_from_workitem_id} 链存在仍 PROCESSING 的
     *     被转办工作项；链上任一被转办工作项已处理（FINISH / PAUSE）抛 STATE_CONFLICT；</li>
     * <li>动作：整链仍 PROCESSING 的被转办工作项置 ABANDON（多代链 A→B→C 中 B 已在转办时弃置）；
     *     为原审批人生成新工作项（PROCESSING、from_workitem_id 回链最新一代仍 PROCESSING 的被转办
     *     工作项，一次转办多人并列时回链原工作项 id，链路均可经 from_workitem_id 逐级回溯；
     *     config 继承原工作项）；会签/串行下恢复的是原序位工作项，节点通过判定不变；</li>
     * <li>留痕 {@link SysProcessConstants#AUDIT_TYPE_HANDLER_TRANSFER_RETRACT}（workitemId=原工作项 id）。</li>
     * </ul>
     *
     * @param exampleId  流程实例 id（必填）
     * @param workitemId 原工作项 id（必填，须为转办发起人的已 ABANDON 工作项）
     * @param content    收回意见（必填）
     */
    @Transactional
    public void transferRetract(String exampleId, String workitemId, String content) {
        SysProcessExample example = requireExample(exampleId);
        if (!EXAMPLE_STATUS_PROCESSING.equals(example.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：仅审批中的流程可执行转办收回");
        }
        requireOperateParam(workitemId, "workitemId");
        if (StringUtil.isEmpty(content)) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_PARAM_MISSING + "：content 必填");
        }
        SysProcessExampleWorkitem original = workitemDao.selectById(workitemId.trim());
        if (null == original || !example.getFieldId().equals(original.getFieldExampleId())) {
            throw new IllegalArgumentException("工作项不存在");
        }
        SysOrgUser current = requireCurrentUser();
        if (!current.getFieldId().equals(original.getFieldHandlerId())) {
            throw new AuthorizationException("FORBIDDEN",
                    CS_PROCESS_OPERATE_FORBIDDEN + "：仅转办发起人可收回");
        }
        if (!WORKITEM_STATUS_ABANDON.equals(original.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：工作项未处于转办状态，无法收回");
        }
        // 沿 from_workitem_id 链收集被转办工作项（按代分层）：任一已处理（FINISH/PAUSE）即不可收回
        List<List<SysProcessExampleWorkitem>> levels = transferDescendantLevels(example.getFieldId(),
                original.getFieldId());
        if (levels.isEmpty()) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：未找到被转办工作项，无法收回");
        }
        List<SysProcessExampleWorkitem> processing = new ArrayList<>();
        for (List<SysProcessExampleWorkitem> level : levels) {
            for (SysProcessExampleWorkitem descendant : level) {
                if (WORKITEM_STATUS_PROCESSING.equals(descendant.getFieldStatus())) {
                    processing.add(descendant);
                } else if (!WORKITEM_STATUS_ABANDON.equals(descendant.getFieldStatus())) {
                    throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT
                            + "：被转办工作项已处理，无法收回");
                }
            }
        }
        if (processing.isEmpty()) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：被转办工作项已处理，无法收回");
        }
        // 新工作项回链：最新一代仍 PROCESSING 的被转办工作项唯一时回链它（A→B→C 回链 C），
        // 并列多目标时回链原工作项 id（均能经 from_workitem_id 逐级回溯整链）
        String fromWorkitemId = original.getFieldId();
        for (int i = levels.size() - 1; i >= 0; i--) {
            List<SysProcessExampleWorkitem> levelProcessing = new ArrayList<>();
            for (SysProcessExampleWorkitem descendant : levels.get(i)) {
                if (WORKITEM_STATUS_PROCESSING.equals(descendant.getFieldStatus())) {
                    levelProcessing.add(descendant);
                }
            }
            if (!levelProcessing.isEmpty()) {
                if (1 == levelProcessing.size()) {
                    fromWorkitemId = levelProcessing.get(0).getFieldId();
                }
                break;
            }
        }
        // 整链置 ABANDON（多代链中间代已在转办时弃置，此处弃置全部仍 PROCESSING 的后代）
        Date now = new Date();
        for (SysProcessExampleWorkitem descendant : processing) {
            descendant.setFieldStatus(WORKITEM_STATUS_ABANDON);
            descendant.setFieldFinishTime(now);
            workitemDao.update(descendant);
        }
        // 为原审批人生成新工作项（恢复原序位：config 继承原工作项）
        workitemDao.insert(buildTransferWorkitem(example, original, original.getFieldHandlerId(), fromWorkitemId));
        addAudit(example.getFieldId(), original.getFieldId(), current.getFieldId(),
                AUDIT_TYPE_HANDLER_TRANSFER_RETRACT, content.trim());
        log.info("process workitem transfer retracted: example={}, workitem={}",
                example.getFieldId(), original.getFieldId());
    }

    /**
     * 已审批人收回（processed_retract，P17-BE-010）：已处理人将本人已办结（FINISH）工作项收回到本人
     * （重新持 PROCESSING 工作项），仅限正常流转场景。
     * <ul>
     * <li>身份：当前用户必须为该工作项 {@code field_handler_id} 且工作项属于本实例（不符抛 FORBIDDEN）；</li>
     * <li>前置：流程 PROCESSING、工作项状态 FINISH、工作项所属节点仍 PROCESSING（节点尚未流出；
     *     节点已 FINISH / PASS / REJECT 抛 STATE_CONFLICT——不得用于已流过的后续节点）；</li>
     * <li>动作：该工作项重新置 PROCESSING（处理人不变；完成时间保留不影响重新处理——工作项动态更新
     *     按非空字段落库）；收回后按节点 approveMode 自然参与节点通过判定（会签需其重新通过；单签/串行
     *     由引擎按在途工作项与队列口径继续推进）；后续节点 skipMode prev/any 的「已审批人」集合不做
     *     追溯性重算（见「审批操作矩阵」实现补充）；</li>
     * <li>留痕 {@link SysProcessConstants#AUDIT_TYPE_PROCESSED_RETRACT}（workitemId=被收回工作项 id）。</li>
     * </ul>
     *
     * @param exampleId  流程实例 id（必填）
     * @param workitemId 被收回工作项 id（必填，须为当前用户已办结工作项）
     * @param content    收回意见（必填）
     */
    @Transactional
    public void processedRetract(String exampleId, String workitemId, String content) {
        SysProcessExample example = requireExample(exampleId);
        if (!EXAMPLE_STATUS_PROCESSING.equals(example.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：仅审批中的流程可收回已办结工作项");
        }
        requireOperateParam(workitemId, "workitemId");
        if (StringUtil.isEmpty(content)) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_PARAM_MISSING + "：content 必填");
        }
        SysProcessExampleWorkitem workitem = workitemDao.selectById(workitemId.trim());
        if (null == workitem || !example.getFieldId().equals(workitem.getFieldExampleId())) {
            throw new IllegalArgumentException("工作项不存在");
        }
        SysOrgUser current = requireCurrentUser();
        if (!current.getFieldId().equals(workitem.getFieldHandlerId())) {
            throw new AuthorizationException("FORBIDDEN",
                    CS_PROCESS_OPERATE_FORBIDDEN + "：仅工作项处理人可收回已办结工作项");
        }
        if (!WORKITEM_STATUS_FINISH.equals(workitem.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：仅已办结工作项可收回");
        }
        SysProcessExampleNode node = requireNode(workitem.getFieldNodeId());
        if (!NODE_STATUS_PROCESSING.equals(node.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：节点已办结，无法收回");
        }
        // 收回到本人：工作项重新置 PROCESSING（处理人不变；完成时间保留不影响重新处理）
        workitem.setFieldStatus(WORKITEM_STATUS_PROCESSING);
        workitemDao.update(workitem);
        addAudit(example.getFieldId(), workitem.getFieldId(), current.getFieldId(),
                AUDIT_TYPE_PROCESSED_RETRACT, content.trim());
        log.info("process workitem processed retracted: example={}, workitem={}",
                example.getFieldId(), workitem.getFieldId());
    }

    /* ==================== 审批人抄送（P17-BE-012） ==================== */

    /**
     * 审批人抄送（handler_cc，P17-BE-012）：当前处理人将本工作项所在在途节点的内容抄送给
     * 显式指定的用户（地址本多选结果 ccToIds），逐人发送 READ 待阅
     * （标题消费公共配置 {@code notify_cc_subject}，经共享组件
     * {@link ProcessNotifySender#sendCcRead} 发送），生成
     * {@link SysProcessConstants#AUDIT_TYPE_HANDLER_CC HANDLER_CC} 审批记录。
     * <ul>
     * <li>身份：当前用户必须为 workitemId 对应工作项的处理人
     *     （{@link #requireProcessingWorkitem} 既有语义，不符抛 FORBIDDEN）；</li>
     * <li>状态：流程 PROCESSING，工作项 PROCESSING 且所属节点在途（PROCESSING；
     *     抄送是在途告知，节点已流出后不可再发起，不符抛 STATE_CONFLICT）；</li>
     * <li>动作：<b>不改变工作项/节点/流程状态</b>（抄送不是审批动作）；收件人 = ccToIds
     *     （';' 分隔去重保序，不排除发起人——抄送对象为显式指定），逐人经用户表校验存在
     *     （允许禁用状态，对齐转办目标校验语义）；待阅内容 = 审批意见；</li>
     * <li>留痕：HANDLER_CC 一条（workitemId=当前工作项 id，handlerId=发起人，content=审批意见）。</li>
     * </ul>
     *
     * @param exampleId  流程实例 id（必填）
     * @param workitemId 当前处理人持有的工作项 id（必填，须为 PROCESSING）
     * @param ccToIds    抄送对象用户 id（';' 分隔或多值，去重保序，必填）
     * @param content    审批意见（必填，写入待阅内容与留痕）
     */
    @Transactional
    public void handlerCc(String exampleId, String workitemId, String ccToIds, String content) {
        SysProcessExample example = requireExample(exampleId);
        if (!EXAMPLE_STATUS_PROCESSING.equals(example.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：仅审批中的流程可执行抄送");
        }
        requireOperateParam(workitemId, "workitemId");
        if (StringUtil.isEmpty(content)) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_PARAM_MISSING + "：content 必填");
        }
        SysProcessExampleWorkitem workitem = requireProcessingWorkitem(example.getFieldId(), workitemId);
        SysProcessExampleNode node = requireNode(workitem.getFieldNodeId());
        if (!NODE_STATUS_PROCESSING.equals(node.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：节点已办结，无法抄送");
        }
        List<String> targets = parseCcToIds(ccToIds);
        requireUserTargetsExist(targets, "抄送对象用户");
        SysOrgUser current = requireCurrentUser();
        notifySender.sendCcRead(example, content.trim(), targets);
        addAudit(example.getFieldId(), workitem.getFieldId(), current.getFieldId(),
                AUDIT_TYPE_HANDLER_CC, content.trim());
        log.info("process workitem cc-ed by handler: example={}, workitem={}, to={}",
                example.getFieldId(), workitem.getFieldId(), String.join(";", targets));
    }

    /** 解析转办目标用户 id 列表（';' 分隔或多值，去重保序）；为空抛 CS_PROCESS_OPERATE_PARAM_MISSING。 */
    private List<String> parseTransferToIds(String transferToIds) {
        Set<String> parsed = parseHandlerIds(transferToIds);
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_PARAM_MISSING + "：transferToIds 必填");
        }
        return new ArrayList<>(parsed);
    }

    /**
     * 解析抄送对象用户 id 列表（handler_cc 的 ccToIds，';' 分隔或多值，去重保序）；
     * 为空抛 CS_PROCESS_OPERATE_PARAM_MISSING（语义同 {@link #parseTransferToIds}，参数名文案区分）。
     */
    private List<String> parseCcToIds(String ccToIds) {
        Set<String> parsed = parseHandlerIds(ccToIds);
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_PARAM_MISSING + "：ccToIds 必填");
        }
        return new ArrayList<>(parsed);
    }

    /** 校验目标用户存在（按 id 查用户表；允许禁用状态，对齐审批人解析语义），不存在的目标拒绝。 */
    private void requireUserTargetsExist(List<String> targets, String label) {
        for (String targetId : targets) {
            if (null == userMapper.selectById(targetId)) {
                throw new IllegalArgumentException(CS_PROCESS_OPERATE_PARAM_MISSING
                        + "：" + label + "不存在: " + targetId);
            }
        }
    }

    /**
     * 构造转办/转办收回生成的新工作项：处理人与期望处理人=目标人（被转办人/原审批人视为该序位
     * 实际处理人）、状态 PROCESSING、{@code field_from_workitem_id} 关联来源工作项、
     * {@code field_config} 继承来源工作项配置（保留 resumeMode / flowChoice / flowBranchKey）。
     */
    private SysProcessExampleWorkitem buildTransferWorkitem(SysProcessExample example,
                                                            SysProcessExampleWorkitem source,
                                                            String targetHandlerId, String fromWorkitemId) {
        SysProcessExampleWorkitem created = new SysProcessExampleWorkitem();
        created.initFieldId();
        created.setFieldExampleId(example.getFieldId());
        created.setFieldNodeId(source.getFieldNodeId());
        created.setFieldHandlerId(targetHandlerId);
        created.setFieldExpectHandlerId(targetHandlerId);
        created.setFieldCreateTime(new Date());
        created.setFieldStatus(WORKITEM_STATUS_PROCESSING);
        // 配置继承来源工作项（先读回原 config 再写，保留 resumeMode / flowChoice / flowBranchKey）
        created.setFieldConfig(source.getFieldConfig());
        created.setFieldFromWorkitemId(fromWorkitemId);
        return created;
    }

    /**
     * 收集原工作项沿 {@code field_from_workitem_id} 链的全部被转办后代（按代分层，广度优先）：
     * 实例工作项一次读取建「来源→子代」索引后逐层下探，支持转办链 A→B→C 与一次转办多人（并列子代）。
     */
    private List<List<SysProcessExampleWorkitem>> transferDescendantLevels(String exampleId,
                                                                           String originWorkitemId) {
        List<SysProcessExampleWorkitem> all = workitemDao.selectListByExampleId(exampleId);
        Map<String, List<SysProcessExampleWorkitem>> childrenByFrom = new HashMap<>();
        if (null != all) {
            for (SysProcessExampleWorkitem item : all) {
                if (StringUtil.isNotEmpty(item.getFieldFromWorkitemId())) {
                    childrenByFrom.computeIfAbsent(item.getFieldFromWorkitemId(), key -> new ArrayList<>()).add(item);
                }
            }
        }
        List<List<SysProcessExampleWorkitem>> levels = new ArrayList<>();
        List<SysProcessExampleWorkitem> current =
                childrenByFrom.getOrDefault(originWorkitemId, List.of());
        Set<String> visited = new HashSet<>();
        visited.add(originWorkitemId);
        while (!current.isEmpty()) {
            List<SysProcessExampleWorkitem> next = new ArrayList<>();
            for (SysProcessExampleWorkitem item : current) {
                if (visited.add(item.getFieldId())) {
                    next.addAll(childrenByFrom.getOrDefault(item.getFieldId(), List.of()));
                }
            }
            levels.add(current);
            current = next;
        }
        return levels;
    }

    /* ==================== 起草人 / 已审批人催办（P17-BE-011） ==================== */

    /**
     * 起草人催办（drafter_expedite，P17-BE-011）：起草人向当前节点全部
     * {@code PROCESSING} 工作项处理人发送催办待阅（READ），生成
     * {@link SysProcessConstants#AUDIT_TYPE_DRAFTER_EXPEDITE DRAFTER_EXPEDITE} 审批记录
     * （无工作项，workitemId 记 null）。催办不改变流程/节点/工作项状态。
     * <ul>
     * <li>身份：仅流程起草人（不符抛 FORBIDDEN，{@code CS_PROCESS_OPERATE_FORBIDDEN} 前缀）；</li>
     * <li>前置：流程 {@code PROCESSING}（否则 {@code CS_PROCESS_OPERATE_STATE_CONFLICT}）；</li>
     * <li>通知：READ 待阅（标题消费公共配置 {@code notify_expedite_subject}，经共享组件
     *     {@link ProcessNotifySender#sendExpediteRead} 发送），收件人排除发起人本人，
     *     收件人为空时仅留痕不发通知；</li>
     * <li>事务：催办与通知同事务——通知发送失败视为催办失败整体抛出（对齐「任务在流转
     *     事务内同步执行」的既有风格，保证留痕与待阅一致）。</li>
     * </ul>
     *
     * @param exampleId 流程实例 id（必填）
     * @param content   催办意见（必填，写入留痕与待阅内容）
     */
    @Transactional
    public void drafterExpedite(String exampleId, String content) {
        SysProcessExample example = requireExample(exampleId);
        requireDrafter(example);
        if (!EXAMPLE_STATUS_PROCESSING.equals(example.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：仅审批中的流程可催办");
        }
        String note = requireOperateParam(content, "content");
        SysOrgUser current = requireCurrentUser();
        addAudit(example.getFieldId(), null, current.getFieldId(),
                AUDIT_TYPE_DRAFTER_EXPEDITE, note);
        sendExpediteNotify(example, current.getFieldId(), note);
        log.info("process example expedited by drafter: id={}", example.getFieldId());
    }

    /**
     * 已审批人催办（processed_expedite，P17-BE-011）：已审批人向当前节点全部
     * {@code PROCESSING} 工作项处理人发送催办待阅（READ），生成
     * {@link SysProcessConstants#AUDIT_TYPE_PROCESSED_EXPEDITE PROCESSED_EXPEDITE} 审批记录
     * （workitemId 记发起人持有的工作项 id）。催办不改变流程/节点/工作项状态。
     * <ul>
     * <li>身份：当前用户必须为 workitemId 对应工作项的处理人且工作项属于本实例
     *     （不符抛 FORBIDDEN，{@code CS_PROCESS_OPERATE_FORBIDDEN} 前缀）；</li>
     * <li>工作项状态：FINISH（已办结）或 PROCESSING（如收回后重新持有）均可——「已处理人」
     *     定义含已办结工作项；转办弃置（ABANDON）的原工作项不算已处理人，拒绝
     *     （{@code CS_PROCESS_OPERATE_STATE_CONFLICT}）；</li>
     * <li>前置：流程 {@code PROCESSING}（否则 {@code CS_PROCESS_OPERATE_STATE_CONFLICT}）；</li>
     * <li>通知/事务：同 {@link #drafterExpedite(String, String)}（共用标题模板与收件人口径，
     *     收件人排除发起人本人，收件人为空时仅留痕不发通知）。</li>
     * </ul>
     *
     * @param exampleId  流程实例 id（必填）
     * @param workitemId 发起人持有的工作项 id（必填，须为本实例 FINISH / PROCESSING 工作项）
     * @param content    催办意见（必填，写入留痕与待阅内容）
     */
    @Transactional
    public void processedExpedite(String exampleId, String workitemId, String content) {
        SysProcessExample example = requireExample(exampleId);
        if (!EXAMPLE_STATUS_PROCESSING.equals(example.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：仅审批中的流程可催办");
        }
        requireOperateParam(workitemId, "workitemId");
        String note = requireOperateParam(content, "content");
        SysProcessExampleWorkitem workitem = workitemDao.selectById(workitemId.trim());
        if (null == workitem || !example.getFieldId().equals(workitem.getFieldExampleId())) {
            throw new IllegalArgumentException("工作项不存在");
        }
        SysOrgUser current = requireCurrentUser();
        if (!current.getFieldId().equals(workitem.getFieldHandlerId())) {
            throw new AuthorizationException("FORBIDDEN",
                    CS_PROCESS_OPERATE_FORBIDDEN + "：仅工作项处理人可催办");
        }
        if (!WORKITEM_STATUS_FINISH.equals(workitem.getFieldStatus())
                && !WORKITEM_STATUS_PROCESSING.equals(workitem.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT
                    + "：仅已办结或审批中的工作项处理人可催办（转办弃置的工作项不算已处理人）");
        }
        addAudit(example.getFieldId(), workitem.getFieldId(), current.getFieldId(),
                AUDIT_TYPE_PROCESSED_EXPEDITE, note);
        sendExpediteNotify(example, current.getFieldId(), note);
        log.info("process example expedited by processed handler: id={}, workitem={}",
                example.getFieldId(), workitem.getFieldId());
    }

    /**
     * 计算催办收件人：当前节点（状态 {@code PROCESSING} 的实例节点，并行分支可能多个）
     * 全部 {@code PROCESSING} 工作项的处理人，跨节点合并去重保序，并排除催办发起人本人。
     *
     * @param example     流程实例（非空）
     * @param initiatorId 催办发起人 id（从收件人中排除）
     * @return 收件人 id 列表（去重保序；可能为空）
     */
    private List<String> resolveExpediteRecipients(SysProcessExample example, String initiatorId) {
        Set<String> recipients = new LinkedHashSet<>();
        List<SysProcessExampleNode> processingNodes =
                nodeDao.selectByExampleAndStatus(example.getFieldId(), NODE_STATUS_PROCESSING);
        if (null != processingNodes) {
            for (SysProcessExampleNode node : processingNodes) {
                for (SysProcessExampleWorkitem workitem : workitemDao.selectListByNodeId(node.getFieldId())) {
                    if (WORKITEM_STATUS_PROCESSING.equals(workitem.getFieldStatus())
                            && StringUtil.isNotEmpty(workitem.getFieldHandlerId())
                            && !workitem.getFieldHandlerId().equals(initiatorId)) {
                        recipients.add(workitem.getFieldHandlerId());
                    }
                }
            }
        }
        return new ArrayList<>(recipients);
    }

    /**
     * 发送催办待阅（起草人催办 / 已审批人催办共用）：READ 待阅发送收口到共享组件
     * {@link ProcessNotifySender#sendExpediteRead}——标题按公共配置
     * {@code notify.notify_expedite_subject} 渲染（缺失回退默认模板），来源模块
     * {@link SysProcessConstants#MODEL_KEY_SYS_PROCESS}，modelId=实例 id，链接=实例详情页
     * （现状路由 {@code /processExample/detail/{id}}；Wave 3 审批中心调整路由时同步更新），
     * 内容=催办意见；收件人为空时仅留痕不发通知（空表由组件静默处理）。
     * 通知落库即「待阅可达」（SSE 推送由 NotifySendEvent 事务提交后异步处理）；
     * 发送失败不在此捕获——催办与通知同事务整体失败（见 {@link #drafterExpedite(String, String)}）。
     */
    private void sendExpediteNotify(SysProcessExample example, String initiatorId, String content) {
        List<String> recipients = resolveExpediteRecipients(example, initiatorId);
        if (recipients.isEmpty()) {
            return;
        }
        notifySender.sendExpediteRead(example, content, recipients);
    }

    /* ==================== 管理员特权操作 ==================== */

    /**
     * 管理员特权操作：跳转 / 终审通过 / 作废 / 暂停 / 恢复 / 更换审批人（v1_7 新增，P17-BE-013）
     * （Controller 已按 SYS_PROCESS_EXAMPLE_ADMIN 鉴权）；意见必填；生成对应 ADMIN_* 审批记录。
     * <p>跳转目标节点：targetNodeId 与 targetNodeKey 二者其一必填（targetNodeId 优先，
     * 见 {@link #adminJump}）；更换审批人：workitemId / changeToId 必填（缺失抛
     * {@code CS_PROCESS_OPERATE_PARAM_MISSING}），状态限 PROCESSING，详见
     * {@link #adminChangeHandler}。</p>
     */
    @Transactional
    public void adminOperate(SysProcessAdminOperateRequest request) {
        if (null == request || StringUtil.isEmpty(request.getFieldId())) {
            throw new IllegalArgumentException("fieldId 不能为空");
        }
        if (StringUtil.isEmpty(request.getContent())) {
            throw new IllegalArgumentException("意见必填");
        }
        SysProcessExample example = requireExample(request.getFieldId());
        SysOrgUser current = requireCurrentUser();
        String operateType = null == request.getOperateType() ? "" : request.getOperateType().trim();
        String content = request.getContent().trim();
        switch (operateType) {
            case ADMIN_OPERATE_JUMP -> adminJump(example, current.getFieldId(),
                    request.getTargetNodeId(), request.getTargetNodeKey(), content);
            case ADMIN_OPERATE_FINISH -> adminFinish(example, current.getFieldId(), content);
            case ADMIN_OPERATE_ABANDON -> abandonExample(example, current.getFieldId(), AUDIT_TYPE_ADMIN_ABANDON, content);
            case ADMIN_OPERATE_PAUSE -> adminPause(example, current.getFieldId(), content);
            case ADMIN_OPERATE_RESUME -> adminResume(example, current.getFieldId(), content);
            case ADMIN_OPERATE_CHANGE_HANDLER -> {
                String workitemId = requireOperateParam(request.getWorkitemId(), "workitemId");
                String changeToId = requireOperateParam(request.getChangeToId(), "changeToId");
                adminChangeHandler(example, current.getFieldId(), workitemId, changeToId, content);
            }
            default -> throw new IllegalArgumentException("非法的特权操作类型: " + operateType);
        }
        log.info("process example admin operated: id={}, type={}", example.getFieldId(), operateType);
    }

    /* ==================== 业务模块流程接入公共能力（供接入流程的业务模块复用） ==================== */

    /**
     * 业务文档审批面板操作转发：接收面板提交内容，按操作类型转发引擎对应方法，
     * 然后返回操作后的流程实例，供调用方按流程最终状态回写业务状态。
     * <p>各业务模块的 audit 接口（如 /backend/{module}/{model}/audit）复用本方法，
     * 面板操作类型值域与审批记录类型一致（既有大写 {@link SysProcessConstants#AUDIT_TYPE_*}），
     * v1_7 起同步扩展 14 类小写操作类型与新参数（rejectTargetKey / flowChoice / transferToIds /
     * changeToId / resumeMode / ccToIds），旧调用不传新参数时按原语义执行（向后兼容）。</p>
     *
     * @param exampleId       流程实例 id
     * @param operateType     操作类型：既有大写 AUDIT_TYPE_* / 14 类小写操作标识
     * @param workitemId      工作项 id（通过/驳回等审批人类操作必填）
     * @param content         审批意见
     * @param resumeMode      驳回后重新提交流转方式（restart / backToNode，驳回时必填）
     * @param targetNodeId    跳转节点 id（管理员跳转必填）
     * @param extraAdminRoles 额外允许执行特权操作的业务角色（如模块管理员），可空
     * @return 操作后的流程实例（非空）
     */
    @Transactional
    public SysProcessExample operate(String exampleId, String operateType, String workitemId,
                                     String content, String resumeMode, String targetNodeId,
                                     String... extraAdminRoles) {
        SysProcessOperateRequest request = new SysProcessOperateRequest();
        request.setExampleId(exampleId);
        request.setOperateType(operateType);
        request.setWorkitemId(workitemId);
        request.setContent(content);
        request.setResumeMode(resumeMode);
        request.setTargetNodeId(targetNodeId);
        return operate(request, extraAdminRoles);
    }

    /**
     * 流程审批统一操作（扩展实现）：按操作类型分发引擎对应方法，返回操作后的流程实例。
     * <p>分发口径（docs/设计文档/sys/process/流程v1_7接口清单.md §6.2 操作矩阵）：</p>
     * <ul>
     * <li>既有大写类型（业务模块 audit 转发通道）：HANDLER_PASS / HANDLER_RETURN /
     *     DRAFTER_BACK / DRAFTER_ABANDON / ADMIN_JUMP / ADMIN_FINISH / ADMIN_ABANDON /
     *     ADMIN_PAUSE / ADMIN_RESUME，分发逻辑不变（向后兼容）；</li>
     * <li>14 类小写操作类型全部接通真实引擎分发（P17-BE-008/009/010/011/012/013 逐批落地，
     *     占位清单已清零）：drafter_submit / drafter_cancel / drafter_retract / drafter_expedite /
     *     handler_pass / handler_reject / handler_cc / handler_transfer / handler_transfer_retract /
     *     processed_retract / processed_expedite / admin_abandon / admin_jump /
     *     admin_change_handler（管理员更换审批人，经 {@link #requireProcessAdmin} 校验后
     *     组装 {@link SysProcessAdminOperateRequest} 走 {@link #adminOperate(SysProcessAdminOperateRequest)}）；</li>
     * <li>未登记类型一律拒绝（{@code CS_PROCESS_OPERATE_TYPE_INVALID}）。</li>
     * </ul>
     * <p>本方法内完成操作人身份校验（起草人 / 当前处理人 / 已处理人 / 流程管理员，
     * 见 §6.3）：身份不符抛 {@code AuthorizationException}（FORBIDDEN，
     * {@code CS_PROCESS_OPERATE_FORBIDDEN} 前缀）；必填参数缺失抛
     * {@code CS_PROCESS_OPERATE_PARAM_MISSING} 前缀；状态前置条件不满足抛
     * {@code CS_PROCESS_OPERATE_STATE_CONFLICT} 前缀。</p>
     *
     * @param request         统一操作请求（exampleId / operateType 必填）
     * @param extraAdminRoles 额外允许执行特权操作的业务角色（如模块管理员），可空
     * @return 操作后的流程实例（非空）
     */
    @Transactional
    public SysProcessExample operate(SysProcessOperateRequest request, String... extraAdminRoles) {
        if (null == request) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_PARAM_MISSING + "：请求体不能为空");
        }
        String exampleId = requireOperateParam(request.getExampleId(), "exampleId");
        String type = requireOperateParam(request.getOperateType(), "operateType");
        String workitemId = trimToNull(request.getWorkitemId());
        String content = request.getContent();
        String resumeMode = trimToNull(request.getResumeMode());
        String targetNodeId = trimToNull(request.getTargetNodeId());
        String rejectTargetKey = trimToNull(request.getRejectTargetKey());
        String flowChoice = trimToNull(request.getFlowChoice());
        requireExample(exampleId);
        switch (type) {
            /* ==================== 既有大写类型（audit 转发通道，向后兼容） ==================== */
            case AUDIT_TYPE_HANDLER_PASS -> pass(exampleId, workitemId, content);
            case AUDIT_TYPE_HANDLER_RETURN -> reject(exampleId, workitemId, content, resumeMode);
            case AUDIT_TYPE_DRAFTER_BACK -> back(exampleId, content);
            case AUDIT_TYPE_DRAFTER_ABANDON -> abandonByDrafter(exampleId, content);
            case AUDIT_TYPE_ADMIN_JUMP -> adminOperate(exampleId, ADMIN_OPERATE_JUMP, targetNodeId, content, extraAdminRoles);
            case AUDIT_TYPE_ADMIN_FINISH -> adminOperate(exampleId, ADMIN_OPERATE_FINISH, null, content, extraAdminRoles);
            case AUDIT_TYPE_ADMIN_ABANDON -> adminOperate(exampleId, ADMIN_OPERATE_ABANDON, null, content, extraAdminRoles);
            case AUDIT_TYPE_ADMIN_PAUSE -> adminOperate(exampleId, ADMIN_OPERATE_PAUSE, null, content, extraAdminRoles);
            case AUDIT_TYPE_ADMIN_RESUME -> adminOperate(exampleId, ADMIN_OPERATE_RESUME, null, content, extraAdminRoles);
            /* ==================== 14 类小写操作类型（操作矩阵 §6.2） ==================== */
            case OPERATE_TYPE_DRAFTER_SUBMIT -> submit(exampleId, content, flowChoice);
            case OPERATE_TYPE_DRAFTER_CANCEL -> abandonByDrafter(exampleId, content);
            case OPERATE_TYPE_DRAFTER_RETRACT -> back(exampleId, content);
            case OPERATE_TYPE_HANDLER_PASS -> {
                requireOperateParam(workitemId, "workitemId");
                pass(exampleId, workitemId, content, flowChoice);
            }
            case OPERATE_TYPE_HANDLER_REJECT -> {
                requireOperateParam(workitemId, "workitemId");
                requireOperateParam(rejectTargetKey, "rejectTargetKey");
                requireOperateParam(resumeMode, "resumeMode");
                reject(exampleId, workitemId, content, rejectTargetKey, resumeMode);
            }
            case OPERATE_TYPE_ADMIN_ABANDON -> adminOperate(exampleId, ADMIN_OPERATE_ABANDON, null, content, extraAdminRoles);
            case OPERATE_TYPE_ADMIN_JUMP -> {
                requireOperateParam(targetNodeId, "targetNodeId");
                adminOperate(exampleId, ADMIN_OPERATE_JUMP, targetNodeId, content, extraAdminRoles);
            }
            /* ==================== 转办 / 转办收回 / 已审批人收回（P17-BE-010） ==================== */
            case OPERATE_TYPE_HANDLER_TRANSFER -> {
                requireOperateParam(workitemId, "workitemId");
                requireOperateParam(request.getTransferToIds(), "transferToIds");
                transfer(exampleId, workitemId, request.getTransferToIds(), content);
            }
            case OPERATE_TYPE_HANDLER_TRANSFER_RETRACT -> {
                requireOperateParam(workitemId, "workitemId");
                transferRetract(exampleId, workitemId, content);
            }
            case OPERATE_TYPE_PROCESSED_RETRACT -> {
                requireOperateParam(workitemId, "workitemId");
                processedRetract(exampleId, workitemId, content);
            }
            /* ==================== 起草人 / 已审批人催办（P17-BE-011） ==================== */
            case OPERATE_TYPE_DRAFTER_EXPEDITE -> {
                requireOperateParam(content, "content");
                drafterExpedite(exampleId, content);
            }
            case OPERATE_TYPE_PROCESSED_EXPEDITE -> {
                requireOperateParam(workitemId, "workitemId");
                requireOperateParam(content, "content");
                processedExpedite(exampleId, workitemId, content);
            }
            /* ==================== 审批人抄送（P17-BE-012） ==================== */
            case OPERATE_TYPE_HANDLER_CC -> {
                requireOperateParam(workitemId, "workitemId");
                requireOperateParam(request.getCcToIds(), "ccToIds");
                requireOperateParam(content, "content");
                handlerCc(exampleId, workitemId, request.getCcToIds(), content);
            }
            /* ==================== 管理员更换审批人（P17-BE-013，占位清零：14 类全接通） ==================== */
            case OPERATE_TYPE_ADMIN_CHANGE_HANDLER -> {
                requireOperateParam(workitemId, "workitemId");
                requireOperateParam(request.getChangeToId(), "changeToId");
                requireOperateParam(content, "content");
                adminChangeHandlerByOperate(exampleId, workitemId, request.getChangeToId(), content, extraAdminRoles);
            }
            default -> throw new IllegalArgumentException(CS_PROCESS_OPERATE_TYPE_INVALID + "：非法的流程操作类型: " + type);
        }
        return exampleDao.selectById(exampleId);
    }

    /**
     * 管理员特权操作（业务模块内部使用）：校验流程管理员后组装请求转发
     * {@link #adminOperate(SysProcessAdminOperateRequest)}。
     */
    private void adminOperate(String exampleId, String operateType, String targetNodeId,
                              String content, String... extraAdminRoles) {
        requireProcessAdmin(extraAdminRoles);
        SysProcessAdminOperateRequest req = new SysProcessAdminOperateRequest();
        req.setFieldId(exampleId);
        req.setOperateType(operateType);
        req.setTargetNodeId(trimToNull(targetNodeId));
        req.setContent(content);
        adminOperate(req);
    }

    /**
     * 管理员更换审批人（统一操作通道 admin_change_handler 分发，P17-BE-013）：
     * 校验流程管理员后组装 {@link SysProcessAdminOperateRequest}
     * （operateType=ADMIN_OPERATE_CHANGE_HANDLER）转发
     * {@link #adminOperate(SysProcessAdminOperateRequest)}，语义与特权弹窗入口一致
     * （Q-06 定稿两入口并存：前者为统一操作入口、后者为特权弹窗入口）。
     */
    private void adminChangeHandlerByOperate(String exampleId, String workitemId, String changeToId,
                                             String content, String... extraAdminRoles) {
        requireProcessAdmin(extraAdminRoles);
        SysProcessAdminOperateRequest req = new SysProcessAdminOperateRequest();
        req.setFieldId(exampleId);
        req.setOperateType(ADMIN_OPERATE_CHANGE_HANDLER);
        req.setWorkitemId(trimToNull(workitemId));
        req.setChangeToId(trimToNull(changeToId));
        req.setContent(content);
        adminOperate(req);
    }

    /**
     * 管理员特权操作（快捷入口）：组装请求转发 {@link #adminOperate(SysProcessAdminOperateRequest)}，
     * 供业务模块在已由接口鉴权（创建人/模块管理员）保障的场景使用（如项目中止时的作废登记）。
     */
    @Transactional
    public void adminOperate(String exampleId, String operateType, String content) {
        SysProcessAdminOperateRequest req = new SysProcessAdminOperateRequest();
        req.setFieldId(exampleId);
        req.setOperateType(operateType);
        req.setContent(content);
        adminOperate(req);
    }

    /**
     * 按模板模型启动/重新提交流程（业务文档保存提交时调用，未指定模板）：
     * <ol>
     * <li>modelId 下已有 DRAFT / RETURN 实例时复用并重新提交（驳回后重新编辑提交场景）；</li>
     * <li>否则取该分类默认模板（field_default=1 且启用）创建草稿并提交；无默认模板时流程启动失败。</li>
     * </ol>
     * 提交后流程可能因自动跳过直达终态（FINISH/ABANDON），由调用方按返回实例回写业务状态。
     *
     * @param modelName       模板所属模块（process_template 分类 key）
     * @param modelId         业务文档 id
     * @param code            流程编号（为空时自动生成）
     * @param submitContent   首次提交审批意见
     * @param resubmitContent 驳回后重新提交审批意见
     * @return 提交后的流程实例（非空）
     */
    @Transactional
    public SysProcessExample startByModel(String modelName, String modelId, String code,
                                          String submitContent, String resubmitContent) {
        return startByModel(modelName, modelId, null, code, submitContent, resubmitContent);
    }

    /**
     * 按模板模型启动/重新提交流程（业务文档保存提交时调用，可显式指定模板）：
     * <ol>
     * <li>modelId 下已有 DRAFT / RETURN 实例时复用并重新提交（驳回后重新编辑提交场景）；</li>
     * <li>否则按 {@link #resolveStartTemplate(String, String)} 解析起始模板创建草稿并提交。</li>
     * </ol>
     * 提交后流程可能因自动跳过直达终态（FINISH/ABANDON），由调用方按返回实例回写业务状态。
     *
     * @param modelName       模板所属模块（process_template 分类 key）
     * @param modelId         业务文档 id
     * @param templateId      指定流程模板 id（可为空：使用该分类默认模板，无默认模板则启动失败）
     * @param code            流程编号（为空时自动生成）
     * @param submitContent   首次提交审批意见
     * @param resubmitContent 驳回后重新提交审批意见
     * @return 提交后的流程实例（非空）
     */
    @Transactional
    public SysProcessExample startByModel(String modelName, String modelId, String templateId, String code,
                                          String submitContent, String resubmitContent) {
        SysProcessExample existing = exampleDao.selectByModelId(modelName, modelId);
        if (null != existing
                && (EXAMPLE_STATUS_DRAFT.equals(existing.getFieldStatus())
                || EXAMPLE_STATUS_RETURN.equals(existing.getFieldStatus()))) {
            submit(existing.getFieldId(), resubmitContent);
            return exampleDao.selectById(existing.getFieldId());
        }
        SysProcessTemplate template = resolveStartTemplate(modelName, templateId);
        SysProcessExampleVo created = createDraft(template.getFieldId(), modelId, code);
        submit(created.getFieldId(), submitContent);
        return exampleDao.selectById(created.getFieldId());
    }

    /**
     * 解析流程启动模板（默认不允许静默回退）：
     * <ul>
     * <li>显式指定 templateId：必须存在、启用且 field_model_name 与 modelName 一致，否则启动失败；</li>
     * <li>未指定：取该分类默认模板（field_default=1 且启用），无默认模板则启动失败。</li>
     * </ul>
     * 依据 docs/设计文档/sys/process/流程核心业务.md「默认模板」。
     *
     * @param modelName  模板所属模块（process_template 分类 key）
     * @param templateId 指定流程模板 id（可为空）
     * @return 起始模板（非空）
     */
    private SysProcessTemplate resolveStartTemplate(String modelName, String templateId) {
        if (StringUtil.isNotEmpty(templateId)) {
            SysProcessTemplate template = templateDao.selectById(templateId.trim());
            if (null == template) {
                throw new IllegalArgumentException("流程模板不存在: " + templateId);
            }
            if (!Boolean.TRUE.equals(template.getFieldEnable())) {
                throw new IllegalStateException("流程模板已停用: " + template.getFieldName());
            }
            if (StringUtil.isNotEmpty(modelName) && !modelName.trim().equals(template.getFieldModelName())) {
                throw new IllegalArgumentException("流程模板不属于该模块: " + template.getFieldName());
            }
            return template;
        }
        SysProcessTemplate def = defaultTemplate(modelName);
        if (null == def) {
            String displayName = SysProcessTemplateProperties.getName(modelName);
            throw new IllegalStateException((null == displayName ? modelName : displayName)
                    + "分类未配置默认模板，流程启动失败，请先在「流程管理-模板管理」中设置默认模板");
        }
        return def;
    }

    /**
     * 取指定模板分类的默认启用模板（field_default=1 且 field_enable=1）。
     *
     * @param modelName 模板所属模块（process_template 分类 key）
     * @return 默认模板；无默认或默认已停用时返回 null
     */
    public SysProcessTemplate defaultTemplate(String modelName) {
        if (StringUtil.isEmpty(modelName)) {
            throw new IllegalArgumentException("modelName 不能为空");
        }
        return templateDao.selectDefaultByModelName(modelName.trim());
    }

    /**
     * 校验当前用户拥有流程管理员权限（管理员特权操作：跳转/终审通过/作废/暂停/恢复）。
     * 内置流程管理员角色（SYS_ADMIN / SYS_PROCESS_ADMIN / SYS_PROCESS_EXAMPLE_ADMIN），
     * 并允许调用方追加业务模块额外角色（各业务模块自定义角色编码）。
     */
    public void requireProcessAdmin(String... extraRoles) {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (null == user || null == user.getRoles()) {
            throw new AuthorizationException("FORBIDDEN", "无流程管理员权限");
        }
        List<String> allowed = new ArrayList<>(List.of(ROLE_ADMIN,
                SysProcessConstants.ROLE_SYS_PROCESS_ADMIN,
                SysProcessConstants.ROLE_SYS_PROCESS_EXAMPLE_ADMIN));
        if (null != extraRoles && extraRoles.length > 0) {
            allowed.addAll(List.of(extraRoles));
        }
        boolean ok = user.getRoles().stream().map(SysOrgRole::getFieldCode).anyMatch(allowed::contains);
        if (!ok) {
            throw new AuthorizationException("FORBIDDEN", "无流程管理员权限");
        }
    }

    /* ==================== 列表 / 详情 / 审批面板 ==================== */

    /**
     * 流程实例列表分页查询：所属模块固定、流程名称（模板名）/编号模糊、
     * 发起人名称模糊、创建时间区间与流程状态筛选；仅创建时间单条件排序。
     */
    public List<SysProcessExampleVo> list(SysProcessExampleQuery query, Pages pages,
                                          String sortField, String sortOrder) {
        return exampleDao.selectListSorted(query, normalizePages(pages),
                normalizeSortField(sortField), normalizeSortOrder(sortOrder));
    }

    /**
     * 流程实例列表总数（与 {@link #list} 过滤条件一致，供分页 metadata.total 使用）
     */
    public long count(SysProcessExampleQuery query) {
        return exampleDao.selectCount(query);
    }

    /**
     * 流程实例所属模块选项（process_template 配置项）：流程实例页「所属模块」筛选下拉，
     * 展示与模板管理页左侧分类一致（field_model_name 为 process_template 二级配置项）。
     */
    public List<SysProcessTemplateModelVo> listModels() {
        return SysProcessTemplateProperties.listModels();
    }

    /**
     * 流程实例详情：流程实例基本字段 + 流程实例节点 + 工作项 + 审批记录。
     *
     * @param fieldId 流程实例 id
     * @return 详情；流程实例不存在时返回 null
     */
    public SysProcessExampleDetailVo load(String fieldId) {
        SysProcessExample example = exampleDao.selectById(requireText(fieldId, "fieldId"));
        if (null == example) {
            return null;
        }
        SysProcessExampleDetailVo detail = new SysProcessExampleDetailVo();
        detail.setExample(toExampleVo(example));
        detail.setNodes(toNodeVos(nodeDao.selectListByExampleId(example.getFieldId())));
        List<SysProcessExampleWorkitem> workitems = workitemDao.selectListByExampleId(example.getFieldId());
        detail.setWorkitems(toWorkitemVos(workitems));
        detail.setAuditNotes(toAuditNoteVos(auditNoteDao.selectListByExampleId(example.getFieldId())));
        return detail;
    }

    /**
     * 流程审批面板数据（可嵌入审批组件）：流程基本信息 + 节点 + 审批记录 +
     * 当前用户可执行的审批操作按钮集与待处理工作项。
     * <p>v1_7 起 {@code operations} 升级为按钮对象数组（P17-BE-013，依据
     * docs/设计文档/sys/process/流程v1_7接口清单.md §5.4）：按「节点 operations 配置 ∩
     * 用户身份 ∩ 状态前置」计算（元素含 operateType/operateName/defaultContent，
     * handler_reject 附 rejectTargets、handler_pass / drafter_submit 附 flowChoices），
     * 旧操作标识语义由 operateType 承载；{@code nodeOps} 业务标识语义不变。</p>
     *
     * @param fieldId 流程实例 id
     * @return 面板数据；流程实例不存在时返回 null
     */
    public SysProcessExamplePanelVo panel(String fieldId) {
        SysProcessExample example = exampleDao.selectById(requireText(fieldId, "fieldId"));
        if (null == example) {
            return null;
        }
        SysProcessExamplePanelVo panel = new SysProcessExamplePanelVo();
        panel.setExample(toExampleVo(example));
        panel.setNodes(toNodeVos(nodeDao.selectListByExampleId(example.getFieldId())));
        panel.setAuditNotes(toAuditNoteVos(auditNoteDao.selectListByExampleId(example.getFieldId())));
        panel.setMyWorkitems(toWorkitemVos(workitemDao.selectByExampleAndHandler(example.getFieldId(),
                currentUserIdOrNull())));
        panel.setOperations(computeOperations(example));
        Set<String> nodeOps = resolveNodeOps(example);
        panel.setNodeOps(null == nodeOps ? Set.of() : nodeOps);
        return panel;
    }

    /**
     * 查询某业务单据当前用户在当前流程节点可操作的「操作标识」集合（默认不允许语义）。
     * <p>返回语义（调用方据此决定是否放行业务操作）：</p>
     * <ul>
     * <li>流程实例不存在或流程非审批中（DRAFT/RETURN 等）：返回 {@code null}，表示无流程约束，
     *     调用方按自身原规则放行（如起草人编辑阶段）；</li>
     * <li>流程审批中（PROCESSING）：仅当当前用户在当前审批节点持有待处理工作项时，
     *     返回该节点配置 ops 中开启（true）的操作标识集合；其余情况返回空集（不允许任何操作标识）；</li>
     * <li>节点未配置 ops 或旧模板（field_node_config 无 ops 字段）视为未开启任何操作标识 → 空集（默认不允许）。</li>
     * </ul>
     * 依据 docs/设计文档/sys/process/流程核心业务.md「审批中审批人特定操作业务」。
     *
     * @param modelName 模板分类（process_template 二级配置项，即业务单据 field_model_name）
     * @param modelId   业务单据 id
     * @return 可操作的操作标识集合；无流程约束时返回 null
     */
    public Set<String> resolveNodeOps(String modelName, String modelId) {
        if (StringUtil.isEmpty(modelName) || StringUtil.isEmpty(modelId)) {
            return null;
        }
        SysProcessExample example = exampleDao.selectByModelId(modelName.trim(), modelId.trim());
        if (null == example) {
            return null;
        }
        return resolveNodeOps(example);
    }

    /**
     * 按流程实例计算当前用户可操作的操作标识集合（逻辑见 {@link #resolveNodeOps(String, String)}）。
     *
     * @param example 流程实例（非空）
     * @return 可操作的操作标识集合；无流程约束时返回 null
     */
    private Set<String> resolveNodeOps(SysProcessExample example) {
        if (!EXAMPLE_STATUS_PROCESSING.equals(example.getFieldStatus())) {
            return null;
        }
        String currentUserId = currentUserIdOrNull();
        if (null == currentUserId) {
            return Set.of();
        }
        List<SysProcessExampleNode> processingNodes =
                nodeDao.selectByExampleAndStatus(example.getFieldId(), NODE_STATUS_PROCESSING);
        if (null == processingNodes) {
            return Set.of();
        }
        List<SysProcessExampleWorkitem> myWorkitems =
                workitemDao.selectByExampleAndHandler(example.getFieldId(), currentUserId);
        for (SysProcessExampleNode node : processingNodes) {
            if (!NODE_TYPE_APPROVE.equals(node.getFieldNodeType())
                    || !isHandlerOfNode(node.getFieldId(), myWorkitems)) {
                continue;
            }
            SysProcessTemplateNodeConfigDo config = parseNodeConfig(node.getFieldNodeConfig());
            Map<String, Boolean> ops = null == config ? null : config.getOps();
            if (null == ops || ops.isEmpty()) {
                return Set.of();
            }
            Set<String> enabled = new LinkedHashSet<>();
            for (Map.Entry<String, Boolean> entry : ops.entrySet()) {
                if (StringUtil.isNotEmpty(entry.getKey()) && Boolean.TRUE.equals(entry.getValue())) {
                    enabled.add(entry.getKey().trim());
                }
            }
            return enabled;
        }
        return Set.of();
    }

    /** 当前用户是否指定节点的待处理工作项处理人（会审/单签均适用，仅 PROCESSING 工作项）。 */
    private static boolean isHandlerOfNode(String nodeId, List<SysProcessExampleWorkitem> myWorkitems) {
        if (null == myWorkitems) {
            return false;
        }
        for (SysProcessExampleWorkitem workitem : myWorkitems) {
            if (nodeId.equals(workitem.getFieldNodeId())
                    && WORKITEM_STATUS_PROCESSING.equals(workitem.getFieldStatus())) {
                return true;
            }
        }
        return false;
    }

    /* ==================== 流转引擎入口（薄层：校验 + 委托 ProcessFlowEngine） ==================== */

    /**
     * 计算本节点的自动跳过处理人集合（依据流程核心业务.md「流程流转公共规范」第 5、6 条）：
     * <ul>
     * <li>上一有效节点的审批人（及其后起草人，取决于公共配置 auto_handle_pass_drafter）视为重复审批人；
     *     公共配置 auto_handle_pass 与节点配置 autoSkip 均允许时才会跳过；</li>
     * <li>上一有效节点中被自动跳过的处理人，本节点「是否连续跳过」（节点配置 autoContinueSkip，
     *     跟随系统时取公共配置 auto_continue_pass）为否则不跳过（需要审批）。</li>
     * </ul>
     */
    private Set<String> computeSkippedHandlers(SysProcessExample example, SysProcessTemplateNodeConfigDo config,
                                               Set<String> handlers, PreviousNode prev) {
        Set<String> skipped = new LinkedHashSet<>();
        // 节点配置缺失时按默认值处理（autoSkip/autoContinueSkip 默认跟随系统）
        String autoSkip = null == config || StringUtil.isEmpty(config.getAutoSkip())
                ? AUTO_SKIP_SYSTEM : config.getAutoSkip();
        String autoContinueSkip = null == config || StringUtil.isEmpty(config.getAutoContinueSkip())
                ? AUTO_CONTINUE_SKIP_SYSTEM : config.getAutoContinueSkip();
        boolean systemAutoPass = boolSetting(SETTING_PROCESS_AUTO_HANDLE_PASS, true);
        boolean nodeAutoPass = AUTO_SKIP_YES.equals(autoSkip)
                || (AUTO_SKIP_SYSTEM.equals(autoSkip) && systemAutoPass);
        if (!nodeAutoPass) {
            return skipped;
        }
        boolean systemContinue = boolSetting(SETTING_PROCESS_AUTO_CONTINUE_PASS, true);
        boolean nodeContinue = AUTO_CONTINUE_SKIP_YES.equals(autoContinueSkip)
                || (AUTO_CONTINUE_SKIP_SYSTEM.equals(autoContinueSkip) && systemContinue);
        boolean includeDrafter = boolSetting(SETTING_PROCESS_AUTO_HANDLE_PASS_DRAFTER, true);

        Set<String> duplicate = new HashSet<>(prev.handlers);
        if (includeDrafter) {
            duplicate.add(example.getFieldCreatorId());
        }
        for (String handlerId : handlers) {
            if (!duplicate.contains(handlerId)) {
                continue;
            }
            // 上一有效节点被自动跳过的处理人：本节点不允许连续跳过（autoContinueSkip=no）时需重新审批
            if (prev.skipped.contains(handlerId) && !nodeContinue) {
                continue;
            }
            skipped.add(handlerId);
        }
        return skipped;
    }

    /**
     * 查找指定节点之前的最近「有效节点」：编号小于当前节点且最近一个实际处理过（FINISH / REJECT）的审批节点；
     * 自动跳过（PASS）节点不作为有效节点。返回其审批人集合与被自动跳过的处理人集合。
     */
    private PreviousNode findPreviousEffectiveNode(String exampleId, int currentNo) {
        PreviousNode prev = new PreviousNode();
        SysProcessExampleNode effective = null;
        for (SysProcessExampleNode node : nodeDao.selectListByExampleId(exampleId)) {
            if (node.getFieldNodeNo() >= currentNo) {
                break;
            }
            if (NODE_TYPE_APPROVE.equals(node.getFieldNodeType())
                    && (NODE_STATUS_FINISH.equals(node.getFieldStatus())
                    || NODE_STATUS_REJECT.equals(node.getFieldStatus()))) {
                effective = node;
            }
        }
        if (null == effective) {
            return prev;
        }
        List<SysProcessExampleWorkitem> workitems = workitemDao.selectListByNodeId(effective.getFieldId());
        Set<String> auditWorkitemIds = new HashSet<>();
        for (SysProcessAuditNote note : auditNoteDao.selectListByExampleId(exampleId)) {
            if (AUDIT_TYPE_AUTO_PASS.equals(note.getFieldHandleType())) {
                auditWorkitemIds.add(note.getFieldWorkitemId());
            }
        }
        for (SysProcessExampleWorkitem workitem : workitems) {
            if (StringUtil.isNotEmpty(workitem.getFieldExpectHandlerId())) {
                prev.handlers.add(workitem.getFieldExpectHandlerId());
            } else if (StringUtil.isNotEmpty(workitem.getFieldHandlerId())) {
                prev.handlers.add(workitem.getFieldHandlerId());
            }
            if (auditWorkitemIds.contains(workitem.getFieldId())) {
                prev.skipped.add(workitem.getFieldHandlerId());
            }
        }
        return prev;
    }

    /* ==================== 管理员操作实现 ==================== */

    /**
     * 管理员跳转（v1_7 扩展，P17-BE-013）：PROCESSING / RETURN 时可跳转到审批节点、起草节点
     * 或人工决策节点；生成 ADMIN_JUMP 记录。
     * <p>跳转目标节点解析：targetNodeId 与 targetNodeKey 二者其一必填——targetNodeId 优先；
     * targetNodeId 为空且 targetNodeKey 非空时，按实例节点集合的 nodeKey 匹配解析目标节点
     * （找不到或不是本实例节点抛 {@code CS_PROCESS_NODE_TARGET_INVALID}）；两者都空抛
     * {@code CS_PROCESS_OPERATE_PARAM_MISSING}（跳转节点必填）。</p>
     */
    private void adminJump(SysProcessExample example, String operatorId, String targetNodeId,
                           String targetNodeKey, String content) {
        if (!EXAMPLE_STATUS_PROCESSING.equals(example.getFieldStatus())
                && !EXAMPLE_STATUS_RETURN.equals(example.getFieldStatus())) {
            throw new IllegalArgumentException("仅审批中或驳回状态的流程可跳转");
        }
        // 目标节点解析：targetNodeId 优先；为空时按 targetNodeKey 匹配实例节点集合（key 非法拒绝）
        String resolvedNodeId = trimToNull(targetNodeId);
        String nodeKey = trimToNull(targetNodeKey);
        if (null == resolvedNodeId && null != nodeKey) {
            SysProcessExampleNode keyed = findInstanceNodeByKey(example.getFieldId(), nodeKey);
            if (null == keyed) {
                throw new IllegalArgumentException(CS_PROCESS_NODE_TARGET_INVALID
                        + "：跳转目标节点 " + nodeKey + " 不是本流程实例的节点");
            }
            resolvedNodeId = keyed.getFieldId();
        }
        if (null == resolvedNodeId) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_PARAM_MISSING
                    + "：跳转节点必填（targetNodeId 或 targetNodeKey）");
        }
        SysProcessExampleNode target = requireNode(resolvedNodeId);
        if (!NODE_TYPE_APPROVE.equals(target.getFieldNodeType())
                && !NODE_TYPE_DRAFT.equals(target.getFieldNodeType())
                && !SysProcessConstants.NODE_TYPE_MANUAL.equals(target.getFieldNodeType())) {
            throw new IllegalArgumentException("跳转节点仅限审批节点、起草节点或人工决策节点");
        }
        addAudit(example.getFieldId(), null, operatorId, AUDIT_TYPE_ADMIN_JUMP, content);
        abandonActiveWorkitems(example.getFieldId());
        flowSupport.markCurrentNodeWaiting(example.getFieldId());
        // 跳转=废弃在途工作项后从目标节点进入引擎（起草节点停留等待重新提交）
        flowEngine.adminJump(example, resolvedNodeId);
    }

    /** 按节点业务键（nodeKey）查找实例节点集合中的节点（未命中返回 null）。 */
    private SysProcessExampleNode findInstanceNodeByKey(String exampleId, String nodeKey) {
        List<SysProcessExampleNode> nodes = nodeDao.selectListByExampleId(exampleId);
        if (null == nodes) {
            return null;
        }
        for (SysProcessExampleNode node : nodes) {
            if (nodeKey.equals(ProcessFlowGraph.nodeKey(node))) {
                return node;
            }
        }
        return null;
    }

    /** 管理员终审通过：PROCESSING / RETURN / PAUSE 时办结；生成 ADMIN_FINISH 记录；
     *  办结后发送办结待阅（P17-BE-014 通知收口，与引擎 enterEnd 同口径）。 */
    private void adminFinish(SysProcessExample example, String operatorId, String content) {
        if (!EXAMPLE_STATUS_PROCESSING.equals(example.getFieldStatus())
                && !EXAMPLE_STATUS_RETURN.equals(example.getFieldStatus())
                && !EXAMPLE_STATUS_PAUSE.equals(example.getFieldStatus())) {
            throw new IllegalArgumentException("仅审批中、驳回或暂停状态的流程可终审通过");
        }
        addAudit(example.getFieldId(), null, operatorId, AUDIT_TYPE_ADMIN_FINISH, content);
        finishActiveWorkitems(example.getFieldId());
        moveToEndNode(example, EXAMPLE_STATUS_FINISH);
        // 办结待阅（P17-BE-014 通知收口）：管理员终审通过不经引擎 enterEnd，由 Service 以同口径发送
        notifyFinishReads(example);
    }

    /** 管理员暂停：PROCESSING 时暂停；流程与工作项状态转 PAUSE；生成 ADMIN_PAUSE 记录。 */
    private void adminPause(SysProcessExample example, String operatorId, String content) {
        if (!EXAMPLE_STATUS_PROCESSING.equals(example.getFieldStatus())) {
            throw new IllegalArgumentException("仅审批中的流程可暂停");
        }
        addAudit(example.getFieldId(), null, operatorId, AUDIT_TYPE_ADMIN_PAUSE, content);
        flowSupport.toggleActiveWorkitems(example.getFieldId(),
                WORKITEM_STATUS_PROCESSING, WORKITEM_STATUS_PAUSE);
        example.setFieldStatus(EXAMPLE_STATUS_PAUSE);
        exampleDao.update(example);
    }

    /** 管理员恢复：PAUSE 时恢复；流程与工作项状态转 PROCESSING；生成 ADMIN_RESUME 记录。 */
    private void adminResume(SysProcessExample example, String operatorId, String content) {
        if (!EXAMPLE_STATUS_PAUSE.equals(example.getFieldStatus())) {
            throw new IllegalArgumentException("仅暂停状态的流程可恢复");
        }
        addAudit(example.getFieldId(), null, operatorId, AUDIT_TYPE_ADMIN_RESUME, content);
        flowSupport.toggleActiveWorkitems(example.getFieldId(),
                WORKITEM_STATUS_PAUSE, WORKITEM_STATUS_PROCESSING);
        example.setFieldStatus(EXAMPLE_STATUS_PROCESSING);
        exampleDao.update(example);
    }

    /**
     * 管理员更换审批人（CHANGE_HANDLER，v1_7 新增，P17-BE-013，依据
     * docs/设计文档/sys/process/流程核心业务.md「管理员特权操作」与「审批操作矩阵（14 类）」#14）：
     * 流程 PROCESSING 时将指定 PROCESSING 工作项的处理人替换为目标用户。
     * <ul>
     * <li>状态前置：流程限 {@code PROCESSING}（否则 {@code CS_PROCESS_OPERATE_STATE_CONFLICT}）；</li>
     * <li>工作项：须属于本实例且状态 {@code PROCESSING}（不属于本实例抛「工作项不存在」，
     *     非 PROCESSING 抛 {@code CS_PROCESS_OPERATE_STATE_CONFLICT}：仅处理中的工作项可更换处理人）；
     *     管理员操作不做操作人身份校验（区别于 {@link #requireProcessingWorkitem}）；</li>
     * <li>目标用户：{@code changeToId} 经用户表按 id 校验须存在（允许禁用用户，对齐审批人解析
     *     语义；不存在抛 {@code CS_PROCESS_OPERATE_PARAM_MISSING}）；</li>
     * <li>动作：仅替换 {@code field_handler_id} 与 {@code field_expect_handler_id} = changeToId
     *     （仅本实例有效，不改模板配置），工作项仍是原序位——不改节点/流程状态，不影响串行
     *     序位与会签计数；换人后新处理人按 {@code field_handler_id} 自然通过身份校验可操作、
     *     原处理人失效（{@link #requireProcessingWorkitem} 的 FORBIDDEN）；</li>
     * <li>留痕：{@link SysProcessConstants#AUDIT_TYPE_ADMIN_CHANGE_HANDLER ADMIN_CHANGE_HANDLER}
     *     一条（workitemId=被换工作项 id，handlerId=管理员，content=操作意见）。</li>
     * </ul>
     *
     * @param example    流程实例（非空）
     * @param operatorId 管理员用户 id（留痕 handlerId）
     * @param workitemId 待换人的工作项 id（必填，须为本实例 PROCESSING 工作项）
     * @param changeToId 更换后的目标处理人用户 id（必填，须存在，允许禁用）
     * @param content    操作意见（非空，由 {@link #adminOperate(SysProcessAdminOperateRequest)} 统一校验）
     */
    private void adminChangeHandler(SysProcessExample example, String operatorId,
                                    String workitemId, String changeToId, String content) {
        if (!EXAMPLE_STATUS_PROCESSING.equals(example.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：仅审批中的流程可更换审批人");
        }
        SysProcessExampleWorkitem workitem = workitemDao.selectById(requireText(workitemId, "workitemId"));
        if (null == workitem || !example.getFieldId().equals(workitem.getFieldExampleId())) {
            throw new IllegalArgumentException("工作项不存在");
        }
        if (!WORKITEM_STATUS_PROCESSING.equals(workitem.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：仅处理中的工作项可更换处理人");
        }
        requireUserTargetsExist(List.of(changeToId.trim()), "更换审批人目标用户");
        // 仅替换处理人与期望处理人（仅本实例有效，工作项仍是原序位；不改节点/流程状态）
        workitem.setFieldHandlerId(changeToId.trim());
        workitem.setFieldExpectHandlerId(changeToId.trim());
        workitemDao.update(workitem);
        addAudit(example.getFieldId(), workitem.getFieldId(), operatorId,
                AUDIT_TYPE_ADMIN_CHANGE_HANDLER, content);
        log.info("process workitem handler changed by admin: example={}, workitem={}, changeTo={}",
                example.getFieldId(), workitem.getFieldId(), changeToId.trim());
    }

    /**
     * 流程作废（起草人废弃 / 管理员作废共用）：仅 PROCESSING / RETURN / DRAFT / PAUSE 状态可作废；
     * 工作项废弃，流程转 ABANDON，节点转入结束节点。
     */
    private void abandonExample(SysProcessExample example, String operatorId, String handleType, String content) {
        String status = example.getFieldStatus();
        if (!EXAMPLE_STATUS_PROCESSING.equals(status)
                && !EXAMPLE_STATUS_RETURN.equals(status)
                && !EXAMPLE_STATUS_DRAFT.equals(status)
                && !EXAMPLE_STATUS_PAUSE.equals(status)) {
            throw new IllegalArgumentException("仅审批中、驳回、草稿或暂停状态的流程可作废");
        }
        addAudit(example.getFieldId(), null, operatorId, handleType, trimToNull(content));
        abandonActiveWorkitems(example.getFieldId());
        moveToEndNode(example, EXAMPLE_STATUS_ABANDON);
    }

    /**
     * 节点转入结束节点：结束节点为当前节点（PROCESSING），流程置终态并记录完成时间。
     */
    private void moveToEndNode(SysProcessExample example, String finishStatus) {
        SysProcessExampleNode endNode = nodeDao.selectByExampleAndType(example.getFieldId(), NODE_TYPE_END);
        if (null != endNode) {
            endNode.setFieldStatus(NODE_STATUS_PROCESSING);
            nodeDao.update(endNode);
        }
        example.setFieldStatus(finishStatus);
        example.setFieldFinishTime(new Date());
        exampleDao.update(example);
    }

    /* ==================== 办结待阅发送（P17-BE-014 通知收口） ==================== */

    /**
     * 办结待阅发送（P17-BE-014 通知收口，Service 侧）：管理员终审通过（ADMIN_FINISH）不经引擎
     * {@code enterEnd}，办结（FINISH 终态）后由本方法以与引擎同口径发送——
     * <ul>
     * <li>起草人待阅（notify_finish_drafter_subject）：收件人 = 实例起草人（办结为系统事件，
     *     不排除任何人）；不受公共配置控制，始终发送；</li>
     * <li>审批人待阅（notify_finish_handler_subject）：收件人 = {@link #finishedHandlerIds}
     *     （本实例「实际审批过」的用户），受公共配置 {@code finish_notify_handler}（缺省 true）
     *     控制，false 时不发送。</li>
     * </ul>
     * ABANDON 终态（管理员作废 / 起草人废弃同样走 {@link #moveToEndNode}）不发办结待阅，
     * 故本方法仅由 {@link #adminFinish} 调用，不在 {@code moveToEndNode} 内触发。
     */
    private void notifyFinishReads(SysProcessExample example) {
        if (StringUtil.isNotEmpty(example.getFieldCreatorId())) {
            notifySender.sendFinishDrafterRead(example, List.of(example.getFieldCreatorId()));
        }
        Boolean notifyHandler = settingService.getBooleanValue(SETTING_TYPE_PROCESS,
                SETTING_PROCESS_FINISH_NOTIFY_HANDLER);
        if (null == notifyHandler || notifyHandler) {
            notifySender.sendFinishHandlerRead(example, finishedHandlerIds(example));
        }
    }

    /**
     * 本实例「实际审批过」的用户（去重保序，与引擎 enterEnd 办结待阅同口径）：
     * HANDLER_PASS 审批记录关联工作项的处理人（expect_handler_id 优先，回退 handler_id）；
     * 自动跳过者留痕为 AUTO_PASS，无通过留痕，不在收件人。
     */
    private List<String> finishedHandlerIds(SysProcessExample example) {
        Map<String, SysProcessExampleWorkitem> workitemById = new HashMap<>();
        List<SysProcessExampleWorkitem> workitems = workitemDao.selectListByExampleId(example.getFieldId());
        if (null != workitems) {
            for (SysProcessExampleWorkitem workitem : workitems) {
                workitemById.put(workitem.getFieldId(), workitem);
            }
        }
        Set<String> handlers = new LinkedHashSet<>();
        List<SysProcessAuditNote> notes = auditNoteDao.selectListByExampleId(example.getFieldId());
        if (null == notes) {
            return List.of();
        }
        for (SysProcessAuditNote note : notes) {
            if (!AUDIT_TYPE_HANDLER_PASS.equals(note.getFieldHandleType())
                    || StringUtil.isEmpty(note.getFieldWorkitemId())) {
                continue;
            }
            String handlerId = resolveWorkitemHandlerId(workitemById.get(note.getFieldWorkitemId()));
            if (StringUtil.isNotEmpty(handlerId)) {
                handlers.add(handlerId);
            }
        }
        return new ArrayList<>(handlers);
    }

    /** 工作项处理人取值（expect_handler_id 优先，回退 handler_id；皆空返回 null）。 */
    private static String resolveWorkitemHandlerId(SysProcessExampleWorkitem workitem) {
        if (null == workitem) {
            return null;
        }
        if (StringUtil.isNotEmpty(workitem.getFieldExpectHandlerId())) {
            return workitem.getFieldExpectHandlerId();
        }
        return StringUtil.isEmpty(workitem.getFieldHandlerId()) ? null : workitem.getFieldHandlerId();
    }

    /* ==================== 私有工具方法 ==================== */

    /**
     * 审批节点是否已通过（非串行）：单签任一通过即通过；会审需全部工作项完成
     * （ABANDON（转办弃置）工作项不阻塞通过判定，P17-BE-010）。串行不在此判定：
     * 通过后交引擎推进下一序位（见 {@link #pass}）。
     */
    private boolean isNodePassed(SysProcessExampleNode node, String passedWorkitemId) {
        SysProcessTemplateNodeConfigDo config = parseNodeConfig(node.getFieldNodeConfig());
        String approveMode = null == config || StringUtil.isEmpty(config.getApproveMode())
                ? APPROVE_MODE_SINGLE : config.getApproveMode();
        if (APPROVE_MODE_SERIAL.equals(approveMode)) {
            return false;
        }
        if (APPROVE_MODE_COUNTERSIGN.equals(approveMode)) {
            for (SysProcessExampleWorkitem workitem : workitemDao.selectListByNodeId(node.getFieldId())) {
                if (!WORKITEM_STATUS_FINISH.equals(workitem.getFieldStatus())
                        && !WORKITEM_STATUS_ABANDON.equals(workitem.getFieldStatus())
                        && !passedWorkitemId.equals(workitem.getFieldId())) {
                    return false;
                }
            }
            return true;
        }
        return true;
    }

    /** 审批节点是否串行模式（{@code approveMode=serial}）。 */
    private boolean isSerialApproveNode(SysProcessExampleNode node) {
        SysProcessTemplateNodeConfigDo config = parseNodeConfig(node.getFieldNodeConfig());
        return null != config && APPROVE_MODE_SERIAL.equals(config.getApproveMode());
    }

    /** 将该节点其他（未完成）工作项置为完成（ABANDON（转办弃置）工作项保持终态不复活）。 */
    private void finishRemainingWorkitems(String nodeId, String exceptWorkitemId) {
        Date now = new Date();
        for (SysProcessExampleWorkitem workitem : workitemDao.selectListByNodeId(nodeId)) {
            if (!WORKITEM_STATUS_FINISH.equals(workitem.getFieldStatus())
                    && !WORKITEM_STATUS_ABANDON.equals(workitem.getFieldStatus())
                    && !exceptWorkitemId.equals(workitem.getFieldId())) {
                workitem.setFieldStatus(WORKITEM_STATUS_FINISH);
                workitem.setFieldFinishTime(now);
                workitemDao.update(workitem);
            }
        }
    }

    /** 完成单个工作项（置 FINISH 并记录完成时间）。 */
    private void finishWorkitem(SysProcessExampleWorkitem workitem) {
        workitem.setFieldStatus(WORKITEM_STATUS_FINISH);
        workitem.setFieldFinishTime(new Date());
        workitemDao.update(workitem);
    }

    /** 废弃处理中（PROCESSING / PAUSE）的工作项（收回/作废/跳转时） */
    private void abandonActiveWorkitems(String exampleId) {
        flowSupport.abandonActiveWorkitems(exampleId);
    }

    /** 完成处理中（PROCESSING / PAUSE）的工作项（管理员办结时） */
    private void finishActiveWorkitems(String exampleId) {
        flowSupport.finishActiveWorkitems(exampleId);
    }

    /** 生成审批记录。 */
    private void addAudit(String exampleId, String workitemId, String handlerId,
                          String handleType, String content) {
        flowSupport.addAudit(exampleId, workitemId, handlerId, handleType, content);
    }

    /** 校验并返回流程实例（不存在抛 CS_PROCESS_EXAMPLE_NOT_FOUND 前缀）。 */
    private SysProcessExample requireExample(String exampleId) {
        String id = requireText(exampleId, "fieldId");
        SysProcessExample example = exampleDao.selectById(id);
        if (null == example) {
            throw new IllegalArgumentException(CS_PROCESS_EXAMPLE_NOT_FOUND + "：流程实例不存在或已删除");
        }
        return example;
    }

    /** 校验流程实例节点存在。 */
    private SysProcessExampleNode requireNode(String nodeId) {
        SysProcessExampleNode node = nodeDao.selectById(requireText(nodeId, "nodeId"));
        if (null == node) {
            throw new IllegalArgumentException("流程节点不存在");
        }
        return node;
    }

    /** 校验工作项存在且为当前处理人可处理（PROCESSING）状态。 */
    private SysProcessExampleWorkitem requireProcessingWorkitem(String exampleId, String workitemId) {
        SysProcessExampleWorkitem workitem = workitemDao.selectById(requireText(workitemId, "workitemId"));
        if (null == workitem || !exampleId.equals(workitem.getFieldExampleId())) {
            throw new IllegalArgumentException("工作项不存在");
        }
        if (!WORKITEM_STATUS_PROCESSING.equals(workitem.getFieldStatus())) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_STATE_CONFLICT + "：工作项已处理或不可处理");
        }
        SysOrgUser current = requireCurrentUser();
        if (!current.getFieldId().equals(workitem.getFieldHandlerId())) {
            throw new AuthorizationException("FORBIDDEN",
                    CS_PROCESS_OPERATE_FORBIDDEN + "：仅工作项处理人可执行审批操作");
        }
        return workitem;
    }

    /** 校验当前用户为流程创建人（起草人身份，身份不符抛 FORBIDDEN）。 */
    private void requireDrafter(SysProcessExample example) {
        SysOrgUser current = requireCurrentUser();
        if (!current.getFieldId().equals(example.getFieldCreatorId())) {
            throw new AuthorizationException("FORBIDDEN",
                    CS_PROCESS_OPERATE_FORBIDDEN + "：仅流程起草人可执行该操作");
        }
    }

    /**
     * 统一操作通道的必填参数校验：缺失抛 {@code CS_PROCESS_OPERATE_PARAM_MISSING：<参数名> 必填}。
     */
    private static String requireOperateParam(String value, String paramName) {
        if (StringUtil.isEmpty(value)) {
            throw new IllegalArgumentException(CS_PROCESS_OPERATE_PARAM_MISSING + "：" + paramName + " 必填");
        }
        return value.trim();
    }

    /**
     * 校验人工决策流向（D6，drafter_submit / handler_pass 携带 flowChoice 时）：
     * 来源节点（起草人提交=起草节点 / 审批人通过=被通过节点）必须是某人工决策节点
     * {@code flowDecisions} 的流向决策来源，且 flowChoice 是该决策节点实际出边目标键之一；
     * 不满足抛 {@code CS_PROCESS_NODE_TARGET_INVALID}。
     */
    private void requireFlowChoiceValid(String exampleId, String sourceNodeKey, String flowChoice) {
        String choice = flowChoice.trim();
        List<SysProcessExampleLine> lines = exampleLineDao.selectListByExampleId(exampleId);
        List<SysProcessExampleNode> nodes = nodeDao.selectListByExampleId(exampleId);
        if (null != lines && null != nodes) {
            // 决策节点出边目标键集合（按 fromKey 索引）
            Map<String, Set<String>> targetsByFromKey = new HashMap<>();
            for (SysProcessExampleLine line : lines) {
                targetsByFromKey.computeIfAbsent(line.getFieldFromKey(), key -> new LinkedHashSet<>())
                        .add(line.getFieldToKey());
            }
            for (SysProcessExampleNode node : nodes) {
                if (!NODE_TYPE_MANUAL.equals(node.getFieldNodeType())) {
                    continue;
                }
                SysProcessTemplateNodeConfigDo config = parseNodeConfig(node.getFieldNodeConfig());
                if (null == config || null == config.getFlowDecisions()
                        || !config.getFlowDecisions().contains(sourceNodeKey)) {
                    continue;
                }
                if (targetsByFromKey.getOrDefault(ProcessFlowGraph.nodeKey(node), Set.of()).contains(choice)) {
                    return;
                }
            }
        }
        throw new IllegalArgumentException(CS_PROCESS_NODE_TARGET_INVALID + "：人工决策流向 "
                + choice + " 不是人工决策节点的可流出方向");
    }

    private SysOrgUser requireCurrentUser() {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (null == user) {
            throw new IllegalStateException("登录已过期，请重新登录");
        }
        return user;
    }

    private String currentUserIdOrNull() {
        SysOrgUser user = UserUtil.getCurrentUser();
        return null == user ? null : user.getFieldId();
    }

    /* ==================== 审批面板按钮集计算（v1_7 升级为按钮对象数组，P17-BE-013） ==================== */

    /**
     * 当前用户对流程实例可执行的审批操作按钮集（面板展示预判，最终鉴权与身份校验仍由
     * 各接口 resolver 与操作时引擎兜底）。
     * <p>计算规则 = 节点 {@code operations} 配置 ∩ 用户身份 ∩ 状态前置（P17-BE-013，
     * 依据 docs/设计文档/sys/process/流程v1_7接口清单.md §5.4）：</p>
     * <ul>
     * <li>配置行身份按 operateType 前缀推导：drafter_*=起草人（user==creator）、
     *     handler_*=审批人（在当前 PROCESSING 节点持 PROCESSING 工作项）、
     *     processed_*=已处理人（本实例有 FINISH 工作项）；</li>
     * <li>配置行取自当前在途（PROCESSING）节点：审批人行取本人持工作项的节点、
     *     起草人/已处理人行取全部当前在途节点（按节点序去重保序）；</li>
     * <li>节点无配置（旧模板/空 operations）时按既有逻辑兜底：起草人收回（PROCESSING）/
     *     废弃（DRAFT/RETURN）、当前处理人通过/拒绝（默认名），保证旧模板面板不塌；</li>
     * <li>管理员按钮：准入判定沿用 {@code resolver.resolve(ACTION_PROCESS_EXAMPLE_ADMIN_OPERATE)}，
     *     按状态给出 JUMP/FINISH/ABANDON/PAUSE/RESUME，PROCESSING 下新增 CHANGE_HANDLER。</li>
     * </ul>
     * <p>按钮行 {@code rejectTargets}（仅 handler_reject）与 {@code flowChoices}
     * （仅 handler_pass / drafter_submit 且存在决策来源关系时）随按钮一并输出；
     * {@code nodeOps} 业务标识逻辑不在此计算（语义维持现状不变）。</p>
     */
    private List<SysProcessPanelOperationVo> computeOperations(SysProcessExample example) {
        List<SysProcessPanelOperationVo> operations = new ArrayList<>();
        String currentUserId = currentUserIdOrNull();
        if (null == currentUserId) {
            return operations;
        }
        String status = example.getFieldStatus();
        List<SysProcessExampleNode> nodes = nodeDao.selectListByExampleId(example.getFieldId());
        List<SysProcessExampleWorkitem> myWorkitems =
                workitemDao.selectByExampleAndHandler(example.getFieldId(), currentUserId);
        boolean drafter = currentUserId.equals(example.getFieldCreatorId());
        boolean processed = null != myWorkitems && myWorkitems.stream()
                .anyMatch(w -> WORKITEM_STATUS_FINISH.equals(w.getFieldStatus()));
        // 当前在途节点（流程审批中才有）与本人持 PROCESSING 工作项的节点（审批人身份落点）
        List<SysProcessExampleNode> currentNodes = new ArrayList<>();
        if (null != nodes && EXAMPLE_STATUS_PROCESSING.equals(status)) {
            for (SysProcessExampleNode node : nodes) {
                if (NODE_STATUS_PROCESSING.equals(node.getFieldStatus())) {
                    currentNodes.add(node);
                }
            }
        }
        SysProcessExampleNode handlerNode = resolveHandlerNode(currentNodes, myWorkitems);
        SysProcessExampleNode fallbackHandlerNode =
                null == handlerNode ? resolveHandlerNode(nodes, myWorkitems) : handlerNode;

        // 节点 operations 配置行（v1_7 模板）：无任何配置（旧模板/空 operations）时按既有逻辑兜底
        List<SysProcessTemplateNodeConfigDo.OperationDetail> configRows =
                collectConfiguredOperations(currentNodes, handlerNode);
        if (configRows.isEmpty()) {
            // 兜底（既有逻辑）：起草人收回/废弃、当前处理人通过/拒绝
            if (drafter) {
                if (EXAMPLE_STATUS_PROCESSING.equals(status)) {
                    operations.add(fallbackPanelOperation(SysProcessConstants.OPERATE_TYPE_DRAFTER_RETRACT,
                            example, fallbackHandlerNode));
                } else if (EXAMPLE_STATUS_DRAFT.equals(status) || EXAMPLE_STATUS_RETURN.equals(status)) {
                    operations.add(fallbackPanelOperation(SysProcessConstants.OPERATE_TYPE_DRAFTER_CANCEL,
                            example, fallbackHandlerNode));
                }
            }
            if (EXAMPLE_STATUS_PROCESSING.equals(status) && null != myWorkitems && myWorkitems.stream()
                    .anyMatch(w -> WORKITEM_STATUS_PROCESSING.equals(w.getFieldStatus()))) {
                operations.add(fallbackPanelOperation(SysProcessConstants.OPERATE_TYPE_HANDLER_PASS,
                        example, fallbackHandlerNode));
                operations.add(fallbackPanelOperation(SysProcessConstants.OPERATE_TYPE_HANDLER_REJECT,
                        example, fallbackHandlerNode));
            }
        } else {
            for (SysProcessTemplateNodeConfigDo.OperationDetail row : configRows) {
                String operateType = trimToNull(row.getOperateType());
                if (null == operateType
                        || !matchesIdentity(operateType, drafter, null != handlerNode, processed)
                        || !stateAllows(operateType, status)) {
                    continue;
                }
                operations.add(configuredPanelOperation(example, row, handlerNode));
            }
        }

        // 管理员按钮：准入判定沿用 resolver.resolve(ACTION_PROCESS_EXAMPLE_ADMIN_OPERATE)，按状态 switch
        if (resolver.resolve(new AuthorizationContext(ACTION_PROCESS_EXAMPLE_ADMIN_OPERATE))) {
            switch (status) {
                case EXAMPLE_STATUS_PROCESSING -> {
                    operations.add(adminPanelOperation(ADMIN_OPERATE_JUMP));
                    operations.add(adminPanelOperation(ADMIN_OPERATE_FINISH));
                    operations.add(adminPanelOperation(ADMIN_OPERATE_ABANDON));
                    operations.add(adminPanelOperation(ADMIN_OPERATE_PAUSE));
                    operations.add(adminPanelOperation(ADMIN_OPERATE_CHANGE_HANDLER));
                }
                case EXAMPLE_STATUS_RETURN -> {
                    operations.add(adminPanelOperation(ADMIN_OPERATE_JUMP));
                    operations.add(adminPanelOperation(ADMIN_OPERATE_FINISH));
                    operations.add(adminPanelOperation(ADMIN_OPERATE_ABANDON));
                }
                case EXAMPLE_STATUS_DRAFT -> operations.add(adminPanelOperation(ADMIN_OPERATE_ABANDON));
                case EXAMPLE_STATUS_PAUSE -> {
                    operations.add(adminPanelOperation(ADMIN_OPERATE_FINISH));
                    operations.add(adminPanelOperation(ADMIN_OPERATE_ABANDON));
                    operations.add(adminPanelOperation(ADMIN_OPERATE_RESUME));
                }
                default -> {
                }
            }
        }
        return operations;
    }

    /**
     * 解析当前处理人身份落点节点：本人 {@code PROCESSING} 工作项所在节点，按候选节点序取首个命中。
     *
     * @param candidates  候选节点（配置行口径传当前在途节点——严格按「当前 PROCESSING 节点持
     *                    PROCESSING 工作项」判定审批人身份；兜底口径传实例节点全集）
     * @param myWorkitems 当前用户在本实例的工作项
     * @return 命中节点；无命中返回 null
     */
    private SysProcessExampleNode resolveHandlerNode(List<SysProcessExampleNode> candidates,
                                                     List<SysProcessExampleWorkitem> myWorkitems) {
        if (null == candidates || null == myWorkitems) {
            return null;
        }
        for (SysProcessExampleNode node : candidates) {
            for (SysProcessExampleWorkitem workitem : myWorkitems) {
                if (WORKITEM_STATUS_PROCESSING.equals(workitem.getFieldStatus())
                        && node.getFieldId().equals(workitem.getFieldNodeId())) {
                    return node;
                }
            }
        }
        return null;
    }

    /**
     * 收集当前在途节点的 {@code operations} 配置行（按节点序去重保序）：审批人行（handler_ 前缀）
     * 取本人持 PROCESSING 工作项的节点，起草人/已处理人行（drafter_ / processed_ 前缀）取全部当前在途节点。
     */
    private List<SysProcessTemplateNodeConfigDo.OperationDetail> collectConfiguredOperations(
            List<SysProcessExampleNode> currentNodes, SysProcessExampleNode handlerNode) {
        Set<String> seen = new HashSet<>();
        List<SysProcessTemplateNodeConfigDo.OperationDetail> rows = new ArrayList<>();
        List<SysProcessExampleNode> sources = new ArrayList<>();
        if (null != handlerNode) {
            sources.add(handlerNode);
        }
        if (null != currentNodes) {
            for (SysProcessExampleNode node : currentNodes) {
                if (!sources.contains(node)) {
                    sources.add(node);
                }
            }
        }
        for (SysProcessExampleNode node : sources) {
            SysProcessTemplateNodeConfigDo config = parseNodeConfig(node.getFieldNodeConfig());
            if (null == config || null == config.getOperations()) {
                continue;
            }
            for (SysProcessTemplateNodeConfigDo.OperationDetail row : config.getOperations()) {
                String operateType = trimToNull(row.getOperateType());
                if (null == operateType || !seen.add(operateType)) {
                    continue;
                }
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * 配置行身份判定（按 operateType 前缀推导）：drafter_*=起草人（user==creator）、
     * handler_*=当前审批人（当前 PROCESSING 节点持 PROCESSING 工作项）、
     * processed_*=已处理人（本实例有 FINISH 工作项）；其余前缀（admin_* 等）不按节点配置出按钮。
     */
    private static boolean matchesIdentity(String operateType, boolean drafter, boolean handler, boolean processed) {
        if (operateType.startsWith("drafter_")) {
            return drafter;
        }
        if (operateType.startsWith("handler_")) {
            return handler;
        }
        if (operateType.startsWith("processed_")) {
            return processed;
        }
        return false;
    }

    /**
     * 配置行状态前置判定（操作矩阵 §6.2）：drafter_submit / drafter_cancel 限 DRAFT / RETURN，
     * 其余 11 类配置行（drafter_retract / drafter_expedite / handler_* / processed_*）限 PROCESSING；
     * 未登记类型一律不出按钮。
     */
    private static boolean stateAllows(String operateType, String status) {
        if (SysProcessConstants.OPERATE_TYPE_DRAFTER_SUBMIT.equals(operateType)
                || SysProcessConstants.OPERATE_TYPE_DRAFTER_CANCEL.equals(operateType)) {
            return EXAMPLE_STATUS_DRAFT.equals(status) || EXAMPLE_STATUS_RETURN.equals(status);
        }
        if (SysProcessConstants.OPERATE_TYPE_DRAFTER_RETRACT.equals(operateType)
                || SysProcessConstants.OPERATE_TYPE_DRAFTER_EXPEDITE.equals(operateType)
                || SysProcessConstants.OPERATE_TYPE_HANDLER_PASS.equals(operateType)
                || SysProcessConstants.OPERATE_TYPE_HANDLER_REJECT.equals(operateType)
                || SysProcessConstants.OPERATE_TYPE_HANDLER_CC.equals(operateType)
                || SysProcessConstants.OPERATE_TYPE_HANDLER_TRANSFER.equals(operateType)
                || SysProcessConstants.OPERATE_TYPE_HANDLER_TRANSFER_RETRACT.equals(operateType)
                || SysProcessConstants.OPERATE_TYPE_PROCESSED_RETRACT.equals(operateType)
                || SysProcessConstants.OPERATE_TYPE_PROCESSED_EXPEDITE.equals(operateType)) {
            return EXAMPLE_STATUS_PROCESSING.equals(status);
        }
        return false;
    }

    /**
     * 旧模板兜底按钮行（默认名；handler_reject / handler_pass 附带候选数据，
     * handlerNode 为本人持 PROCESSING 工作项所在节点）。
     */
    private SysProcessPanelOperationVo fallbackPanelOperation(String operateType, SysProcessExample example,
                                                              SysProcessExampleNode handlerNode) {
        SysProcessPanelOperationVo vo = new SysProcessPanelOperationVo();
        vo.setOperateType(operateType);
        vo.setOperateName(SysProcessPanelOperationVo.defaultOperateName(operateType));
        enrichPanelOperation(vo, example, handlerNode);
        return vo;
    }

    /**
     * 节点配置按钮行：操作名缺省取操作类型中文名，默认审批意见按节点配置回显；
     * handler_reject / handler_pass / drafter_submit 附带候选数据。
     */
    private SysProcessPanelOperationVo configuredPanelOperation(SysProcessExample example,
            SysProcessTemplateNodeConfigDo.OperationDetail row, SysProcessExampleNode handlerNode) {
        String operateType = trimToNull(row.getOperateType());
        SysProcessPanelOperationVo vo = new SysProcessPanelOperationVo();
        vo.setOperateType(operateType);
        String operateName = trimToNull(row.getOperateName());
        vo.setOperateName(null != operateName ? operateName
                : SysProcessPanelOperationVo.defaultOperateName(operateType));
        vo.setDefaultContent(trimToNull(row.getDefaultContent()));
        enrichPanelOperation(vo, example, handlerNode);
        return vo;
    }

    /**
     * 补充按钮候选数据：handler_reject 附驳回候选（仅本人持 PROCESSING 工作项时，候选来源=该节点）；
     * handler_pass 附人工决策流向候选（决策来源=被通过节点）；drafter_submit 附人工决策流向候选
     * （决策来源=起草节点）。
     */
    private void enrichPanelOperation(SysProcessPanelOperationVo vo, SysProcessExample example,
                                      SysProcessExampleNode handlerNode) {
        String operateType = vo.getOperateType();
        if (SysProcessConstants.OPERATE_TYPE_HANDLER_REJECT.equals(operateType)) {
            vo.setRejectTargets(null == handlerNode
                    ? new ArrayList<>() : resolveRejectTargetOptions(example, handlerNode));
        }
        if (SysProcessConstants.OPERATE_TYPE_HANDLER_PASS.equals(operateType) && null != handlerNode) {
            vo.setFlowChoices(resolveFlowChoiceOptions(example, ProcessFlowGraph.nodeKey(handlerNode)));
        }
        if (SysProcessConstants.OPERATE_TYPE_DRAFTER_SUBMIT.equals(operateType)) {
            SysProcessExampleNode draftNode = nodeDao.selectByExampleAndType(example.getFieldId(), NODE_TYPE_DRAFT);
            vo.setFlowChoices(resolveFlowChoiceOptions(example, null == draftNode
                    ? SysProcessConstants.FIXED_NODE_KEY_DRAFT : ProcessFlowGraph.nodeKey(draftNode)));
        }
    }

    /**
     * 解析驳回候选节点（面板 handler_reject 按钮）：复用 P17-BE-009 允许目标口径
     * （节点配置 {@code rejectTargets} ∩ 已流经节点，空默认起草节点，见
     * {@link #resolveAllowedRejectTargetKeys}），输出候选节点 key+名称（按实例节点序）。
     */
    private List<SysProcessNodeOptionVo> resolveRejectTargetOptions(SysProcessExample example,
                                                                    SysProcessExampleNode rejectNode) {
        Set<String> allowed = resolveAllowedRejectTargetKeys(example, rejectNode);
        List<SysProcessNodeOptionVo> options = new ArrayList<>();
        List<SysProcessExampleNode> nodes = nodeDao.selectListByExampleId(example.getFieldId());
        if (null == nodes) {
            return options;
        }
        for (SysProcessExampleNode node : nodes) {
            String key = ProcessFlowGraph.nodeKey(node);
            if (allowed.contains(key)) {
                options.add(nodeOption(key, node.getFieldNodeName()));
            }
        }
        return options;
    }

    /**
     * 解析人工决策流向候选（面板 handler_pass / drafter_submit 按钮，D6）：遍历人工决策（MANUAL）
     * 节点，其 {@code flowDecisions} 含来源节点键（handler_pass=被通过节点键；drafter_submit=
     * 起草节点键）时，输出该决策节点全部出边目标（nodeKey+nodeName）。
     */
    private List<SysProcessNodeOptionVo> resolveFlowChoiceOptions(SysProcessExample example, String sourceNodeKey) {
        List<SysProcessNodeOptionVo> options = new ArrayList<>();
        List<SysProcessExampleLine> lines = exampleLineDao.selectListByExampleId(example.getFieldId());
        List<SysProcessExampleNode> nodes = nodeDao.selectListByExampleId(example.getFieldId());
        if (null == lines || null == nodes || StringUtil.isEmpty(sourceNodeKey)) {
            return options;
        }
        Map<String, String> nodeNames = new HashMap<>();
        for (SysProcessExampleNode node : nodes) {
            nodeNames.put(ProcessFlowGraph.nodeKey(node), node.getFieldNodeName());
        }
        for (SysProcessExampleNode node : nodes) {
            if (!NODE_TYPE_MANUAL.equals(node.getFieldNodeType())) {
                continue;
            }
            SysProcessTemplateNodeConfigDo config = parseNodeConfig(node.getFieldNodeConfig());
            if (null == config || null == config.getFlowDecisions()
                    || !config.getFlowDecisions().contains(sourceNodeKey)) {
                continue;
            }
            for (SysProcessExampleLine line : lines) {
                if (!ProcessFlowGraph.nodeKey(node).equals(line.getFieldFromKey())) {
                    continue;
                }
                String targetKey = trimToNull(line.getFieldToKey());
                if (null != targetKey) {
                    options.add(nodeOption(targetKey, nodeNames.get(targetKey)));
                }
            }
        }
        return options;
    }

    /** 节点候选行（nodeKey + nodeName）。 */
    private static SysProcessNodeOptionVo nodeOption(String nodeKey, String nodeName) {
        SysProcessNodeOptionVo option = new SysProcessNodeOptionVo();
        option.setNodeKey(nodeKey);
        option.setNodeName(nodeName);
        return option;
    }

    /** 管理员按钮行（大写特权操作标识，默认中文名）。 */
    private static SysProcessPanelOperationVo adminPanelOperation(String operateType) {
        SysProcessPanelOperationVo vo = new SysProcessPanelOperationVo();
        vo.setOperateType(operateType);
        vo.setOperateName(SysProcessPanelOperationVo.defaultOperateName(operateType));
        return vo;
    }

    /* ==================== VO 转换 ==================== */

    private SysProcessExampleVo toExampleVo(SysProcessExample example) {
        SysProcessExampleVo vo = new SysProcessExampleVo();
        vo.setFieldId(example.getFieldId());
        vo.setFieldTemplateId(example.getFieldTemplateId());
        vo.setFieldTemplateVersion(example.getFieldTemplateVersion());
        vo.setFieldModelId(example.getFieldModelId());
        vo.setFieldModelName(example.getFieldModelName());
        vo.setFieldCode(example.getFieldCode());
        vo.setFieldCreatorId(example.getFieldCreatorId());
        vo.setFieldCreatorName(resolveUserName(example.getFieldCreatorId()));
        vo.setFieldCreateTime(example.getFieldCreateTime());
        vo.setFieldFinishTime(example.getFieldFinishTime());
        vo.setFieldStatus(example.getFieldStatus());
        List<SysProcessExampleNode> processingNodes =
                nodeDao.selectByExampleAndStatus(example.getFieldId(), NODE_STATUS_PROCESSING);
        if (null != processingNodes && !processingNodes.isEmpty()) {
            vo.setCurrentNodeName(processingNodes.get(0).getFieldNodeName());
        }
        SysProcessTemplate template = templateDao.selectById(example.getFieldTemplateId());
        if (null != template) {
            vo.setFieldName(template.getFieldName());
        }
        return vo;
    }

    private List<SysProcessExampleNodeVo> toNodeVos(List<SysProcessExampleNode> nodes) {
        if (null == nodes) {
            return List.of();
        }
        return nodes.stream().map(node -> {
            SysProcessExampleNodeVo vo = new SysProcessExampleNodeVo();
            vo.setFieldId(node.getFieldId());
            vo.setFieldNodeNo(node.getFieldNodeNo());
            vo.setFieldNodeName(node.getFieldNodeName());
            vo.setFieldNodeType(node.getFieldNodeType());
            vo.setFieldStatus(node.getFieldStatus());
            vo.setFieldNodeKey(ProcessFlowGraph.nodeKey(node));
            return vo;
        }).toList();
    }

    private List<SysProcessExampleWorkitemVo> toWorkitemVos(List<SysProcessExampleWorkitem> workitems) {
        if (null == workitems) {
            return List.of();
        }
        Map<String, String> nodeNames = new HashMap<>();
        Map<String, String> userNames = new HashMap<>();
        return workitems.stream().map(workitem -> {
            SysProcessExampleWorkitemVo vo = new SysProcessExampleWorkitemVo();
            vo.setFieldId(workitem.getFieldId());
            vo.setFieldNodeId(workitem.getFieldNodeId());
            vo.setFieldNodeName(nodeNames.computeIfAbsent(workitem.getFieldNodeId(),
                    id -> loadNodeName(id)));
            vo.setFieldHandlerId(workitem.getFieldHandlerId());
            vo.setFieldHandlerName(userNames.computeIfAbsent(workitem.getFieldHandlerId(),
                    id -> resolveUserName(id)));
            vo.setFieldExpectHandlerId(workitem.getFieldExpectHandlerId());
            vo.setFieldExpectHandlerName(userNames.computeIfAbsent(workitem.getFieldExpectHandlerId(),
                    id -> resolveUserName(id)));
            vo.setFieldCreateTime(workitem.getFieldCreateTime());
            vo.setFieldFinishTime(workitem.getFieldFinishTime());
            vo.setFieldStatus(workitem.getFieldStatus());
            vo.setResumeMode(parseResumeMode(workitem.getFieldConfig()));
            return vo;
        }).toList();
    }

    private List<SysProcessAuditNoteVo> toAuditNoteVos(List<SysProcessAuditNote> notes) {
        if (null == notes) {
            return List.of();
        }
        Map<String, String> userNames = new HashMap<>();
        return notes.stream().map(note -> {
            SysProcessAuditNoteVo vo = new SysProcessAuditNoteVo();
            vo.setFieldId(note.getFieldId());
            vo.setFieldWorkitemId(note.getFieldWorkitemId());
            vo.setFieldHandlerId(note.getFieldHandlerId());
            vo.setFieldHandlerName(userNames.computeIfAbsent(note.getFieldHandlerId(),
                    id -> resolveUserName(id)));
            vo.setFieldContent(note.getFieldContent());
            vo.setFieldCreateTime(note.getFieldCreateTime());
            vo.setFieldHandleType(note.getFieldHandleType());
            return vo;
        }).toList();
    }

    private String loadNodeName(String nodeId) {
        SysProcessExampleNode node = nodeDao.selectById(nodeId);
        return null == node ? null : node.getFieldNodeName();
    }

    private String resolveUserName(String userId) {
        if (StringUtil.isEmpty(userId)) {
            return null;
        }
        SysOrgUser user = userMapper.selectById(userId);
        return null == user ? null : user.getFieldName();
    }

    private String parseResumeMode(String configJson) {
        if (StringUtil.isEmpty(configJson)) {
            return null;
        }
        try {
            SysProcessExampleWorkitemConfigDo config =
                    objectMapper.readValue(configJson, SysProcessExampleWorkitemConfigDo.class);
            return null == config ? null : config.getResumeMode();
        } catch (Exception e) {
            return null;
        }
    }

    /** 节点配置 JSON 解析为配置对象（空或解析失败返回 null）。 */
    private SysProcessTemplateNodeConfigDo parseNodeConfig(String json) {
        if (StringUtil.isEmpty(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SysProcessTemplateNodeConfigDo.class);
        } catch (Exception e) {
            log.warn("parse node config failed, fallback to null: {}", e.getMessage());
            return null;
        }
    }

    /** 解析审批人 id 列表（';' 分隔，去重保序）。 */
    private static Set<String> parseHandlerIds(String handlerIds) {
        Set<String> handlers = new LinkedHashSet<>();
        if (StringUtil.isEmpty(handlerIds)) {
            return handlers;
        }
        for (String id : handlerIds.split(";")) {
            if (StringUtil.isNotEmpty(id)) {
                handlers.add(id.trim());
            }
        }
        return handlers;
    }

    /** 读取布尔公共配置（缺失时返回默认值）。 */
    private boolean boolSetting(String key, boolean defaultValue) {
        Boolean value = settingService.getBooleanValue(SysProcessConstants.SETTING_TYPE_PROCESS, key);
        return null == value ? defaultValue : value;
    }

    /** 生成流程编号：P + 时间戳 + 随机数。 */
    private static String generateCode() {
        return "P" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("serialize config failed", e);
            throw new IllegalStateException("配置序列化失败");
        }
    }

    private static String trimToNull(String value) {
        return StringUtil.isEmpty(value) ? null : value.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (StringUtil.isEmpty(value)) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }

    private static Pages normalizePages(Pages pages) {
        Pages safe = null == pages ? new Pages() : pages;
        if (safe.getPageNo() < 1) {
            safe.setPageNo(1);
        }
        if (safe.getPageSize() < 1) {
            safe.setPageSize(10);
        }
        return safe;
    }

    private static String normalizeSortField(String sortField) {
        return "createTime".equals(sortField) ? sortField : null;
    }

    private static String normalizeSortOrder(String sortOrder) {
        return "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
    }

    /** 上一有效节点信息：审批人集合与被自动跳过的处理人集合。 */
    private static final class PreviousNode {
        private final Set<String> handlers = new HashSet<>();
        private final Set<String> skipped = new HashSet<>();
    }
}
