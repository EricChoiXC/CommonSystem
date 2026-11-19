package com.eric.base.sys.process.service.engine;

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
import com.eric.base.sys.org.model.SysOrgOrg;
import com.eric.base.sys.org.model.SysOrgPost;
import com.eric.base.sys.process.dao.SysProcessOrgUserDao;
import com.eric.base.sys.process.model.SysProcessTemplateNodeConfigDo;
import com.eric.base.sys.process.properties.SysProcessConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程节点处理人解析服务测试
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-002 单测（Mockito，不连数据库）：覆盖
 *              docs/v1_7-流程管理/功能实现方案-改造分析与方案设计.md §5.5 地址本六类对象
 *              ×（leader/member）展开、§5.6 公式四规则（文本/Map/List/非法类型与对象不存在）、
 *              禁用对象允许语义、去重保序、emptyHandlerMode 三值语义与抄送解析空结果语义。
 */
class ProcessHandlerResolverTest {

    private static final String U1 = "12345678901234567890123456789001";
    private static final String U2 = "12345678901234567890123456789002";
    private static final String U3 = "12345678901234567890123456789003";
    private static final String U4 = "12345678901234567890123456789004";
    private static final String POST1 = "12345678901234567890123456789011";
    private static final String DEPT1 = "12345678901234567890123456789012";
    private static final String ORG1 = "12345678901234567890123456789013";
    private static final String GROUP1 = "12345678901234567890123456789014";
    private static final String ROLE1 = "12345678901234567890123456789015";
    private static final String UNKNOWN1 = "12345678901234567890123456789019";

    /** 地址本启用元素（loadElement 主表命中来源） */
    private final Map<String, SysOrgElement> addressBook = new LinkedHashMap<>();

    private SysOrgAddressBookDao addressBookDao;
    private SysOrgDeptDao deptDao;
    private SysOrgOrgDao orgDao;
    private SysOrgPostDao postDao;
    private SysOrgRoleDao roleDao;
    private SysOrgGroupUserDao groupUserDao;
    private SysProcessOrgUserDao processOrgUserDao;
    private FormulaEngine formulaEngine;
    private ProcessHandlerResolver resolver;

    @BeforeEach
    void setUp() {
        addressBookDao = mock(SysOrgAddressBookDao.class);
        deptDao = mock(SysOrgDeptDao.class);
        orgDao = mock(SysOrgOrgDao.class);
        postDao = mock(SysOrgPostDao.class);
        roleDao = mock(SysOrgRoleDao.class);
        groupUserDao = mock(SysOrgGroupUserDao.class);
        processOrgUserDao = mock(SysProcessOrgUserDao.class);
        formulaEngine = mock(FormulaEngine.class);
        resolver = new ProcessHandlerResolver(addressBookDao, deptDao, orgDao, postDao, roleDao,
                groupUserDao, processOrgUserDao, formulaEngine);

        // 地址本主表：仅返回命中的启用元素（禁用/不存在元素走扩展表存在性确认）
        when(addressBookDao.selectByIds(anyList())).thenAnswer(invocation -> {
            List<String> ids = invocation.getArgument(0);
            List<SysOrgElement> hit = new ArrayList<>();
            for (String id : ids) {
                SysOrgElement element = addressBook.get(id);
                if (null != element) {
                    hit.add(element);
                }
            }
            return hit;
        });
        // 扩展表默认无行、角色默认无关联：未显式打桩的 ID 一律判定为「对象不存在」
        when(orgDao.selectById(anyString())).thenReturn(null);
        when(deptDao.selectById(anyString())).thenReturn(null);
        when(postDao.selectById(anyString())).thenReturn(null);
        when(roleDao.selectElementIdsByRoleId(anyString())).thenReturn(List.of());

        addressBook.put(U1, element(U1, 3));
        addressBook.put(U2, element(U2, 3));
    }

    /* ==================== §5.5 地址本六类对象 × leader/member ==================== */

