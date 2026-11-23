package com.eric.base.sys.process.mapper;

import com.eric.base.sys.process.model.SysProcessStatisticHandlerWorkloadDo;
import com.eric.base.sys.process.model.SysProcessStatisticModelCountDo;
import com.eric.base.sys.process.model.SysProcessStatisticStatusCountDo;
import com.eric.base.sys.process.model.SysProcessStatisticTemplateCountDo;
import com.eric.base.sys.process.request.SysProcessStatisticAdminQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程统计 Mapper XML 解析与绑定测试（v1_7 P17-BE-016）
 *
 * @author Eric
 * @date 2026/11/23
 * @description 覆盖统计专用 Mapper 的 SQL 骨架验收（docs/设计文档/sys/process/流程v1_7接口清单.md §7）：
 *              Mapper XML 可被 MyBatis 正常解析；管理员统计五条语句共用同一筛选条件片段
 *              （概览与各分布口径一致）；平均办结耗时只计 FINISH 实例；人员工作量以
 *              HANDLER_PASS 留痕关联工作项实际处理人聚合并 LIMIT TopN；我的统计五项指标
 *              的本人条件只绑定会话注入的 myUserId。不做数据库连接，只校验 SQL 文本口径
 *              （先例 SysProcessExampleMapperXmlTest）。
 */
class SysProcessStatisticMapperXmlTest {

    private static final String STATISTIC_MAPPER = "mappers/sys/process/SysProcessStatisticMapper.xml";
    private static final String MAPPER_NS = SysProcessStatisticMapper.class.getName();

    /** 管理员统计共用筛选条件（全字段赋值时逐一渲染） */
    private static final String[] ADMIN_FILTERS = {
            "e.field_model_name = ?", "e.field_template_id = ?", "e.field_status = ?",
            "e.field_create_time >= ?", "e.field_create_time <= ?"};

    /** 平均办结耗时表达式（只计 FINISH 实例，自然时长毫秒） */
    private static final String FINISH_DURATION_EXPR =
            "CASE WHEN e.field_status = 'FINISH' AND e.field_finish_time IS NOT NULL"
                    + " THEN TIMESTAMPDIFF(MICROSECOND, e.field_create_time, e.field_finish_time) END";

    /** 人员工作量处理人取值（expect_handler_id 优先回退 handler_id） */
    private static final String HANDLER_ID_EXPR =
            "COALESCE(NULLIF(w.field_expect_handler_id, ''), w.field_handler_id) AS field_handler_id";

    /** 人员工作量的平均办结耗时表达式（派生表列别名 t） */
    private static final String WORKLOAD_DURATION_EXPR =
            "CASE WHEN t.field_status = 'FINISH' AND t.field_finish_time IS NOT NULL"
                    + " THEN TIMESTAMPDIFF(MICROSECOND, t.field_create_time, t.field_finish_time) END";

    private static Configuration configuration;

    @BeforeAll
    static void parseMappers() throws IOException {
        configuration = new Configuration();
        // 解析成功本身即校验 XML 结构合法、resultMap/type 引用有效
        parse(STATISTIC_MAPPER);
    }

    /* ==================== 管理员统计：概览 ==================== */

    @Test
    void selectAdminOverviewAppliesAllFiltersAndFinishOnlyDuration() {
        String sql = overviewSql(fullQuery());
        assertTrue(sql.contains("SELECT COUNT(*) AS total_count"), sql);
        assertTrue(sql.contains("IFNULL(ROUND(AVG(" + FINISH_DURATION_EXPR + ") / 1000), 0) AS avg_duration_ms"), sql);
        for (String filter : ADMIN_FILTERS) {
            assertTrue(sql.contains(filter), "概览缺少条件 " + filter + ": " + sql);
        }
    }

    @Test
    void selectAdminOverviewOmitsFiltersWhenConditionEmpty() {
        String sql = overviewSql(new SysProcessStatisticAdminQuery());
        assertFalse(sql.contains("WHERE"), sql);
        for (String filter : ADMIN_FILTERS) {
            assertFalse(sql.contains(filter), "空条件不应拼 " + filter + ": " + sql);
        }
    }

    /* ==================== 管理员统计：三组数量分布 ==================== */

