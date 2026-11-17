package com.eric.base.common.source.ddl;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.base.model.MainModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.DataSource;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import com.eric.base.common.source.enums.IndexType;
import com.eric.base.common.source.model.ColumnDef;
import com.eric.base.common.source.model.IndexDef;
import com.eric.base.common.source.model.TableDef;
import com.eric.base.common.source.scanner.SourceTableScanner;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表结构 DDL 生成器测试
 *
 * @author Eric
 * @date 2026/11/16
 * @description 覆盖任务 F19-BE-003 验收标准：以 V1_01_046 为基线断言 sys_datasource
 *              全量建表 DDL 文本等价（TableDef 经 SourceTableScanner 扫描桩类归一，
 *              整段比对空白归一化 + 语义片段逐一断言）；ADD COLUMN / MODIFY COLUMN /
 *              ALTER COLUMN SET DEFAULT（字面量 + 函数式）/ ALTER COMMENT / ADD INDEX
 *              各语句文本断言；标识符白名单与原样输出字面量白名单 fail-fast（防御纵深）；
 *              TEXT/JSON 禁默认值、ON UPDATE 非 TIMESTAMP、无默认值 SET DEFAULT 等
 *              违例 fail-fast；COMMENT 与字符串字面量单引号转义。补充 F19-BE-011：
 *              primaryKeyColumns 声明主键列集——组合主键建表 DDL 与 V1_01_001
 *              sys_org_post_user 脚本段整段文本等价、单列非 field_id 主键、声明列集
 *              子集/重复/白名单防御 fail-fast。
 */
class SourceDdlGeneratorTest {

    // ---------------------------------------------------------------- 扫描桩类（等价基线）

    /**
     * 试点表 sys_datasource 扫描桩：注解定义与 V1_01_046 建表脚本逐列一致
     * （设计方案 §3.2 示例），公共列模板（field_id 主键 + 审计五列）由 Scanner 内建
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    @TableSource(name = "sys_datasource", comment = "数据源表（自定义数据源配置）",
            indexes = {
                    @TableIndex(name = "uk_datasource_code", columns = "field_code", unique = true),
                    @TableIndex(name = "idx_datasource_target", columns = "field_target")})
    @DataSource(name = "datasource")
    static class SysDatasourceEntity extends MainModel {

        @ColumnField(name = "field_code", comment = "数据源编码，全局唯一（与默认数据源共用命名空间），Service 层查重",
                type = ColumnType.STRING, length = 100)
        private String fieldCode;

        @ColumnField(name = "field_name", comment = "数据源名称", type = ColumnType.STRING, length = 200)
        private String fieldName;

        @ColumnField(name = "field_target", comment = "目标查询能力编码（Provider code，如 calendar.list），保存后锁定",
                type = ColumnType.STRING, length = 100)
        private String fieldTarget;

        @ColumnField(name = "field_conditions", comment = "固定条件 JSON：[{key,op,value}]，value 支持 ${CURRENT_USER_ID} 等运行时变量",
                type = ColumnType.JSON, nullable = true)
        private Object fieldConditions;

        @ColumnField(name = "field_fields", comment = "返回字段白名单 JSON：字符串数组，NULL=目标能力默认全字段",
                type = ColumnType.JSON, nullable = true)
        private Object fieldFields;

        @ColumnField(name = "field_remark", comment = "备注", type = ColumnType.STRING, length = 500, nullable = true)
        private String fieldRemark;
    }

    /** BaseModel 模板桩：仅内建主键（流水类口径，无审计列） */
    @TableSource(name = "mock_ddl_base", comment = "DDL 生成测试流水表（BaseModel 模板）")
    static class MockBaseEntity extends BaseModel {

        @ColumnField(name = "field_remark", comment = "备注", type = ColumnType.TEXT, nullable = true)
        private String fieldRemark;
    }

    /**
     * 组合主键桩（F19-DOC-005）：注解定义与 V1_01_001 sys_org_post_user 建表脚本逐列一致
     * （BaseModel + primaryKeyColumns 复合主键声明 + 二级索引，主键列以业务列标注）
     */
    @TableSource(name = "sys_org_post_user", comment = "岗位-人员关联表",
            primaryKeyColumns = {"field_user_id", "field_post_id"},
            indexes = @TableIndex(name = "idx_post_user_post", columns = "field_post_id"))
    static class SysOrgPostUserEntity extends BaseModel {

