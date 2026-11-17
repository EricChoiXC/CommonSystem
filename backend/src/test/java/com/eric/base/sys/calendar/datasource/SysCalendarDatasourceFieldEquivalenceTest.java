package com.eric.base.sys.calendar.datasource;

import com.eric.base.sys.calendar.request.SysCalendarQuery;
import com.eric.base.sys.calendar.response.SysCalendarVo;
import com.eric.base.sys.datasource.spi.SysDatasourceFieldDo;
import com.eric.base.sys.datasource.spi.SysDatasourceFieldScanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 日程数据源字段等价性基线测试
 *
 * @author Eric
 * @date 2026/11/17
 * @description F19-BE-009 A8 注解迁移（数据源字段注解迁至
 *              {@code common.source.annotation.DataSourceField}）等价性断言：
 *              以迁移前扫描结果为基线，对真实 SysCalendarQuery（conditionFields，注解 order 1-6）
 *              与 SysCalendarVo（resultFields，注解 order 1-8 + fieldId 内建补齐固定最前
 *              order=Integer.MIN_VALUE）逐字段写死 key/label/type/ops/literalAllowed/sortable
 *              期望值；SysDatasourceFieldDo 不承载 order 属性，输出序列按注解 order 升序即为
 *              order 断言。迁移后同断言保持全绿即等价，不许为通过而修改期望值。
 */
class SysCalendarDatasourceFieldEquivalenceTest {

    @Test
    void conditionFieldsMatchMigrationBaseline() {
        List<SysDatasourceFieldDo> fields = SysDatasourceFieldScanner.conditionFields(SysCalendarQuery.class);

        assertEquals(List.of("fieldName", "fieldType", "fieldAccept",
                        "fieldStartTimeStart", "fieldStartTimeEnd", "fieldCreatorId"),
                fields.stream().map(SysDatasourceFieldDo::getKey).toList(),
                "条件字段 6 项按注解 order 1-6 升序（year/month 不标注不进白名单）");

        assertField(fields.get(0), "fieldName", "标题", "STRING", List.of("LIKE"), true, false);
        assertField(fields.get(1), "fieldType", "类型", "STRING", List.of("EQ"), true, false);
        assertField(fields.get(2), "fieldAccept", "接收状态", "STRING", List.of("EQ"), true, false);
        assertField(fields.get(3), "fieldStartTimeStart", "开始时间-起", "DATETIME", List.of("GE"), true, false);
        assertField(fields.get(4), "fieldStartTimeEnd", "开始时间-止", "DATETIME", List.of("LE"), true, false);
        assertField(fields.get(5), "fieldCreatorId", "创建人", "STRING", List.of("EQ"), false, false);
    }

    @Test
    void resultFieldsMatchMigrationBaseline() {
        List<SysDatasourceFieldDo> fields = SysDatasourceFieldScanner.resultFields(SysCalendarVo.class);

        assertEquals(List.of("fieldId", "fieldCreatorId", "fieldCreateTime", "fieldUpdateTime",
                        "fieldStartTime", "fieldEndTime", "fieldName", "fieldContent", "fieldType"),
                fields.stream().map(SysDatasourceFieldDo::getKey).toList(),
                "返回字段母集 9 项按注解 order 升序（fieldId 内建补齐固定最前，persons 不标注不进母集）");

        assertField(fields.get(0), "fieldId", "ID", "STRING", null, true, false);
        assertField(fields.get(1), "fieldCreatorId", "创建人id", "STRING", null, true, false);
        assertField(fields.get(2), "fieldCreateTime", "创建时间", "DATETIME", null, true, true);
        assertField(fields.get(3), "fieldUpdateTime", "更新时间", "DATETIME", null, true, false);
        assertField(fields.get(4), "fieldStartTime", "开始时间", "DATETIME", null, true, true);
        assertField(fields.get(5), "fieldEndTime", "结束时间", "DATETIME", null, true, false);
        assertField(fields.get(6), "fieldName", "标题", "STRING", null, true, false);
        assertField(fields.get(7), "fieldContent", "内容", "STRING", null, true, false);
        assertField(fields.get(8), "fieldType", "类型", "STRING", null, true, false);
    }

    /**
     * 逐字段基线断言：key/label/type/ops/literalAllowed/sortable 六属性与迁移前注解逐一对应
     */
    private void assertField(SysDatasourceFieldDo field, String key, String label, String type,
                             List<String> ops, boolean literalAllowed, boolean sortable) {
        assertEquals(key, field.getKey(), "key 取 Java 字段名");
        assertEquals(label, field.getLabel(), "label 与迁移前注解一致");
        assertEquals(type, field.getType(), "type 与迁移前基线一致");
        if (null == ops) {
            assertNull(field.getOps(), "返回字段定义不承载 ops");
        } else {
            assertEquals(ops, field.getOps(), "ops 与迁移前注解一致");
        }
        assertEquals(literalAllowed, field.isLiteralAllowed(), "literalAllowed 与迁移前注解一致");
        assertEquals(sortable, field.isSortable(), "sortable 与迁移前注解一致");
    }
}