    @Test
    void statusCountsGroupByStatusSharingAdminFilters() {
        String sql = aggregateSql("selectStatusCounts", fullQuery());
        assertTrue(sql.contains("e.field_status, COUNT(*) AS total_count"), sql);
        assertTrue(sql.contains("GROUP BY e.field_status"), sql);
        assertTrue(sql.contains("ORDER BY total_count DESC, e.field_status ASC"), sql);
        for (String filter : ADMIN_FILTERS) {
            assertTrue(sql.contains(filter), "状态分布缺少条件 " + filter + ": " + sql);
        }
    }

    @Test
    void modelCountsGroupByModelNameSharingAdminFilters() {
        String sql = aggregateSql("selectModelCounts", fullQuery());
        assertTrue(sql.contains("e.field_model_name, COUNT(*) AS total_count"), sql);
        assertTrue(sql.contains("GROUP BY e.field_model_name"), sql);
        assertTrue(sql.contains("ORDER BY total_count DESC, e.field_model_name ASC"), sql);
        for (String filter : ADMIN_FILTERS) {
            assertTrue(sql.contains(filter), "模块分布缺少条件 " + filter + ": " + sql);
        }
    }

    @Test
    void templateCountsJoinTemplateForNameSharingAdminFilters() {
        String sql = aggregateSql("selectTemplateCounts", fullQuery());
        assertTrue(sql.contains("e.field_template_id, t.field_name AS field_template_name,"
                + " COUNT(*) AS total_count"), sql);
        assertTrue(sql.contains("LEFT JOIN sys_process_template t ON t.field_id = e.field_template_id"), sql);
        assertTrue(sql.contains("GROUP BY e.field_template_id, t.field_name"), sql);
        assertTrue(sql.contains("ORDER BY total_count DESC, e.field_template_id ASC"), sql);
        for (String filter : ADMIN_FILTERS) {
            assertTrue(sql.contains(filter), "模板分布缺少条件 " + filter + ": " + sql);
        }
    }

    /* ==================== 管理员统计：人员工作量 TopN ==================== */

    @Test
    void handlerWorkloadsAggregatePassNotesByActualHandler() {
        String sql = handlerWorkloadSql(fullQuery(), 10);
        // 事实来源：HANDLER_PASS 留痕关联工作项与实例
        assertTrue(sql.contains("FROM sys_process_audit_note an"), sql);
        assertTrue(sql.contains("INNER JOIN sys_process_example_workitem w ON w.field_id = an.field_workitem_id"), sql);
        assertTrue(sql.contains("INNER JOIN sys_process_example e ON e.field_id = an.field_example_id"), sql);
        assertTrue(sql.contains("an.field_handle_type = 'HANDLER_PASS'"), sql);
        // 实际处理人取值（expect_handler_id 优先回退 handler_id），皆空行不计
        assertTrue(sql.contains("SELECT " + HANDLER_ID_EXPR), sql);
        assertTrue(sql.contains("AND " + HANDLER_ID_EXPR.replace(" AS field_handler_id", "")
                + " IS NOT NULL"), sql);
        // 工作量与效率列、处理人名称只读补全（不写 org 数据）
        assertTrue(sql.contains("COUNT(*) AS handled_count"), sql);
        assertTrue(sql.contains("IFNULL(ROUND(AVG(" + WORKLOAD_DURATION_EXPR + ") / 1000), 0) AS avg_duration_ms"), sql);
        assertTrue(sql.contains("LEFT JOIN sys_org_element u ON u.field_id = t.field_handler_id"
                + " AND u.field_org_type = 3"), sql);
        // TopN 截取与稳定排序
        assertTrue(sql.contains("GROUP BY t.field_handler_id"), sql);
        assertTrue(sql.contains("ORDER BY handled_count DESC, t.field_handler_id ASC"), sql);
        assertTrue(sql.contains("LIMIT ?"), sql);
        // 实例维度筛选条件同样作用于工作量聚合
        for (String filter : ADMIN_FILTERS) {
            assertTrue(sql.contains(filter), "工作量聚合缺少条件 " + filter + ": " + sql);
        }
    }

    @Test
    void handlerWorkloadsBindsNormalizedTopNOnly() {
        // topN 由 Service 归一后经独立参数绑定，请求体 topN 字段不直接进 SQL
        String sql = handlerWorkloadSql(fullQuery(), 25);
        assertTrue(sql.contains("LIMIT ?"), sql);
    }

    /* ==================== 我的统计 ==================== */