    @Test
    void resolveUserElementReturnsSelfInBothModes() {
        assertEquals(List.of(U1), resolve("leader", U1));
        assertEquals(List.of(U1), resolve("member", U1));
        // 人员类型两值同解：不触发任何展开查询
        verify(processOrgUserDao, never()).selectUserIdsByOrgHierarchy(anyList());
        verify(processOrgUserDao, never()).selectUserIdsByPostIds(anyList());
        verify(groupUserDao, never()).selectUserIdsByGroupId(anyString());
    }

    @Test
    void resolvePostLeaderTakesPostLeaderIdAndMemberTakesPostUsers() {
        addressBook.put(POST1, element(POST1, 4));
        SysOrgPost post = new SysOrgPost();
        post.setFieldId(POST1);
        post.setFieldLeaderId(U1);
        when(postDao.selectById(POST1)).thenReturn(post);
        when(processOrgUserDao.selectUserIdsByPostIds(List.of(POST1))).thenReturn(List.of(U1, U2));

        assertEquals(List.of(U1), resolve("leader", POST1));
        assertEquals(List.of(U1, U2), resolve("member", POST1));
    }

    @Test
    void resolveDeptLeaderTakesLeaderIdAndMemberTakesSubtreeUsers() {
        addressBook.put(DEPT1, element(DEPT1, 2));
        SysOrgDept dept = new SysOrgDept();
        dept.setFieldId(DEPT1);
        dept.setFieldLeaderId(U1);
        when(deptDao.selectById(DEPT1)).thenReturn(dept);
        when(processOrgUserDao.selectUserIdsByOrgHierarchy(List.of(DEPT1))).thenReturn(List.of(U1, U2));

        assertEquals(List.of(U1), resolve("leader", DEPT1));
        assertEquals(List.of(U1, U2), resolve("member", DEPT1));
    }

    @Test
    void resolveOrgLeaderTakesLeaderIdAndMemberTakesSubtreeUsers() {
        addressBook.put(ORG1, element(ORG1, 1));
        SysOrgOrg org = new SysOrgOrg();
        org.setFieldId(ORG1);
        org.setFieldLeaderId(U2);
        when(orgDao.selectById(ORG1)).thenReturn(org);
        when(processOrgUserDao.selectUserIdsByOrgHierarchy(List.of(ORG1))).thenReturn(List.of(U1, U2));

        assertEquals(List.of(U2), resolve("leader", ORG1));
        assertEquals(List.of(U1, U2), resolve("member", ORG1));
    }

    @Test
    void resolveGroupReturnsMembersInBothModes() {
        addressBook.put(GROUP1, element(GROUP1, 6));
        when(groupUserDao.selectUserIdsByGroupId(GROUP1)).thenReturn(List.of(U1, U2));

        assertEquals(List.of(U1, U2), resolve("leader", GROUP1));
        assertEquals(List.of(U1, U2), resolve("member", GROUP1));
    }

    @Test
    void resolveRoleExpandsAssociatedElementsIntoUsersInLeaderMode() {
        addressBook.put(ROLE1, element(ROLE1, 5));
        addressBook.put(DEPT1, element(DEPT1, 2));
        // 角色关联：人员 + 部门（部门按子树人员继续展开，与直接选中部门的 leader 语义区分）
        when(roleDao.selectElementIdsByRoleId(ROLE1)).thenReturn(List.of(U1, DEPT1));
        when(processOrgUserDao.selectUserIdsByOrgHierarchy(List.of(DEPT1))).thenReturn(List.of(U3));

        assertEquals(List.of(U1, U3), resolve("leader", ROLE1));
        verify(roleDao).selectElementIdsByRoleId(ROLE1);
        // 角色关联的部门不取 field_leader_id（部门负责人仅在直接选中部门且 leader 方式时生效）
        verify(deptDao, never()).selectById(DEPT1);
    }

