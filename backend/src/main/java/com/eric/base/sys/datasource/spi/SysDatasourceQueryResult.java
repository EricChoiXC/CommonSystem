package com.eric.base.sys.datasource.spi;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 数据源执行结果
 *
 * @author Eric
 * @date 2026/11/13
 * @description Provider 返回的原始行（VO/模型对象列表）与总数；
 *              字段投影由数据源模块统一执行（FieldProjector），Provider 不感知投影。
 */
@Data
@AllArgsConstructor
public class SysDatasourceQueryResult {

    /** 原始行列表（VO/模型对象） */
    private List<?> rows;

    /** 总数（分页用；不分页时可与 rows.size() 一致） */
    private long total;
}
