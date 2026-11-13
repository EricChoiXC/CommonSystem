package com.eric.base.sys.datasource.enums;

/**
 * 数据源来源类型
 *
 * @author Eric
 * @date 2026/11/13
 * @description 区分默认数据源与自定义数据源：默认由代码 Provider 注册（不入库），
 *              自定义由管理人员配置存 sys_datasource。
 */
public enum SysDatasourceSourceType {

    /** 默认数据源：代码 Provider 注册，不可改删 */
    CODE,
    /** 自定义数据源：管理人员配置，存 sys_datasource */
    CUSTOM;

    public String getValue() {
        return name();
    }
}
