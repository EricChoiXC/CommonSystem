package com.eric.base.common.source.service;

import com.eric.base.common.source.dao.CommonSourceSyncLogDao;
import com.eric.base.common.source.diff.SourceSchemaDiffer;
import com.eric.base.common.source.enums.ChangeLevel;
import com.eric.base.common.source.enums.SyncMode;
import com.eric.base.common.source.model.SchemaChange;
import com.eric.base.common.source.model.TableDef;
import com.eric.base.common.source.scanner.SourceTableScanner;
import com.eric.base.common.utils.IDUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 表结构注解同步引擎（设计方案 §5：门禁 + 自举 + 比对分流 + 审计）
 *
 * @author Eric
 * @date 2026/11/16
 * @description 承载设计方案 §5.1 执行流程：1 解析 CS_SOURCE_SYNC_MODE（默认 VALIDATE，
 *              非法值 fail-fast 提示合法取值）→ 2 SELECT GET_LOCK('common_source_schema_sync', 30)
 *              多实例互斥（未获锁 WARN「另一实例同步中」跳过返回）→ 3 引擎自举两张审计表
 *              （CREATE TABLE IF NOT EXISTS，§5.3 逐字，走 JdbcTemplate）→ 4 SourceTableScanner
 *              classpath 扫描（fail-fast 异常经 catch 落 FAILED 审计行后原样阻断启动）→
 *              5 managed 历史表清单（common_source_sync_log 历史审计行 field_changes JSON
 *              提取去重表名，XML 内 JSON_TABLE 实现）→ 6 SourceSchemaDiffer 比对（标注表 ∪
 *              managed 历史表）→ 7 按 §4.3 语义矩阵 + 组合差异口径分流执行/报告 →
 *              8 写一条汇总审计行（SUCCESS；异常路径 status=FAILED、field_error 记异常链，
 *              FAILED 也算一条审计行只增不改，写入自身失败仅记日志不吞原始异常）→
 *              finally RELEASE_LOCK（含异常路径）。
 *              <p>模式分流（§4.3 矩阵 + 组合差异口径回写）：VALIDATE 全部只报告（INFO/WARN
 *              日志汇总分级条数与明细），不执行任何变更 DDL；INSTALL 仅执行缺失对象新增子集
 *              （MISSING_TABLE / MISSING_COLUMN / MISSING_INDEX），其余只告警；APPLY 先收集后
 *              执行——存在任一 L3 直接抛 IllegalStateException（异常信息含「手工 SQL 或
 *              DataMigration」处理指引）且一条 DDL 都不执行，L1 DDL 逐条执行（单条失败抛异常
 *              阻断），L2 全部 WARN 告警不执行，同列被 L2 收紧/L3 收窄暂缓的 L1（Differ 产出
 *              ddl=null，组合差异口径）WARN 待人工裁决。managed 孤儿表告警（L2）在任意模式
 *              都输出 WARN。</p>
 *              <p>双层收口：引擎永不执行 DROP TABLE / DROP COLUMN / RENAME——DDL 文本由
 *              SourceDdlGenerator 方法面硬收口，本执行器入口再对文本做黑名单断言
 *              （containsIgnoreCase DROP TABLE / DROP COLUMN / RENAME，命中抛 IllegalStateException）。</p>
 *              <p>注入形态：SourceTableScanner 为静态工具类直接静态调用（无 Bean）；
 *              JdbcTemplate 经 ObjectProvider 惰性构造注入（对齐同包 SourceSchemaDiffer
 *              先例——排除数据源的测试上下文以 null 注入不阻断加载，sync() 入口显式校验
 *              后 WARN 跳过；运行容器经 mybatis starter 自动装配必含 JdbcTemplate）。
 *              审计写入走 CommonSourceSyncLogDao（Dao/Mapper 一对一）。</p>
 *              <p>实现口径偏差（供主控回写设计方案）：managed 历史表清单从 field_changes
 *              JSON 的 $.table 提取（§4.4 原文为 field_tables 独立列，§5.3 表结构无该列），
 *              历史表仅限产生过变更的表，从未产生 diff 的表失去标注时不产孤儿告警；
 *              field_id 由应用侧 IDUtil.initID 生成（库无默认值），field_sync_time /
 *              field_create_time 走库默认。</p>
 */
