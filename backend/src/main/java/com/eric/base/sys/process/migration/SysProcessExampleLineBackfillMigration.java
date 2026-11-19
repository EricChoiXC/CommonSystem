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
 * 流程实例节点业务键回填与连线快照生成数据迁移（DM-20261119-003，任务 P17-DB-005）
 *
 * <p>承接《流程v1_7数据库设计分册》§5.4 与 §5.5 的存量数据迁移方案（在途实例兼容，覆盖风险
 * R06）：v1_7 起实例侧流转只读实例快照 {@code sys_process_example_node} +
 * {@code sys_process_example_line}（不再回查模板），而存量实例只有节点行、无连线快照。本迁移
 * 分两步补齐：①按 {@code field_node_no} 顺序为实例节点回填 {@code field_node_key = N1..Nn}；
 * ②按回填后的节点键序列生成线性连线快照写入 {@code sys_process_example_line}。</p>
 *
 * @author Eric
 * @date 2026/11/19
 * @description 背景：`sys_process_example_node.field_node_key` 为本批新增列（分册 §4.3 变更 2，
 *              可空），`sys_process_example_line` 为本批新表（分册 §3.2.3，与模板连线表 §3.1.3
 *              同构，`field_template_id` → `field_example_id`）；存量实例（含**在途**：
 *              PROCESSING / PAUSE / RETURN，也覆盖 DRAFT / FINISH / ABANDON 历史实例以保图
 *              数据完整）其节点键为 NULL 且无连线行（分册 §5.4 触发条件）。
 *              <p>处理逻辑（分册 §5.4 分两步）：**①实例节点键回填**——对每个实例（按
 *              `field_example_id` 分组）按 `field_node_no` 升序、**并列按 `field_id` 字典序**
 *              回填 `field_node_key = N1..Nn`（SQL 形同 001，表换 `sys_process_example_node`、
 *              排序键换 `field_node_no`）；**②实例连线快照生成**——按回填后的节点键序列生成
 *              线性连线写入 `sys_process_example_line`（字段口径同 002/§5.3：`field_line_type
 *              = 'NORMAL'`、`field_branch_order = 0`、`field_branch_name = NULL`、
 *              `field_condition = NULL`、`field_is_default = b'0'`、`field_id` 由应用侧
 *              `IDUtil.initID()` 生成，`field_example_id` 替代 `field_template_id`）。
 *              **全状态覆盖**：不按实例状态过滤（分册 §5.4 覆盖风险 R06 的显式处置）。</p>
 *              <p>幂等性（双保险）：①SQL 自身幂等——步骤①回填语句恒带外层 `WHERE
 *              field_node_key IS NULL` 守护，步骤②清单查询以 `LEFT JOIN ... field_id IS NULL`
 *              过滤已存在连线、插入语句再以 `NOT EXISTS` 守护（SQL 侧双重幂等），重复执行两步
 *              均命中 0 行、结果不变；②执行器侧幂等——CommonSourceMigrationService 查
 *              common_source_migration_log 存在本迁移 SUCCESS 审计行即跳过（existsSuccess）。</p>
 *              <p>失败与阻断（分册 §5.4）：任一步失败 → 抛异常 → FAILED 审计行 + 阻断启动——
 *              在途实例图数据不全会导致流转中断，**宁阻断不半迁**（覆盖风险 R06）。</p>
 *              <p>注入与执行形态（对齐同包首例 SysProcessTemplateDefaultBackfillMigration）：
 *              JdbcTemplate 经 ObjectProvider 惰性构造注入（测试上下文以 null 注入不阻断 Bean
 *              加载），migrate() 入口判空 WARN 跳过——执行器侧同样判空，双保险；SQL 为类内
 *              private static final String 文本块常量（禁止手工拼串）；执行成功 INFO 记录两步
 *              影响行数，失败异常上抛由执行器落 FAILED 审计行并阻断启动。</p>
 */
@Component
public class SysProcessExampleLineBackfillMigration implements SourceDataMigration {

    private static final Logger log = LoggerFactory.getLogger(SysProcessExampleLineBackfillMigration.class);