    @Test
    void selectMyStatisticAggregatesFiveMetricsBySessionUser() {
        String sql = myStatisticSql("U1");
        assertTrue(sql.contains("AS today_created_count"), sql);
        assertTrue(sql.contains("AS my_processing_count"), sql);
        assertTrue(sql.contains("AS pending_approve_count"), sql);
        assertTrue(sql.contains("AS today_approved_count"), sql);
        assertTrue(sql.contains("AS avg_duration_ms"), sql);
        // 本人条件只绑定会话注入的 myUserId（e.field_creator_id / w.field_handler_id = ?）
        assertEquals(2, countOccurrences(sql, "e.field_creator_id = ?"), sql);
        assertEquals(3, countOccurrences(sql, "w.field_handler_id = ?"), sql);
        // 我的未办结口径：DRAFT / PROCESSING / RETURN / PAUSE
        assertTrue(sql.contains("e.field_status IN ('DRAFT', 'PROCESSING', 'RETURN', 'PAUSE')"), sql);
        // 待审 / 今日已审：工作项 PROCESSING / FINISH + 今日窗口
        assertTrue(sql.contains("w.field_status = 'PROCESSING'"), sql);
        assertTrue(sql.contains("w.field_status = 'FINISH'"), sql);
        assertTrue(sql.contains("e.field_create_time >= ?"), sql);
        assertTrue(sql.contains("e.field_create_time < ?"), sql);
        assertTrue(sql.contains("w.field_finish_time >= ?"), sql);
        assertTrue(sql.contains("w.field_finish_time < ?"), sql);
        // 平均耗时：只计 FINISH 实例（本人参与，EXISTS 工作项条件）
        assertTrue(sql.contains("WHERE e.field_status = 'FINISH' AND e.field_finish_time IS NOT NULL"), sql);
        assertTrue(sql.contains("AND EXISTS (SELECT 1 FROM sys_process_example_workitem w"), sql);
        // 今日窗口由 Service 计算传入（不依赖数据库时区函数）
        assertFalse(sql.contains("CURDATE"), sql);
    }

    /* ==================== 只读与防越权结构性断言 ==================== */

    @Test
    void statisticMapperIsReadOnlyAggregate() throws IOException {
        String xml = resourceText(STATISTIC_MAPPER);
        assertFalse(xml.contains("<insert"), "统计 Mapper 不得包含写语句: " + xml);
        assertFalse(xml.contains("<update"), "统计 Mapper 不得包含写语句");
        assertFalse(xml.contains("<delete"), "统计 Mapper 不得包含写语句");
        // 统计专用隔离：不 include 流转 Mapper 的任何片段
        assertFalse(xml.contains("refid=\"com.eric.base"), "统计 Mapper 不得引用其他 Mapper 的片段");
    }

    @Test
    void adminStatementsNeverBindUserIdentity() {
        // 管理员统计为全量聚合：任何语句都不出现按人过滤的绑定点（防越权口径）
        String overview = overviewSql(fullQuery());
        assertFalse(overview.contains("field_creator_id = ?"), overview);
        assertFalse(overview.contains("field_handler_id = ?"), overview);
        String workload = handlerWorkloadSql(fullQuery(), 10);
        assertFalse(workload.contains("field_handler_id = ?"), workload);
        assertFalse(workload.contains("field_creator_id = ?"), workload);
    }

