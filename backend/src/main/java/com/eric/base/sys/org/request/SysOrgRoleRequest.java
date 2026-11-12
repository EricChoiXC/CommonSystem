package com.eric.base.sys.org.request;

import com.eric.base.common.base.model.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 角色列表查询请求
 *
 * @author Eric
 * @date 2026/8/10
 * @description 请求体 { body: { fieldName, fieldCode, enable }, pages: { pageNo, pageSize, needPage },
 *              sortField, sortOrder }；sortField 取 createTime / updateTime / code，默认创建时间降序。
 */
public class SysOrgRoleRequest extends BaseRequest<SysOrgRoleQuery> {

    /** 排序字段：createTime / updateTime / code，默认 createTime */
    @Schema(description = "排序字段：createTime / updateTime / code，默认 createTime",
            allowableValues = {"createTime", "updateTime", "code"}, defaultValue = "createTime")
    private String sortField;

    /** 排序方向：asc / desc，默认 desc */
    @Schema(description = "排序方向：asc / desc，默认 desc", allowableValues = {"asc", "desc"},
            defaultValue = "desc")
    private String sortOrder;

    @Override
    public SysOrgRoleQuery getBody() {
        return (SysOrgRoleQuery) super.getBody();
    }

    @Override
    public void setBody(SysOrgRoleQuery body) {
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
