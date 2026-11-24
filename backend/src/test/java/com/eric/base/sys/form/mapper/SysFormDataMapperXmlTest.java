package com.eric.base.sys.form.mapper;

import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.form.model.SysFormData;
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
 * 表单数据 Mapper XML 解析与绑定测试
 *
 * @author Eric
 * @date 2026/11/24
 * @description 覆盖任务 F15-DB-002 的 XML 验收（对齐 SysFormMapperXmlTest 先例）：
 *              SysFormDataMapper.xml 可被 MyBatis 正常解析（结构与 DTD 合法、
 *              resultMap/type 引用有效），基础语句（insert / update / deleteById /
 *              selectById / selectList / loadModel）与模块专属语句（selectCount /
 *              existsByFormId）可按参数取到最终 SQL。不做数据库连接，只校验 SQL 文本
 *              口径：insert 全列；selectList 走 BaseColumns 不含 field_form_data 而
 *              selectById 走 DetailColumns 含 field_form_data（列表行不返回数据内容，
 *              分册 §5.3.6④）；基础 update 为恒不生效的防御性占位（一期只增不改，
 *              分册 §4.4 / §5.3.6）；deleteById 物理删除；表结构落地验证随 dev 库
 *              启动 VALIDATE 观察（注解同步引擎）。
 */
class SysFormDataMapperXmlTest {

    private static final String FORM_DATA_MAPPER = "mappers/sys/form/SysFormDataMapper.xml";

    private static Configuration configuration;

    @BeforeAll
    static void parseMappers() throws IOException {
        configuration = new Configuration();
        // 解析 Mapper：解析成功本身即校验 XML 结构合法、类型引用有效
        parse(FORM_DATA_MAPPER);
    }

    @Test
    void insertContainsAllColumns() {
        String sql = boundSql(SysFormDataMapper.class.getName() + ".insert",
                Map.of("model", new SysFormData()));
        assertTrue(sql.contains("INSERT INTO sys_form_data"), sql);
        for (String column : new String[]{"field_id", "field_form_id", "field_form_version_id",
                "field_business_id", "field_process_instance_id", "field_form_data",
                "field_creator_id", "field_create_time"}) {
            assertTrue(sql.contains(column), "insert 缺少列 " + column + ": " + sql);
        }
    }

    @Test
    void updatePlaceholderNeverTakesEffect() {
        // 一期只增不改（分册 §5.3.6）：基础 update 为恒不生效的防御性占位——
        // SET 自身恒等 + AND 1 = 0，任何调用影响行数恒为 0
        SysFormData model = new SysFormData();
        model.setFieldId("0".repeat(32));
        String sql = boundSql(SysFormDataMapper.class.getName() + ".update", Map.of("model", model));
        assertTrue(sql.contains("UPDATE sys_form_data"), sql);
        assertTrue(sql.contains("SET field_id = field_id"), sql);
        assertTrue(sql.contains("AND 1 = 0"), sql);
        // 占位不得触碰任何业务列（禁止承载实例更新语义）
        for (String forbidden : new String[]{"field_form_id =", "field_form_version_id =",
                "field_business_id =", "field_process_instance_id =", "field_form_data =",
                "field_creator_id =", "field_create_time ="}) {
            assertFalse(sql.contains(forbidden), "update 占位不得改业务列 " + forbidden + ": " + sql);
        }
    }

