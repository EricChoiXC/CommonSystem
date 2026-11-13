package com.eric.base.sys.datasource.request;

import com.eric.base.common.base.model.Pages;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 数据源统一执行请求
 *
 * @author Eric
 * @date 2026/11/13
 * @description 消费方按 code 取数的统一入口请求体（方案 §5.3，DOC-002 §4.10.4 /query 契约）；
 *              body 为调用方过滤条件（key → 值，固定条件同名覆盖之），sortField 须命中
 *              目标能力可排序白名单（sortable=true 汇总集），非法值由 Service 回退目标能力默认排序。
 */
@Data
@Schema(description = "数据源执行请求：{ code, body(调用方条件), pages, sortField, sortOrder }")
public class SysDatasourceExecuteRequest {

    /** 数据源编码（默认或自定义，如 calendar.list / calendar.my-created） */
    @Schema(description = "数据源编码（默认或自定义）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    /** 调用方过滤条件（key → 值；固定条件同名覆盖之） */
    @Schema(description = "调用方过滤条件（key → 值，固定条件同名覆盖之）")
    private Map<String, Object> body;

    /** 分页参数 */
    @Schema(description = "分页参数 { pageNo, pageSize, needPage }")
    private Pages pages;

    /** 排序字段（须 ∈ 目标能力可排序白名单） */
    @Schema(description = "排序字段（须 ∈ 目标能力可排序白名单，非法值回退目标能力默认排序）")
    private String sortField;

    /** 排序方向 asc / desc，默认 desc */
    @Schema(description = "排序方向：asc / desc，默认 desc", allowableValues = {"asc", "desc"},
            defaultValue = "desc")
    private String sortOrder;
}
