package com.eric.base.sys.process.mapper;

import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.process.request.SysProcessExampleQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程实例 Mapper XML 解析与绑定测试（P17-BE-015 列表查询扩展）
 *
 * @author Eric
 * @date 2026/11/21
 * @description 覆盖 v1_7 列表查询扩展（docs/设计文档/sys/process/流程v1_7接口清单.md §5.2/§5.6）的 SQL 骨架验收：
 *              Mapper XML 可被 MyBatis 正常解析、selectCount 与 selectListSorted 过滤条件一致
 *              （供 metadata.total）、数据范围 scope（myCreated/myJoined）按 myUserId 独立参数
 *              强制本人过滤、监控语句（selectMonitorList/selectMonitorCount）在途/异常筛选与
 *              exceptionFlag 列齐备且不做本人数据过滤。
 *              不做数据库连接，只校验 SQL 文本口径（先例 SysDatasourceMapperXmlTest）。
 */
class SysProcessExampleMapperXmlTest {

    private static final String EXAMPLE_MAPPER = "mappers/sys/process/SysProcessExampleMapper.xml";
    private static final String MAPPER_NS = SysProcessExampleMapper.class.getName();

    /** 异常实例判定（Q-03 定稿口径：存在 SYSTEM_ERROR 审批留痕 或 状态为 PAUSE，二者取或） */
    private static final String EXCEPTION_CONDITION =
            "(e.field_status = 'PAUSE' OR EXISTS (SELECT 1 FROM sys_process_audit_note an"
                    + " WHERE an.field_example_id = e.field_id AND an.field_handle_type = 'SYSTEM_ERROR'))";
    /** myJoined 本人过滤（当前用户作为审批人或已审批人，工作项含历史全部状态） */
    private static final String MY_JOINED_CONDITION =
            "AND EXISTS (SELECT 1 FROM sys_process_example_workitem w"
                    + " WHERE w.field_example_id = e.field_id AND w.field_handler_id = ?)";
    /** 与列表同构的基础筛选条件（计数与列表共用，保证 metadata.total 一致） */
    private static final String[] BASE_FILTERS = {
            "e.field_model_name = ?", "e.field_code LIKE", "t.field_name LIKE", "u.field_name LIKE",
            "e.field_create_time >=", "e.field_create_time <=", "e.field_status = ?"};

    private static Configuration configuration;

    @BeforeAll
    static void parseMappers() throws IOException {
        configuration = new Configuration();
        // 解析成功本身即校验 XML 结构合法、resultMap/type 引用有效
        parse(EXAMPLE_MAPPER);
    }

    @Test
    void selectListSortedAppliesMyCreatedFilterBySessionUserId() {
        SysProcessExampleQuery query = queryWithScope("myCreated");
        String sql = listSql(query, "U1");
        assertTrue(sql.contains("AND e.field_creator_id = ?"), sql);
        // myCreated 不拼 myJoined 的工作项 EXISTS 条件
        assertFalse(sql.contains("sys_process_example_workitem"), sql);
    }

    @Test
    void selectListSortedAppliesMyJoinedFilterOverAllWorkitemHistory() {
        SysProcessExampleQuery query = queryWithScope("myJoined");
        String sql = listSql(query, "U1");
        assertTrue(sql.contains(MY_JOINED_CONDITION), sql);
        // myJoined 不拼 myCreated 的创建人条件
        assertFalse(sql.contains("AND e.field_creator_id = ?"), sql);
    }

    @Test
    void selectListSortedKeepsAdminBehaviorWhenScopeMissingOrInvalid() {
        // 缺省（null）/ 空串 / 非法值一律按 admin 全量：不拼任何本人过滤条件（与既有列表行为一致）
        for (String scope : new String[]{null, "", "  ", "admin", "my", "MYCREATED", "myCreated "}) {
            SysProcessExampleQuery query = queryWithScope(scope);
            String sql = listSql(query, "U1");
            assertFalse(sql.contains("field_creator_id = ?"), "scope=" + scope + " 不应拼本人过滤: " + sql);
            assertFalse(sql.contains("sys_process_example_workitem"), "scope=" + scope + " 不应拼工作项条件: " + sql);
        }
    }

