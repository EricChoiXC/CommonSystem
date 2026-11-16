package com.eric.base.common.source.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 表结构注解同步结果模型（F19-BE-005）
 *
 * @author Eric
 * @date 2026/11/16
 * @description CommonSourceSyncService#sync() 的返回汇总（供调用方与单测消费）：
 *              模式、结果状态、标注表数量、变更分级计数与明细。changes 明细与审计表
 *              common_source_sync_log.field_changes JSON 同形状（设计方案 §5.3 口径
 *              [{level,type,table,detail,sql}]，经 Jackson 序列化）。未获锁跳过时
 *              sync() 返回 null，失败（L3 fail-fast / DDL 失败）以异常抛出并落 FAILED
 *              审计行，本模型仅在成功路径返回。
 */
@Getter
@Setter
public class CommonSourceSyncResultDo {

    /** 同步模式（VALIDATE/INSTALL/APPLY） */
    private String mode;

    /** 结果（SUCCESS；跳过返回 null 不产结果，失败抛异常） */
    private String status;

    /** 标注表数量 */
    private int tableTotal;

    /** 变更条数（含 L1/L2/L3） */
    private int changeTotal;

    /** L1 变更条数（缺失/无损） */
    private int l1Total;

    /** L2 变更条数（歧义告警） */
    private int l2Total;

    /** L3 变更条数（数据丢失风险） */
    private int l3Total;

    /** 变更明细（与审计表 field_changes JSON 同形状） */
    private List<ChangeItemDo> changes;

    /** 耗时毫秒 */
    private long costMs;

    /**
     * 变更明细条目：固定结构 POJO，Jackson 序列化进审计表 field_changes JSON
     * （设计方案 §5.3 口径 [{level,type,table,detail,sql}]）
     */
    @Getter
    @AllArgsConstructor
    public static class ChangeItemDo {

        /** 分级（L1/L2/L3） */
        private final String level;

        /** 变更类型（SchemaChange 常量之一，与同步语义矩阵行对应） */
        private final String type;

        /** 涉及表名 */
        private final String table;

        /** 明细说明（两侧值与处理指引） */
        private final String detail;

        /** DDL 文本（告警类条目为 null） */
        private final String sql;
    }
}
