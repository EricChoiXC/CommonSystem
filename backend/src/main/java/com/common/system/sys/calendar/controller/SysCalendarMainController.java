package com.common.system.sys.calendar.controller;

import com.common.system.common.base.controller.BaseCrudController;
import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.base.model.Pages;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.calendar.model.SysCalendarMain;
import com.common.system.sys.calendar.request.SysCalendarListRequest;
import com.common.system.sys.calendar.request.SysCalendarMainRequest;
import com.common.system.sys.calendar.request.SysCalendarQuery;
import com.common.system.sys.calendar.request.SysCalendarSaveRequest;
import com.common.system.sys.calendar.resolver.CalendarResolver;
import com.common.system.sys.calendar.response.SysCalendarVo;
import com.common.system.sys.calendar.service.SysCalendarMainService;
import com.common.system.sys.org.model.SysOrgUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.common.system.sys.calendar.properties.SysCalendarConstants.*;

/**
 * 日程接口
 *
 * @author Eric
 * @date 2026/8/21
 * @description 日程模块的请求入口：只负责接收请求、基础校验、执行最终鉴权并调用
 *              Service 返回统一响应。接口路径遵循 /backend/{根模块}/{模块}/{请求方法}。
 */
@RestController
@RequestMapping("/backend/sys/calendar")
public class SysCalendarMainController extends BaseCrudController<SysCalendarMain, SysCalendarMainService, CalendarResolver> {

    public SysCalendarMainController(SysCalendarMainService baseService, CalendarResolver baseResolver,
                                     ApplicationEventPublisher eventPublisher) {
        super(baseService, baseResolver, eventPublisher);
    }

    /**
     * 新增日程：POST /backend/sys/calendar/save
     * 请求体 { body: { fieldName, fieldStartTime, fieldEndTime, fieldType, fieldContent, personIds } }
     */
    @PostMapping("/save")
    public BaseResponse<SysCalendarMain> save(@RequestBody SysCalendarSaveRequest request) {
        authorize(new AuthorizationContext(ACTION_SAVE));
        SysCalendarMainRequest body = null == request ? null : request.getBody();
        return BaseResponse.success(getBaseService().create(body), null);
    }

    /**
     * 编辑日程：PATCH /backend/sys/calendar/update
     * 请求体 { body: { fieldId, fieldName, fieldStartTime, fieldEndTime, fieldType, fieldContent, personIds } }
     */
    @PatchMapping("/update")
    public BaseResponse<SysCalendarMain> update(@RequestBody SysCalendarSaveRequest request) {
        SysCalendarMainRequest body = null == request ? null : request.getBody();
        authorize(ACTION_UPDATE, null == body ? null : body.getFieldId());
        return BaseResponse.success(getBaseService().update(body), null);
    }

    /**
     * 删除日程：DELETE /backend/sys/calendar/delete?fieldId=${id}
     * 仅限创建者本人删除；团队日程删除后，接收人待阅统一转入已阅。
     */
    @DeleteMapping("/delete")
    public BaseResponse<Void> delete(@RequestParam("fieldId") String fieldId) {
        authorize(ACTION_DELETE, fieldId);
        getBaseService().delete(fieldId);
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 日程详情：GET /backend/sys/calendar/load?fieldId=${id}
     * 返回日程基本信息 + 接收人列表（含接收状态）。
     */
    @GetMapping("/load")
    public BaseResponse<SysCalendarVo> load(@RequestParam("fieldId") String fieldId) {
        authorize(ACTION_LOAD, fieldId);
        return BaseResponse.success(getBaseService().loadDetail(fieldId), null);
    }

    /**
     * 日程列表：POST /backend/sys/calendar/list
     * 请求体 { body: { fieldName, fieldType, fieldAccept, fieldStartTimeStart, fieldStartTimeEnd },
     *           pages: { pageNo, pageSize, needPage }, sortField, sortOrder }
     * 固定查询条件为自己创建的日程或自己作为接收人的日程。
     */
    @PostMapping("/list")
    public BaseResponse<List<SysCalendarVo>> list(@RequestBody SysCalendarListRequest request) {
        authorize(new AuthorizationContext(ACTION_LIST));
        SysCalendarQuery query = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        String userId = requireCurrentUserId();
        List<SysCalendarVo> list = getBaseService().list(query, userId, pages,
                null == request ? null : request.getSortField(),
                null == request ? null : request.getSortOrder());
        long total = getBaseService().count(query, userId);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 当月日程（日历视图）：POST /backend/sys/calendar/monthList
     * 请求体 { body: { year, month } }
     */
    @PostMapping("/monthList")
    public BaseResponse<List<SysCalendarVo>> monthList(@RequestBody SysCalendarListRequest request) {
        authorize(new AuthorizationContext(ACTION_MONTH_LIST));
        SysCalendarQuery query = null == request ? null : request.getBody();
        String userId = requireCurrentUserId();
        List<SysCalendarVo> list = getBaseService().monthList(query, userId);
        return BaseResponse.success(list, null);
    }

    /**
     * 今日日程（顶部弹窗）：GET /backend/sys/calendar/todayList
     */
    @GetMapping("/todayList")
    public BaseResponse<List<SysCalendarVo>> todayList() {
        authorize(new AuthorizationContext(ACTION_TODAY_LIST));
        String userId = requireCurrentUserId();
        SysCalendarQuery query = new SysCalendarQuery();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        query.setFieldStartTimeStart(cal.getTime());
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
        cal.set(java.util.Calendar.MINUTE, 59);
        cal.set(java.util.Calendar.SECOND, 59);
        query.setFieldStartTimeEnd(cal.getTime());
        List<SysCalendarVo> list = getBaseService().list(query, userId, null, null, null);
        return BaseResponse.success(list, null);
    }

    /**
     * 接收/拒绝日程：PATCH /backend/sys/calendar/accept?fieldId=${id}&accept=${true/false}
     * 仅限团队日程的接收人本人操作。
     */
    @PatchMapping("/accept")
    public BaseResponse<Void> accept(@RequestParam("fieldId") String fieldId,
                                     @RequestParam("accept") Boolean accept) {
        authorize(ACTION_ACCEPT, fieldId);
        getBaseService().accept(fieldId, accept);
        return BaseResponse.<Void>success("success", null, null);
    }

    private String requireCurrentUserId() {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (null == user) {
            throw new com.common.system.common.exception.AuthorizationException("UNAUTHORIZED", "登录已过期，请重新登录");
        }
        return user.getFieldId();
    }
}