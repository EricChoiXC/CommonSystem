package com.common.system.sys.process.request;

import com.common.system.common.base.model.BaseRequest;

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
    private String sortField;

    /** 排序方向：asc / desc，默认 desc */
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
