package com.eric.base.sys.formula.spi;

import java.util.List;

/**
 * 公式函数 SPI
 *
 * @author Eric
 * @date 2026/11/18
 * @description 各模块向公式定义器注册公式函数的扩展契约（方案 §2.2）：实现本接口并注册为
 *              Spring Bean，即由 {@code FormulaFunctionRegistry} 经 Spring 构造注入收集，
 *              构造期完成进程内索引（functionCode → 定义与执行体），不新增 ApplicationRunner
 *              （启动期统一收口 SysStartupTask，注册中心为被动组件）。扩展方式：
 *              <ul>
 *              <li>内置函数（F14-BE-003 九类首批）：同样实现本接口落
 *                  service/engine/functions 包，每类一个 Provider 或单个内置 Provider 均可；</li>
 *              <li>业务模块扩展：org 注册 ORG_*（v1.7）、datasource 注册 DS_QUERY 等，
 *                  执行体可引用本模块 Service（经各自既有鉴权语义，方案 §6.4）；</li>
 *              <li>函数编码全局唯一：重复 functionCode 输出告警且首个注册生效，不阻断启动
 *                  （对齐 SysDatasourceRegistry 先例）；getFunctions() 返回空列表 = 不提供函数。</li>
 *              </ul>
 */
public interface FormulaFunctionProvider {

    /**
     * 本 Provider 提供的公式函数定义列表（元数据 + Aviator 执行体）
     *
     * @return 函数定义列表；空列表 = 本 Provider 不提供函数
     */
    List<FormulaFunctionDefinition> getFunctions();
}
