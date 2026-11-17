package com.eric.base.common.source.dao;

import com.eric.base.common.source.mapper.CommonSourceMigrationLogMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;

import java.util.Date;

/**
 * 数据迁移执行审计数据访问对象
 *
 * @author Eric
 * @date 2026/11/17
 * @description 与 CommonSourceMigrationLogMapper 一对一对接，不承载业务逻辑；对应审计表
 *              common_source_migration_log（设计方案 §5.3，流水类口径只增不改），仅提供
 *              迁移审计行写入与幂等判定查询（existsSuccess，供 CommonSourceMigrationService
 *              执行前查 SUCCESS 行跳过），无 update / delete 语义，因此不走 BaseDao 基类
 *              （BaseDao 承载 BaseModel 实体 CRUD，审计表无对应实体模型）。
 *              <p>Mapper 经 ObjectProvider 惰性构造注入（对齐同包 CommonSourceSyncLogDao
 *              先例）：运行容器经 mybatis starter 自动装配必含 Mapper；排除 MyBatis 自动配置的
 *              测试上下文（CommonSystemApplicationTests）装配本 Bean 时以 null 持有不阻断
 *              加载，实际调用时显式校验抛 IllegalStateException。</p>
 */
@Repository
public class CommonSourceMigrationLogDao {

    /** 审计 Mapper（测试上下文无 MyBatis 自动配置时为 null，调用时校验） */
    private final CommonSourceMigrationLogMapper mapper;

    /**
     * 构造注入 Mapper（ObjectProvider 惰性形态）
     *
     * @param mapperProvider Mapper 提供者
     */
    public CommonSourceMigrationLogDao(ObjectProvider<CommonSourceMigrationLogMapper> mapperProvider) {
        this.mapper = mapperProvider.getIfAvailable();
    }

    /**
     * 迁移审计行落库（SUCCESS / FAILED 各一条，只增不改；字段对齐设计方案 §5.3）。
     * field_start_time / field_end_time 由 Service 显式写入（开始/结束时刻），
     * field_create_time 由库默认 CURRENT_TIMESTAMP 维护。
     *
     * @param fieldId            主键 ID（应用侧 IDUtil.initID 生成）
     * @param fieldMigrationCode 迁移编码（DM-yyyyMMdd-nnn）
     * @param fieldMigrationName 迁移名称
     * @param fieldStatus        结果（SUCCESS/FAILED）
     * @param fieldStartTime     开始时间（Service 显式写入）
     * @param fieldEndTime       结束时间（Service 显式写入）
     * @param fieldError         失败原因（异常链；SUCCESS 为 null）
     * @param fieldCostMs        耗时毫秒
     * @param fieldCreatorId     创建者 ID（引擎系统身份）
     * @return 影响行数
     */
    public int insert(String fieldId, String fieldMigrationCode, String fieldMigrationName, String fieldStatus,
                      Date fieldStartTime, Date fieldEndTime, String fieldError,
                      long fieldCostMs, String fieldCreatorId) {
        requireMapper("迁移审计行写入");
        return mapper.insert(fieldId, fieldMigrationCode, fieldMigrationName, fieldStatus,
                fieldStartTime, fieldEndTime, fieldError, fieldCostMs, fieldCreatorId);
    }

    /**
     * 幂等判定：该迁移编码是否已存在 SUCCESS 审计行（设计方案 §5.3/§6）。
     *
     * @param fieldMigrationCode 迁移编码
     * @return 存在 SUCCESS 行返回 true（执行时幂等跳过）
     */
    public boolean existsSuccess(String fieldMigrationCode) {
        requireMapper("迁移幂等判定查询");
        return mapper.existsSuccess(fieldMigrationCode) > 0;
    }

    /**
     * Mapper 可用性校验（测试上下文无 MyBatis 自动配置时为 null，显式 fail-fast）
     */
    private void requireMapper(String action) {
        if (null == mapper) {
            throw new IllegalStateException("CommonSourceMigrationLogMapper 不可用（MyBatis 未装配），无法执行" + action);
        }
    }
}