    /**
     * 步骤①实例节点键回填语句（分册 §5.4；外层 WHERE field_node_key IS NULL 为 SQL 自身幂等
     * 守护，ROW_NUMBER 按 field_node_no 升序、并列按 field_id 字典序保证确定性回填）
     */
    private static final String NODE_KEY_BACKFILL_SQL = """
            UPDATE sys_process_example_node n
            JOIN (
                SELECT field_id,
                       CONCAT('N', ROW_NUMBER() OVER (PARTITION BY field_example_id
                                                      ORDER BY field_node_no ASC, field_id ASC)) AS node_key
                FROM sys_process_example_node
                WHERE field_node_key IS NULL
            ) k ON k.field_id = n.field_id
            SET n.field_node_key = k.node_key
            WHERE n.field_node_key IS NULL""";

    /**
     * 步骤②缺失连线快照清单查询（分册 §5.4，口径同 §5.3 仅换表与键名）：available / nxt 按
     * `ROW_NUMBER() OVER (PARTITION BY field_example_id ORDER BY field_node_no ASC, field_id ASC)`
     * 取相邻对（单节点实例天然不命中），LEFT JOIN 实例连线表后仅保留 `l.field_id IS NULL`
     * （尚无连线快照）的相邻对；**不按实例状态过滤**（全状态覆盖，含 DRAFT/FINISH/ABANDON）
     */
    private static final String MISSING_LINE_SQL = """
            SELECT available.field_example_id AS example_id,
                   available.field_node_key   AS from_key,
                   nxt.field_node_key         AS to_key
            FROM (
                SELECT t.field_example_id, t.field_node_key, t.field_node_no,
                       ROW_NUMBER() OVER (PARTITION BY t.field_example_id
                                          ORDER BY t.field_node_no ASC, t.field_id ASC) AS rn
                FROM sys_process_example_node t
                WHERE t.field_node_key IS NOT NULL
            ) available
            JOIN (
                SELECT t.field_example_id, t.field_node_key, t.field_node_no,
                       ROW_NUMBER() OVER (PARTITION BY t.field_example_id
                                          ORDER BY t.field_node_no ASC, t.field_id ASC) AS rn
                FROM sys_process_example_node t
                WHERE t.field_node_key IS NOT NULL
            ) nxt ON nxt.field_example_id = available.field_example_id
                 AND nxt.rn = available.rn + 1
            LEFT JOIN sys_process_example_line l
                   ON l.field_example_id = available.field_example_id
                  AND l.field_from_key = available.field_node_key
                  AND l.field_to_key = nxt.field_node_key
            WHERE l.field_id IS NULL
            ORDER BY available.field_example_id ASC, available.rn ASC""";

    /**
     * 实例连线快照插入语句（分册 §5.4 字段口径同 §5.3）：NOT EXISTS 为 SQL 自身幂等守护
     * （双重：清单查询已过滤 + 插入再守护），field_line_type='NORMAL'、field_branch_order=0、
     * field_branch_name/field_condition 为 NULL、field_is_default=b'0'；field_id 由应用侧
     * IDUtil.initID() 传入（禁用 MySQL UUID()，保证与应用侧同码同源）
     */
    private static final String INSERT_LINE_SQL = """
            INSERT INTO sys_process_example_line
                (field_id, field_example_id, field_from_key, field_to_key, field_line_type,
                 field_branch_order, field_branch_name, field_condition, field_is_default)
            SELECT ?, ?, ?, ?, 'NORMAL', 0, NULL, NULL, b'0'
            WHERE NOT EXISTS (
                SELECT 1 FROM sys_process_example_line l
                WHERE l.field_example_id = ?
                  AND l.field_from_key = ?
                  AND l.field_to_key = ?
            )""";

    /**
     * 孤儿节点计数查询（分册 §5.4 触发条件①；步骤①为条内自洽回填，本计数用于「实例维度完整」
     * 验收留痕与排障：>0 说明存在未被任何实例分组的异常节点，需人工核查）
     */
    private static final String ORPHAN_NODE_COUNT_SQL =
            "SELECT COUNT(*) FROM sys_process_example_node WHERE field_node_key IS NULL";

