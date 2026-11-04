package com.common.system.common.base.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 统一请求体基类
 *
 * @author Eric
 * @date 2026/7/30
 * @description 列表查询类接口的通用请求包装：body 承载查询条件实体，params 承载非固定结构的
 *              扩展条件，pages 承载分页参数。各模块派生专属 Request（如 SysOrgElementRequest）
 *              后可在其上追加 sortField / sortOrder 等排序字段。
 */
@Data
@Schema(description = "统一请求体：body / params / pages")
public class BaseRequest<T> {

    /** 业务查询条件实体，字段名与对应 Model 一致（如 fieldName、fieldEnable） */
    private T body;

    /** 非固定结构的扩展查询条件，按需使用，未使用时传 null */
    private Map<String, Object> params;

    /** 分页参数；不传时部分接口按默认值处理（如 pageNo=1、pageSize=100） */
    private Pages pages;

}
