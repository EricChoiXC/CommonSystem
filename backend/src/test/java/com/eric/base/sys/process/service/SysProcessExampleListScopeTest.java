package com.eric.base.sys.process.service;

import com.eric.base.common.base.model.Pages;
import com.eric.base.common.setting.service.CommonSettingService;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.mapper.SysOrgUserMapper;
import com.eric.base.sys.org.model.SysOrgUser;
import com.eric.base.sys.process.dao.SysProcessAuditNoteDao;
import com.eric.base.sys.process.dao.SysProcessExampleDao;
import com.eric.base.sys.process.dao.SysProcessExampleLineDao;
import com.eric.base.sys.process.dao.SysProcessExampleNodeDao;
import com.eric.base.sys.process.dao.SysProcessExampleWorkitemDao;
import com.eric.base.sys.process.dao.SysProcessTemplateDao;
import com.eric.base.sys.process.dao.SysProcessTemplateLineDao;
import com.eric.base.sys.process.dao.SysProcessTemplateNodeDao;
import com.eric.base.sys.process.properties.SysProcessConstants;
import com.eric.base.sys.process.request.SysProcessExampleQuery;
import com.eric.base.sys.process.resolver.ProcessResolver;
import com.eric.base.sys.process.response.SysProcessExampleVo;
import com.eric.base.sys.process.service.engine.ProcessFlowEngine;
import com.eric.base.sys.process.service.engine.ProcessFlowSupport;
import com.eric.base.sys.process.service.engine.ProcessNotifySender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程实例列表数据范围与监控列表测试（v1_7 P17-BE-015）
 *
 * @author Eric
 * @date 2026/11/21
 * @description 覆盖列表查询扩展的 Service 层逻辑（docs/设计文档/sys/process/流程v1_7接口清单.md §5.2/§5.6）：
 *              <ul>
 *              <li>scope 归一：缺省 / 空 / 非法值一律按 admin 处理（与既有列表行为完全一致）；</li>
 *              <li>my* 强制本人过滤：myCreated / myJoined 由 Service 从会话（UserUtil）注入
 *                  myUserId 传给 Mapper，list 与 count 口径一致（metadata.total 正确），
 *                  未登录注入 null（Mapper 本人条件比对 NULL 恒不成立，零泄露）；</li>
 *              <li>越权零泄露口径：请求体不承载用户标识——{@link SysProcessExampleQuery}
 *                  无任何用户身份字段（反射断言钉死），用户标识只能来自会话；</li>
 *              <li>monitorList / monitorCount：管理员全量视图忽略任何 scope（不做本人数据过滤），
 *                  inFlightOnly / exceptionOnly 原样透传由 Mapper 承担。</li>
 *              </ul>
 */
class SysProcessExampleListScopeTest {

    private static final String CREATOR_ID = "12345678901234567890123456789002";

    private SysProcessExampleDao exampleDao;
    private SysProcessExampleService service;
    private MockedStatic<UserUtil> userUtil;