    @Test
    void selectListBindsFiltersAndPaging() {
        SysFormData model = new SysFormData();
        model.setFieldFormId("0".repeat(32));
        model.setFieldBusinessId("1".repeat(32));
        model.setFieldProcessInstanceId("2".repeat(32));
        model.setFieldCreatorId("U");
        Pages pages = new Pages();
        pages.setPageNo(2);
        pages.setPageSize(10);
        pages.setNeedPage(true);
        Map<String, Object> params = new HashMap<>();
        params.put("model", model);
        params.put("pages", pages);

        String sql = boundSql(SysFormDataMapper.class.getName() + ".selectList", params);
        assertTrue(sql.contains("field_form_id ="), sql);
        assertTrue(sql.contains("field_business_id ="), sql);
        assertTrue(sql.contains("field_process_instance_id ="), sql);
        assertTrue(sql.contains("field_creator_id ="), sql);
        assertTrue(sql.contains("ORDER BY field_create_time DESC"), sql);
        assertTrue(sql.contains("LIMIT"), sql);
        // 列表行不返回 field_form_data 内容（分册 §5.3.6④，走 BaseColumns）
        assertFalse(sql.contains("field_form_data"), "列表行不得返回 field_form_data: " + sql);

        // 无条件 + 不分页：裸列表
        String plainSql = boundSql(SysFormDataMapper.class.getName() + ".selectList",
                Map.of("model", new SysFormData(), "pages", new Pages()));
        assertFalse(plainSql.contains("WHERE"), plainSql);
        assertFalse(plainSql.contains("LIMIT"), plainSql);
    }

    @Test
    void selectByIdReturnsDetailColumns() {
        // 详情回显（分册 §5.3.6②）：走 DetailColumns，含 field_form_data
        String sql = boundSql(SysFormDataMapper.class.getName() + ".selectById",
                Map.of("fieldId", "0".repeat(32)));
        assertTrue(sql.contains("field_form_data"), "selectById 须含 field_form_data: " + sql);
        assertTrue(sql.contains("field_creator_id"), sql);
        assertTrue(sql.contains("field_create_time"), sql);
        assertTrue(sql.contains("field_id ="), sql);
    }

    @Test
    void selectCountSharesQueryConditions() {
        // count 与列表共用 QueryConditions 片段，条件一致（供 metadata.total）
        SysFormData model = new SysFormData();
        model.setFieldFormId("0".repeat(32));
        model.setFieldCreatorId("U");
        String sql = boundSql(SysFormDataMapper.class.getName() + ".selectCount", Map.of("model", model));
        assertTrue(sql.contains("COUNT(*)"), sql);
        assertTrue(sql.contains("field_form_id ="), sql);
        assertTrue(sql.contains("field_creator_id ="), sql);

        // 无条件：全表计数
        String plainSql = boundSql(SysFormDataMapper.class.getName() + ".selectCount",
                Map.of("model", new SysFormData()));
        assertFalse(plainSql.contains("WHERE"), plainSql);
    }

    @Test
    void existsByFormIdChecksExistence() {
        // /define/delete 前置存在性探测（CS_FORM_HAS_DATA，API 分册 §3.7）
        String sql = boundSql(SysFormDataMapper.class.getName() + ".existsByFormId",
                Map.of("formId", "0".repeat(32)));
        assertTrue(sql.contains("EXISTS("), sql);
        assertTrue(sql.contains("field_form_id ="), sql);
    }

    @Test
    void loadModelReturnsIdAndFormIdOnly() {
        // 轻量加载：本表无名称列，返回 field_id 与业务标识列 field_form_id（先例
        // SysAttOperationMapper.loadModel）；不取数据内容与审计列
        String sql = boundSql(SysFormDataMapper.class.getName() + ".loadModel",
                Map.of("fieldId", "0".repeat(32)));
        assertTrue(sql.contains("SELECT field_id, field_form_id"), sql);
        assertFalse(sql.contains("field_form_data"), "轻量加载不取数据内容: " + sql);
        assertFalse(sql.contains("field_form_version_id"), "轻量加载不取版本锁定: " + sql);
        assertFalse(sql.contains("field_creator_id"), "轻量加载不取创建人: " + sql);
    }

    @Test
    void deleteByIdDeletesByPrimaryKey() {
        // 物理删除（分册 §4.4 删除语义定稿），无软删
        String sql = boundSql(SysFormDataMapper.class.getName() + ".deleteById",
                Map.of("fieldId", "0".repeat(32)));
        assertTrue(sql.contains("DELETE FROM sys_form_data"), sql);
        assertTrue(sql.contains("field_id ="), sql);
        assertFalse(sql.contains("UPDATE"), "物理删除不走 UPDATE: " + sql);
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
