package com.eric.base.sys.calendar.datasource;

import com.eric.base.common.base.model.Pages;
import com.eric.base.common.exception.AuthorizationException;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.calendar.request.SysCalendarQuery;
import com.eric.base.sys.calendar.resolver.CalendarResolver;
import com.eric.base.sys.calendar.response.SysCalendarVo;
import com.eric.base.sys.calendar.service.SysCalendarMainService;
import com.eric.base.sys.datasource.service.SysDatasourceRegistry;
import com.eric.base.sys.datasource.spi.SysDatasourceDefaultDo;
import com.eric.base.sys.datasource.spi.SysDatasourceFieldDo;
import com.eric.base.sys.datasource.spi.SysDatasourceFieldScanner;
import com.eric.base.sys.datasource.spi.SysDatasourceQueryContext;
import com.eric.base.sys.datasource.spi.SysDatasourceQueryResult;
import com.eric.base.sys.org.model.SysOrgUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日程数据源 Provider 测试
 *
 * @author Eric
 * @date 2026/11/16
 * @description 覆盖任务 F18-BE-007 验收标准（Mockito 纯单测，SysCalendarMainService /
 *              CalendarResolver 用 mock 桩，MockedStatic&lt;UserUtil&gt; 构造当前登录人——
 *              先例 SysDatasourceServiceTest）：
 *              <ul>
 *              <li>目标能力元数据与规格一致（编码/名称/ACTION/路径/resolver）；</li>
 *              <li>条件字段/返回字段等价性基线（A8）：扫描归一结果逐项断言
 *                  key 顺序、ops、type 推导、literalAllowed、sortable 白名单、
 *                  公共字段 fieldId 内建补齐固定最前、persons 不进母集；</li>
 *              <li>条件映射、当前登录人固定、排序白名单 key 翻译（fieldStartTime →
 *                  startTime 等）、needTotal 语义（分页才 count）；</li>
 *              <li>默认数据源【日程列表】定义与 Registry 注册可见；</li>
 *              <li>未登录执行拒绝（UNAUTHORIZED）。</li>
 *              </ul>
 */
class SysCalendarDatasourceProviderTest {

    /** 32 位测试 ID（避免 BaseModel.setFieldId 触发重新生成） */
    private static final String USER_ID = "1".repeat(32);

    private SysCalendarMainService calendarMainService;
    private CalendarResolver calendarResolver;
    private SysCalendarDatasourceProvider provider;
    private MockedStatic<UserUtil> userUtil;

