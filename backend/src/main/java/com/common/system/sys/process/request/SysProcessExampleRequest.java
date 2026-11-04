package com.common.system.sys.process.request;

import com.common.system.common.base.model.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 流程实例列表查询请求
 *
 * @author Eric
 * @date 2026/8/23
 * @description 请求体 { body: { fieldName, fieldCode, fieldModelName, fieldCreatorName,
 *              fieldCreateTimeBegin, fieldCreateTimeEnd, fieldStatus }, pages: { pageNo, pageSize, needPage },
 *              sortField, sortOrder }；排序字段仅 createTime，默认创建时间降序。
 */
public class SysProcessExampleRequest extends BaseRequest<SysProcessExampleQuery> {

    /** 排序字段：仅 createTime */
    @Schema(description = "排序字段：仅支持 createTime（创建时间），默认 createTime",
            allowableValues = {"createTime"}, defaultValue = "createTime")
    private String sortField;

    /** 排序方向：asc / desc，默认 desc */
    @Schema(description = "排序方向：asc / desc，默认 desc", allowableValues = {"asc", "desc"},
            defaultValue = "desc")
    private String sortOrder;

    @Override
    public SysProcessExampleQuery getBody() {
        return (SysProcessExampleQuery) super.getBody();
    }

    @Override
    public void setBody(SysProcessExampleQuery body) {
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
