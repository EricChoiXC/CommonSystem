package com.eric.base.sys.datasource.spi;

import lombok.Data;

import java.util.List;

/**
 * 默认数据源定义文档对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description Provider 声明的随模块默认数据源：编码全局唯一（不入库），
 *              conditions 为固定条件（默认为空 = 不限），fields 为返回字段白名单
 *              （默认为空 = 目标能力全字段）。
 */
@Data
public class SysDatasourceDefaultDo {

    /** 默认数据源编码（全局唯一，如 calendar.list） */
    private String code;

    /** 默认数据源名称 */
    private String name;

    /** 固定条件（可为空；key 须 ∈ 目标能力条件白名单） */
    private List<SysDatasourceConditionDo> conditions;

    /** 返回字段白名单（可为空 = 全字段；key 须 ∈ 目标能力返回字段母集） */
    private List<String> fields;
}