@Service
public class CommonSourceSyncService {

    private static final Logger log = LoggerFactory.getLogger(CommonSourceSyncService.class);

    /** 命名锁（设计方案 §5.4，多实例互斥） */
    static final String LOCK_NAME = "common_source_schema_sync";

    /** 锁等待秒数（GET_LOCK 第二参数，SQL 内联 30 与本值保持一致） */
    static final int LOCK_WAIT_SECONDS = 30;

    /** 获锁查询（30s 等待；返回 1=获锁、0=超时、NULL=错误） */
    static final String SQL_GET_LOCK = "SELECT GET_LOCK(?, 30)";

    /** 释放锁查询（返回 1=已释放、0=非持有者、NULL=锁不存在） */
    static final String SQL_RELEASE_LOCK = "SELECT RELEASE_LOCK(?)";

    /** 审计结果：成功 */
    static final String STATUS_SUCCESS = "SUCCESS";

    /** 审计结果：失败 */
    static final String STATUS_FAILED = "FAILED";

    /** 引擎系统身份（内置管理员 ID，对齐 SysStartupTask 系统级写入口径） */
    static final String SYSTEM_CREATOR_ID = "0".repeat(32);

    /** 审计表自举 DDL：同步审计表（设计方案 §5.3 逐字，字段/注释/索引/ENGINE/CHARSET） */
    static final String BOOTSTRAP_SYNC_LOG_DDL = """
            CREATE TABLE IF NOT EXISTS common_source_sync_log (
                field_id          VARCHAR(32)  NOT NULL COMMENT '主键ID',
                field_sync_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '同步时间',
                field_mode        VARCHAR(20)  NOT NULL COMMENT '同步模式（VALIDATE/INSTALL/APPLY）',
                field_status      VARCHAR(20)  NOT NULL COMMENT '结果（SUCCESS/FAILED）',
                field_table_total INT          NOT NULL DEFAULT 0 COMMENT '标注表数量',
                field_change_total INT         NOT NULL DEFAULT 0 COMMENT '变更条数（含 L1/L2/L3）',
                field_changes     JSON         DEFAULT NULL COMMENT '变更明细 [{level,type,table,detail,sql}]',
                field_error       TEXT         DEFAULT NULL COMMENT '失败原因',
                field_cost_ms     BIGINT       DEFAULT NULL COMMENT '耗时毫秒',
                field_creator_id  VARCHAR(32)  DEFAULT NULL COMMENT '创建者ID',
                field_create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                PRIMARY KEY (field_id),
                KEY idx_source_sync_log_time (field_sync_time)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表结构注解同步审计表（只增不改）'""";

    /** 审计表自举 DDL：数据迁移审计表（设计方案 §5.3 逐字，消费方为 F19-BE-006 数据迁移引擎） */
    static final String BOOTSTRAP_MIGRATION_LOG_DDL = """
            CREATE TABLE IF NOT EXISTS common_source_migration_log (
                field_id              VARCHAR(32) NOT NULL COMMENT '主键ID',
                field_migration_code  VARCHAR(100) NOT NULL COMMENT '迁移编码（DM-yyyyMMdd-nnn，全局唯一）',
                field_migration_name  VARCHAR(200) NOT NULL COMMENT '迁移名称',
                field_status          VARCHAR(20) NOT NULL COMMENT '结果（SUCCESS/FAILED）',
                field_start_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
                field_end_time        TIMESTAMP   NULL DEFAULT NULL COMMENT '结束时间',
                field_error           TEXT        DEFAULT NULL COMMENT '失败原因',
                field_cost_ms         BIGINT       DEFAULT NULL COMMENT '耗时毫秒',
                field_creator_id      VARCHAR(32) DEFAULT NULL COMMENT '创建者ID',
                field_create_time     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                PRIMARY KEY (field_id),
                KEY idx_source_migration_code (field_migration_code)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据迁移执行审计表（只增不改）'""";

