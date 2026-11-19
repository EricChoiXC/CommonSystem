package com.eric.base.sys.process.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import com.eric.base.sys.process.dao.SysProcessTemplateDao;
import com.eric.base.sys.process.dao.SysProcessTemplateHistoryDao;
import com.eric.base.sys.process.dao.SysProcessTemplateLineDao;
import com.eric.base.sys.process.dao.SysProcessTemplateNodeDao;
import com.eric.base.sys.process.model.SysProcessTemplate;
import com.eric.base.sys.process.model.SysProcessTemplateHistory;
import com.eric.base.sys.process.model.SysProcessTemplateHistoryContentDo;
import com.eric.base.sys.process.model.SysProcessTemplateLine;
import com.eric.base.sys.process.model.SysProcessTemplateNode;
import com.eric.base.sys.process.model.SysProcessTemplateNodeConfigDo;
import com.eric.base.sys.process.properties.SysProcessConstants;
import com.eric.base.sys.process.properties.SysProcessTemplateProperties;
import com.eric.base.sys.process.request.SysProcessTemplateQuery;
import com.eric.base.sys.process.response.SysProcessTemplateLineVo;
import com.eric.base.sys.process.response.SysProcessTemplateModelVo;
import com.eric.base.sys.process.response.SysProcessTemplateNodeVo;
import com.eric.base.sys.process.response.SysProcessTemplateVo;
import com.eric.base.sys.process.task.ProcessTaskRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.eric.base.sys.process.properties.SysProcessConstants.APPROVE_MODE_COUNTERSIGN;
import static com.eric.base.sys.process.properties.SysProcessConstants.APPROVE_MODE_SERIAL;
import static com.eric.base.sys.process.properties.SysProcessConstants.APPROVE_MODE_SINGLE;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUTO_CONTINUE_SKIP_SYSTEM;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUTO_SKIP_NO;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUTO_SKIP_SYSTEM;
import static com.eric.base.sys.process.properties.SysProcessConstants.AUTO_SKIP_YES;
import static com.eric.base.sys.process.properties.SysProcessConstants.FIXED_NODE_KEY_DRAFT;
import static com.eric.base.sys.process.properties.SysProcessConstants.FIXED_NODE_KEY_END;
import static com.eric.base.sys.process.properties.SysProcessConstants.FIXED_NODE_KEY_START;
import static com.eric.base.sys.process.properties.SysProcessConstants.FIXED_NODE_NAME_DRAFT;
import static com.eric.base.sys.process.properties.SysProcessConstants.FIXED_NODE_NAME_END;
import static com.eric.base.sys.process.properties.SysProcessConstants.FIXED_NODE_NAME_START;
import static com.eric.base.sys.process.properties.SysProcessConstants.FIXED_NODE_NO_DRAFT;
import static com.eric.base.sys.process.properties.SysProcessConstants.FIXED_NODE_NO_START;
import static com.eric.base.sys.process.properties.SysProcessConstants.HANDLER_RESOLVE_MODE_LEADER;
import static com.eric.base.sys.process.properties.SysProcessConstants.HANDLER_RESOLVE_MODE_MEMBER;
import static com.eric.base.sys.process.properties.SysProcessConstants.HANDLER_SOURCE_FIELD;
import static com.eric.base.sys.process.properties.SysProcessConstants.HANDLER_SOURCE_FORMULA;
import static com.eric.base.sys.process.properties.SysProcessConstants.HANDLER_SOURCE_ORG;
import static com.eric.base.sys.process.properties.SysProcessConstants.CC_SOURCE_FORMULA;
import static com.eric.base.sys.process.properties.SysProcessConstants.CC_SOURCE_ORG;
import static com.eric.base.sys.process.properties.SysProcessConstants.EMPTY_HANDLER_MODE_ERROR;
import static com.eric.base.sys.process.properties.SysProcessConstants.EMPTY_HANDLER_MODE_SKIP;
import static com.eric.base.sys.process.properties.SysProcessConstants.EMPTY_HANDLER_MODE_SYSTEM;
import static com.eric.base.sys.process.properties.SysProcessConstants.JOIN_MODE_ANY;
import static com.eric.base.sys.process.properties.SysProcessConstants.LINE_TYPE_CONDITION;
import static com.eric.base.sys.process.properties.SysProcessConstants.LINE_TYPE_MANUAL;
import static com.eric.base.sys.process.properties.SysProcessConstants.LINE_TYPE_NORMAL;
import static com.eric.base.sys.process.properties.SysProcessConstants.LINE_TYPE_PARALLEL;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_APPROVE;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_CC;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_CONDITION;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_DRAFT;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_END;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_FORK;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_JOIN;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_MANUAL;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_ROBORT;
import static com.eric.base.sys.process.properties.SysProcessConstants.NODE_TYPE_START;
import static com.eric.base.sys.process.properties.SysProcessConstants.SKIP_MODE_ANY;
import static com.eric.base.sys.process.properties.SysProcessConstants.SKIP_MODE_NONE;
import static com.eric.base.sys.process.properties.SysProcessConstants.SKIP_MODE_PREV;
import static com.eric.base.sys.process.properties.SysProcessConstants.SKIP_MODE_SYSTEM;
import static com.eric.base.sys.process.properties.SysProcessConstants.TRIGGER_TYPE_CONDITION;
import static com.eric.base.sys.process.properties.SysProcessConstants.TRIGGER_TYPE_FIXED;

/**
 * 流程模板业务服务
 *
 * @author Eric
 * @date 2026/8/22
 * @description 承载「模板管理」页面的后端能力（依据 artificial/docs/设计文档/sys/process/流程核心业务.md「流程模板」）：
 *              <ul>
 *              <li>分类列表：读取 sys/process/application.yml 的 process_template 配置；</li>
 *              <li>列表/详情：模板分页查询、模板+节点详情读取；</li>
 *              <li>新建：校验并落库模板、节点与连线（固定节点 + 编号与节点键归一 + 审批节点配置校验
 *                  + 服务端图校验 G1~G10），连线全量落库；</li>
 *              <li>编辑：先归档历史版本（模板 + 全部节点 + 全部连线序列化到
 *                  sys_process_template_history.field_content），再更新模板（版本号 +1）
 *                  并全量替换节点与连线（连线先删后建，不做差量）；</li>
 *              <li>启停用：仅更新启用状态。</li>
 *              </ul>
 *              节点规则：固定 [1 开始节点 START]、[2 草稿节点 DRAFT]、[结束节点 END] 三个固定节点且无配置，
 *              结束节点编号随节点的创建往后顺延（保存时统一归一为 START=1、DRAFT=2、其余节点按序、
 *              END=最后）；节点业务键 nodeKey 归一为 START=N1、DRAFT=N2、END=N3、其余按创建顺序
 *              N4… 顺延（模板内唯一，连线以此表达）；触发条件必须是判断公式，${field} 占位符字段
 *              必须是文档实体类中存在的字段。
 *              图校验规则（docs/设计文档/sys/process/流程v1_7接口清单.md §4.4，非法图拒绝并返回中文提示）：
 *              G1 N1 沿连线连通可达 N3；G2 无孤立节点；G3 各节点出边数符合类型约束；
 *              G4 CONDITION 出边有且仅有一条默认分支；G5 FORK 与 JOIN 成对且分支汇聚、
 *              嵌套并行拒绝；G6 MANUAL 默认流向须为实际出边目标之一；G7 审批节点 operations
 *              必须包含且仅包含一个 handler_pass；G8 节点 tasks 经 ProcessTaskRegistry 校验；
 *              G9 rejectTargets 候选限定为起草节点 + 图上游审批节点；G10 flowDecisions 同口径限定。
 */
@Service
public class SysProcessTemplateService {

    private static final Logger log = LoggerFactory.getLogger(SysProcessTemplateService.class);

    /** 触发条件中的占位符模式：${field} */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /** 判断公式最低要求：必须包含比较运算符（> < >= <= == != 等） */
    private static final Pattern COMPARATOR_PATTERN = Pattern.compile(".*[<>=!].*");

    private static final Set<String> NODE_TYPES = Set.of(
            NODE_TYPE_START, NODE_TYPE_DRAFT, NODE_TYPE_END, NODE_TYPE_APPROVE, NODE_TYPE_CC,
            NODE_TYPE_CONDITION, NODE_TYPE_FORK, NODE_TYPE_JOIN, NODE_TYPE_ROBORT, NODE_TYPE_MANUAL);
    private static final Set<String> APPROVE_MODES = Set.of(
            APPROVE_MODE_SINGLE, APPROVE_MODE_SERIAL, APPROVE_MODE_COUNTERSIGN);
    private static final Set<String> TRIGGER_TYPES = Set.of(TRIGGER_TYPE_FIXED, TRIGGER_TYPE_CONDITION);
    private static final Set<String> AUTO_SKIPS = Set.of(AUTO_SKIP_SYSTEM, AUTO_SKIP_YES, AUTO_SKIP_NO);

    /** 连线类型全集（NORMAL / CONDITION / PARALLEL / MANUAL，由起始节点类型决定） */
    private static final Set<String> LINE_TYPES = Set.of(
            LINE_TYPE_NORMAL, LINE_TYPE_CONDITION, LINE_TYPE_PARALLEL, LINE_TYPE_MANUAL);

    /** 节点默认展示名（未命名且非审批节点时按类型取中文名） */
    private static final Map<String, String> NODE_TYPE_NAMES = Map.of(
            NODE_TYPE_START, "开始节点",
            NODE_TYPE_DRAFT, "草稿节点",
            NODE_TYPE_END, "结束节点",
            NODE_TYPE_APPROVE, "审批节点",
            NODE_TYPE_CC, "抄送节点",
            NODE_TYPE_CONDITION, "条件分支",
            NODE_TYPE_FORK, "并行开始",
            NODE_TYPE_JOIN, "并行结束",
            NODE_TYPE_ROBORT, "机器人节点",
            NODE_TYPE_MANUAL, "人工决策");

    /** 审批操作明细中的通过操作标识（G7：审批节点必须包含且仅包含一个） */
    private static final String OPERATE_TYPE_HANDLER_PASS = SysProcessConstants.OPERATE_TYPE_HANDLER_PASS;


    private final SysProcessTemplateDao templateDao;
    private final SysProcessTemplateNodeDao nodeDao;
    private final SysProcessTemplateLineDao templateLineDao;
    private final SysProcessTemplateHistoryDao historyDao;
    private final ProcessTaskRegistry processTaskRegistry;
    private final ObjectMapper objectMapper;

    public SysProcessTemplateService(SysProcessTemplateDao templateDao,
                                     SysProcessTemplateNodeDao nodeDao,
                                     SysProcessTemplateLineDao templateLineDao,
                                     SysProcessTemplateHistoryDao historyDao,
                                     ProcessTaskRegistry processTaskRegistry,
                                     ObjectMapper objectMapper) {
        this.templateDao = templateDao;
        this.nodeDao = nodeDao;
        this.templateLineDao = templateLineDao;
        this.historyDao = historyDao;
        this.processTaskRegistry = processTaskRegistry;
        this.objectMapper = objectMapper;
    }

