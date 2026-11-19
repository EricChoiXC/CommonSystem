package com.eric.base.sys.process.migration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 流程存量数据迁移 JDBC 级校验（守卫式集成测试，真实 MySQL 执行三个迁移 SQL）
 *
 * @author Eric
 * @date 2026/11/19
 * @description 覆盖任务 P17-DB-005「迁移后旧模板/在途实例在引擎下流转等价且幂等」验收口径
 *              （分册 §5.2 / §5.3 / §5.4）：经 java.sql.DriverManager 直连本机 MySQL
 *              （root/root，3306），创建独立临时 schema（绝不触碰 common 开发库），按分册
 *              §3.1.3 / §3.2.3 列口径自建 4 张最小表（模板节点 + 模板连线 + 实例节点 +
 *              实例连线，含本批新增的 field_node_key 列），造数覆盖：
 *              <ul>
 *              <li>模板 A：3 节点（field_no 1/2/3）——验证 N1..N3 顺序回填与 N1→N2、N2→N3 连线；</li>
 *              <li>模板 B：3 节点**同 field_no（并列 5）**但 field_id 字典序递增——验证确定性
 *              tie-break（N1/N2/N3 严格按 field_id 字典序，不依赖存储引擎返回序）；</li>
 *              <li>模板 C：单节点——验证不生成连线（无相邻对）；</li>
 *              <li>实例 E1：3 节点（field_node_no 1/2/3）——验证实例侧 N1..N3 与实例连线快照；</li>
 *              <li>实例 E2：单节点——验证不生成连线快照（全状态覆盖下也不误生成）。</li>
 *              </ul>
 *              三个迁移（101/102/103）按 order 升序注入真实 JdbcTemplate 后连续执行两轮，
 *              第二轮断言全表状态逐行不变（SQL 自身幂等），并对「连线数 = 节点数 − 1」
 *              「字段口径 NORMAL / 0 / b'0' / NULL」逐项断言。
 *              <p>守卫：MySQL 连不上（未监听/凭据不符）时 assumeTrue 跳过，保证其他环境
 *              {@code mvnw test} 不红；不启动整个 Spring 应用（本机 Redis 未监听会阻断启动
 *              链路，dev 库 APPLY/VALIDATE 复跑属 Wave 1 集成验证 P17-INT-001 职责）。
 *              {@code @AfterAll} 兜底 DROP 临时 schema（@BeforeAll 异常中断同样清理），
 *              ObjectProvider 以 Mockito mock 承载（返回真实 JdbcTemplate，不经 Spring 容器）。</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SysProcessLineBackfillMigrationDbCheckTest {

    /** 独立临时 schema（绝不触碰 common 开发库） */
    private static final String CHECK_SCHEMA = "common_p17db005_check";

    /** 本机 MySQL 连接（无 schema，供建/删临时 schema 与造数） */
    private static final String BOOT_URL =
            "jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";

    /** 临时 schema 连接（迁移类 JdbcTemplate 数据源指向此库） */
    private static final String CHECK_URL = "jdbc:mysql://localhost:3306/" + CHECK_SCHEMA
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";

    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root";

    /** 模板节点表（分册 §3.1.3 的表 1 侧；field_node_key 为本批新增可空列） */
    private static final String TEMPLATE_NODE_DDL = """
            CREATE TABLE sys_process_template_node (
                field_id          VARCHAR(32)  NOT NULL COMMENT '主键ID',
                field_template_id VARCHAR(32)  NOT NULL COMMENT '模板id',
                field_no          INT          NOT NULL COMMENT '节点编号',
                field_node_name   VARCHAR(200) NOT NULL COMMENT '节点名称',
                field_node_type   VARCHAR(20)  NOT NULL COMMENT '节点类型',
                field_node_config JSON         DEFAULT NULL COMMENT '节点配置(JSON对象)',
                field_node_key    VARCHAR(50)  DEFAULT NULL COMMENT '节点业务键(模板内唯一)',
                PRIMARY KEY (field_id),
                KEY idx_template_node_template (field_template_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程模板节点表（P17-DB-005 校验专用最小结构）'""";

    /** 模板连线表（分册 §3.1.3 逐列） */
    private static final String TEMPLATE_LINE_DDL = """
            CREATE TABLE sys_process_template_line (
                field_id           VARCHAR(32)  NOT NULL COMMENT '主键ID',
                field_template_id  VARCHAR(32)  NOT NULL COMMENT '模板id',
                field_from_key     VARCHAR(50)  NOT NULL COMMENT '起始节点键',
                field_to_key       VARCHAR(50)  NOT NULL COMMENT '目标节点键',
                field_line_type    VARCHAR(20)  NOT NULL COMMENT '连线类型',
                field_branch_order INT          NOT NULL COMMENT '分支序号',
                field_branch_name  VARCHAR(200) DEFAULT NULL COMMENT '分支名称',
                field_condition    TEXT         DEFAULT NULL COMMENT '分支条件公式',
                field_is_default   BIT(1)       NOT NULL COMMENT '是否默认分支',
                PRIMARY KEY (field_id),
                KEY idx_template_line_template (field_template_id),
                KEY idx_template_line_from (field_template_id, field_from_key)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程模板连线表（P17-DB-005 校验专用最小结构）'""";

    /** 实例节点表（分册 §4.3 / §4.4 的表 侧；field_node_key 为本批新增可空列） */
    private static final String EXAMPLE_NODE_DDL = """
            CREATE TABLE sys_process_example_node (
                field_id          VARCHAR(32)  NOT NULL COMMENT '主键ID',
                field_example_id  VARCHAR(32)  NOT NULL COMMENT '流程实例id',
                field_node_no     INT          NOT NULL COMMENT '节点编号',
                field_node_name   VARCHAR(200) NOT NULL COMMENT '节点名称',
                field_node_type   VARCHAR(20)  NOT NULL COMMENT '节点类型',
                field_node_config JSON         DEFAULT NULL COMMENT '节点配置(JSON对象)',
                field_status      VARCHAR(20)  NOT NULL DEFAULT 'WAITING' COMMENT '节点状态',
                field_node_key    VARCHAR(50)  DEFAULT NULL COMMENT '节点业务键(模板节点快照)',
                field_runtime     JSON         DEFAULT NULL COMMENT '节点运行态',
                PRIMARY KEY (field_id),
                KEY idx_example_node_example (field_example_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例节点表（P17-DB-005 校验专用最小结构）'""";

    /** 实例连线快照表（分册 §3.2.3 逐列） */
    private static final String EXAMPLE_LINE_DDL = """
            CREATE TABLE sys_process_example_line (
                field_id           VARCHAR(32)  NOT NULL COMMENT '主键ID',
                field_example_id   VARCHAR(32)  NOT NULL COMMENT '流程实例id',
                field_from_key     VARCHAR(50)  NOT NULL COMMENT '起始节点键',
                field_to_key       VARCHAR(50)  NOT NULL COMMENT '目标节点键',
                field_line_type    VARCHAR(20)  NOT NULL COMMENT '连线类型',
                field_branch_order INT          NOT NULL COMMENT '分支序号',
                field_branch_name  VARCHAR(200) DEFAULT NULL COMMENT '分支名称',
                field_condition    TEXT         DEFAULT NULL COMMENT '分支条件公式',
                field_is_default   BIT(1)       NOT NULL COMMENT '是否默认分支',
                PRIMARY KEY (field_id),
                KEY idx_example_line_example (field_example_id),
                KEY idx_example_line_from (field_example_id, field_from_key)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例连线快照表（P17-DB-005 校验专用最小结构）'""";

    /** 共享连接（@AfterAll 清理后关闭） */
    private static Connection connection;

    /** MySQL 可用标记（@BeforeAll 探测，各 @Test 经 assumeTrue 守卫） */
    private static boolean available;

    /** 连接失败原因（skip 报告可读性） */
    private static String unavailableReason;

    /** 三个迁移（真实 JdbcTemplate 注入，migrate() 走真实 MySQL） */
    private static SysProcessTemplateNodeKeyBackfillMigration nodeKeyMigration;
    private static SysProcessTemplateLineBackfillMigration templateLineMigration;
    private static SysProcessExampleLineBackfillMigration exampleLineMigration;

    /* ==================== 生命周期 ==================== */

    @BeforeAll
    static void setUpSchemaAndData() {
        available = false;
        try {
            connection = DriverManager.getConnection(BOOT_URL, DB_USER, DB_PASSWORD);
        } catch (SQLException e) {
            unavailableReason = e.getMessage();
            return;
        }
        try (Statement statement = connection.createStatement()) {
            // 独立临时 schema：先兜底清理再新建，绝不触碰 common 开发库
            statement.execute("DROP DATABASE IF EXISTS " + CHECK_SCHEMA);
            statement.execute("CREATE DATABASE " + CHECK_SCHEMA + " DEFAULT CHARACTER SET utf8mb4");
            statement.execute("USE " + CHECK_SCHEMA);
            statement.execute(EXAMPLE_LINE_DDL);
            statement.execute(TEMPLATE_LINE_DDL);
            statement.execute(EXAMPLE_NODE_DDL);
            statement.execute(TEMPLATE_NODE_DDL);
        } catch (SQLException e) {
            unavailableReason = "临时 schema 初始化失败: " + e.getMessage();
            closeQuietly();
            return;
        }
        try {
            seedData();
        } catch (SQLException e) {
            unavailableReason = "测试数据造数失败: " + e.getMessage();
            closeQuietly();
            return;
        }
        // 迁移实例：真实 DriverManager 数据源包装 JdbcTemplate（ObjectProvider 以 mock 注入）
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(CHECK_URL, DB_USER, DB_PASSWORD));
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        nodeKeyMigration = new SysProcessTemplateNodeKeyBackfillMigration(provider);
        templateLineMigration = new SysProcessTemplateLineBackfillMigration(provider);
        exampleLineMigration = new SysProcessExampleLineBackfillMigration(provider);
        available = true;
    }

    @AfterAll
    static void dropSchemaAndClose() throws SQLException {
        if (null != connection) {
            try (Statement statement = connection.createStatement()) {
                // 兜底 DROP IF EXISTS：确保测试结束后临时 schema 必被清理
                statement.execute("DROP DATABASE IF EXISTS " + CHECK_SCHEMA);
            }
            closeQuietly();
        }
    }

    private static void closeQuietly() {
        if (null != connection) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // 关闭失败不影响清理语义
            }
            connection = null;
        }
    }

    /* ==================== 测试数据 ==================== */

    /** 32 位主键（对齐 field_id VARCHAR(32) 口径；字典序与传入顺序一致便于 tie-break 断言） */
    private static String id(String prefix) {
        return String.format("%-32s", prefix).replace(' ', '0');
    }

    /**
     * 造数：模板 A（3 节点，field_no 1/2/3）、模板 B（3 节点同 field_no=5，field_id 字典序递增）、
     * 模板 C（单节点）；实例 E1（3 节点）、实例 E2（单节点）
     */
    private static void seedData() throws SQLException {
        String templateNodeSql = "INSERT INTO sys_process_template_node "
                + "(field_id, field_template_id, field_no, field_node_name, field_node_type, field_node_key) "
                + "VALUES (?, ?, ?, ?, ?, NULL)";
        try (PreparedStatement ps = connection.prepareStatement(templateNodeSql)) {
            // 模板 A：正常顺序
            insertTemplateNode(ps, "tplA-node1", "tpl-a", 1, "开始", "START");
            insertTemplateNode(ps, "tplA-node2", "tpl-a", 2, "起草", "DRAFT");
            insertTemplateNode(ps, "tplA-node3", "tpl-a", 3, "结束", "END");
            // 模板 B：三节点同 field_no（并列），field_id 字典序 tplB-node1 < tplB-node2 < tplB-node3
            insertTemplateNode(ps, "tplB-node1", "tpl-b", 5, "开始", "START");
            insertTemplateNode(ps, "tplB-node2", "tpl-b", 5, "审批", "APPROVE");
            insertTemplateNode(ps, "tplB-node3", "tpl-b", 5, "结束", "END");
            // 模板 C：单节点（不生成连线）
            insertTemplateNode(ps, "tplC-node1", "tpl-c", 1, "开始", "START");
        }
        String exampleNodeSql = "INSERT INTO sys_process_example_node "
                + "(field_id, field_example_id, field_node_no, field_node_name, field_node_type, "
                + "field_status, field_node_key) VALUES (?, ?, ?, ?, ?, ?, NULL)";
        try (PreparedStatement ps = connection.prepareStatement(exampleNodeSql)) {
            // 实例 E1：在途实例（PROCESSING）
            insertExampleNode(ps, "exE1-node1", "ex-e1", 1, "开始", "START", "FINISH");
            insertExampleNode(ps, "exE1-node2", "ex-e1", 2, "起草", "DRAFT", "FINISH");
            insertExampleNode(ps, "exE1-node3", "ex-e1", 3, "结束", "END", "WAITING");
            // 实例 E2：历史实例（FINISH），单节点
            insertExampleNode(ps, "exE2-node1", "ex-e2", 1, "开始", "START", "FINISH");
        }
    }

    private static void insertTemplateNode(PreparedStatement ps, String fieldId, String templateId,
                                           int no, String name, String type) throws SQLException {
        ps.setString(1, id(fieldId));
        ps.setString(2, id(templateId));
        ps.setInt(3, no);
        ps.setString(4, name);
        ps.setString(5, type);
        ps.executeUpdate();
    }

    private static void insertExampleNode(PreparedStatement ps, String fieldId, String exampleId,
                                          int no, String name, String type, String status) throws SQLException {
        ps.setString(1, id(fieldId));
        ps.setString(2, id(exampleId));
        ps.setInt(3, no);
        ps.setString(4, name);
        ps.setString(5, type);
        ps.setString(6, status);
        ps.executeUpdate();
    }

    /* ==================== 查询辅助 ==================== */

    /** 模板节点 field_id → field_node_key（按 field_id 排序，便于逐行幂等比对） */
    private static Map<String, String> templateNodeKeys() throws SQLException {
        return queryKeys("SELECT field_id, field_node_key FROM sys_process_template_node ORDER BY field_id");
    }

    /** 实例节点 field_id → field_node_key */
    private static Map<String, String> exampleNodeKeys() throws SQLException {
        return queryKeys("SELECT field_id, field_node_key FROM sys_process_example_node ORDER BY field_id");
    }

    private static Map<String, String> queryKeys(String sql) throws SQLException {
        Map<String, String> keys = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                keys.put(rs.getString(1), rs.getString(2));
            }
        }
        return keys;
    }

    /** 连线快照「起始键 → 目标键」清单（按起始键排序） */
    private static Map<String, String> queryLines(String table, String ownerColumn, String ownerId)
            throws SQLException {
        Map<String, String> lines = new LinkedHashMap<>();
        String sql = "SELECT field_from_key, field_to_key FROM " + table
                + " WHERE " + ownerColumn + " = ? ORDER BY field_from_key";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id(ownerId));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return lines;
    }

    /** 连线字段口径断言辅助（type/branch_order/is_default 全表一致） */
    private static void assertLineSemantics(String table) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + table
                + " WHERE field_line_type <> 'NORMAL' OR field_branch_order <> 0"
                + " OR field_is_default <> b'0' OR field_branch_name IS NOT NULL"
                + " OR field_condition IS NOT NULL";
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), table + " 连线字段口径须为 NORMAL / 0 / b'0' / NULL");
        }
    }

    private static int count(String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    /** 全表快照（两轮幂等比对用） */
    private static List<String> fullSnapshot() throws SQLException {
        List<String> snapshot = new ArrayList<>();
        snapshot.add("template_node=" + templateNodeKeys());
        snapshot.add("example_node=" + exampleNodeKeys());
        for (String table : List.of("sys_process_template_line", "sys_process_example_line")) {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT field_id, field_from_key, field_to_key, field_line_type,"
                         + " field_branch_order, field_is_default + 0 FROM " + table
                         + " ORDER BY field_from_key, field_to_key")) {
                while (rs.next()) {
                    snapshot.add(table + "|" + rs.getString(2) + "->" + rs.getString(3)
                            + "|" + rs.getString(4) + "|" + rs.getInt(5) + "|" + rs.getInt(6));
                }
            }
        }
        return snapshot;
    }

    /** 三个迁移按 order 升序（101 → 102 → 103）执行一轮 */
    private static void migrateAll() {
        nodeKeyMigration.migrate();
        templateLineMigration.migrate();
        exampleLineMigration.migrate();
    }

    /* ==================== 断言 ==================== */

    private static void assertMigratedState() throws SQLException {
        // 模板节点：按 field_no 升序回填 N1..Nn（并列按 field_id 字典序）
        Map<String, String> templateKeys = templateNodeKeys();
        assertEquals("N1", templateKeys.get(id("tplA-node1")), "模板 A 首节点 N1");
        assertEquals("N2", templateKeys.get(id("tplA-node2")), "模板 A 次节点 N2");
        assertEquals("N3", templateKeys.get(id("tplA-node3")), "模板 A 末节点 N3");
        // 模板 B：同 field_no 并列 → 严格按 field_id 字典序（确定性 tie-break）
        assertEquals("N1", templateKeys.get(id("tplB-node1")), "并列行按 field_id 字典序取 N1");
        assertEquals("N2", templateKeys.get(id("tplB-node2")), "并列行按 field_id 字典序取 N2");
        assertEquals("N3", templateKeys.get(id("tplB-node3")), "并列行按 field_id 字典序取 N3");
        assertEquals("N1", templateKeys.get(id("tplC-node1")), "单节点模板取 N1");
        assertTrue(templateKeys.values().stream().noneMatch(java.util.Objects::isNull),
                "回填后不应再有 NULL 模板节点键");

        // 模板连线：3 节点模板 2 条、单节点模板 0 条（连线数 = 节点数 − 1）
        Map<String, String> templateLinesA = queryLines("sys_process_template_line", "field_template_id", "tpl-a");
        assertEquals(Map.of("N1", "N2", "N2", "N3"), templateLinesA, "模板 A 线性连线 N1→N2→N3");
        Map<String, String> templateLinesB = queryLines("sys_process_template_line", "field_template_id", "tpl-b");
        assertEquals(Map.of("N1", "N2", "N2", "N3"), templateLinesB, "模板 B 线性连线（并列 tie-break 后同序）");
        assertTrue(queryLines("sys_process_template_line", "field_template_id", "tpl-c").isEmpty(),
                "单节点模板不生成连线（无相邻对）");
        assertEquals(4, count("sys_process_template_line"), "模板连线合计 4 条（2 + 2 + 0）");
        assertLineSemantics("sys_process_template_line");

        // 实例节点与连线快照（全状态覆盖：在途 PROCESSING + 历史 FINISH 均处理）
        Map<String, String> exampleKeys = exampleNodeKeys();
        assertEquals("N1", exampleKeys.get(id("exE1-node1")), "实例 E1 首节点 N1");
        assertEquals("N2", exampleKeys.get(id("exE1-node2")), "实例 E1 次节点 N2");
        assertEquals("N3", exampleKeys.get(id("exE1-node3")), "实例 E1 末节点 N3");
        assertEquals("N1", exampleKeys.get(id("exE2-node1")), "实例 E2（历史实例）单节点 N1");
        assertTrue(exampleKeys.values().stream().noneMatch(java.util.Objects::isNull),
                "回填后不应再有 NULL 实例节点键");

        Map<String, String> exampleLinesE1 = queryLines("sys_process_example_line", "field_example_id", "ex-e1");
        assertEquals(Map.of("N1", "N2", "N2", "N3"), exampleLinesE1, "实例 E1 连线快照 N1→N2→N3");
        assertTrue(queryLines("sys_process_example_line", "field_example_id", "ex-e2").isEmpty(),
                "单节点实例不生成连线快照");
        assertEquals(2, count("sys_process_example_line"), "实例连线快照合计 2 条");
        assertLineSemantics("sys_process_example_line");

        // 实例维度完整（分册 §5.4 验收口径）：逐实例「节点键非空数 = 节点数、连线数 = 节点数 − 1（单节点为 0）」
        assertEquals(count("sys_process_example_node"), exampleKeys.size(), "实例节点键全部非空");
        assertEquals(2, count("sys_process_example_line"),
                "实例连线数 = Σ(节点数 − 1) = E1(3−1) + E2(1−1) = 2");
    }

    /* ==================== 用例（@Order 保证首轮/幂等轮顺序） ==================== */

    @Test
    @Order(1)
    void firstRunBackfillsNodeKeysAndLinearLines() throws SQLException {
        assumeTrue(available, () -> "本机 MySQL 不可用（" + unavailableReason + "），JDBC 级校验跳过");

        migrateAll();

        assertMigratedState();
    }

    @Test
    @Order(2)
    void secondRunIsIdempotent() throws SQLException {
        assumeTrue(available, () -> "本机 MySQL 不可用（" + unavailableReason + "），JDBC 级校验跳过");

        List<String> before = fullSnapshot();
        assertTrue(before.size() > 4, "首轮已产生连线行");

        // 重复执行第二轮：守卫条件（field_node_key IS NULL / NOT EXISTS）不再命中，结果逐行不变
        migrateAll();

        assertEquals(before, fullSnapshot(), "重复执行第二轮结果逐行不变（SQL 自身幂等）");
        assertMigratedState();
    }
}