    @BeforeEach
    void setUp() {
        calendarMainService = mock(SysCalendarMainService.class);
        calendarResolver = mock(CalendarResolver.class);
        provider = new SysCalendarDatasourceProvider(calendarMainService, calendarResolver);
        // 默认当前登录人 USER_ID（query 固定数据边界依赖，R02）
        userUtil = mockStatic(UserUtil.class);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(USER_ID));
    }

    @AfterEach
    void tearDown() {
        userUtil.close();
    }

    // ---------------------------------------------------------------- 桩构造

    private SysOrgUser user(String userId) {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(userId);
        user.setFieldEnable(true);
        return user;
    }

    private Pages pages(boolean needPage) {
        Pages pages = new Pages();
        pages.setPageNo(1);
        pages.setPageSize(20);
        pages.setNeedPage(needPage);
        return pages;
    }

    private SysDatasourceQueryContext context(Map<String, Object> conditions, Pages pages,
                                              String sortField, String sortOrder) {
        SysDatasourceQueryContext context = new SysDatasourceQueryContext();
        context.setConditions(conditions);
        context.setPages(pages);
        context.setSortField(sortField);
        context.setSortOrder(sortOrder);
        return context;
    }

    /** 6 个条件 key 的全量条件集（日期经 SysDatasourceFieldDo#convert 产物为 Timestamp） */
    private Map<String, Object> fullConditions() {
        Date start = Timestamp.valueOf("2026-11-01 00:00:00");
        Date end = Timestamp.valueOf("2026-11-30 23:59:59");
        return Map.of(
                "fieldName", "周报",
                "fieldType", "PERSONAL",
                "fieldAccept", "ACCEPTED",
                "fieldStartTimeStart", start,
                "fieldStartTimeEnd", end,
                "fieldCreatorId", USER_ID);
    }

    // ---------------------------------------------------------------- 元数据

    @Test
    void metadataMatchesSpecification() {
        assertEquals("calendar.list", provider.getTargetCode());
        assertEquals("日程查询", provider.getTargetName());
        assertEquals("sys.calendar.list", provider.getTargetAction(), "ACTION 委托日程模块常量");
        assertEquals("/backend/sys/calendar/list", provider.getTargetPath());
        assertSame(calendarResolver, provider.getTargetResolver());
    }

    // ---------------------------------------------------------------- 条件字段等价性基线（A8）

    @Test
    void conditionFieldsMatchEquivalenceBaseline() {
        List<SysDatasourceFieldDo> fields = SysDatasourceFieldScanner.conditionFields(SysCalendarQuery.class);

        assertEquals(6, fields.size(), "条件字段 6 项：year/month 不标注不进白名单");
        assertEquals(List.of("fieldName", "fieldType", "fieldAccept",
                        "fieldStartTimeStart", "fieldStartTimeEnd", "fieldCreatorId"),
                fields.stream().map(SysDatasourceFieldDo::getKey).toList(), "key 顺序按注解 order");
        assertEquals(List.of("LIKE"), fields.get(0).getOps());
        assertEquals(List.of("EQ"), fields.get(1).getOps());
        assertEquals(List.of("EQ"), fields.get(2).getOps());
        assertEquals(List.of("GE"), fields.get(3).getOps());
        assertEquals(List.of("LE"), fields.get(4).getOps());
        assertEquals(List.of("EQ"), fields.get(5).getOps());
        assertEquals(List.of("STRING", "STRING", "STRING", "DATETIME", "DATETIME", "STRING"),
                fields.stream().map(SysDatasourceFieldDo::getType).toList(), "type 按 Java 字段类型推导");
        assertEquals(List.of("标题", "类型", "接收状态", "开始时间-起", "开始时间-止", "创建人"),
                fields.stream().map(SysDatasourceFieldDo::getLabel).toList(), "label 与注解一致");
        for (SysDatasourceFieldDo field : fields) {
            assertEquals("fieldCreatorId".equals(field.getKey()), !field.isLiteralAllowed(),
                    "仅 fieldCreatorId literalAllowed=false（A7 防越权）");
            assertFalse(field.isSortable(), "条件字段一律不参与排序白名单");
        }
        // Provider 一行委托扫描器（进程内缓存，同一归一结果）
        assertSame(fields, provider.getConditionFields());
    }

    // ---------------------------------------------------------------- 返回字段等价性基线（A8）

    @Test
    void resultFieldsMatchEquivalenceBaseline() {
        List<SysDatasourceFieldDo> fields = SysDatasourceFieldScanner.resultFields(SysCalendarVo.class);

        assertEquals(9, fields.size(), "返回字段母集 9 项：fieldId 内建补齐 + Vo 声明序 8 项，persons 不进母集");
        assertEquals(List.of("fieldId", "fieldCreatorId", "fieldCreateTime", "fieldUpdateTime",
                        "fieldStartTime", "fieldEndTime", "fieldName", "fieldContent", "fieldType"),
                fields.stream().map(SysDatasourceFieldDo::getKey).toList(), "fieldId 固定最前，其余按注解 order");
        assertEquals("ID", fields.get(0).getLabel(), "公共字段 fieldId 标准定义");
        assertEquals("STRING", fields.get(0).getType());
        assertEquals(List.of("STRING", "DATETIME", "DATETIME", "DATETIME", "DATETIME", "STRING", "STRING", "STRING"),
                fields.subList(1, 9).stream().map(SysDatasourceFieldDo::getType).toList(), "type 按 Java 字段类型推导");
        assertEquals(List.of("创建人id", "创建时间", "更新时间", "开始时间", "结束时间", "标题", "内容", "类型"),
                fields.subList(1, 9).stream().map(SysDatasourceFieldDo::getLabel).toList(), "label 与注解一致");
        for (SysDatasourceFieldDo field : fields) {
            assertNull(field.getOps(), "返回字段定义 ops 忽略");
            assertEquals(List.of("fieldCreateTime", "fieldStartTime").contains(field.getKey()),
                    field.isSortable(), "仅 fieldCreateTime/fieldStartTime 可排序");
            assertFalse(field.getKey().equals("persons"), "persons 不标注不进母集");
        }
        // Provider 一行委托扫描器（进程内缓存，同一归一结果）
        assertSame(fields, provider.getResultFields());
    }

    // ---------------------------------------------------------------- 条件映射与执行

    @Test
    void queryMapsAllConditionsAndFixesCurrentUserId() {
        // any() 口径（不带类型）：pages/sortField 等参数可为 null
        when(calendarMainService.list(any(), any(), any(), any(), any()))
                .thenReturn(List.of(new SysCalendarVo()));
        when(calendarMainService.count(any(), any())).thenReturn(7L);

        Date start = Timestamp.valueOf("2026-11-01 00:00:00");
        Date end = Timestamp.valueOf("2026-11-30 23:59:59");
        SysDatasourceQueryResult result = provider.query(context(
                fullConditions(), pages(true), "fieldCreateTime", "asc"));

        ArgumentCaptor<SysCalendarQuery> captor = ArgumentCaptor.forClass(SysCalendarQuery.class);
        verify(calendarMainService).list(captor.capture(), eq(USER_ID),
                eq(pages(true)), eq("createTime"), eq("asc"));
        SysCalendarQuery mapped = captor.getValue();
        assertEquals("周报", mapped.getFieldName());
        assertEquals("PERSONAL", mapped.getFieldType());
        assertEquals("ACCEPTED", mapped.getFieldAccept());
        assertEquals(start, mapped.getFieldStartTimeStart());
        assertEquals(end, mapped.getFieldStartTimeEnd());
        assertEquals(USER_ID, mapped.getFieldCreatorId(), "「我创建的」经固定条件解析传入");
        verify(calendarMainService).count(mapped, USER_ID);
        assertEquals(7L, result.getTotal(), "分页时 total 取 count 返回值");
        assertEquals(1, result.getRows().size());
    }

    @Test
    void queryTranslatesWhitelistSortFieldToMapperVocabulary() {
        when(calendarMainService.list(any(), any(), any(), any(), any())).thenReturn(List.of());

        // 白名单 key → Mapper 排序词汇
        provider.query(context(Map.of(), null, "fieldStartTime", "desc"));
        provider.query(context(Map.of(), null, "fieldCreateTime", "asc"));
        // null 或未知 key 回退 null（Mapper 按默认排序执行）
        provider.query(context(Map.of(), null, null, null));
        provider.query(context(Map.of(), null, "fieldContent", "desc"));

        ArgumentCaptor<String> sortCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> orderCaptor = ArgumentCaptor.forClass(String.class);
        verify(calendarMainService, times(4)).list(any(SysCalendarQuery.class), eq(USER_ID),
                isNull(), sortCaptor.capture(), orderCaptor.capture());
        // Arrays.asList 允许 null 元素（List.of 不允许）
        assertEquals(Arrays.asList("startTime", "createTime", null, null), sortCaptor.getAllValues());
        assertEquals(Arrays.asList("desc", "asc", null, "desc"), orderCaptor.getAllValues(),
                "sortOrder 原样透传");
    }

    @Test
    void queryWithoutPaginationSkipsCount() {
        when(calendarMainService.list(any(), any(), any(), any(), any()))
                .thenReturn(List.of(new SysCalendarVo(), new SysCalendarVo()));

        // pages=null：不分页
        SysDatasourceQueryResult withoutPages = provider.query(context(Map.of(), null, null, null));
        assertEquals(2, withoutPages.getTotal(), "不分页 total = rows.size()");
        // needPage=false：不分页
        SysDatasourceQueryResult noNeedPage = provider.query(context(Map.of(), pages(false), null, null));
        assertEquals(2, noNeedPage.getTotal());

        verify(calendarMainService, never()).count(any(SysCalendarQuery.class), any(String.class));
    }

    // ---------------------------------------------------------------- 默认数据源与 Registry

    @Test
    void defaultDatasourceMatchesDefinition() {
        List<SysDatasourceDefaultDo> defaults = provider.getDefaultDatasources();

        assertEquals(1, defaults.size(), "默认数据源【日程列表】");
        SysDatasourceDefaultDo def = defaults.get(0);
        assertEquals("calendar.list", def.getCode());
        assertEquals("日程列表", def.getName());
        assertTrue(def.getConditions().isEmpty(), "无条件");
        assertTrue(def.getFields().isEmpty(), "空字段白名单 = 目标能力全字段");
    }

    @Test
    void registryExposesTargetAndDefault() {
        SysDatasourceRegistry registry = new SysDatasourceRegistry(List.of(provider));

        SysDatasourceDefaultDo def = registry.getDefault("calendar.list");
        assertNotNull(def, "默认数据源经 Registry 可枚举");
        assertEquals("日程列表", def.getName());
        assertSame(provider, registry.getProviderByTarget("calendar.list"), "目标能力注册可见");
        assertSame(provider, registry.getProviderByDefaultCode("calendar.list"), "默认源归属 Provider 正确");
    }

    // ---------------------------------------------------------------- 未登录拒绝

    @Test
    void queryRejectsAnonymous() {
        userUtil.when(UserUtil::getCurrentUser).thenReturn(null);

        AuthorizationException ex = assertThrows(AuthorizationException.class,
                () -> provider.query(context(Map.of(), pages(true), null, null)));

        assertEquals("UNAUTHORIZED", ex.getCode());
        verify(calendarMainService, never()).list(any(), any(), any(), any(), any());
        verify(calendarMainService, never()).count(any(), any());
    }
}