    /* ==================== 分类 / 列表 / 详情 ==================== */

    /**
     * 模板分类列表：读取 process_template 配置（左侧分类展示）。
     */
    public List<SysProcessTemplateModelVo> listModels() {
        return SysProcessTemplateProperties.listModels();
    }

    /**
     * 模板列表分页查询：field_model_name 为左侧分类固定条件，
     * 支持模板名称/编号模糊、启用状态筛选与单条件排序（createTime / updateTime）。
     */
    public List<SysProcessTemplateVo> list(SysProcessTemplateQuery query, Pages pages,
                                           String sortField, String sortOrder) {
        SysProcessTemplate criteria = toCriteria(query);
        return templateDao.selectListSorted(criteria, normalizePages(pages),
                        normalizeSortField(sortField), normalizeSortOrder(sortOrder)).stream()
                .map(SysProcessTemplateService::toBasicVo)
                .toList();
    }

    /**
     * 模板列表总数（与 {@link #list} 过滤条件一致，供分页 metadata.total 使用）
     */
    public long count(SysProcessTemplateQuery query) {
        return templateDao.selectCount(toCriteria(query));
    }

    /**
     * 模板详情：模板基本字段 + 节点列表（按编号升序，节点配置解析展开）+ 连线列表
     * （按起始节点键、分支序号升序；v1_7 契约 §4.2）。
     *
     * @param fieldId 模板 id
     * @return 模板详情；模板不存在时返回 null
     */
    public SysProcessTemplateVo load(String fieldId) {
        if (StringUtil.isEmpty(fieldId)) {
            throw new IllegalArgumentException("fieldId must not be blank");
        }
        SysProcessTemplate template = templateDao.selectById(fieldId.trim());
        if (null == template) {
            return null;
        }
        return toDetailVo(template, nodeDao.selectListByTemplateId(template.getFieldId()),
                templateLineDao.selectListByTemplateId(template.getFieldId()));
    }

    /* ==================== 新建 / 编辑 / 启停用 ==================== */

    /**
     * 新建模板：校验模板字段、节点、连线与服务端图校验（G1~G10）后落库，初始版本号 1；
     * 节点与连线均按请求全量落库（连线不做差量）。
     *
     * @param vo 模板请求体（含 nodes 节点列表与 lines 连线列表）
     * @return 保存后的模板详情
     */
    @Transactional
    public SysProcessTemplateVo save(SysProcessTemplateVo vo) {
        validateTemplate(vo);
        // 防御：新建请求的 fieldId 由前端预取（getInitID），若与既有模板重复则直接拒绝，
        // 避免落入数据库主键冲突路径（兜底异常处理可能回显 SQL 细节）。
        if (StringUtil.isNotEmpty(vo.getFieldId())
                && null != templateDao.selectById(vo.getFieldId().trim())) {
            throw new IllegalArgumentException("模板已存在，请勿重复提交");
        }
        SysProcessTemplate template = new SysProcessTemplate();
        template.setFieldId(vo.getFieldId());
        template.setFieldName(vo.getFieldName().trim());
        template.setFieldModelName(vo.getFieldModelName().trim());
        template.setFieldCode(trimToNull(vo.getFieldCode()));
        template.setFieldRemark(trimToNull(vo.getFieldRemark()));
        template.setFieldVersion(1);
        template.setFieldEnable(null == vo.getFieldEnable() ? Boolean.TRUE : vo.getFieldEnable());
        // 默认模板：置为默认=替换同分类现有默认模板（先清旧再设新，事务内保证唯一）
        boolean makeDefault = Boolean.TRUE.equals(vo.getFieldDefault());
        if (makeDefault) {
            templateDao.clearDefaultByModelName(template.getFieldModelName());
        }
        template.setFieldDefault(makeDefault);
        fillAudit(template, true);
        templateDao.insert(template);

        PreparedGraph graph = prepareGraph(template.getFieldId(), template.getFieldModelName(), vo);
        if (!graph.nodes().isEmpty()) {
            nodeDao.insertBatch(template.getFieldId(), graph.nodes());
        }
        if (!graph.lines().isEmpty()) {
            templateLineDao.insertBatch(template.getFieldId(), graph.lines());
        }
        log.info("process template saved: id={}, name={}, model={}, nodes={}, lines={}",
                template.getFieldId(), template.getFieldName(), template.getFieldModelName(),
                graph.nodes().size(), graph.lines().size());
        return load(template.getFieldId());
    }

    /**
     * 编辑模板：将历史版本的模板、全部节点与全部连线字段信息统一归档到
     * sys_process_template_history.field_content，再更新现版本（版本号 +1）
     * 并全量替换节点与连线（连线先删后建，不做差量）。
     *
     * @param vo 模板请求体（必须携带 fieldId，含 nodes + lines）
     * @return 保存后的模板详情
     */
    @Transactional
    public SysProcessTemplateVo update(SysProcessTemplateVo vo) {
        if (null == vo || StringUtil.isEmpty(vo.getFieldId())) {
            throw new IllegalArgumentException("fieldId must not be blank");
        }
        validateTemplate(vo);
        SysProcessTemplate exist = templateDao.selectById(vo.getFieldId().trim());
        if (null == exist) {
            throw new IllegalArgumentException("流程模板不存在或已删除");
        }
        if (StringUtil.isNotEmpty(vo.getFieldModelName())
                && !vo.getFieldModelName().trim().equals(exist.getFieldModelName())) {
            throw new IllegalArgumentException("模板所属模块不可变更");
        }

        // 归档当前版本（模板 + 全部节点 + 全部连线统一序列化）
        List<SysProcessTemplateNode> existNodes = nodeDao.selectListByTemplateId(exist.getFieldId());
        List<SysProcessTemplateLine> existLines = templateLineDao.selectListByTemplateId(exist.getFieldId());
        archiveHistory(exist, existNodes, existLines);

        // 更新现版本
        exist.setFieldName(vo.getFieldName().trim());
        exist.setFieldCode(trimToNull(vo.getFieldCode()));
        exist.setFieldRemark(trimToNull(vo.getFieldRemark()));
        exist.setFieldVersion(null == exist.getFieldVersion() ? 2 : exist.getFieldVersion() + 1);
        // 默认模板：置为默认=替换同分类现有默认模板；取消默认（false）直接落库
        boolean makeDefault = Boolean.TRUE.equals(vo.getFieldDefault());
        if (makeDefault && !Boolean.TRUE.equals(exist.getFieldDefault())) {
            templateDao.clearDefaultByModelName(exist.getFieldModelName());
        }
        exist.setFieldDefault(vo.getFieldDefault());
        fillAudit(exist, false);
        templateDao.update(exist);

        // 节点与连线全量替换（先删后插，连线不做差量）
        PreparedGraph graph = prepareGraph(exist.getFieldId(), exist.getFieldModelName(), vo);
        nodeDao.deleteByTemplateId(exist.getFieldId());
        if (!graph.nodes().isEmpty()) {
            nodeDao.insertBatch(exist.getFieldId(), graph.nodes());
        }
        templateLineDao.deleteByTemplateId(exist.getFieldId());
        if (!graph.lines().isEmpty()) {
            templateLineDao.insertBatch(exist.getFieldId(), graph.lines());
        }
        log.info("process template updated: id={}, name={}, version={}, nodes={}, lines={}",
                exist.getFieldId(), exist.getFieldName(), exist.getFieldVersion(),
                graph.nodes().size(), graph.lines().size());
        return load(exist.getFieldId());
    }

    /**
     * 模板启停用：仅更新启用状态与修改人/修改时间。
     *
     * @param fieldId 模板 id
     * @param enable  目标启用状态
     */
    @Transactional
    public void updateEnable(String fieldId, Boolean enable) {
        if (StringUtil.isEmpty(fieldId)) {
            throw new IllegalArgumentException("fieldId must not be blank");
        }
        if (null == enable) {
            throw new IllegalArgumentException("enable must not be null");
        }
        SysProcessTemplate exist = templateDao.selectById(fieldId.trim());
        if (null == exist) {
            throw new IllegalArgumentException("流程模板不存在或已删除");
        }
        exist.setFieldEnable(enable);
        fillAudit(exist, false);
        templateDao.update(exist);
        log.info("process template enable updated: id={}, enable={}", exist.getFieldId(), enable);
    }

    /**
     * 置为默认模板：替换同分类现有默认模板（先取消旧默认再设置新默认，事务内保证唯一）。
     * 校验模板存在且启用（停用模板不可作为默认模板）。
     *
     * @param fieldId 模板 id
     */
    @Transactional
    public void setDefault(String fieldId) {
        if (StringUtil.isEmpty(fieldId)) {
            throw new IllegalArgumentException("fieldId must not be blank");
        }
        SysProcessTemplate exist = templateDao.selectById(fieldId.trim());
        if (null == exist) {
            throw new IllegalArgumentException("流程模板不存在或已删除");
        }
        if (!Boolean.TRUE.equals(exist.getFieldEnable())) {
            throw new IllegalArgumentException("停用的流程模板不能置为默认");
        }
        if (Boolean.TRUE.equals(exist.getFieldDefault())) {
            log.info("process template already default: id={}", exist.getFieldId());
            return;
        }
        templateDao.clearDefaultByModelName(exist.getFieldModelName());
        exist.setFieldDefault(Boolean.TRUE);
        fillAudit(exist, false);
        templateDao.update(exist);
        log.info("process template set default: id={}, model={}", exist.getFieldId(), exist.getFieldModelName());
    }

    /* ==================== 私有方法 ==================== */

    private static SysProcessTemplate toCriteria(SysProcessTemplateQuery query) {
        SysProcessTemplate criteria = new SysProcessTemplate();
        if (null == query) {
            return criteria;
        }
        criteria.setFieldModelName(StringUtil.isEmpty(query.getFieldModelName()) ? null : query.getFieldModelName().trim());
        criteria.setFieldName(StringUtil.isEmpty(query.getFieldName()) ? null : query.getFieldName().trim());
        criteria.setFieldCode(StringUtil.isEmpty(query.getFieldCode()) ? null : query.getFieldCode().trim());
        criteria.setFieldEnable(query.getEnable());
        return criteria;
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
        return "updateTime".equals(sortField) ? sortField : "createTime";
    }

    private static String normalizeSortOrder(String sortOrder) {
        return "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
    }

