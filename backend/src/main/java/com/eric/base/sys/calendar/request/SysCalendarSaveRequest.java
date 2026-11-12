package com.eric.base.sys.calendar.request;

import com.eric.base.common.base.model.BaseRequest;

/**
 * 日程保存请求对象
 *
 * @author Eric
 * @date 2026/8/21
 */
public class SysCalendarSaveRequest extends BaseRequest<SysCalendarMainRequest> {

    @Override
    public SysCalendarMainRequest getBody() {
        return (SysCalendarMainRequest) super.getBody();
    }

    @Override
    public void setBody(SysCalendarMainRequest body) {
        super.setBody(body);
    }
}