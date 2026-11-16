package com.eric.base.common.source.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 表结构注解同步审计数据访问映射
 *
 * @author Eric
 * @date 2026/11/16
 * @description 与 resources/mappers/common/source/CommonSourceSyncLogMapper.xml 一对一对接，
 *              只做 SQL 映射，不承载业务逻辑。对应审计表 common_source_sync_log（设计方案
 *              §5.3，流水类口径只增不改）：仅提供汇总审计行 insert 与 managed 历史表清单
 *              selectManagedTables 两条语句，不提供 update / delete。
 *              field_sync_time / field_create_time 由库默认 CURRENT_TIMESTAMP 维护，
 *              insert 不写这两列；field_changes 为 JSON 列，Service 层经 Jackson 序列化
 *              SchemaChange 数组后按字符串写入（禁止手工拼串）。
 */
@Mapper
public interface CommonSourceSyncLogMapper {

    /**
     * 汇总审计行落库（SUCCESS / FAILED 各一条，重跑产生新行，只增不改）。
     *
     * @param fieldId         主键 ID（应用侧 IDUtil.initID 生成，32 位，库无默认值）
     * @param fieldMode       同步模式（VALIDATE/INSTALL/APPLY）
     * @param fieldStatus     结果（SUCCESS/FAILED）
     * @param fieldTableTotal 标注表数量
     * @param fieldChangeTotal 变更条数（含 L1/L2/L3）
     * @param fieldChanges    变更明细 JSON（[{level,type,table,detail,sql}]，无明细为 null）
     * @param fieldError      失败原因（异常链；SUCCESS 为 null）
     * @param fieldCostMs     耗时毫秒
     * @param fieldCreatorId  创建者 ID（引擎系统身份）
     * @return 影响行数
     */
    int insert(@Param("fieldId") String fieldId,
               @Param("fieldMode") String fieldMode,
               @Param("fieldStatus") String fieldStatus,
               @Param("fieldTableTotal") int fieldTableTotal,
               @Param("fieldChangeTotal") int fieldChangeTotal,
               @Param("fieldChanges") String fieldChanges,
               @Param("fieldError") String fieldError,
               @Param("fieldCostMs") long fieldCostMs,
               @Param("fieldCreatorId") String fieldCreatorId);

    /**
     * managed 历史表清单：从历史审计行 field_changes JSON 明细（[{level,type,table,detail,sql}]）
     * 经 JSON_TABLE 展开提取去重表名（MySQL 8.0.14+ 支持对 FROM 前表列的 lateral 引用）。
     * managed 口径 = 当前标注表 ∪ 本查询产出的历史表（设计方案 §4.4；实现口径偏差由主控回写）。
     *
     * @return 历史标注表名清单（去重）
     */
    List<String> selectManagedTables();
}
