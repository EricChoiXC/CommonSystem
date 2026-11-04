package com.common.system.sys.calendar.request;

import com.common.system.common.base.model.BaseRequest;

/**
 * 日程列表查询请求对象
 *
 * @author Eric
 * @date 2026/8/21
 */
public class SysCalendarListRequest extends BaseRequest<SysCalendarQuery> {

    /** 排序字段 */
    private String sortField;

    /** 排序方向：asc/desc */
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