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
import com.eric.base.sys.process.response.SysProcessAuditNoteVo;
import com.eric.base.sys.process.response.SysProcessExampleDetailVo;
import com.eric.base.sys.process.response.SysProcessExampleNodeVo;
import com.eric.base.sys.process.response.SysProcessExamplePanelVo;
import com.eric.base.sys.process.response.SysProcessExampleVo;
import com.eric.base.sys.process.response.SysProcessExampleWorkitemVo;
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
import static com.eric.base.sys.process.properties.SysProcessConstants.ADMIN_OPERATE_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.ADMIN_OPERATE_JUMP;
import static com.eric.base.sys.process.properties.SysProcessConstants.ADMIN_OPERATE_PAUSE;
import static com.eric.base.sys.process.properties.SysProcessConstants.ADMIN_OPERATE_RESUME;
import static com.eric.base.sys.process.properties.SysProcessConstants.APPROVE_MODE_COUNTERSIGN;
import static com.eric.base.sys.process.properties.SysProcessConstants.APPROVE_MODE_SINGLE;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_ADMIN_ABANDON;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_ADMIN_FINISH;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_ADMIN_JUMP;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_ADMIN_PAUSE;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_ADMIN_RESUME;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_AUTO_PASS;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_DRAFTER_ABANDON;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_DRAFTER_BACK;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_DRAFTER_SUBMIT;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_HANDLER_PASS;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_HANDLER_REJECT;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUDIT_TYPE_HANDLER_RETURN;
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
 *              <li>审批人解析：按节点「审批来源」解析——组织架构直接取配置审批人，表单字段读取文档实体
 *                  对应字段值解析出组织架构（岗位/用户）作为审批人，结果为空时跳过节点；</li>
 *              <li>自动跳过：按公共配置（auto_handle_pass / auto_handle_pass_drafter / auto_continue_pass）
 *                  与节点配置（autoSkip / autoContinueSkip）跳过上一有效节点的重复审批人/起草人，
 *                  生成办结的工作项与 AUTO_PASS 审批记录，实际处理人为空时跳过节点；</li>
 *              <li>管理员特权操作：跳转/终审通过/作废/暂停/恢复（生成 ADMIN_* 审批记录）；</li>
 *              <li>列表/详情/审批面板：流程实例分页查询、实例+节点+工作项+审批记录详情、
 *                  可嵌入审批组件的面板数据。</li>
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
        log.info("process example rejected: example={}, node={}, rejectTargetKey={}, resumeMode={}",
                example.getFieldId(), node.getFieldNodeName(), targetKey, resumeMode);
    }

    /**
     * 驳回目标校验（P17-BE-009，依据 docs/设计文档/sys/process/流程核心业务.md「驳回与重新提交（D3）」）：
     * 允许目标 = 驳回节点配置 {@code rejectTargets} ∩ 已流经节点（配置为空或交集为空时默认仅起草节点）。
     * 已流经节点运行期判定口径：起草节点恒视为已流经；审批节点状态 FINISH / REJECT 视为已流经
     * （实际审批过或曾被驳回）；PASS（自动跳过）与 WAITING / PROCESSING 不算。
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
        Set<String> traversedKeys = new LinkedHashSet<>();
        if (null != nodes) {
            for (SysProcessExampleNode node : nodes) {
                String key = ProcessFlowGraph.nodeKey(node);
                if (key.equals(targetKey)) {
                    target = node;
                }
                if (isTraversedNode(node)) {
                    traversedKeys.add(key);
                }
            }
        }
        if (null == target) {
            throw new IllegalArgumentException(CS_PROCESS_NODE_TARGET_INVALID
                    + "：驳回目标节点 " + targetKey + " 不是本流程实例的节点");
        }
        // 允许目标集合 = rejectTargets ∩ 已流经节点；配置为空（或交集为空）默认仅起草节点
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
        if (!allowed.contains(targetKey)) {
            throw new IllegalArgumentException(CS_PROCESS_NODE_TARGET_INVALID + "：驳回目标「"
                    + (StringUtil.isEmpty(target.getFieldNodeName()) ? targetKey : target.getFieldNodeName())
                    + "」不在允许的驳回目标范围内（允许目标 = 节点配置 rejectTargets ∩ 已流经节点）");
        }
        return target;
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

    /* ==================== 管理员特权操作 ==================== */

    /**
     * 管理员特权操作：跳转 / 终审通过 / 作废 / 暂停 / 恢复（Controller 已按
     * SYS_PROCESS_EXAMPLE_ADMIN 鉴权）；意见必填；生成对应 ADMIN_* 审批记录。
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
            case ADMIN_OPERATE_JUMP -> adminJump(example, current.getFieldId(), request.getTargetNodeId(), content);
            case ADMIN_OPERATE_FINISH -> adminFinish(example, current.getFieldId(), content);
            case ADMIN_OPERATE_ABANDON -> abandonExample(example, current.getFieldId(), AUDIT_TYPE_ADMIN_ABANDON, content);
            case ADMIN_OPERATE_PAUSE -> adminPause(example, current.getFieldId(), content);
            case ADMIN_OPERATE_RESUME -> adminResume(example, current.getFieldId(), content);
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
     * <li>14 类小写操作类型：drafter_submit / drafter_cancel / drafter_retract / handler_pass /
     *     handler_reject / admin_abandon / admin_jump 本期接通真实引擎分发；
     *     drafter_expedite / handler_cc / handler_transfer / handler_transfer_retract /
     *     processed_retract / processed_expedite / admin_change_handler 暂未开放
     *     （{@code CS_PROCESS_OPERATE_TYPE_INVALID}，后续任务逐个替换为真实分发）；</li>
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
            /* ==================== 暂未开放（后续任务落地引擎分发） ==================== */
            case OPERATE_TYPE_DRAFTER_EXPEDITE,
                 OPERATE_TYPE_HANDLER_CC,
                 OPERATE_TYPE_HANDLER_TRANSFER,
                 OPERATE_TYPE_HANDLER_TRANSFER_RETRACT,
                 OPERATE_TYPE_PROCESSED_RETRACT,
                 OPERATE_TYPE_PROCESSED_EXPEDITE,
                 OPERATE_TYPE_ADMIN_CHANGE_HANDLER ->
                    throw new IllegalArgumentException(CS_PROCESS_OPERATE_TYPE_INVALID + "：操作类型暂未开放: " + type);
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
     * 当前用户可执行的审批操作与待处理工作项。
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

    /** 管理员跳转：PROCESSING / RETURN 时可跳转到审批节点或起草节点；生成 ADMIN_JUMP 记录。 */
    private void adminJump(SysProcessExample example, String operatorId, String targetNodeId, String content) {
        if (!EXAMPLE_STATUS_PROCESSING.equals(example.getFieldStatus())
                && !EXAMPLE_STATUS_RETURN.equals(example.getFieldStatus())) {
            throw new IllegalArgumentException("仅审批中或驳回状态的流程可跳转");
        }
        SysProcessExampleNode target = requireNode(targetNodeId);
        if (!NODE_TYPE_APPROVE.equals(target.getFieldNodeType())
                && !NODE_TYPE_DRAFT.equals(target.getFieldNodeType())
                && !SysProcessConstants.NODE_TYPE_MANUAL.equals(target.getFieldNodeType())) {
            throw new IllegalArgumentException("跳转节点仅限审批节点、起草节点或人工决策节点");
        }
        addAudit(example.getFieldId(), null, operatorId, AUDIT_TYPE_ADMIN_JUMP, content);
        abandonActiveWorkitems(example.getFieldId());
        flowSupport.markCurrentNodeWaiting(example.getFieldId());
        // 跳转=废弃在途工作项后从目标节点进入引擎（起草节点停留等待重新提交）
        flowEngine.adminJump(example, targetNodeId);
    }

    /** 管理员终审通过：PROCESSING / RETURN / PAUSE 时办结；生成 ADMIN_FINISH 记录。 */
    private void adminFinish(SysProcessExample example, String operatorId, String content) {
        if (!EXAMPLE_STATUS_PROCESSING.equals(example.getFieldStatus())
                && !EXAMPLE_STATUS_RETURN.equals(example.getFieldStatus())
                && !EXAMPLE_STATUS_PAUSE.equals(example.getFieldStatus())) {
            throw new IllegalArgumentException("仅审批中、驳回或暂停状态的流程可终审通过");
        }
        addAudit(example.getFieldId(), null, operatorId, AUDIT_TYPE_ADMIN_FINISH, content);
        finishActiveWorkitems(example.getFieldId());
        moveToEndNode(example, EXAMPLE_STATUS_FINISH);
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

    /* ==================== 私有工具方法 ==================== */

    /** 审批节点是否已通过：单签任一通过即通过；会审需全部工作项完成。 */
    private boolean isNodePassed(SysProcessExampleNode node, String passedWorkitemId) {
        SysProcessTemplateNodeConfigDo config = parseNodeConfig(node.getFieldNodeConfig());
        String approveMode = null == config || StringUtil.isEmpty(config.getApproveMode())
                ? APPROVE_MODE_SINGLE : config.getApproveMode();
        if (APPROVE_MODE_COUNTERSIGN.equals(approveMode)) {
            for (SysProcessExampleWorkitem workitem : workitemDao.selectListByNodeId(node.getFieldId())) {
                if (!WORKITEM_STATUS_FINISH.equals(workitem.getFieldStatus())
                        && !passedWorkitemId.equals(workitem.getFieldId())) {
                    return false;
                }
            }
            return true;
        }
        return true;
    }

    /** 将该节点其他（未完成）工作项置为完成。 */
    private void finishRemainingWorkitems(String nodeId, String exceptWorkitemId) {
        Date now = new Date();
        for (SysProcessExampleWorkitem workitem : workitemDao.selectListByNodeId(nodeId)) {
            if (!WORKITEM_STATUS_FINISH.equals(workitem.getFieldStatus())
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

    /** 当前用户对流程实例可执行的审批操作（面板展示，最终鉴权仍由各接口 resolver 兜底）。 */
    private List<String> computeOperations(SysProcessExample example) {
        List<String> operations = new ArrayList<>();
        String currentUserId = currentUserIdOrNull();
        if (null == currentUserId) {
            return operations;
        }
        String status = example.getFieldStatus();
        boolean drafter = currentUserId.equals(example.getFieldCreatorId());
        if (drafter) {
            if (EXAMPLE_STATUS_PROCESSING.equals(status)) {
                operations.add("DRAFTER_BACK");
            } else if (EXAMPLE_STATUS_DRAFT.equals(status) || EXAMPLE_STATUS_RETURN.equals(status)) {
                operations.add("DRAFTER_ABANDON");
            }
        }
        boolean hasProcessingWorkitem = workitemDao.selectByExampleAndHandler(example.getFieldId(), currentUserId)
                .stream().anyMatch(w -> WORKITEM_STATUS_PROCESSING.equals(w.getFieldStatus()));
        if (EXAMPLE_STATUS_PROCESSING.equals(status) && hasProcessingWorkitem) {
            operations.add("HANDLER_PASS");
            operations.add("HANDLER_RETURN");
        }
        if (resolver.resolve(new AuthorizationContext(ACTION_PROCESS_EXAMPLE_ADMIN_OPERATE))) {
            switch (status) {
                case EXAMPLE_STATUS_PROCESSING -> {
                    operations.add(ADMIN_OPERATE_JUMP);
                    operations.add(ADMIN_OPERATE_FINISH);
                    operations.add(ADMIN_OPERATE_ABANDON);
                    operations.add(ADMIN_OPERATE_PAUSE);
                }
                case EXAMPLE_STATUS_RETURN -> {
                    operations.add(ADMIN_OPERATE_JUMP);
                    operations.add(ADMIN_OPERATE_FINISH);
                    operations.add(ADMIN_OPERATE_ABANDON);
                }
                case EXAMPLE_STATUS_DRAFT -> operations.add(ADMIN_OPERATE_ABANDON);
                case EXAMPLE_STATUS_PAUSE -> {
                    operations.add(ADMIN_OPERATE_FINISH);
                    operations.add(ADMIN_OPERATE_ABANDON);
                    operations.add(ADMIN_OPERATE_RESUME);
                }
                default -> {
                }
            }
        }
        return operations;
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
