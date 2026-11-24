package com.eric.base.sys.form.mapper;

import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.form.model.SysFormVersion;
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
 * 表单版本 Mapper XML 解析与绑定测试
 *
 * @author Eric
 * @date 2026/11/24
 * @description 覆盖任务 F15-DB-001 的 XML 验收（对齐 SysFormulaMapperXmlTest 先例）：
 *              SysFormVersionMapper.xml 可被 MyBatis 正常解析（结构与 DTD 合法、
 *              resultMap/type 引用有效），基础语句（insert / update / deleteById /
 *              selectById / selectList / loadModel）与模块专属语句（selectCount /
 *              selectByFormId / selectMaxVersion / freezeDraft / deprecate /
 *              deleteByFormId）可按参数取到最终 SQL。不做数据库连接，只校验 SQL 文本
 *              口径：基础 update 承载「草稿行内容重写」语义且 WHERE 守护
 *              field_status='DRAFT'，freezeDraft / deprecate 分别守护 DRAFT /
 *              PUBLISHED 起始态（不可变版本原则的 WHERE 守护，分册 §5.2.6⑤⑥⑦）；
 *              表结构落地验证随 dev 库启动 VALIDATE 观察（注解同步引擎）。
 */
class SysFormVersionMapperXmlTest {

    private static final String VERSION_MAPPER = "mappers/sys/form/SysFormVersionMapper.xml";

    private static Configuration configuration;

    @BeforeAll
    static void parseMappers() throws IOException {
        configuration = new Configuration();
        // 解析 Mapper：解析成功本身即校验 XML 结构合法、类型引用有效
        parse(VERSION_MAPPER);
    }

    @Test
    void insertContainsAllColumns() {
        String sql = boundSql(SysFormVersionMapper.class.getName() + ".insert",
                Map.of("model", new SysFormVersion()));
        assertTrue(sql.contains("INSERT INTO sys_form_version"), sql);
        for (String column : new String[]{"field_id", "field_form_id", "field_version",
                "field_schema_json", "field_status", "field_published_at", "field_creator_id",
                "field_create_time", "field_updater_id", "field_update_time", "field_enable"}) {
            assertTrue(sql.contains(column), "insert 缺少列 " + column + ": " + sql);
        }
    }

    @Test
    void updateRewritesDraftSchemaOnly() {
        // 基础 update = 草稿行内容重写（分册 §5.2.6⑤）：仅 SET field_schema_json + 审计两列
        SysFormVersion model = new SysFormVersion();
        model.setFieldId("0".repeat(32));
        model.setFieldSchemaJson("{\"formId\":\"0\".repeat(32),\"fields\":[]}");
        model.setFieldUpdaterId("U");
        model.setFieldUpdateTime(new Date());
        String sql = boundSql(SysFormVersionMapper.class.getName() + ".update", Map.of("model", model));
        assertTrue(sql.contains("UPDATE sys_form_version"), sql);
        assertTrue(sql.contains("field_schema_json ="), sql);
        assertTrue(sql.contains("field_updater_id ="), sql);
        assertTrue(sql.contains("field_update_time ="), sql);
        // WHERE 守护 DRAFT：防御 PUBLISHED / DEPRECATED 终态行被写（不可变版本双保险）
        assertTrue(sql.contains("AND field_status = 'DRAFT'"), sql);
        for (String forbidden : new String[]{"field_form_id =", "field_version =",
                "field_status = #{", "field_published_at ="}) {
            assertFalse(sql.contains(forbidden), "草稿重写不得改 " + forbidden + ": " + sql);
        }
    }

    @Test
    void freezeDraftGuardsDraftStatus() {
        // 草稿冻结（分册 §5.2.6⑥）：回填版本号 / PUBLISHED / 发布时刻 + 审计，WHERE 守护 DRAFT
        String sql = boundSql(SysFormVersionMapper.class.getName() + ".freezeDraft",
                Map.of("fieldId", "0".repeat(32), "fieldVersion", 1,
                        "publishedAt", new Date(), "updaterId", "U", "updateTime", new Date()));
        assertTrue(sql.contains("UPDATE sys_form_version"), sql);
        assertTrue(sql.contains("field_version ="), sql);
        assertTrue(sql.contains("field_status = 'PUBLISHED'"), sql);
        assertTrue(sql.contains("field_published_at ="), sql);
        assertTrue(sql.contains("field_updater_id ="), sql);
        assertTrue(sql.contains("field_update_time ="), sql);
        assertTrue(sql.contains("AND field_status = 'DRAFT'"), sql);
        assertFalse(sql.contains("field_schema_json ="), "冻结只改状态与审计，不改快照内容: " + sql);
    }