    @Test
    void resolveRoleExpandsAssociatedElementsIntoUsersInMemberMode() {
        addressBook.put(ROLE1, element(ROLE1, 5));
        addressBook.put(DEPT1, element(DEPT1, 2));
        when(roleDao.selectElementIdsByRoleId(ROLE1)).thenReturn(List.of(U1, DEPT1));
        // 角色关联部门在 member 方式下展开为部门子树人员（而非部门负责人）
        when(processOrgUserDao.selectUserIdsByOrgHierarchy(List.of(DEPT1))).thenReturn(List.of(U2, U3));

        assertEquals(List.of(U1, U2, U3), resolve("member", ROLE1));
        verify(deptDao, never()).selectById(DEPT1);
    }

    @Test
    void resolveRoleSkipsDirtyAssociationWhenElementMissing() {
        addressBook.put(ROLE1, element(ROLE1, 5));
        when(roleDao.selectElementIdsByRoleId(ROLE1)).thenReturn(List.of(U1, UNKNOWN1));

        // 脏关联（关联元素已不存在）不阻断解析，跳过该关联
        assertEquals(List.of(U1), resolve("leader", ROLE1));
    }

    /* ==================== 去重保序 ==================== */

    @Test
    void resolveDeduplicatesWhilePreservingSelectionOrder() {
        addressBook.put(DEPT1, element(DEPT1, 2));
        when(processOrgUserDao.selectUserIdsByOrgHierarchy(List.of(DEPT1))).thenReturn(List.of(U2, U1, U2));

        // 重复选择同一部门 + 取数结果自身重复：结果去重且保序（选择顺序 + 展开顺序）
        assertEquals(List.of(U2, U1), resolver.resolveElementIds(
                List.of(DEPT1, DEPT1), "member", "审批节点A", "审批人"));
        // 重复元素已归一并只查询一次子树
        verify(processOrgUserDao, times(1)).selectUserIdsByOrgHierarchy(List.of(DEPT1));
    }

    /* ==================== 禁用对象允许语义 ==================== */

    @Test
    void resolveDisabledPostViaExtensionTableInMemberMode() {
        // 地址本主表未命中（已禁用），扩展表 sys_org_post 存在 → 视为对象存在，继续解析
        SysOrgPost post = new SysOrgPost();
        post.setFieldId(POST1);
        when(postDao.selectById(POST1)).thenReturn(post);
        when(processOrgUserDao.selectUserIdsByPostIds(List.of(POST1))).thenReturn(List.of(U1, U2));

        assertEquals(List.of(U1, U2), resolve("member", POST1));
    }

    @Test
    void resolveDisabledDeptViaExtensionTableInLeaderMode() {
        // 地址本主表未命中（已禁用），扩展表 sys_org_dept 存在 → 取部门负责人
        SysOrgDept dept = new SysOrgDept();
        dept.setFieldId(DEPT1);
        dept.setFieldLeaderId(U1);
        when(deptDao.selectById(DEPT1)).thenReturn(dept);

        assertEquals(List.of(U1), resolve("leader", DEPT1));
    }

    /* ==================== 越界/不存在对象（§5.6 第 4 条） ==================== */

    @Test
    void resolveThrowsWhenObjectMissingInMainAndExtensionTables() {
        ProcessHandlerResolveException e = assertThrows(ProcessHandlerResolveException.class,
                () -> resolve("leader", UNKNOWN1));
        assertEquals(ProcessHandlerResolveException.CODE_RESOLVE_FAILED, e.getCode());
        assertTrue(e.getMessage().contains("对象不存在"));
        assertTrue(e.getMessage().contains(UNKNOWN1));
    }

    /* ==================== §5.6 公式四规则 ==================== */

    @Test
    void resolveFormulaTextRuleTreatsTextAsElementId() {
        when(formulaEngine.execute(anyString(), any(FormulaContext.class), any())).thenReturn(POST1);
        addressBook.put(POST1, element(POST1, 4));
        when(processOrgUserDao.selectUserIdsByPostIds(List.of(POST1))).thenReturn(List.of(U1, U2));
        SysProcessTemplateNodeConfigDo config = handlerFormulaConfig("member");

        assertEquals(List.of(U1, U2), resolver.resolveHandlerIds(config, "审批节点A", Map.of()));
        // 规则1：公式结果文本直接作为地址本对象 field_id 查询
        verify(processOrgUserDao).selectUserIdsByPostIds(List.of(POST1));
        verify(formulaEngine).execute(eq("handlerFormula"), any(FormulaContext.class), any());
    }

