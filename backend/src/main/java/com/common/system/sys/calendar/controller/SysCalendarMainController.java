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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("${project.request.prefix}/sys/calendar")
@Tag(name = "日程", description = "日程管理：创建/编辑/删除、列表与日历视图、接收/拒绝团队日程；仅创建者或接收人可见")
public class SysCalendarMainController extends BaseCrudController<SysCalendarMain, SysCalendarMainService, CalendarResolver> {

    public SysCalendarMainController(SysCalendarMainService baseService, CalendarResolver baseResolver,
                                     ApplicationEventPublisher eventPublisher) {
        super(baseService, baseResolver, eventPublisher);
    }

    /**
     * 新增日程：POST /backend/sys/calendar/save
     * 请求体 { body: { fieldName, fieldStartTime, fieldEndTime, fieldType, fieldContent, personIds } }
     *
     * @param request 保存请求，personIds 为团队日程的接收人 ID 列表
     * @return 统一响应，data 为新建的日程
     */
    @PostMapping("/save")
    @Operation(summary = "新增日程", description = "鉴权动作 sys.calendar.save；请求体 "
            + "{ body: { fieldName, fieldStartTime, fieldEndTime, fieldType, fieldContent, personIds } }，"
            + "personIds 非空即为团队日程，接收人待阅")
    public BaseResponse<SysCalendarMain> save(@RequestBody SysCalendarSaveRequest request) {
        authorize(new AuthorizationContext(ACTION_SAVE));
        SysCalendarMainRequest body = null == request ? null : request.getBody();
        return BaseResponse.success(getBaseService().create(body), null);
    }

    /**
     * 编辑日程：PATCH /backend/sys/calendar/update
     * 请求体 { body: { fieldId, fieldName, fieldStartTime, fieldEndTime, fieldType, fieldContent, personIds } }
     *
     * @param request 保存请求，body 必须携带 fieldId
     * @return 统一响应，data 为更新后的日程
     */
    @PatchMapping("/update")
    @Operation(summary = "编辑日程", description = "鉴权动作 sys.calendar.update；请求体 "
            + "{ body: { fieldId, fieldName, fieldStartTime, fieldEndTime, fieldType, fieldContent, personIds } }，"
            + "接收人列表全量替换")
    public BaseResponse<SysCalendarMain> update(@RequestBody SysCalendarSaveRequest request) {
        SysCalendarMainRequest body = null == request ? null : request.getBody();
        authorize(ACTION_UPDATE, null == body ? null : body.getFieldId());
        return BaseResponse.success(getBaseService().update(body), null);
    }

    /**
     * 删除日程：DELETE /backend/sys/calendar/delete?fieldId=${id}
     * 仅限创建者本人删除；团队日程删除后，接收人待阅统一转入已阅。
     *
     * @param fieldId 日程 ID
     * @return 统一响应
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除日程", description = "鉴权动作 sys.calendar.delete；仅限创建者本人删除；"
            + "团队日程删除后接收人待阅统一转入已阅")
    public BaseResponse<Void> delete(
            @Parameter(description = "日程 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_DELETE, fieldId);
        getBaseService().delete(fieldId);
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 日程详情：GET /backend/sys/calendar/load?fieldId=${id}
     * 返回日程基本信息 + 接收人列表（含接收状态）。
     *
     * @param fieldId 日程 ID
     * @return 统一响应，data 为日程详情（含接收人接收状态）
     */
    @GetMapping("/load")
    @Operation(summary = "日程详情", description = "鉴权动作 sys.calendar.load；返回日程基本信息与接收人列表"
            + "（含接收状态）")
    public BaseResponse<SysCalendarVo> load(
            @Parameter(description = "日程 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_LOAD, fieldId);
        return BaseResponse.success(getBaseService().loadDetail(fieldId), null);
    }

    /**
     * 日程列表：POST /backend/sys/calendar/list
     * 请求体 { body: { fieldName, fieldType, fieldAccept, fieldStartTimeStart, fieldStartTimeEnd },
     *           pages: { pageNo, pageSize, needPage }, sortField, sortOrder }
     * 固定查询条件为自己创建的日程或自己作为接收人的日程。
     *
     * @param request 列表请求（条件 + 分页 + 单条件排序），排序字段取 startTime / createTime
     * @return 统一响应，data 为日程列表，metadata.total 为总数
     */
    @PostMapping("/list")
    @Operation(summary = "日程列表", description = "鉴权动作 sys.calendar.list；请求体 "
            + "{ body: { fieldName, fieldType, fieldAccept, fieldStartTimeStart, fieldStartTimeEnd }, pages, "
            + "sortField, sortOrder }；固定查询自己创建或自己作为接收人的日程，排序字段取 startTime / createTime")
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
     *
     * @param request 月视图请求，body 携带 year 与 month
     * @return 统一响应，data 为当月与自己相关的日程列表
     */
    @PostMapping("/monthList")
    @Operation(summary = "当月日程（日历视图）", description = "鉴权动作 sys.calendar.monthList；请求体 "
            + "{ body: { year, month } }，返回与自己相关的当月日程，供日历视图渲染")
    public BaseResponse<List<SysCalendarVo>> monthList(@RequestBody SysCalendarListRequest request) {
        authorize(new AuthorizationContext(ACTION_MONTH_LIST));
        SysCalendarQuery query = null == request ? null : request.getBody();
        String userId = requireCurrentUserId();
        List<SysCalendarVo> list = getBaseService().monthList(query, userId);
        return BaseResponse.success(list, null);
    }

    /**
     * 今日日程（顶部弹窗）：GET /backend/sys/calendar/todayList
     *
     * @return 统一响应，data 为开始时间落在今天 00:00:00 ~ 23:59:59 的日程列表
     */
    @GetMapping("/todayList")
    @Operation(summary = "今日日程（顶部弹窗）", description = "鉴权动作 sys.calendar.todayList；返回开始时间"
            + "落在当天 00:00:00 ~ 23:59:59 的日程，供顶部提醒弹窗使用")
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
     *
     * @param fieldId 日程 ID
     * @param accept  true 接收、false 拒绝
     * @return 统一响应
     */
    @PatchMapping("/accept")
    @Operation(summary = "接收/拒绝日程", description = "鉴权动作 sys.calendar.accept；仅限团队日程的接收人本人操作，"
            + "accept=true 接收、accept=false 拒绝")
    public BaseResponse<Void> accept(
            @Parameter(description = "日程 ID", required = true) @RequestParam("fieldId") String fieldId,
            @Parameter(description = "true 接收 / false 拒绝", required = true) @RequestParam("accept") Boolean accept) {
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