package com.eric.base.sys.number.mapper;

import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.number.model.SysNumberRecord;
import com.eric.base.sys.number.model.SysNumberRecordDetail;
import com.eric.base.sys.number.model.SysNumberRule;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
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
