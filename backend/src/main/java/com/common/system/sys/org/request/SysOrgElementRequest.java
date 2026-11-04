package com.common.system.sys.org.request;

import com.common.system.common.base.model.BaseRequest;

/**
 * 组织架构元素列表请求对象
 *
 * @author Eric
 * @date 2026/8/10
 * @description 列表查询请求体：body 承载查询条件，pages 承载分页，
 *              sortField / sortOrder 承载单条件排序（createTime / updateTime / code + asc / desc）。
 */
public class SysOrgElementRequest extends BaseRequest<SysOrgElementQuery> {

    /** 排序字段：createTime / updateTime / code，默认 createTime */
    private String sortField;

    /** 排序方向：asc / desc，默认 desc */
    private String sortOrder;

    @Override
    public SysOrgElementQuery getBody() {
        return (SysOrgElementQuery) super.getBody();
    }

    @Override
    public void setBody(SysOrgElementQuery body) {
        super.setBody(body);
    }

    public String getSortField() {
        return sortField;
    }

    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }
}
