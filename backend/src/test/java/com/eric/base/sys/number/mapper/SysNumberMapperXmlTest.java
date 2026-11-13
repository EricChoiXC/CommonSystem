package com.eric.base.sys.number.mapper;

import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.number.model.SysNumberRecord;
import com.eric.base.sys.number.model.SysNumberRecordDetail;
import com.eric.base.sys.number.model.SysNumberRule;
import com.eric.base.sys.number.request.SysNumberRuleQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
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
 * 编号机制三表 Mapper XML 解析与绑定测试
 *
 * @author Eric
 * @date 2026/11/13
 * @description 覆盖任务 NB1-DB-001 的 XML 骨架验收：三份 XML 可被 MyBatis 正常解析
 *              （结构与 DTD 合法、resultMap/type 引用有效）、基础语句（insert / update /
 *              deleteById / selectById / selectList / loadModel）可按参数取到最终 SQL。
 *              不做数据库连接，只校验 SQL 文本口径（先例 SysOrgElementExclusionSqlTest）；
 *              Flyway 建表落地验证随本地启动观察（V1_01_044）。
 */
class SysNumberMapperXmlTest {

    private static final String RULE_MAPPER = "mappers/sys/number/SysNumberRuleMapper.xml";
    private static final String RECORD_MAPPER = "mappers/sys/number/SysNumberRecordMapper.xml";
    private static final String DETAIL_MAPPER = "mappers/sys/number/SysNumberRecordDetailMapper.xml";

    private static Configuration configuration;

    @BeforeAll
    static void parseMappers() throws IOException {
        configuration = new Configuration();
        // 依次解析三份 Mapper：解析成功本身即校验 XML 结构合法、类型引用有效
        parse(RULE_MAPPER);
        parse(RECORD_MAPPER);
        parse(DETAIL_MAPPER);
    }

    @Test
    void ruleInsertContainsAllColumns() {
        String sql = boundSql(SysNumberRuleMapper.class.getName() + ".insert",
                Map.of("model", new SysNumberRule()));
        assertTrue(sql.contains("INSERT INTO sys_number_rule"), sql);
        for (String column : new String[]{"field_id", "field_name", "field_code", "field_creator_id",
                "field_create_time", "field_updater_id", "field_update_time", "field_enable",
                "field_rule", "field_model_id", "field_model_name", "field_model_field"}) {
            assertTrue(sql.contains(column), "insert 缺少列 " + column + ": " + sql);
        }
    }

    @Test
    void ruleUpdateNeverTouchesLockedModelId() {
        // D3：所属接入项 field_model_id 保存后锁定，update 不提供更新入口
        SysNumberRule model = new SysNumberRule();
        model.setFieldId("0".repeat(32));
        model.setFieldName("规则");
        model.setFieldModelId("process-example-code");
        String sql = boundSql(SysNumberRuleMapper.class.getName() + ".update", Map.of("model", model));
        assertTrue(sql.contains("UPDATE sys_number_rule"), sql);
        assertTrue(sql.contains("field_name ="), sql);
        assertFalse(sql.contains("field_model_id ="), "field_model_id 保存后锁定，禁止出现在 update: " + sql);
    }

    @Test
    void ruleSelectListBindsFiltersAndPaging() {
        SysNumberRule model = new SysNumberRule();
        model.setFieldCode("CG-RULE");
        model.setFieldName("订单");
        model.setFieldEnable(true);
        Pages pages = new Pages();
        pages.setPageNo(2);
        pages.setPageSize(10);
        pages.setNeedPage(true);
        Map<String, Object> params = new HashMap<>();
        params.put("model", model);
        params.put("pages", pages);

        String sql = boundSql(SysNumberRuleMapper.class.getName() + ".selectList", params);
        assertTrue(sql.contains("field_code ="), sql);
        assertTrue(sql.contains("field_name LIKE"), sql);
        assertTrue(sql.contains("field_enable ="), sql);
        assertTrue(sql.contains("LIMIT"), sql);
        assertTrue(sql.contains("ORDER BY field_create_time DESC"), sql);

        // 无条件 + 不分页：裸列表
        String plainSql = boundSql(SysNumberRuleMapper.class.getName() + ".selectList",
                Map.of("model", new SysNumberRule(), "pages", new Pages()));
        assertFalse(plainSql.contains("WHERE"), plainSql);
        assertFalse(plainSql.contains("LIMIT"), plainSql);
    }

    @Test
    void ruleLoadModelReturnsIdAndNameOnly() {
        String sql = boundSql(SysNumberRuleMapper.class.getName() + ".loadModel",
                Map.of("fieldId", "0".repeat(32)));
        assertTrue(sql.contains("SELECT field_id, field_name"), sql);
        assertFalse(sql.contains("field_rule"), "轻量加载不取规则 JSON: " + sql);
    }

    @Test
    void recordInsertContainsAllColumns() {
        String sql = boundSql(SysNumberRecordMapper.class.getName() + ".insert",
                Map.of("model", new SysNumberRecord()));
        assertTrue(sql.contains("INSERT INTO sys_number_record"), sql);
        for (String column : new String[]{"field_id", "field_rule_id", "field_scope_key", "field_serial_id",
                "field_period", "field_serial_no", "field_record_time", "field_record_finish_time"}) {
            assertTrue(sql.contains(column), "insert 缺少列 " + column + ": " + sql);
        }
    }

