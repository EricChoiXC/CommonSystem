package com.eric.base.sys.org.mapper;

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
 * 组织架构树/列表排除条件测试
 *
 * @author Eric
 * @date 2026/11/4
 * @description 覆盖 v1.1 的「角色与群组不参与组织架构树」约束（风险 R02）：
 *              直接解析 Mapper XML 并从动态 SQL 取最终语句，断言地址本树查询与组织管理页列表
 *              均带 {@code field_org_type NOT IN (5, 6)}；同时验证两个 XML 可被 MyBatis 正常解析。
 *              不做数据库连接，只校验 SQL 文本口径，避免排除条件被后续改动悄悄删除。
 */
class SysOrgElementExclusionSqlTest {

    private static final String ELEMENT_MAPPER = "mappers/sys/org/SysOrgElementMapper.xml";
    private static final String ADDRESS_BOOK_MAPPER = "mappers/sys/org/SysOrgAddressBookMapper.xml";

    private static Configuration configuration;

    @BeforeAll
    static void parseMappers() throws IOException {
        configuration = new Configuration();
        // 依次解析两个 Mapper：解析成功本身即校验 XML 结构与 DTD 合法
        parse(ELEMENT_MAPPER);
        parse(ADDRESS_BOOK_MAPPER);
    }

    @Test
    void addressBookTreeAlwaysExcludesRoleAndGroup() {
        Map<String, Object> params = new HashMap<>();
        // 即使调用方显式传入含 5/6 的 orgTypes，树查询也必须排除
        params.put("orgTypes", java.util.List.of(5, 6));
        params.put("limitRange", null);
        params.put("parentId", null);

        String sql = boundSql(SysOrgAddressBookMapper.class.getName() + ".selectTree", params);

        assertTrue(sql.contains("field_org_type NOT IN (5, 6)"), "地址本树必须排除角色/群组: " + sql);
        // 排除条件与调用方类型条件是「与」关系，不能互相覆盖
        assertTrue(sql.contains("field_org_type IN"), "orgTypes 条件仍需生效: " + sql);
    }

    @Test
    void elementVoListExcludesRoleAndGroup() {
        Map<String, Object> params = new HashMap<>();
        params.put("query", new com.eric.base.sys.org.request.SysOrgElementQuery());
        params.put("pages", null);
        params.put("sortField", "createTime");
        params.put("sortOrder", "desc");

        String sql = boundSql(SysOrgElementMapper.class.getName() + ".selectVoList", params);
        assertTrue(sql.contains("field_org_type NOT IN (5, 6)"), "组织管理页列表必须排除角色/群组: " + sql);

        String countSql = boundSql(SysOrgElementMapper.class.getName() + ".countVoList", params);
        assertTrue(countSql.contains("field_org_type NOT IN (5, 6)"), "列表总数口径必须与列表一致: " + countSql);
    }

    @Test
    void elementDetailAndChildrenQueriesDoNotExclude() {
        // 详情/下级计数不参与排除：删除校验与父级计算按 id 精确定位，不受类型影响
        String detailSql = boundSql(SysOrgElementMapper.class.getName() + ".selectVoById",
                Map.of("fieldId", "0".repeat(32)));
        assertFalse(detailSql.contains("field_org_type NOT IN (5, 6)"), "详情按 id 查询无需类型排除: " + detailSql);
    }

    @Test
    void addressBookKeywordListStaysGenericForRoleTab() {
        // 角色页签复用通用 list（orgTypes=[5]），因此 list 不做 5/6 排除
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", null);
        params.put("orgTypes", java.util.List.of(5));
        params.put("limitRange", null);
        params.put("pages", null);

        String sql = boundSql(SysOrgAddressBookMapper.class.getName() + ".selectList", params);

        assertFalse(sql.contains("field_org_type NOT IN (5, 6)"), "地址本通用列表需支持角色页签: " + sql);
        assertTrue(sql.contains("field_org_type IN"), "按 orgTypes 过滤: " + sql);
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
