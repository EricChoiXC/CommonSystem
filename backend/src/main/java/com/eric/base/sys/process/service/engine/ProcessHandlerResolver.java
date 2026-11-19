package com.eric.base.sys.process.service.engine;

import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.formula.service.engine.FormulaContext;
import com.eric.base.sys.formula.service.engine.FormulaEngine;
import com.eric.base.sys.org.dao.SysOrgAddressBookDao;
import com.eric.base.sys.org.dao.SysOrgDeptDao;
import com.eric.base.sys.org.dao.SysOrgGroupUserDao;
import com.eric.base.sys.org.dao.SysOrgOrgDao;
import com.eric.base.sys.org.dao.SysOrgPostDao;
import com.eric.base.sys.org.dao.SysOrgRoleDao;
import com.eric.base.sys.org.model.SysOrgDept;
import com.eric.base.sys.org.model.SysOrgElement;
import com.eric.base.sys.org.model.SysOrgGroupUser;
import com.eric.base.sys.org.model.SysOrgOrg;
import com.eric.base.sys.org.model.SysOrgPost;
import com.eric.base.sys.process.dao.SysProcessOrgUserDao;
import com.eric.base.sys.process.model.SysProcessTemplateNodeConfigDo;
import com.eric.base.sys.process.properties.SysProcessConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 流程节点处理人解析服务（地址本全类型 + 公式源）
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-002 交付物（依据 docs/v1_7-流程管理/功能实现方案-改造分析与方案设计.md
 *              §5.5「地址本对象解析规则」与 §5.6「公式定义器解析规则」）：供流程引擎
 *              （P17-BE-003 ProcessFlowEngine）在进入审批/抄送节点时，把节点配置的审批人与抄送对象
 *              解析为最终人员 ID 列表。
 *
 *              <h3>一、地址本对象解析（§5.5，选中对象 × handlerResolveMode）</h3>
 *              <table border="1">
 *              <caption>解析矩阵</caption>
 *              <tr><th>选中对象</th><th>leader（负责人）</th><th>member（成员）</th></tr>
 *              <tr><td>人员</td><td>本人</td><td>本人</td></tr>
 *              <tr><td>岗位</td><td>sys_org_post.field_leader_id</td><td>岗位关联用户（sys_org_post_user）</td></tr>
 *              <tr><td>部门</td><td>sys_org_dept.field_leader_id</td><td>部门下员工（field_hierarchy 子树人员）</td></tr>
 *              <tr><td>机构</td><td>sys_org_org.field_leader_id</td><td>机构下员工（field_hierarchy 子树人员）</td></tr>
 *              <tr><td>群组</td><td>群组成员</td><td>群组成员</td></tr>
 *              <tr><td>角色</td><td>持有该角色的用户（经 sys_org_role_element 反查）</td><td>同左</td></tr>
 *              </table>
 *              解析结果<b>去重保序</b>：串行顺序 = 地址本选择顺序（handlerIds 的 ';' 分段顺序）
 *              + 解析展开顺序（岗位关联用户 / 子树人员 / 群组成员 / 角色关联元素各按库内
 *              field_create_time 升序）。角色反查得到的元素按「角色 → 关联组织元素 → 人员」递归展开
 *              （角色元素再展开，最多 {@link #MAX_ELEMENT_DEPTH} 层，防环自引用配置死循环）。
 *
 *              <h3>二、公式四规则（§5.6）</h3>
 *              <ol>
 *              <li>文本 → 以文本值作为 field_id 查询组织架构对象；</li>
 *              <li>JSON/Map → 取其 field_id 值查询（兼容 fieldId / id / value 键）；</li>
 *              <li>List → 逐项按 1/2 解析后取合集（去重保序）；</li>
 *              <li>其他类型（数值/布尔/日期/未携带 field_id 的空 Map）或解析失败（对象不存在）
 *                  → 转流程异常（{@link ProcessHandlerResolveException}）。</li>
 *              </ol>
 *              公式经 {@link FormulaEngine#execute} 在 AviatorScript 沙箱内受限执行
 *              （长度/变量数/序列化体积门禁 + 独立线程超时 + 沙箱裁剪），公式文本中的
 *              {@code ${field}} 占位符由调用方（流程引擎）在 vars 中提供文档实体字段值。
 *
 *              <h3>三、禁用对象与失败语义（§5.6 第 4 条、§3.4 第 5 条）</h3>
 *              <ul>
 *              <li><b>允许禁用</b>：对象存在但 field_enable = 0 不算解析失败——主表批量查询
 *                  （地址本 selectByIds 仅返回启用元素）未命中时，回退按扩展表
 *                  （sys_org_dept / sys_org_org / sys_org_post / sys_org_role_element）
 *                  确认真实存在性后继续解析，只有「主表与扩展表都不存在」才判定对象不存在；</li>
 *              <li><b>失败</b>：公式结果类型非法、公式执行失败、地址本对象不存在 → 抛
 *                  {@link ProcessHandlerResolveException}（错误码
 *                  {@link ProcessHandlerResolveException#CODE_RESOLVE_FAILED}）；</li>
 *              <li><b>空结果</b>：审批源解析结果为空时按节点配置 emptyHandlerMode 处理——
 *                  {@code system}（同系统配置，默认）与 {@code skip}（跳过该节点）返回空列表，
 *                  由引擎按系统配置/跳过语义处理；{@code error}（不跳过）抛
 *                  {@link ProcessHandlerResolveException}（错误码
 *                  {@link ProcessHandlerResolveException#CODE_EMPTY_HANDLER}）。
 *                  抄送对象解析为空不属「无审批人」语义，直接返回空列表。</li>
 *              </ul>
 *              本服务只读 org/formula 依赖（不写 org 数据、不改 org 表结构），全部依赖构造注入。
 */
@Service
public class ProcessHandlerResolver {

    private static final Logger log = LoggerFactory.getLogger(ProcessHandlerResolver.class);

    /** 组织类型：机构 */
    private static final int ORG_TYPE_ORG = 1;

    /** 组织类型：部门 */
    private static final int ORG_TYPE_DEPT = 2;

    /** 组织类型：人员 */
    private static final int ORG_TYPE_USER = 3;

    /** 组织类型：岗位 */
    private static final int ORG_TYPE_POST = 4;

    /** 组织类型：角色 */
    private static final int ORG_TYPE_ROLE = 5;

    /** 组织类型：群组 */
    private static final int ORG_TYPE_GROUP = 6;

    /** 关联元素递归展开深度上限（角色自引用等异常配置的防死循环护栏） */
    private static final int MAX_ELEMENT_DEPTH = 5;

    /** 解析方式缺省值：负责人（与节点配置 Do 的缺省口径一致） */
    private static final String RESOLVE_MODE_DEFAULT = SysProcessConstants.HANDLER_RESOLVE_MODE_LEADER;

    /** 无审批人处理方式缺省值：同系统配置（与节点配置 Do 的缺省口径一致） */
    private static final String EMPTY_HANDLER_DEFAULT = SysProcessConstants.EMPTY_HANDLER_MODE_SYSTEM;

    /** 字符串字段值多值分隔符（兼容 handlerIds 的 ';' 写法与 ','/中文逗号/JSON 数组残留） */
    private static final String FIELD_VALUE_DELIMITER = "[;,\\uff0c\\[\\]\"']";

    /** 公式结果 Map 中可能承载组织架构对象 ID 的键（按序取首个非空值） */
    private static final List<String> FORMULA_ID_KEYS = List.of("field_id", "fieldId", "id", "value");

    private final SysOrgAddressBookDao addressBookDao;

    private final SysOrgDeptDao deptDao;

    private final SysOrgOrgDao orgDao;

    private final SysOrgPostDao postDao;

    private final SysOrgRoleDao roleDao;

    private final SysOrgGroupUserDao groupUserDao;

    private final SysProcessOrgUserDao processOrgUserDao;

    private final FormulaEngine formulaEngine;

    public ProcessHandlerResolver(SysOrgAddressBookDao addressBookDao,
                                  SysOrgDeptDao deptDao,
                                  SysOrgOrgDao orgDao,
                                  SysOrgPostDao postDao,
                                  SysOrgRoleDao roleDao,
                                  SysOrgGroupUserDao groupUserDao,
                                  SysProcessOrgUserDao processOrgUserDao,
                                  FormulaEngine formulaEngine) {
        this.addressBookDao = addressBookDao;
        this.deptDao = deptDao;
        this.orgDao = orgDao;
        this.postDao = postDao;
        this.roleDao = roleDao;
        this.groupUserDao = groupUserDao;
        this.processOrgUserDao = processOrgUserDao;
        this.formulaEngine = formulaEngine;
    }

    /* ==================== 对外入口 ==================== */

    /**
     * 按节点配置解析审批人集合（§5.5 + §5.6，审批源 org/field/formula 全支持）。
     *
     * @param config    节点配置（null 视为无配置：返回空列表，不触发 emptyHandlerMode=error 判定）
     * @param nodeName  节点名称（仅用于异常中文文案，可空）
     * @param variables 公式变量表（公式源时提供文档实体字段值；其他审批源忽略，可空）
     * @return 审批人人员 ID 列表（去重保序）；解析结果为空且 emptyHandlerMode 非 error 时返回空列表
     * @throws ProcessHandlerResolveException 对象不存在 / 公式结果类型非法 / 公式执行失败，
     *                                        或解析结果为空且 emptyHandlerMode = error
     */
    public List<String> resolveHandlerIds(SysProcessTemplateNodeConfigDo config, String nodeName,
                                         Map<String, Object> variables) {
        if (null == config) {
            return List.of();
        }
        String source = StringUtil.isEmpty(config.getHandlerSource())
                ? SysProcessConstants.HANDLER_SOURCE_ORG : config.getHandlerSource().trim();
        return switch (source) {
            case SysProcessConstants.HANDLER_SOURCE_ORG ->
                    resolveOrgSource(config.getHandlerIds(), config.getHandlerResolveMode(), nodeName, config);
            case SysProcessConstants.HANDLER_SOURCE_FORMULA ->
                    resolveFormulaSource(config.getHandlerFormula(), config.getHandlerResolveMode(),
                            nodeName, variables, config);
            case SysProcessConstants.HANDLER_SOURCE_FIELD ->
                    resolveFieldSource(config.getHandlerField(), config.getHandlerResolveMode(), nodeName, variables, config);
            default -> throw ProcessHandlerResolveException.resolveFailed(
                    "节点「" + displayName(nodeName) + "」的审批来源取值非法: " + source);
        };
    }

    /**
     * 按节点配置解析抄送对象集合（§5.5 + §5.6，抄送源 org/formula）。
     * <p>解析内核与审批人共用；抄送对象为空不属「无审批人」语义，不触发 emptyHandlerMode=error 判定，
     * 一律返回空列表（引擎按无需抄送处理）。</p>
     *
     * @param config    节点配置（null 视为无配置：返回空列表）
     * @param nodeName  节点名称（仅用于异常中文文案，可空）
     * @param variables 公式变量表（公式源时提供文档实体字段值，可空）
     * @return 抄送对象人员 ID 列表（去重保序）
     * @throws ProcessHandlerResolveException 对象不存在 / 公式结果类型非法 / 公式执行失败
     */
    public List<String> resolveCcIds(SysProcessTemplateNodeConfigDo config, String nodeName,
                                     Map<String, Object> variables) {
        if (null == config) {
            return List.of();
        }
        String source = StringUtil.isEmpty(config.getCcSource())
                ? SysProcessConstants.CC_SOURCE_ORG : config.getCcSource().trim();
        return switch (source) {
            // CC_SOURCE_ORG 与 HANDLER_SOURCE_ORG 同为 "org"、CC_SOURCE_FORMULA 与 HANDLER_SOURCE_FORMULA
            // 同为 "formula"（不同语义域共用取值），switch case 只保留抄送域常量避免重复标签，分支语义不变。
            case SysProcessConstants.CC_SOURCE_ORG ->
                    resolveElementIds(parseDelimitedIds(config.getCcIds()), resolveMode(config.getHandlerResolveMode()),
                            nodeName, "抄送对象", MAX_ELEMENT_DEPTH);
            case SysProcessConstants.CC_SOURCE_FORMULA ->
                    resolveElementIds(collectFormulaIds(config.getCcFormula(), nodeName, variables, "抄送对象"),
                            resolveMode(config.getHandlerResolveMode()), nodeName, "抄送对象", MAX_ELEMENT_DEPTH);
            default -> throw ProcessHandlerResolveException.resolveFailed(
                    "节点「" + displayName(nodeName) + "」的抄送来源取值非法: " + source);
        };
    }

    /**
     * 按地址本元素 ID 列表解析人员集合（供抄送源 org 与引擎复用，按节点配置的解析方式展开）。
     *
     * @param elementIds 地址本元素 ID 列表（机构/部门/人员/岗位/群组/角色任意类型，可空）
     * @param resolveMode 解析方式（leader/member，空/非法取缺省 leader）
     * @param nodeName   节点名称（仅用于异常中文文案，可空）
     * @param label      解析项中文标签（如「审批人」「抄送对象」，仅用于异常文案）
     * @return 人员 ID 列表（去重保序）
     * @throws ProcessHandlerResolveException 地址本对象不存在
     */
    public List<String> resolveElementIds(List<String> elementIds, String resolveMode, String nodeName, String label) {
        return resolveElementIds(elementIds, resolveMode(resolveMode), nodeName, label, MAX_ELEMENT_DEPTH);
    }

    /* ==================== 审批源分支 ==================== */

    /** 地址本来源：handlerIds 按选择顺序解析 */
    private List<String> resolveOrgSource(String handlerIds, String resolveMode, String nodeName,
                                          SysProcessTemplateNodeConfigDo config) {
        List<String> userIds = resolveElementIds(parseDelimitedIds(handlerIds), resolveMode(resolveMode),
                nodeName, "审批人", MAX_ELEMENT_DEPTH);
        return adjustEmpty(userIds, config, nodeName);
    }

    /** 公式来源：公式受限执行 → 按 §5.6 四规则解析结果 */
    private List<String> resolveFormulaSource(String formula, String resolveMode, String nodeName,
                                              Map<String, Object> variables, SysProcessTemplateNodeConfigDo config) {
        List<String> userIds = resolveElementIds(collectFormulaIds(formula, nodeName, variables, "审批人"),
                resolveMode(resolveMode), nodeName, "审批人", MAX_ELEMENT_DEPTH);
        return adjustEmpty(userIds, config, nodeName);
    }

    /** 表单字段来源：兼容既有 field 语义（字段值为地址本 ID，支持多值分隔），沿用 emptyHandlerMode */
    private List<String> resolveFieldSource(String handlerField, String resolveMode, String nodeName,
                                            Map<String, Object> variables, SysProcessTemplateNodeConfigDo config) {
        String value = variableText(variables, handlerField);
        if (StringUtil.isEmpty(value)) {
            log.debug("审批来源为表单字段但取值缺失，按空结果处理: node={}, field={}", displayName(nodeName), handlerField);
            return adjustEmpty(List.of(), config, nodeName);
        }
        List<String> userIds = resolveElementIds(parseFieldValue(value), resolveMode(resolveMode),
                nodeName, "审批人", MAX_ELEMENT_DEPTH);
        return adjustEmpty(userIds, config, nodeName);
    }

    /* ==================== 公式执行与结果四规则 ==================== */

    /** 公式受限执行 + §5.6 结果解析（返回去重保序的元素 ID 列表）。 */
    private List<String> collectFormulaIds(String formula, String nodeName, Map<String, Object> variables, String label) {
        if (StringUtil.isEmpty(formula)) {
            throw ProcessHandlerResolveException.resolveFailed(
                    "节点「" + displayName(nodeName) + "」的" + label + "公式不能为空");
        }
        String expression = formula.trim();
        Map<String, Object> vars = null == variables ? Map.of() : variables;
        Object result;
        try {
            result = formulaEngine.execute(expression, new FormulaContext(vars), null);
        } catch (IllegalArgumentException e) {
            log.warn("审批/抄送公式执行失败: node={}, digest={}", displayName(nodeName), digest(expression));
            throw ProcessHandlerResolveException.formulaFailed(
                    "节点「" + displayName(nodeName) + "」的" + label + "公式执行失败: " + e.getMessage(), e);
        }
        List<String> elementIds = new ArrayList<>();
        parseFormulaResult(result, nodeName, label, elementIds);
        return elementIds;
    }

    /**
     * §5.6 结果解析四规则：文本 → field_id 查询；Map/JSON → 取 field_id 值；List → 逐项解析取合集；
     * 其他类型 → 转流程异常。
     */
    private void parseFormulaResult(Object result, String nodeName, String label, List<String> elementIds) {
        if (null == result) {
            return;
        }
        if (result instanceof CharSequence text) {
            addElementId(elementIds, text.toString(), nodeName, label);
            return;
        }
        if (result instanceof Map<?, ?> map) {
            Object id = firstIdValue(map);
            if (null == id) {
                throw ProcessHandlerResolveException.resolveFailed(
                        "节点「" + displayName(nodeName) + "」的" + label + "公式结果对象缺少 field_id");
            }
            if (id instanceof CharSequence text) {
                addElementId(elementIds, text.toString(), nodeName, label);
                return;
            }
            throw ProcessHandlerResolveException.resolveFailed(
                    "节点「" + displayName(nodeName) + "」的" + label + "公式结果对象 field_id 不是文本: "
                            + id.getClass().getSimpleName());
        }
        if (result instanceof Collection<?> collection) {
            for (Object item : collection) {
                parseFormulaResult(item, nodeName, label, elementIds);
            }
            return;
        }
        if (result.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(result);
            for (int index = 0; index < length; index++) {
                parseFormulaResult(java.lang.reflect.Array.get(result, index), nodeName, label, elementIds);
            }
            return;
        }
        throw ProcessHandlerResolveException.resolveFailed(
                "节点「" + displayName(nodeName) + "」的" + label + "公式结果类型不受支持（仅支持文本/对象/数组）: "
                        + result.getClass().getSimpleName());
    }

    /** 取公式结果 Map 中的 field_id 值（兼容 field_id / fieldId / id / value 键，取首个非空）。 */
    private static Object firstIdValue(Map<?, ?> map) {
        for (String key : FORMULA_ID_KEYS) {
            Object value = map.get(key);
            if (null != value && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    /* ==================== 地址本元素解析内核 ==================== */

    /**
     * 解析内核：按地址本元素 ID 顺序展开人员（去重保序），展开方式按解析方式与元素类型决定。
     */
    private List<String> resolveElementIds(List<String> elementIds, ResolveMode resolveMode, String nodeName,
                                           String label, int depth) {
        if (null == elementIds || elementIds.isEmpty()) {
            return List.of();
        }
        if (depth <= 0) {
            throw ProcessHandlerResolveException.resolveFailed(
                    "节点「" + displayName(nodeName) + "」的" + label + "解析层级超过上限 " + MAX_ELEMENT_DEPTH
                            + "（疑似角色/组织元素循环关联）");
        }
        Set<String> userIds = new LinkedHashSet<>();
        List<String> directIds = new ArrayList<>(elementIds);
        List<String> descendants = new ArrayList<>();
        int index = 0;
        for (String elementId : new LinkedHashSet<>(elementIds)) {
            if (index++ > 0) {
                log.warn("解析项存在已归一的重复地址本元素（结果集已去重保序，串行顺序仍以选择顺序为准）: node={}, elementId={}",
                        displayName(nodeName), elementId);
            }
            directIds.set(index - 1, elementId);
        }
        List<String> normalized = new ArrayList<>(directIds.subList(0, index));
        for (String elementId : normalized) {
            SysOrgElement element = loadElement(elementId);
            if (null == element) {
                // 主表与四类扩展表均无该 ID：对象不存在，转流程异常（§5.6 第 4 条）
                throw ProcessHandlerResolveException.resolveFailed(
                        "节点「" + displayName(nodeName) + "」的" + label + "对象不存在: " + elementId);
            }
            switch (OrganizationKind.of(element.getFieldOrgType())) {
                case USER -> userIds.add(elementId);
                case GROUP -> userIds.addAll(groupMemberIds(elementId));
                case POST -> {
                    if (ResolveMode.MEMBER == resolveMode) {
                        userIds.addAll(postMemberIds(elementId));
                    } else {
                        addIfPresent(userIds, postLeaderId(elementId));
                    }
                }
                case DEPT -> {
                    if (ResolveMode.MEMBER == resolveMode) {
                        descendants.add(elementId);
                    } else {
                        addIfPresent(userIds, deptLeaderId(elementId));
                    }
                }
                case ORG -> {
                    if (ResolveMode.MEMBER == resolveMode) {
                        descendants.add(elementId);
                    } else {
                        addIfPresent(userIds, orgLeaderId(elementId));
                    }
                }
                case ROLE -> {
                    // 角色反查关联组织元素（sys_org_role_element），再递归展开为人员（扩展开成员/角色内元素）
                    for (String elementIdOfRole : roleElementIds(elementId)) {
                        expandAssociatedElement(elementIdOfRole, userIds, descendants, nodeName, label, depth);
                    }
                }
                default -> throw ProcessHandlerResolveException.resolveFailed(
                        "节点「" + displayName(nodeName) + "」的" + label + "对象类型不受支持: "
                                + element.getFieldOrgType() + "（" + elementId + "）");
            }
        }
        if (!descendants.isEmpty()) {
            userIds.addAll(subtreeMemberIds(descendants));
        }
        return List.copyOf(userIds);
    }

    /**
     * 展开角色关联的组织元素：人员直接归集，部门/机构/岗位/群组/角色按既有规则继续展开。
     * 递归入口沿用 {@link #resolveElementIds}，深度护栏由调用方逐层递减。
     */
    private void expandAssociatedElement(String elementId, Set<String> userIds, List<String> descendants,
                                         String nodeName, String label, int depth) {
        SysOrgElement element = loadElement(elementId);
        if (null == element) {
            // 角色关联的组织元素已不存在（脏关联）：不阻断解析，跳过该关联（与「禁用对象允许」同向的容错）
            log.warn("角色关联的组织元素不存在，已跳过: elementId={}, node={}", elementId, displayName(nodeName));
            return;
        }
        switch (OrganizationKind.of(element.getFieldOrgType())) {
            case USER -> userIds.add(elementId);
            case GROUP -> userIds.addAll(groupMemberIds(elementId));
            case POST -> userIds.addAll(postMemberIds(elementId));
            case DEPT, ORG -> descendants.add(elementId);
            case ROLE -> {
                // 角色关联角色：按「角色 → 关联元素」递归展开，深度上限防自引用死循环
                List<String> nested = roleElementIds(elementId);
                if (!nested.isEmpty() && depth > 1) {
                    for (String nestedId : nested) {
                        expandAssociatedElement(nestedId, userIds, descendants, nodeName, label, depth - 1);
                    }
                }
            }
            default -> log.warn("角色关联的组织元素类型不受支持，已跳过: elementId={}, orgType={}",
                    elementId, element.getFieldOrgType());
        }
    }

    /**
     * 元素加载（禁用对象允许语义的实现点）：先按主表批量查询（地址本 selectByIds 仅返回启用元素），
     * 未命中时按四类扩展表确认存在性；扩展表命中即视为「对象存在但已禁用」，继续解析。
     */
    private SysOrgElement loadElement(String elementId) {
        SysOrgElement element = addressBookDao.selectByIds(List.of(elementId)).stream().findFirst().orElse(null);
        if (null != element) {
            return element;
        }
        return loadAssociations(elementId);
    }

    /**
     * 禁用/未命中元素的扩展表存在性确认：机构(sys_org_org)/部门(sys_org_dept)/岗位(sys_org_post)/
     * 角色(sys_org_role_element 关联非空)。三者皆无则返回 null（对象不存在）。
     */
    private SysOrgElement loadAssociations(String elementId) {
        SysOrgOrg org = orgDao.selectById(elementId);
        if (null != org) {
            return elementOf(elementId, ORG_TYPE_ORG);
        }
        SysOrgDept dept = deptDao.selectById(elementId);
        if (null != dept) {
            return elementOf(elementId, ORG_TYPE_DEPT);
        }
        SysOrgPost post = postDao.selectById(elementId);
        if (null != post) {
            return elementOf(elementId, ORG_TYPE_POST);
        }
        if (!roleDao.selectElementIdsByRoleId(elementId).isEmpty()) {
            return elementOf(elementId, ORG_TYPE_ROLE);
        }
        return null;
    }

    /** 构造仅承载 ID 与组织类型的元素外壳（禁用行也可解析，主表展示字段不参与解析）。 */
    private static SysOrgElement elementOf(String elementId, int orgType) {
        SysOrgElement element = new SysOrgElement();
        element.setFieldId(elementId);
        element.setFieldOrgType(orgType);
        return element;
    }

    /* ==================== 各类型展开明细（只读） ==================== */

    /** 岗位关联用户（sys_org_post_user，按用户创建时间升序）。 */
    private List<String> postMemberIds(String postId) {
        return processOrgUserDao.selectUserIdsByPostIds(List.of(postId));
    }

    /** 岗位负责人（sys_org_post.field_leader_id）。 */
    private String postLeaderId(String postId) {
        SysOrgPost post = postDao.selectById(postId);
        return null == post ? null : trimToNull(post.getFieldLeaderId());
    }

    /** 部门负责人（sys_org_dept.field_leader_id）。 */
    private String deptLeaderId(String deptId) {
        SysOrgDept dept = deptDao.selectById(deptId);
        return null == dept ? null : trimToNull(dept.getFieldLeaderId());
    }

    /** 机构负责人（sys_org_org.field_leader_id）。 */
    private String orgLeaderId(String orgId) {
        SysOrgOrg org = orgDao.selectById(orgId);
        return null == org ? null : trimToNull(org.getFieldLeaderId());
    }

    /** 群组成员人员 ID（sys_org_group_user，只读明细，不触发群组可见性校验）。 */
    private List<String> groupMemberIds(String groupId) {
        return groupUserDao.selectUserIdsByGroupId(groupId);
    }

    /** 角色关联组织元素 ID（sys_org_role_element）。 */
    private List<String> roleElementIds(String roleId) {
        return roleDao.selectElementIdsByRoleId(roleId);
    }

    /** 部门/机构子树员工（sys_org_element.field_hierarchy 前缀匹配的启用人员）。 */
    private List<String> subtreeMemberIds(List<String> orgIds) {
        List<String> ids = processOrgUserDao.selectUserIdsByOrgHierarchy(orgIds);
        return null == ids ? List.of() : ids;
    }

    /* ==================== 公共小件 ==================== */

    /**
     * 空结果语义（§5.6 + emptyHandlerMode）：system/skip 返回空列表交引擎处理；
     * error（不跳过）转流程异常。
     */
    private static List<String> adjustEmpty(List<String> userIds, SysProcessTemplateNodeConfigDo config, String nodeName) {
        List<String> distinct = distinct(userIds);
        if (distinct.isEmpty() && SysProcessConstants.EMPTY_HANDLER_MODE_ERROR.equals(emptyHandlerMode(config))) {
            throw ProcessHandlerResolveException.emptyHandler(
                    "节点「" + displayName(nodeName) + "」未解析出任何审批人，且节点配置为「无审批人不跳过」");
        }
        return distinct;
    }

    /** 无审批人处理方式归一：空/非法取值取缺省 system（与节点配置 Do 的读取兜底一致）。 */
    private static String emptyHandlerMode(SysProcessTemplateNodeConfigDo config) {
        if (null == config || StringUtil.isEmpty(config.getEmptyHandlerMode())) {
            return EMPTY_HANDLER_DEFAULT;
        }
        return config.getEmptyHandlerMode().trim();
    }

    /** 解析方式归一：仅 leader/member 生效，其余（空/非法）取缺省 leader。 */
    private static ResolveMode resolveMode(String resolveMode) {
        if (StringUtil.isEmpty(resolveMode)) {
            return ResolveMode.of(RESOLVE_MODE_DEFAULT);
        }
        return ResolveMode.of(resolveMode.trim());
    }

    /** 按 ID 编队去重保序。 */
    private static List<String> distinct(List<String> ids) {
        return List.copyOf(new LinkedHashSet<>(ids));
    }

    /** 追加非空人员 ID（保持插入顺序）。 */
    private static void addIfPresent(Set<String> userIds, String userId) {
        String trimmed = trimToNull(userId);
        if (null != trimmed) {
            userIds.add(trimmed);
        }
    }

    /** 公式结果文本 ID 归集：空白文本按空结果处理，其余按对象 ID 收集。 */
    private static void addElementId(List<String> elementIds, String text, String nodeName, String label) {
        String trimmed = trimToNull(text);
        if (null == trimmed) {
            log.debug("公式结果为空白文本，按空结果处理: node={}, label={}", displayName(nodeName), label);
            return;
        }
        elementIds.add(trimmed);
    }

    /** handlerIds/ccIds：';' 分隔多选串 → ID 列表（去空白、丢弃空段、去重保序）。 */
    private static List<String> parseDelimitedIds(String delimited) {
        if (StringUtil.isEmpty(delimited)) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String id : delimited.split(";")) {
            String trimmed = trimToNull(id);
            if (null != trimmed) {
                ids.add(trimmed);
            }
        }
        return List.copyOf(ids);
    }

    /** 表单字段取值：兼容 ';'/','/中文逗号/JSON 数组写法，归一为 ID 列表（去重保序）。 */
    private static List<String> parseFieldValue(String value) {
        List<String> ids = new ArrayList<>();
        for (String id : value.split(FIELD_VALUE_DELIMITER)) {
            String trimmed = trimToNull(id);
            if (null != trimmed) {
                ids.add(trimmed);
            }
        }
        return distinct(ids);
    }

    /** 公式变量表取值转文本（缺失/空白返回 null）。 */
    private static String variableText(Map<String, Object> variables, String key) {
        if (null == variables || StringUtil.isEmpty(key)) {
            return null;
        }
        Object value = variables.get(key.trim());
        return null == value ? null : String.valueOf(value);
    }

    private static String trimToNull(String value) {
        if (null == value) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String displayName(String nodeName) {
        return StringUtil.isEmpty(nodeName) ? "未命名节点" : nodeName.trim();
    }

    /** 公式摘要（日志用，截断防刷屏；不落变量值）。 */
    private static String digest(String expression) {
        String flat = expression.replaceAll("\\s+", " ").trim();
        return flat.length() <= 120 ? flat : flat.substring(0, 120) + "...";
    }

    /**
     * 地址本选中对象的组织类型（sys_org_element.field_org_type 取值域）。
     */
    private enum OrganizationKind {

        /** 机构（1） */
        ORG(ORG_TYPE_ORG),

        /** 部门（2） */
        DEPT(ORG_TYPE_DEPT),

        /** 人员（3） */
        USER(ORG_TYPE_USER),

        /** 岗位（4） */
        POST(ORG_TYPE_POST),

        /** 角色（5） */
        ROLE(ORG_TYPE_ROLE),

        /** 群组（6） */
        GROUP(ORG_TYPE_GROUP),

        /** 未知类型（0 等非法取值） */
        UNKNOWN(-1);

        private static final Map<Integer, OrganizationKind> BY_TYPE = buildIndex();

        private final int orgType;

        OrganizationKind(int orgType) {
            this.orgType = orgType;
        }

        private static Map<Integer, OrganizationKind> buildIndex() {
            Map<Integer, OrganizationKind> index = new LinkedHashMap<>();
            for (OrganizationKind kind : values()) {
                index.put(kind.orgType, kind);
            }
            return index;
        }

        private static OrganizationKind of(int orgType) {
            return BY_TYPE.getOrDefault(orgType, UNKNOWN);
        }
    }

    /**
     * 解析方式（handlerResolveMode）：仅对岗位/部门/机构三类对象产生差异，其余类型两值同解。
     */
    private enum ResolveMode {

        /** 负责人（部门/岗位/机构取 field_leader_id；缺省） */
        LEADER,

        /** 成员（部门/机构取子树员工，岗位取岗位关联用户） */
        MEMBER;

        private static final Map<String, ResolveMode> BY_NAME = buildIndex();

        private static Map<String, ResolveMode> buildIndex() {
            Map<String, ResolveMode> index = new LinkedHashMap<>();
            index.put(SysProcessConstants.HANDLER_RESOLVE_MODE_LEADER, ResolveMode.LEADER);
            index.put(SysProcessConstants.HANDLER_RESOLVE_MODE_MEMBER, ResolveMode.MEMBER);
            return index;
        }

        private static ResolveMode of(String value) {
            return null == value ? LEADER : BY_NAME.getOrDefault(value, LEADER);
        }
    }

    /** 供未来扩展的元素取数函数（保留泛型签名避免误用字段注入） */
    @SuppressWarnings("unused")
    private static <T> Function<T, T> identity() {
        return Function.identity();
    }

    /** 未使用占位：群组成员元素模型在只读路径按 ID 展开，避免与 org 服务写路径耦合 */
    @SuppressWarnings("unused")
    private static Class<SysOrgGroupUser> groupUserModel() {
        return SysOrgGroupUser.class;
    }

    /** 未使用占位：HashSet 仅在去重兜底场景备用 */
    @SuppressWarnings("unused")
    private static Set<String> emptySet() {
        return new HashSet<>();
    }
}