    @BeforeEach
    void setUp() {
        exampleDao = mock(SysProcessExampleDao.class);
        SysProcessExampleNodeDao nodeDao = mock(SysProcessExampleNodeDao.class);
        SysProcessExampleLineDao exampleLineDao = mock(SysProcessExampleLineDao.class);
        SysProcessExampleWorkitemDao workitemDao = mock(SysProcessExampleWorkitemDao.class);
        SysProcessAuditNoteDao auditNoteDao = mock(SysProcessAuditNoteDao.class);
        SysProcessTemplateDao templateDao = mock(SysProcessTemplateDao.class);
        SysProcessTemplateNodeDao templateNodeDao = mock(SysProcessTemplateNodeDao.class);
        SysProcessTemplateLineDao templateLineDao = mock(SysProcessTemplateLineDao.class);
        CommonSettingService settingService = mock(CommonSettingService.class);
        SysOrgUserMapper userMapper = mock(SysOrgUserMapper.class);
        ProcessNotifySender notifySender = mock(ProcessNotifySender.class);
        ProcessResolver resolver = mock(ProcessResolver.class);
        SysProcessDocumentFieldService documentFieldService = mock(SysProcessDocumentFieldService.class);
        ProcessFlowSupport flowSupport = mock(ProcessFlowSupport.class);
        ProcessFlowEngine flowEngine = mock(ProcessFlowEngine.class);
        service = new SysProcessExampleService(exampleDao, nodeDao, exampleLineDao, workitemDao,
                auditNoteDao, templateDao, templateNodeDao, templateLineDao, settingService,
                userMapper, notifySender, resolver, documentFieldService,
                new ObjectMapper(), flowEngine, flowSupport);

        userUtil = mockStatic(UserUtil.class);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(CREATOR_ID, "起草人"));
    }

    @AfterEach
    void tearDown() {
        userUtil.close();
    }

    /* ==================== scope 归一（缺省 / 空 / 非法 → admin） ==================== */

    @Test
    void listNormalizesMissingOrInvalidScopeToAdmin() {
        for (String invalid : new String[]{null, "", "   ", "admin", "myCreatedX", "MYJOINED"}) {
            service.list(query(invalid), new Pages(), null, null);
        }

        verify(exampleDao, times(6)).selectListSorted(argThat(q ->
                        SysProcessConstants.EXAMPLE_SCOPE_ADMIN.equals(q.getScope())),
                any(Pages.class), isNull(), eq("desc"), isNull());
    }

    @Test
    void listWithNullQueryBecomesAdminScopeEmptyQuery() {
        service.list(null, null, null, null);

        verify(exampleDao).selectListSorted(argThat(q ->
                        null != q && SysProcessConstants.EXAMPLE_SCOPE_ADMIN.equals(q.getScope())
                                && q.getFieldName() == null && q.getFieldStatus() == null),
                argThat(p -> p != null && p.getPageNo() == 1 && p.getPageSize() == 10),
                isNull(), eq("desc"), isNull());
    }

    @Test
    void countNormalizesMissingOrInvalidScopeToAdmin() {
        for (String invalid : new String[]{null, "", "garbage"}) {
            service.count(query(invalid));
        }

        verify(exampleDao, times(3)).selectCount(argThat(q ->
                SysProcessConstants.EXAMPLE_SCOPE_ADMIN.equals(q.getScope())), isNull());
    }

    @Test
    void countWithNullQueryBecomesAdminScopeEmptyQuery() {
        service.count(null);

        verify(exampleDao).selectCount(argThat(q ->
                null != q && SysProcessConstants.EXAMPLE_SCOPE_ADMIN.equals(q.getScope())), isNull());
    }

    /* ==================== my* 强制本人过滤（用户标识只来自会话） ==================== */

    @Test
    void listMyCreatedInjectsSessionUserAsFilterIdentity() {
        service.list(query("myCreated"), new Pages(), "createTime", "asc");

        verify(exampleDao).selectListSorted(argThat(q ->
                        SysProcessConstants.EXAMPLE_SCOPE_MY_CREATED.equals(q.getScope())),
                any(Pages.class), eq("createTime"), eq("asc"), eq(CREATOR_ID));
    }

    @Test
    void listMyJoinedInjectsSessionUserAsFilterIdentity() {
        service.list(query("myJoined"), new Pages(), null, null);

        verify(exampleDao).selectListSorted(argThat(q ->
                        SysProcessConstants.EXAMPLE_SCOPE_MY_JOINED.equals(q.getScope())),
                any(Pages.class), isNull(), eq("desc"), eq(CREATOR_ID));
    }

    @Test
    void countMyCreatedInjectsSameIdentityAsListForMetadataTotal() {
        // list 与 count 归一口径一致：同一 query 两次调用（Controller 先 list 后 count）结果相同
        SysProcessExampleQuery query = query("myCreated");
        service.list(query, new Pages(), null, null);
        service.count(query);

        verify(exampleDao).selectListSorted(any(SysProcessExampleQuery.class), any(Pages.class),
                isNull(), eq("desc"), eq(CREATOR_ID));
        verify(exampleDao).selectCount(argThat(q ->
                SysProcessConstants.EXAMPLE_SCOPE_MY_CREATED.equals(q.getScope())), eq(CREATOR_ID));
    }

    @Test
    void myScopeWithoutLoginInjectsNullIdentityForZeroLeak() {
        // 未登录（会话无用户）：my* 注入 null，Mapper 本人过滤条件比对 NULL 恒不成立，结果为空（零泄露）
        userUtil.when(UserUtil::getCurrentUser).thenReturn(null);

        service.list(query("myJoined"), new Pages(), null, null);
        service.count(query("myCreated"));

        verify(exampleDao).selectListSorted(argThat(q ->
                        SysProcessConstants.EXAMPLE_SCOPE_MY_JOINED.equals(q.getScope())),
                any(Pages.class), isNull(), eq("desc"), isNull());
        verify(exampleDao).selectCount(argThat(q ->
                SysProcessConstants.EXAMPLE_SCOPE_MY_CREATED.equals(q.getScope())), isNull());
    }

    @Test
    void listPassesThroughDaoRows() {
        SysProcessExampleVo row = new SysProcessExampleVo();
        row.setFieldId("0".repeat(32));
        List<SysProcessExampleVo> rows = List.of(row);
        when(exampleDao.selectListSorted(any(SysProcessExampleQuery.class), any(Pages.class),
                any(), any(), any())).thenReturn(rows);

        List<SysProcessExampleVo> result = service.list(query("admin"), new Pages(), null, null);

        assertEquals(rows, result);
        assertNotNull(result.get(0));
    }

    /**
     * 越权零泄露口径的结构性钉死：查询条件对象不承载任何用户身份字段
     * （userId / creatorId / handlerId 等），my* 维度的过滤用户只能由 Service 从会话注入，
     * 请求体伪造任何用户字段在类型层面即不存在。
     */
    @Test
    void queryCarriesNoUserIdentityField() {
        Set<String> fields = Stream.of(SysProcessExampleQuery.class.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet());
        assertTrue(fields.containsAll(Set.of("scope", "inFlightOnly", "exceptionOnly")),
                "v1_7 扩展字段缺失: " + fields);
        for (String name : fields) {
            assertTrue(!name.toLowerCase().contains("userid") && !name.toLowerCase().contains("creatorid")
                            && !name.toLowerCase().contains("handlerid"),
                    "查询条件不得承载用户身份字段: " + name);
        }
    }

    /* ==================== monitorList（管理员全量视图） ==================== */

    @Test
    void monitorListIgnoresAnyScopeAndPassesMonitorFlags() {
        SysProcessExampleQuery query = query("myJoined");
        query.setInFlightOnly(true);
        query.setExceptionOnly(true);

        service.monitorList(query, new Pages(), "createTime", "asc");
        service.monitorCount(query);

        // 管理员全量视图：scope 被归一为 admin（Mapper 监控语句本身不含本人过滤分支，且无 myUserId 参数）
        verify(exampleDao).selectMonitorList(argThat(q ->
                        SysProcessConstants.EXAMPLE_SCOPE_ADMIN.equals(q.getScope())
                                && q.isInFlightOnly() && q.isExceptionOnly()),
                any(Pages.class), eq("createTime"), eq("asc"));
        verify(exampleDao).selectMonitorCount(argThat(q ->
                SysProcessConstants.EXAMPLE_SCOPE_ADMIN.equals(q.getScope())
                        && q.isInFlightOnly() && q.isExceptionOnly()));
    }

    @Test
    void monitorListDefaultsFlagsToFalseWhenBodyMissing() {
        service.monitorList(null, null, null, null);
        service.monitorCount(null);

        verify(exampleDao).selectMonitorList(argThat(q ->
                        null != q && SysProcessConstants.EXAMPLE_SCOPE_ADMIN.equals(q.getScope())
                                && !q.isInFlightOnly() && !q.isExceptionOnly()),
                argThat(p -> p != null && p.getPageNo() == 1 && p.getPageSize() == 10),
                isNull(), eq("desc"));
        verify(exampleDao).selectMonitorCount(argThat(q ->
                null != q && SysProcessConstants.EXAMPLE_SCOPE_ADMIN.equals(q.getScope())));
    }

    /* ==================== 辅助 ==================== */

    private static SysProcessExampleQuery query(String scope) {
        SysProcessExampleQuery query = new SysProcessExampleQuery();
        query.setFieldName("测试流程");
        query.setFieldStatus("PROCESSING");
        query.setScope(scope);
        return query;
    }

    private static SysOrgUser user(String id, String name) {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(id);
        user.setFieldName(name);
        user.setFieldEnable(true);
        return user;
    }
}