    private static SysProcessTemplateVo toBasicVo(SysProcessTemplate template) {
        SysProcessTemplateVo vo = new SysProcessTemplateVo();
        vo.setFieldId(template.getFieldId());
        vo.setFieldName(template.getFieldName());
        vo.setFieldModelName(template.getFieldModelName());
        vo.setFieldCode(template.getFieldCode());
        vo.setFieldVersion(template.getFieldVersion());
        vo.setFieldRemark(template.getFieldRemark());
        vo.setFieldDefault(template.getFieldDefault());
        vo.setFieldCreatorId(template.getFieldCreatorId());
        vo.setFieldCreateTime(template.getFieldCreateTime());
        vo.setFieldUpdaterId(template.getFieldUpdaterId());
        vo.setFieldUpdateTime(template.getFieldUpdateTime());
        vo.setFieldEnable(template.getFieldEnable());
        return vo;
    }

    private SysProcessTemplateVo toDetailVo(SysProcessTemplate template, List<SysProcessTemplateNode> nodes,
                                            List<SysProcessTemplateLine> lines) {
        SysProcessTemplateVo vo = toBasicVo(template);
        if (null != nodes) {
            vo.setNodes(nodes.stream().map(this::toNodeVo).toList());
        }
        if (null != lines) {
            vo.setLines(lines.stream().map(SysProcessTemplateService::toLineVo).toList());
        }
        return vo;
    }

    /** 连线实体 → 连线 VO（load 响应回显；fieldTemplateId 一并回显便于前端定位模板）。 */
    private static SysProcessTemplateLineVo toLineVo(SysProcessTemplateLine line) {
        SysProcessTemplateLineVo vo = new SysProcessTemplateLineVo();
        vo.setFieldId(line.getFieldId());
        vo.setFieldTemplateId(line.getFieldTemplateId());
        vo.setFieldFromKey(line.getFieldFromKey());
        vo.setFieldToKey(line.getFieldToKey());
        vo.setFieldLineType(line.getFieldLineType());
        vo.setFieldBranchOrder(line.getFieldBranchOrder());
        vo.setFieldBranchName(line.getFieldBranchName());
        vo.setFieldCondition(line.getFieldCondition());
        vo.setFieldIsDefault(line.getFieldIsDefault());
        return vo;
    }

    private SysProcessTemplateNodeVo toNodeVo(SysProcessTemplateNode node) {
        SysProcessTemplateNodeVo vo = new SysProcessTemplateNodeVo();
        vo.setFieldId(node.getFieldId());
        vo.setFieldNo(node.getFieldNo());
        // 实体列 field_node_key / 实体属性 fieldNodeKey，按接口清单 §4.2 契约以 nodeKey 输出
        vo.setNodeKey(node.getFieldNodeKey());
        vo.setFieldNodeName(node.getFieldNodeName());
        vo.setFieldNodeType(node.getFieldNodeType());
        SysProcessTemplateNodeConfigDo config = parseNodeConfig(node.getFieldNodeConfig());
        if (null != config) {
            vo.setHandlerSource(config.getHandlerSource());
            vo.setHandlerIds(config.getHandlerIds());
            vo.setHandlerNames(config.getHandlerNames());
            vo.setHandlerField(config.getHandlerField());
            vo.setHandlerFormula(config.getHandlerFormula());
            vo.setHandlerResolveMode(config.getHandlerResolveMode());
            vo.setApproveMode(config.getApproveMode());
            vo.setTriggerType(config.getTriggerType());
            vo.setCondition(config.getCondition());
            vo.setAutoSkip(config.getAutoSkip());
            vo.setSkipMode(config.getSkipMode());
            vo.setAutoContinueSkip(config.getAutoContinueSkip());
            vo.setEmptyHandlerMode(config.getEmptyHandlerMode());
            vo.setRejectTargets(config.getRejectTargets());
            vo.setOperations(toOperationDetailVos(config.getOperations()));
            vo.setTasks(toTaskDetailVos(config.getTasks()));
            vo.setCcSource(config.getCcSource());
            vo.setCcIds(config.getCcIds());
            vo.setCcNames(config.getCcNames());
            vo.setCcFormula(config.getCcFormula());
            vo.setFlowDecisions(config.getFlowDecisions());
            vo.setDefaultFlowKey(config.getDefaultFlowKey());
            vo.setJoinMode(config.getJoinMode());
            vo.setOps(config.getOps());
        }
        return vo;
    }

    private static List<SysProcessTemplateNodeVo.OperationDetailVo> toOperationDetailVos(
            List<SysProcessTemplateNodeConfigDo.OperationDetail> details) {
        if (null == details || details.isEmpty()) {
            return List.of();
        }
        List<SysProcessTemplateNodeVo.OperationDetailVo> vos = new ArrayList<>();
        for (SysProcessTemplateNodeConfigDo.OperationDetail detail : details) {
            if (null == detail) {
                continue;
            }
            SysProcessTemplateNodeVo.OperationDetailVo vo = new SysProcessTemplateNodeVo.OperationDetailVo();
            vo.setOperateType(detail.getOperateType());
            vo.setOperateName(detail.getOperateName());
            vo.setDefaultContent(detail.getDefaultContent());
            vos.add(vo);
        }
        return vos;
    }

    private static List<SysProcessTemplateNodeVo.TaskDetailVo> toTaskDetailVos(
            List<SysProcessTemplateNodeConfigDo.TaskDetail> details) {
        if (null == details || details.isEmpty()) {
            return List.of();
        }
        List<SysProcessTemplateNodeVo.TaskDetailVo> vos = new ArrayList<>();
        for (SysProcessTemplateNodeConfigDo.TaskDetail detail : details) {
            if (null == detail) {
                continue;
            }
            SysProcessTemplateNodeVo.TaskDetailVo vo = new SysProcessTemplateNodeVo.TaskDetailVo();
            vo.setTaskName(detail.getTaskName());
            vo.setTaskBean(detail.getTaskBean());
            vo.setTaskType(detail.getTaskType());
            vo.setParams(detail.getParams());
            vos.add(vo);
        }
        return vos;
    }

    /**
     * 模板基本字段校验：名称必填、所属模块必填且必须为 process_template 已配置分类。
     */
    private static void validateTemplate(SysProcessTemplateVo vo) {
        Objects.requireNonNull(vo, "body must not be null");
        if (StringUtil.isEmpty(vo.getFieldName())) {
            throw new IllegalArgumentException("模板名称不能为空");
        }
        if (StringUtil.isEmpty(vo.getFieldModelName())) {
            throw new IllegalArgumentException("所属模块不能为空");
        }
        if (!SysProcessTemplateProperties.contains(vo.getFieldModelName().trim())) {
            throw new IllegalArgumentException("所属模块未配置: " + vo.getFieldModelName());
        }
    }

    /**
     * 已归一化（节点键、编号、配置 JSON 均已定稿）的节点与其 nodeKey 对照项。
     */
    private record PreparedNode(SysProcessTemplateNode node, String nodeKey, String nodeType,
                                SysProcessTemplateNodeConfigDo config) {
    }

    /** 一次保存请求的归一化结果：节点实体列表 + 连线实体列表（同事务全量落库）。 */
    private record PreparedGraph(List<SysProcessTemplateNode> nodes, List<SysProcessTemplateLine> lines) {
    }

    /**
     * 节点校验与归一化（v1_7：10 类节点 + 节点键归一）：
     * <ul>
     * <li>必须包含且仅包含一个开始节点 START、草稿节点 DRAFT、结束节点 END（固定节点无配置）；</li>
     * <li>节点类型必须为 10 类合法枚举之一；</li>
     * <li>节点键归一：START=N1、DRAFT=N2、END=N3、其余按传入顺序 N4… 顺延（模板内唯一，
     *     请求携带的 nodeKey 仅作对照，与归一结果不一致即拒绝）；</li>
     * <li>编号归一：START=1、DRAFT=2、其余节点按传入顺序从 3 顺延、END 置末；</li>
     * <li>节点配置校验：审批节点审批源（组织架构/表单字段/公式）与审批模式（单签/串行/会签）、
     *     抄送节点抄送源（地址本/公式）、各类节点取值合法性，条件判断时触发条件必须为判断公式
     *     且占位符字段存在于文档实体类。</li>
     * </ul>
     *
     * @param templateId 模板 id（落库外键）
     * @param modelName  模板分类（process_template 二级配置项，用于字段/操作标识字典校验）
     * @param nodeVos    请求节点列表（非空）
     * @return 归一化后的节点（START、DRAFT、其余按序、END）及其节点键对照项
     */
    private List<PreparedNode> validateAndNormalizeNodes(String templateId, String modelName,
                                                         List<SysProcessTemplateNodeVo> nodeVos) {
        if (null == nodeVos || nodeVos.isEmpty()) {
            throw new IllegalArgumentException("模板至少需要开始/草稿/结束三个固定节点");
        }
        SysProcessTemplateNodeVo start = null;
        SysProcessTemplateNodeVo draft = null;
        SysProcessTemplateNodeVo end = null;
        List<SysProcessTemplateNodeVo> middles = new ArrayList<>();
        Set<String> requestedKeys = new LinkedHashSet<>();
        for (SysProcessTemplateNodeVo vo : nodeVos) {
            String type = StringUtil.isEmpty(vo.getFieldNodeType()) ? null : vo.getFieldNodeType().trim();
            if (null == type || !NODE_TYPES.contains(type)) {
                throw new IllegalArgumentException("非法的节点类型: " + vo.getFieldNodeType());
            }
            if (StringUtil.isNotEmpty(vo.getNodeKey())) {
                requestedKeys.add(vo.getNodeKey().trim());
            }
            if (NODE_TYPE_START.equals(type)) {
                if (null != start) {
                    throw new IllegalArgumentException("开始节点只能有一个");
                }
                start = vo;
            } else if (NODE_TYPE_DRAFT.equals(type)) {
                if (null != draft) {
                    throw new IllegalArgumentException("草稿节点只能有一个");
                }
                draft = vo;
            } else if (NODE_TYPE_END.equals(type)) {
                if (null != end) {
                    throw new IllegalArgumentException("结束节点只能有一个");
                }
                end = vo;
            } else {
                middles.add(vo);
            }
        }
        if (null == start || null == draft || null == end) {
            throw new IllegalArgumentException("模板必须包含开始节点、草稿节点与结束节点");
        }

        // 节点键归一：固定节点 N1/N2/N3，其余按传入顺序 N4… 顺延（与页面节点顺序一一对应）
        List<String> nodeKeys = new ArrayList<>();
        nodeKeys.add(FIXED_NODE_KEY_START);
        nodeKeys.add(FIXED_NODE_KEY_DRAFT);
        int no = FIXED_NODE_NO_DRAFT + 1;
        // 节点键与展示编号错开：固定键 N1/N2/N3 已被开始/起草/结束占用，中间节点键须自 N4 起顺延
        for (int i = 0; i < middles.size(); i++) {
            nodeKeys.add("N" + (FIXED_NODE_NO_DRAFT + 2 + i));
        }
        nodeKeys.add(FIXED_NODE_KEY_END);
        if (new HashSet<>(nodeKeys).size() != nodeKeys.size()) {
            throw new IllegalArgumentException("节点业务键重复：模板内节点键必须唯一");
        }
        // 请求携带的 nodeKey 按「节点类型分组」对照归一结果（请求顺序不影响归一：固定节点 N1/N2/N3，中间节点按其中出现顺序 N4…）
        int middleSeq = 0;
        for (SysProcessTemplateNodeVo nodeVo : nodeVos) {
            String requested = StringUtil.isEmpty(nodeVo.getNodeKey()) ? null : nodeVo.getNodeKey().trim();
            if (null == requested) {
                continue;
            }
            String expected;
            if (NODE_TYPE_START.equals(nodeVo.getFieldNodeType())) {
                expected = FIXED_NODE_KEY_START;
            } else if (NODE_TYPE_DRAFT.equals(nodeVo.getFieldNodeType())) {
                expected = FIXED_NODE_KEY_DRAFT;
            } else if (NODE_TYPE_END.equals(nodeVo.getFieldNodeType())) {
                expected = FIXED_NODE_KEY_END;
            } else {
                expected = "N" + (FIXED_NODE_NO_DRAFT + 2 + middleSeq++);
            }
            if (!requested.equals(expected)) {
                throw new IllegalArgumentException("节点「" + displayNodeName(nodeVo)
                        + "」的节点业务键与归一结果不一致：请求 " + requested + "，归一为 " + expected
                        + "（固定节点 N1=开始 N2=起草 N3=结束，其余 N4… 按节点顺序顺延）");
            }
        }

        List<SysProcessTemplateNode> result = new ArrayList<>();
        result.add(toNode(templateId, start, 0, nodeKeys.get(0), FIXED_NODE_NO_START,
                NODE_TYPE_START, FIXED_NODE_NAME_START, null, null));
        result.add(toNode(templateId, draft, 1, nodeKeys.get(1), FIXED_NODE_NO_DRAFT,
                NODE_TYPE_DRAFT, FIXED_NODE_NAME_DRAFT, null, null));
        String className = SysProcessTemplateProperties.getClassName(modelName);
        for (int i = 0; i < middles.size(); i++) {
            result.add(toNode(templateId, middles.get(i), i + 2, nodeKeys.get(i + 2), no + i,
                    middles.get(i).getFieldNodeType().trim(), null, className, modelName));
        }
        // 结束节点编号随中间节点顺延（开始 1、起草 2、中间 3…n、结束 n+1），与改造前的线性编号口径一致
        int endNo = no + middles.size();
        result.add(toNode(templateId, end, nodeKeys.size() - 1, nodeKeys.get(nodeKeys.size() - 1),
                endNo, NODE_TYPE_END, FIXED_NODE_NAME_END, null, null));

        // 节点键与配置回读（供连线一致性与图校验使用；配置由落库 JSON 反序列化，保证与落库一致）
        List<PreparedNode> prepared = new ArrayList<>();
        for (int i = 0; i < result.size(); i++) {
            SysProcessTemplateNode node = result.get(i);
            prepared.add(new PreparedNode(node, nodeKeys.get(i), node.getFieldNodeType(),
                    parseNodeConfig(node.getFieldNodeConfig())));
        }
        return prepared;
    }

