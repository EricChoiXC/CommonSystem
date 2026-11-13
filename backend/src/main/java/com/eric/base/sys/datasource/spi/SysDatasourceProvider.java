package com.eric.base.sys.datasource.spi;

import com.eric.base.common.base.resolver.BaseResolver;

import java.util.List;

/**
 * 数据源目标能力 SPI
 *
 * @author Eric
 * @date 2026/11/13
 * @description 业务模块实现本接口并注册为 Spring Bean，即向数据源模块声明一个
 *              「目标查询能力」；同时可在 {@link #getDefaultDatasources()} 中声明随模块
 *              提供的默认数据源。数据源模块经 {@code SysDatasourceRegistry} 收集全部实现：
 *              <ul>
 *              <li>targets/options 下拉的元数据来源（编码、名称、条件/返回字段定义）；</li>
 *              <li>自定义数据源的挂接白名单（field_target 只能取 getTargetCode）；</li>
 *              <li>统一执行入口的执行与鉴权委托（getTargetAction + getTargetResolver）。</li>
 *              </ul>
 */
public interface SysDatasourceProvider {

    /** 目标查询能力编码（全局唯一，命名 {模块}.{能力}，如 calendar.list） */
    String getTargetCode();

    /** 目标查询能力名称（下拉展示） */
    String getTargetName();

    /** 目标接口标识（ACTION_* 常量值，如 sys.calendar.list；执行期鉴权委托用） */
    String getTargetAction();

    /** 目标接口路径（如 /backend/sys/calendar/list；仅展示用） */
    String getTargetPath();

    /** 目标接口鉴权器（执行期经 AuthorizationHelper 委托目标 action 鉴权） */
    BaseResolver getTargetResolver();

    /** 条件字段定义（自定义数据源配置表单与执行期条件白名单） */
    List<SysDatasourceFieldDo> getConditionFields();

    /** 返回字段定义（默认全字段母集；投影白名单必须是其子集） */
    List<SysDatasourceFieldDo> getResultFields();

    /**
     * 执行查询
     *
     * @param context 条件合并后的执行上下文
     * @return 行列表（元素为 VO/模型对象，由数据源模块统一投影；总数经行级 count 一并返回）
     */
    SysDatasourceQueryResult query(SysDatasourceQueryContext context);

    /**
     * 随本能力提供的默认数据源（可多个）；返回空列表 = 仅作为目标能力，不带默认数据源
     *
     * @return 默认数据源定义列表
     */
    List<SysDatasourceDefaultDo> getDefaultDatasources();
}
