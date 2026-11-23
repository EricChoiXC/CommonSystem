package com.eric.base.sys.process.service;

import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import com.eric.base.sys.process.dao.SysProcessStatisticDao;
import com.eric.base.sys.process.model.SysProcessStatisticHandlerWorkloadDo;
import com.eric.base.sys.process.model.SysProcessStatisticModelCountDo;
import com.eric.base.sys.process.model.SysProcessStatisticStatusCountDo;
import com.eric.base.sys.process.model.SysProcessStatisticTemplateCountDo;
import com.eric.base.sys.process.request.SysProcessStatisticAdminQuery;
import com.eric.base.sys.process.response.SysProcessStatisticAdminVo;
import com.eric.base.sys.process.response.SysProcessStatisticMyVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程统计服务测试（v1_7 P17-BE-016）
 *
 * @author Eric
 * @date 2026/11/23
 * @description 覆盖统计编排层口径（docs/设计文档/sys/process/流程v1_7接口清单.md §7）：
 *              <ul>
 *              <li>管理员统计：body 缺省归一为空条件、topN 归一（缺省 / 非法 → 默认 10）、
 *                  五路聚合结果装配与空表兜底、筛选条件原样透传不被改动；</li>
 *              <li>我的统计：统计对象只取会话（UserUtil）注入 myUserId（方法无 userId 参数，
 *                  未登录注入 null 零泄露）、今日窗口为应用服务器时区的今日零点（含）至
 *                  明日零点（不含）；</li>
 *              <li>越权零泄露口径的结构性钉死：{@link SysProcessStatisticAdminQuery}
 *                  不承载任何用户身份字段。</li>
 *              </ul>
 *              不依赖真实 MySQL/Redis（Dao 全 mock，先例 SysProcessExampleListScopeTest）。
 */
class SysProcessStatisticServiceTest {

    private static final String MY_USER_ID = "12345678901234567890123456789001";

    private SysProcessStatisticDao statisticDao;
    private SysProcessStatisticService service;
    private MockedStatic<UserUtil> userUtil;

