package com.eric.base.sys.process.migration;

import com.eric.base.common.source.spi.SourceDataMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 流程模板默认标记补录数据迁移（首个 SourceDataMigration 实现，任务 F19-BE-007）
 *
 * <p>承接 {@code db/migration/V1_01_038__sys_process_template_default_backfill.sql} 的语义：
 * Flyway 纯替换为注解引擎后（注解维护表结构设计方案.md §6「首例」），SQL 数据脚本通道停用，
 * 数据回填改走 DataMigration SPI（{@link SourceDataMigration}）——本类即其首个实现，原脚本
 * 存档保留不改动（Flyway 移除后的数据通道），等价性由单测从 test classpath 读取原脚本
 * 提取 UPDATE 语句文本比对断言（机器可验证口径）。</p>
 *
 * @author Eric
 * @date 2026/11/17
 * @description 背景（与原脚本头注口径一致）：V1_01_019 仅对当时已存在的模板分类做过一次
 *               「存量迁移」（每个分类最新创建的启用模板置为默认），之后新增的业务分类
 *               （如 biz_order / biz_asset 等业务分类）没有默认模板，导致流程启动（单据
 *               提交）时抛出「xx分类未配置默认模板，流程启动失败，请先在「流程管理-模板管理」
 *               中设置默认模板」。
 *               <p>规则：对「有启用模板、且完全没有默认模板」的分类（按 field_model_name
 *               分组），取最新创建（field_create_time 降序、field_id 降序兜底同刻）的启用
 *               模板置为默认（field_default = 1），与 V1_01_019 的存量迁移语义一致；默认
 *               数量子查询不区分启用状态，避免与生成列 field_default_unique 的分类唯一键
 *               兜底冲突；仅处理 default_cnt = 0 的分类——保证不会与已有默认模板冲突，
 *               已人工取消默认的分类（default_cnt 仍为 1）不会被本迁移改动。业务语境见
 *               docs/设计文档/sys/process/流程核心业务.md「默认模板」节（每分类最多一个
 *               默认模板，流程启动未指定模板时使用，无默认模板则启动失败）。</p>
 *               <p>幂等性（双保险）：1 SQL 自身幂等——迁移完成后各分类 default_cnt 均为 1，
 *               default_cnt = 0 条件不再命中任何行，重复执行结果不变；2 执行器侧幂等——
 *               CommonSourceMigrationService 查 common_source_migration_log 存在本迁移
 *               SUCCESS 审计行即跳过，不重放 SQL。</p>
 *               <p>注入与执行形态（对齐 common/source 包先例）：JdbcTemplate 经
 *               ObjectProvider 惰性构造注入（运行容器经 mybatis starter 自动装配必含；
 *               排除数据源的测试上下文以 null 注入不阻断 Bean 加载），migrate() 入口判空
 *               WARN 跳过——执行器侧同样判空，双保险；SQL 为类内 private static final
 *               String 文本块常量（逐字承接原脚本 UPDATE 语句，含行内注释，禁止手工拼串），
 *               等价性断言测试按「去注释行 + 压缩空白 + 去结尾分号」归一化后逐字比对；
 *               执行成功 INFO 记录影响行数，失败异常上抛由执行器落 FAILED 审计行并阻断
 *               启动（对齐 Flyway 失败语义）。</p>
 */
@Component
public class SysProcessTemplateDefaultBackfillMigration implements SourceDataMigration {

    private static final Logger log = LoggerFactory.getLogger(SysProcessTemplateDefaultBackfillMigration.class);

    /**
     * 默认模板回填语句（逐字承接 V1_01_038__sys_process_template_default_backfill.sql 的
     * UPDATE 语句，含行内注释；等价性由 SysProcessTemplateDefaultBackfillMigrationTest
     * 从 test classpath 读取原脚本归一化比对断言，禁止在本类内手工拼串或改动语句文本）
     */
    private static final String DEFAULT_BACKFILL_SQL = """
            UPDATE sys_process_template t
            JOIN (
                -- 每个分类最新创建的启用模板
                SELECT e.field_id
                FROM (
                    SELECT field_id, field_model_name,
                           ROW_NUMBER() OVER (PARTITION BY field_model_name
                                              ORDER BY field_create_time DESC, field_id DESC) AS rn
                    FROM sys_process_template
                    WHERE field_enable = 1
                ) e
                -- 每个分类的默认模板数量（不区分启用状态，避免与生成列唯一键冲突）
                JOIN (
                    SELECT field_model_name,
                           SUM(CASE WHEN field_default = 1 THEN 1 ELSE 0 END) AS default_cnt
                    FROM sys_process_template
                    GROUP BY field_model_name
                ) d ON d.field_model_name = e.field_model_name
                WHERE e.rn = 1
                  AND d.default_cnt = 0
            ) r ON t.field_id = r.field_id
            SET t.field_default = 1;""";

    /** JDBC 模板（ObjectProvider 惰性注入；测试上下文可为 null，migrate() 入口判空跳过） */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造注入：JdbcTemplate 经 ObjectProvider 惰性形态获取（对齐 CommonSourceSyncService /
     * CommonSourceMigrationService 先例——测试上下文无数据源时以 null 注入不阻断加载）
     *
     * @param jdbcTemplateProvider JdbcTemplate 提供者
     */
    public SysProcessTemplateDefaultBackfillMigration(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    /**
     * 迁移编码：DM-20261117-001（DM-yyyyMMdd-nnn 格式，2026-11-17 当日首例，nnn 自 001 起）
     *
     * @return 迁移编码
     */
    @Override
    public String code() {
        return "DM-20261117-001";
    }

    /**
     * 迁移中文名（审计与排障用）
     *
     * @return 迁移中文名
     */
    @Override
    public String name() {
        return "流程模板默认标记补录（默认模板 backfill）";
    }

    /**
     * 执行顺序：100（首位迁移预留序号空间）。
     * <p>order 约定：本首例迁移占 100，后续新增迁移自 101 起依次递增；如出现须排在
     * 本例之前执行的历史迁移，取 1~99（执行清单按 order 升序、并列按 code 字典序排序，
     * 见 CommonSourceMigrationService 构造期排序）。</p>
     *
     * @return 执行顺序号
     */
    @Override
    public int order() {
        return 100;
    }

    /**
     * 迁移逻辑：JdbcTemplate 判空（排除数据源的测试上下文）WARN 跳过——执行器侧
     * （CommonSourceMigrationService）同样判空，双保险；否则执行逐字等价的回填语句，
     * 成功 INFO 记录影响行数（首次回填命中「无默认模板」分类的最新启用模板，重复执行
     * 因 SQL 自身幂等命中 0 行），失败异常上抛由执行器落 FAILED 审计行并阻断启动
     */
    @Override
    public void migrate() {
        if (null == jdbcTemplate) {
            log.warn("流程模板默认标记补录跳过：Spring JdbcTemplate 不可用（数据源未装配，"
                    + "如排除数据源的测试上下文），迁移不执行");
            return;
        }
        int updated = jdbcTemplate.update(DEFAULT_BACKFILL_SQL);
        log.info("流程模板默认标记补录完成 ({}): 本轮置为默认模板 {} 行（0 行=各分类均已有默认模板，SQL 自身幂等）",
                code(), updated);
    }
}
