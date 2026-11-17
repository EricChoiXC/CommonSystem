package com.eric.base.common.source.ddl;

import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import com.eric.base.common.source.enums.IndexType;
import com.eric.base.common.source.model.ColumnDef;
import com.eric.base.common.source.model.IndexDef;
import com.eric.base.common.source.model.TableDef;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 表结构 DDL 生成器（MySQL 8.4 方言）
 *
 * @author Eric
 * @date 2026/11/16
 * @description 纯文本 DDL 生成工具（设计方案 §4.1）：输入 {@link TableDef}
 *              （SourceTableScanner 归一产物），输出与既有建表脚本同风格的 SQL 文本，
 *              以 V1_01_046 的 sys_datasource 建表语句为全量等价基线；不连库、无 Spring
 *              依赖，供 SourceSchemaDiffer 组装变更与执行器落地。ENGINE=InnoDB /
 *              CHARSET=utf8mb4 为内建统一默认，不开放表级定制（§3.1）。
 *              <p>硬约束：本类只提供新增/修改类语句（CREATE TABLE / ADD COLUMN /
 *              MODIFY COLUMN / ALTER COLUMN SET DEFAULT / ALTER COMMENT / ADD INDEX），
 *              不提供 DROP TABLE / DROP COLUMN / RENAME 及任何删改结构方法——「引擎永不
 *              执行破坏性变更」（§4.3）由本类方法面硬收口。</p>
 *              <p>注入面收口（防御纵深，§4.1）：标识符（表/列/索引名）先过白名单正则
 *              [a-z][a-z0-9_]*（列名再校验 field_ 前缀）、原样输出的字面量默认值
 *              （INT/BIGINT 数字、DECIMAL 数字、BOOLEAN b'0'/b'1'）过正则白名单，违例
 *              一律 fail-fast IllegalStateException——注解在扫描期已校验，本类前置再校验
 *              一次；引号内容（COMMENT 与字符串/时间字面量）统一转义单引号（''）与
 *              反斜杠（\）。</p>
 *              <p>渲染口径：类型映射见 {@link ColumnType}（STRING→VARCHAR(length)、
 *              DECIMAL→DECIMAL(length,decimalLength)、BOOLEAN→BIT(1) 等，§3.4）；可空
 *              与默认值——非可空 NOT NULL、可空且无默认值仅 DEFAULT NULL（省略 NULL
 *              关键字，对齐 V1_01_046 的 field_conditions / field_creator_id 等列）、
 *              可空且带默认值 NULL DEFAULT ...（对齐 field_update_time 先例）；TEXT/JSON
 *              列不允许任何默认值（违例 fail-fast）；函数式默认值 CURRENT_TIMESTAMP
 *              允许 TIMESTAMP/DATETIME 列（DATE 等其余类型违例 fail-fast），
 *              ON_UPDATE_CURRENT_TIMESTAMP 仅 TIMESTAMP 列合法（违例 fail-fast）。</p>
 *              <p>实现口径补充（供 F19-BE-004 回写设计方案 §4.1）：ADD COLUMN 首版不带
 *              AFTER 定位（设计未定稿，MySQL 将新列追加到表尾；列序差异不影响 Differ
 *              按列名比对的语义结果），如需按注解列序定位再扩展；ADD INDEX 用
 *              ADD [UNIQUE] INDEX 语法、不写 USING BTREE（对齐既有脚本，InnoDB 仅支持
 *              BTREE）；ALTER COLUMN SET DEFAULT 仅变更默认值，ON UPDATE 子句不受影响
 *              （MySQL 8.0+ 语法限制），该子句本身的变更走 MODIFY COLUMN 全量重定义；
 *              生成语句不带结尾分号（执行器按单条语句提交，脚本中的分号不属于语句语义）。</p>
 */
public final class SourceDdlGenerator {

    /** 标识符白名单：表名 / 列名 / 索引名统一口径（与 SourceTableScanner 一致；DDL 无绑定变量，注入面由白名单收口） */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");

    /** 列名前缀（全工程规范） */
    private static final String COLUMN_PREFIX = "field_";

    /** 整数字面量（INT / BIGINT 字面量默认值白名单，原样输出前的注入面收口） */
    private static final Pattern INTEGER_LITERAL = Pattern.compile("[+-]?\\d+");

    /** 小数字面量（DECIMAL 字面量默认值白名单） */
    private static final Pattern DECIMAL_LITERAL = Pattern.compile("[+-]?\\d+(\\.\\d+)?");

    /** 布尔字面量（BOOLEAN 字面量默认值白名单，先例 b'1'，原样输出不加引号） */
    private static final Pattern BOOLEAN_LITERAL = Pattern.compile("b'[01]'");