    @BeforeEach
    void setUp() {
        statisticDao = mock(SysProcessStatisticDao.class);
        service = new SysProcessStatisticService(statisticDao);

        userUtil = mockStatic(UserUtil.class);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(MY_USER_ID, "统计人"));
    }

    @AfterEach
    void tearDown() {
        userUtil.close();
    }

    /* ==================== 管理员统计：条件与 TopN 归一 ==================== */

    @Test
    void adminStatisticWithNullBodyBecomesEmptyConditionAndDefaultTopN() {
        service.adminStatistic(null);

        verify(statisticDao).selectAdminOverview(argThatQuery(q ->
                null != q && null == q.getFieldModelName() && null == q.getFieldTemplateId()
                        && null == q.getFieldStatus() && null == q.getFieldCreateTimeBegin()
                        && null == q.getFieldCreateTimeEnd() && null == q.getTopN()));
        verify(statisticDao).selectStatusCounts(argThatQuery(q -> null != q));
        verify(statisticDao).selectModelCounts(argThatQuery(q -> null != q));
        verify(statisticDao).selectTemplateCounts(argThatQuery(q -> null != q));
        verify(statisticDao).selectHandlerWorkloads(argThatQuery(q -> null != q),
                eq(SysProcessStatisticService.DEFAULT_TOP_N));
    }

    @Test
    void adminStatisticNormalizesTopNToDefaultWhenMissingOrInvalid() {
        for (Integer invalid : new Integer[]{null, 0, -1, -100}) {
            SysProcessStatisticAdminQuery query = new SysProcessStatisticAdminQuery();
            query.setTopN(invalid);
            service.adminStatistic(query);
        }

        verify(statisticDao, times(4)).selectHandlerWorkloads(any(SysProcessStatisticAdminQuery.class),
                eq(SysProcessStatisticService.DEFAULT_TOP_N));
    }

    @Test
    void adminStatisticKeepsPositiveTopNAsIs() {
        SysProcessStatisticAdminQuery query = new SysProcessStatisticAdminQuery();
        query.setTopN(25);
        service.adminStatistic(query);

        verify(statisticDao).selectHandlerWorkloads(any(SysProcessStatisticAdminQuery.class), eq(25));
    }

    @Test
    void adminStatisticPassesFiltersUntouchedToAllAggregates() {
        SysProcessStatisticAdminQuery query = new SysProcessStatisticAdminQuery();
        query.setFieldModelName("sys_org");
        query.setFieldTemplateId("T12345678901234567890123456789012");
        query.setFieldStatus("PROCESSING");
        query.setFieldCreateTimeBegin(new Date(0));
        query.setFieldCreateTimeEnd(new Date(86400000L));
        query.setTopN(5);

        service.adminStatistic(query);

        // 五路聚合使用同一筛选条件（同一实例透传，口径一致）
        verify(statisticDao).selectAdminOverview(sameQuery(query));
        verify(statisticDao).selectStatusCounts(sameQuery(query));
        verify(statisticDao).selectModelCounts(sameQuery(query));
        verify(statisticDao).selectTemplateCounts(sameQuery(query));
        verify(statisticDao).selectHandlerWorkloads(sameQuery(query), eq(5));
    }

    /* ==================== 管理员统计：结果装配与兜底 ==================== */

    @Test
    void adminStatisticAssemblesAllAggregatesIntoVo() {
        stubAggregates("sys_org");

        SysProcessStatisticAdminVo vo = service.adminStatistic(fullQuery());

        assertNotNull(vo);
        assertEquals(7L, vo.getTotalCount());
        assertEquals(123456L, vo.getAvgDurationMs());
        assertEquals(1, vo.getStatusCounts().size());
        assertEquals("PROCESSING", vo.getStatusCounts().get(0).getFieldStatus());
        assertEquals(4L, vo.getStatusCounts().get(0).getCount());
        assertEquals(1, vo.getModelCounts().size());
        assertEquals("sys_org", vo.getModelCounts().get(0).getFieldModelName());
        assertEquals(1, vo.getTemplateCounts().size());
        assertEquals("T12345678901234567890123456789012", vo.getTemplateCounts().get(0).getFieldTemplateId());
        assertEquals("组织架构模板", vo.getTemplateCounts().get(0).getFieldTemplateName());
        assertEquals(1, vo.getHandlerWorkloads().size());
        assertEquals(MY_USER_ID, vo.getHandlerWorkloads().get(0).getFieldHandlerId());
        assertEquals(3L, vo.getHandlerWorkloads().get(0).getHandledCount());
        assertEquals(999L, vo.getHandlerWorkloads().get(0).getAvgDurationMs());
    }

    @Test
    void adminStatisticFallsBackToZerosAndEmptyListsWhenDaoReturnsNothing() {
        // 概览 / 各分布Dao 返回 null（理论不发生，防御性兜底）：响应结构稳定不塌
        when(statisticDao.selectAdminOverview(any(SysProcessStatisticAdminQuery.class))).thenReturn(null);
        when(statisticDao.selectStatusCounts(any(SysProcessStatisticAdminQuery.class))).thenReturn(null);
        when(statisticDao.selectModelCounts(any(SysProcessStatisticAdminQuery.class))).thenReturn(null);
        when(statisticDao.selectTemplateCounts(any(SysProcessStatisticAdminQuery.class))).thenReturn(null);
        when(statisticDao.selectHandlerWorkloads(any(SysProcessStatisticAdminQuery.class), anyInt()))
                .thenReturn(null);

        SysProcessStatisticAdminVo vo = service.adminStatistic(fullQuery());

        assertNotNull(vo);
        assertEquals(0L, vo.getTotalCount());
        assertEquals(0L, vo.getAvgDurationMs());
        assertNotNull(vo.getStatusCounts());
        assertTrue(vo.getStatusCounts().isEmpty());
        assertNotNull(vo.getModelCounts());
        assertTrue(vo.getModelCounts().isEmpty());
        assertNotNull(vo.getTemplateCounts());
        assertTrue(vo.getTemplateCounts().isEmpty());
        assertNotNull(vo.getHandlerWorkloads());
        assertTrue(vo.getHandlerWorkloads().isEmpty());
    }

    /* ==================== 我的统计：会话注入与今日窗口 ==================== */

    @Test
    void myStatisticInjectsSessionUserAndTodayWindow() {
        service.myStatistic();

        ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Date> beginCaptor = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> endCaptor = ArgumentCaptor.forClass(Date.class);
        verify(statisticDao).selectMyStatistic(userIdCaptor.capture(), beginCaptor.capture(), endCaptor.capture());

        // 过滤身份只来自会话（方法无 userId 参数，请求体无用户字段可传）
        assertEquals(MY_USER_ID, userIdCaptor.getValue());

        // 今日窗口 = 应用服务器时区的今日零点（含）至明日零点（不含）
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        assertEquals(today, beginCaptor.getValue().toInstant().atZone(zone).toLocalDate());
        assertEquals(today.plusDays(1), endCaptor.getValue().toInstant().atZone(zone).toLocalDate());
        assertTrue(beginCaptor.getValue().toInstant().atZone(zone).toLocalDateTime().toLocalTime().toSecondOfDay() == 0,
                "窗口起点应为今日零点");
        assertTrue(endCaptor.getValue().toInstant().atZone(zone).toLocalDateTime().toLocalTime().toSecondOfDay() == 0,
                "窗口终点应为明日零点");
    }

    @Test
    void myStatisticWithoutLoginInjectsNullIdentityForZeroLeak() {
        // 未登录（会话无用户）：注入 null，本人条件比对 NULL 恒不成立，五项指标全 0（零泄露）
        userUtil.when(UserUtil::getCurrentUser).thenReturn(null);

        service.myStatistic();

        verify(statisticDao).selectMyStatistic(isNull(), any(Date.class), any(Date.class));
    }

    @Test
    void myStatisticFallsBackToZeroVoWhenDaoReturnsNothing() {
        when(statisticDao.selectMyStatistic(any(String.class), any(Date.class), any(Date.class)))
                .thenReturn(null);

        SysProcessStatisticMyVo vo = service.myStatistic();

        assertNotNull(vo);
        assertEquals(0L, vo.getTodayCreatedCount());
        assertEquals(0L, vo.getMyProcessingCount());
        assertEquals(0L, vo.getPendingApproveCount());
        assertEquals(0L, vo.getTodayApprovedCount());
        assertEquals(0L, vo.getAvgDurationMs());
    }

    /* ==================== 越权零泄露口径的结构性钉死 ==================== */

    @Test
    void adminQueryCarriesNoUserIdentityField() {
        // 请求体不承载用户标识：userId / creatorId / handlerId 等字段在类型层面即不存在，
        // 管理员统计全量聚合、我的统计统计对象只取会话（与 BE-015 查询条件口径一致）
        Set<String> fields = Stream.of(SysProcessStatisticAdminQuery.class.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet());
        assertEquals(Set.of("fieldModelName", "fieldTemplateId", "fieldStatus",
                        "fieldCreateTimeBegin", "fieldCreateTimeEnd", "topN"),
                fields, "统计查询条件字段集必须与接口清单 §7.1 逐项一致");
    }

    @Test
    void myVoCarriesNoUserIdentityField() {
        // 我的统计响应同样不回传用户标识（五项指标，接口清单 §7.2）
        Set<String> fields = Stream.of(SysProcessStatisticMyVo.class.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet());
        assertEquals(Set.of("todayCreatedCount", "myProcessingCount", "pendingApproveCount",
                        "todayApprovedCount", "avgDurationMs"),
                fields, "我的统计响应字段集必须与接口清单 §7.2 逐项一致");
    }

    /* ==================== 辅助 ==================== */

    private static SysProcessStatisticAdminQuery fullQuery() {
        SysProcessStatisticAdminQuery query = new SysProcessStatisticAdminQuery();
        query.setFieldModelName("sys_org");
        query.setTopN(10);
        return query;
    }

    private void stubAggregates(String modelName) {
        SysProcessStatisticAdminVo overview = new SysProcessStatisticAdminVo();
        overview.setTotalCount(7L);
        overview.setAvgDurationMs(123456L);
        when(statisticDao.selectAdminOverview(any(SysProcessStatisticAdminQuery.class))).thenReturn(overview);

        SysProcessStatisticStatusCountDo statusCount = new SysProcessStatisticStatusCountDo();
        statusCount.setFieldStatus("PROCESSING");
        statusCount.setCount(4L);
        when(statisticDao.selectStatusCounts(any(SysProcessStatisticAdminQuery.class)))
                .thenReturn(List.of(statusCount));

        SysProcessStatisticModelCountDo modelCount = new SysProcessStatisticModelCountDo();
        modelCount.setFieldModelName(modelName);
        modelCount.setCount(7L);
        when(statisticDao.selectModelCounts(any(SysProcessStatisticAdminQuery.class)))
                .thenReturn(List.of(modelCount));

        SysProcessStatisticTemplateCountDo templateCount = new SysProcessStatisticTemplateCountDo();
        templateCount.setFieldTemplateId("T12345678901234567890123456789012");
        templateCount.setFieldTemplateName("组织架构模板");
        templateCount.setCount(7L);
        when(statisticDao.selectTemplateCounts(any(SysProcessStatisticAdminQuery.class)))
                .thenReturn(List.of(templateCount));

        SysProcessStatisticHandlerWorkloadDo workload = new SysProcessStatisticHandlerWorkloadDo();
        workload.setFieldHandlerId(MY_USER_ID);
        workload.setFieldHandlerName("统计人");
        workload.setHandledCount(3L);
        workload.setAvgDurationMs(999L);
        when(statisticDao.selectHandlerWorkloads(any(SysProcessStatisticAdminQuery.class), anyInt()))
                .thenReturn(List.of(workload));
    }

    private static SysProcessStatisticAdminQuery argThatQuery(
            java.util.function.Predicate<SysProcessStatisticAdminQuery> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher::test);
    }

    private static SysProcessStatisticAdminQuery sameQuery(SysProcessStatisticAdminQuery expected) {
        return org.mockito.ArgumentMatchers.argThat(actual -> actual == expected);
    }

    private static SysOrgUser user(String id, String name) {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(id);
        user.setFieldName(name);
        user.setFieldEnable(true);
        return user;
    }
}