    /** 执行器黑名单（引擎永不执行的破坏性 DDL 关键字，双层收口第二层） */
    private static final String[] FORBIDDEN_DDL_TOKENS = {"DROP TABLE", "DROP COLUMN", "RENAME"};

    /** 比对器（information_schema 三源读取 + 语义矩阵分级） */
    private final SourceSchemaDiffer differ;

    /** 同步审计 Dao（Dao/Mapper 一对一） */
    private final CommonSourceSyncLogDao syncLogDao;

    /** JDBC 模板（GET_LOCK / RELEASE_LOCK 与审计表自举、变更 DDL 执行；测试上下文可为 null） */
    private final JdbcTemplate jdbcTemplate;

    /** 同步模式配置原文（common.source.sync-mode 三段式，默认 VALIDATE；sync() 内解析） */
    private final String syncModeConfig;

    /** 变更明细 JSON 序列化（field_changes JSON 同形状） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造注入：比对器、审计 Dao、JdbcTemplate（ObjectProvider 惰性形态）与同步模式配置
     *
     * @param differ                比对器
     * @param syncLogDao            同步审计 Dao
     * @param jdbcTemplateProvider  JdbcTemplate 提供者
     * @param syncModeConfig        同步模式配置（common.source.sync-mode 三段式解析值）
     */
    public CommonSourceSyncService(SourceSchemaDiffer differ,
                                   CommonSourceSyncLogDao syncLogDao,
                                   ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                                   @Value("${common.source.sync-mode:VALIDATE}") String syncModeConfig) {
        this.differ = differ;
        this.syncLogDao = syncLogDao;
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        this.syncModeConfig = syncModeConfig;
    }

    /**
     * 同步主入口（设计方案 §5.1 执行流程，SysStartupTask 第一个启动任务调用）
     *
     * @return 本次同步汇总；未获锁（另一实例同步中）或 JdbcTemplate 不可用时返回 null，
     *         失败（模式非法 / L3 数据丢失风险 / DDL 执行失败）抛异常并以 FAILED 落审计行
     */
    public CommonSourceSyncResultDo sync() {
        long start = System.currentTimeMillis();
        if (null == jdbcTemplate) {
            log.warn("表结构注解同步跳过：Spring JdbcTemplate 不可用（数据源未装配，如排除数据源的测试上下文），"
                    + "引擎不执行任何比对与 DDL");
            return null;
        }
        // 1. 解析同步模式（§5.2 环境门禁；非法值 fail-fast 提示合法取值）
        SyncMode mode = resolveMode();
        // 2. 多实例互斥（§5.4）：超时未获锁 WARN 跳过本次
        Long acquired = jdbcTemplate.queryForObject(SQL_GET_LOCK, Long.class, LOCK_NAME);
        if (null == acquired || 1L != acquired) {
            log.warn("表结构注解同步跳过：另一实例同步中（GET_LOCK({}) 等待 {}s 未获锁，本次不比对不落审计）",
                    LOCK_NAME, LOCK_WAIT_SECONDS);
            return null;
        }
        List<SchemaChange> changes = null;
        int tableTotal = 0;
        try {
            // 3. 引擎自举：两张审计表 CREATE TABLE IF NOT EXISTS（§5.3 逐字；失败直接抛——审计表不可用则无法落审计行）
            jdbcTemplate.execute(BOOTSTRAP_SYNC_LOG_DDL);
            jdbcTemplate.execute(BOOTSTRAP_MIGRATION_LOG_DDL);
            // 4. 扫描标注实体（§3.5 fail-fast 校验；异常经下方 catch 落 FAILED 审计后原样阻断启动）
            List<TableDef> annotatedTables = SourceTableScanner.scan();
            tableTotal = annotatedTables.size();
            // 5. managed 历史表清单（历史审计行 field_changes JSON 提取去重表名）
            Set<String> managedTables = new HashSet<>(syncLogDao.selectManagedTables());
            // 6. 比对（标注表 ∪ managed 历史表，§4.2）
            changes = differ.diff(annotatedTables, managedTables);
            // 7. 模式分流执行/报告（§4.3 语义矩阵 + 组合差异口径）
            CommonSourceSyncResultDo result = dispatch(mode, tableTotal, changes, start);
            // 8. 汇总审计行（SUCCESS，只增不改）并返回本次汇总
            writeAuditLog(mode, STATUS_SUCCESS, tableTotal, result.getChangeTotal(),
                    result.getChanges(), null, costSince(start));
            return result;
        } catch (Exception e) {
            // FAILED 同样落一条审计行（只增不改）：field_error 记异常链，已知 diff 明细照记；
            // 审计写入自身失败仅记日志，原始失败继续抛出（对齐 Flyway 失败语义）
            try {
                writeAuditLog(mode, STATUS_FAILED, tableTotal,
                        null == changes ? 0 : changes.size(),
                        toChangeItems(changes), describeError(e), costSince(start));
            } catch (Exception auditError) {
                log.error("表结构同步 FAILED 审计行写入失败（原始失败继续抛出）: {}", auditError.getMessage(), auditError);
            }
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("表结构注解同步失败: " + e.getMessage(), e);
        } finally {
            // 释放命名锁（含异常路径）
            Long released = jdbcTemplate.queryForObject(SQL_RELEASE_LOCK, Long.class, LOCK_NAME);
            if (null == released || 1L != released) {
                log.warn("表结构注解同步命名锁释放异常（RELEASE_LOCK({}) 返回 {}），如残留请人工清理", LOCK_NAME, released);
            }
        }
    }