    @Test
    void resultMapsKeepContractProperties() {
        // 响应结构契约：聚合列与 VO/Do 属性一一映射
        var overviewMap = configuration.getResultMap(MAPPER_NS + ".SysProcessStatisticAdminOverviewMap");
        assertNotNull(overviewMap);
        assertTrue(hasProperty(overviewMap, "totalCount") && hasProperty(overviewMap, "avgDurationMs"),
                "概览映射缺少 totalCount / avgDurationMs");

        var statusMap = configuration.getResultMap(MAPPER_NS + ".SysProcessStatisticStatusCountMap");
        assertNotNull(statusMap);
        assertTrue(hasProperty(statusMap, "fieldStatus") && hasProperty(statusMap, "count"));

        var templateMap = configuration.getResultMap(MAPPER_NS + ".SysProcessStatisticTemplateCountMap");
        assertNotNull(templateMap);
        assertTrue(hasProperty(templateMap, "fieldTemplateId")
                && hasProperty(templateMap, "fieldTemplateName") && hasProperty(templateMap, "count"));

        var workloadMap = configuration.getResultMap(MAPPER_NS + ".SysProcessStatisticHandlerWorkloadMap");
        assertNotNull(workloadMap);
        assertTrue(hasProperty(workloadMap, "fieldHandlerId") && hasProperty(workloadMap, "fieldHandlerName")
                && hasProperty(workloadMap, "handledCount") && hasProperty(workloadMap, "avgDurationMs"));

        var myMap = configuration.getResultMap(MAPPER_NS + ".SysProcessStatisticMyMap");
        assertNotNull(myMap);
        assertTrue(hasProperty(myMap, "todayCreatedCount") && hasProperty(myMap, "myProcessingCount")
                && hasProperty(myMap, "pendingApproveCount") && hasProperty(myMap, "todayApprovedCount")
                && hasProperty(myMap, "avgDurationMs"));

        // 聚合行 Do 的字段契约（{业务}{语义}Do，Jackson 序列化固定结构）
        assertNotNull(configuration.getResultMap(MAPPER_NS + ".SysProcessStatisticModelCountMap"));
        assertEquals(String.class, fieldNameType(SysProcessStatisticStatusCountDo.class, "fieldStatus"));
        assertEquals(long.class, fieldType(SysProcessStatisticStatusCountDo.class, "count"));
        assertEquals(long.class, fieldType(SysProcessStatisticHandlerWorkloadDo.class, "handledCount"));
        assertEquals(String.class, fieldNameType(SysProcessStatisticModelCountDo.class, "fieldModelName"));
        assertEquals(String.class, fieldNameType(SysProcessStatisticTemplateCountDo.class, "fieldTemplateName"));
    }

    /* ==================== 辅助 ==================== */

    private static SysProcessStatisticAdminQuery fullQuery() {
        SysProcessStatisticAdminQuery query = new SysProcessStatisticAdminQuery();
        // 全部筛选字段赋值：保证共用片段的每个条件分支都渲染，便于各聚合口径一致性断言
        query.setFieldModelName("sys_org");
        query.setFieldTemplateId("T12345678901234567890123456789012");
        query.setFieldStatus("PROCESSING");
        query.setFieldCreateTimeBegin(new Date(0));
        query.setFieldCreateTimeEnd(new Date());
        return query;
    }

    private static String overviewSql(SysProcessStatisticAdminQuery query) {
        return boundSql(MAPPER_NS + ".selectAdminOverview", Map.of("query", query));
    }

    private static String aggregateSql(String statement, SysProcessStatisticAdminQuery query) {
        return boundSql(MAPPER_NS + "." + statement, Map.of("query", query));
    }

    private static String handlerWorkloadSql(SysProcessStatisticAdminQuery query, int topN) {
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("topN", topN);
        return boundSql(MAPPER_NS + ".selectHandlerWorkloads", params);
    }

    private static String myStatisticSql(String myUserId) {
        Map<String, Object> params = new HashMap<>();
        params.put("myUserId", myUserId);
        params.put("dayBegin", new Date(0));
        params.put("dayEnd", new Date(86400000L));
        return boundSql(MAPPER_NS + ".selectMyStatistic", params);
    }

    private static String boundSql(String statementId, Map<String, Object> params) {
        BoundSql boundSql = configuration.getMappedStatement(statementId).getBoundSql(params);
        assertNotNull(boundSql.getSql());
        return boundSql.getSql().replaceAll("\\s+", " ");
    }

    private static boolean hasProperty(org.apache.ibatis.mapping.ResultMap resultMap, String property) {
        return resultMap.getResultMappings().stream().anyMatch(mapping -> property.equals(mapping.getProperty()));
    }

    private static Class<?> fieldNameType(Class<?> type, String field) {
        try {
            return type.getDeclaredField(field).getType();
        } catch (NoSuchFieldException e) {
            throw new AssertionError("缺少字段 " + field + ": " + type.getName(), e);
        }
    }

    private static Class<?> fieldType(Class<?> type, String field) {
        return fieldNameType(type, field);
    }

    private static long countOccurrences(String text, String token) {
        long count = 0;
        int index = 0;
        while (-1 != (index = text.indexOf(token, index))) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static String resourceText(String resource) throws IOException {
        try (Scanner scanner = new Scanner(new ClassPathResource(resource).getInputStream(),
                StandardCharsets.UTF_8)) {
            return scanner.useDelimiter("\\A").next();
        }
    }

    private static void parse(String resource) throws IOException {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }
}