    /**
     * 单个节点落库实体构造：固定节点强制编号/名称/类型且不保存配置；其余节点按类型校验并序列化配置。
     *
     * @param vo        请求节点
     * @param seq       传入顺序下标（用于节点键对照与默认展示名）
     * @param nodeKey   归一后的节点业务键
     * @param no        归一后的节点编号
     * @param type      归一后的节点类型
     * @param fixedName 固定节点名称（null 表示非固定节点）
     * @param className 文档实体类（审批节点占位符字段校验用，可空）
     * @param modelName 模板分类（审批/抄送配置校验用，可空）
     */
    private SysProcessTemplateNode toNode(String templateId, SysProcessTemplateNodeVo vo, int seq, String nodeKey,
                                          int no, String type, String fixedName, String className,
                                          String modelName) {
        boolean fixed = null != fixedName;
        SysProcessTemplateNode node = new SysProcessTemplateNode();
        node.setFieldId(vo.getFieldId());
        node.setFieldTemplateId(templateId);
        node.setFieldNo(no);
        node.setFieldNodeKey(nodeKey);
        node.setFieldNodeName(fixed ? fixedName
                : (StringUtil.isEmpty(vo.getFieldNodeName()) ? defaultNodeName(type) + no : vo.getFieldNodeName().trim()));
        node.setFieldNodeType(type);
        if (fixed) {
            node.setFieldNodeConfig(null);
            return node;
        }
        SysProcessTemplateNodeConfigDo config = new SysProcessTemplateNodeConfigDo();
        switch (type) {
            case NODE_TYPE_APPROVE -> fillApproveConfig(config, vo, className, modelName);
            case NODE_TYPE_CC -> fillCcConfig(config, vo);
            case NODE_TYPE_CONDITION, NODE_TYPE_FORK, NODE_TYPE_JOIN, NODE_TYPE_ROBORT -> {
                // 无节点级配置弹窗：条件/并行分支配置在连线上，机器人节点配置由后续任务扩展
            }
            case NODE_TYPE_MANUAL -> {
                config.setFlowDecisions(trimList(vo.getFlowDecisions()));
                config.setDefaultFlowKey(trimToNull(vo.getDefaultFlowKey()));
            }
            default -> throw new IllegalArgumentException("非法的节点类型: " + type);
        }
        validateNodeTasks(vo, config, modelName);
        node.setFieldNodeConfig(toNodeConfigJson(config));
        return node;
    }

    /** 节点默认展示名（未命名时按类型取中文名） */
    private static String defaultNodeName(String type) {
        return NODE_TYPE_NAMES.getOrDefault(type, "节点");
    }

    /* ==================== 连线与图校验（G1~G10） ==================== */

    /**
     * 模板图归一化：节点归一 → 连线校验与归一 → 服务端图校验（G1~G10）。
     * 节点与连线任一不合法即抛出 {@link IllegalArgumentException}（中文提示），
     * 由 GlobalExceptionHandler 以 BAD_REQUEST + 中文原因返回，不落库任何数据。
     *
     * @param templateId 模板 id（落库外键）
     * @param modelName  模板分类（process_template 二级配置项）
     * @param vo         模板请求体（含 nodes + lines）
     * @return 归一化后的节点与连线实体（同事务全量落库）
     */
    private PreparedGraph prepareGraph(String templateId, String modelName, SysProcessTemplateVo vo) {
        List<PreparedNode> prepared = validateAndNormalizeNodes(templateId, modelName, vo.getNodes());
        List<PreparedNode> ordered = new ArrayList<>(prepared);
        ordered.sort(Comparator.comparingInt(item -> item.node().getFieldNo()));
        Set<String> nodeKeys = new LinkedHashSet<>();
        for (PreparedNode node : ordered) {
            nodeKeys.add(node.nodeKey());
        }
        List<SysProcessTemplateLine> lines = validateAndNormalizeLines(templateId, modelName, ordered, nodeKeys,
                vo.getLines());
        validateGraphRules(ordered, lines);
        return new PreparedGraph(prepared.stream().map(PreparedNode::node).toList(), lines);
    }

    /**
     * 连线校验与归一化（接口清单 §4.3：连线全量替换 + 节点键一致性）：
     * <ul>
     * <li>连线类型合法性，且与起始节点类型匹配（CONDITION→CONDITION、FORK→PARALLEL、
     *     MANUAL→MANUAL、其余→NORMAL）；未显式给出时按起始节点类型推导；</li>
     * <li>起始/目标节点键必须存在于请求 nodes 的 nodeKey 集合；</li>
     * <li>起止不得为同一节点；分支条件公式非空时必须是判断公式（占位符字段校验口径同触发条件）。</li>
     * </ul>
     *
     * @param templateId 模板 id
     * @param modelName  模板分类（分支条件公式占位符字段校验取该分类的文档实体类）
     * @param nodes      归一化并按编号升序的节点
     * @param nodeKeys   请求 nodes 的 nodeKey 集合
     * @param lineVos    请求连线列表（可空=无连线，随后由图校验 G1 拒绝）
     * @return 连线实体列表（模板 id 由服务端回填）
     */
    private List<SysProcessTemplateLine> validateAndNormalizeLines(String templateId, String modelName,
                                                                   List<PreparedNode> nodes,
                                                                   Set<String> nodeKeys,
                                                                   List<SysProcessTemplateLineVo> lineVos) {
        List<SysProcessTemplateLine> lines = new ArrayList<>();
        if (null == lineVos || lineVos.isEmpty()) {
            return lines;
        }
        Map<String, PreparedNode> nodeByKey = new LinkedHashMap<>();
        for (PreparedNode node : nodes) {
            nodeByKey.put(node.nodeKey(), node);
        }
        for (SysProcessTemplateLineVo vo : lineVos) {
            String fromKey = trimToNull(vo.getFieldFromKey());
            String toKey = trimToNull(vo.getFieldToKey());
            if (null == fromKey || null == toKey) {
                throw new IllegalArgumentException("连线的起始节点键与目标节点键不能为空");
            }
            if (!nodeKeys.contains(fromKey)) {
                throw new IllegalArgumentException("连线的起始节点键不存在于节点列表中: " + fromKey);
            }
            if (!nodeKeys.contains(toKey)) {
                throw new IllegalArgumentException("连线的目标节点键不存在于节点列表中: " + toKey);
            }
            if (fromKey.equals(toKey)) {
                throw new IllegalArgumentException("连线的起始节点与目标节点不能相同: " + fromKey);
            }
            String expectedType = lineTypeOf(nodeByKey.get(fromKey).nodeType());
            String lineType = trimToNull(vo.getFieldLineType());
            if (null == lineType) {
                lineType = expectedType;
            } else if (!LINE_TYPES.contains(lineType)) {
                throw new IllegalArgumentException("连线的连线类型取值非法: " + vo.getFieldLineType());
            } else if (!expectedType.equals(lineType)) {
                throw new IllegalArgumentException("连线「" + fromKey + "→" + toKey + "」的连线类型应为 "
                        + expectedType + "（由起始节点类型决定），实际为 " + lineType);
            }
            if (StringUtil.isNotEmpty(vo.getFieldCondition())) {
                validateCondition(vo.getFieldCondition(), SysProcessTemplateProperties.getClassName(modelName));
            }
            SysProcessTemplateLine line = new SysProcessTemplateLine();
            line.setFieldId(vo.getFieldId());
            line.setFieldTemplateId(templateId);
            line.setFieldFromKey(fromKey);
            line.setFieldToKey(toKey);
            line.setFieldLineType(lineType);
            // 分支序号列 NOT NULL（线性连线约定 0），请求未携带时按 0 归一，避免插入 NULL 主键约束失败
            line.setFieldBranchOrder(null == vo.getFieldBranchOrder() ? 0 : vo.getFieldBranchOrder());
            line.setFieldBranchName(trimToNull(vo.getFieldBranchName()));
            line.setFieldCondition(trimToNull(vo.getFieldCondition()));
            line.setFieldIsDefault(Boolean.TRUE.equals(vo.getFieldIsDefault()));
            lines.add(line);
        }
        return lines;
    }

