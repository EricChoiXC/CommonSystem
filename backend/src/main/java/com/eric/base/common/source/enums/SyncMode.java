package com.eric.base.common.source.enums;

/**
 * 表结构同步模式枚举：由环境变量 CS_SOURCE_SYNC_MODE 配置
 * （application.properties 三段式 {@code common.source.sync-mode=${CS_SOURCE_SYNC_MODE:VALIDATE}}），
 * 代码兜底默认 VALIDATE；属部署运行参数，不入 common_setting。
 *
 * @author Eric
 * @date 2026/11/16
 * @description 模式行为按同步语义矩阵（设计方案 §4.3）分流。
 */
public enum SyncMode {

    /** 只比对出报告落审计表 + WARN 日志，不执行任何 DDL，不阻断启动（test/prod 常态，代码兜底默认） */
    VALIDATE,

    /** 仅执行「缺失对象新增」子集（建表/加列/加索引），不执行任何修改类变更（prod 全新库首次部署一次性门禁） */
    INSTALL,

    /** L1 自动执行、L2 告警不执行、L3 fail-fast 阻断启动（dev 开发态，本地启动参数显式开启） */
    APPLY
}
