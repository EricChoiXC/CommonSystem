package com.common.system.sys.org.mapper;

import com.common.system.common.base.model.Pages;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 地址本角色页签查询 SQL 测试（ORG1-BE-004）
 *
 * @author Eric
 * @date 2026/11/4
 * @description 覆盖 v1.1 方案 A 的「地址本角色页签复用通用 list（orgTypes=[5]）」约束：
 *              直接解析 Mapper XML 并从动态 SQL 取最终语句，断言 selectList / countList
 *              在 orgTypes=[5] 时按正向 IN 过滤角色镜像行（类型 5）且仅显示启用，
 *              不带 NOT IN (5, 6) 排除，分页生成 LIMIT/OFFSET，keyword 空/非空时 LIKE 条件
 *              按 XML 骨架正确拼与不拼；同时防回归验证 selectTree 对角色永久排除。
 *              不做数据库连接，只校验 SQL 文本口径，避免角色页签能力被后续改动悄悄破坏。
 */
class SysOrgAddressBookRoleQuerySqlTest {

    private static final String ADDRESS_BOOK_MAPPER = "mappers/sys/org/SysOrgAddressBookMapper.xml";
    private static final String ADDRESS_BOOK_STATEMENT_PREFIX = SysOrgAddressBookMapper.class.getName() + ".";

    private static Configuration configuration;

    @BeforeAll
    static void parseMapper() throws IOException {
        configuration = new Configuration();
        // 解析成功本身即校验 XML 结构与 DTD 合法
        parse(ADDRESS_BOOK_MAPPER);
    }

    @Test
    void selectListWithRoleTypeFiltersMirrorRowsWithoutExclusion() {
        // keyword 为空：仅类型过滤，不拼接 LIKE 条件
        String sql = boundSql(ADDRESS_BOOK_STATEMENT_PREFIX + "selectList", baseListParams(null));

        assertTrue(sql.contains("field_org_type IN"), "orgTypes=[5] 需生成正向 IN 过滤: " + sql);
        assertFalse(sql.contains("field_org_type NOT IN (5, 6)"), "通用列表不得排除角色/群组: " + sql);
        assertTrue(sql.contains("field_enable = 1"), "角色页签需仅显示启用角色: " + sql);
        assertFalse(sql.contains("LIKE"), "keyword 为空时不应出现 LIKE 条件: " + sql);

        // keyword 非空：LIKE 名称/编码骨架生效，支撑「返回角色名称/编码」验收标准
        String keywordSql = boundSql(ADDRESS_BOOK_STATEMENT_PREFIX + "selectList", baseListParams("项目经理"));

        assertTrue(keywordSql.contains("field_name LIKE"), "关键字需匹配角色名称: " + keywordSql);
        assertTrue(keywordSql.contains("field_code LIKE"), "关键字需匹配角色编码: " + keywordSql);
        assertTrue(keywordSql.contains("field_org_type IN"), "关键字搜索仍受 orgTypes=[5] 限定: " + keywordSql);
    }

    @Test
    void selectListWithRoleTypeAppliesPagination() {
        Pages pages = new Pages();
        pages.setNeedPage(true);
        pages.setPageNo(1);
        pages.setPageSize(20);
        Map<String, Object> params = baseListParams(null);
        params.put("pages", pages);

        String sql = boundSql(ADDRESS_BOOK_STATEMENT_PREFIX + "selectList", params);

        // needPage=true 时按 pageNo/pageSize 生成物理分页，metadata.total 由 countList 同口径提供
        assertTrue(sql.contains("LIMIT") && sql.contains("OFFSET"), "分页需生成 LIMIT/OFFSET: " + sql);
    }

    @Test
    void countListWithRoleTypeKeepsSameFilterScope() {
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", null);
        params.put("orgTypes", List.of(5));
        params.put("limitRange", null);

        String sql = boundSql(ADDRESS_BOOK_STATEMENT_PREFIX + "countList", params);

        assertTrue(sql.contains("field_org_type IN"), "总数口径需与列表一致按 IN 过滤: " + sql);
        assertFalse(sql.contains("field_org_type NOT IN (5, 6)"), "总数不得排除角色/群组: " + sql);
        assertTrue(sql.contains("field_enable = 1"), "总数仅统计启用角色: " + sql);
    }

    @Test
    void selectTreeStillExcludesRoleEvenWithRoleTypeParam() {
        Map<String, Object> params = new HashMap<>();
        // 即使调用方显式传入 orgTypes=[5]，树查询也必须永久排除角色
        params.put("orgTypes", List.of(5));
        params.put("limitRange", null);
        params.put("parentId", null);

        String sql = boundSql(ADDRESS_BOOK_STATEMENT_PREFIX + "selectTree", params);

        assertTrue(sql.contains("field_org_type NOT IN (5, 6)"), "地址本树必须永久排除角色/群组: " + sql);
        // 排除条件与调用方类型条件是「与」关系，不能互相覆盖
        assertTrue(sql.contains("field_org_type IN"), "调用方 orgTypes 条件仍需生效: " + sql);
    }

    /**
     * selectList 参数骨架：orgTypes 固定 [5]，limitRange 空、不分页，keyword 可变。
     */
    private static Map<String, Object> baseListParams(String keyword) {
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", keyword);
        params.put("orgTypes", List.of(5));
        params.put("limitRange", null);
        params.put("pages", null);
        return params;
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