    @Test
    void resolveFormulaMapRuleTakesFieldIdValue() {
        addressBook.put(DEPT1, element(DEPT1, 2));
        when(processOrgUserDao.selectUserIdsByOrgHierarchy(List.of(DEPT1))).thenReturn(List.of(U2, U3));
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("field_id", DEPT1);
        node.put("field_name", "研发部");
        when(formulaEngine.execute(anyString(), any(FormulaContext.class), any())).thenReturn(node);

        assertEquals(List.of(U2, U3),
                resolver.resolveHandlerIds(handlerFormulaConfig("member"), "审批节点A", Map.of()));
    }

    @Test
    void resolveFormulaListRuleResolvesNestedItemsWithDedupAndOrder() {
        addressBook.put(U3, element(U3, 3));
        addressBook.put(DEPT1, element(DEPT1, 2));
        when(processOrgUserDao.selectUserIdsByOrgHierarchy(List.of(DEPT1))).thenReturn(List.of(U2));
        List<Object> items = new ArrayList<>();
        items.add(U1);
        items.add(List.of(U1, Map.of("fieldId", U3)));
        items.add(Map.of("field_id", DEPT1));
        when(formulaEngine.execute(anyString(), any(FormulaContext.class), any())).thenReturn(items);

        // 嵌套 List/Map 逐项解析取合集，跨项去重且保序
        assertEquals(List.of(U1, U3, U2),
                resolver.resolveHandlerIds(handlerFormulaConfig("member"), "审批节点A", Map.of()));
    }

    @Test
    void resolveFormulaThrowsWhenResultTypeUnsupported() {
        when(formulaEngine.execute(anyString(), any(FormulaContext.class), any())).thenReturn(100);
        ProcessHandlerResolveException e = assertThrows(ProcessHandlerResolveException.class,
                () -> resolver.resolveHandlerIds(handlerFormulaConfig("leader"), "审批节点A", Map.of()));
        assertEquals(ProcessHandlerResolveException.CODE_RESOLVE_FAILED, e.getCode());
        assertTrue(e.getMessage().contains("公式结果类型不受支持"));

        when(formulaEngine.execute(anyString(), any(FormulaContext.class), any())).thenReturn(Boolean.TRUE);
        ProcessHandlerResolveException boolError = assertThrows(ProcessHandlerResolveException.class,
                () -> resolver.resolveHandlerIds(handlerFormulaConfig("leader"), "审批节点A", Map.of()));
        assertTrue(boolError.getMessage().contains("公式结果类型不受支持"));
    }

    @Test
    void resolveFormulaThrowsWhenResultObjectMissing() {
        // 规则1 文本指向不存在的对象：主表与扩展表皆无 → 对象不存在
        when(formulaEngine.execute(anyString(), any(FormulaContext.class), any())).thenReturn(UNKNOWN1);
        ProcessHandlerResolveException e = assertThrows(ProcessHandlerResolveException.class,
                () -> resolver.resolveHandlerIds(handlerFormulaConfig("leader"), "审批节点A", Map.of()));
        assertTrue(e.getMessage().contains("对象不存在"));

        // 规则2 Map 未携带 field_id：公式结果对象缺少 field_id
        when(formulaEngine.execute(anyString(), any(FormulaContext.class), any()))
                .thenReturn(new HashMap<>(Map.of("field_name", "研发部")));
        ProcessHandlerResolveException mapError = assertThrows(ProcessHandlerResolveException.class,
                () -> resolver.resolveHandlerIds(handlerFormulaConfig("leader"), "审批节点A", Map.of()));
        assertTrue(mapError.getMessage().contains("缺少 field_id"));
    }