        @ColumnField(name = "field_user_id", comment = "人员ID", type = ColumnType.STRING, length = 32)
        private String fieldUserId;

        @ColumnField(name = "field_post_id", comment = "岗位ID", type = ColumnType.STRING, length = 32)
        private String fieldPostId;
    }

    /** 单列非 field_id 主键桩（F19-DOC-005）：BaseModel + 单列主键声明 */
    @TableSource(name = "mock_ddl_single_pk", comment = "DDL 生成测试单列主键表（BaseModel + 单列主键声明）",
            primaryKeyColumns = {"field_user_id"})
    static class SinglePrimaryKeyEntity extends BaseModel {

        @ColumnField(name = "field_user_id", comment = "人员ID", type = ColumnType.STRING, length = 32)
        private String fieldUserId;

        @ColumnField(name = "field_remark", comment = "备注", type = ColumnType.STRING, length = 200, nullable = true)
        private String fieldRemark;
    }

    // ---------------------------------------------------------------- 基线常量与工具

    /** V1_01_046 sys_datasource 建表语句原文（验收基线；比对时空白归一化并去掉结尾分号） */
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

    /** 业务列定义构造（primaryKey 恒为 false） */
    private static ColumnDef column(String name, String comment, ColumnType type, int length, int decimalLength,
                                    boolean nullable, String defaultValue, DefaultFunction defaultFunction) {
        return new ColumnDef(name, comment, type, length, decimalLength, nullable, defaultValue, defaultFunction, false);
    }

    /** 公共列模板内建主键列（与 Scanner 内建口径一致） */
    private static ColumnDef primaryKey() {
        return new ColumnDef("field_id", "主键ID", ColumnType.STRING, 32, 0, false, null, DefaultFunction.NONE, true);
    }

    /** 最小表定义构造（仅主键，供 ALTER 语句文本断言） */
    private static TableDef table(String name) {
        return new TableDef(name, "DDL 生成测试表", null, List.of(primaryKey()), List.of(), List.of());
    }

    // ---------------------------------------------------------------- CREATE TABLE 等价基线

