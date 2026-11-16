package com.eric.base.sys.datasource.service;

import com.eric.base.sys.datasource.enums.SysDatasourceSourceType;
import com.eric.base.sys.datasource.spi.SysDatasourceDefaultDo;
import com.eric.base.sys.datasource.spi.SysDatasourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据源注册表
 *
 * @author Eric
 * @date 2026/11/13
 * @description 收集容器内全部 {@link SysDatasourceProvider}（方案 A3：默认数据源与目标能力
 *              由代码注册，对比 numberRule.yml 纯元数据先例——数据源需要可执行逻辑与目标
 *              鉴权器引用，只能由代码承载），构建三个进程内只读索引：
 *              <ul>
 *              <li>targetCode → Provider：自定义数据源挂接白名单与执行分发；</li>
 *              <li>defaultCode → {Provider, 默认定义}：默认数据源解析；</li>
 *              <li>编码占用全集（默认数据源编码 + 目标能力编码）：自定义数据源
 *                  field_code 查重的排除集（防伪装默认源/能力码）。</li>
 *              </ul>
 *              重复 target / 默认数据源编码按方案 A3 输出 WARN 且首个生效，不阻断启动；
 *              经 Spring 构造注入收集，无需追加 SysStartupTask 启动任务。
 *              构造期对每个 Provider 预热调用一次 getConditionFields/getResultFields
 *              （A8：字段定义注解扫描归一），注解配置错误启动期即暴露，不等首个 /targets 请求。
 */
@Component
public class SysDatasourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(SysDatasourceRegistry.class);

    /** 能力编码 → Provider（LinkedHashMap 保序，供 targets 下拉稳定展示） */
    private final Map<String, SysDatasourceProvider> providersByTarget;

    /** 默认数据源编码 → 定义（LinkedHashMap 保序，供 options 下拉稳定展示） */
    private final Map<String, SysDatasourceDefaultDo> defaultsByCode;

    /** 默认数据源编码 → 所属 Provider */
    private final Map<String, SysDatasourceProvider> providersByDefaultCode;

    /** 编码占用全集：默认数据源编码 + 目标能力编码（自定义数据源保存查重排除集） */
    private final Set<String> occupiedCodes;

    public SysDatasourceRegistry(List<SysDatasourceProvider> providerList) {
        Map<String, SysDatasourceProvider> targets = new LinkedHashMap<>();
        Map<String, SysDatasourceDefaultDo> defaults = new LinkedHashMap<>();
        Map<String, SysDatasourceProvider> defaultOwners = new LinkedHashMap<>();
        for (SysDatasourceProvider provider : providerList) {
            if (null != targets.putIfAbsent(provider.getTargetCode(), provider)) {
                log.warn("duplicate datasource target code [{}], first provider wins", provider.getTargetCode());
                continue;
            }
            for (SysDatasourceDefaultDo def : provider.getDefaultDatasources()) {
                if (null != defaults.putIfAbsent(def.getCode(), def)) {
                    log.warn("duplicate default datasource code [{}], first definition wins", def.getCode());
                    continue;
                }
                defaultOwners.put(def.getCode(), provider);
            }
        }
        Set<String> occupied = new LinkedHashSet<>(targets.keySet());
        occupied.addAll(defaults.keySet());
        this.providersByTarget = Collections.unmodifiableMap(targets);
        this.defaultsByCode = Collections.unmodifiableMap(defaults);
        this.providersByDefaultCode = Collections.unmodifiableMap(defaultOwners);
        this.occupiedCodes = Collections.unmodifiableSet(occupied);
        // A8 启动期 fail-fast：预热每个 Provider 的字段定义（含重复编码被跳过者），
        // 注解配置错误（ops 非法、key 不合口径等）在启动期即抛出，不等首个 /targets 请求
        for (SysDatasourceProvider provider : providerList) {
            provider.getConditionFields();
            provider.getResultFields();
        }
        log.info("datasource registry built: {} targets, {} default datasources",
                targets.size(), defaults.size());
    }

    /** 按能力编码取 Provider；不存在返回 null */
    public SysDatasourceProvider getProviderByTarget(String targetCode) {
        return null == targetCode ? null : providersByTarget.get(targetCode);
    }

    /** 按默认数据源编码取定义；不存在返回 null */
    public SysDatasourceDefaultDo getDefault(String code) {
        return null == code ? null : defaultsByCode.get(code);
    }

    /** 按默认数据源编码取所属 Provider；不存在返回 null */
    public SysDatasourceProvider getProviderByDefaultCode(String code) {
        return null == code ? null : providersByDefaultCode.get(code);
    }

    /**
     * 编码是否已被默认数据源占用（自定义数据源保存查重的排除集，防伪装默认源）
     */
    public boolean isCodeOccupiedByDefault(String code) {
        return null != code && defaultsByCode.containsKey(code);
    }

    /**
     * 编码是否被目标能力编码或默认数据源编码占用（自定义数据源保存查重的完整排除集）；
     * null / 空编码不占用。
     */
    public boolean isCodeOccupied(String code) {
        return null != code && !code.isEmpty() && occupiedCodes.contains(code);
    }

    /**
     * 全量目标能力 Provider 列表（targets 下拉）
     */
    public List<SysDatasourceProvider> listProviders() {
        return List.copyOf(providersByTarget.values());
    }

    /**
     * 全量默认数据源定义（options 下拉）；sourceType 固定 CODE
     */
    public List<SysDatasourceDefaultDo> listDefaults() {
        return List.copyOf(defaultsByCode.values());
    }

    /** 来源类型标记（供 options 区分 CODE/CUSTOM） */
    public static String typeOf(boolean custom) {
        return custom ? SysDatasourceSourceType.CUSTOM.getValue() : SysDatasourceSourceType.CODE.getValue();
    }

    /** 供日志/调试列出全部默认数据源编码 */
    public List<String> listDefaultCodes() {
        return new ArrayList<>(defaultsByCode.keySet());
    }
}
