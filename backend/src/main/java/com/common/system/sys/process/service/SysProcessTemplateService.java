package com.common.system.sys.process.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.common.system.common.base.model.Pages;
import com.common.system.common.utils.StringUtil;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.org.model.SysOrgUser;
import com.common.system.sys.process.dao.SysProcessTemplateDao;
import com.common.system.sys.process.dao.SysProcessTemplateHistoryDao;
import com.common.system.sys.process.dao.SysProcessTemplateNodeDao;
import com.common.system.sys.process.model.SysProcessTemplate;
import com.common.system.sys.process.model.SysProcessTemplateHistory;
import com.common.system.sys.process.model.SysProcessTemplateHistoryContentDo;
import com.common.system.sys.process.model.SysProcessTemplateNode;
import com.common.system.sys.process.model.SysProcessTemplateNodeConfigDo;
import com.common.system.sys.process.properties.SysProcessTemplateProperties;
import com.common.system.sys.process.request.SysProcessTemplateQuery;
import com.common.system.sys.process.response.SysProcessTemplateModelVo;
import com.common.system.sys.process.response.SysProcessTemplateNodeVo;
import com.common.system.sys.process.response.SysProcessTemplateVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.common.system.sys.process.properties.SysProcessConstants.APPROVE_MODE_COUNTERSIGN;
import static com.common.system.sys.process.properties.SysProcessConstants.APPROVE_MODE_SINGLE;
import static com.common.system.sys.process.properties.SysProcessConstants.AUTO_CONTINUE_SKIP_SYSTEM;
import static com.common.system.sys.process.properties.SysProcessConstants.AUTO_SKIP_NO;
import static com.common.system.sys.process.properties.SysProcessConstants.AUTO_SKIP_SYSTEM;
import static com.common.system.sys.process.properties.SysProcessConstants.AUTO_SKIP_YES;
import static com.common.system.sys.process.properties.SysProcessConstants.FIXED_NODE_NAME_DRAFT;
import static com.common.system.sys.process.properties.SysProcessConstants.FIXED_NODE_NAME_END;
import static com.common.system.sys.process.properties.SysProcessConstants.FIXED_NODE_NAME_START;
import static com.common.system.sys.process.properties.SysProcessConstants.FIXED_NODE_NO_DRAFT;
import static com.common.system.sys.process.properties.SysProcessConstants.FIXED_NODE_NO_START;
import static com.common.system.sys.process.properties.SysProcessConstants.HANDLER_SOURCE_FIELD;
import static com.common.system.sys.process.properties.SysProcessConstants.HANDLER_SOURCE_ORG;
import static com.common.system.sys.process.properties.SysProcessConstants.NODE_TYPE_APPROVE;
import static com.common.system.sys.process.properties.SysProcessConstants.NODE_TYPE_DRAFT;
import static com.common.system.sys.process.properties.SysProcessConstants.NODE_TYPE_END;
import static com.common.system.sys.process.properties.SysProcessConstants.NODE_TYPE_ROBORT;
import static com.common.system.sys.process.properties.SysProcessConstants.NODE_TYPE_START;
import static com.common.system.sys.process.properties.SysProcessConstants.TRIGGER_TYPE_CONDITION;
import static com.common.system.sys.process.properties.SysProcessConstants.TRIGGER_TYPE_FIXED;

/**
 * 流程模板业务服务
 *
 * @author Eric
 * @date 2026/8/22
 * @description 承载「模板管理」页面的后端能力（依据 artificial/docs/设计文档/sys/process/流程核心业务.md「流程模板」）：
 *              <ul>
 *              <li>分类列表：读取 sys/process/application.yml 的 process_template 配置；</li>
 *              <li>列表/详情：模板分页查询、模板+节点详情读取；</li>
 *              <li>新建：校验并落库模板与节点（固定节点 + 编号归一 + 审批节点配置校验）；</li>
 *              <li>编辑：先归档历史版本（模板+节点序列化到 sys_process_template_history.field_content），
 *                  再更新模板（版本号 +1）并全量替换节点；</li>
 *              <li>启停用：仅更新启用状态。</li>
 *              </ul>
 *              节点规则：固定 [1 开始节点 START]、[2 草稿节点 DRAFT]、[结束节点 END] 三个固定节点且无配置，
 *              结束节点编号随节点的创建往后顺延（保存时统一归一为 START=1、DRAFT=2、其余节点按序、
 *              END=最后）；触发条件必须是判断公式，${field} 占位符字段必须是文档实体类中存在的字段。
 */
