package com.eric.base.sys.calendar.request;

import com.eric.base.common.base.model.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 日程列表查询请求对象
 *
 * @author Eric
 * @date 2026/8/21
 */
public class SysCalendarListRequest extends BaseRequest<SysCalendarQuery> {

    /** 排序字段：startTime（开始时间）/ createTime（创建时间），默认 createTime */
    @Schema(description = "排序字段：startTime（日程开始时间）/ createTime（创建时间），默认 createTime",
            allowableValues = {"startTime", "createTime"}, defaultValue = "createTime")
    private String sortField;

    /** 排序方向：asc/desc，默认 desc */
    @Schema(description = "排序方向：asc / desc，默认 desc", allowableValues = {"asc", "desc"},
            defaultValue = "desc")
    private String sortOrder;

    @Override
    public SysCalendarQuery getBody() {
        return (SysCalendarQuery) super.getBody();
    }

    @Override
    public void setBody(SysCalendarQuery body) {
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