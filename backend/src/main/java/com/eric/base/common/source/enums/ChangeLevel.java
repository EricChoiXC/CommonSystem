package com.eric.base.common.source.enums;

/**
 * 结构变更分级枚举：同步语义矩阵（设计方案 §4.3）比对结果的三级口径，
 * 执行分流为引擎（CommonSourceSyncService）核心行为。
 *
 * @author Eric
 * @date 2026/11/16
 * @description 引擎永不执行 DROP TABLE / DROP COLUMN / RENAME（执行器硬编码）。
 */
public enum ChangeLevel {

    /** 结构安全或无损变更：APPLY 自动执行；INSTALL 仅限缺失对象新增子集（建表/加列/加索引）；VALIDATE 只报告 */
    L1,

    /** 有歧义或需人工裁决（可空收紧/孤儿列/索引差异/ENGINE-CHARSET 不一致等）：任何模式只告警不执行 */
    L2,

    /** 有数据丢失风险（类型不兼容/长度收窄/疑似改名）：APPLY fail-fast 阻断启动并给出处理指引；VALIDATE/INSTALL 只报告 */
    L3
}