@Service
public class SysProcessTemplateService {

    private static final Logger log = LoggerFactory.getLogger(SysProcessTemplateService.class);

    /** 触发条件中的占位符模式：${field} */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /** 判断公式最低要求：必须包含比较运算符（> < >= <= == != 等） */
    private static final Pattern COMPARATOR_PATTERN = Pattern.compile(".*[<>=!].*");

    private static final Set<String> NODE_TYPES = Set.of(
            NODE_TYPE_START, NODE_TYPE_END, NODE_TYPE_DRAFT, NODE_TYPE_APPROVE, NODE_TYPE_ROBORT);
    private static final Set<String> APPROVE_MODES = Set.of(APPROVE_MODE_SINGLE, APPROVE_MODE_COUNTERSIGN);
    private static final Set<String> TRIGGER_TYPES = Set.of(TRIGGER_TYPE_FIXED, TRIGGER_TYPE_CONDITION);
    private static final Set<String> AUTO_SKIPS = Set.of(AUTO_SKIP_SYSTEM, AUTO_SKIP_YES, AUTO_SKIP_NO);

    private final SysProcessTemplateDao templateDao;
    private final SysProcessTemplateNodeDao nodeDao;
    private final SysProcessTemplateHistoryDao historyDao;
    private final ObjectMapper objectMapper;

    public SysProcessTemplateService(SysProcessTemplateDao templateDao,
                                     SysProcessTemplateNodeDao nodeDao,
                                     SysProcessTemplateHistoryDao historyDao,
                                     ObjectMapper objectMapper) {
        this.templateDao = templateDao;
        this.nodeDao = nodeDao;
        this.historyDao = historyDao;
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
     * 模板详情：模板基本字段 + 节点列表（按编号升序，审批节点配置解析展开）。
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
        return toDetailVo(template, nodeDao.selectListByTemplateId(template.getFieldId()));
    }

    /* ==================== 新建 / 编辑 / 启停用 ==================== */