    @Test
    void recordSelectListBindsScopeFilters() {
        SysNumberRecord model = new SysNumberRecord();
        model.setFieldScopeKey("CG-RULE-ID");
        model.setFieldPeriod("20261113");
        Map<String, Object> params = new HashMap<>();
        params.put("model", model);
        params.put("pages", new Pages());
        String sql = boundSql(SysNumberRecordMapper.class.getName() + ".selectList", params);
        assertTrue(sql.contains("field_scope_key ="), sql);
        assertTrue(sql.contains("field_period ="), sql);
        assertTrue(sql.contains("ORDER BY field_record_time DESC"), sql);
    }

    @Test
    void detailInsertContainsAllColumns() {
        String sql = boundSql(SysNumberRecordDetailMapper.class.getName() + ".insert",
                Map.of("model", new SysNumberRecordDetail()));
        assertTrue(sql.contains("INSERT INTO sys_number_record_detail"), sql);
        for (String column : new String[]{"field_id", "field_scope_key", "field_period", "field_serial_id",
                "field_serial_no", "field_model_name", "field_model_id", "field_code", "field_record_time",
                "field_creator_id", "field_void_time", "field_void_reason"}) {
            assertTrue(sql.contains(column), "insert 缺少列 " + column + ": " + sql);
        }
    }

    @Test
    void detailSelectListBindsIdempotencyAnchorFilters() {
        SysNumberRecordDetail model = new SysNumberRecordDetail();
        model.setFieldScopeKey("ErpContract:CAT-1");
        model.setFieldModelName("ErpContract");
        model.setFieldModelId("0".repeat(32));
        Map<String, Object> params = new HashMap<>();
        params.put("model", model);
        params.put("pages", new Pages());
        String sql = boundSql(SysNumberRecordDetailMapper.class.getName() + ".selectList", params);
        assertTrue(sql.contains("field_scope_key ="), sql);
        assertTrue(sql.contains("field_model_name ="), sql);
        assertTrue(sql.contains("field_model_id ="), sql);
    }

    // ---------------------------------------------------------- NB2-BE-002 取号核心语句

    @Test
    void ruleSelectDistinctModelIdsSingleColumn() {
        String sql = boundSql(SysNumberRuleMapper.class.getName() + ".selectDistinctModelIds", Map.of());
        assertTrue(sql.contains("SELECT DISTINCT field_model_id"), sql);
        assertTrue(sql.contains("FROM sys_number_rule"), sql);
    }

    @Test
    void ruleSelectByModelBindsModelIdentity() {
        String sql = boundSql(SysNumberRuleMapper.class.getName() + ".selectByModel",
                Map.of("fieldModelName", "SysProcessExample", "fieldModelField", "fieldCode"));
        assertTrue(sql.contains("field_model_name ="), sql);
        assertTrue(sql.contains("field_model_field ="), sql);
    }

    @Test
    void recordUpsertSerialUsesAtomicAdvanceWithSealedRestart() {
        SysNumberRecord model = new SysNumberRecord();
        model.setFieldId("0".repeat(32));
        model.setFieldScopeKey("R".repeat(32));
        model.setFieldSerialId("ci-seq");
        model.setFieldPeriod("20261112");
        model.setFieldSerialNo(1);
        String sql = boundSql(SysNumberRecordMapper.class.getName() + ".upsertSerial", Map.of("model", model));
        assertTrue(sql.contains("INSERT INTO sys_number_record"), sql);
        // 方案 §5.2 原文口径：ODKU + LAST_INSERT_ID 原子自增取回新值
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"), sql);
        assertTrue(sql.contains("LAST_INSERT_ID"), sql);
        // 方案 §5.4 reset 后「下次取号按 start 重开」的取号侧实现：封存行重开（IF 先于清封存求值）
        assertTrue(sql.contains("IF(field_record_finish_time IS NULL"), sql);
        assertTrue(sql.contains("field_record_finish_time = NULL"), sql);
    }

    @Test
    void recordSelectCurrentSerialNoBindsScopeTriple() {
        String sql = boundSql(SysNumberRecordMapper.class.getName() + ".selectCurrentSerialNo",
                Map.of("fieldScopeKey", "R".repeat(32), "fieldSerialId", "ci-seq", "fieldPeriod", "20261112"));
        assertTrue(sql.contains("SELECT field_serial_no"), sql);
        assertTrue(sql.contains("field_scope_key ="), sql);
        assertTrue(sql.contains("field_serial_id ="), sql);
        assertTrue(sql.contains("field_period ="), sql);
    }

    @Test
    void recordSealOtherPeriodsOnlyTouchesUnfinishedRows() {
        String sql = boundSql(SysNumberRecordMapper.class.getName() + ".sealOtherPeriods",
                Map.of("fieldScopeKey", "R".repeat(32), "fieldSerialId", "ci-seq", "fieldPeriod", "20261112"));
        assertTrue(sql.contains("SET field_record_finish_time"), sql);
        assertTrue(sql.contains("field_period !="), sql);
        assertTrue(sql.contains("field_record_finish_time IS NULL"), sql);
    }