    private SourceDdlGenerator() {
    }

    /**
     * 生成 CREATE TABLE IF NOT EXISTS 语句：列定义 → PRIMARY KEY（有效主键列集，
     * 单列/组合按声明序，F19-DOC-005）→ 索引（UNIQUE KEY / KEY）→ ) ENGINE=InnoDB
     * DEFAULT CHARSET=utf8mb4 COMMENT='...'，输出形态对齐既有建表脚本
     * （全量等价基线 V1_01_046；组合主键基线 V1_01_001 sys_org_post_user）
     *
     * @param table 表定义（列清单含公共列模板内建列或 primaryKeyColumns 声明主键的全部
     *              业务列，索引清单为二级索引）
     * @return DDL 文本（不带结尾分号）
     */
    public static String createTable(TableDef table) {
        requireTableIdentifier(table.getName());
        requireComment("表 COMMENT", table.getName(), table.getComment());
        List<ColumnDef> columns = table.getColumns();
        if (columns.isEmpty()) {
            throw new IllegalStateException("表定义缺少列: " + table.getName());
        }
        String primaryKeyColumns = resolvePrimaryKeyColumns(table, columns);
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(table.getName()).append(" (\n");
        for (ColumnDef column : columns) {
            sql.append("    ").append(columnDefinition(column)).append(",\n");
        }
        sql.append("    PRIMARY KEY (").append(primaryKeyColumns).append(')');
        for (IndexDef index : table.getIndexes()) {
            sql.append(",\n    ").append(indexKeyDefinition(table, index));
        }
        sql.append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='").append(escape(table.getComment())).append('\'');
        return sql.toString();
    }

    /**
     * 生成 ALTER TABLE ... ADD COLUMN 语句（缺失列，语义矩阵 L1）。首版不带 AFTER 定位
     * （设计未定稿）：MySQL 将新列追加到表尾，列序差异不影响 SourceSchemaDiffer 按列名
     * 比对的语义结果；如需按注解列序定位，由 F19-BE-004 回写设计方案后扩展
     * （可基于 TableDef 列序推导前一列）
     *
     * @param table  目标表定义
     * @param column 新增列定义
     * @return DDL 文本（不带结尾分号）
     */
    public static String addColumn(TableDef table, ColumnDef column) {
        requireTableIdentifier(table.getName());
        return "ALTER TABLE " + table.getName() + " ADD COLUMN " + columnDefinition(column);
    }

    /**
     * 生成 ALTER TABLE ... MODIFY COLUMN 语句（完整列定义重写，语义矩阵 L1：VARCHAR 扩长 /
     * DECIMAL 精度提升）；同承担可空放宽、ON UPDATE 子句变更的全量重定义出口
     *
     * @param table  目标表定义
     * @param column 修改后的列定义（含最新类型/长度/可空/默认值/COMMENT）
     * @return DDL 文本（不带结尾分号）
     */
    public static String modifyColumn(TableDef table, ColumnDef column) {
        requireTableIdentifier(table.getName());
        return "ALTER TABLE " + table.getName() + " MODIFY COLUMN " + columnDefinition(column);
    }

    /**
     * 生成 ALTER TABLE ... ALTER COLUMN ... SET DEFAULT 语句（列默认值变更，语义矩阵 L1）。
     * 字面量默认值按列类型渲染（数字/BOOLEAN 经白名单原样输出、字符串/时间加引号转义）；
     * 函数式默认值统一渲染 SET DEFAULT CURRENT_TIMESTAMP——ON UPDATE 子句不受
     * ALTER COLUMN SET DEFAULT 影响（MySQL 8.0+ 语法限制），实际列已有的 ON UPDATE 保持
     * 不变，该子句本身的变更须走 {@link #modifyColumn} 全量重定义；列无默认值时生成
     * DROP DEFAULT 超出本类方法面（去除默认值走 MODIFY COLUMN），违例 fail-fast
     *
     * @param table  目标表定义
     * @param column 携带目标默认值的列定义
     * @return DDL 文本（不带结尾分号）
     */
    public static String alterColumnDefault(TableDef table, ColumnDef column) {
        requireTableIdentifier(table.getName());
        checkColumnDefinition(column);
        if (!hasDefault(column)) {
            throw new IllegalStateException("列无默认值，不生成 ALTER COLUMN SET DEFAULT"
                    + "（去除默认值走 MODIFY COLUMN 全量重定义）: " + column.getName() + " (" + table.getName() + ")");
        }
        String value = DefaultFunction.NONE != column.getDefaultFunction()
                ? "CURRENT_TIMESTAMP"
                : renderLiteral(column.getType(), column.getDefaultValue());
        return "ALTER TABLE " + table.getName() + " ALTER COLUMN " + column.getName() + " SET DEFAULT " + value;
    }

    /**
     * 生成列 COMMENT 变更语句（语义矩阵 L1）：MODIFY COLUMN 完整列定义重写（列定义携带
     * 最新 COMMENT），与 {@link #modifyColumn} 文本一致，保留独立入口供 Differ 按变更类型分流
     *
     * @param table  目标表定义
     * @param column 修改后的列定义（comment 为最新注释）
     * @return DDL 文本（不带结尾分号）
     */
    public static String alterColumnComment(TableDef table, ColumnDef column) {
        return modifyColumn(table, column);
    }

    /**
     * 生成 ALTER TABLE ... COMMENT='...' 语句（表 COMMENT 变更，语义矩阵 L1）
     *
     * @param table 目标表定义（comment 为最新注释）
     * @return DDL 文本（不带结尾分号）
     */
    public static String alterTableComment(TableDef table) {
        requireTableIdentifier(table.getName());
        requireComment("表 COMMENT", table.getName(), table.getComment());
        return "ALTER TABLE " + table.getName() + " COMMENT='" + escape(table.getComment()) + "'";
    }

    /**
     * 生成 ALTER TABLE ... ADD [UNIQUE] INDEX 语句（缺失索引含唯一索引，语义矩阵 L1）。
     * 不写 USING BTREE：对齐既有脚本风格，InnoDB 仅支持 BTREE；索引名过白名单、索引列
     * 须为该表列集合子集（违例 fail-fast）
     *
     * @param table 目标表定义
     * @param index 新增索引定义
     * @return DDL 文本（不带结尾分号）
     */
    public static String addIndex(TableDef table, IndexDef index) {
        requireTableIdentifier(table.getName());
        checkIndex(table, index);
        return "ALTER TABLE " + table.getName() + " ADD " + (index.isUnique() ? "UNIQUE " : "") + "INDEX "
                + index.getName() + " (" + String.join(", ", index.getColumns()) + ")";
    }

    /* ==================== 内部 ==================== */

    /**
     * 有效主键列集解析（F19-DOC-005）：注解声明非空时按声明序输出（组合/单列均支持），
     * 逐列防御再校验——列名白名单 + field_ 前缀、该表列集合子集、声明不重复（对齐扫描器
     * 规则 10 前置通道）；缺省时维持公共列模板内建 field_id 口径（ColumnDef#primaryKey
     * 标记列唯一），违例 fail-fast 文案与扩展前一致
     */
    private static String resolvePrimaryKeyColumns(TableDef table, List<ColumnDef> columns) {
        List<String> declared = table.getPrimaryKeyColumns();
        if (!declared.isEmpty()) {
            Set<String> tableColumns = new HashSet<>();
            for (ColumnDef column : columns) {
                tableColumns.add(column.getName());
            }
            Set<String> seen = new HashSet<>();
            for (String column : declared) {
                requireColumnIdentifier(column);
                if (!tableColumns.contains(column)) {
                    throw new IllegalStateException("主键列必须是该表列集合的子集: "
                            + column + " (" + table.getName() + ")");
                }
                if (!seen.add(column)) {
                    throw new IllegalStateException("主键列重复: " + column + " (" + table.getName() + ")");
                }
            }
            return String.join(", ", declared);
        }
        ColumnDef primaryKey = null;
        for (ColumnDef column : columns) {
            if (column.isPrimaryKey()) {
                if (null != primaryKey) {
                    throw new IllegalStateException("主键列不唯一: "
                            + table.getName() + " -> " + column.getName());
                }
                primaryKey = column;
            }
        }
        if (null == primaryKey) {
            throw new IllegalStateException("表定义缺少主键列（公共列模板内建 field_id）: " + table.getName());
        }
        return primaryKey.getName();
    }

    /**
     * 单列完整定义渲染：{列名} {类型} {可空与默认值} COMMENT '{注释}'，
     * 渲染前过标识符白名单与列语义校验（防御纵深）
     */
    private static String columnDefinition(ColumnDef column) {
        checkColumnDefinition(column);
        return column.getName() + " " + sqlType(column) + " " + nullabilityAndDefault(column)
                + " COMMENT '" + escape(column.getComment()) + "'";
    }

    /**
     * CREATE TABLE 内的索引片段：UNIQUE KEY {name} ({cols}) / KEY {name} ({cols})，
     * 对齐既有脚本风格
     */
    private static String indexKeyDefinition(TableDef table, IndexDef index) {
        checkIndex(table, index);
        return (index.isUnique() ? "UNIQUE KEY " : "KEY ") + index.getName()
                + " (" + String.join(", ", index.getColumns()) + ")";
    }

    /**
     * 列语义校验（fail-fast 清单）：标识符白名单 + field_ 前缀、defaultValue 与
     * defaultFunction 互斥、comment 必填非空、STRING/DECIMAL length ≥ 1、TEXT/JSON
     * 禁默认值、ON_UPDATE 仅 TIMESTAMP（CURRENT_TIMESTAMP 放宽到 DATETIME，实现口径）、
     * 原样输出字面量过白名单——扫描器已校验的规则前置再校验一次
     */
    private static void checkColumnDefinition(ColumnDef column) {
        requireColumnIdentifier(column.getName());
        if (null != column.getDefaultValue() && DefaultFunction.NONE != column.getDefaultFunction()) {
            throw new IllegalStateException("defaultValue 与 defaultFunction 互斥: " + column.getName());
        }
        if (null == column.getComment() || column.getComment().isBlank()) {
            throw new IllegalStateException("列 COMMENT 必填非空: " + column.getName());
        }
        boolean lengthMatters = ColumnType.STRING == column.getType() || ColumnType.DECIMAL == column.getType();
        if (lengthMatters && column.getLength() < 1) {
            throw new IllegalStateException(column.getType() + " 列必须 length ≥ 1: " + column.getName());
        }
        if (ColumnType.DECIMAL == column.getType() && column.getDecimalLength() < 0) {
            throw new IllegalStateException("DECIMAL 列小数位不能为负: " + column.getName());
        }
        if ((ColumnType.TEXT == column.getType() || ColumnType.JSON == column.getType()) && hasDefault(column)) {
            throw new IllegalStateException("TEXT/JSON 列不允许默认值: " + column.getName());
        }
        if (DefaultFunction.ON_UPDATE_CURRENT_TIMESTAMP == column.getDefaultFunction()
                && ColumnType.TIMESTAMP != column.getType()) {
            throw new IllegalStateException("ON UPDATE CURRENT_TIMESTAMP 仅 TIMESTAMP 列合法: "
                    + column.getName() + " (" + column.getType() + ")");
        }
        if (DefaultFunction.CURRENT_TIMESTAMP == column.getDefaultFunction()
                && ColumnType.TIMESTAMP != column.getType() && ColumnType.DATETIME != column.getType()) {
            throw new IllegalStateException("CURRENT_TIMESTAMP 仅 TIMESTAMP/DATETIME 列合法: "
                    + column.getName() + " (" + column.getType() + ")");
        }
        if (null != column.getDefaultValue()) {
            checkLiteral(column);
        }
    }

    /**
     * 索引定义校验：索引名白名单、类型当前仅 BTREE（FULLTEXT 预留未实现）、索引列非空、
     * 索引列过列名白名单且为该表列集合子集（对齐扫描器规则 1/6）
     */
    private static void checkIndex(TableDef table, IndexDef index) {
        if (null == index.getName() || !IDENTIFIER_PATTERN.matcher(index.getName()).matches()) {
            throw new IllegalStateException("索引名不符合标识符规范 [a-z][a-z0-9_]*: "
                    + index.getName() + " (" + table.getName() + ")");
        }
        if (IndexType.BTREE != index.getType()) {
            throw new IllegalStateException("索引类型暂不支持（FULLTEXT 预留，设计方案 §10 Out of Scope）: "
                    + index.getType() + " (" + index.getName() + ")");
        }
        if (index.getColumns().isEmpty()) {
            throw new IllegalStateException("索引列不能为空: " + index.getName() + " (" + table.getName() + ")");
        }
        Set<String> tableColumns = new HashSet<>();
        for (ColumnDef column : table.getColumns()) {
            tableColumns.add(column.getName());
        }
        for (String column : index.getColumns()) {
            requireColumnIdentifier(column);
            if (!tableColumns.contains(column)) {
                throw new IllegalStateException("索引列必须是该表列集合的子集: " + index.getName()
                        + " -> " + column + " (" + table.getName() + ")");
            }
        }
    }

    /**
     * 逻辑类型 → MySQL 8.4 物理类型（§3.4 映射）
     */
    private static String sqlType(ColumnDef column) {
        return switch (column.getType()) {
            case STRING -> "VARCHAR(" + column.getLength() + ")";
            case TEXT -> "TEXT";
            case INT -> "INT";
            case BIGINT -> "BIGINT";
            case DECIMAL -> "DECIMAL(" + column.getLength() + "," + column.getDecimalLength() + ")";
            case BOOLEAN -> "BIT(1)";
            case DATE -> "DATE";
            case DATETIME -> "DATETIME";
            case TIMESTAMP -> "TIMESTAMP";
            case JSON -> "JSON";
        };
    }

    /**
     * 可空与默认值渲染（对齐 V1_01_046 基线）：非可空 NOT NULL；可空且无默认值仅
     * DEFAULT NULL（省略 NULL 关键字，DEFAULT NULL 本身已表达可空）；可空且带默认值
     * NULL DEFAULT ...（对齐 field_update_time 先例）
     */
    private static String nullabilityAndDefault(ColumnDef column) {
        if (column.isNullable()) {
            return hasDefault(column)
                    ? "NULL DEFAULT " + defaultValueToken(column)
                    : "DEFAULT NULL";
        }
        return hasDefault(column) ? "NOT NULL DEFAULT " + defaultValueToken(column) : "NOT NULL";
    }

    /**
     * 默认值片段渲染：函数式 CURRENT_TIMESTAMP → CURRENT_TIMESTAMP、
     * ON_UPDATE_CURRENT_TIMESTAMP → CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
     * （类型合法性由 {@link #checkColumnDefinition} 前置保证）；字面量按列类型渲染
     */
    private static String defaultValueToken(ColumnDef column) {
        if (DefaultFunction.NONE != column.getDefaultFunction()) {
            return "CURRENT_TIMESTAMP" + (DefaultFunction.ON_UPDATE_CURRENT_TIMESTAMP == column.getDefaultFunction()
                    ? " ON UPDATE CURRENT_TIMESTAMP" : "");
        }
        return renderLiteral(column.getType(), column.getDefaultValue());
    }

    /**
     * 字面量渲染：字符串/时间列加单引号并转义（注入面收口）；数字列与 BOOLEAN 经
     * {@link #checkColumnDefinition} 白名单后原样输出；TEXT/JSON 已被前置拦截不会到达
     */
    private static String renderLiteral(ColumnType type, String literal) {
        return switch (type) {
            case STRING, DATE, DATETIME, TIMESTAMP -> "'" + escape(literal) + "'";
            default -> literal;
        };
    }

    /**
     * 原样输出字面量的白名单校验（与扫描器规则 5 口径一致，前置再校验一次）：
     * INT/BIGINT 收整数、DECIMAL 收小数、BOOLEAN 收 b'0'/b'1'；字符串/时间列经引号
     * 转义输出不再限制格式
     */
    private static void checkLiteral(ColumnDef column) {
        String literal = column.getDefaultValue();
        boolean matches = switch (column.getType()) {
            case INT, BIGINT -> INTEGER_LITERAL.matcher(literal).matches();
            case DECIMAL -> DECIMAL_LITERAL.matcher(literal).matches();
            case BOOLEAN -> BOOLEAN_LITERAL.matcher(literal).matches();
            default -> true;
        };
        if (!matches) {
            throw new IllegalStateException("defaultValue 字面量与列类型不匹配: "
                    + column.getName() + " -> " + literal);
        }
    }

    /**
     * 是否携带默认值（字面量或函数式任一）
     */
    private static boolean hasDefault(ColumnDef column) {
        return null != column.getDefaultValue() || DefaultFunction.NONE != column.getDefaultFunction();
    }

    /**
     * 表名白名单校验（防御纵深）
     */
    private static void requireTableIdentifier(String name) {
        if (null == name || !IDENTIFIER_PATTERN.matcher(name).matches()) {
            throw new IllegalStateException("表名不符合标识符规范 [a-z][a-z0-9_]*: " + name);
        }
    }

    /**
     * 列名白名单校验：标识符正则 + field_ 前缀（防御纵深）
     */
    private static void requireColumnIdentifier(String name) {
        if (null == name || !IDENTIFIER_PATTERN.matcher(name).matches()) {
            throw new IllegalStateException("列名不符合标识符规范 [a-z][a-z0-9_]*: " + name);
        }
        if (!name.startsWith(COLUMN_PREFIX)) {
            throw new IllegalStateException("列名必须以 field_ 为前缀: " + name);
        }
    }

    /**
     * COMMENT 必填非空校验（对齐扫描器规则 3）
     */
    private static void requireComment(String what, String context, String comment) {
        if (null == comment || comment.isBlank()) {
            throw new IllegalStateException(what + "必填非空: " + context);
        }
    }

    /**
     * 引号内容转义：反斜杠翻倍（MySQL 默认 sql_mode 下字符串转义符）+ 单引号成对
     * （'' 转义），顺序为先反斜杠后单引号
     */
    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("'", "''");
    }
}
