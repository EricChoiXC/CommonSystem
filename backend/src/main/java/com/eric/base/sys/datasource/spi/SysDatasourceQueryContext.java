package com.eric.base.sys.datasource.spi;

import com.eric.base.common.base.model.Pages;
import lombok.Data;

import java.util.Map;

/**
 * 数据源执行上下文
 *
 * @author Eric
 * @date 2026/11/13
 * @description SysDatasourceService 条件合并后传给 Provider 的执行入参：
 *              conditions 为「固定条件(已解析变量) + 调用方条件(被固定条件覆盖)」的最终条件集，
 *              key 为字段名、value 为类型转换后的值；Provider 据此调用本模块查询链路。
 */
@Data
public class SysDatasourceQueryContext {

    /** 最终条件集（key → 值，无条件的 key 不出现） */
    private Map<String, Object> conditions;

    /** 分页参数（可为 null = 不分页） */
    private Pages pages;

    /** 排序字段（已通过可排序白名单校验；可为 null） */
    private String sortField;

    /** 排序方向 asc / desc（可为 null，默认 desc） */
    private String sortOrder;
}