    /**
     * 新建模板：校验模板字段与节点后落库，初始版本号 1。
     *
     * @param vo 模板请求体（含 nodes 节点列表）
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

        List<SysProcessTemplateNode> nodes = validateAndNormalizeNodes(template.getFieldId(), template.getFieldModelName(), vo.getNodes());
        if (!nodes.isEmpty()) {
            nodeDao.insertBatch(template.getFieldId(), nodes);
        }
        log.info("process template saved: id={}, name={}, model={}, nodes={}",
                template.getFieldId(), template.getFieldName(), template.getFieldModelName(), nodes.size());
        return load(template.getFieldId());
    }

    /**
     * 编辑模板：将历史版本的模板与全部节点字段信息统一归档到
     * sys_process_template_history.field_content，再更新现版本（版本号 +1）并全量替换节点。
     *
     * @param vo 模板请求体（必须携带 fieldId）
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

        // 归档当前版本（模板 + 节点统一序列化）
        List<SysProcessTemplateNode> existNodes = nodeDao.selectListByTemplateId(exist.getFieldId());
        archiveHistory(exist, existNodes);

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

        // 节点全量替换（先删后插）
        List<SysProcessTemplateNode> nodes = validateAndNormalizeNodes(exist.getFieldId(), exist.getFieldModelName(), vo.getNodes());
        nodeDao.deleteByTemplateId(exist.getFieldId());
        if (!nodes.isEmpty()) {
            nodeDao.insertBatch(exist.getFieldId(), nodes);
        }
        log.info("process template updated: id={}, name={}, version={}, nodes={}",
                exist.getFieldId(), exist.getFieldName(), exist.getFieldVersion(), nodes.size());
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

    private SysProcessTemplateVo toDetailVo(SysProcessTemplate template, List<SysProcessTemplateNode> nodes) {
        SysProcessTemplateVo vo = toBasicVo(template);
        if (null != nodes) {
            vo.setNodes(nodes.stream().map(this::toNodeVo).toList());
        }
        return vo;
    }

    private SysProcessTemplateNodeVo toNodeVo(SysProcessTemplateNode node) {
        SysProcessTemplateNodeVo vo = new SysProcessTemplateNodeVo();
        vo.setFieldId(node.getFieldId());
        vo.setFieldNo(node.getFieldNo());
        vo.setFieldNodeName(node.getFieldNodeName());
        vo.setFieldNodeType(node.getFieldNodeType());
        SysProcessTemplateNodeConfigDo config = parseNodeConfig(node.getFieldNodeConfig());
        if (null != config) {
            vo.setHandlerSource(config.getHandlerSource());
            vo.setHandlerIds(config.getHandlerIds());
            vo.setHandlerNames(config.getHandlerNames());
            vo.setHandlerField(config.getHandlerField());
            vo.setApproveMode(config.getApproveMode());
            vo.setTriggerType(config.getTriggerType());
            vo.setCondition(config.getCondition());
            vo.setAutoSkip(config.getAutoSkip());
            vo.setAutoContinueSkip(config.getAutoContinueSkip());
            vo.setOps(config.getOps());
        }
        return vo;
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
     * 节点校验与归一化：
     * <ul>
     * <li>必须包含且仅包含一个开始节点 START、草稿节点 DRAFT、结束节点 END（固定节点无配置）；</li>
     * <li>节点类型必须为合法枚举；</li>
     * <li>编号归一：START=1、DRAFT=2、其余节点按传入顺序从 3 顺延、END 置末
     *     （即结束节点编号随节点的创建往后顺延）；</li>
     * <li>审批节点（APPROVE）配置校验：审批来源（默认组织架构；组织架构审批人必填 /
     *     表单字段审批人字段必须定义在分类 fields 字典）、审批模式/节点触发/自动跳过取值合法并给默认值、
     *     条件判断时触发条件必须为判断公式且占位符字段存在于文档实体类。</li>
     * </ul>
     */
    private List<SysProcessTemplateNode> validateAndNormalizeNodes(String templateId, String modelName,
                                                                   List<SysProcessTemplateNodeVo> nodeVos) {
        if (null == nodeVos || nodeVos.isEmpty()) {
            throw new IllegalArgumentException("模板至少需要开始/草稿/结束三个固定节点");
        }
        SysProcessTemplateNodeVo start = null;
        SysProcessTemplateNodeVo draft = null;
        SysProcessTemplateNodeVo end = null;
        List<SysProcessTemplateNodeVo> middles = new ArrayList<>();
        for (SysProcessTemplateNodeVo vo : nodeVos) {
            String type = vo.getFieldNodeType();
            if (StringUtil.isEmpty(type) || !NODE_TYPES.contains(type)) {
                throw new IllegalArgumentException("非法的节点类型: " + type);
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

        List<SysProcessTemplateNode> result = new ArrayList<>();
        result.add(toFixedNode(templateId, start, FIXED_NODE_NO_START, NODE_TYPE_START, FIXED_NODE_NAME_START));
        result.add(toFixedNode(templateId, draft, FIXED_NODE_NO_DRAFT, NODE_TYPE_DRAFT, FIXED_NODE_NAME_DRAFT));
        int no = FIXED_NODE_NO_DRAFT + 1;
        String className = SysProcessTemplateProperties.getClassName(modelName);
        for (SysProcessTemplateNodeVo vo : middles) {
            result.add(toApproveNode(templateId, vo, no++, className, modelName));
        }
        result.add(toFixedNode(templateId, end, no, NODE_TYPE_END, FIXED_NODE_NAME_END));
        return result;
    }

    /** 固定节点：强制编号/名称/类型，不保存节点配置。 */
    private static SysProcessTemplateNode toFixedNode(String templateId, SysProcessTemplateNodeVo vo,
                                                      int no, String type, String name) {
        SysProcessTemplateNode node = new SysProcessTemplateNode();
        node.setFieldId(vo.getFieldId());
        node.setFieldTemplateId(templateId);
        node.setFieldNo(no);
        node.setFieldNodeName(name);
        node.setFieldNodeType(type);
        node.setFieldNodeConfig(null);
        return node;
    }

    /** 审批/机器人节点：编号顺延，审批节点校验并序列化配置。 */
    private SysProcessTemplateNode toApproveNode(String templateId, SysProcessTemplateNodeVo vo, int no,
                                                 String className, String modelName) {
        SysProcessTemplateNode node = new SysProcessTemplateNode();
        node.setFieldId(vo.getFieldId());
        node.setFieldTemplateId(templateId);
        node.setFieldNo(no);
        node.setFieldNodeName(StringUtil.isEmpty(vo.getFieldNodeName()) ? "审批节点" + no : vo.getFieldNodeName().trim());
        node.setFieldNodeType(vo.getFieldNodeType());
        if (NODE_TYPE_APPROVE.equals(vo.getFieldNodeType())) {
            validateApproveConfig(vo, className, modelName);
            node.setFieldNodeConfig(toNodeConfigJson(vo));
        }
        return node;
    }

    /**
     * 审批节点配置校验：
     * 审批来源默认组织架构；来源为组织架构时审批人（地址本，多选，限岗位+用户）必填，
     * 来源为表单字段时审批人字段必填且必须定义在分类 process_template.${fieldModelName}.fields 字典中；
     * 审批模式默认单签；节点触发默认固定；
     * 条件判断时必须填写触发条件，且必须是判断公式、${field} 占位符字段必须是文档实体类中存在的字段；
     * 是否允许自动跳过默认跟随系统；操作标识开关的 key 必须定义在分类
     * process_template.${fieldModelName}.ops 字典中（未配置字典的分类不允许配置任何操作标识）。
     *
     * @param className 当前模板所属模块的文档实体类（可为 null，此时跳过占位符字段校验）
     * @param modelName 当前模板所属模块（process_template 二级配置项，用于操作标识/字段字典校验）
     */
    private void validateApproveConfig(SysProcessTemplateNodeVo vo, String className, String modelName) {
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

    /** 序列化审批节点配置为 JSON 字符串（默认值补齐后落库；操作标识仅保留开启项，未开启默认不允许）。 */
    private String toNodeConfigJson(SysProcessTemplateNodeVo vo) {
        SysProcessTemplateNodeConfigDo config = new SysProcessTemplateNodeConfigDo();
        // 审批来源：默认组织架构；组织架构来源写 handlerIds/handlerNames，表单字段来源写 handlerField
        String handlerSource = StringUtil.isEmpty(vo.getHandlerSource())
                ? HANDLER_SOURCE_ORG : vo.getHandlerSource().trim();
        config.setHandlerSource(handlerSource);
        if (HANDLER_SOURCE_FIELD.equals(handlerSource)) {
            config.setHandlerField(vo.getHandlerField().trim());
        } else {
            config.setHandlerIds(vo.getHandlerIds());
            config.setHandlerNames(vo.getHandlerNames());
        }
        config.setApproveMode(StringUtil.isEmpty(vo.getApproveMode()) ? APPROVE_MODE_SINGLE : vo.getApproveMode().trim());
        config.setTriggerType(StringUtil.isEmpty(vo.getTriggerType()) ? TRIGGER_TYPE_FIXED : vo.getTriggerType().trim());
        if (StringUtil.isNotEmpty(vo.getCondition())) {
            config.setCondition(vo.getCondition().trim());
        }
        config.setAutoSkip(StringUtil.isEmpty(vo.getAutoSkip()) ? AUTO_SKIP_SYSTEM : vo.getAutoSkip().trim());
        config.setAutoContinueSkip(StringUtil.isEmpty(vo.getAutoContinueSkip())
                ? AUTO_CONTINUE_SKIP_SYSTEM : vo.getAutoContinueSkip().trim());
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
        return writeJson(config);
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

    /** 归档历史版本：模板与全部节点字段信息统一序列化保存到 field_content。 */
    private void archiveHistory(SysProcessTemplate template, List<SysProcessTemplateNode> nodes) {
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
            snapshot.setFieldNodeName(node.getFieldNodeName());
            snapshot.setFieldNodeType(node.getFieldNodeType());
            snapshot.setFieldNodeConfig(parseNodeConfig(node.getFieldNodeConfig()));
            nodeSnapshots.add(snapshot);
        }
        content.setNodes(nodeSnapshots);

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
