package com.eric.base.common.source.spi;

/**
 * 数据迁移 SPI：承载注解表达不了的数据回填（纯替换 Flyway 后的唯一数据通道）。
 *
 * @author Eric
 * @date 2026/11/17
 * @description 实现类声明为 Spring Bean 即被 {@code CommonSourceMigrationService} 收集注册
 *              （对齐 {@code SysDatasourceRegistry} 收集模式），构造期 fail-fast 校验
 *              {@link #code()} 格式（DM-yyyyMMdd-nnn 全匹配）与全局唯一性，违例阻断启动；
 *              执行时机为第十三个启动任务（表结构同步之后，先结构后数据），按 {@link #order()}
 *              升序（并列按 code 字典序保证确定性）逐个执行，执行前查迁移审计表
 *              common_source_migration_log 存在 SUCCESS 行则幂等跳过；{@link #migrate()}
 *              失败落 FAILED 审计行后原异常重新抛出阻断启动（对齐 Flyway 失败语义）。
 *              迁移逻辑内可用 JdbcTemplate / 模块 Dao；复杂回填（游标分批）对齐
 *              {@code SysOrgElementService#backfillPinyin} 先例。
 */
public interface SourceDataMigration {

    /**
     * 迁移编码：DM-{yyyyMMdd}-{nnn}，全局唯一；禁止复用已 SUCCESS 的 code，语义调整须新 code
     *
     * @return 迁移编码
     */
    String code();

    /**
     * 中文名（审计与排障用）
     *
     * @return 迁移中文名
     */
    String name();

    /**
     * 执行顺序（同日多迁移时显式控制）
     *
     * @return 执行顺序号
     */
    int order();

    /**
     * 迁移逻辑；失败抛异常阻断启动
     */
    void migrate();
}
