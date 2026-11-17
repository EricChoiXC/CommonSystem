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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 流程模板默认标记补录迁移 JDBC 级校验（守卫式集成测试，真实 MySQL 执行迁移 SQL）
 *
 * @author Eric
 * @date 2026/11/17
 * @description 覆盖任务 F19-BE-007「迁移 SQL 在真实 MySQL 上语义正确且幂等」验收口径：
 *              经 java.sql.DriverManager 直连本机 MySQL（root/root，3306），创建独立临时
 *              schema（绝不触碰 common 开发库，本测试不建立与 common 的任何连接），按
 *              V1_01_007 + V1_01_019 迁移脚本列自建最小 sys_process_template（含
 *              field_default 列与 field_default_unique 生成列唯一键，镜像真实表兜底口径），
 *              造三类测试数据——A 有启用模板且完全无默认（含比最新启用模板更新的停用模板，
 *              验证启用过滤）、B 已有人工默认（含比默认模板更新的启用模板，验证不改动）、
 *              C 无启用模板（验证不改动）——经真实 DriverManager 数据源包装 Spring
 *              JdbcTemplate 注入迁移类执行 migrate()，断言置默认/不改动语义，重复执行
 *              第二次断言全表状态不变（SQL 自身幂等）。
 *              <p>守卫：MySQL 连不上（未监听/凭据不符）时 assumeTrue 跳过，保证其他环境
 *              {@code mvnw test} 不红；不启动整个 Spring 应用（本机 Redis 未监听会阻断
 *              启动链路，全应用空库冷启动验证属 Wave 4 任务 F19-INT-001 职责）。
 *              {@code @AfterAll} 兜底 DROP 临时 schema（@BeforeAll 异常中断同样清理），
 *              ObjectProvider 以 Mockito mock 承载（返回真实 JdbcTemplate，不经 Spring 容器）。</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SysProcessTemplateDefaultBackfillMigrationDbCheckTest {

    /** 独立临时 schema（绝不触碰 common 开发库） */
    private static final String CHECK_SCHEMA = "common_f19be007_check";

    /** 本机 MySQL 连接（无 schema，供建/删临时 schema 与造数） */
    private static final String BOOT_URL =
            "jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";

    /** 临时 schema 连接（迁移类 JdbcTemplate 数据源指向此库） */
    private static final String CHECK_URL = "jdbc:mysql://localhost:3306/" + CHECK_SCHEMA
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";

    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root";

    /** 临时 schema 与最小表（V1_01_007 + V1_01_019 列口径，含生成列唯一键） */
    private static final String TEMPLATE_DDL = """
            CREATE TABLE sys_process_template (
                field_id          VARCHAR(32)   NOT NULL COMMENT '主键ID',
                field_name        VARCHAR(200)  NOT NULL COMMENT '模板名称',
                field_model_name  VARCHAR(200)  NOT NULL COMMENT '所属模块',
                field_code        VARCHAR(50)   DEFAULT NULL COMMENT '模板编号',
                field_version     INT           NOT NULL DEFAULT 1 COMMENT '版本号',
                field_remark      VARCHAR(2000) DEFAULT NULL COMMENT '备注',
                field_creator_id  VARCHAR(32)   DEFAULT NULL COMMENT '创建人',
                field_create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                field_updater_id  VARCHAR(32)   DEFAULT NULL COMMENT '修改人',
                field_update_time TIMESTAMP     NULL DEFAULT NULL COMMENT '修改时间',
                field_enable      BIT(1)        NOT NULL DEFAULT b'1' COMMENT '是否启用',
                field_default     BIT(1)        NULL DEFAULT b'0' COMMENT '是否默认模板（每分类最多一个）',
                field_default_unique VARCHAR(50) GENERATED ALWAYS AS (
                    CASE WHEN field_default = 1 THEN field_model_name ELSE NULL END) STORED
                    COMMENT '默认模板分类唯一键（生成列，field_default=1 时按分类唯一）',
                PRIMARY KEY (field_id),
                UNIQUE KEY uk_sys_process_template_default (field_default_unique)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程模板主表（F19-BE-007 校验专用最小结构）'""";

    /** 共享连接（@AfterAll 清理后关闭） */
    private static Connection connection;

    /** MySQL 可用标记（@BeforeAll 探测，各 @Test 经 assumeTrue 守卫） */
    private static boolean available;

    /** 连接失败原因（skip 报告可读性） */
    private static String unavailableReason;

    /** 迁移实例（真实 JdbcTemplate 注入，migrate() 走真实 MySQL） */
    private static SysProcessTemplateDefaultBackfillMigration migration;

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
            statement.execute(TEMPLATE_DDL);
        } catch (SQLException e) {
            unavailableReason = "临时 schema 初始化失败: " + e.getMessage();
            closeQuietly();
            return;
        }
        try {
            seedTemplates();
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
        migration = new SysProcessTemplateDefaultBackfillMigration(provider);
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

    /** 32 位主键（对齐 field_id VARCHAR(32) 口径） */
    private static String id(String prefix) {
        return String.format("%-32s", prefix).replace(' ', '0');
    }

    /**
     * 造三类测试数据（create_time 显式赋值保证「最新创建」判定确定）：
     * A 有启用模板无默认（a3 为最新启用模板应被置默认；a4 更新但停用不参与）；
     * B 已有人工默认（b1=默认、b2 更新的启用模板，均不改动）；C 无启用模板（均不改动）
     */
    private static void seedTemplates() throws SQLException {
        String sql = "INSERT INTO sys_process_template "
                + "(field_id, field_name, field_model_name, field_version, field_create_time, "
                + "field_enable, field_default) VALUES (?, ?, ?, 1, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            // 分类 A：有启用模板、完全无默认
            insert(ps, id("tpl-a1"), "A 模板一", "biz_check_a", LocalDateTime.of(2026, 11, 17, 10, 0), true, false);
            insert(ps, id("tpl-a2"), "A 模板二", "biz_check_a", LocalDateTime.of(2026, 11, 17, 10, 5), true, false);
            insert(ps, id("tpl-a3"), "A 模板三", "biz_check_a", LocalDateTime.of(2026, 11, 17, 10, 10), true, false);
            insert(ps, id("tpl-a4"), "A 停用模板", "biz_check_a", LocalDateTime.of(2026, 11, 17, 10, 15), false, false);
            // 分类 B：已有人工默认
            insert(ps, id("tpl-b1"), "B 人工默认", "biz_check_b", LocalDateTime.of(2026, 11, 17, 9, 0), true, true);
            insert(ps, id("tpl-b2"), "B 更新启用", "biz_check_b", LocalDateTime.of(2026, 11, 17, 9, 30), true, false);
            // 分类 C：无启用模板
            insert(ps, id("tpl-c1"), "C 停用一", "biz_check_c", LocalDateTime.of(2026, 11, 17, 8, 0), false, false);
            insert(ps, id("tpl-c2"), "C 停用二", "biz_check_c", LocalDateTime.of(2026, 11, 17, 8, 30), false, false);
        }
    }

    private static void insert(PreparedStatement ps, String fieldId, String fieldName, String modelName,
                               LocalDateTime createTime, boolean enable, boolean isDefault) throws SQLException {
        ps.setString(1, fieldId);
        ps.setString(2, fieldName);
        ps.setString(3, modelName);
        ps.setTimestamp(4, Timestamp.valueOf(createTime));
        ps.setBoolean(5, enable);
        ps.setBoolean(6, isDefault);
        ps.executeUpdate();
    }

    /** 按分类查询 field_id → field_default（BIT(1) 转数值便于断言），按 field_id 排序 */
    private static Map<String, Integer> loadDefaultFlags(String modelName) throws SQLException {
        Map<String, Integer> flags = new LinkedHashMap<>();
        String sql = "SELECT field_id, field_default + 0 AS default_flag FROM sys_process_template "
                + "WHERE field_model_name = ? ORDER BY field_id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, modelName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    flags.put(rs.getString(1), rs.getInt(2));
                }
            }
        }
        return flags;
    }

    /** 全表 field_id → field_default 快照（幂等断言用） */
    private static Map<String, Integer> loadAllDefaultFlags() throws SQLException {
        Map<String, Integer> flags = new LinkedHashMap<>();
        String sql = "SELECT field_id, field_default + 0 AS default_flag FROM sys_process_template ORDER BY field_id";
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                flags.put(rs.getString(1), rs.getInt(2));
            }
        }
        return flags;
    }

    /* ==================== 断言 ==================== */

    private static void assertExpectedState() throws SQLException {
        // 分类 A（有启用模板无默认）：最新创建的启用模板 a3 被置默认；更新的停用模板 a4 与其余行不动
        Map<String, Integer> categoryA = loadDefaultFlags("biz_check_a");
        assertEquals(4, categoryA.size(), "分类 A 共 4 行");
        assertEquals(0, categoryA.get(id("tpl-a1")), "a1 非最新不置默认");
        assertEquals(0, categoryA.get(id("tpl-a2")), "a2 非最新不置默认");
        assertEquals(1, categoryA.get(id("tpl-a3")), "a3 最新创建的启用模板被置默认");
        assertEquals(0, categoryA.get(id("tpl-a4")), "a4 停用不参与（启用过滤）");
        // 分类 B（已有人工默认）：完全不被改动（default_cnt = 0 守护条件）
        Map<String, Integer> categoryB = loadDefaultFlags("biz_check_b");
        assertEquals(1, categoryB.get(id("tpl-b1")), "b1 人工默认保持");
        assertEquals(0, categoryB.get(id("tpl-b2")), "b2 更新的启用模板不抢占人工默认");
        // 分类 C（无启用模板）：完全不被改动
        Map<String, Integer> categoryC = loadDefaultFlags("biz_check_c");
        assertEquals(0, categoryC.get(id("tpl-c1")), "c1 停用模板不置默认");
        assertEquals(0, categoryC.get(id("tpl-c2")), "c2 停用模板不置默认");
        // 每分类至多一个默认（生成列唯一键兜底口径的运行期佐证）
        assertEquals(1, categoryA.values().stream().filter(flag -> 1 == flag).count(), "分类 A 恰一个默认");
        assertEquals(1, categoryB.values().stream().filter(flag -> 1 == flag).count(), "分类 B 恰一个默认");
        assertEquals(0, categoryC.values().stream().filter(flag -> 1 == flag).count(), "分类 C 无默认");
    }

    /* ==================== 用例（@Order 保证首轮/幂等轮顺序） ==================== */

    @Test
    @Order(1)
    void firstRunBackfillsOnlyCategoriesWithoutDefault() throws SQLException {
        assumeTrue(available, () -> "本机 MySQL 不可用（" + unavailableReason + "），JDBC 级校验跳过");

        migration.migrate();

        assertExpectedState();
    }

    @Test
    @Order(2)
    void secondRunIsIdempotent() throws SQLException {
        assumeTrue(available, () -> "本机 MySQL 不可用（" + unavailableReason + "），JDBC 级校验跳过");

        // 重复执行第二次：default_cnt = 0 条件不再命中任何行，全表状态不变（SQL 自身幂等）
        Map<String, Integer> before = loadAllDefaultFlags();
        assertTrue(before.values().stream().anyMatch(flag -> 1 == flag), "首轮已产生默认模板");

        migration.migrate();

        Map<String, Integer> after = loadAllDefaultFlags();
        assertEquals(before, after, "重复执行第二次结果不变（幂等）");
        assertExpectedState();
    }
}
