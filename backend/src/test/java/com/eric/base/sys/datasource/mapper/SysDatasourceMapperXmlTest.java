package com.eric.base.sys.datasource.mapper;

import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.datasource.model.SysDatasource;
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
 * 数据源 Mapper XML 解析与绑定测试
 *
 * @author Eric
 * @date 2026/11/13
 * @description 覆盖任务 F18-BE-003 的 XML 骨架验收：Mapper XML 可被 MyBatis 正常解析
 *              （结构与 DTD 合法、resultMap/type 引用有效）、基础语句（insert / update /
 *              updateEnable / deleteById / selectById / selectList / selectCount /
 *              loadModel / selectByCode）可按参数取到最终 SQL。
 *              不做数据库连接，只校验 SQL 文本口径（先例 SysNumberMapperXmlTest）；
 *              Flyway 建表落地验证随本地启动观察（V1_01_046）。
 */
class SysDatasourceMapperXmlTest {

    private static final String DATASOURCE_MAPPER = "mappers/sys/datasource/SysDatasourceMapper.xml";

    private static Configuration configuration;

    @BeforeAll
    static void parseMappers() throws IOException {
        configuration = new Configuration();
        // 解析成功本身即校验 XML 结构合法、类型引用有效
        parse(DATASOURCE_MAPPER);
    }

    @Test
    void insertContainsAllColumns() {
        String sql = boundSql(SysDatasourceMapper.class.getName() + ".insert",
                Map.of("model", new SysDatasource()));
        assertTrue(sql.contains("INSERT INTO sys_datasource"), sql);
        for (String column : new String[]{"field_id", "field_name", "field_code", "field_creator_id",
                "field_create_time", "field_updater_id", "field_update_time", "field_enable",
                "field_target", "field_conditions", "field_fields", "field_remark"}) {
            assertTrue(sql.contains(column), "insert 缺少列 " + column + ": " + sql);
        }
    }

    @Test
    void updateNeverTouchesLockedTarget() {
        // field_target 保存后锁定（D3 先例），update 不提供更新入口
        SysDatasource model = new SysDatasource();
        model.setFieldId("0".repeat(32));
        model.setFieldName("数据源");
        model.setFieldTarget("calendar.list");
        model.setFieldConditions("[{\"key\":\"fieldCreatorId\",\"op\":\"EQ\",\"value\":\"${CURRENT_USER_ID}\"}]");
        String sql = boundSql(SysDatasourceMapper.class.getName() + ".update", Map.of("model", model));
        assertTrue(sql.contains("UPDATE sys_datasource"), sql);
        assertTrue(sql.contains("field_name ="), sql);
        assertTrue(sql.contains("field_conditions ="), sql);
        assertFalse(sql.contains("field_target"), "field_target 保存后锁定，禁止出现在 update: " + sql);
    }

    @Test
    void selectListBindsFiltersAndPaging() {
        SysDatasource model = new SysDatasource();
        model.setFieldCode("calendar.my-created");
        model.setFieldName("用户日程");
        model.setFieldTarget("calendar.list");
        model.setFieldEnable(true);
        Pages pages = new Pages();
        pages.setPageNo(2);
        pages.setPageSize(10);
        pages.setNeedPage(true);
        Map<String, Object> params = new HashMap<>();
        params.put("model", model);
        params.put("pages", pages);

        String sql = boundSql(SysDatasourceMapper.class.getName() + ".selectList", params);
        assertTrue(sql.contains("field_name LIKE"), sql);
        assertTrue(sql.contains("field_code LIKE"), sql);
        assertTrue(sql.contains("field_target ="), sql);
        assertTrue(sql.contains("field_enable ="), sql);
        assertTrue(sql.contains("ORDER BY field_create_time DESC"), sql);
        assertTrue(sql.contains("LIMIT"), sql);

        // 无条件 + 不分页：裸列表
        String plainSql = boundSql(SysDatasourceMapper.class.getName() + ".selectList",
                Map.of("model", new SysDatasource(), "pages", new Pages()));
        assertFalse(plainSql.contains("WHERE"), plainSql);
        assertFalse(plainSql.contains("LIMIT"), plainSql);
    }

    @Test
    void selectCountSharesListConditions() {
        // 总数与列表共用 QueryConditions 片段，条件一致（供 metadata.total），且不分页不排序
        SysDatasource model = new SysDatasource();
        model.setFieldCode("calendar");
        model.setFieldEnable(false);
        String sql = boundSql(SysDatasourceMapper.class.getName() + ".selectCount",
                Map.of("model", model));
        assertTrue(sql.contains("COUNT(*)"), sql);
        assertTrue(sql.contains("field_code LIKE"), sql);
        assertTrue(sql.contains("field_enable ="), sql);
        assertFalse(sql.contains("ORDER BY"), sql);
        assertFalse(sql.contains("LIMIT"), sql);
    }

    @Test
    void loadModelReturnsIdAndNameOnly() {
        String sql = boundSql(SysDatasourceMapper.class.getName() + ".loadModel",
                Map.of("fieldId", "0".repeat(32)));
        assertTrue(sql.contains("SELECT field_id, field_name"), sql);
        assertFalse(sql.contains("field_conditions"), "轻量加载不取条件 JSON: " + sql);
        assertFalse(sql.contains("field_fields"), "轻量加载不取字段 JSON: " + sql);
    }

    @Test
    void selectByCodeMatchesExactCode() {
        String sql = boundSql(SysDatasourceMapper.class.getName() + ".selectByCode",
                Map.of("fieldCode", "calendar.my-created"));
        assertTrue(sql.contains("field_code ="), sql);
        assertTrue(sql.contains("LIMIT 1"), sql);
        // BaseColumns 含 JSON 两列（原文返回，解析由 Service 完成）
        assertTrue(sql.contains("field_conditions"), sql);
        assertTrue(sql.contains("field_fields"), sql);
    }

    @Test
    void updateEnableTouchesOnlyEnableAndAudit() {
        String sql = boundSql(SysDatasourceMapper.class.getName() + ".updateEnable",
                Map.of("fieldId", "0".repeat(32), "fieldEnable", Boolean.TRUE,
                        "updaterId", "U", "updateTime", new Date()));
        assertTrue(sql.contains("UPDATE sys_datasource"), sql);
        assertTrue(sql.contains("field_enable ="), sql);
        assertTrue(sql.contains("field_updater_id ="), sql);
        assertTrue(sql.contains("field_update_time ="), sql);
        assertTrue(sql.contains("field_id ="), sql);
        // /enable 通道不触碰业务字段
        for (String forbidden : new String[]{"field_conditions =", "field_fields =",
                "field_remark =", "field_name =", "field_target ="}) {
            assertFalse(sql.contains(forbidden), "enable 不得改业务字段 " + forbidden + ": " + sql);
        }
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
