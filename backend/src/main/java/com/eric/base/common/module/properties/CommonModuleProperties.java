package com.eric.base.common.module.properties;

/**
 * 模块组开关接口常量
 *
 * @author Eric
 * @date 2026/11/12
 * @description 模块组（module-group）管理接口的接口标识与状态缓存常量。
 *              依据 docs/v1_1-common/05-增加接口开关/接口开关机制设计方案-修订稿.md
 *              第 9 节（状态缓存与多实例一致性）与第 10.1 节（权限码与 ACTION 常量）。
 */
public class CommonModuleProperties {

    public static final String ACTION_LIST = "common.module.list";
    public static final String ACTION_ENABLE = "common.module.enable";

    /**
     * 模块组状态缓存 TTL（秒）。默认 30 秒，为修订稿第 9 节默认决策 D1：
     * 多实例切换经 Redis 广播失效（best-effort）后，最迟经该 TTL 收敛一致；
     * Redis 不可用时仅依赖该 TTL 兜底。联调期可调整。
     */
    public static final long CACHE_TTL_SECONDS = 30;
}