    /** 连线类型与起始节点类型的对应关系（由源节点类型决定，显式存储便于校验）。 */
    private static String lineTypeOf(String nodeType) {
        if (NODE_TYPE_CONDITION.equals(nodeType)) {
            return LINE_TYPE_CONDITION;
        }
        if (NODE_TYPE_FORK.equals(nodeType)) {
            return LINE_TYPE_PARALLEL;
        }
        if (NODE_TYPE_MANUAL.equals(nodeType)) {
            return LINE_TYPE_MANUAL;
        }
        return LINE_TYPE_NORMAL;
    }

    /**
     * 节点默认展示名（未命名时按类型取中文名，带节点键便于排障） */
    private static String describeNode(PreparedNode node) {
        String name = StringUtil.isEmpty(node.node().getFieldNodeName()) ? "未命名节点" : node.node().getFieldNodeName().trim();
        return name + "（" + node.nodeKey() + "）";
    }

    /**
     * 服务端图校验（docs/设计文档/sys/process/流程v1_7接口清单.md §4.4，非法图拒绝并返回中文提示）：
     * <ol>
     * <li>G3 各节点出边数符合类型约束（START=1 且固定指向 N2、DRAFT=1 且默认指向 N3、END=0、
     *     APPROVE/CC/ROBORT=1、CONDITION/FORK/MANUAL≥1、JOIN=1）；</li>
     * <li>G2 无孤立节点（除 N1 无入边、N3 无出边外，每个节点至少一条入边或出边）；</li>
     * <li>G1 从 N1 沿连线连通可达 N3（首尾贯通，无连线、断链、循环无出口均拒绝）；</li>
     * <li>G4 CONDITION 出边有且仅有一条默认分支；</li>
     * <li>G5 FORK 与 JOIN 成对、各分支汇聚到对应 JOIN、嵌套并行拒绝；</li>
     * <li>G6 MANUAL 的 defaultFlowKey 必须是其实际出边目标之一；</li>
     * <li>G7 审批节点 operations 必须包含且仅包含一个 handler_pass；</li>
     * <li>G8 节点 tasks 必须经 ProcessTaskRegistry 校验通过（保存前已逐行校验，此处复校机读值与步骤）；</li>
     * <li>G9 rejectTargets 候选限定为起草节点 + 图上游审批节点；</li>
     * <li>G10 flowDecisions 候选限定为起草节点 + 该 MANUAL 节点图上游审批节点。</li>
     * </ol>
     *
     * @param nodes 归一化并按编号升序的节点
     * @param lines 归一化后的连线
     */
    private void validateGraphRules(List<PreparedNode> nodes, List<SysProcessTemplateLine> lines) {
        Map<String, PreparedNode> nodeByKey = new LinkedHashMap<>();
        for (PreparedNode node : nodes) {
            nodeByKey.put(node.nodeKey(), node);
        }
        Map<String, List<SysProcessTemplateLine>> outgoing = buildOutgoing(lines);
        Map<String, List<SysProcessTemplateLine>> incoming = buildIncoming(lines);
        // 是否存在中间节点（除固定节点外的 10 类节点）：决定草稿节点出边是否必须直达结束节点 N3
        boolean hasMiddle = nodes.stream().anyMatch(node -> !NODE_TYPE_START.equals(node.nodeType())
                && !NODE_TYPE_DRAFT.equals(node.nodeType()) && !NODE_TYPE_END.equals(node.nodeType()));

        for (PreparedNode node : nodes) {
            String key = node.nodeKey();
            String name = describeNode(node);
            List<SysProcessTemplateLine> out = outgoing.getOrDefault(key, List.of());
            List<SysProcessTemplateLine> in = incoming.getOrDefault(key, List.of());
            int outCount = out.size();
            // G2：无孤立节点（N1 允许无入边、N3 允许无出边）——先于 G3 出边数判定，
            // 保证「零入边且零出边」的节点按孤立节点语义提示，不被出边数提示掩盖
            if (outCount == 0 && in.isEmpty()
                    && !FIXED_NODE_KEY_START.equals(key) && !FIXED_NODE_KEY_END.equals(key)) {
                throw new IllegalArgumentException("节点「" + name + "」存在孤立节点：除开始节点 N1 与结束节点 N3 外，每个节点至少需要一条入边或出边");
            }
            switch (node.nodeType()) {
                case NODE_TYPE_START -> {
                    if (outCount != 1 || !FIXED_NODE_KEY_DRAFT.equals(out.get(0).getFieldToKey())) {
                        throw new IllegalArgumentException("节点「" + name + "」的出边数不合法：开始节点（START）必须有且仅有一条出边且指向草稿节点 N2");
                    }
                }
                case NODE_TYPE_DRAFT -> {
                    if (outCount != 1) {
                        throw new IllegalArgumentException("节点「" + name + "」的出边数不合法：草稿节点（DRAFT）必须有且仅有一条出边");
                    }
                    // 无中间节点时默认指向结束节点 N3；存在中间节点时其唯一出边连向后续链路（由 G1/G2 保证无断链、无孤立）
                    if (!hasMiddle && !FIXED_NODE_KEY_END.equals(out.get(0).getFieldToKey())) {
                        throw new IllegalArgumentException("节点「" + name
                                + "」的出边数不合法：草稿节点（DRAFT）无后续中间节点时必须有且仅有一条出边且默认指向结束节点 N3");
                    }
                }
                case NODE_TYPE_END -> {
                    if (outCount != 0) {
                        throw new IllegalArgumentException("节点「" + name + "」的出边数不合法：结束节点（END）不允许有出边");
                    }
                }
                case NODE_TYPE_APPROVE, NODE_TYPE_CC, NODE_TYPE_ROBORT -> {
                    if (outCount != 1) {
                        throw new IllegalArgumentException("节点「" + name + "」的出边数不合法：该节点类型必须有且仅有一条出边，实际 " + outCount);
                    }
                }
                case NODE_TYPE_CONDITION, NODE_TYPE_FORK, NODE_TYPE_MANUAL -> {
                    if (outCount < 1) {
                        throw new IllegalArgumentException("节点「" + name + "」的出边数不合法：该节点类型至少需要一条出边");
                    }
                }
                case NODE_TYPE_JOIN -> {
                    if (outCount != 1) {
                        throw new IllegalArgumentException("节点「" + name + "」的出边数不合法：并行结束节点（JOIN）必须有且仅有一条出边，实际 " + outCount);
                    }
                }
                default -> throw new IllegalArgumentException("非法的节点类型: " + node.nodeType());
            }
        }

        // G1：从 N1 沿连线连通可达 N3
        if (!reachableKeys(outgoing, FIXED_NODE_KEY_START).contains(FIXED_NODE_KEY_END)) {
            throw new IllegalArgumentException("流程首尾不连通：从开始节点 N1 沿连线无法到达结束节点 N3，请检查节点连线");
        }

        validateConditionDefaultBranches(nodes, outgoing);
        Map<String, String> forkByJoin = validateForkJoin(nodes, outgoing, incoming);
        validateManualNodes(nodes, outgoing);
        validateApproveNodes(nodes);
        validateRejectTargets(nodes, incoming);
        validateFlowDecisions(nodes, incoming, forkByJoin);
        validateNodeTaskRules(nodes);
    }

    /** 出边分组（key=起始节点键，保持连线顺序）。 */
    private static Map<String, List<SysProcessTemplateLine>> buildOutgoing(List<SysProcessTemplateLine> lines) {
        Map<String, List<SysProcessTemplateLine>> outgoing = new LinkedHashMap<>();
        for (SysProcessTemplateLine line : lines) {
            outgoing.computeIfAbsent(line.getFieldFromKey(), k -> new ArrayList<>()).add(line);
        }
        return outgoing;
    }

    /** 入边分组（key=目标节点键，保持连线顺序）。 */
    private static Map<String, List<SysProcessTemplateLine>> buildIncoming(List<SysProcessTemplateLine> lines) {
        Map<String, List<SysProcessTemplateLine>> incoming = new LinkedHashMap<>();
        for (SysProcessTemplateLine line : lines) {
            incoming.computeIfAbsent(line.getFieldToKey(), k -> new ArrayList<>()).add(line);
        }
        return incoming;
    }