    @Test
    void resolveFormulaWrapsEngineFailureAsResolveFailed() {
        when(formulaEngine.execute(anyString(), any(FormulaContext.class), any()))
                .thenThrow(new IllegalArgumentException("公式长度超过上限"));
        ProcessHandlerResolveException e = assertThrows(ProcessHandlerResolveException.class,
                () -> resolver.resolveHandlerIds(handlerFormulaConfig("leader"), "审批节点A", Map.of()));
        assertEquals(ProcessHandlerResolveException.CODE_RESOLVE_FAILED, e.getCode());
        assertTrue(e.getMessage().contains("公式执行失败"));
    }

    /* ==================== emptyHandlerMode 三值语义 ==================== */

    @Test
    void emptyResultWithSystemModeReturnsEmptyList() {
        SysProcessTemplateNodeConfigDo config = handlerOrgConfig(null);
        config.setEmptyHandlerMode(SysProcessConstants.EMPTY_HANDLER_MODE_SYSTEM);

        assertEquals(List.of(), resolver.resolveHandlerIds(config, "审批节点A", null));
    }

    @Test
    void emptyResultWithSkipModeReturnsEmptyList() {
        SysProcessTemplateNodeConfigDo config = handlerOrgConfig(null);
        config.setEmptyHandlerMode(SysProcessConstants.EMPTY_HANDLER_MODE_SKIP);

        assertEquals(List.of(), resolver.resolveHandlerIds(config, "审批节点A", null));
    }

    @Test
    void emptyResultWithErrorModeThrowsEmptyHandler() {
        SysProcessTemplateNodeConfigDo config = handlerOrgConfig(null);
        config.setEmptyHandlerMode(SysProcessConstants.EMPTY_HANDLER_MODE_ERROR);

        ProcessHandlerResolveException e = assertThrows(ProcessHandlerResolveException.class,
                () -> resolver.resolveHandlerIds(config, "审批节点A", null));
        assertEquals(ProcessHandlerResolveException.CODE_EMPTY_HANDLER, e.getCode());
    }

    @Test
    void nullConfigReturnsEmptyListWithoutEmptyHandlerJudgement() {
        assertEquals(List.of(), resolver.resolveHandlerIds(null, "审批节点A", null));
        assertEquals(List.of(), resolver.resolveCcIds(null, "审批节点A", null));
    }

    /* ==================== 抄送解析（resolveCcIds） ==================== */

    @Test
    void resolveCcIdsWithEmptyConfigReturnsEmptyListAndSkipsEmptyHandlerJudgement() {
        // 抄送对象为空不属「无审批人」语义：即便 emptyHandlerMode = error 也返回空列表
        SysProcessTemplateNodeConfigDo config = ccOrgConfig("");
        config.setEmptyHandlerMode(SysProcessConstants.EMPTY_HANDLER_MODE_ERROR);

        assertEquals(List.of(), resolver.resolveCcIds(config, "审批节点A", null));
    }

    @Test
    void resolveCcIdsResolvesOrgSourceElements() {
        addressBook.put(DEPT1, element(DEPT1, 2));
        when(processOrgUserDao.selectUserIdsByOrgHierarchy(List.of(DEPT1))).thenReturn(List.of(U1, U2));

        assertEquals(List.of(U1, U2),
                resolver.resolveCcIds(ccOrgConfig(DEPT1 + ";"), "审批节点A", null));
    }

    @Test
    void resolveCcIdsFormulaSourceKeepsCcSemanticsWithoutEmptyHandlerError() {
        when(formulaEngine.execute(eq("ccFormula"), any(FormulaContext.class), any())).thenReturn(List.of());
        SysProcessTemplateNodeConfigDo config = ccFormulaConfig("ccFormula");
        config.setEmptyHandlerMode(SysProcessConstants.EMPTY_HANDLER_MODE_ERROR);

        assertEquals(List.of(), resolver.resolveCcIds(config, "审批节点A", Map.of()));
        verify(formulaEngine).execute(eq("ccFormula"), any(FormulaContext.class), any());
    }