    /* ==================== 模式分流（§4.3 语义矩阵 + 组合差异口径） ==================== */

    /**
     * 分流执行/报告并组装返回汇总（变更明细与审计 field_changes JSON 同形状）
     */
    private CommonSourceSyncResultDo dispatch(SyncMode mode, int tableTotal, List<SchemaChange> changes, long start) {
        CommonSourceSyncResultDo result = new CommonSourceSyncResultDo();
        result.setMode(mode.name());
        result.setStatus(STATUS_SUCCESS);
        result.setTableTotal(tableTotal);
        result.setChangeTotal(changes.size());
        result.setL1Total(countByLevel(changes, ChangeLevel.L1));
        result.setL2Total(countByLevel(changes, ChangeLevel.L2));
        result.setL3Total(countByLevel(changes, ChangeLevel.L3));
        result.setChanges(toChangeItems(changes));
        switch (mode) {
            case VALIDATE -> reportOnly(changes);
            case INSTALL -> executeInstall(changes);
            case APPLY -> executeApply(changes);
        }
        result.setCostMs(costSince(start));
        log.info("表结构注解同步完成（mode={}，标注表 {} 张，变更 L1={} L2={} L3={}，耗时 {}ms）",
                mode, tableTotal, result.getL1Total(), result.getL2Total(), result.getL3Total(), result.getCostMs());
        return result;
    }

    /**
     * VALIDATE：全部只报告（L2/L3 WARN、L1 INFO），不执行任何变更 DDL，不阻断启动
     */
    private void reportOnly(List<SchemaChange> changes) {
        log.info("VALIDATE 模式只比对出报告，不执行任何 DDL（L1={} L2={} L3={}）",
                countByLevel(changes, ChangeLevel.L1), countByLevel(changes, ChangeLevel.L2),
                countByLevel(changes, ChangeLevel.L3));
        for (SchemaChange change : changes) {
            if (ChangeLevel.L1 == change.getLevel()) {
                log.info("[表结构同步报告][{}] {}", change.getLevel(), describeChange(change));
            } else {
                log.warn("[表结构同步报告][{}] {}", change.getLevel(), describeChange(change));
            }
        }
    }

