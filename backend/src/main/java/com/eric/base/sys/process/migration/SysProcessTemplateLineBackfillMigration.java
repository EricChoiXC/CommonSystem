package com.eric.base.sys.process.migration;

import com.eric.base.common.source.spi.SourceDataMigration;
import com.eric.base.common.utils.IDUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * 流程模板线性连线生成数据迁移（DM-20261119-002，任务 P17-DB-005）
 *
 * <p>承接《流程v1_7数据库设计分册》§5.3 与 §5.5 的存量数据迁移方案：v1_7 起流转的唯一依据是
 * {@code sys_process_template_line}（{@code field_from_key} → {@code field_to_key}），而存量
 * 模板只有节点序列（改造前按 {@code field_no} 顺延取下一节点形成线性串行链）。本迁移为存量
 * 模板按节点序列生成线性连线 {@code N1→N2}、{@code N2→N3}、…、{@code N(n-1)→Nn}
 * （语义等价于改造前 §6.3-2 前半段的线性串行链），依赖
 * {@link SysProcessTemplateNodeKeyBackfillMigration}（DM-20261119-001，order=101）已回填的
 * {@code field_node_key}。</p>
 *
 * @author Eric
 * @date 2026/11/19
 * @description 背景：本批新增 `sys_process_template_line` 表（分册 §3.1.3，9 列：field_id
 *              主键 + field_template_id / field_from_key / field_to_key / field_line_type /
 *              field_branch_order / field_branch_name / field_condition / field_is_default），
 *              存量模板无任何连线行（分册 §5.3 触发条件：拥有节点但本表无对应连线行）。
 *              <p>规则：对每个模板按 `field_no` 升序（并列按 `field_id` 字典序，与 001 的
 *              tie-break 完全一致）取节点序列 `N1, N2, …, Nn`，生成线性连线
 *              `N1→N2`、`N2→N3`、…、`N(n-1)→Nn`，写入字段口径（分册 §5.3 逐字）：`field_line_type
 *              = 'NORMAL'`、`field_branch_order = 0`、`field_branch_name = NULL`、`field_condition
 *              = NULL`、`field_is_default = b'0'`、`field_id` 由应用侧 `IDUtil.initID()` 生成
 *              （**PreparedStatement 批处理，不使用 MySQL UUID()**——`field_id` 全局唯一性
 *              与应用侧同码同源）。</p>
 *              <p>实现口径：①一条「缺失连线清单」查询（派生表 available 与 nxt 以
 *              `ROW_NUMBER() = rn + 1` 取相邻对，`LEFT JOIN ... WHERE l.field_id IS NULL`
 *              仅取尚无连线行者），再以 `JdbcTemplate.batchUpdate` 逐条插入，插入语句同样带
 *              `NOT EXISTS` 守护——「查询过滤 + 插入守护」构成 SQL 侧双重幂等；②另有一条
 *              WARN 计数查询 `SELECT COUNT(*) FROM sys_process_template_node WHERE
 *              field_node_key IS NULL`，>0 时 WARN 提示 001 未完成（分册 §5.3 边界说明：
 *              节点键为 NULL 则 JOIN 不命中，**记录 WARN 计数、不静默丢数据**，由 001 修复后
 *              重跑补齐）。</p>
 *              <p>幂等性（双保险）：①SQL 自身幂等——`LEFT JOIN ... field_id IS NULL` 只取
 *              尚不存在的连线、插入语句以 `NOT EXISTS` 守护，已生成过的连线不再插入，重复
 *              执行清单命中 0 行、结果不变；②执行器侧幂等——CommonSourceMigrationService 查
 *              common_source_migration_log 存在本迁移 SUCCESS 审计行即跳过（existsSuccess）。</p>
 *              <p>边界（分册 §5.3 边界说明）：单节点模板（仅 `N1`）无相邻对（`rn + 1` 不命中），
 *              不生成连线；空模板（无节点）同样不生成。</p>
 *              <p>注入与执行形态（对齐同包首例 SysProcessTemplateDefaultBackfillMigration）：
 *              JdbcTemplate 经 ObjectProvider 惰性构造注入（测试上下文以 null 注入不阻断 Bean
 *              加载），migrate() 入口判空 WARN 跳过——执行器侧同样判空，双保险；SQL 为类内
 *              private static final String 文本块常量（禁止手工拼串）；执行成功 INFO 记录影响
 *              行数，失败异常上抛由执行器落 FAILED 审计行并阻断启动（分册 §5.1；修复后重跑仅
 *              补插缺失连线，不会重复插入已有连线）。</p>
 */