    @Test
    void deprecateGuardsPublishedStatus() {
        // 旧版本退役（分册 §5.2.6⑦）：PUBLISHED→DEPRECATED，WHERE 守护 PUBLISHED 起始态
        String sql = boundSql(SysFormVersionMapper.class.getName() + ".deprecate",
                Map.of("fieldId", "0".repeat(32), "updaterId", "U", "updateTime", new Date()));
        assertTrue(sql.contains("UPDATE sys_form_version"), sql);
        assertTrue(sql.contains("field_status = 'DEPRECATED'"), sql);
        assertTrue(sql.contains("field_updater_id ="), sql);
        assertTrue(sql.contains("field_update_time ="), sql);
        assertTrue(sql.contains("AND field_status = 'PUBLISHED'"), sql);
        assertFalse(sql.contains("field_schema_json ="), "退役只改状态与审计，不改快照内容: " + sql);
    }

    @Test
    void selectByFormIdOrdersVersionDesc() {
        String sql = boundSql(SysFormVersionMapper.class.getName() + ".selectByFormId",
                Map.of("formId", "0".repeat(32)));
        assertTrue(sql.contains("field_form_id ="), sql);
        assertTrue(sql.contains("ORDER BY field_version DESC"), sql);
    }

    @Test
    void selectMaxVersionComputesMax() {
        String sql = boundSql(SysFormVersionMapper.class.getName() + ".selectMaxVersion",
                Map.of("formId", "0".repeat(32)));
        assertTrue(sql.contains("MAX(field_version)"), sql);
        assertTrue(sql.contains("field_form_id ="), sql);
    }

    @Test
    void selectListBindsFiltersAndPaging() {
        SysFormVersion model = new SysFormVersion();
        model.setFieldFormId("0".repeat(32));
        model.setFieldStatus("PUBLISHED");
        Pages pages = new Pages();
        pages.setPageNo(1);
        pages.setPageSize(20);
        pages.setNeedPage(true);
        Map<String, Object> params = new HashMap<>();
        params.put("model", model);
        params.put("pages", pages);

        String sql = boundSql(SysFormVersionMapper.class.getName() + ".selectList", params);
        assertTrue(sql.contains("field_form_id ="), sql);
        assertTrue(sql.contains("field_status ="), sql);
        assertTrue(sql.contains("ORDER BY field_create_time DESC"), sql);
        assertTrue(sql.contains("LIMIT"), sql);

        // 无条件 + 不分页：裸列表
        String plainSql = boundSql(SysFormVersionMapper.class.getName() + ".selectList",
                Map.of("model", new SysFormVersion(), "pages", new Pages()));
        assertFalse(plainSql.contains("WHERE"), plainSql);
        assertFalse(plainSql.contains("LIMIT"), plainSql);
    }

    @Test
    void selectCountSharesQueryConditions() {
        // count 与列表共用 QueryConditions 片段，条件一致（供 metadata.total）
        SysFormVersion model = new SysFormVersion();
        model.setFieldFormId("0".repeat(32));
        model.setFieldStatus("DRAFT");
        String sql = boundSql(SysFormVersionMapper.class.getName() + ".selectCount", Map.of("model", model));
        assertTrue(sql.contains("COUNT(*)"), sql);
        assertTrue(sql.contains("field_form_id ="), sql);
        assertTrue(sql.contains("field_status ="), sql);

        // 无条件：全表计数
        String plainSql = boundSql(SysFormVersionMapper.class.getName() + ".selectCount",
                Map.of("model", new SysFormVersion()));
        assertFalse(plainSql.contains("WHERE"), plainSql);
    }

    @Test
    void loadModelReturnsIdAndVersionOnly() {
        // 版本行无名称列，以整数版本号作为轻量展示名（草稿行为 null）
        String sql = boundSql(SysFormVersionMapper.class.getName() + ".loadModel",
                Map.of("fieldId", "0".repeat(32)));
        assertTrue(sql.contains("SELECT field_id, field_version AS field_name"), sql);
        assertFalse(sql.contains("field_schema_json"), "轻量加载不取 Schema 快照: " + sql);
        assertFalse(sql.contains("field_status"), "轻量加载不取状态: " + sql);
    }

    @Test
    void deleteByFormIdDeletesAllVersionRows() {
        String sql = boundSql(SysFormVersionMapper.class.getName() + ".deleteByFormId",
                Map.of("formId", "0".repeat(32)));
        assertTrue(sql.contains("DELETE FROM sys_form_version"), sql);
        assertTrue(sql.contains("field_form_id ="), sql);
    }

    @Test
    void deleteByIdDeletesByPrimaryKey() {
        String sql = boundSql(SysFormVersionMapper.class.getName() + ".deleteById",
                Map.of("fieldId", "0".repeat(32)));
        assertTrue(sql.contains("DELETE FROM sys_form_version"), sql);
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