    /**
     * INSTALL：仅执行缺失对象新增子集（MISSING_TABLE / MISSING_COLUMN / MISSING_INDEX 的 DDL），
     * 其余（修改类 L1 / L2 / L3）只告警不执行（§5.2：prod 全新库首次部署一次性门禁）
     */
    private void executeInstall(List<SchemaChange> changes) {
        for (SchemaChange change : changes) {
            if (isMissingObjectChange(change)) {
                executeDdl(change);
            } else {
                log.warn("[表结构同步告警][INSTALL 仅执行缺失对象新增，本条不执行] {}", describeChange(change));
            }
        }
    }

    /**
     * 是否缺失对象新增变更（INSTALL 执行子集）：L1 且携带 DDL 且类型为缺失表/缺失列/缺失索引
     */
    private static boolean isMissingObjectChange(SchemaChange change) {
        return ChangeLevel.L1 == change.getLevel() && null != change.getDdl()
                && (SchemaChange.MISSING_TABLE.equals(change.getChangeType())
                || SchemaChange.MISSING_COLUMN.equals(change.getChangeType())
                || SchemaChange.MISSING_INDEX.equals(change.getChangeType()));
    }

    /**
     * APPLY：先收集后执行——存在任一 L3 直接抛 IllegalStateException（一条 DDL 都不执行，
     * 异常信息含「手工 SQL 或 DataMigration」处理指引）；L1 DDL 逐条执行（单条失败抛异常
     * 阻断启动，失败详情经 sync() catch 落 FAILED 审计行）；L2 全部 WARN 告警不执行；
     * 同列被 L2 收紧 / L3 收窄暂缓的 L1（组合差异口径，Differ 产出 ddl=null）WARN 待人工裁决
     */
    private void executeApply(List<SchemaChange> changes) {
        // L3 判定前置：存在任一 L3 直接失败，一条 DDL 都不执行（§4.3）
        List<SchemaChange> l3Changes = changes.stream()
                .filter(change -> ChangeLevel.L3 == change.getLevel()).toList();
        if (!l3Changes.isEmpty()) {
            throw new IllegalStateException("表结构同步存在 L3 数据丢失风险变更（APPLY fail-fast，未执行任何 DDL），"
                    + "请以手工 SQL 或 DataMigration 显式处置后重试: " + describeChanges(l3Changes));
        }
        for (SchemaChange change : changes) {
            if (ChangeLevel.L1 == change.getLevel()) {
                if (null != change.getDdl()) {
                    executeDdl(change);
                } else {
                    log.warn("[表结构同步告警][L1 DDL 暂缓待人工裁决（同列存在可空收紧/收窄差异）] {}",
                            describeChange(change));
                }
            } else if (ChangeLevel.L2 == change.getLevel()) {
                log.warn("[表结构同步告警][L2 任何模式只告警不执行] {}", describeChange(change));
            }
            // L3 已被前置 fail-fast，不会到达
        }
    }

    /**
     * 执行单条变更 DDL：入口黑名单防御断言（永不 DROP TABLE / DROP COLUMN / RENAME，
     * 双层收口第二层）后经 JdbcTemplate 提交
     */
    private void executeDdl(SchemaChange change) {
        assertExecutableDdl(change.getDdl());
        jdbcTemplate.execute(change.getDdl());
        log.info("表结构变更 DDL 执行成功: {}", change.getDdl());
    }

    /**
     * 执行器入口 DDL 黑名单断言（永不执行的破坏性关键字，containsIgnoreCase）：命中抛
     * IllegalStateException——DDL 文本由 SourceDdlGenerator 方法面硬收口，本断言为双层收口第二层
     */
    private static void assertExecutableDdl(String ddl) {
        if (null == ddl || ddl.isBlank()) {
            throw new IllegalStateException("待执行 DDL 为空，拒绝执行空语句");
        }
        String normalized = ddl.toUpperCase(Locale.ROOT);
        for (String token : FORBIDDEN_DDL_TOKENS) {
            if (normalized.contains(token)) {
                throw new IllegalStateException("检测到禁用的破坏性 DDL（引擎永不执行 DROP TABLE / DROP COLUMN / RENAME）: "
                        + ddl);
            }
        }
    }

