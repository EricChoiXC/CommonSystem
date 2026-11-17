package com.eric.base.common.source.model;

import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import com.eric.base.common.source.enums.IndexType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表定义模型测试
 *
 * @author Eric
 * @date 2026/11/16
 * @description 覆盖任务 F19-BE-002 产出物 A：TableDef / ColumnDef / IndexDef 不可变
 *              POJO 语义——入参拷贝持有（外部修改不穿透）、集合不可变（add 抛
 *              UnsupportedOperationException）、@DataSource 未标注时 dataSourceName 为 null。
 *              补充 F19-BE-011：primaryKeyColumns 声明主键列集（缺省空集）与有效主键
 *              派生口径——声明非空用声明列集（声明序），空声明派生 = 公共列模板内建 field_id。
 */
class TableDefTest {

    @Test
    void indexDefCopiesColumnsAndImmutable() {
        List<String> columns = new ArrayList<>(List.of("field_code"));
        IndexDef index = new IndexDef("uk_test_code", columns, true, IndexType.BTREE);

        columns.add("field_name");
        assertEquals(List.of("field_code"), index.getColumns(), "外部修改入参不影响模型（拷贝持有）");
        assertThrows(UnsupportedOperationException.class, () -> index.getColumns().add("field_name"),
                "索引列清单不可变");
        assertTrue(index.isUnique());
        assertEquals(IndexType.BTREE, index.getType());
        assertEquals("uk_test_code", index.getName());
    }

    @Test
    void tableDefCopiesListsAndImmutable() {
        List<ColumnDef> columns = new ArrayList<>();
        columns.add(new ColumnDef("field_id", "主键ID", ColumnType.STRING, 32, 0,
                false, null, DefaultFunction.NONE, true));
        List<IndexDef> indexes = new ArrayList<>();
        indexes.add(new IndexDef("uk_test_code", List.of("field_code"), true, IndexType.BTREE));

        TableDef table = new TableDef("mock_test_table", "模型测试表", null, columns, indexes, List.of());

        columns.clear();
        assertEquals(1, table.getColumns().size(), "外部修改入参不影响模型（拷贝持有）");
        indexes.clear();
        assertEquals(1, table.getIndexes().size());

        assertEquals("mock_test_table", table.getName());
        assertEquals("模型测试表", table.getComment());
        assertNull(table.getDataSourceName(), "@DataSource 未标注时逻辑数据源名为 null");

        ColumnDef fieldId = table.getColumns().get(0);
        assertTrue(fieldId.isPrimaryKey());
        assertEquals(ColumnType.STRING, fieldId.getType());
        assertEquals(32, fieldId.getLength());
        assertFalse(fieldId.isNullable());
        assertNull(fieldId.getDefaultValue());
        assertEquals(DefaultFunction.NONE, fieldId.getDefaultFunction());
        assertTrue(table.getIndexes().get(0).isUnique());

        ColumnDef extraColumn = new ColumnDef("field_extra", "多余列", ColumnType.STRING, 1, 0,
                false, null, DefaultFunction.NONE, false);
        IndexDef extraIndex = new IndexDef("idx_extra", List.of("field_id"), false, IndexType.BTREE);
        assertThrows(UnsupportedOperationException.class, () -> table.getColumns().add(extraColumn),
                "列清单不可变");
        assertThrows(UnsupportedOperationException.class, () -> table.getIndexes().add(extraIndex),
                "索引清单不可变");
    }

    // ---------------------------------------------------------------- 有效主键派生（F19-BE-011）

    @Test
    void effectivePrimaryKeyDefaultsToBuiltinFieldId() {
        TableDef table = new TableDef("mock_test_table", "模型测试表", null,
                List.of(new ColumnDef("field_id", "主键ID", ColumnType.STRING, 32, 0,
                        false, null, DefaultFunction.NONE, true)),
                List.of(), List.of());

        assertTrue(table.getPrimaryKeyColumns().isEmpty(), "缺省 primaryKeyColumns 为空集（保留声明原样）");
        assertEquals(List.of("field_id"), table.getEffectivePrimaryKeyColumns(),
                "空声明派生有效主键 = 公共列模板内建 field_id（回归断言：既有 35 张标注表语义零变化）");
    }

    @Test
    void effectivePrimaryKeyUsesDeclaredColumnsInOrder() {
        List<String> declared = new ArrayList<>(List.of("field_user_id", "field_post_id"));
        TableDef table = new TableDef("mock_test_table", "模型测试表", null, List.of(), List.of(), declared);

        declared.add("field_extra");
        assertEquals(List.of("field_user_id", "field_post_id"), table.getPrimaryKeyColumns(),
                "外部修改入参不影响模型（拷贝持有，声明原样）");
        assertThrows(UnsupportedOperationException.class, () -> table.getPrimaryKeyColumns().add("field_extra"),
                "声明主键列集不可变");
        assertEquals(List.of("field_user_id", "field_post_id"), table.getEffectivePrimaryKeyColumns(),
                "声明非空时有效主键 = 声明列集（数组顺序即 PRIMARY KEY 列序）");
    }
}