    @Test
    void selectCountSharesListConditionsIncludingScope() {
        // 计数与列表过滤条件必须一致（供分页 metadata.total）：基础筛选 + scope 本人过滤同构
        SysProcessExampleQuery myCreated = queryWithScope("myCreated");
        String countSql = countSql("selectCount", myCreated, "U1");
        String listSql = listSql(myCreated, "U1");
        assertTrue(countSql.contains("COUNT(*)"), countSql);
        for (String filter : BASE_FILTERS) {
            assertTrue(countSql.contains(filter), "selectCount 缺少条件 " + filter + ": " + countSql);
            assertTrue(listSql.contains(filter), "selectListSorted 缺少条件 " + filter + ": " + listSql);
        }
        assertTrue(countSql.contains("AND e.field_creator_id = ?"), countSql);
        assertTrue(listSql.contains("AND e.field_creator_id = ?"), listSql);

        SysProcessExampleQuery myJoined = queryWithScope("myJoined");
        assertTrue(countSql("selectCount", myJoined, "U1").contains(MY_JOINED_CONDITION),
                "selectCount 缺少 myJoined 工作项条件");
        assertTrue(listSql(myJoined, "U1").contains(MY_JOINED_CONDITION),
                "selectListSorted 缺少 myJoined 工作项条件");

        // 计数不排序不分页
        assertFalse(countSql.contains("ORDER BY"), countSql);
        assertFalse(countSql.contains("LIMIT"), countSql);
    }

    @Test
    void selectListSortedBindsPagingAndSortWhitelist() {
        Pages pages = new Pages();
        pages.setPageNo(2);
        pages.setPageSize(10);
        pages.setNeedPage(true);
        Map<String, Object> params = new HashMap<>();
        params.put("query", queryWithScope("myCreated"));
        params.put("pages", pages);
        params.put("sortField", "createTime");
        params.put("sortOrder", "asc");
        params.put("myUserId", "U1");
        String sql = boundSql(MAPPER_NS + ".selectListSorted", params);
        assertTrue(sql.contains("ORDER BY e.field_create_time ASC"), sql);
        assertTrue(sql.contains("LIMIT ? OFFSET ?"), sql);
        // 列表行透出当前节点名称（关联子查询），不透出异常标记
        assertTrue(sql.contains("AS current_node_name"), sql);
        assertFalse(sql.contains("exception_flag"), sql);
    }

    @Test
    void selectMonitorListAppliesInFlightAndExceptionFilters() {
        // 在途筛选：PROCESSING / RETURN / PAUSE（异常筛选未启用，WHERE 不拼异常条件；
        // 注意 exception_flag 输出列恒引用审批留痕表，不能用表名断言筛选分支缺席）
        SysProcessExampleQuery inFlight = monitorQuery(true, false);
        String inFlightSql = monitorListSql(inFlight);
        assertTrue(inFlightSql.contains("AND e.field_status IN ('PROCESSING', 'RETURN', 'PAUSE')"), inFlightSql);
        assertFalse(inFlightSql.contains("AND " + EXCEPTION_CONDITION), inFlightSql);

        // 异常筛选：存在 SYSTEM_ERROR 留痕 或 状态 PAUSE（二者取或；在途筛选未启用，不拼 IN 条件）
        SysProcessExampleQuery exceptionOnly = monitorQuery(false, true);
        String exceptionSql = monitorListSql(exceptionOnly);
        assertTrue(exceptionSql.contains("AND " + EXCEPTION_CONDITION), exceptionSql);
        assertFalse(exceptionSql.contains("IN ('PROCESSING', 'RETURN', 'PAUSE')"), exceptionSql);

        // 组合：两个筛选同时生效
        String bothSql = monitorListSql(monitorQuery(true, true));
        assertTrue(bothSql.contains("IN ('PROCESSING', 'RETURN', 'PAUSE')"), bothSql);
        assertTrue(bothSql.contains("AND " + EXCEPTION_CONDITION), bothSql);

        // 缺省 false：不拼监控筛选
        String plainSql = monitorListSql(monitorQuery(false, false));
        assertFalse(plainSql.contains("IN ('PROCESSING', 'RETURN', 'PAUSE')"), plainSql);
        assertFalse(plainSql.contains("AND " + EXCEPTION_CONDITION), plainSql);
    }

    @Test
    void selectMonitorListExposesExceptionFlagAndCurrentNode() {
        String sql = monitorListSql(monitorQuery(false, false));
        assertTrue(sql.contains("CASE WHEN " + EXCEPTION_CONDITION
                + " THEN 1 ELSE 0 END AS exception_flag"), sql);
        assertTrue(sql.contains("AS current_node_name"), sql);
        assertTrue(sql.contains("ORDER BY e.field_create_time DESC"), sql);
        assertTrue(sql.contains("LIMIT ? OFFSET ?"), sql);
    }

    @Test
    void selectMonitorCountSharesMonitorConditions() {
        // 监控计数与监控列表过滤条件一致（供 metadata.total）
        SysProcessExampleQuery query = monitorQuery(true, true);
        String countSql = boundSql(MAPPER_NS + ".selectMonitorCount", Map.of("query", query));
        String listSql = monitorListSql(query);
        assertTrue(countSql.contains("COUNT(*)"), countSql);
        for (String filter : BASE_FILTERS) {
            assertTrue(countSql.contains(filter), "selectMonitorCount 缺少条件 " + filter + ": " + countSql);
            assertTrue(listSql.contains(filter), "selectMonitorList 缺少条件 " + filter + ": " + listSql);
        }
        assertTrue(countSql.contains("IN ('PROCESSING', 'RETURN', 'PAUSE')"), countSql);
        assertTrue(countSql.contains("AND " + EXCEPTION_CONDITION), countSql);
        assertFalse(countSql.contains("exception_flag"), "计数语句不取异常标记列: " + countSql);
        assertFalse(countSql.contains("ORDER BY"), countSql);
        assertFalse(countSql.contains("LIMIT"), countSql);
    }