    /* ==================== 内部：模式解析 / 审计 / 工具 ==================== */

    /**
     * 解析同步模式（§5.2）：空白回退 VALIDATE（代码兜底），大小写不敏感；
     * 非法值 fail-fast 抛 IllegalStateException 并提示合法取值
     */
    private SyncMode resolveMode() {
        if (null == syncModeConfig || syncModeConfig.isBlank()) {
            return SyncMode.VALIDATE;
        }
        try {
            return SyncMode.valueOf(syncModeConfig.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("common.source.sync-mode / CS_SOURCE_SYNC_MODE 非法取值: " + syncModeConfig
                    + "（合法取值 VALIDATE / INSTALL / APPLY，默认 VALIDATE；属部署运行参数，经环境变量显式设置）", e);
        }
    }

    /**
     * 写一条汇总审计行（只增不改）：changes JSON 经 Jackson 序列化
     * （[{level,type,table,detail,sql}]，无明细为 null）；field_id 由 IDUtil.initID 生成，
     * field_sync_time / field_create_time 走库默认
     */
    private void writeAuditLog(SyncMode mode, String status, int tableTotal, int changeTotal,
                               List<CommonSourceSyncResultDo.ChangeItemDo> items, String error, long costMs) {
        String changesJson = null;
        if (null != items && !items.isEmpty()) {
            try {
                changesJson = objectMapper.writeValueAsString(items);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("审计变更明细 JSON 序列化失败: " + e.getMessage(), e);
            }
        }
        syncLogDao.insert(IDUtil.initID(), mode.name(), status, tableTotal, changeTotal,
                changesJson, error, costMs, SYSTEM_CREATOR_ID);
    }

    /**
     * SchemaChange → 审计明细条目（level/type/table/detail/sql，§5.3 field_changes 口径）
     */
    private static List<CommonSourceSyncResultDo.ChangeItemDo> toChangeItems(List<SchemaChange> changes) {
        List<CommonSourceSyncResultDo.ChangeItemDo> items =
                new ArrayList<>(null == changes ? 0 : changes.size());
        if (null == changes) {
            return items;
        }
        for (SchemaChange change : changes) {
            items.add(new CommonSourceSyncResultDo.ChangeItemDo(change.getLevel().name(), change.getChangeType(),
                    change.getTableName(), change.getDetail(), change.getDdl()));
        }
        return items;
    }

    /**
     * 分级计数
     */
    private static int countByLevel(List<SchemaChange> changes, ChangeLevel level) {
        return (int) changes.stream().filter(change -> change.getLevel() == level).count();
    }

    /**
     * 异常链展开（field_error 口径）：「类全名: 消息 | caused by ...」逐级拼接
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
     * 单条变更描述（WARN/INFO 日志与 L3 fail-fast 信息用）
     */
    private static String describeChange(SchemaChange change) {
        StringBuilder text = new StringBuilder(change.getTableName());
        if (null != change.getColumnName()) {
            text.append('.').append(change.getColumnName());
        }
        if (null != change.getIndexName()) {
            text.append('.').append(change.getIndexName());
        }
        text.append(" | ").append(change.getChangeType()).append(" | ").append(change.getDetail());
        return text.toString();
    }

    /**
     * 多条变更描述（分号连接）
     */
    private static String describeChanges(List<SchemaChange> changes) {
        StringBuilder text = new StringBuilder();
        for (SchemaChange change : changes) {
            if (!text.isEmpty()) {
                text.append("；");
            }
            text.append(describeChange(change));
        }
        return text.toString();
    }

    /**
     * 耗时毫秒
     */
    private static long costSince(long start) {
        return System.currentTimeMillis() - start;
    }
}
