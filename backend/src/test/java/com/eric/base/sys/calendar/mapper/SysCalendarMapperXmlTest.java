package com.eric.base.sys.calendar.mapper;

import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.calendar.request.SysCalendarQuery;
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
 * 日程 Mapper XML 解析与绑定测试
 *
 * @author Eric
 * @date 2026/11/16
 * @description 覆盖任务 F18-BE-006 的验收：selectCalendarList / selectCalendarCount
 *              在 model.fieldCreatorId 非空时于固定数据边界
 *              （m.field_creator_id = ? OR p.field_person_id = ?）
 *              之内追加 AND m.field_creator_id = ? 收紧条件，
 *              为空时不追加、数据边界不放宽。
 *              不做数据库连接，只校验 SQL 文本口径（先例 SysDatasourceMapperXmlTest）；
 *              getBoundSql 已将 #{} 渲染为 ?，断言以绑定后占位符形态书写。
 */
class SysCalendarMapperXmlTest {

    private static final String CALENDAR_MAPPER = "mappers/sys/calendar/SysCalendarMainMapper.xml";

    private static Configuration configuration;

    @BeforeAll
    static void parseMappers() throws IOException {
        configuration = new Configuration();
        // 解析成功本身即校验 XML 结构合法、类型引用有效
        parse(CALENDAR_MAPPER);
    }

    private static final String BOUNDARY = "WHERE (m.field_creator_id = ? OR p.field_person_id = ?)";

    private static final String TIGHTEN = "AND m.field_creator_id = ?";

    @Test
    void selectCalendarListTightensByCreatorId() {
        // fieldCreatorId 非空：在固定边界之内收紧，不放宽边界
        SysCalendarQuery query = new SysCalendarQuery();
        query.setFieldCreatorId("0".repeat(32));
        String sql = boundSql(SysCalendarMainMapper.class.getName() + ".selectCalendarList", listParams(query));
        assertTrue(sql.contains(BOUNDARY), sql);
        assertTrue(sql.contains(TIGHTEN), sql);
    }

    @Test
    void selectCalendarListWithoutCreatorIdKeepsBoundaryOnly() {
        // fieldCreatorId 为 null：不追加收紧条件，固定边界保持原样
        String sql = boundSql(SysCalendarMainMapper.class.getName() + ".selectCalendarList",
                listParams(new SysCalendarQuery()));
        assertTrue(sql.contains(BOUNDARY), sql);
        assertFalse(sql.contains(TIGHTEN), sql);

        // fieldCreatorId 为空串：同样不追加收紧条件
        SysCalendarQuery empty = new SysCalendarQuery();
        empty.setFieldCreatorId("");
        String blankSql = boundSql(SysCalendarMainMapper.class.getName() + ".selectCalendarList", listParams(empty));
        assertTrue(blankSql.contains(BOUNDARY), blankSql);
        assertFalse(blankSql.contains(TIGHTEN), blankSql);
    }

    @Test
    void selectCalendarCountTightensByCreatorId() {
        SysCalendarQuery query = new SysCalendarQuery();
        query.setFieldCreatorId("0".repeat(32));
        Map<String, Object> params = new HashMap<>();
        params.put("model", query);
        params.put("userId", "u".repeat(32));
        String sql = boundSql(SysCalendarMainMapper.class.getName() + ".selectCalendarCount", params);
        assertTrue(sql.contains("COUNT(DISTINCT m.field_id)"), sql);
        assertTrue(sql.contains(BOUNDARY), sql);
        assertTrue(sql.contains(TIGHTEN), sql);
    }

    @Test
    void selectCalendarCountWithoutCreatorIdKeepsBoundaryOnly() {
        Map<String, Object> params = new HashMap<>();
        params.put("model", new SysCalendarQuery());
        params.put("userId", "u".repeat(32));
        String sql = boundSql(SysCalendarMainMapper.class.getName() + ".selectCalendarCount", params);
        assertTrue(sql.contains("COUNT(DISTINCT m.field_id)"), sql);
        assertTrue(sql.contains(BOUNDARY), sql);
        assertFalse(sql.contains(TIGHTEN), sql);
    }

    private static Map<String, Object> listParams(SysCalendarQuery query) {
        Map<String, Object> params = new HashMap<>();
        params.put("model", query);
        params.put("userId", "u".repeat(32));
        params.put("pages", new Pages());
        params.put("sortField", null);
        params.put("sortOrder", null);
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