    @Test
    void detailSelectValidByScopeAndModelFiltersVoidWithOptionalLock() {
        Map<String, Object> plain = new HashMap<>();
        plain.put("fieldScopeKey", "R".repeat(32));
        plain.put("fieldModelId", "0".repeat(32));
        plain.put("lockForUpdate", false);
        String sql = boundSql(SysNumberRecordDetailMapper.class.getName() + ".selectValidByScopeAndModel", plain);
        assertTrue(sql.contains("field_void_time IS NULL"), sql);
        assertTrue(sql.contains("LIMIT 1"), sql);
        assertFalse(sql.contains("FOR UPDATE"), "幂等预检为普通读: " + sql);

        Map<String, Object> locked = new HashMap<>(plain);
        locked.put("lockForUpdate", true);
        String lockedSql = boundSql(SysNumberRecordDetailMapper.class.getName() + ".selectValidByScopeAndModel", locked);
        assertTrue(lockedSql.contains("FOR UPDATE"), "幂等二次确认为锁定读: " + lockedSql);
    }

    // ---------------------------------------------------------- NB3-BE-002 规则维护语句

    @Test
    void ruleSelectQueryListBindsFuzzyAndExactFilters() {
        SysNumberRuleQuery query = new SysNumberRuleQuery();
        query.setFieldName("订单");
        query.setFieldCode("CG");
        query.setFieldModelId("process-example-code");
        query.setFieldModelField("fieldCode");
        query.setFieldEnable(true);
        Pages pages = new Pages();
        pages.setPageNo(2);
        pages.setPageSize(10);
        pages.setNeedPage(true);
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("pages", pages);

        String sql = boundSql(SysNumberRuleMapper.class.getName() + ".selectQueryList", params);
        assertTrue(sql.contains("field_name LIKE"), sql);
        assertTrue(sql.contains("field_code LIKE"), sql);
        assertTrue(sql.contains("field_model_id ="), sql);
        assertTrue(sql.contains("field_model_field ="), sql);
        assertTrue(sql.contains("field_enable ="), sql);
        assertTrue(sql.contains("ORDER BY field_create_time DESC"), sql);
        assertTrue(sql.contains("LIMIT"), sql);

        // count 与列表共用条件片段，条件一致（供 metadata.total）
        String countSql = boundSql(SysNumberRuleMapper.class.getName() + ".countQueryList",
                Map.of("query", query));
        assertTrue(countSql.contains("COUNT(*)"), countSql);
        assertTrue(countSql.contains("field_name LIKE"), countSql);
        assertTrue(countSql.contains("field_code LIKE"), countSql);

        // 空条件 + 不分页：裸列表
        String plainSql = boundSql(SysNumberRuleMapper.class.getName() + ".selectQueryList",
                Map.of("query", new SysNumberRuleQuery(), "pages", new Pages()));
        assertFalse(plainSql.contains("WHERE"), plainSql);
        assertFalse(plainSql.contains("LIMIT"), plainSql);
    }

    @Test
    void ruleSelectByCodeMatchesExactCode() {
        String sql = boundSql(SysNumberRuleMapper.class.getName() + ".selectByCode",
                Map.of("fieldCode", "CG-RULE"));
        assertTrue(sql.contains("field_code ="), sql);
        assertTrue(sql.contains("LIMIT 1"), sql);
    }

    @Test
    void ruleCountEnabledByModelCountsEnabledOnly() {
        String sql = boundSql(SysNumberRuleMapper.class.getName() + ".countEnabledByModel",
                Map.of("fieldModelId", "process-example-code"));
        assertTrue(sql.contains("COUNT(*)"), sql);
        assertTrue(sql.contains("field_model_id ="), sql);
        assertTrue(sql.contains("field_enable = b'1'"), sql);
    }

    @Test
    void ruleDisableOtherEnabledByModelExcludesSelf() {
        String sql = boundSql(SysNumberRuleMapper.class.getName() + ".disableOtherEnabledByModel",
                Map.of("fieldModelId", "process-example-code", "excludeFieldId", "0".repeat(32),
                        "updaterId", "U", "updateTime", new Date()));
        assertTrue(sql.contains("UPDATE sys_number_rule"), sql);
        assertTrue(sql.contains("field_enable = b'0'"), sql);
        assertTrue(sql.contains("field_updater_id ="), sql);
        assertTrue(sql.contains("field_update_time ="), sql);
        assertTrue(sql.contains("field_enable = b'1'"), sql);
        assertTrue(sql.contains("field_id !="), sql);
    }

    @Test
    void detailCountByScopeKeyCountsByScope() {
        String sql = boundSql(SysNumberRecordDetailMapper.class.getName() + ".countByScopeKey",
                Map.of("fieldScopeKey", "0".repeat(32)));
        assertTrue(sql.contains("COUNT(*)"), sql);
        assertTrue(sql.contains("field_scope_key ="), sql);
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
