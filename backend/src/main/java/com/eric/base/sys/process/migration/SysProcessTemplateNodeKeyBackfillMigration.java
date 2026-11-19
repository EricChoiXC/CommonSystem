package com.eric.base.sys.process.migration;

import com.eric.base.common.source.spi.SourceDataMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 流程模板节点业务键回填数据迁移（DM-20261119-001，任务 P17-DB-005）
 *
 * <p>承接《流程v1_7数据库设计分册》§5.2 与 §5.3 的存量数据迁移方案：v1_7 起流程图由
 * 节点业务键（{@code field_node_key}，模板内唯一，如 {@code N1} / {@code N2}）与连线
 * （{@code sys_process_template_line}）共同表达，而存量模板节点只有自增排序列
 * {@code field_no}、{@code field_node_key} 为 NULL。本迁移为存量模板节点按
 * {@code field_no} 顺序回填 {@code N1..Nn}，使其可被 {@link SysProcessTemplateLineBackfillMigration}
 * （DM-20261119-002，order=102）与模板 Service 的图校验一致地消费。</p>
 *
 * @author Eric
 * @date 2026/11/19
 * @description 背景：`sys_process_template_node.field_node_key` 为本批新增列（分册 §4.2
 *              变更 1，可空），存量行允许为 NULL，由本迁移（分册 §5.2）按 `field_no`
 *              顺序回填 `N1..Nn`；与 v1_7 固定节点口径一致（N1 开始 / N2 起草 / N3 结束，
 *              存量线性模板的 field_no 顺序天然满足该口径）。
 *              <p>规则：对每个模板（按 `field_template_id` 分组），按 `field_no` 升序、
 *              **并列按 `field_id` 字典序**（确定性 tie-break，避免同序号节点回填顺序
 *              依赖存储引擎返回顺序）依次回填 `N1`、`N2`…`Nn`；UPDATE 的 JOIN 派生表
 *              自身带 `WHERE field_node_key IS NULL`，只对未回填模板计算序号，已回填模板
 *              的键不被重排。</p>
 *              <p>幂等性（双保险）：①SQL 自身幂等——语句恒带外层 `WHERE n.field_node_key
 *              IS NULL` 守护条件，回填后所有命中行该列为非空、条件不再命中任何行，重复
 *              执行命中 0 行、结果不变；②执行器侧幂等——CommonSourceMigrationService 查
 *              common_source_migration_log 存在本迁移 SUCCESS 审计行即跳过（existsSuccess），
 *              不重放 SQL。</p>
 *              <p>与后续迁移的耦合（分册 §5.2「与后续迁移的耦合」）：DM-20261119-002 依赖
 *              本迁移已回填的 `field_node_key` 生成连线，两者按 order 顺序执行（101 → 102）；
 *              若本迁移未 SUCCESS 则 002 在连线生成时因源节点键为空而记 WARN 并阻断，此即
 *              期望语义（宁阻断不产错图）。</p>
 *              <p>注入与执行形态（对齐同包首例 SysProcessTemplateDefaultBackfillMigration）：
 *              JdbcTemplate 经 ObjectProvider 惰性构造注入（运行容器经 mybatis starter 自动
 *              装配必含；排除数据源的测试上下文以 null 注入不阻断 Bean 加载），migrate()
 *              入口判空 WARN 跳过——执行器侧同样判空，双保险；SQL 为类内 private static final
 *              String 文本块常量（禁止手工拼串）；执行成功 INFO 记录影响行数（先例口径），
 *              失败异常上抛由执行器落 FAILED 审计行并阻断启动（分册 §5.1「失败与阻断语义」）。</p>
 */
@Component
public class SysProcessTemplateNodeKeyBackfillMigration implements SourceDataMigration {

    private static final Logger log = LoggerFactory.getLogger(SysProcessTemplateNodeKeyBackfillMigration.class);

    /**
     * 模板节点键回填语句（分册 §5.2 处理逻辑；外层 WHERE field_node_key IS NULL 为 SQL 自身
     * 幂等守护，ROW_NUMBER 按 field_no 升序、并列按 field_id 字典序保证确定性回填）
     */
    private static final String NODE_KEY_BACKFILL_SQL = """
            UPDATE sys_process_template_node n
            JOIN (
                SELECT field_id,
                       CONCAT('N', ROW_NUMBER() OVER (PARTITION BY field_template_id
                                                      ORDER BY field_no ASC, field_id ASC)) AS node_key
                FROM sys_process_template_node
                WHERE field_node_key IS NULL
            ) k ON k.field_id = n.field_id
            SET n.field_node_key = k.node_key
            WHERE n.field_node_key IS NULL""";

    /** JDBC 模板（ObjectProvider 惰性注入；测试上下文可为 null，migrate() 入口判空跳过） */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造注入：JdbcTemplate 经 ObjectProvider 惰性形态获取（对齐 CommonSourceMigrationService /
     * SysProcessTemplateDefaultBackfillMigration 先例——测试上下文无数据源时以 null 注入不阻断加载）
     *
     * @param jdbcTemplateProvider JdbcTemplate 提供者
     */
    public SysProcessTemplateNodeKeyBackfillMigration(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    /**
     * 迁移编码：DM-20261119-001（分册 §5.5 迁移登记表；DM-yyyyMMdd-nnn 全匹配，
     * 严禁复用已 SUCCESS 的 code）
     *
     * @return 迁移编码
     */
    @Override
    public String code() {
        return "DM-20261119-001";
    }

    /**
     * 迁移中文名（分册 §5.5 迁移登记表逐字一致，审计与排障用）
     *
     * @return 迁移中文名
     */
    @Override
    public String name() {
        return "流程模板节点业务键回填（node_key backfill）";
    }

    /**
     * 执行顺序：101（分册 §5.1 order 约定——首例 DM-20261117-001 占 100，本批自 101 起递增；
     * 101 → 102 → 103 保证「先回填节点键、再生成连线」的依赖顺序）
     *
     * @return 执行顺序号
     */
    @Override
    public int order() {
        return 101;
    }

    /**
     * 迁移逻辑（分册 §5.2）：JdbcTemplate 判空（排除数据源的测试上下文）WARN 跳过——执行器侧
     * 同样判空，双保险；否则执行节点键回填语句，成功 INFO 记录影响行数（首次回填命中所有
     * field_node_key 为 NULL 的存量节点，重复执行因 SQL 自身幂等命中 0 行），失败异常上抛由
     * 执行器落 FAILED 审计行并阻断启动（宁阻断不半迁）
     */
    @Override
    public void migrate() {
        if (null == jdbcTemplate) {
            log.warn("流程模板节点业务键回填跳过：Spring JdbcTemplate 不可用（数据源未装配，"
                    + "如排除数据源的测试上下文），迁移不执行");
            return;
        }
        int updated = jdbcTemplate.update(NODE_KEY_BACKFILL_SQL);
        log.info("流程模板节点业务键回填完成 ({}): 本轮回填 field_node_key {} 行（0 行=无 field_node_key "
                + "为 NULL 的存量节点，SQL 自身幂等）", code(), updated);
    }
}