    /**
     * 沿出边广度优先可达的节点键集合（不含起点自身）。
     *
     * @param outgoing 出边分组
     * @param fromKey  起点节点键
     */
    private static Set<String> reachableKeys(Map<String, List<SysProcessTemplateLine>> outgoing, String fromKey) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(fromKey);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (SysProcessTemplateLine line : outgoing.getOrDefault(current, List.of())) {
                String next = line.getFieldToKey();
                if (StringUtil.isNotEmpty(next) && visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return visited;
    }

    /**
     * G4：条件分支（CONDITION）出边中有且仅有一条默认分支。
     *
     * @param nodes    全部节点
     * @param outgoing 出边分组
     */
    private static void validateConditionDefaultBranches(List<PreparedNode> nodes,
                                                         Map<String, List<SysProcessTemplateLine>> outgoing) {
        for (PreparedNode node : nodes) {
            if (!NODE_TYPE_CONDITION.equals(node.nodeType())) {
                continue;
            }
            long defaults = outgoing.getOrDefault(node.nodeKey(), List.of()).stream()
                    .filter(line -> Boolean.TRUE.equals(line.getFieldIsDefault()))
                    .count();
            if (defaults != 1) {
                throw new IllegalArgumentException("节点「" + describeNode(node)
                        + "」的分支配置不合法：条件分支节点的出边中有且仅有一条默认分支，实际 " + defaults + " 条");
            }
        }
    }

    /**
     * G5：FORK 与 JOIN 成对（每个 FORK 的各个分支必须汇聚到同一个 JOIN、
     * 该 JOIN 的入边必须来自 FORK 下游节点、每个 JOIN 必须被某个 FORK 认领），
     * 且当前节点之后不得再嵌套另一个并行开始节点（嵌套并行本期不支持）。
     *
     * @param nodes    全部节点
     * @param outgoing 出边分组
     * @param incoming 入边分组
     * @return 认领关系：JOIN 节点键 → 对应 FORK 节点键
     */
    private static Map<String, String> validateForkJoin(List<PreparedNode> nodes,
                                                        Map<String, List<SysProcessTemplateLine>> outgoing,
                                                        Map<String, List<SysProcessTemplateLine>> incoming) {
        Map<String, String> forkByJoin = new LinkedHashMap<>();
        Set<String> joinKeys = new LinkedHashSet<>();
        for (PreparedNode node : nodes) {
            if (NODE_TYPE_JOIN.equals(node.nodeType())) {
                joinKeys.add(node.nodeKey());
            }
        }
        for (PreparedNode node : nodes) {
            if (!NODE_TYPE_FORK.equals(node.nodeType())) {
                continue;
            }
            String forkKey = node.nodeKey();
            Set<String> reachable = reachableKeys(outgoing, forkKey);
            Set<String> reachableJoins = filterByType(nodes, reachable, NODE_TYPE_JOIN);
            if (reachableJoins.isEmpty()) {
                throw new IllegalArgumentException("节点「" + describeNode(node)
                        + "」的并行结构不合法：并行开始节点（FORK）的分支未汇聚到并行结束节点（JOIN）");
            }
            if (reachableJoins.size() > 1) {
                throw new IllegalArgumentException("节点「" + describeNode(node)
                        + "」的并行结构不合法：各分支必须汇聚到同一个并行结束节点（JOIN），当前可达 "
                        + reachableJoins);
            }
            String joinKey = reachableJoins.iterator().next();
            String claimed = forkByJoin.put(joinKey, forkKey);
            if (null != claimed) {
                throw new IllegalArgumentException("并行结束节点（" + joinKey + "）被多个并行开始节点认领（"
                        + claimed + "、" + forkKey + "）；并行开始与并行结束必须一一成对");
            }
            if (!filterByType(nodes, reachable, NODE_TYPE_FORK).isEmpty()) {
                throw new IllegalArgumentException("节点「" + describeNode(node)
                        + "」的并行结构嵌套了另一个并行开始节点（FORK）；嵌套并行本期不支持，请拆分并行分支");
            }
            for (PreparedNode other : nodes) {
                if (!NODE_TYPE_JOIN.equals(other.nodeType())) {
                    continue;
                }
                if (joinKey.equals(other.nodeKey())) {
                    continue;
                }
                if (incoming.getOrDefault(other.nodeKey(), List.of()).stream()
                        .anyMatch(line -> reachable.contains(line.getFieldFromKey()))) {
                    throw new IllegalArgumentException("节点「" + describeNode(other)
                            + "」与并行开始节点（" + forkKey + "）不匹配：该并行结束节点的入边来自其分支之外，"
                            + "各分支必须汇聚到对应的并行结束节点");
                }
            }
        }
        for (String joinKey : joinKeys) {
            if (!forkByJoin.containsKey(joinKey)) {
                throw new IllegalArgumentException("并行结束节点（" + joinKey
                        + "）没有对应的并行开始节点（FORK）；并行开始与并行结束必须一一成对");
            }
        }
        return forkByJoin;
    }

    /**
     * G6：人工决策（MANUAL）的 defaultFlowKey 必须是其实际出边目标之一。
     * （G10 的 flowDecisions 候选范围由 {@link #validateFlowDecisions} 单独校验。）
     *
     * @param nodes    全部节点
     * @param outgoing 出边分组
     */
    private static void validateManualNodes(List<PreparedNode> nodes,
                                            Map<String, List<SysProcessTemplateLine>> outgoing) {
        for (PreparedNode node : nodes) {
            if (!NODE_TYPE_MANUAL.equals(node.nodeType())) {
                continue;
            }
            String name = describeNode(node);
            Set<String> targets = new LinkedHashSet<>();
            for (SysProcessTemplateLine line : outgoing.getOrDefault(node.nodeKey(), List.of())) {
                targets.add(line.getFieldToKey());
            }
            String defaultFlowKey = trimToNull(node.config().getDefaultFlowKey());
            if (null == defaultFlowKey) {
                throw new IllegalArgumentException("节点「" + name
                        + "」的默认流向不能为空：人工决策节点的默认流向必须是其实际出边目标之一");
            }
            if (!targets.contains(defaultFlowKey)) {
                throw new IllegalArgumentException("节点「" + name + "」的默认流向 " + defaultFlowKey
                        + " 不是其实际出边目标之一（实际出边目标: " + targets + "）");
            }
        }
    }

    /**
     * G7：审批节点 operations 必须包含且仅包含一个 {@code handler_pass}。
     *
     * @param nodes 全部节点
     */
    private static void validateApproveNodes(List<PreparedNode> nodes) {
        for (PreparedNode node : nodes) {
            if (!NODE_TYPE_APPROVE.equals(node.nodeType())) {
                continue;
            }
            long passCount = node.config().getOperations().stream()
                    .filter(detail -> null != detail
                            && OPERATE_TYPE_HANDLER_PASS.equals(trimToNull(detail.getOperateType())))
                    .count();
            if (passCount != 1) {
                throw new IllegalArgumentException("节点「" + describeNode(node)
                        + "」的审批操作配置不合法：必须包含且仅包含一个通过操作（handler_pass），实际 " + passCount + " 个");
            }
        }
    }

    /**
     * G8：节点 tasks 必须经 ProcessTaskRegistry 校验通过（保存前逐行校验，此处复校任务与步骤非空并回读登记名）。
     *
     * @param nodes 全部节点
     */
    private void validateNodeTaskRules(List<PreparedNode> nodes) {
        for (PreparedNode node : nodes) {
            // 固定节点（START/DRAFT/END）不承载节点配置，跳过
            if (null == node.config()) {
                continue;
            }
            for (SysProcessTemplateNodeConfigDo.TaskDetail task : node.config().getTasks()) {
                if (null == task || StringUtil.isEmpty(task.getTaskBean()) || StringUtil.isEmpty(task.getTaskType())) {
                    throw new IllegalArgumentException("节点「" + describeNode(node)
                            + "」的流程任务不合法：执行任务与执行步骤均不能为空（须经 ProcessTaskRegistry 校验）");
                }
            }
        }
    }

    /**
     * G9：rejectTargets 候选限定为起草节点 + 图上游审批节点。
     *
     * @param nodes    全部节点
     * @param incoming 入边分组
     */
    private static void validateRejectTargets(List<PreparedNode> nodes,
                                              Map<String, List<SysProcessTemplateLine>> incoming) {
        for (PreparedNode node : nodes) {
            // 固定节点（START/DRAFT/END）不承载节点配置，无驳回目标可校验
            if (null == node.config() || node.config().getRejectTargets().isEmpty()) {
                continue;
            }
            Set<String> candidates = new LinkedHashSet<>();
            candidates.add(FIXED_NODE_KEY_DRAFT);
            candidates.addAll(upstreamApproveKeys(nodes, incoming, node.nodeKey(), Map.of()));
            for (String target : node.config().getRejectTargets()) {
                if (!candidates.contains(target)) {
                    throw new IllegalArgumentException("节点「" + describeNode(node) + "」的可驳回目标 " + target
                            + " 不在候选范围内（候选限定为起草节点 N2 + 图上游审批节点: " + candidates + "）");
                }
            }
        }
    }

    /**
     * G10 的候选集合：指定节点的图上游审批节点（沿入边反向广度优先，不含自身；包含固定起草节点 N2）。
     * <p>JOIN 节点可能位于某个 FORK 的分支上，此时其真实上游应回到对应 FORK 之前，
     * 因此遍历到 JOIN 时改用 ForkJoin 认领关系指回的 FORK 继续反向遍历（{@code forkByJoin}）。</p>
     *
     * @param nodes      全部节点
     * @param incoming   入边分组
     * @param nodeKey    目标节点键
     * @param forkByJoin JOIN → FORK 认领关系（可为空表，表示不做 JOIN 还原）
     */
    private static Set<String> upstreamApproveKeys(List<PreparedNode> nodes,
                                                   Map<String, List<SysProcessTemplateLine>> incoming,
                                                   String nodeKey,
                                                   Map<String, String> forkByJoin) {
        Set<String> upstream = new LinkedHashSet<>();
        Set<String> allUpstream = upstreamKeys(incoming, nodeKey, forkByJoin);
        for (PreparedNode node : nodes) {
            if (!allUpstream.contains(node.nodeKey())) {
                continue;
            }
            if (NODE_TYPE_APPROVE.equals(node.nodeType()) || NODE_TYPE_DRAFT.equals(node.nodeType())) {
                upstream.add(node.nodeKey());
            }
        }
        return upstream;
    }

    /** 沿入边反向广度优先可达的节点键集合（不含起点自身）。 */
    private static Set<String> upstreamKeys(Map<String, List<SysProcessTemplateLine>> incoming, String nodeKey,
                                            Map<String, String> forkByJoin) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(nodeKey);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            List<String> sources = new ArrayList<>();
            for (SysProcessTemplateLine line : incoming.getOrDefault(current, List.of())) {
                sources.add(line.getFieldFromKey());
            }
            if (sources.isEmpty() && null != forkByJoin && forkByJoin.containsKey(current)) {
                sources.add(forkByJoin.get(current));
            }
            for (String source : sources) {
                if (StringUtil.isNotEmpty(source) && visited.add(source)) {
                    queue.add(source);
                }
            }
        }
        return visited;
    }

    /**
     * G10：flowDecisions 候选限定为起草节点 + 该 MANUAL 节点图上游审批节点。
     *
     * @param nodes      全部节点
     * @param incoming   入边分组
     * @param forkByJoin JOIN → FORK 认领关系（上游遍历遇 JOIN 时还原到对应 FORK 之前）
     */
    private static void validateFlowDecisions(List<PreparedNode> nodes,
                                              Map<String, List<SysProcessTemplateLine>> incoming,
                                              Map<String, String> forkByJoin) {
        for (PreparedNode node : nodes) {
            if (!NODE_TYPE_MANUAL.equals(node.nodeType()) || null == node.config()
                    || node.config().getFlowDecisions().isEmpty()) {
                continue;
            }
            Set<String> candidates = new LinkedHashSet<>();
            candidates.add(FIXED_NODE_KEY_DRAFT);
            candidates.addAll(upstreamApproveKeys(nodes, incoming, node.nodeKey(), forkByJoin));
            for (String decision : node.config().getFlowDecisions()) {
                if (!candidates.contains(decision)) {
                    throw new IllegalArgumentException("节点「" + describeNode(node) + "」的流向决策来源 " + decision
                            + " 不在候选范围内（候选限定为起草节点 N2 + 该节点的图上游审批节点: " + candidates + "）");
                }
            }
        }
    }

    /** 从给定节点键集合中按节点类型过滤。 */
    private static Set<String> filterByType(List<PreparedNode> nodes, Set<String> keys, String nodeType) {
        Set<String> result = new LinkedHashSet<>();
        for (PreparedNode node : nodes) {
            if (nodeType.equals(node.nodeType()) && keys.contains(node.nodeKey())) {
                result.add(node.nodeKey());
            }
        }
        return result;
    }

    /**
     * 审批节点配置校验与写入：
     * 审批来源默认组织架构；来源为组织架构时审批人（地址本，多选，限岗位+用户）必填，
     * 来源为表单字段时审批人字段必填且必须定义在分类 process_template.${fieldModelName}.fields 字典中，
     * 来源为公式器时审批人公式必填（不在此执行公式，执行口径见引擎）；
     * 审批人解析方式默认负责人（leader/member）；
     * 审批模式默认单签（single/serial/countersign）；节点触发默认固定；
     * 条件判断时必须填写触发条件，且必须是判断公式、${field} 占位符字段必须是文档实体类中存在的字段；
     * 是否允许自动跳过默认跟随系统、连续跳过默认跟随系统；
     * 无审批人处理方式与驳回目标按请求透传（候选范围由 G9 图校验裁定）；
     * 审批操作明细按请求透传（必须包含且仅包含一个 handler_pass 由 G7 图校验裁定）；
     * 操作标识开关的 key 必须定义在分类 process_template.${fieldModelName}.ops 字典中
     * （未配置字典的分类不允许配置任何操作标识）。
     *
     * @param config    待写入的节点配置
     * @param vo        请求节点
     * @param className 当前模板所属模块的文档实体类（可为 null，此时跳过占位符字段校验）
     * @param modelName 当前模板所属模块（process_template 二级配置项，用于操作标识/字段字典校验）
     */
    private void fillApproveConfig(SysProcessTemplateNodeConfigDo config, SysProcessTemplateNodeVo vo,
                                   String className, String modelName) {
        String handlerSource = StringUtil.isEmpty(vo.getHandlerSource())
                ? HANDLER_SOURCE_ORG : vo.getHandlerSource().trim();
        if (HANDLER_SOURCE_FIELD.equals(handlerSource)) {
            if (StringUtil.isEmpty(vo.getHandlerField())) {
                throw new IllegalArgumentException("节点「" + displayNodeName(vo) + "」的审批人字段不能为空");
            }
            Map<String, String> fieldsDict = SysProcessTemplateProperties.getFields(modelName);
            if (!fieldsDict.containsKey(vo.getHandlerField().trim())) {
                throw new IllegalArgumentException("节点「" + displayNodeName(vo) + "」的审批人字段未定义在分类配置中: " + vo.getHandlerField());
            }
        } else if (HANDLER_SOURCE_FORMULA.equals(handlerSource)) {
            if (StringUtil.isEmpty(vo.getHandlerFormula())) {
                throw new IllegalArgumentException("节点「" + displayNodeName(vo) + "」的审批人公式不能为空");
            }
        } else if (!HANDLER_SOURCE_ORG.equals(handlerSource)) {
            throw new IllegalArgumentException("节点「" + displayNodeName(vo) + "」的审批来源取值非法: " + handlerSource);
        } else if (StringUtil.isEmpty(vo.getHandlerIds())) {
            throw new IllegalArgumentException("节点「" + displayNodeName(vo) + "」的审批人不能为空");
        }
        String approveMode = StringUtil.isEmpty(vo.getApproveMode()) ? APPROVE_MODE_SINGLE : vo.getApproveMode().trim();
        if (!APPROVE_MODES.contains(approveMode)) {
            throw new IllegalArgumentException("节点「" + displayNodeName(vo) + "」的审批模式取值非法: " + approveMode);
        }
        String triggerType = StringUtil.isEmpty(vo.getTriggerType()) ? TRIGGER_TYPE_FIXED : vo.getTriggerType().trim();
        if (!TRIGGER_TYPES.contains(triggerType)) {
            throw new IllegalArgumentException("节点「" + displayNodeName(vo) + "」的节点触发取值非法: " + triggerType);
        }
        if (TRIGGER_TYPE_CONDITION.equals(triggerType)) {
            validateCondition(vo.getCondition(), className);
        }
        String autoSkip = StringUtil.isEmpty(vo.getAutoSkip()) ? AUTO_SKIP_SYSTEM : vo.getAutoSkip().trim();
        if (!AUTO_SKIPS.contains(autoSkip)) {
            throw new IllegalArgumentException("节点「" + displayNodeName(vo) + "」的自动跳过取值非法: " + autoSkip);
        }
        String autoContinueSkip = StringUtil.isEmpty(vo.getAutoContinueSkip())
                ? AUTO_CONTINUE_SKIP_SYSTEM : vo.getAutoContinueSkip().trim();
        if (!AUTO_SKIPS.contains(autoContinueSkip)) {
            throw new IllegalArgumentException("节点「" + displayNodeName(vo) + "」的连续跳过取值非法: " + autoContinueSkip);
        }
        validateHandlerResolveMode(vo.getHandlerResolveMode(), vo);
        validateSkipMode(vo.getSkipMode(), vo);
        validateEmptyHandlerMode(vo.getEmptyHandlerMode(), vo);
        // 操作标识开关：key 必须定义在分类 process_template.${fieldModelName}.ops 字典中
        if (null != vo.getOps() && !vo.getOps().isEmpty()) {
            Map<String, String> opsDict = SysProcessTemplateProperties.getOps(modelName);
            for (String key : vo.getOps().keySet()) {
                if (StringUtil.isEmpty(key)) {
                    throw new IllegalArgumentException("节点「" + displayNodeName(vo) + "」的操作标识不能为空");
                }
                if (!opsDict.containsKey(key)) {
                    throw new IllegalArgumentException("节点「" + displayNodeName(vo) + "」的操作标识未定义在分类配置中: " + key);
                }
            }
        }
        // 落库：审批源与审批人
        config.setHandlerSource(handlerSource);
        if (HANDLER_SOURCE_FIELD.equals(handlerSource)) {
            config.setHandlerField(vo.getHandlerField().trim());
        } else if (HANDLER_SOURCE_FORMULA.equals(handlerSource)) {
            config.setHandlerFormula(vo.getHandlerFormula().trim());
            config.setHandlerIds(trimToNull(vo.getHandlerIds()));
            config.setHandlerNames(trimToNull(vo.getHandlerNames()));
        } else {
            config.setHandlerIds(vo.getHandlerIds());
            config.setHandlerNames(vo.getHandlerNames());
        }
        config.setHandlerResolveMode(StringUtil.isEmpty(vo.getHandlerResolveMode())
                ? HANDLER_RESOLVE_MODE_LEADER : vo.getHandlerResolveMode().trim());
        config.setApproveMode(approveMode);
        config.setTriggerType(triggerType);
        if (StringUtil.isNotEmpty(vo.getCondition())) {
            config.setCondition(vo.getCondition().trim());
        }
        config.setAutoSkip(autoSkip);
        config.setSkipMode(trimToNull(vo.getSkipMode()));
        config.setAutoContinueSkip(autoContinueSkip);
        config.setEmptyHandlerMode(StringUtil.isEmpty(vo.getEmptyHandlerMode())
                ? EMPTY_HANDLER_MODE_SYSTEM : vo.getEmptyHandlerMode().trim());
        config.setRejectTargets(trimList(vo.getRejectTargets()));
        config.setOperations(defaultOperationsIfEmpty(toOperationDetails(vo.getOperations())));
        config.setCcSource(trimToNull(vo.getCcSource()));
        config.setCcIds(trimToNull(vo.getCcIds()));
        config.setCcNames(trimToNull(vo.getCcNames()));
        config.setCcFormula(trimToNull(vo.getCcFormula()));
        if (null != vo.getOps() && !vo.getOps().isEmpty()) {
            Map<String, Boolean> enabledOps = new LinkedHashMap<>();
            for (Map.Entry<String, Boolean> entry : vo.getOps().entrySet()) {
                if (StringUtil.isNotEmpty(entry.getKey()) && Boolean.TRUE.equals(entry.getValue())) {
                    enabledOps.put(entry.getKey().trim(), true);
                }
            }
            if (!enabledOps.isEmpty()) {
                config.setOps(enabledOps);
            }
        }
    }

    /**
     * 抄送节点配置校验与写入：抄送源默认地址本（ccIds 必填）/ formula（ccFormula 必填）。
     *
     * @param config 待写入的节点配置
     * @param vo     请求节点
     */
    private static void fillCcConfig(SysProcessTemplateNodeConfigDo config, SysProcessTemplateNodeVo vo) {
        String ccSource = StringUtil.isEmpty(vo.getCcSource()) ? CC_SOURCE_ORG : vo.getCcSource().trim();
        if (!CC_SOURCE_ORG.equals(ccSource) && !CC_SOURCE_FORMULA.equals(ccSource)) {
            throw new IllegalArgumentException("节点「" + displayNodeName(vo) + "」的抄送来源取值非法: " + ccSource);
        }
        if (CC_SOURCE_FORMULA.equals(ccSource) && StringUtil.isEmpty(vo.getCcFormula())) {
            throw new IllegalArgumentException("节点「" + displayNodeName(vo) + "」的抄送公式不能为空");
        }
        if (CC_SOURCE_ORG.equals(ccSource) && StringUtil.isEmpty(vo.getCcIds())) {
            throw new IllegalArgumentException("节点「" + displayNodeName(vo) + "」的抄送对象不能为空");
        }
        config.setCcSource(ccSource);
        config.setCcIds(trimToNull(vo.getCcIds()));
        config.setCcNames(trimToNull(vo.getCcNames()));
        config.setCcFormula(trimToNull(vo.getCcFormula()));
    }

    /**
     * 节点「流程任务明细」校验（G8）：逐行调用 {@link ProcessTaskRegistry#validateTaskDetail}
     * （任务 name 已注册、processTemplate 匹配（不配置=全模板可用）、allowTaskType 包含所选步骤），
     * 注册表校验异常统一转为本服务入参异常并保留中文原因（注册表校验不在本服务重复实现）。
     *
     * @param vo        请求节点
     * @param config    已写入其他配置的节点配置（任务明细写入其中）
     * @param modelName 模板分类（processTemplate 匹配口径）
     */
    private void validateNodeTasks(SysProcessTemplateNodeVo vo, SysProcessTemplateNodeConfigDo config,
                                   String modelName) {
        List<SysProcessTemplateNodeConfigDo.TaskDetail> tasks = toTaskDetails(vo.getTasks());
        for (SysProcessTemplateNodeConfigDo.TaskDetail task : tasks) {
            try {
                processTaskRegistry.validateTaskDetail(task, modelName);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("节点「" + displayNodeName(vo) + "」的流程任务校验不通过："
                        + (StringUtil.isEmpty(e.getMessage()) ? "未通过 ProcessTaskRegistry 校验" : e.getMessage()));
            }
        }
        config.setTasks(tasks);
    }

    /**
     * 审批操作明细兜底：请求未配置时补一条默认「通过」操作（handler_pass），
     * 保证存量配置（改造前无 operations 字段）保存后满足 G7「包含且仅包含一个 handler_pass」，
     * 审批面板按钮集与改造前等价。
     *
     * @param details 请求解析出的操作明细（可空）
     * @return 非空操作明细（请求为空时为默认通过操作）
     */
    private static List<SysProcessTemplateNodeConfigDo.OperationDetail> defaultOperationsIfEmpty(
            List<SysProcessTemplateNodeConfigDo.OperationDetail> details) {
        if (null != details && !details.isEmpty()) {
            return details;
        }
        SysProcessTemplateNodeConfigDo.OperationDetail pass = new SysProcessTemplateNodeConfigDo.OperationDetail();
        pass.setOperateType(OPERATE_TYPE_HANDLER_PASS);
        pass.setOperateName("通过");
        return new ArrayList<>(List.of(pass));
    }

    /** 审批操作明细：VO → 节点配置 Do（空行忽略）。 */
    private static List<SysProcessTemplateNodeConfigDo.OperationDetail> toOperationDetails(
            List<SysProcessTemplateNodeVo.OperationDetailVo> details) {
        List<SysProcessTemplateNodeConfigDo.OperationDetail> result = new ArrayList<>();
        if (null == details) {
            return result;
        }
        for (SysProcessTemplateNodeVo.OperationDetailVo detail : details) {
            if (null == detail) {
                continue;
            }
            SysProcessTemplateNodeConfigDo.OperationDetail target = new SysProcessTemplateNodeConfigDo.OperationDetail();
            target.setOperateType(trimToNull(detail.getOperateType()));
            target.setOperateName(trimToNull(detail.getOperateName()));
            target.setDefaultContent(trimToNull(detail.getDefaultContent()));
            result.add(target);
        }
        return result;
    }

    /** 流程任务明细：VO → 节点配置 Do（空行忽略）。 */
    private static List<SysProcessTemplateNodeConfigDo.TaskDetail> toTaskDetails(
            List<SysProcessTemplateNodeVo.TaskDetailVo> details) {
        List<SysProcessTemplateNodeConfigDo.TaskDetail> result = new ArrayList<>();
        if (null == details) {
            return result;
        }
        for (SysProcessTemplateNodeVo.TaskDetailVo detail : details) {
            if (null == detail) {
                continue;
            }
            SysProcessTemplateNodeConfigDo.TaskDetail target = new SysProcessTemplateNodeConfigDo.TaskDetail();
            target.setTaskName(trimToNull(detail.getTaskName()));
            target.setTaskBean(trimToNull(detail.getTaskBean()));
            target.setTaskType(trimToNull(detail.getTaskType()));
            target.setParams(detail.getParams());
            result.add(target);
        }
        return result;
    }

    /** 审批人解析方式校验：缺省 leader；仅 leader/member 合法。 */
    private static void validateHandlerResolveMode(String resolveMode, SysProcessTemplateNodeVo vo) {
        String value = StringUtil.isEmpty(resolveMode) ? HANDLER_RESOLVE_MODE_LEADER : resolveMode.trim();
        if (!HANDLER_RESOLVE_MODE_LEADER.equals(value) && !HANDLER_RESOLVE_MODE_MEMBER.equals(value)) {
            throw new IllegalArgumentException("节点「" + displayNodeName(vo) + "」的审批人解析方式取值非法: " + resolveMode);
        }
    }

    /** 相同审批人跳过模式校验：缺省 system；仅 system/none/prev/any 合法（旧值 yes/no 按读取归一后判定）。 */
    private static void validateSkipMode(String skipMode, SysProcessTemplateNodeVo vo) {
        if (StringUtil.isEmpty(skipMode)) {
            return;
        }
        String value = skipMode.trim();
        if (AUTO_SKIP_YES.equals(value)) {
            value = SKIP_MODE_PREV;
        } else if (AUTO_SKIP_NO.equals(value)) {
            value = SKIP_MODE_NONE;
        }
        if (!SKIP_MODE_SYSTEM.equals(value) && !SKIP_MODE_NONE.equals(value)
                && !SKIP_MODE_PREV.equals(value) && !SKIP_MODE_ANY.equals(value)) {
            throw new IllegalArgumentException("节点「" + displayNodeName(vo) + "」的跳过模式取值非法: " + skipMode);
        }
    }

    /** 无审批人处理方式校验：缺省 system；仅 system/skip/error 合法。 */
    private static void validateEmptyHandlerMode(String emptyHandlerMode, SysProcessTemplateNodeVo vo) {
        if (StringUtil.isEmpty(emptyHandlerMode)) {
            return;
        }
        String value = emptyHandlerMode.trim();
        if (!EMPTY_HANDLER_MODE_SYSTEM.equals(value) && !EMPTY_HANDLER_MODE_SKIP.equals(value)
                && !EMPTY_HANDLER_MODE_ERROR.equals(value)) {
            throw new IllegalArgumentException("节点「" + displayNodeName(vo) + "」的无审批人处理方式取值非法: " + emptyHandlerMode);
        }
    }

    /**
     * 触发条件的判断规则（依据流程核心业务.md「触发条件的判断规则」）：
     * 1. 必须是判断公式（含比较运算符）；2. 使用占位符(${field})的字段必须是文档实体类中存在的字段。
     *
     * @param className 文档实体类全限定名（可为 null/加载失败，此时跳过占位符字段校验）
     */
    private void validateCondition(String condition, String className) {
        if (StringUtil.isEmpty(condition)) {
            throw new IllegalArgumentException("节点触发为条件判断时，触发条件不能为空");
        }
        String formula = condition.trim();
        if (!COMPARATOR_PATTERN.matcher(formula).matches()) {
            throw new IllegalArgumentException("触发条件必须是判断公式（需包含比较运算符，如 amount > 1000）: " + formula);
        }
        if (StringUtil.isEmpty(className)) {
            log.warn("process_template 未配置文档实体类，跳过触发条件占位符字段校验");
            return;
        }
        Class<?> entityClass = loadClassQuietly(className);
        if (null == entityClass) {
            log.warn("文档实体类加载失败，跳过触发条件占位符字段校验: {}", className);
            return;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(formula);
        while (matcher.find()) {
            String field = matcher.group(1).trim();
            if (StringUtil.isEmpty(field) || !entityHasField(entityClass, field)) {
                throw new IllegalArgumentException("触发条件占位符字段不存在于文档实体类: ${" + field + "}");
            }
        }
    }

    /** 反射校验实体类（含父类）是否存在指定字段名。 */
    private static boolean entityHasField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (null != current && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getName().equals(fieldName)) {
                    return true;
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static Class<?> loadClassQuietly(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /** 序列化节点配置为 JSON 字符串（校验与默认值补齐后落库，固定节点由调用方传 null 不落配置）。 */
    private String toNodeConfigJson(SysProcessTemplateNodeConfigDo config) {
        return writeJson(config);
    }

    /** 字符串列表裁剪：去空白、去空项；空输入返回空列表（保持 null 与空列表语义一致）。 */
    private static List<String> trimList(List<String> values) {
        List<String> result = new ArrayList<>();
        if (null == values) {
            return result;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (null != trimmed) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /** 解析节点配置 JSON 字符串为节点配置对象（固定节点无配置或解析失败时返回 null）。 */
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

    /**
     * 归档历史版本：模板、全部节点与全部连线字段信息统一序列化保存到 field_content。
     *
     * @param template 归档时的现版本模板
     * @param nodes    归档时的全部节点（按节点编号升序）
     * @param lines    归档时的全部连线（按起始节点键、分支序号升序）
     */
    private void archiveHistory(SysProcessTemplate template, List<SysProcessTemplateNode> nodes,
                                List<SysProcessTemplateLine> lines) {
        SysProcessTemplateHistoryContentDo content = new SysProcessTemplateHistoryContentDo();

        SysProcessTemplateHistoryContentDo.TemplateSnapshot templateSnapshot = new SysProcessTemplateHistoryContentDo.TemplateSnapshot();
        templateSnapshot.setFieldId(template.getFieldId());
        templateSnapshot.setFieldName(template.getFieldName());
        templateSnapshot.setFieldModelName(template.getFieldModelName());
        templateSnapshot.setFieldCode(template.getFieldCode());
        templateSnapshot.setFieldVersion(template.getFieldVersion());
        templateSnapshot.setFieldRemark(template.getFieldRemark());
        templateSnapshot.setFieldEnable(template.getFieldEnable());
        content.setTemplate(templateSnapshot);

        List<SysProcessTemplateHistoryContentDo.NodeSnapshot> nodeSnapshots = new ArrayList<>();
        for (SysProcessTemplateNode node : nodes) {
            SysProcessTemplateHistoryContentDo.NodeSnapshot snapshot = new SysProcessTemplateHistoryContentDo.NodeSnapshot();
            snapshot.setFieldNo(node.getFieldNo());
            snapshot.setFieldNodeKey(node.getFieldNodeKey());
            snapshot.setFieldNodeName(node.getFieldNodeName());
            snapshot.setFieldNodeType(node.getFieldNodeType());
            snapshot.setFieldNodeConfig(parseNodeConfig(node.getFieldNodeConfig()));
            nodeSnapshots.add(snapshot);
        }
        content.setNodes(nodeSnapshots);

        // v1_7：归档快照扩展为「模板 + 全部节点 + 全部连线」（接口清单 §4.3）
        List<SysProcessTemplateHistoryContentDo.LineSnapshot> lineSnapshots = new ArrayList<>();
        for (SysProcessTemplateLine line : lines) {
            SysProcessTemplateHistoryContentDo.LineSnapshot snapshot = new SysProcessTemplateHistoryContentDo.LineSnapshot();
            snapshot.setFieldId(line.getFieldId());
            snapshot.setFieldFromKey(line.getFieldFromKey());
            snapshot.setFieldToKey(line.getFieldToKey());
            snapshot.setFieldLineType(line.getFieldLineType());
            snapshot.setFieldBranchOrder(line.getFieldBranchOrder());
            snapshot.setFieldBranchName(line.getFieldBranchName());
            snapshot.setFieldCondition(line.getFieldCondition());
            snapshot.setFieldIsDefault(line.getFieldIsDefault());
            lineSnapshots.add(snapshot);
        }
        content.setLines(lineSnapshots);

        SysProcessTemplateHistory history = new SysProcessTemplateHistory();
        history.initFieldId();
        history.setFieldTemplateId(template.getFieldId());
        history.setFieldVersion(template.getFieldVersion());
        history.setFieldContent(writeJson(content));
        historyDao.insert(history);
        log.info("process template history archived: templateId={}, version={}", template.getFieldId(), template.getFieldVersion());
    }

    /** 当前登录用户审计字段填充：新建填充创建人/修改人，编辑仅填充修改人。 */
    private static void fillAudit(SysProcessTemplate template, boolean creating) {
        SysOrgUser current = UserUtil.getCurrentUser();
        String userId = null == current ? null : current.getFieldId();
        Date now = new Date();
        if (creating) {
            template.setFieldCreatorId(userId);
            template.setFieldCreateTime(now);
        }
        template.setFieldUpdaterId(userId);
        template.setFieldUpdateTime(now);
    }

    private static String trimToNull(String value) {
        return StringUtil.isEmpty(value) ? null : value.trim();
    }

    private static String displayNodeName(SysProcessTemplateNodeVo vo) {
        return StringUtil.isEmpty(vo.getFieldNodeName()) ? "未命名节点" : vo.getFieldNodeName();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            // 不向客户端回显序列化失败细节（如 Jackson 内部消息），完整信息记录服务端日志
            log.error("serialize node config failed", e);
            throw new IllegalStateException("节点配置序列化失败");
        }
    }
}
