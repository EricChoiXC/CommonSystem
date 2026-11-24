package com.eric.base.sys.form.mapper;

import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.form.model.SysForm;
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
 * 表单主档 Mapper XML 解析与绑定测试
 *
 * @author Eric
 * @date 2026/11/24
 * @description 覆盖任务 F15-DB-001 的 XML 验收（对齐 SysFormulaMapperXmlTest 先例）：
 *              SysFormMapper.xml 可被 MyBatis 正常解析（结构与 DTD 合法、resultMap/
 *              type 引用有效），基础语句（insert / update / deleteById / selectById /
 *              selectList / loadModel）与模块专属语句（selectCount / selectByCode /
 *              updateEnable / updateCurrentVersionId）可按参数取到最终 SQL。不做数据库
 *              连接，只校验 SQL 文本口径；列清单不含 field_code / field_name（MainModel
 *              继承属性不入列清单，分册 §5.1.2）；表结构落地验证随 dev 库启动 VALIDATE
 *              观察（注解同步引擎）。
 */
class SysFormMapperXmlTest {

    private static final String FORM_MAPPER = "mappers/sys/form/SysFormMapper.xml";

    private static Configuration configuration;

    @BeforeAll
    static void parseMappers() throws IOException {
        configuration = new Configuration();
        // 解析 Mapper：解析成功本身即校验 XML 结构合法、类型引用有效
        parse(FORM_MAPPER);
    }

    @Test
    void insertContainsAllColumns() {
        String sql = boundSql(SysFormMapper.class.getName() + ".insert",
                Map.of("model", new SysForm()));
        assertTrue(sql.contains("INSERT INTO sys_form"), sql);
        for (String column : new String[]{"field_id", "field_form_code", "field_form_name",
                "field_current_version_id", "field_status", "field_creator_id", "field_create_time",
                "field_updater_id", "field_update_time", "field_enable"}) {
            assertTrue(sql.contains(column), "insert 缺少列 " + column + ": " + sql);
        }
    }

    @Test
    void updateTouchesMetadataOnly() {
        // 元数据更新（分册 §5.1.6⑤）：仅 field_form_name + 审计列
        SysForm model = new SysForm();
        model.setFieldId("0".repeat(32));
        model.setFieldFormName("采购申请单");
        model.setFieldUpdaterId("U");
        model.setFieldUpdateTime(new Date());
        String sql = boundSql(SysFormMapper.class.getName() + ".update", Map.of("model", model));
        assertTrue(sql.contains("UPDATE sys_form"), sql);
        assertTrue(sql.contains("field_form_name ="), sql);
        assertTrue(sql.contains("field_updater_id ="), sql);
        assertTrue(sql.contains("field_update_time ="), sql);
        // 编码禁改（保存后稳定标识）、状态由发布事务切换、启停用走 updateEnable 专用通道
        for (String forbidden : new String[]{"field_form_code =", "field_status =",
                "field_current_version_id =", "field_enable ="}) {
            assertFalse(sql.contains(forbidden), "元数据更新不得改 " + forbidden + ": " + sql);
        }
    }

    @Test
    void selectListBindsFiltersAndPaging() {
        SysForm model = new SysForm();
        model.setFieldFormCode("FRM");
        model.setFieldFormName("申请");
        model.setFieldStatus("DRAFT");
        model.setFieldEnable(true);
        Pages pages = new Pages();
        pages.setPageNo(2);
        pages.setPageSize(10);
        pages.setNeedPage(true);
        Map<String, Object> params = new HashMap<>();
        params.put("model", model);
        params.put("pages", pages);

        String sql = boundSql(SysFormMapper.class.getName() + ".selectList", params);
        assertTrue(sql.contains("field_form_code LIKE"), sql);
        assertTrue(sql.contains("field_form_name LIKE"), sql);
        assertTrue(sql.contains("field_status ="), sql);
        assertTrue(sql.contains("field_enable ="), sql);
        assertTrue(sql.contains("ORDER BY field_create_time DESC"), sql);
        assertTrue(sql.contains("LIMIT"), sql);

        // 无条件 + 不分页：裸列表
        String plainSql = boundSql(SysFormMapper.class.getName() + ".selectList",
                Map.of("model", new SysForm(), "pages", new Pages()));
        assertFalse(plainSql.contains("WHERE"), plainSql);
        assertFalse(plainSql.contains("LIMIT"), plainSql);
    }