    @Test
    void createTableMatchesV1_01_046Baseline() {
        TableDef table = SourceTableScanner.parse(SysDatasourceEntity.class);
        String normalized = normalize(SourceDdlGenerator.createTable(table));

        // 核心验收：空白归一化后与 V1_01_046 建表语句整段逐字等价
        assertEquals(normalize(V1_01_046_SYS_DATASOURCE_DDL), normalized, "与 V1_01_046 建表语句整段等价");

        // 语义片段逐一断言（列序/类型/长度/可空/默认值/COMMENT/索引），同时验证出现顺序
        List<String> fragments = List.of(
                "field_id VARCHAR(32) NOT NULL COMMENT '主键ID'",
                "field_code VARCHAR(100) NOT NULL COMMENT '数据源编码，全局唯一（与默认数据源共用命名空间），Service 层查重'",
                "field_name VARCHAR(200) NOT NULL COMMENT '数据源名称'",
                "field_target VARCHAR(100) NOT NULL COMMENT '目标查询能力编码（Provider code，如 calendar.list），保存后锁定'",
                "field_conditions JSON DEFAULT NULL COMMENT '固定条件 JSON：[{key,op,value}]，value 支持 ${CURRENT_USER_ID} 等运行时变量'",
                "field_fields JSON DEFAULT NULL COMMENT '返回字段白名单 JSON：字符串数组，NULL=目标能力默认全字段'",
                "field_remark VARCHAR(500) DEFAULT NULL COMMENT '备注'",
                "field_creator_id VARCHAR(32) DEFAULT NULL COMMENT '创建者ID'",
                "field_create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'",
                "field_updater_id VARCHAR(32) DEFAULT NULL COMMENT '修改者ID'",
                "field_update_time TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间'",
                "field_enable BIT(1) NOT NULL DEFAULT b'1' COMMENT '是否启用'",
                "PRIMARY KEY (field_id)",
                "UNIQUE KEY uk_datasource_code (field_code)",
                "KEY idx_datasource_target (field_target)");
        int position = -1;
        for (String fragment : fragments) {
            int found = normalized.indexOf(fragment);
            assertTrue(found >= 0, "缺失语义片段: " + fragment);
            assertTrue(found > position, "列序/索引序错位: " + fragment);
            position = found;
        }
        assertTrue(normalized.startsWith("CREATE TABLE IF NOT EXISTS sys_datasource ("));
        assertTrue(normalized.endsWith(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源表（自定义数据源配置）'"),
                "ENGINE/CHARSET/表 COMMENT 对齐既有脚本风格");
        assertFalse(normalized.contains("DROP"), "引擎永不生成 DROP 类语句");
    }

    @Test
    void createTableForBaseModelOmitsAuditColumns() {
        TableDef table = SourceTableScanner.parse(MockBaseEntity.class);
        String normalized = normalize(SourceDdlGenerator.createTable(table));

        assertTrue(normalized.contains("field_id VARCHAR(32) NOT NULL COMMENT '主键ID',"));
        assertTrue(normalized.contains("field_remark TEXT DEFAULT NULL COMMENT '备注',"));
        assertFalse(normalized.contains("field_creator_id"), "BaseModel 模板无审计列");
        assertFalse(normalized.contains("field_enable"), "BaseModel 模板无启用列");
        assertTrue(normalized.endsWith(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DDL 生成测试流水表（BaseModel 模板）'"));
    }

    // ---------------------------------------------------------------- ALTER 语句文本断言

    @Test
    void addColumnAppendsColumnDefinitionWithoutAfter() {
        assertEquals("ALTER TABLE mock_ddl_table ADD COLUMN field_remark VARCHAR(500) DEFAULT NULL COMMENT '备注'",
                SourceDdlGenerator.addColumn(table("mock_ddl_table"),
                        column("field_remark", "备注", ColumnType.STRING, 500, 0, true, null, DefaultFunction.NONE)));
    }

    @Test
    void modifyColumnRendersFullDefinition() {
        TableDef table = table("mock_ddl_table");
        assertEquals("ALTER TABLE mock_ddl_table MODIFY COLUMN field_name VARCHAR(300) NOT NULL COMMENT '名称'",
                SourceDdlGenerator.modifyColumn(table,
                        column("field_name", "名称", ColumnType.STRING, 300, 0, false, null, DefaultFunction.NONE)),
                "VARCHAR 扩长走 MODIFY COLUMN 全量重写");
        assertEquals("ALTER TABLE mock_ddl_table MODIFY COLUMN field_amount DECIMAL(15,2) NOT NULL COMMENT '金额'",
                SourceDdlGenerator.modifyColumn(table,
                        column("field_amount", "金额", ColumnType.DECIMAL, 15, 2, false, null, DefaultFunction.NONE)),
                "DECIMAL 精度提升走 MODIFY COLUMN 全量重写");
    }

    @Test
    void alterColumnDefaultRendersLiteralsPerType() {
        TableDef table = table("mock_ddl_table");
        assertEquals("ALTER TABLE mock_ddl_table ALTER COLUMN field_status SET DEFAULT 'Y'",
                SourceDdlGenerator.alterColumnDefault(table,
                        column("field_status", "状态", ColumnType.STRING, 20, 0, false, "Y", DefaultFunction.NONE)),
                "字符串字面量加单引号");
        assertEquals("ALTER TABLE mock_ddl_table ALTER COLUMN field_count SET DEFAULT 0",
                SourceDdlGenerator.alterColumnDefault(table,
                        column("field_count", "数量", ColumnType.INT, 0, 0, false, "0", DefaultFunction.NONE)),
                "数字字面量原样输出");
        assertEquals("ALTER TABLE mock_ddl_table ALTER COLUMN field_total SET DEFAULT 0.00",
                SourceDdlGenerator.alterColumnDefault(table,
                        column("field_total", "总额", ColumnType.DECIMAL, 10, 2, false, "0.00", DefaultFunction.NONE)),
                "DECIMAL 字面量原样输出");
        assertEquals("ALTER TABLE mock_ddl_table ALTER COLUMN field_enable SET DEFAULT b'1'",
                SourceDdlGenerator.alterColumnDefault(table,
                        column("field_enable", "是否启用", ColumnType.BOOLEAN, 0, 0, false, "b'1'", DefaultFunction.NONE)),
                "BOOLEAN 字面量 b'1' 原样输出不加引号");
        assertEquals("ALTER TABLE mock_ddl_table ALTER COLUMN field_valid_time SET DEFAULT '1970-01-01 00:00:01'",
                SourceDdlGenerator.alterColumnDefault(table,
                        column("field_valid_time", "生效时间", ColumnType.TIMESTAMP, 0, 0, false,
                                "1970-01-01 00:00:01", DefaultFunction.NONE)),
                "时间字面量加单引号");
    }

    @Test
    void alterColumnDefaultRendersFunctionalDefaults() {
        TableDef table = table("mock_ddl_table");
        assertEquals("ALTER TABLE mock_ddl_table ALTER COLUMN field_create_time SET DEFAULT CURRENT_TIMESTAMP",
                SourceDdlGenerator.alterColumnDefault(table,
                        column("field_create_time", "创建时间", ColumnType.TIMESTAMP, 0, 0, false,
                                null, DefaultFunction.CURRENT_TIMESTAMP)),
                "CURRENT_TIMESTAMP 渲染 SET DEFAULT CURRENT_TIMESTAMP");
        String ddl = SourceDdlGenerator.alterColumnDefault(table,
                column("field_update_time", "修改时间", ColumnType.TIMESTAMP, 0, 0, true,
                        null, DefaultFunction.ON_UPDATE_CURRENT_TIMESTAMP));
        assertEquals("ALTER TABLE mock_ddl_table ALTER COLUMN field_update_time SET DEFAULT CURRENT_TIMESTAMP", ddl,
                "ON_UPDATE 同样渲染 SET DEFAULT CURRENT_TIMESTAMP（MySQL 8.0+ 支持）");
        assertFalse(ddl.contains("ON UPDATE"), "SET DEFAULT 仅变更默认值，实际列已有 ON UPDATE 子句保持不变");
    }

    @Test
    void alterColumnDefaultWithoutDefaultFailsFast() {
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.alterColumnDefault(table("mock_ddl_table"),
                column("field_remark", "备注", ColumnType.STRING, 500, 0, true, null, DefaultFunction.NONE)),
                "列无默认值时不生成 DROP DEFAULT（去除默认值走 MODIFY COLUMN）");
    }

    @Test
    void alterColumnCommentRewritesFullDefinition() {
        TableDef table = table("mock_ddl_table");
        ColumnDef renamed = column("field_remark", "备注（新注释）", ColumnType.STRING, 500, 0, true, null, DefaultFunction.NONE);
        assertEquals("ALTER TABLE mock_ddl_table MODIFY COLUMN field_remark VARCHAR(500) DEFAULT NULL COMMENT '备注（新注释）'",
                SourceDdlGenerator.alterColumnComment(table, renamed),
                "列 COMMENT 变更走 MODIFY COLUMN 完整列定义重写");
        assertEquals(SourceDdlGenerator.modifyColumn(table, renamed), SourceDdlGenerator.alterColumnComment(table, renamed),
                "与 MODIFY COLUMN 文本一致（保留独立语义入口供 Differ 分流）");
    }

    @Test
    void alterTableCommentRendersSetComment() {
        TableDef table = new TableDef("mock_ddl_table", "新表注释", null, List.of(primaryKey()), List.of(), List.of());
        assertEquals("ALTER TABLE mock_ddl_table COMMENT='新表注释'",
                SourceDdlGenerator.alterTableComment(table), "表 COMMENT 取 TableDef 携带的最新注释");
    }

    @Test
    void addIndexRendersUniqueAndComposite() {
        TableDef table = new TableDef("mock_ddl_table", "DDL 生成测试表", null,
                List.of(primaryKey(),
                        column("field_code", "编码", ColumnType.STRING, 100, 0, false, null, DefaultFunction.NONE),
                        column("field_name", "名称", ColumnType.STRING, 200, 0, false, null, DefaultFunction.NONE),
                        column("field_enable", "是否启用", ColumnType.BOOLEAN, 0, 0, false, "b'1'", DefaultFunction.NONE)),
                List.of(), List.of());
        assertEquals("ALTER TABLE mock_ddl_table ADD UNIQUE INDEX uk_mock_code (field_code)",
                SourceDdlGenerator.addIndex(table, new IndexDef("uk_mock_code", List.of("field_code"), true, IndexType.BTREE)),
                "唯一索引用 ADD UNIQUE INDEX");
        assertEquals("ALTER TABLE mock_ddl_table ADD INDEX idx_mock_name_enable (field_name, field_enable)",
                SourceDdlGenerator.addIndex(table,
                        new IndexDef("idx_mock_name_enable", List.of("field_name", "field_enable"), false, IndexType.BTREE)),
                "组合索引按数组顺序渲染、不写 USING BTREE");
    }

    // ---------------------------------------------------------------- fail-fast（防御纵深）

    @Test
    void onUpdateDefaultOnNonTimestampFailsFast() {
        TableDef table = table("mock_ddl_table");
        ColumnDef datetime = column("field_finish_time", "完成时间", ColumnType.DATETIME, 0, 0, true,
                null, DefaultFunction.ON_UPDATE_CURRENT_TIMESTAMP);
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.modifyColumn(table, datetime),
                "ON UPDATE CURRENT_TIMESTAMP 仅 TIMESTAMP 列合法");
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.alterColumnDefault(table, datetime),
                "SET DEFAULT 路径同口径拦截");
        ColumnDef string = column("field_remark", "备注", ColumnType.STRING, 100, 0, false,
                null, DefaultFunction.ON_UPDATE_CURRENT_TIMESTAMP);
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.addColumn(table, string),
                "字符串列携带函数式默认值拦截");
    }

    @Test
    void currentTimestampDefaultTypeCheck() {
        TableDef table = table("mock_ddl_table");
        ColumnDef date = column("field_biz_date", "业务日期", ColumnType.DATE, 0, 0, false,
                null, DefaultFunction.CURRENT_TIMESTAMP);
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.modifyColumn(table, date),
                "DATE 列不支持 CURRENT_TIMESTAMP 默认值");
        ColumnDef datetime = column("field_finish_time", "完成时间", ColumnType.DATETIME, 0, 0, false,
                null, DefaultFunction.CURRENT_TIMESTAMP);
        assertEquals("ALTER TABLE mock_ddl_table MODIFY COLUMN field_finish_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '完成时间'",
                SourceDdlGenerator.modifyColumn(table, datetime),
                "CURRENT_TIMESTAMP 放宽到 TIMESTAMP/DATETIME（MySQL 8.4 均合法，实现口径）");
    }

    @Test
    void identifierViolationsFailFast() {
        TableDef badTable = new TableDef("Mock_Table", "违例表名", null, List.of(primaryKey()), List.of(), List.of());
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.createTable(badTable), "表名违例");
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.alterTableComment(badTable), "表名违例（入口全覆盖）");

        TableDef table = table("mock_ddl_table");
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.addColumn(table,
                        column("remark", "缺前缀列名", ColumnType.STRING, 32, 0, false, null, DefaultFunction.NONE)),
                "列名缺 field_ 前缀");
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.modifyColumn(table,
                        column("Field_Remark", "大写列名", ColumnType.STRING, 32, 0, false, null, DefaultFunction.NONE)),
                "列名违反标识符正则");
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.alterColumnDefault(table,
                        column("field_remark;", "注入列名", ColumnType.STRING, 32, 0, false, null, DefaultFunction.NONE)),
                "列名含特殊字符（注入面收口）");
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.addIndex(table,
                        new IndexDef("Idx_Bad", List.of("field_id"), false, IndexType.BTREE)),
                "索引名违反标识符正则");
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.addIndex(table,
                        new IndexDef("idx_ok", List.of("field_not_exists"), false, IndexType.BTREE)),
                "索引列必须是该表列集合的子集");

        TableDef badColumnTable = new TableDef("mock_ddl_table", "DDL 生成测试表", null,
                List.of(primaryKey(), column("Bad_Column", "违例列", ColumnType.STRING, 32, 0, false, null, DefaultFunction.NONE)),
                List.of(), List.of());
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.createTable(badColumnTable),
                "CREATE TABLE 内列名违例");
    }

    @Test
    void textAndJsonColumnsRejectDefaults() {
        TableDef table = table("mock_ddl_table");
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.addColumn(table,
                        column("field_content", "内容", ColumnType.TEXT, 0, 0, true, "x", DefaultFunction.NONE)),
                "TEXT 列不允许字面量默认值");
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.modifyColumn(table,
                        column("field_payload", "载荷", ColumnType.JSON, 0, 0, true, null, DefaultFunction.CURRENT_TIMESTAMP)),
                "JSON 列不允许函数式默认值");
        assertEquals("ALTER TABLE mock_ddl_table ADD COLUMN field_content TEXT DEFAULT NULL COMMENT '内容'",
                SourceDdlGenerator.addColumn(table,
                        column("field_content", "内容", ColumnType.TEXT, 0, 0, true, null, DefaultFunction.NONE)),
                "无默认值的 TEXT 正常渲染 DEFAULT NULL");
    }

    @Test
    void rawLiteralsViolatingWhitelistFailFast() {
        TableDef table = table("mock_ddl_table");
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.alterColumnDefault(table,
                        column("field_count", "数量", ColumnType.INT, 0, 0, false, "0; DROP TABLE x", DefaultFunction.NONE)),
                "INT 字面量过白名单（原样输出的注入面收口）");
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.alterColumnDefault(table,
                        column("field_flag", "标志", ColumnType.BOOLEAN, 0, 0, false, "1", DefaultFunction.NONE)),
                "BOOLEAN 字面量仅收 b'0'/b'1'");
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.alterColumnDefault(table,
                        column("field_total", "总额", ColumnType.DECIMAL, 10, 2, false, "abc", DefaultFunction.NONE)),
                "DECIMAL 字面量收小数");
    }

    @Test
    void stringAndDecimalRequirePositiveLength() {
        TableDef table = table("mock_ddl_table");
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.addColumn(table,
                        column("field_remark", "备注", ColumnType.STRING, 0, 0, false, null, DefaultFunction.NONE)),
                "STRING 必须 length ≥ 1（防御纵深）");
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.modifyColumn(table,
                        column("field_amount", "金额", ColumnType.DECIMAL, 0, 2, false, null, DefaultFunction.NONE)),
                "DECIMAL 必须 length ≥ 1（防御纵深）");
    }

    // ---------------------------------------------------------------- 结构与转义

    @Test
    void createTableRequiresSinglePrimaryKey() {
        TableDef noPk = new TableDef("mock_ddl_table", "DDL 生成测试表", null,
                List.of(column("field_remark", "备注", ColumnType.STRING, 100, 0, false, null, DefaultFunction.NONE)),
                List.of(), List.of());
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.createTable(noPk), "缺少主键列");
        TableDef twoPk = new TableDef("mock_ddl_table", "DDL 生成测试表", null,
                List.of(primaryKey(), new ColumnDef("field_dup", "重复主键", ColumnType.STRING, 32, 0,
                        false, null, DefaultFunction.NONE, true)),
                List.of(), List.of());
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.createTable(twoPk), "主键列不唯一");
    }

    @Test
    void commentAndStringLiteralEscapeSingleQuote() {
        TableDef table = table("mock_ddl_table");
        assertEquals("ALTER TABLE mock_ddl_table ADD COLUMN field_remark VARCHAR(100) DEFAULT NULL COMMENT '备注''含引号'",
                SourceDdlGenerator.addColumn(table,
                        column("field_remark", "备注'含引号", ColumnType.STRING, 100, 0, true, null, DefaultFunction.NONE)),
                "列 COMMENT 单引号成对转义");
        assertEquals("ALTER TABLE mock_ddl_table ALTER COLUMN field_status SET DEFAULT 'It''s ok'",
                SourceDdlGenerator.alterColumnDefault(table,
                        column("field_status", "状态", ColumnType.STRING, 50, 0, false, "It's ok", DefaultFunction.NONE)),
                "字符串字面量单引号成对转义");
        TableDef quotedTable = new TableDef("mock_ddl_table", "含'引号'注释", null, List.of(primaryKey()), List.of(), List.of());
        assertEquals("ALTER TABLE mock_ddl_table COMMENT='含''引号''注释'",
                SourceDdlGenerator.alterTableComment(quotedTable), "表 COMMENT 单引号成对转义");
    }

    // ---------------------------------------------------------------- 声明主键列集（F19-DOC-005）

    /** V1_01_001 sys_org_post_user 建表语句原文（比对时空白归一化并去掉结尾分号） */
    private static final String V1_01_001_SYS_ORG_POST_USER_DDL = """
            CREATE TABLE IF NOT EXISTS sys_org_post_user (
                field_user_id VARCHAR(32) NOT NULL COMMENT '人员ID',
                field_post_id VARCHAR(32) NOT NULL COMMENT '岗位ID',
                PRIMARY KEY (field_user_id, field_post_id),
                KEY idx_post_user_post (field_post_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位-人员关联表';
            """;

    @Test
    void createTableCompositePrimaryKeyMatchesV1_01_001Baseline() {
        TableDef table = SourceTableScanner.parse(SysOrgPostUserEntity.class);
        String normalized = normalize(SourceDdlGenerator.createTable(table));

        // 核心验收：空白归一化后与 V1_01_001 sys_org_post_user 建表语句整段逐字等价
        assertEquals(normalize(V1_01_001_SYS_ORG_POST_USER_DDL), normalized,
                "组合主键建表 DDL 与 V1_01_001 脚本段整段等价（无内建列、主键列以业务列标注）");

        // 语义片段逐一断言：组合主键按声明序渲染、位于列定义之后二级索引之前
        List<String> fragments = List.of(
                "field_user_id VARCHAR(32) NOT NULL COMMENT '人员ID'",
                "field_post_id VARCHAR(32) NOT NULL COMMENT '岗位ID'",
                "PRIMARY KEY (field_user_id, field_post_id)",
                "KEY idx_post_user_post (field_post_id)");
        int position = -1;
        for (String fragment : fragments) {
            int found = normalized.indexOf(fragment);
            assertTrue(found >= 0, "缺失语义片段: " + fragment);
            assertTrue(found > position, "列序/主键/索引序错位: " + fragment);
            position = found;
        }
        assertFalse(normalized.contains("field_id"), "primaryKeyColumns 非空时引擎不再内建 field_id");
        assertTrue(normalized.endsWith(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位-人员关联表'"));
    }

    @Test
    void createTableSingleNonFieldIdPrimaryKey() {
        TableDef table = SourceTableScanner.parse(SinglePrimaryKeyEntity.class);
        String normalized = normalize(SourceDdlGenerator.createTable(table));

        assertTrue(normalized.contains("PRIMARY KEY (field_user_id)"),
                "单列非 field_id 主键按声明渲染: " + normalized);
        assertFalse(normalized.contains("PRIMARY KEY (field_id)"), "不再渲染内建 field_id 主键");
        assertTrue(normalized.contains("field_user_id VARCHAR(32) NOT NULL COMMENT '人员ID'"));
        assertTrue(normalized.contains("field_remark VARCHAR(200) DEFAULT NULL COMMENT '备注'"));
        assertTrue(normalized.endsWith(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
                + " COMMENT='DDL 生成测试单列主键表（BaseModel + 单列主键声明）'"));
    }

    @Test
    void declaredPrimaryKeyViolationsFailFast() {
        // 声明主键列不在表列集合（防御纵深，对齐扫描器规则 10）
        TableDef unknownColumn = new TableDef("mock_ddl_table", "DDL 生成测试表", null,
                List.of(primaryKey()), List.of(), List.of("field_not_exists"));
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.createTable(unknownColumn),
                "声明主键列必须是该表列集合的子集");

        // 声明主键列重复（防御纵深）
        TableDef duplicated = new TableDef("mock_ddl_table", "DDL 生成测试表", null,
                List.of(primaryKey(), column("field_remark", "备注", ColumnType.STRING, 100, 0, true, null, DefaultFunction.NONE)),
                List.of(), List.of("field_remark", "field_remark"));
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.createTable(duplicated), "声明主键列不得重复");

        // 声明主键列名违反标识符正则（注入面收口）
        TableDef badName = new TableDef("mock_ddl_table", "DDL 生成测试表", null,
                List.of(primaryKey()), List.of(), List.of("field_id; DROP TABLE x"));
        assertThrows(IllegalStateException.class, () -> SourceDdlGenerator.createTable(badName),
                "声明主键列名过白名单");
    }
}