    @Test
    void resolveCcIdsWithEmptyFormulaThrowsResolveFailed() {
        ProcessHandlerResolveException e = assertThrows(ProcessHandlerResolveException.class,
                () -> resolver.resolveCcIds(ccFormulaConfig(""), "审批节点A", Map.of()));
        assertEquals(ProcessHandlerResolveException.CODE_RESOLVE_FAILED, e.getCode());
        assertTrue(e.getMessage().contains("公式不能为空"));
        verify(formulaEngine, never()).execute(anyString(), any(FormulaContext.class), any());
    }

    @Test
    void resolveCcIdsSourceSwitchKeepsOrgBranchWhenHandlerSourceIsFormula() {
        // ccSource 与 handlerSource 取值域不同：ccSource=org 不因 handlerSource=formula 而走公式分支
        SysProcessTemplateNodeConfigDo config = ccOrgConfig(DEPT1);
        config.setHandlerSource(SysProcessConstants.HANDLER_SOURCE_FORMULA);
        addressBook.put(DEPT1, element(DEPT1, 2));
        when(processOrgUserDao.selectUserIdsByOrgHierarchy(List.of(DEPT1))).thenReturn(List.of(U1));

        assertEquals(List.of(U1), resolver.resolveCcIds(config, "审批节点A", null));
        verify(formulaEngine, never()).execute(anyString(), any(FormulaContext.class), any());
    }

    /* ==================== 工具方法 ==================== */

    /** 以 leader/member 解析方式解析单个地址本元素，标签沿用「审批人」 */
    private List<String> resolve(String resolveMode, String elementId) {
        return resolver.resolveElementIds(List.of(elementId), resolveMode, "审批节点A", "审批人");
    }

    /** 审批来源=地址本（org）的节点配置 */
    private static SysProcessTemplateNodeConfigDo handlerOrgConfig(String handlerIds) {
        SysProcessTemplateNodeConfigDo config = new SysProcessTemplateNodeConfigDo();
        config.setHandlerSource(SysProcessConstants.HANDLER_SOURCE_ORG);
        config.setHandlerIds(handlerIds);
        config.setHandlerResolveMode(SysProcessConstants.HANDLER_RESOLVE_MODE_LEADER);
        return config;
    }

    /** 审批来源=公式（formula）的节点配置 */
    private static SysProcessTemplateNodeConfigDo handlerFormulaConfig(String resolveMode) {
        SysProcessTemplateNodeConfigDo config = new SysProcessTemplateNodeConfigDo();
        config.setHandlerSource(SysProcessConstants.HANDLER_SOURCE_FORMULA);
        config.setHandlerFormula("handlerFormula");
        config.setHandlerResolveMode(resolveMode);
        return config;
    }

    /** 抄送来源=地址本（org）的节点配置 */
    private static SysProcessTemplateNodeConfigDo ccOrgConfig(String ccIds) {
        SysProcessTemplateNodeConfigDo config = new SysProcessTemplateNodeConfigDo();
        config.setCcSource(SysProcessConstants.CC_SOURCE_ORG);
        config.setCcIds(ccIds);
        config.setHandlerResolveMode(SysProcessConstants.HANDLER_RESOLVE_MODE_MEMBER);
        return config;
    }

    /** 抄送来源=公式（formula）的节点配置 */
    private static SysProcessTemplateNodeConfigDo ccFormulaConfig(String ccFormula) {
        SysProcessTemplateNodeConfigDo config = new SysProcessTemplateNodeConfigDo();
        config.setCcSource(SysProcessConstants.CC_SOURCE_FORMULA);
        config.setCcFormula(ccFormula);
        config.setHandlerResolveMode(SysProcessConstants.HANDLER_RESOLVE_MODE_LEADER);
        return config;
    }

    private static SysOrgElement element(String id, int orgType) {
        SysOrgElement element = new SysOrgElement();
        element.setFieldId(id);
        element.setFieldOrgType(orgType);
        return element;
    }
}