    @Test
    void selectCountSharesQueryConditions() {
        // count 与列表共用 QueryConditions 片段，条件一致（供 metadata.total）
        SysForm model = new SysForm();
        model.setFieldFormCode("FRM");
        model.setFieldStatus("PUBLISHED");
        String sql = boundSql(SysFormMapper.class.getName() + ".selectCount", Map.of("model", model));
        assertTrue(sql.contains("COUNT(*)"), sql);
        assertTrue(sql.contains("field_form_code LIKE"), sql);
        assertTrue(sql.contains("field_status ="), sql);

        // 无条件：全表计数
        String plainSql = boundSql(SysFormMapper.class.getName() + ".selectCount",
                Map.of("model", new SysForm()));
        assertFalse(plainSql.contains("WHERE"), plainSql);
    }

    @Test
    void selectByCodeMatchesExactCode() {
        String sql = boundSql(SysFormMapper.class.getName() + ".selectByCode",
                Map.of("fieldCode", "PURCHASE_REQUEST"));
        assertTrue(sql.contains("field_form_code ="), sql);
        assertTrue(sql.contains("LIMIT 1"), sql);
    }

    @Test
    void updateEnableTouchesOnlyEnableFields() {
        // /define/enable 通道：仅更新 field_enable 与审计字段，不触碰业务字段
        String sql = boundSql(SysFormMapper.class.getName() + ".updateEnable",
                Map.of("fieldId", "0".repeat(32), "fieldEnable", Boolean.FALSE,
                        "updaterId", "U", "updateTime", new Date()));
        assertTrue(sql.contains("UPDATE sys_form"), sql);
        assertTrue(sql.contains("field_enable ="), sql);
        assertTrue(sql.contains("field_updater_id ="), sql);
        assertTrue(sql.contains("field_update_time ="), sql);
        assertTrue(sql.contains("field_id ="), sql);
        for (String forbidden : new String[]{"field_form_code =", "field_form_name =",
                "field_status =", "field_current_version_id ="}) {
            assertFalse(sql.contains(forbidden), "enable 不得改业务字段 " + forbidden + ": " + sql);
        }
    }

    @Test
    void updateCurrentVersionIdSwitchesPointerWithStatus() {
        // 发布/回滚事务内指针与状态成对切换（分册 §5.1.6⑦），不触碰编码/名称等元数据
        String sql = boundSql(SysFormMapper.class.getName() + ".updateCurrentVersionId",
                Map.of("fieldId", "0".repeat(32), "fieldStatus", "PUBLISHED",
                        "fieldCurrentVersionId", "1".repeat(32),
                        "updaterId", "U", "updateTime", new Date()));
        assertTrue(sql.contains("UPDATE sys_form"), sql);
        assertTrue(sql.contains("field_current_version_id ="), sql);
        assertTrue(sql.contains("field_status ="), sql);
        assertTrue(sql.contains("field_updater_id ="), sql);
        assertTrue(sql.contains("field_update_time ="), sql);
        assertTrue(sql.contains("field_id ="), sql);
        for (String forbidden : new String[]{"field_form_code =", "field_form_name =", "field_enable ="}) {
            assertFalse(sql.contains(forbidden), "指针切换不得改元数据 " + forbidden + ": " + sql);
        }
    }

    @Test
    void loadModelReturnsIdAndNameOnly() {
        // 本表名称列为 field_form_name，别名对齐 loadModel 契约的 field_name
        String sql = boundSql(SysFormMapper.class.getName() + ".loadModel",
                Map.of("fieldId", "0".repeat(32)));
        assertTrue(sql.contains("SELECT field_id, field_form_name AS field_name"), sql);
        assertFalse(sql.contains("field_status"), "轻量加载不取状态: " + sql);
        assertFalse(sql.contains("field_current_version_id"), "轻量加载不取版本指针: " + sql);
        assertFalse(sql.contains("field_enable"), "轻量加载不取启用位: " + sql);
    }

    @Test
    void deleteByIdDeletesByPrimaryKey() {
        String sql = boundSql(SysFormMapper.class.getName() + ".deleteById",
                Map.of("fieldId", "0".repeat(32)));
        assertTrue(sql.contains("DELETE FROM sys_form"), sql);
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
