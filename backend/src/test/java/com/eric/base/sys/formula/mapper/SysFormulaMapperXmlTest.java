package com.eric.base.sys.formula.mapper;

import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.formula.model.SysFormula;
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
 * 公式 Mapper XML 解析与绑定测试
 *
 * @author Eric
 * @date 2026/11/18
 * @description 覆盖任务 F14-BE-006 的 XML 验收（对齐 SysNumberMapperXmlTest 先例）：
 *              SysFormulaMapper.xml 可被 MyBatis 正常解析（结构与 DTD 合法、
 *              resultMap/type 引用有效），基础语句（insert / update / deleteById /
 *              selectById / selectList / loadModel）与模块专属语句（selectCount /
 *              selectByCode / updateEnable）可按参数取到最终 SQL。不做数据库连接，
 *              只校验 SQL 文本口径（先例 SysOrgElementExclusionSqlTest）；
 *              表结构落地验证随本地启动 VALIDATE 观察（F14-DB-001 注解同步引擎）。
 */
class SysFormulaMapperXmlTest {

    private static final String FORMULA_MAPPER = "mappers/sys/formula/SysFormulaMapper.xml";

    private static Configuration configuration;

    @BeforeAll
    static void parseMappers() throws IOException {
        configuration = new Configuration();
        // 解析 Mapper：解析成功本身即校验 XML 结构合法、类型引用有效
        parse(FORMULA_MAPPER);
    }

    @Test
    void insertContainsAllColumns() {
        String sql = boundSql(SysFormulaMapper.class.getName() + ".insert",
                Map.of("model", new SysFormula()));
        assertTrue(sql.contains("INSERT INTO sys_formula"), sql);
        for (String column : new String[]{"field_id", "field_name", "field_code", "field_creator_id",
                "field_create_time", "field_updater_id", "field_update_time", "field_enable",
                "field_type", "field_expression", "field_dependencies", "field_return_type", "field_remark"}) {
            assertTrue(sql.contains(column), "insert 缺少列 " + column + ": " + sql);
        }
    }

    @Test
    void updateTouchesBusinessFieldsAndDependencies() {
        // 业务列（含 field_dependencies）随保存/更新动态可改：依赖由 Service 解析表达式后序列化落库
        SysFormula model = new SysFormula();
        model.setFieldId("0".repeat(32));
        model.setFieldName("含税金额");
        model.setFieldExpression("ROUND(${applyAmount} * ${taxRate}, 2)");
        model.setFieldDependencies("[\"applyAmount\",\"taxRate\"]");
        String sql = boundSql(SysFormulaMapper.class.getName() + ".update", Map.of("model", model));
        assertTrue(sql.contains("UPDATE sys_formula"), sql);
        assertTrue(sql.contains("field_name ="), sql);
        assertTrue(sql.contains("field_expression ="), sql);
        assertTrue(sql.contains("field_dependencies ="), sql);
    }

    @Test
    void selectListBindsFiltersAndPaging() {
        SysFormula model = new SysFormula();
        model.setFieldCode("TAX");
        model.setFieldName("金额");
        model.setFieldType("FIELD");
        model.setFieldEnable(true);
        Pages pages = new Pages();
        pages.setPageNo(2);
        pages.setPageSize(10);
        pages.setNeedPage(true);
        Map<String, Object> params = new HashMap<>();
        params.put("model", model);
        params.put("pages", pages);

        String sql = boundSql(SysFormulaMapper.class.getName() + ".selectList", params);
        assertTrue(sql.contains("field_code LIKE"), sql);
        assertTrue(sql.contains("field_name LIKE"), sql);
        assertTrue(sql.contains("field_type ="), sql);
        assertTrue(sql.contains("field_enable ="), sql);
        assertTrue(sql.contains("ORDER BY field_create_time DESC"), sql);
        assertTrue(sql.contains("LIMIT"), sql);

        // 无条件 + 不分页：裸列表
        String plainSql = boundSql(SysFormulaMapper.class.getName() + ".selectList",
                Map.of("model", new SysFormula(), "pages", new Pages()));
        assertFalse(plainSql.contains("WHERE"), plainSql);
        assertFalse(plainSql.contains("LIMIT"), plainSql);
    }

    @Test
    void selectCountSharesQueryConditions() {
        // count 与列表共用 QueryConditions 片段，条件一致（供 metadata.total）
        SysFormula model = new SysFormula();
        model.setFieldCode("TAX");
        model.setFieldType("FIELD");
        String sql = boundSql(SysFormulaMapper.class.getName() + ".selectCount", Map.of("model", model));
        assertTrue(sql.contains("COUNT(*)"), sql);
        assertTrue(sql.contains("field_code LIKE"), sql);
        assertTrue(sql.contains("field_type ="), sql);

        // 无条件：全表计数
        String plainSql = boundSql(SysFormulaMapper.class.getName() + ".selectCount",
                Map.of("model", new SysFormula()));
        assertFalse(plainSql.contains("WHERE"), plainSql);
    }

    @Test
    void selectByCodeMatchesExactCode() {
        String sql = boundSql(SysFormulaMapper.class.getName() + ".selectByCode",
                Map.of("fieldCode", "TAX_INCLUDED_AMOUNT"));
        assertTrue(sql.contains("field_code ="), sql);
        assertTrue(sql.contains("LIMIT 1"), sql);
    }

    @Test
    void updateEnableTouchesOnlyEnableFields() {
        // /enable 通道：仅更新 field_enable 与审计字段，不触碰业务字段
        String sql = boundSql(SysFormulaMapper.class.getName() + ".updateEnable",
                Map.of("fieldId", "0".repeat(32), "fieldEnable", Boolean.FALSE,
                        "updaterId", "U", "updateTime", new Date()));
        assertTrue(sql.contains("UPDATE sys_formula"), sql);
        assertTrue(sql.contains("field_enable ="), sql);
        assertTrue(sql.contains("field_updater_id ="), sql);
        assertTrue(sql.contains("field_update_time ="), sql);
        assertTrue(sql.contains("field_id ="), sql);
        for (String forbidden : new String[]{"field_code =", "field_name =", "field_type =",
                "field_expression =", "field_dependencies =", "field_return_type =", "field_remark ="}) {
            assertFalse(sql.contains(forbidden), "enable 不得改业务字段 " + forbidden + ": " + sql);
        }
    }

    @Test
    void loadModelReturnsIdAndNameOnly() {
        String sql = boundSql(SysFormulaMapper.class.getName() + ".loadModel",
                Map.of("fieldId", "0".repeat(32)));
        assertTrue(sql.contains("SELECT field_id, field_name"), sql);
        assertFalse(sql.contains("field_expression"), "轻量加载不取表达式文本: " + sql);
        assertFalse(sql.contains("field_dependencies"), "轻量加载不取依赖 JSON: " + sql);
    }

    @Test
    void deleteByIdDeletesByPrimaryKey() {
        String sql = boundSql(SysFormulaMapper.class.getName() + ".deleteById",
                Map.of("fieldId", "0".repeat(32)));
        assertTrue(sql.contains("DELETE FROM sys_formula"), sql);
        assertTrue(sql.contains("field_id ="), sql);
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