    @Test
    void selectMonitorNeverAppliesPersonalScopeFilter() {
        // 监控为管理员全量视图：即使请求体伪造 scope=myCreated/myJoined 也不拼本人过滤条件
        SysProcessExampleQuery forged = monitorQuery(false, false);
        forged.setScope("myCreated");
        String listSql = monitorListSql(forged);
        assertFalse(listSql.contains("field_creator_id = ?"), listSql);
        assertFalse(listSql.contains("sys_process_example_workitem"), listSql);

        SysProcessExampleQuery forgedJoined = monitorQuery(true, true);
        forgedJoined.setScope("myJoined");
        String countSql = boundSql(MAPPER_NS + ".selectMonitorCount", Map.of("query", forgedJoined));
        assertFalse(countSql.contains("field_handler_id = ?"), countSql);
    }

    @Test
    void monitorResultMapMapsExceptionFlag() {
        var resultMap = configuration.getResultMap(MAPPER_NS + ".SysProcessExampleMonitorVoMap");
        assertNotNull(resultMap, "监控 resultMap 未注册");
        assertTrue(resultMap.getResultMappings().stream()
                        .anyMatch(mapping -> "exceptionFlag".equals(mapping.getProperty())),
                "监控 resultMap 缺少 exceptionFlag 映射");
        // 基础列表 resultMap 不含异常标记映射（列表行为 null，不透出）
        var listMap = configuration.getResultMap(MAPPER_NS + ".SysProcessExampleVoMap");
        assertNotNull(listMap);
        for (ResultMapping mapping : listMap.getResultMappings()) {
            assertFalse("exceptionFlag".equals(mapping.getProperty()), "基础列表 resultMap 不应映射 exceptionFlag");
        }
    }

    @Test
    void voRowColumnsKeepExistingContract() {
        // 列表/监控行字段契约：既有 Vo 列 + 当前节点名称齐备（v1_7 不改既有字段）
        String sql = monitorListSql(monitorQuery(false, false));
        for (String column : new String[]{"e.field_id", "e.field_template_id", "e.field_template_version",
                "e.field_model_id", "e.field_model_name", "e.field_code", "e.field_creator_id",
                "field_creator_name", "field_name", "e.field_create_time", "e.field_finish_time",
                "e.field_status"}) {
            assertTrue(sql.contains(column), "缺少列 " + column + ": " + sql);
        }
    }

    /* ==================== 辅助 ==================== */

    private static SysProcessExampleQuery queryWithScope(String scope) {
        SysProcessExampleQuery query = new SysProcessExampleQuery();
        // 全部基础筛选字段赋值：保证共用片段的每个条件分支都渲染，便于计数/列表一致性断言
        query.setFieldName("测试流程");
        query.setFieldCode("CS2026");
        query.setFieldModelName("sys_org");
        query.setFieldCreatorName("张三");
        query.setFieldCreateTimeBegin(new Date(0));
        query.setFieldCreateTimeEnd(new Date());
        query.setFieldStatus("PROCESSING");
        query.setScope(scope);
        return query;
    }

    private static SysProcessExampleQuery monitorQuery(boolean inFlightOnly, boolean exceptionOnly) {
        SysProcessExampleQuery query = queryWithScope(null);
        query.setInFlightOnly(inFlightOnly);
        query.setExceptionOnly(exceptionOnly);
        return query;
    }

    private static String listSql(SysProcessExampleQuery query, String myUserId) {
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("pages", new Pages());
        params.put("sortField", null);
        params.put("sortOrder", null);
        params.put("myUserId", myUserId);
        return boundSql(MAPPER_NS + ".selectListSorted", params);
    }

    private static String countSql(String statement, SysProcessExampleQuery query, String myUserId) {
        return boundSql(MAPPER_NS + "." + statement,
                Map.of("query", query, "myUserId", myUserId));
    }

    private static String monitorListSql(SysProcessExampleQuery query) {
        Pages pages = new Pages();
        pages.setPageNo(1);
        pages.setPageSize(20);
        pages.setNeedPage(true);
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("pages", pages);
        params.put("sortField", null);
        params.put("sortOrder", null);
        return boundSql(MAPPER_NS + ".selectMonitorList", params);
    }

    private static String boundSql(String statementId, Map<String, Object> params) {
        BoundSql boundSql = configuration.getMappedStatement(statementId).getBoundSql(params);
        assertNotNull(boundSql.getSql());
        return boundSql.getSql().replaceAll("\\s+", " ");
    }

    private static void parse(String resource) throws IOException {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }
}
