package com.eric.base.common.source.service;

import com.eric.base.common.source.ddl.SourceDdlGenerator;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import com.eric.base.common.source.model.ColumnDef;
import com.eric.base.common.source.model.IndexDef;
import com.eric.base.common.source.model.TableDef;
import com.eric.base.common.source.scanner.SourceTableScanner;
import com.eric.base.sys.datasource.model.SysDatasource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 试点表标注扫描测试（F19-BE-005 产出 D：sys_datasource 试点标注，不连 MySQL）
 *
 * @author Eric
 * @date 2026/11/16
 * @description 对真实实体 {@link SysDatasource} 直接断言 SourceTableScanner.parse 归一产物
 *              与 V1_01_046__sys_datasource_schema.sql 逐列一致（列序/类型/长度/可空/
 *              默认值/COMMENT/两索引），并经 SourceDdlGenerator 全量建表文本与 V1_01_046
 *              原文空白归一化后整段等价（口径对齐 SourceDdlGeneratorTest 的
 *              SysDatasourceEntity 测试桩，该桩即按真实标注抄录）；附 D5 字段重声明遮蔽的
 *              Lombok 读写器覆盖验证。这是「dev 库 VALIDATE 零 diff」验收的静态前置。
 */
class CommonSourcePilotScanTest {

    /** V1_01_046 sys_datasource 建表语句原文（比对时空白归一化并去掉结尾分号） */
    private static final String V1_01_046_SYS_DATASOURCE_DDL = """
            CREATE TABLE IF NOT EXISTS sys_datasource (
                field_id         VARCHAR(32)  NOT NULL COMMENT '主键ID',
                field_code       VARCHAR(100) NOT NULL COMMENT '数据源编码，全局唯一（与默认数据源共用命名空间），Service 层查重',
                field_name       VARCHAR(200) NOT NULL COMMENT '数据源名称',
                field_target     VARCHAR(100) NOT NULL COMMENT '目标查询能力编码（Provider code，如 calendar.list），保存后锁定',
                field_conditions JSON         DEFAULT NULL COMMENT '固定条件 JSON：[{key,op,value}]，value 支持 ${CURRENT_USER_ID} 等运行时变量',
                field_fields     JSON         DEFAULT NULL COMMENT '返回字段白名单 JSON：字符串数组，NULL=目标能力默认全字段',
                field_remark     VARCHAR(500) DEFAULT NULL COMMENT '备注',
                field_creator_id VARCHAR(32)  DEFAULT NULL COMMENT '创建者ID',
                field_create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                field_updater_id VARCHAR(32)  DEFAULT NULL COMMENT '修改者ID',
                field_update_time TIMESTAMP   NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                field_enable     BIT(1)       NOT NULL DEFAULT b'1' COMMENT '是否启用',
                PRIMARY KEY (field_id),
                UNIQUE KEY uk_datasource_code (field_code),
                KEY idx_datasource_target (field_target)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源表（自定义数据源配置）';
            """;

    /** 空白归一化：连续空白折叠为单空格并去掉结尾分号（脚本带分号、生成语句不带） */
    private static String normalize(String ddl) {
        String trimmed = ddl.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.replaceAll("\\s+", " ");
    }

    /** 按列名取列定义（断言辅助） */
    private static ColumnDef column(TableDef table, String name) {
        return table.getColumns().stream().filter(def -> def.getName().equals(name)).findFirst().orElseThrow();
    }

    /* ==================== 逐列一致 ==================== */

