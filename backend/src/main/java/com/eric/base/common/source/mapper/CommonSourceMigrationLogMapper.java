package com.eric.base.common.source.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * 数据迁移执行审计数据访问映射
 *
 * @author Eric
 * @date 2026/11/17
 * @description 与 resources/mappers/common/source/CommonSourceMigrationLogMapper.xml 一对一对接，
 *              只做 SQL 映射，不承载业务逻辑。对应审计表 common_source_migration_log（设计方案
 *              §5.3，流水类口径只增不改）：仅提供迁移审计行 insert 与幂等判定 existsSuccess
 *              两条语句，不提供 update / delete。field_start_time / field_end_time 由
 *              CommonSourceMigrationService 显式写入（开始/结束时刻），field_create_time 由
 *              库默认 CURRENT_TIMESTAMP 维护，insert 不写该列；field_error 为 TEXT 列，
 *              Service 层传入异常链描述字符串（SUCCESS 为 null）。
 */
@Mapper
public interface CommonSourceMigrationLogMapper {

    /**
     * 迁移审计行落库（SUCCESS / FAILED 各一条，FAILED 记录保留供追溯、重跑产生新行，只增不改）。
     *
     * @param fieldId            主键 ID（应用侧 IDUtil.initID 生成，32 位，库无默认值）
     * @param fieldMigrationCode 迁移编码（DM-yyyyMMdd-nnn，全局唯一）
     * @param fieldMigrationName 迁移名称
     * @param fieldStatus        结果（SUCCESS/FAILED）
     * @param fieldStartTime     开始时间（Service 显式写入）
     * @param fieldEndTime       结束时间（Service 显式写入；FAILED 同样写结束时刻）
     * @param fieldError         失败原因（异常链；SUCCESS 为 null）
     * @param fieldCostMs        耗时毫秒
     * @param fieldCreatorId     创建者 ID（引擎系统身份）
     * @return 影响行数
     */
    int insert(@Param("fieldId") String fieldId,
               @Param("fieldMigrationCode") String fieldMigrationCode,
               @Param("fieldMigrationName") String fieldMigrationName,
               @Param("fieldStatus") String fieldStatus,
               @Param("fieldStartTime") Date fieldStartTime,
               @Param("fieldEndTime") Date fieldEndTime,
               @Param("fieldError") String fieldError,
               @Param("fieldCostMs") long fieldCostMs,
               @Param("fieldCreatorId") String fieldCreatorId);

    /**
     * 幂等判定：该迁移编码是否已存在 SUCCESS 审计行（设计方案 §5.3——不设库级唯一键，
     * 幂等由 Service 层执行前查询保证，并发互斥由独立命名锁 GET_LOCK 保证）。
     *
     * @param fieldMigrationCode 迁移编码
     * @return 存在 SUCCESS 行返回 1（true），否则 0（false）
     */
    long existsSuccess(@Param("fieldMigrationCode") String fieldMigrationCode);
}