    /** JDBC 模板（ObjectProvider 惰性注入；测试上下文可为 null，migrate() 入口判空跳过） */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造注入：JdbcTemplate 经 ObjectProvider 惰性形态获取（对齐 CommonSourceMigrationService /
     * SysProcessTemplateDefaultBackfillMigration 先例——测试上下文无数据源时以 null 注入不阻断加载）
     *
     * @param jdbcTemplateProvider JdbcTemplate 提供者
     */
    public SysProcessExampleLineBackfillMigration(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    /**
     * 迁移编码：DM-20261119-003（分册 §5.5 迁移登记表；DM-yyyyMMdd-nnn 全匹配，
     * 严禁复用已 SUCCESS 的 code）
     *
     * @return 迁移编码
     */
    @Override
    public String code() {
        return "DM-20261119-003";
    }

    /**
     * 迁移中文名（分册 §5.5 迁移登记表逐字一致，审计与排障用）
     *
     * @return 迁移中文名
     */
    @Override
    public String name() {
        return "流程实例节点业务键回填与连线快照生成（在途实例兼容）";
    }

    /**
     * 执行顺序：103（分册 §5.1 order 约定；经 order 排在最后，步骤①②条内自洽，不依赖 001/002
     * 的产出——实例侧为独立快照表）
     *
     * @return 执行顺序号
     */
    @Override
    public int order() {
        return 103;
    }

    /**
     * 迁移逻辑（分册 §5.4 两步）：JdbcTemplate 判空（排除数据源的测试上下文）WARN 跳过——执行器侧
     * 同样判空，双保险；否则 ①执行实例节点键回填（INFO 记录影响行数）；②计数留痕（仍为 NULL 的
     * 实例节点数，&gt;0 WARN）；③取缺失连线快照清单，逐条经 PreparedStatement 批处理插入（field_id 由
     * IDUtil.initID() 生成，插入语句带 NOT EXISTS 守护）；④INFO 记录清单行数与实际影响行数。
     * 任一步失败异常上抛，由执行器落 FAILED 审计行并阻断启动（宁阻断不半迁，覆盖风险 R06）
     */
    @Override
    public void migrate() {
        if (null == jdbcTemplate) {
            log.warn("流程实例节点业务键回填与连线快照生成跳过：Spring JdbcTemplate 不可用"
                    + "（数据源未装配，如排除数据源的测试上下文），迁移不执行");
            return;
        }
        // 步骤①（分册 §5.4）：实例节点键回填 N1..Nn（按 field_node_no 升序、并列按 field_id 字典序）
        int nodesUpdated = jdbcTemplate.update(NODE_KEY_BACKFILL_SQL);
        log.info("流程实例节点业务键回填完成 ({}): 本轮回填 field_node_key {} 行（0 行=无 field_node_key "
                + "为 NULL 的存量实例节点，SQL 自身幂等）", code(), nodesUpdated);
        // 计数留痕：步骤①后仍为 NULL 的实例节点（正常为 0；>0 说明存在异常节点，需人工核查）
        Integer orphanNodes = jdbcTemplate.queryForObject(ORPHAN_NODE_COUNT_SQL, Integer.class);
        if (null != orphanNodes && orphanNodes > 0) {
            log.warn("流程实例节点业务键回填警告 ({}): 回填后仍有 {} 个实例节点 field_node_key 为 NULL，"
                    + "这些节点不参与连线快照生成，请人工核查后重跑", code(), orphanNodes);
        }
        // 步骤②（分册 §5.4）：缺失连线快照清单（LEFT JOIN ... field_id IS NULL 过滤，不按实例状态过滤）
        List<Object[]> batchArgs = new ArrayList<>();
        jdbcTemplate.query(MISSING_LINE_SQL, (ResultSet rs) -> {
            while (rs.next()) {
                String exampleId = rs.getString("example_id");
                String fromKey = rs.getString("from_key");
                String toKey = rs.getString("to_key");
                batchArgs.add(new Object[]{IDUtil.initID(), exampleId, fromKey, toKey,
                        exampleId, fromKey, toKey});
            }
            return null;
        });
        // 批处理插入（PreparedStatement；插入语句自带 NOT EXISTS 守护，构成 SQL 侧双重幂等）
        int inserted = 0;
        if (!batchArgs.isEmpty()) {
            int[] rows = jdbcTemplate.batchUpdate(INSERT_LINE_SQL, batchArgs);
            for (int row : rows) {
                inserted += row;
            }
        }
        log.info("流程实例连线快照生成完成 ({}): 缺失连线清单 {} 条，实际插入 {} 行"
                        + "（清单 0 条=各实例连线快照已齐备，SQL 自身幂等）",
                code(), batchArgs.size(), inserted);
    }
}
