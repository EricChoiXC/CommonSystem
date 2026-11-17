package com.eric.base.common.source.service;

import com.eric.base.common.source.dao.CommonSourceMigrationLogDao;
import com.eric.base.common.source.spi.SourceDataMigration;
import com.eric.base.common.utils.IDUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 数据迁移执行引擎（设计方案 §6：DataMigration SPI 收集 + 幂等 + 失败阻断 + 审计）
 *
 * @author Eric
 * @date 2026/11/17
 * @description 承载设计方案 §6 DataMigration SPI 契约的执行侧：构造期经 Spring 收集容器内
 *              全部 {@link SourceDataMigration} 实现 Bean（对齐 SysDatasourceRegistry 收集
 *              模式）并 fail-fast 校验——code 须匹配 DM-yyyyMMdd-nnn 全匹配、全局唯一（重复
 *              抛 IllegalStateException），违例阻断启动；执行清单按 order() 升序、并列按
 *              code 字典序排序（同日多迁移顺序确定，不依赖 Bean 注册顺序）。
 *              <p>主入口 {@link #migrate()}（SysStartupTask 第二个启动任务调用，位于
 *              表结构同步之后——先结构后数据）：1 JdbcTemplate 不可用（排除数据源的测试
 *              上下文）WARN 跳过、不比对不落审计；2 无已注册迁移 INFO 跳过（不必取锁）；
 *              3 SELECT GET_LOCK('common_source_data_migration', 30) 多实例互斥（独立于
 *              表结构同步锁 common_source_schema_sync，F19-DOC-001 评审修正口径），未获锁
 *              WARN「另一实例迁移中」跳过返回；4 获锁后 try：先 CREATE TABLE IF NOT EXISTS
 *              自举 common_source_migration_log（DDL 复用 CommonSourceSyncService 的
 *              BOOTSTRAP_MIGRATION_LOG_DDL 逐字一致；自举幂等，覆盖 sync 未获锁跳过时
 *              该审计表缺失的场景——实现口径补充）；5 按序逐个执行：先查审计表该 code
 *              是否存在 SUCCESS 行，存在则 INFO 幂等跳过；否则记录开始时刻执行
 *              migration.migrate()，成功插 SUCCESS 审计行（结束时刻、耗时毫秒），失败插
 *              FAILED 审计行（field_error 记异常链「类全名: 消息 | caused by …」，
 *              口径对齐 CommonSourceSyncService#describeError）后把原异常重新抛出阻断
 *              启动（对齐 Flyway 失败语义；审计写入自身失败仅记日志不吞原始异常）；
 *              6 finally RELEASE_LOCK（含异常路径），释放异常 WARN。</p>
 *              <p>注入形态：对齐同包 CommonSourceSyncService 先例——JdbcTemplate 经
 *              ObjectProvider 惰性构造注入（运行容器经 mybatis starter 自动装配必含；
 *              测试上下文以 null 注入不阻断加载，migrate() 入口显式校验后 WARN 跳过）；
 *              审计写入走 CommonSourceMigrationLogDao（Dao/Mapper 一对一）。</p>
 */
@Service
public class CommonSourceMigrationService {

    private static final Logger log = LoggerFactory.getLogger(CommonSourceMigrationService.class);

    /** 数据迁移独立命名锁（设计方案 §5.4，多实例互斥；与表结构同步锁 common_source_schema_sync 分离） */
    static final String MIGRATION_LOCK_NAME = "common_source_data_migration";

    /** 迁移编码格式（DM-yyyyMMdd-nnn 全匹配，如 DM-20261117-001） */
    private static final Pattern CODE_PATTERN = Pattern.compile("DM-\\d{8}-\\d{3}");

    /** 数据迁移实现清单（构造期已按 order 升序、并列按 code 字典序排序，只读） */
    private final List<SourceDataMigration> migrations;

    /** 迁移审计 Dao（Dao/Mapper 一对一） */
    private final CommonSourceMigrationLogDao migrationLogDao;

    /** JDBC 模板（GET_LOCK / RELEASE_LOCK 与审计表自举；测试上下文可为 null） */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造注入：Spring 收集全部 SourceDataMigration 实现 Bean、迁移审计 Dao 与
     * JdbcTemplate（ObjectProvider 惰性形态）；构造期 fail-fast 校验 code 格式与
     * 全局唯一（违例抛 IllegalStateException 阻断启动），并完成执行清单排序
     *
     * @param migrations            全部数据迁移实现 Bean
     * @param migrationLogDao       迁移审计 Dao
     * @param jdbcTemplateProvider  JdbcTemplate 提供者
     */
    public CommonSourceMigrationService(List<SourceDataMigration> migrations,
                                        CommonSourceMigrationLogDao migrationLogDao,
                                        ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        // fail-fast：code 格式（DM-yyyyMMdd-nnn 全匹配）与全局唯一校验，违例阻断启动
        Set<String> seenCodes = new HashSet<>();
        for (SourceDataMigration migration : migrations) {
            String code = migration.code();
            if (null == code || !CODE_PATTERN.matcher(code).matches()) {
                throw new IllegalStateException("数据迁移编码非法（须匹配 DM-yyyyMMdd-nnn 全匹配）: " + code
                        + "（name=" + migration.name() + "）");
            }
            if (!seenCodes.add(code)) {
                throw new IllegalStateException("数据迁移编码重复（须全局唯一，禁止复用已 SUCCESS 的 code，"
                        + "语义调整须新 code）: " + code);
            }
        }
        // 执行清单排序：order() 升序，并列按 code 字典序（保证同日多迁移的执行确定性）
        List<SourceDataMigration> ordered = new ArrayList<>(migrations);
        ordered.sort(Comparator.comparingInt(SourceDataMigration::order).thenComparing(SourceDataMigration::code));
        this.migrations = List.copyOf(ordered);
        this.migrationLogDao = migrationLogDao;
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        log.info("数据迁移注册表构建完成: {} 个迁移（{}）", this.migrations.size(), describeMigrations());
    }

    /**
     * 数据迁移主入口（设计方案 §6，SysStartupTask 第二个启动任务调用，表结构同步之后执行）
     *
     * <p>语义：JdbcTemplate 不可用 / 无已注册迁移 / 未获锁 → 跳过返回（不比对不落审计）；
     * 获锁后逐个幂等执行，任一迁移失败落 FAILED 审计行后原异常重新抛出（阻断启动，
     * 后续迁移不再执行）。</p>
     */
    public void migrate() {
        // 1. JdbcTemplate 不可用（如排除数据源的测试上下文）：跳过，不比对不落审计
        if (null == jdbcTemplate) {
            log.warn("数据迁移跳过：Spring JdbcTemplate 不可用（数据源未装配，如排除数据源的测试上下文），"
                    + "引擎不执行任何迁移与审计");
            return;
        }
        // 2. 无已注册迁移：跳过（不必取锁）
        if (migrations.isEmpty()) {
            log.info("数据迁移跳过：无已注册的 SourceDataMigration 实现");
            return;
        }
        // 3. 多实例互斥（§5.4 独立命名锁）：超时未获锁 WARN 跳过本次
        Long acquired = jdbcTemplate.queryForObject(CommonSourceSyncService.SQL_GET_LOCK, Long.class,
                MIGRATION_LOCK_NAME);
        if (null == acquired || 1L != acquired) {
            log.warn("数据迁移跳过：另一实例迁移中（GET_LOCK({}) 等待 {}s 未获锁，本次不执行不落审计）",
                    MIGRATION_LOCK_NAME, CommonSourceSyncService.LOCK_WAIT_SECONDS);
            return;
        }
        try {
            // 4. 引擎自举：数据迁移审计表 CREATE TABLE IF NOT EXISTS（§5.3 逐字；自举幂等，
            //    覆盖表结构同步未获锁跳过时本审计表缺失的场景）
            jdbcTemplate.execute(CommonSourceSyncService.BOOTSTRAP_MIGRATION_LOG_DDL);
            // 5. 按序逐个执行（order 升序、并列按 code 字典序；任一失败抛异常阻断，后续不再执行）
            for (SourceDataMigration migration : migrations) {
                executeOne(migration);
            }
            log.info("数据迁移全部完成: 共 {} 个迁移", migrations.size());
        } finally {
            // 6. 释放命名锁（含异常路径）
            Long released = jdbcTemplate.queryForObject(CommonSourceSyncService.SQL_RELEASE_LOCK, Long.class,
                    MIGRATION_LOCK_NAME);
            if (null == released || 1L != released) {
                log.warn("数据迁移命名锁释放异常（RELEASE_LOCK({}) 返回 {}），如残留请人工清理",
                        MIGRATION_LOCK_NAME, released);
            }
        }
    }

    /**
     * 执行单个迁移（幂等 + 审计）：已存在 SUCCESS 审计行 INFO 跳过；否则记录开始时刻执行
     * migrate()，成功插 SUCCESS 审计行、失败插 FAILED 审计行（异常链口径对齐
     * CommonSourceSyncService#describeError）后原异常重新抛出阻断启动
     */
    private void executeOne(SourceDataMigration migration) {
        // 幂等判定（§5.3/§6）：该 code 已存在 SUCCESS 审计行 → 跳过
        if (migrationLogDao.existsSuccess(migration.code())) {
            log.info("数据迁移幂等跳过: {} ({})，审计表已存在 SUCCESS 记录", migration.name(), migration.code());
            return;
        }
        Date startTime = new Date();
        long start = System.currentTimeMillis();
        try {
            migration.migrate();
            Date endTime = new Date();
            migrationLogDao.insert(IDUtil.initID(), migration.code(), migration.name(),
                    CommonSourceSyncService.STATUS_SUCCESS, startTime, endTime, null,
                    System.currentTimeMillis() - start, CommonSourceSyncService.SYSTEM_CREATOR_ID);
            log.info("数据迁移执行成功: {} ({})，耗时 {}ms", migration.name(), migration.code(),
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            // FAILED 同样落一条审计行（只增不改，FAILED 记录保留供追溯、重跑产生新行）：
            // field_error 记异常链；审计写入自身失败仅记日志，原始失败继续抛出（对齐 Flyway 失败语义）
            Date endTime = new Date();
            String error = describeError(e);
            try {
                migrationLogDao.insert(IDUtil.initID(), migration.code(), migration.name(),
                        CommonSourceSyncService.STATUS_FAILED, startTime, endTime, error,
                        System.currentTimeMillis() - start, CommonSourceSyncService.SYSTEM_CREATOR_ID);
            } catch (Exception auditError) {
                log.error("数据迁移 FAILED 审计行写入失败（原始失败继续抛出）: {}", auditError.getMessage(), auditError);
            }
            log.error("数据迁移执行失败（阻断启动，后续迁移不再执行）: {} ({}): {}",
                    migration.name(), migration.code(), error);
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("数据迁移失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异常链展开（field_error 口径，对齐 CommonSourceSyncService#describeError）：
     * 「类全名: 消息 | caused by ...」逐级拼接
     */
    private static String describeError(Throwable error) {
        StringBuilder text = new StringBuilder();
        Throwable current = error;
        while (null != current) {
            if (!text.isEmpty()) {
                text.append(" | caused by ");
            }
            text.append(current.getClass().getName()).append(": ").append(current.getMessage());
            current = current.getCause() == current ? null : current.getCause();
        }
        return text.toString();
    }

    /**
     * 已注册迁移清单描述（构造完成日志用，按执行顺序）
     */
    private String describeMigrations() {
        StringBuilder text = new StringBuilder();
        for (SourceDataMigration migration : migrations) {
            if (!text.isEmpty()) {
                text.append("、");
            }
            text.append(migration.code());
        }
        return text.toString();
    }
}
