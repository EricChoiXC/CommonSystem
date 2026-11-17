package com.eric.base.common.source.tool;

import com.eric.base.common.source.diff.ActualColumn;
import com.eric.base.common.source.diff.ActualIndex;
import com.eric.base.common.source.diff.ActualTable;
import com.eric.base.common.source.diff.SourceSchemaDiffer;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import lombok.Getter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 表结构反向生成工具：从真实库 information_schema 反推 {@code @TableSource} /
 * {@code @ColumnField} / {@code @TableIndex} Java 片段（设计方案 §7.1，任务 F19-BE-008）。
 *
 * @author Eric
 * @date 2026/11/17
 * @description Wave 2 收尾任务：供 Wave 3 存量 21 表标注 adopt 用——工具产底稿、
 *              人工审阅落码（生成器正确性由 adopt 比对归零兜底，本工具不直接改源码）。
 *              普通类（非 @Component），仅 {@link #main(String[])} 入口，无 HTTP 面、
 *              无鉴权面；对目标库<strong>只读</strong>（information_schema 三源查询与
 *              表清单 SELECT，不写任何数据）。
 *              <p><strong>读取复用</strong>：information_schema 三源读取整体复用
 *              {@link SourceSchemaDiffer#loadActualTables()}（TABLES / COLUMNS /
 *              STATISTICS → ActualTable/ActualColumn/ActualIndex 归一，PRIMARY 主键已在
 *              聚合层剔除），经 ObjectProvider 匿名实现桥接构造注入的 JdbcTemplate，
 *              不重复实现三源读取。</p>
 *              <p><strong>连接参数解析优先级</strong>：命令行参数 &gt; 环境变量
 *              {@code CS_DB_HOST} / {@code CS_DB_PORT} / {@code CS_DB_NAME} /
 *              {@code CS_DB_USER} / {@code CS_DB_PASSWORD} &gt; 代码兜底
 *              （localhost/3306/common/root/root，与 application.properties 默认链一致）。
 *              参数形态 {@code --host --port --db --user --password}，表清单
 *              {@code --tables a,b,c}（缺省 = 库内全部 BASE TABLE）。</p>
 *              <p><strong>缺省表清单排除口径</strong>：{@code flyway_schema_history}
 *              （Flyway 历史留档表）与 {@code common_source_sync_log} /
 *              {@code common_source_migration_log}（引擎两张审计表）不进清单——设计方案
 *              §5.3 元数据表不纳入注解系统，反推它们只会产出无法 adopt 的噪音片段
 *              （审计表无独立实体类且由引擎自举）。该口径仅作用于缺省清单，显式
 *              {@code --tables} 指定时以人工意图为准不再过滤。</p>
 *              <p><strong>公共列剔除口径</strong>：field_id 主键与审计五列
 *              （field_creator_id / field_create_time / field_updater_id /
 *              field_update_time / field_enable）不产 {@code @ColumnField}（引擎公共列
 *              模板内建，标注开放性见 {@code ColumnField} javadoc）。剔除按推断基类执行：
 *              审计五列齐备 → 建议 extends MainModel（五列全剔除）；完全无审计列 →
 *              建议 extends BaseModel（仅剔除 field_id）；部分含审计列（如 sys_dict_type
 *              仅 field_enable）→ 与 MainModel/BaseModel 模板均不匹配，建议 BaseModel 并
 *              警示，未内建的审计名列按业务列标注（保证 adopt 零 diff 可达——若照搬
 *              「审计列一律剔除」，BaseModel 模板不建该列，adopt 必产缺失列 diff）。
 *              MainModel 建议下再逐列核对审计列与公共列模板（设计方案 §3.3）的定义，
 *              不一致处（如存量表 field_update_time 缺 DEFAULT/ON UPDATE）输出警示，
 *              提示 adopt 将产生 diff、人工核对。</p>
 *              <p><strong>无 field_id 主键表口径（F19-DOC-005 补充评审定稿，F19-BE-012 落地）</strong>：
 *              实际主键列集 ≠ [field_id]（以 {@code ActualTable.primaryKeyColumns} 为准，
 *              STATISTICS 中 PRIMARY 行按 SEQ_IN_INDEX 排序的列集）的表不再按「无审计列 →
 *              BaseModel（隐含 field_id）」误推——照搬该建议必产 L1「缺失列 field_id」diff、
 *              APPLY 会自动 ADD COLUMN 破坏既有主键语义。改输出：建议基类行注明 extends
 *              BaseModel + 显式 {@code primaryKeyColumns = {...}}（按实际主键列序渲染进类级
 *              注解，属性位置在 comment 之后 indexes 之前，数组顺序即 PRIMARY KEY 列序）；
 *              全部实际列（含主键列）按业务列 {@code @ColumnField} 输出（主键列实际 NOT NULL
 *              本就满足 nullable=false 口径，不额外改写）；不输出「field_id 内建」类尾注
 *              （该表无内建列）；主键仍不输出为索引成员（PRIMARY 剔除逻辑保持）。实际无主键的
 *              表（primaryKeyColumns 空集）维持现状口径不扩权。</p>
 *              <p><strong>类型反推</strong>：COLUMN_TYPE（如 varchar(100) / text / json /
 *              bigint / decimal(10,2) / timestamp / bit(1)）→ {@link ColumnType} +
 *              length / decimalLength；映射集合与 SourceSchemaDiffer#typeCompatible 的
 *              注解 ↔ DATA_TYPE 等价域一致（STRING↔varchar、TEXT↔text、INT↔int、
 *              BIGINT↔bigint、DECIMAL↔decimal、BOOLEAN↔bit(1)、DATE↔date、
 *              DATETIME↔datetime、TIMESTAMP↔timestamp、JSON↔json），超集类型
 *              （实测库中 sys_log_operation 的 longtext）与 varchar 不带长度等无法表达的
 *              形态不产 {@code @ColumnField}，输出警示注释行提示人工处置。</p>
 *              <p><strong>默认值反推（MySQL 8.x 实测口径）</strong>：COLUMN_DEFAULT 为
 *              CURRENT_TIMESTAMP（或 current_timestamp() 形态）→
 *              {@link DefaultFunction#CURRENT_TIMESTAMP}；EXTRA 含 on update
 *              CURRENT_TIMESTAMP → {@link DefaultFunction#ON_UPDATE_CURRENT_TIMESTAMP}；
 *              bit 默认 b'0'/b'1' 字面量与数字默认（DEFAULT 0/1）原样作 defaultValue；
 *              字符串字面量默认值经 Differ 读取层归一（剥外层引号、'' → '、\\ → \）后作
 *              defaultValue，渲染时做 Java 字符串转义（\、"、换行等）——注解 defaultValue
 *              语义 = Differ 归一值，与 VALIDATE 比对口径一致、adopt 零 diff；TEXT/JSON 列
 *              COLUMN_DEFAULT 为 NULL 表示无默认（MySQL 禁止其默认值，若实测带默认值则
 *              警示人工处置）。<strong>生成列识别</strong>：EXTRA 含 GENERATED——须先剔除
 *              {@code DEFAULT_GENERATED} 标记再判定（MySQL 8.0+ 对 DEFAULT CURRENT_TIMESTAMP
 *              也带 DEFAULT_GENERATED，直接 contains 会把函数默认值列误判为生成列），剩余
 *              含 GENERATED（STORED GENERATED / VIRTUAL GENERATED，实测
 *              sys_process_template.field_default_unique）的列不产 {@code @ColumnField}，
 *              输出 {@code // 生成列} 警示注释行提示人工处置（引擎暂不支持生成列标注，
 *              Wave 3 process 批次会涉及；引用生成列的索引一并警示）。</p>
 *              <p><strong>输出形态</strong>：逐表打印 Java 片段——类级
 *              {@code @TableSource(name, comment, indexes = {@TableIndex(...)})}（多个索引
 *              列 {@code columns = {"a", "b"}}，以 TableIndex 注解 String[] 属性为准）+
 *              逐列 {@code @ColumnField(...)}（属性输出顺序对齐 SysDatasource 手写风格：
 *              name、comment、type、length——仅 STRING/DECIMAL、decimalLength——仅 &gt;0、
 *              nullable——仅 true 时输出、默认值——仅有默认时输出）+ 建议的字段声明行
 *              （列名 → camelCase Java 字段名）+ 建议基类注释行。</p>
 *              <p><strong>Java 类型映射（仅供人工参考，工具不校验）</strong>：
 *              varchar→String、text→String、int→Integer、bigint→Long、decimal→BigDecimal、
 *              bit→Boolean、date→LocalDate、datetime→LocalDateTime、timestamp→LocalDateTime、
 *              json→Object（MainModel 现存审计字段为 java.util.Date，落码时以人工审阅为准）。</p>
 */
public final class SourceSchemaExportTool {

    /** 连接兜底默认值（与 application.properties 默认链一致：localhost/3306/common/root/root） */
    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "3306";
    private static final String DEFAULT_DB = "common";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "root";

    /** 缺省表清单排除口径：Flyway 历史留档表 + 引擎两张审计表（设计方案 §5.3 元数据表不纳入注解系统） */
    static final Set<String> EXCLUDED_TABLES = Set.of(
            "flyway_schema_history", "common_source_sync_log", "common_source_migration_log");

    /** 公共主键列名（引擎公共列模板内建，任何表不标注） */
    private static final String PRIMARY_KEY_COLUMN = "field_id";

    /** 审计五列（MainModel 公共列模板内建；部分匹配表按业务列标注） */
    static final List<String> AUDIT_COLUMNS = List.of(
            "field_creator_id", "field_create_time", "field_updater_id", "field_update_time", "field_enable");

    /** 主键索引名（differ 聚合层已剔除，此处防御性再滤一次） */
    private static final String PRIMARY_INDEX = "PRIMARY";

    /** 缺省表清单查询：库内全部 BASE TABLE（视图不可标注），升序稳定输出 */
    static final String SQL_TABLE_NAMES = """
            SELECT TABLE_NAME
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
            ORDER BY TABLE_NAME""";

    /** 参数用法摘要（解析违例时随异常信息输出） */
    private static final String USAGE = "用法: java " + SourceSchemaExportTool.class.getName()
            + " [--host h] [--port p] [--db name] [--user u] [--password pwd] [--tables a,b,c]"
            + "（连接参数优先级：命令行 > CS_DB_* 环境变量 > 代码兜底 localhost/3306/common/root/root；"
            + "--tables 缺省 = 库内全部 BASE TABLE，排除 flyway_schema_history 与引擎两张审计表）";

    private SourceSchemaExportTool() {
    }

    /* ==================== main 入口 ==================== */

    /**
     * 工具入口：解析连接参数与表清单 → 只读读取 information_schema → 逐表打印
     * Java 片段底稿
     *
     * @param args 命令行参数（--host / --port / --db / --user / --password / --tables）
     */
    public static void main(String[] args) {
        ExportOptions options = parseArgs(args, System.getenv());
        JdbcTemplate template = new JdbcTemplate(new DriverManagerDataSource(
                jdbcUrl(options.getHost(), options.getPort(), options.getDb()),
                options.getUser(), options.getPassword()));
        Map<String, ActualTable> actualTables = loadActualTables(template);
        List<String> tableNames = resolveTableNames(options, template);
        printHeader(options, tableNames.size());
        for (String tableName : tableNames) {
            ActualTable table = actualTables.get(tableName);
            if (null == table) {
                System.err.println("[跳过] 表在实际库中不存在或非 BASE TABLE: " + tableName);
                continue;
            }
            System.out.println(exportTable(table));
            System.out.println();
        }
    }

    /**
     * 构造 JDBC URL（形态对齐 application.properties 的 datasource.url 参数链）
     *
     * @param host 主机
     * @param port 端口
     * @param db   库名
     * @return JDBC URL
     */
    static String jdbcUrl(String host, String port, String db) {
        return "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false";
    }

    /**
     * information_schema 三源读取（复用 {@link SourceSchemaDiffer#loadActualTables()}，
     * 避免重复实现三源读取）：JdbcTemplate 经 ObjectProvider 匿名实现桥接（工具无
     * Spring 容器，ObjectProvider 仅作为 Differ 构造器注入形态），返回整库 ActualTable
     *
     * @param template 只读 JdbcTemplate（连接指向目标库，DATABASE() 即目标库）
     * @return 实际表模型（key = 表名）
     */
    static Map<String, ActualTable> loadActualTables(JdbcTemplate template) {
        ObjectProvider<JdbcTemplate> provider = new ObjectProvider<>() {
            @Override
            public JdbcTemplate getObject() {
                return template;
            }

            @Override
            public JdbcTemplate getIfAvailable() {
                return template;
            }

            @Override
            public JdbcTemplate getIfUnique() {
                return template;
            }
        };
        return new SourceSchemaDiffer(provider).loadActualTables();
    }

    /**
     * 表清单解析：显式 --tables 去重保序；缺省 = 库内全部 BASE TABLE（升序）并按
     * 排除口径剔除 flyway_schema_history 与引擎两张审计表
     *
     * @param options   解析后的选项
     * @param template  只读 JdbcTemplate
     * @return 表名清单（不可变）
     */
    static List<String> resolveTableNames(ExportOptions options, JdbcTemplate template) {
        if (null != options.getTables()) {
            return List.copyOf(new LinkedHashSet<>(options.getTables()));
        }
        List<String> names = template.queryForList(SQL_TABLE_NAMES, String.class);
        List<String> result = new ArrayList<>(names.size());
        for (String name : names) {
            if (!EXCLUDED_TABLES.contains(name)) {
                result.add(name);
            }
        }
        return List.copyOf(result);
    }

    /**
     * 输出头：说明工具形态（只读）、连接目标与表清单口径（人工核对留痕）
     *
     * @param options   解析后的选项
     * @param tableTotal 待导出表数量
     */
    private static void printHeader(ExportOptions options, int tableTotal) {
        System.out.println("== SourceSchemaExportTool 反向生成工具（对目标库只读：information_schema 三源 + 表清单 SELECT，不写任何数据） ==");
        System.out.println("连接: " + options.getHost() + ":" + options.getPort() + "/" + options.getDb()
                + "（user=" + options.getUser() + "）");
        System.out.println("表清单: " + (null == options.getTables()
                ? "缺省 = 库内全部 BASE TABLE（排除 flyway_schema_history / common_source_sync_log / common_source_migration_log）"
                : "显式指定 " + tableTotal + " 张")
                + "，共 " + tableTotal + " 张");
        System.out.println("输出: 逐表 @TableSource / @ColumnField / @TableIndex Java 片段底稿，人工审阅后贴入实体类");
        System.out.println();
    }

    /* ==================== 参数解析 ==================== */

    /**
     * 解析命令行参数（环境变量取 System.getenv()）
     *
     * @param args 命令行参数
     * @return 解析后的选项
     */
    static ExportOptions parseArgs(String[] args) {
        return parseArgs(args, System.getenv());
    }

    /**
     * 解析命令行参数与环境变量：命令行参数 &gt; 环境变量 CS_DB_* &gt; 代码兜底；
     * 未知参数或缺值抛 IllegalArgumentException（信息含用法摘要）
     *
     * @param args 命令行参数
     * @param env  环境变量视图（测试可注入）
     * @return 解析后的选项（tables 为 null 表示缺省清单）
     */
    static ExportOptions parseArgs(String[] args, Map<String, String> env) {
        String host = null;
        String port = null;
        String db = null;
        String user = null;
        String password = null;
        List<String> tables = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host" -> host = requireValue(args, ++i);
                case "--port" -> port = requireValue(args, ++i);
                case "--db" -> db = requireValue(args, ++i);
                case "--user" -> user = requireValue(args, ++i);
                case "--password" -> password = requireValue(args, ++i);
                case "--tables" -> tables = splitTables(requireValue(args, ++i));
                default -> throw new IllegalArgumentException("未知参数: " + args[i] + "。" + USAGE);
            }
        }
        return new ExportOptions(
                firstNonNull(host, env.get("CS_DB_HOST"), DEFAULT_HOST),
                firstNonNull(port, env.get("CS_DB_PORT"), DEFAULT_PORT),
                firstNonNull(db, env.get("CS_DB_NAME"), DEFAULT_DB),
                firstNonNull(user, env.get("CS_DB_USER"), DEFAULT_USER),
                firstNonNull(password, env.get("CS_DB_PASSWORD"), DEFAULT_PASSWORD),
                tables);
    }

    /**
     * 取参数值（越界即缺值，违例抛 IllegalArgumentException）
     */
    private static String requireValue(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("参数缺少值: " + args[index - 1] + "。" + USAGE);
        }
        return args[index];
    }

    /**
     * --tables 值按逗号拆分：去空白、滤空段；全空视为非法（显式指定却无有效表名）
     */
    private static List<String> splitTables(String value) {
        List<String> tables = Arrays.stream(value.split(","))
                .map(String::trim).filter(name -> !name.isEmpty()).toList();
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("--tables 需至少一个表名: '" + value + "'。" + USAGE);
        }
        return tables;
    }

    /**
     * 逐级取第一个非空值（命令行 &gt; 环境变量 &gt; 兜底）
     */
    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (null != value && !value.isEmpty()) {
                return value;
            }
        }
        throw new IllegalStateException("连接参数兜底值缺失（不可达分支）");
    }

    /* ==================== 片段构建（纯函数，不连库） ==================== */

    /**
     * 单表片段导出：构建结构化片段并渲染为文本
     *
     * @param table 实际表结构（information_schema 归一产物）
     * @return Java 片段文本（底稿）
     */
    public static String exportTable(ActualTable table) {
        return renderTable(buildSnippet(table));
    }

    /**
     * 单表片段构建（纯函数）：公共列剔除 → 基类推断 → 逐列类型/默认值反推 → 索引归集，
     * 生成列与不可表达列只产警示不产 {@code @ColumnField}
     *
     * @param table 实际表结构
     * @return 结构化片段
     */
    static TableSnippet buildSnippet(ActualTable table) {
        Set<String> generatedNames = new LinkedHashSet<>();
        Set<String> presentAudit = new LinkedHashSet<>();
        boolean tableCommentBlank = null == table.getTableComment() || table.getTableComment().isBlank();
        for (ActualColumn column : table.getColumns()) {
            if (isGenerated(column)) {
                generatedNames.add(column.getName());
            }
            if (AUDIT_COLUMNS.contains(column.getName())) {
                presentAudit.add(column.getName());
            }
        }
        // 主键形态（F19-DOC-005 无 field_id 表口径修正）：实际主键列集 = [field_id] → 公共列
        // 模板内建 field_id 主键（现状口径零变化）；其他非空列集 → 无 field_id 主键表，需显式
        // 声明 primaryKeyColumns（引擎不再内建任何列）；空集（无主键表）→ 维持现状口径不扩权
        List<String> actualPrimaryKeys = table.getPrimaryKeyColumns();
        boolean explicitPrimaryKey = !actualPrimaryKeys.isEmpty()
                && !List.of(PRIMARY_KEY_COLUMN).equals(actualPrimaryKeys);
        // 基类推断：审计五列齐备 → MainModel（模板内建五列，全剔除）；完全无审计列 →
        // BaseModel；部分含审计列 → 与两模板均不匹配，建议 BaseModel 并警示，
        // 未内建列按业务列标注（保证 adopt 零 diff 可达）。无 field_id 主键表一律建议
        // BaseModel（primaryKeyColumns 仅 BaseModel 开放，MainModel + 非空声明扫描器 fail-fast）
        boolean mainModel = !explicitPrimaryKey && presentAudit.size() == AUDIT_COLUMNS.size();
        List<String> warnings = new ArrayList<>();
        if (tableCommentBlank) {
            warnings.add("// 警示：表 COMMENT 为空——@TableSource.comment 必填非空，请人工补充");
        }
        List<ColumnSnippet> columns = new ArrayList<>();
        for (ActualColumn column : table.getColumns()) {
            String name = column.getName();
            if (!explicitPrimaryKey && PRIMARY_KEY_COLUMN.equals(name)) {
                // 内建 field_id 主键剔除；无 field_id 主键表不剔（field_id 若实际存在则为业务列）
                continue;
            }
            if (generatedNames.contains(name)) {
                warnings.add("// 生成列：" + name + "（EXTRA=" + nullToBlank(column.getExtra())
                        + "）——引擎暂不支持生成列标注，请人工处置（Wave 3 process 批次涉及）；"
                        + "引用该列的索引需一并人工处置");
                continue;
            }
            if (mainModel && AUDIT_COLUMNS.contains(name)) {
                continue;
            }
            ColumnSnippet snippet = buildColumn(column, warnings);
            if (null != snippet) {
                columns.add(snippet);
            }
        }
        if (mainModel) {
            // MainModel 建议下核对审计列与公共列模板（§3.3）定义，不一致处警示（adopt 将产生 diff）
            for (ActualColumn column : table.getColumns()) {
                if (AUDIT_COLUMNS.contains(column.getName())) {
                    String deviation = auditDeviation(column);
                    if (null != deviation) {
                        warnings.add("// 警示：审计列 " + column.getName() + " 与公共列模板（设计方案 §3.3）不一致（"
                                + deviation + "）——adopt 比对将产生 diff，请人工核对");
                    }
                }
            }
        }
        String suggestedBaseClass;
        boolean baseClassMismatch = false;
        List<String> partialAuditColumns = List.of();
        if (mainModel) {
            suggestedBaseClass = "MainModel";
        } else {
            suggestedBaseClass = "BaseModel";
            baseClassMismatch = !presentAudit.isEmpty();
            if (baseClassMismatch) {
                partialAuditColumns = List.copyOf(presentAudit);
            }
        }
        List<IndexSnippet> indexes = new ArrayList<>();
        for (ActualIndex index : table.getIndexes()) {
            if (PRIMARY_INDEX.equalsIgnoreCase(index.getName())) {
                // 防御：differ 聚合层已剔除主键（公共列模板内建 field_id 由主键表达），不再输出
                continue;
            }
            boolean referencesGenerated = index.getColumns().stream().anyMatch(generatedNames::contains);
            indexes.add(new IndexSnippet(index.getName(), index.getColumns(), index.isUnique(), referencesGenerated));
        }
        return new TableSnippet(table.getName(), nullToBlank(table.getTableComment()), suggestedBaseClass,
                baseClassMismatch, partialAuditColumns,
                explicitPrimaryKey ? List.copyOf(actualPrimaryKeys) : List.of(),
                List.copyOf(warnings), List.copyOf(columns), List.copyOf(indexes));
    }

    /**
     * 单列反推：COLUMN_TYPE → ColumnType + length/decimalLength，COLUMN_DEFAULT/EXTRA →
     * 字面量默认值或函数式默认值，超出注解类型集（含 varchar 不带长度）返回 null 并
     * 记警示；TEXT/JSON 列实测带默认值同样警示（引擎禁止其默认值，adopt 必 fail-fast）
     *
     * @param column   实际列
     * @param warnings 警示收集（追加式）
     * @return 列片段；不可表达为 null
     */
    private static ColumnSnippet buildColumn(ActualColumn column, List<String> warnings) {
        ParsedType parsed = parseType(column.getColumnType());
        if (null == parsed) {
            warnings.add("// 警示：列 " + column.getName() + " 类型 " + nullToBlank(column.getColumnType())
                    + " 超出注解类型集（ColumnType），无法表达，请人工处置");
            return null;
        }
        if ((ColumnType.TEXT == parsed.getType() || ColumnType.JSON == parsed.getType())
                && null != column.getColumnDefault()) {
            warnings.add("// 警示：列 " + column.getName() + " 为 " + parsed.getType()
                    + " 却带默认值 " + column.getColumnDefault() + "——引擎禁止 TEXT/JSON 默认值，请人工处置");
        }
        if (null == column.getComment() || column.getComment().isBlank()) {
            warnings.add("// 警示：列 " + column.getName() + " COMMENT 为空——@ColumnField.comment 必填非空，请人工补充");
        }
        DefaultFunction defaultFunction = DefaultFunction.NONE;
        String defaultValue = null;
        if (column.isOnUpdate()) {
            // EXTRA 含 on update CURRENT_TIMESTAMP：函数式默认值（DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP）
            defaultFunction = DefaultFunction.ON_UPDATE_CURRENT_TIMESTAMP;
        } else if (isCurrentTimestampDefault(column.getColumnDefault())) {
            defaultFunction = DefaultFunction.CURRENT_TIMESTAMP;
        } else if (null != column.getColumnDefault()) {
            // Differ 读取层已归一（SQL NULL/"NULL" → null、外层引号剥离并反转义），
            // 注解 defaultValue 语义即归一值，与 VALIDATE 比对口径一致
            defaultValue = column.getColumnDefault();
        }
        return new ColumnSnippet(column.getName(), nullToBlank(column.getComment()), parsed.getType(),
                parsed.getLength(), parsed.getDecimalLength(), column.isNullable(), defaultValue, defaultFunction,
                toJavaFieldName(column.getName()), javaTypeOf(parsed.getType()));
    }

    /**
     * COLUMN_TYPE 反推为 {@link ParsedType}（type + length + decimalLength）：按括号前
     * 取类型名精确匹配（datetime 与 date 同前缀，须整词区分）；返回 null 表示注解类型集
     * 无法表达（如 longtext、bit(2)、varchar 不带长度）
     *
     * @param columnType COLUMN_TYPE 归一值（小写去空白，如 varchar(100) / decimal(10,2) / bit(1)）
     * @return 解析结果；不可表达为 null
     */
    static ParsedType parseType(String columnType) {
        if (null == columnType || columnType.isBlank()) {
            return null;
        }
        String value = columnType.trim().toLowerCase(Locale.ROOT);
        int paren = value.indexOf('(');
        String token = paren < 0 ? value : value.substring(0, paren);
        String args = paren < 0 ? null
                : value.substring(paren + 1, value.endsWith(")") ? value.length() - 1 : value.length());
        return switch (token) {
            case "varchar" -> {
                Integer length = integerArgs(args, 1, Integer.MAX_VALUE);
                yield null == length ? null : new ParsedType(ColumnType.STRING, length, 0);
            }
            case "text" -> new ParsedType(ColumnType.TEXT, 0, 0);
            case "int" -> new ParsedType(ColumnType.INT, 0, 0);
            case "bigint" -> new ParsedType(ColumnType.BIGINT, 0, 0);
            case "decimal" -> parseDecimal(args);
            case "bit" -> "1".equals(args) ? new ParsedType(ColumnType.BOOLEAN, 0, 0) : null;
            case "date" -> new ParsedType(ColumnType.DATE, 0, 0);
            case "datetime" -> new ParsedType(ColumnType.DATETIME, 0, 0);
            case "timestamp" -> new ParsedType(ColumnType.TIMESTAMP, 0, 0);
            case "json" -> new ParsedType(ColumnType.JSON, 0, 0);
            default -> null;
        };
    }

    /**
     * decimal 精度标度解析：decimal(10,2) → (10, 2)；decimal(10) → (10, 0)；
     * 形态非法（无参/非数字）返回 null（不可表达）
     */
    private static ParsedType parseDecimal(String args) {
        if (null == args) {
            return null;
        }
        String[] parts = args.split(",", 2);
        Integer precision = parseInteger(parts[0].trim());
        if (null == precision || precision < 1) {
            return null;
        }
        int scale = 0;
        if (parts.length == 2) {
            Integer parsed = parseInteger(parts[1].trim());
            if (null == parsed || parsed < 0) {
                return null;
            }
            scale = parsed;
        }
        return new ParsedType(ColumnType.DECIMAL, precision, scale);
    }

    /**
     * 括号参数整数解析（varchar 长度等）：非纯数字返回 null
     */
    private static Integer integerArgs(String args, int min, int max) {
        Integer value = parseInteger(args);
        if (null == value || value < min || value > max) {
            return null;
        }
        return value;
    }

    /**
     * 字符串整数解析（非数字返回 null）
     */
    private static Integer parseInteger(String value) {
        if (null == value || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * COLUMN_DEFAULT 是否 CURRENT_TIMESTAMP 函数默认值（兼容 current_timestamp() 形态，
     * 大小写不敏感）；null 与其它字面量均否
     */
    private static boolean isCurrentTimestampDefault(String columnDefault) {
        if (null == columnDefault) {
            return false;
        }
        String value = columnDefault.trim();
        if (value.endsWith("()")) {
            value = value.substring(0, value.length() - 2);
        }
        return "current_timestamp".equalsIgnoreCase(value);
    }

    /**
     * 生成列识别：EXTRA 剔除 DEFAULT_GENERATED 标记（MySQL 8.0+ 对函数式默认值
     * DEFAULT CURRENT_TIMESTAMP 也带该标记，直接 contains GENERATED 会误判）后仍含
     * GENERATED（STORED GENERATED / VIRTUAL GENERATED）方为生成列
     */
    static boolean isGenerated(ActualColumn column) {
        String extra = column.getExtra();
        if (null == extra) {
            return false;
        }
        String remaining = extra.replaceAll("(?i)default_generated", " ");
        return remaining.toUpperCase(Locale.ROOT).contains("GENERATED");
    }

    /**
     * 审计列与公共列模板（设计方案 §3.3）逐维核对：类型/长度/可空/默认值任一不一致
     * 返回差异描述（「维度：模板 X ↔ 实际 Y」拼接），完全一致返回 null
     *
     * @param column 实际审计列
     * @return 差异描述；一致为 null
     */
    private static String auditDeviation(ActualColumn column) {
        ParsedType parsed = parseType(column.getColumnType());
        List<String> diffs = new ArrayList<>();
        if (null == parsed) {
            return "类型 " + nullToBlank(column.getColumnType()) + " 无法归一为注解类型";
        }
        switch (column.getName()) {
            case "field_creator_id", "field_updater_id" -> {
                if (ColumnType.STRING != parsed.getType() || 32 != parsed.getLength()) {
                    diffs.add("类型/长度：模板 STRING(32) ↔ 实际 " + column.getColumnType());
                }
                if (!column.isNullable()) {
                    diffs.add("可空：模板 NULL ↔ 实际 NOT NULL");
                }
                if (null != column.getColumnDefault()) {
                    diffs.add("默认值：模板 无默认值 ↔ 实际 " + column.getColumnDefault());
                }
            }
            case "field_create_time" -> {
                if (ColumnType.TIMESTAMP != parsed.getType()) {
                    diffs.add("类型：模板 TIMESTAMP ↔ 实际 " + column.getColumnType());
                }
                if (column.isNullable()) {
                    diffs.add("可空：模板 NOT NULL ↔ 实际 NULL");
                }
                if (!isCurrentTimestampDefault(column.getColumnDefault())) {
                    diffs.add("默认值：模板 CURRENT_TIMESTAMP ↔ 实际 "
                            + (null == column.getColumnDefault() ? "无默认值" : column.getColumnDefault()));
                }
                if (column.isOnUpdate()) {
                    diffs.add("ON UPDATE：模板 无 ↔ 实际 有");
                }
            }
            case "field_update_time" -> {
                if (ColumnType.TIMESTAMP != parsed.getType()) {
                    diffs.add("类型：模板 TIMESTAMP ↔ 实际 " + column.getColumnType());
                }
                if (!column.isNullable()) {
                    diffs.add("可空：模板 NULL ↔ 实际 NOT NULL");
                }
                if (!column.isOnUpdate() || !isCurrentTimestampDefault(column.getColumnDefault())) {
                    diffs.add("默认值：模板 CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ↔ 实际 "
                            + (column.isOnUpdate() ? "CURRENT_TIMESTAMP"
                            : null == column.getColumnDefault() ? "无默认值" : column.getColumnDefault()));
                }
            }
            case "field_enable" -> {
                if (ColumnType.BOOLEAN != parsed.getType()) {
                    diffs.add("类型：模板 BIT(1) ↔ 实际 " + column.getColumnType());
                }
                if (column.isNullable()) {
                    diffs.add("可空：模板 NOT NULL ↔ 实际 NULL");
                }
                if (!"b'1'".equals(column.getColumnDefault())) {
                    diffs.add("默认值：模板 b'1' ↔ 实际 "
                            + (null == column.getColumnDefault() ? "无默认值" : column.getColumnDefault()));
                }
            }
            default -> {
                return null;
            }
        }
        return diffs.isEmpty() ? null : String.join("；", diffs);
    }

    /**
     * Java 类型映射（仅供人工参考）：varchar/text→String、int→Integer、bigint→Long、
     * decimal→BigDecimal、bit→Boolean、date→LocalDate、datetime/timestamp→LocalDateTime、
     * json→Object
     */
    static String javaTypeOf(ColumnType type) {
        return switch (type) {
            case STRING, TEXT -> "String";
            case INT -> "Integer";
            case BIGINT -> "Long";
            case DECIMAL -> "BigDecimal";
            case BOOLEAN -> "Boolean";
            case DATE -> "LocalDate";
            case DATETIME, TIMESTAMP -> "LocalDateTime";
            case JSON -> "Object";
        };
    }

    /**
     * 列名 → camelCase Java 字段名：field_default_unique → fieldDefaultUnique
     *
     * @param columnName 列名（field_ 前缀 snake_case）
     * @return Java 字段名
     */
    static String toJavaFieldName(String columnName) {
        return camelize(columnName, false);
    }

    /**
     * 表名 → PascalCase Java 类名（建议名，仅供参考）：sys_process_template →
     * SysProcessTemplate
     *
     * @param tableName 表名（snake_case）
     * @return Java 类名建议
     */
    static String toJavaClassName(String tableName) {
        return camelize(tableName, true);
    }

    /**
     * 下划线分词驼峰化（首词可选首字母大写；连续/首尾下划线防御性跳过）
     */
    private static String camelize(String value, boolean capitalizeFirst) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean upperNext = capitalizeFirst;
        for (char ch : value.toCharArray()) {
            if ('_' == ch) {
                upperNext = true;
                continue;
            }
            builder.append(upperNext ? Character.toUpperCase(ch) : ch);
            upperNext = false;
        }
        return builder.toString();
    }

    /**
     * Java 字符串字面量转义（渲染 comment / defaultValue 用）：\ 与 " 转义、控制字符
     * （换行/回车/制表）转义为转义序列；单引号在 Java 双引号串中无需转义
     *
     * @param text 原文
     * @return 可嵌入 Java 双引号字符串的字面量
     */
    static String escapeJava(String text) {
        StringBuilder builder = new StringBuilder(text.length() + 8);
        for (char ch : text.toCharArray()) {
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String nullToBlank(String value) {
        return null == value ? "" : value;
    }

    /* ==================== 片段渲染 ==================== */

    /**
     * 结构化片段 → Java 片段文本：头注（底稿口径）+ 建议基类行 + 警示行 →
     * 类级 @TableSource（无 field_id 主键表含 primaryKeyColumns 属性、含索引）→
     * 类声明行 → 逐列 @ColumnField + 字段声明行 → 公共列内建说明 → 类结束括号
     *
     * @param snippet 结构化片段
     * @return Java 片段文本
     */
    static String renderTable(TableSnippet snippet) {
        StringBuilder out = new StringBuilder();
        out.append("// ==== 表 ").append(snippet.getTableName())
                .append(" 反向生成底稿（SourceSchemaExportTool 产出，人工审阅后落码；类名/包名仅供参考） ====");
        out.append('\n').append(baseClassLine(snippet));
        for (String warning : snippet.getWarnings()) {
            out.append('\n').append(warning);
        }
        out.append('\n').append("@TableSource(name = \"").append(escapeJava(snippet.getTableName()))
                .append("\", comment = \"").append(escapeJava(snippet.getTableComment())).append('"');
        if (!snippet.getPrimaryKeyColumns().isEmpty()) {
            // 无 field_id 主键表：primaryKeyColumns 作为类级注解属性渲染（按实际主键列序，
            // 数组顺序即 PRIMARY KEY 列序），属性位置对齐既有输出风格（name/comment 之后、indexes 之前）
            out.append(", primaryKeyColumns = ").append(stringArrayLiteral(snippet.getPrimaryKeyColumns()));
        }
        if (!snippet.getIndexes().isEmpty()) {
            out.append(",\n        indexes = {");
            for (int i = 0; i < snippet.getIndexes().size(); i++) {
                IndexSnippet index = snippet.getIndexes().get(i);
                out.append(i == 0 ? "\n" : ",\n");
                if (index.isReferencesGenerated()) {
                    out.append("                // 引用生成列（引擎暂不支持生成列标注），请人工处置\n");
                }
                out.append("                ").append(indexAnnotation(index));
            }
            out.append('}');
        }
        out.append(')');
        out.append("\npublic class ").append(toJavaClassName(snippet.getTableName()))
                .append(" extends ").append(snippet.getSuggestedBaseClass()).append(" {\n");
        for (ColumnSnippet column : snippet.getColumns()) {
            out.append('\n')
                    .append("    ").append(columnAnnotation(column)).append('\n')
                    .append("    private ").append(column.getJavaType()).append(' ')
                    .append(column.getJavaFieldName()).append(";\n");
        }
        out.append('\n').append("    ").append(builtinNote(snippet)).append('\n').append('}');
        return out.toString();
    }

    /**
     * 建议基类注释行：无 field_id 主键表（显式 primaryKeyColumns 声明）/ MainModel /
     * BaseModel（含部分审计列的模板不匹配说明，列明实际部分审计列供人工核对）
     */
    private static String baseClassLine(TableSnippet snippet) {
        if (!snippet.getPrimaryKeyColumns().isEmpty()) {
            // 无 field_id 主键表（F19-DOC-005 口径修正）：不再建议隐含 field_id 的 BaseModel
            // （照搬必产 L1「缺失列 field_id」diff、APPLY 会自动 ADD COLUMN 破坏既有主键语义）
            StringBuilder line = new StringBuilder("// 建议基类：extends BaseModel（表实际主键为非 field_id 列集，")
                    .append("@TableSource 需显式声明 primaryKeyColumns = ")
                    .append(stringArrayLiteral(snippet.getPrimaryKeyColumns()))
                    .append("（数组顺序即 PRIMARY KEY 列序），主键列已按业务列标注，无公共列模板内建列");
            if (snippet.isBaseClassMismatch()) {
                line.append("；另含部分审计列 ").append(String.join("、", snippet.getPartialAuditColumns()))
                        .append("，已按业务列标注，请人工核对基类取舍");
            }
            return line.append('）').toString();
        }
        if ("MainModel".equals(snippet.getSuggestedBaseClass())) {
            return "// 建议基类：extends MainModel（表含审计五列，公共列模板内建 field_id 主键 + 审计五列，无需标注）";
        }
        if (snippet.isBaseClassMismatch()) {
            return "// 建议基类：extends BaseModel（表含部分审计列 " + String.join("、", snippet.getPartialAuditColumns())
                    + "，与 MainModel/BaseModel 公共列模板均不匹配——未内建列已按业务列标注，请人工核对基类取舍）";
        }
        return "// 建议基类：extends BaseModel（表无审计列，公共列模板仅内建 field_id 主键）";
    }

    /**
     * 类尾公共列内建说明（对齐 SysDatasource 手写示例尾注）；无 field_id 主键表无任何
     * 内建列，不输出「field_id 内建」尾注，改注明显式主键声明口径
     */
    private static String builtinNote(TableSnippet snippet) {
        if (!snippet.getPrimaryKeyColumns().isEmpty()) {
            return "// 该表无公共列模板内建列（primaryKeyColumns 显式声明主键，引擎不再内建 field_id，"
                    + "设计方案 §3.3），全部列按业务列标注";
        }
        return "MainModel".equals(snippet.getSuggestedBaseClass())
                ? "// field_id（主键）与审计五列由公共列模板内建（设计方案 §3.3），无需标注"
                : "// field_id（主键）由公共列模板内建（设计方案 §3.3），无需标注";
    }

    /**
     * 单列 @ColumnField 注解文本：属性顺序对齐 SysDatasource 手写风格——name、comment、
     * type、length（仅 STRING/DECIMAL）、decimalLength（仅 &gt;0）、nullable（仅 true）、
     * defaultValue（仅有字面量默认）、defaultFunction（仅函数式默认）
     */
    private static String columnAnnotation(ColumnSnippet column) {
        StringBuilder line = new StringBuilder("@ColumnField(name = \"").append(escapeJava(column.getName()))
                .append("\", comment = \"").append(escapeJava(column.getComment()))
                .append("\", type = ColumnType.").append(column.getType().name());
        if (ColumnType.STRING == column.getType() || ColumnType.DECIMAL == column.getType()) {
            line.append(", length = ").append(column.getLength());
        }
        if (column.getDecimalLength() > 0) {
            line.append(", decimalLength = ").append(column.getDecimalLength());
        }
        if (column.isNullable()) {
            line.append(", nullable = true");
        }
        if (null != column.getDefaultValue()) {
            line.append(", defaultValue = \"").append(escapeJava(column.getDefaultValue())).append('"');
        }
        if (DefaultFunction.NONE != column.getDefaultFunction()) {
            line.append(", defaultFunction = DefaultFunction.").append(column.getDefaultFunction().name());
        }
        return line.append(')').toString();
    }

    /**
     * String[] 属性字面量渲染（primaryKeyColumns 用）：始终数组形态 {@code {"a", "b"}}
     * （单元素亦然，与注解 String[] 属性对应），元素做 Java 转义
     */
    private static String stringArrayLiteral(List<String> values) {
        StringBuilder line = new StringBuilder("{");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                line.append(", ");
            }
            line.append('"').append(escapeJava(values.get(i))).append('"');
        }
        return line.append('}').toString();
    }

    /**
     * 单索引 @TableIndex 注解文本：单列 {@code columns = "field_x"}；多列以注解
     * String[] 属性为准 {@code columns = {"a", "b"}}（组合列按 SEQ_IN_INDEX 序）；
     * unique 仅 true 时输出
     */
    private static String indexAnnotation(IndexSnippet index) {
        StringBuilder line = new StringBuilder("@TableIndex(name = \"").append(escapeJava(index.getName()))
                .append("\", columns = ");
        List<String> columns = index.getColumns();
        if (columns.size() == 1) {
            line.append('"').append(escapeJava(columns.get(0))).append('"');
        } else {
            line.append('{');
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    line.append(", ");
                }
                line.append('"').append(escapeJava(columns.get(i))).append('"');
            }
            line.append('}');
        }
        if (index.isUnique()) {
            line.append(", unique = true");
        }
        return line.append(')').toString();
    }

    /* ==================== 结构化片段模型 ==================== */

    /**
     * 单表结构化片段：renderTable 的输入、等价性断言的比对单元
     * （buildSnippet 纯函数产物，字段均为只读）
     */
    @Getter
    static final class TableSnippet {

        /** 表名 */
        private final String tableName;

        /** 表 COMMENT（null 归空串） */
        private final String tableComment;

        /** 建议基类（MainModel / BaseModel） */
        private final String suggestedBaseClass;

        /** 部分审计列与两模板均不匹配（建议 BaseModel 且未内建列按业务列标注） */
        private final boolean baseClassMismatch;

        /** 部分审计列名清单（baseClassMismatch 时的实际座行，建议行中列明供人工核对） */
        private final List<String> partialAuditColumns;

        /** 显式主键列集（无 field_id 主键表的实际主键列序；空 = 内建 field_id 主键，不渲染 primaryKeyColumns 属性） */
        private final List<String> primaryKeyColumns;

        /** 警示清单（已带 "// " 前缀，逐行渲染） */
        private final List<String> warnings;

        /** 业务列片段（已剔主键/内建审计列/生成列，沿 ORDINAL_POSITION 序） */
        private final List<ColumnSnippet> columns;

        /** 索引片段（主键剔除，索引名序） */
        private final List<IndexSnippet> indexes;

        TableSnippet(String tableName, String tableComment, String suggestedBaseClass, boolean baseClassMismatch,
                     List<String> partialAuditColumns, List<String> primaryKeyColumns, List<String> warnings,
                     List<ColumnSnippet> columns, List<IndexSnippet> indexes) {
            this.tableName = tableName;
            this.tableComment = tableComment;
            this.suggestedBaseClass = suggestedBaseClass;
            this.baseClassMismatch = baseClassMismatch;
            this.partialAuditColumns = partialAuditColumns;
            this.primaryKeyColumns = primaryKeyColumns;
            this.warnings = warnings;
            this.columns = columns;
            this.indexes = indexes;
        }
    }

    /**
     * 单列结构化片段：属性与 {@code @ColumnField} 一一对应
     * （comment/defaultValue 为原文，渲染时才做 Java 转义）
     */
    @Getter
    static final class ColumnSnippet {

        /** 列名 */
        private final String name;

        /** 列 COMMENT（原文） */
        private final String comment;

        /** 反推列类型 */
        private final ColumnType type;

        /** 长度（仅 STRING/DECIMAL 有意义，其余 0） */
        private final int length;

        /** 小数位（仅 DECIMAL，其余 0） */
        private final int decimalLength;

        /** 是否可空 */
        private final boolean nullable;

        /** 字面量默认值（无默认为 null；Differ 归一值，与 VALIDATE 比对口径一致） */
        private final String defaultValue;

        /** 函数式默认值 */
        private final DefaultFunction defaultFunction;

        /** 建议字段名（camelCase） */
        private final String javaFieldName;

        /** 建议 Java 类型（仅供参考） */
        private final String javaType;

        ColumnSnippet(String name, String comment, ColumnType type, int length, int decimalLength,
                      boolean nullable, String defaultValue, DefaultFunction defaultFunction,
                      String javaFieldName, String javaType) {
            this.name = name;
            this.comment = comment;
            this.type = type;
            this.length = length;
            this.decimalLength = decimalLength;
            this.nullable = nullable;
            this.defaultValue = defaultValue;
            this.defaultFunction = defaultFunction;
            this.javaFieldName = javaFieldName;
            this.javaType = javaType;
        }
    }

    /**
     * 单索引结构化片段：属性与 {@code @TableIndex} 一一对应
     */
    @Getter
    static final class IndexSnippet {

        /** 索引名 */
        private final String name;

        /** 索引列（组合索引按 SEQ_IN_INDEX 序） */
        private final List<String> columns;

        /** 是否唯一索引 */
        private final boolean unique;

        /** 是否引用生成列（引擎暂不支持生成列标注，渲染时加警示注释） */
        private final boolean referencesGenerated;

        IndexSnippet(String name, List<String> columns, boolean unique, boolean referencesGenerated) {
            this.name = name;
            this.columns = columns;
            this.unique = unique;
            this.referencesGenerated = referencesGenerated;
        }
    }

    /**
     * COLUMN_TYPE 反推结果（type + length + decimalLength 三元组）
     */
    @Getter
    static final class ParsedType {

        /** 反推列类型 */
        private final ColumnType type;

        /** 长度/精度 */
        private final int length;

        /** 小数位 */
        private final int decimalLength;

        ParsedType(ColumnType type, int length, int decimalLength) {
            this.type = type;
            this.length = length;
            this.decimalLength = decimalLength;
        }
    }

    /**
     * 解析后的连接与表清单选项（tables 为 null 表示缺省清单）
     */
    @Getter
    static final class ExportOptions {

        /** 主机 */
        private final String host;

        /** 端口 */
        private final String port;

        /** 库名 */
        private final String db;

        /** 用户名 */
        private final String user;

        /** 密码 */
        private final String password;

        /** 显式表清单（null = 缺省清单） */
        private final List<String> tables;

        ExportOptions(String host, String port, String db, String user, String password, List<String> tables) {
            this.host = host;
            this.port = port;
            this.db = db;
            this.user = user;
            this.password = password;
            this.tables = tables;
        }
    }
}