@Component
public class SysProcessTemplateLineBackfillMigration implements SourceDataMigration {

    private static final Logger log = LoggerFactory.getLogger(SysProcessTemplateLineBackfillMigration.class);

    /**
     * 缺失连线清单查询（分册 §5.3 处理逻辑）：available / nxt 两张同构派生表按
     * `ROW_NUMBER() OVER (PARTITION BY field_template_id ORDER BY field_no ASC, field_id ASC)`
     * 取相邻对（nxt.rn = available.rn + 1，单节点模板天然不命中），LEFT JOIN 连线表后仅保留
     * `l.field_id IS NULL`（尚无连线）的相邻对，即本轮待插入清单；ORDER BY 保证批插入顺序确定
     */
    private static final String MISSING_LINE_SQL = """
            SELECT available.field_template_id AS template_id,
                   available.field_node_key    AS from_key,
                   nxt.field_node_key          AS to_key
            FROM (
                SELECT t.field_template_id, t.field_node_key, t.field_no,
                       ROW_NUMBER() OVER (PARTITION BY t.field_template_id
                                          ORDER BY t.field_no ASC, t.field_id ASC) AS rn
                FROM sys_process_template_node t
                WHERE t.field_node_key IS NOT NULL
            ) available
            JOIN (
                SELECT t.field_template_id, t.field_node_key, t.field_no,
                       ROW_NUMBER() OVER (PARTITION BY t.field_template_id
                                          ORDER BY t.field_no ASC, t.field_id ASC) AS rn
                FROM sys_process_template_node t
                WHERE t.field_node_key IS NOT NULL
            ) nxt ON nxt.field_template_id = available.field_template_id
                 AND nxt.rn = available.rn + 1
            LEFT JOIN sys_process_template_line l
                   ON l.field_template_id = available.field_template_id
                  AND l.field_from_key = available.field_node_key
                  AND l.field_to_key = nxt.field_node_key
            WHERE l.field_id IS NULL
            ORDER BY available.field_template_id ASC, available.rn ASC""";

    /**
     * 线性连线插入语句（分册 §5.3 字段口径逐字）：NOT EXISTS 为 SQL 自身幂等守护（双重：
     * 清单查询已过滤 + 插入再守护），field_line_type='NORMAL'、field_branch_order=0、
     * field_branch_name/field_condition 为 NULL、field_is_default=b'0'；field_id 由应用侧
     * IDUtil.initID() 传入（禁用 MySQL UUID()，保证与应用侧同码同源）
     */
    private static final String INSERT_LINE_SQL = """
            INSERT INTO sys_process_template_line
                (field_id, field_template_id, field_from_key, field_to_key, field_line_type,
                 field_branch_order, field_branch_name, field_condition, field_is_default)
            SELECT ?, ?, ?, ?, 'NORMAL', 0, NULL, NULL, b'0'
            WHERE NOT EXISTS (
                SELECT 1 FROM sys_process_template_line l
                WHERE l.field_template_id = ?
                  AND l.field_from_key = ?
                  AND l.field_to_key = ?
            )""";

    /**
     * 孤儿节点计数查询（分册 §5.3 边界说明）：模板节点 `field_node_key IS NULL` 的存量行数，
     * &gt;0 表示 001（DM-20261119-001）未完成，本迁移 JOIN 不命中——WARN 计数提示、不静默丢数据
     */
    private static final String ORPHAN_NODE_COUNT_SQL =
            "SELECT COUNT(*) FROM sys_process_template_node WHERE field_node_key IS NULL";