    @Test
    void pilotAnnotationMatchesV1_01_046ColumnByColumn() {
        TableDef table = SourceTableScanner.parse(SysDatasource.class);

        assertEquals("sys_datasource", table.getName());
        assertEquals("数据源表（自定义数据源配置）", table.getComment());
        assertEquals("datasource", table.getDataSourceName(), "@DataSource 逻辑数据源名透传");

        // 列序 = 内建主键（最前）+ 业务列（声明序）+ 内建审计五列（最后），与 V1_01_046 一致
        assertEquals(List.of("field_id", "field_code", "field_name", "field_target", "field_conditions",
                        "field_fields", "field_remark", "field_creator_id", "field_create_time",
                        "field_updater_id", "field_update_time", "field_enable"),
                table.getColumns().stream().map(ColumnDef::getName).toList(), "列清单与列序与 V1_01_046 一致");
        assertEquals(12, table.getColumns().size());

        // 业务列逐列断言（类型/长度/可空/默认值/COMMENT，文案对齐 V1_01_046）
        ColumnDef code = column(table, "field_code");
        assertEquals("数据源编码，全局唯一（与默认数据源共用命名空间），Service 层查重", code.getComment());
        assertEquals(ColumnType.STRING, code.getType());
        assertEquals(100, code.getLength());
        assertFalse(code.isNullable());
        assertNull(code.getDefaultValue());
        assertEquals(DefaultFunction.NONE, code.getDefaultFunction());
        assertFalse(code.isPrimaryKey(), "field_code 重声明后为业务列（遮蔽 MainModel 继承列，D5）");

        ColumnDef name = column(table, "field_name");
        assertEquals("数据源名称", name.getComment());
        assertEquals(ColumnType.STRING, name.getType());
        assertEquals(200, name.getLength());
        assertFalse(name.isNullable());

        ColumnDef target = column(table, "field_target");
        assertEquals("目标查询能力编码（Provider code，如 calendar.list），保存后锁定", target.getComment());
        assertEquals(ColumnType.STRING, target.getType());
        assertEquals(100, target.getLength());
        assertFalse(target.isNullable());

        ColumnDef conditions = column(table, "field_conditions");
        assertEquals("固定条件 JSON：[{key,op,value}]，value 支持 ${CURRENT_USER_ID} 等运行时变量", conditions.getComment());
        assertEquals(ColumnType.JSON, conditions.getType());
        assertTrue(conditions.isNullable(), "JSON 列 nullable=true（DEFAULT NULL）");
        assertNull(conditions.getDefaultValue(), "JSON 列禁默认值");

        ColumnDef fields = column(table, "field_fields");
        assertEquals("返回字段白名单 JSON：字符串数组，NULL=目标能力默认全字段", fields.getComment());
        assertEquals(ColumnType.JSON, fields.getType());
        assertTrue(fields.isNullable());

        ColumnDef remark = column(table, "field_remark");
        assertEquals("备注", remark.getComment());
        assertEquals(ColumnType.STRING, remark.getType());
        assertEquals(500, remark.getLength());
        assertTrue(remark.isNullable());

        // 公共列模板内建（主键 + 审计五列，§3.3 评审修正口径）
        ColumnDef fieldId = column(table, "field_id");
        assertTrue(fieldId.isPrimaryKey());
        assertEquals("主键ID", fieldId.getComment());
        assertEquals(32, fieldId.getLength());
        ColumnDef createTime = column(table, "field_create_time");
        assertEquals(DefaultFunction.CURRENT_TIMESTAMP, createTime.getDefaultFunction());
        assertFalse(createTime.isNullable());
        ColumnDef updateTime = column(table, "field_update_time");
        assertEquals(DefaultFunction.ON_UPDATE_CURRENT_TIMESTAMP, updateTime.getDefaultFunction());
        assertTrue(updateTime.isNullable());
        ColumnDef enable = column(table, "field_enable");
        assertEquals("b'1'", enable.getDefaultValue());
        assertFalse(enable.isNullable());

        // 两索引：唯一索引 uk_datasource_code + 普通索引 idx_datasource_target
        assertEquals(2, table.getIndexes().size());
        IndexDef uk = table.getIndexes().get(0);
        assertEquals("uk_datasource_code", uk.getName());
        assertEquals(List.of("field_code"), uk.getColumns());
        assertTrue(uk.isUnique());
        IndexDef idx = table.getIndexes().get(1);
        assertEquals("idx_datasource_target", idx.getName());
        assertEquals(List.of("field_target"), idx.getColumns());
        assertFalse(idx.isUnique());
    }

    /* ==================== 建表文本等价（强断言） ==================== */

    @Test
    void pilotCreateTableDdlMatchesV1_01_046Baseline() {
        TableDef table = SourceTableScanner.parse(SysDatasource.class);
        String normalized = normalize(SourceDdlGenerator.createTable(table));
        assertEquals(normalize(V1_01_046_SYS_DATASOURCE_DDL), normalized,
                "试点实体注解经扫描归一 + DDL 生成后与 V1_01_046 建表语句整段逐字等价"
                        + "（列序/类型/长度/可空/默认值/COMMENT/索引/ENGINE/CHARSET）");
    }

    /* ==================== D5 字段遮蔽与 Lombok 覆盖 ==================== */

    @Test
    void pilotRedeclaredFieldsShadowInheritedColumnsWithLombokOverride() throws Exception {
        // 重声明遮蔽：fieldCode / fieldName 为子类自有字段，Lombok 生成同名读写器覆盖父类实现
        assertEquals(SysDatasource.class, SysDatasource.class.getMethod("setFieldCode", String.class)
                .getDeclaringClass(), "Lombok 为子类重声明字段生成同名读写器");
        assertEquals(SysDatasource.class, SysDatasource.class.getMethod("setFieldName", String.class)
                .getDeclaringClass());
        SysDatasource entity = new SysDatasource();
        entity.setFieldCode("ds-001");
        entity.setFieldName("测试数据源");
        assertEquals("ds-001", entity.getFieldCode(), "父类引用经子类读写器访问子类字段");
        assertEquals("测试数据源", entity.getFieldName());

        // 未重声明的 MainModel 继承字段（此处无）与不入库展示字段不进列清单
        TableDef table = SourceTableScanner.parse(SysDatasource.class);
        assertFalse(table.getColumns().stream().anyMatch(def -> def.getName().equals("field_target_name")),
                "fieldTargetName 为不入库的展示字段，不标注不入列");
    }
}