    /** JDBC 模板（ObjectProvider 惰性注入；测试上下文可为 null，migrate() 入口判空跳过） */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造注入：JdbcTemplate 经 ObjectProvider 惰性形态获取（对齐 CommonSourceMigrationService /
     * SysProcessTemplateDefaultBackfillMigration 先例——测试上下文无数据源时以 null 注入不阻断加载）
     *
     * @param jdbcTemplateProvider JdbcTemplate 提供者
     */
    public SysProcessTemplateLineBackfillMigration(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    /**
     * 迁移编码：DM-20261119-002（分册 §5.5 迁移登记表；DM-yyyyMMdd-nnn 全匹配，
     * 严禁复用已 SUCCESS 的 code）
     *
     * @return 迁移编码
     */
    @Override
    public String code() {
        return "DM-20261119-002";
    }

    /**
     * 迁移中文名（分册 §5.5 迁移登记表逐字一致，审计与排障用）
     *
     * @return 迁移中文名
     */
    @Override
    public String name() {
        return "流程模板线性连线生成（模板连线 backfill）";
    }

    /**
     * 执行顺序：102（分册 §5.1 order 约定；须晚于 101 的节点键回填——依赖其产出的
     * `field_node_key` 才能取相邻对）
     *
     * @return 执行顺序号
     */
    @Override
    public int order() {
        return 102;
    }

    /**
     * 迁移逻辑（分册 §5.3）：JdbcTemplate 判空（排除数据源的测试上下文）WARN 跳过——执行器侧
     * 同样判空，双保险；否则 1 先计数 WARN（模板节点键仍为 NULL 的行数，&gt;0 提示 001 未完成、
     * 重跑补齐）；2 取缺失连线清单，逐条经 PreparedStatement 批处理插入（field_id 由
     * IDUtil.initID() 生成，插入语句带 NOT EXISTS 守护）；3 INFO 记录清单行数与实际影响行数。
     * 任一语句失败异常上抛，由执行器落 FAILED 审计行并阻断启动（宁阻断不半迁）
     */
    @Override
    public void migrate() {
        if (null == jdbcTemplate) {
            log.warn("流程模板线性连线生成跳过：Spring JdbcTemplate 不可用（数据源未装配，"
                    + "如排除数据源的测试上下文），迁移不执行");
            return;
        }
        // 1. 边界说明（§5.3）：仍为 NULL 的模板节点本轮 JOIN 不命中——WARN 计数提示，不静默丢数据
        Integer orphanNodes = jdbcTemplate.queryForObject(ORPHAN_NODE_COUNT_SQL, Integer.class);
        if (null != orphanNodes && orphanNodes > 0) {
            log.warn("流程模板线性连线生成警告 ({}): 存在 {} 个 field_node_key 仍为 NULL 的模板节点，"
                            + "这些节点本轮不参与连线生成（DM-20261119-001 未完成或失败），"
                            + "请修复 001 后重跑补齐", code(), orphanNodes);
        }
        // 2. 缺失连线清单（LEFT JOIN ... field_id IS NULL 过滤，已生成过的连线不在清单内）
        List<Object[]> batchArgs = new ArrayList<>();
        jdbcTemplate.query(MISSING_LINE_SQL, (ResultSet rs) -> {
            while (rs.next()) {
                String templateId = rs.getString("template_id");
                String fromKey = rs.getString("from_key");
                String toKey = rs.getString("to_key");
                batchArgs.add(new Object[]{IDUtil.initID(), templateId, fromKey, toKey,
                        templateId, fromKey, toKey});
            }
            return null;
        });
        // 3. 批处理插入（PreparedStatement；插入语句自带 NOT EXISTS 守护，构成 SQL 侧双重幂等）
        int inserted = 0;
        if (!batchArgs.isEmpty()) {
            int[] rows = jdbcTemplate.batchUpdate(INSERT_LINE_SQL, batchArgs);
            for (int row : rows) {
                inserted += row;
            }
        }
        log.info("流程模板线性连线生成完成 ({}): 缺失连线清单 {} 条，实际插入 {} 行"
                        + "（清单 0 条=各模板连线已齐备，SQL 自身幂等）",
                code(), batchArgs.size(), inserted);
    }
}
