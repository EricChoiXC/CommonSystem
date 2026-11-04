package com.common.system.sys.notify.controller;

import com.common.system.common.base.controller.BaseCrudController;
import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.base.model.Pages;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.sys.notify.model.SysNotifyTodo;
import com.common.system.sys.notify.request.SysNotifyAdminListRequest;
import com.common.system.sys.notify.request.SysNotifyAdminQuery;
import com.common.system.sys.notify.request.SysNotifyBatchRequest;
import com.common.system.sys.notify.request.SysNotifySendRequest;
import com.common.system.sys.notify.request.SysNotifyTodoListRequest;
import com.common.system.sys.notify.request.SysNotifyTodoQuery;
import com.common.system.sys.notify.resolver.NotifyResolver;
import com.common.system.sys.notify.response.SysNotifyAdminTodoVo;
import com.common.system.sys.notify.response.SysNotifyTodoVo;
import com.common.system.sys.notify.service.SysNotifyTodoService;
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

import static com.common.system.sys.notify.properties.SysNotifyConstants.*;

/**
 * 待办接口
 *
 * @author Eric
 * @date 2026/8/12
 * @description 通知待办模块的请求入口：只负责接收请求、基础校验、执行最终鉴权并调用
 *              Service 返回统一响应。每个接口在执行核心业务逻辑前先通过 NotifyResolver
 *              完成鉴权：我的待办类动作登录即可（数据按本人过滤），发送待办与后台待办
 *              列表仅 SYS_NOTIFY_ADMIN 权限。接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}。
 */
@RestController
@RequestMapping("/backend/sys/notify/todo")
@Tag(name = "通知待办", description = "我的待办（列表/详情/已办/星标/删除/批量）登录即可并按本人过滤；发送待办与后台待办列表仅 SYS_NOTIFY_ADMIN")
public class SysNotifyTodoController extends BaseCrudController<SysNotifyTodo, SysNotifyTodoService, NotifyResolver> {

    public SysNotifyTodoController(SysNotifyTodoService baseService, NotifyResolver baseResolver,
                                   ApplicationEventPublisher eventPublisher) {
        super(baseService, baseResolver, eventPublisher);
    }

    /**
     * 发送待办：POST /backend/sys/notify/todo/send
     * 请求体 { body: { fieldName, fieldNotifyType, fieldModelName, fieldModelId, fieldUrl, fieldContent }, userIds: string[] }；
     * 仅 SYS_NOTIFY_ADMIN 权限账号调用，提供专属发送待办页面；
     * fieldModelName 必须为 model:name:map 中的合法 MODEL_KEY（由前端下拉选择）。
     *
     * @param request 发送请求，body 为待办内容，userIds 为接收人 ID 列表
     * @return 统一响应，data 为创建的主待办记录
     */
    @PostMapping("/send")
    @Operation(summary = "发送待办", description = "鉴权动作 sys.notify.todo.send（仅 SYS_NOTIFY_ADMIN）；请求体 "
            + "{ body: { fieldName, fieldNotifyType, fieldModelName, fieldModelId, fieldUrl, fieldContent }, "
            + "userIds: string[] }，fieldModelName 必须为 model:name:map 中的合法 MODEL_KEY")
    public BaseResponse<SysNotifyTodo> send(@RequestBody SysNotifySendRequest request) {
        authorize(new AuthorizationContext(ACTION_SEND));
        SysNotifyTodo todo = null == request ? null : request.getBody();
        if (null == todo) {
            todo = new SysNotifyTodo();
        }
        List<String> userIds = null == request ? null : request.getUserIds();
        return BaseResponse.success(getBaseService().send(todo, userIds), null);
    }

    /**
     * 来源模块选项：GET /backend/sys/notify/todo/modelOptions
     * 读取 model:name:map 缓存返回 MODEL_KEY → MODEL_NAME 映射，
     * 供发送待办页与列表筛选区的「来源模块」下拉使用；登录即可访问。
     *
     * @return 统一响应，data 为 MODEL_KEY → MODEL_NAME 映射
     */
    @GetMapping("/modelOptions")
    @Operation(summary = "来源模块选项", description = "鉴权动作 sys.notify.todo.modelOptions（登录即可）；"
            + "读取 model:name:map 缓存返回 MODEL_KEY → MODEL_NAME 映射，供发送待办页与筛选区下拉使用")
    public BaseResponse<Map<String, String>> modelOptions() {
        authorize(new AuthorizationContext(ACTION_MODEL_OPTIONS));
        return BaseResponse.success(getBaseService().modelOptions(), null);
    }

    /**
     * 我的待办列表：POST /backend/sys/notify/todo/myList
     * 请求体 { body: { fieldNotifyType, fieldModelName, fieldIsStar, fieldName, fieldReceiveTimeStart, fieldReceiveTimeEnd },
     *           pages: { pageNo, pageSize, needPage }, sortField, sortOrder }；
     * 只显示当前用户接收到的待办，总数经 metadata.total 返回。
     *
     * @param request 列表请求（条件 + 分页 + 单条件排序），排序字段取 receiveTime
     * @return 统一响应，data 为当前用户的待办列表，metadata.total 为总数
     */
    @PostMapping("/myList")
    @Operation(summary = "我的待办列表", description = "鉴权动作 sys.notify.todo.myList（登录即可）；请求体 "
            + "{ body: { fieldNotifyType, fieldModelName, fieldIsStar, fieldName, fieldReceiveTimeStart, "
            + "fieldReceiveTimeEnd }, pages, sortField, sortOrder }，只显示当前用户接收到的待办，"
            + "排序字段取 receiveTime")
    public BaseResponse<List<SysNotifyTodoVo>> myList(@RequestBody SysNotifyTodoListRequest request) {
        authorize(new AuthorizationContext(ACTION_MY_LIST));
        SysNotifyTodoQuery query = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysNotifyTodoVo> list = getBaseService().myList(query, pages,
                null == request ? null : request.getSortField(),
                null == request ? null : request.getSortOrder());
        long total = getBaseService().myCount(query);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 待办详情：GET /backend/sys/notify/todo/load?fieldId=${id}
     * 待阅类型点击详情触发跳转时，系统自动将该待阅转已办。
     *
     * @param fieldId 待办接收记录 ID
     * @return 统一响应，data 为待办详情
     */
    @GetMapping("/load")
    @Operation(summary = "待办详情", description = "鉴权动作 sys.notify.todo.load；待阅类型点击详情触发跳转时，"
            + "系统自动将该待阅转已办")
    public BaseResponse<SysNotifyTodoVo> load(
            @Parameter(description = "待办接收记录 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_LOAD, fieldId);
        return BaseResponse.success(getBaseService().loadDetail(fieldId), null);
    }

    /**
     * 转已办：PATCH /backend/sys/notify/todo/done?fieldId=${id}
     *
     * @param fieldId 待办接收记录 ID
     * @return 统一响应
     */
    @PatchMapping("/done")
    @Operation(summary = "转已办", description = "鉴权动作 sys.notify.todo.done；将当前用户的待办接收记录置为已办")
    public BaseResponse<Void> done(
            @Parameter(description = "待办接收记录 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_DONE, fieldId);
        getBaseService().markDone(fieldId);
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 转未办：PATCH /backend/sys/notify/todo/undone?fieldId=${id}
     *
     * @param fieldId 待办接收记录 ID
     * @return 统一响应
     */
    @PatchMapping("/undone")
    @Operation(summary = "转未办", description = "鉴权动作 sys.notify.todo.undone；将当前用户的待办接收记录置回未办")
    public BaseResponse<Void> undone(
            @Parameter(description = "待办接收记录 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_UNDONE, fieldId);
        getBaseService().markUndone(fieldId);
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 星标：PATCH /backend/sys/notify/todo/star?fieldId=${id}（未星标则置星标，已星标则取消）
     *
     * @param fieldId 待办接收记录 ID
     * @return 统一响应
     */
    @PatchMapping("/star")
    @Operation(summary = "切换星标", description = "鉴权动作 sys.notify.todo.star；未星标则置星标，已星标则取消")
    public BaseResponse<Void> star(
            @Parameter(description = "待办接收记录 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_STAR, fieldId);
        getBaseService().toggleStar(fieldId);
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 删除：DELETE /backend/sys/notify/todo/delete?fieldId=${id}
     * 删除只是对 sys_notify_todo_target.field_is_del 置 true，不是真的删除记录。
     *
     * @param fieldId 待办接收记录 ID
     * @return 统一响应
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除待办", description = "鉴权动作 sys.notify.todo.delete；仅对 "
            + "sys_notify_todo_target.field_is_del 置 true（软删除），不删除主记录")
    public BaseResponse<Void> delete(
            @Parameter(description = "待办接收记录 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_DELETE, fieldId);
        getBaseService().deleteByTarget(fieldId);
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 批量已办：POST /backend/sys/notify/todo/batchDone，请求体 { ids: string[] }
     *
     * @param request 批量请求，ids 为待办接收记录 ID 列表
     * @return 统一响应
     */
    @PostMapping("/batchDone")
    @Operation(summary = "批量转已办", description = "鉴权动作 sys.notify.todo.batchDone；请求体 "
            + "{ ids: string[] }")
    public BaseResponse<Void> batchDone(@RequestBody SysNotifyBatchRequest request) {
        authorize(new AuthorizationContext(ACTION_BATCH_DONE));
        getBaseService().batchDone(idsOf(request));
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 批量转未办：POST /backend/sys/notify/todo/batchUndone，请求体 { ids: string[] }
     *
     * @param request 批量请求，ids 为待办接收记录 ID 列表
     * @return 统一响应
     */
    @PostMapping("/batchUndone")
    @Operation(summary = "批量转未办", description = "鉴权动作 sys.notify.todo.batchUndone；请求体 "
            + "{ ids: string[] }")
    public BaseResponse<Void> batchUndone(@RequestBody SysNotifyBatchRequest request) {
        authorize(new AuthorizationContext(ACTION_BATCH_UNDONE));
        getBaseService().batchUndone(idsOf(request));
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 批量星标：POST /backend/sys/notify/todo/batchStar，请求体 { ids: string[] }
     *
     * @param request 批量请求，ids 为待办接收记录 ID 列表
     * @return 统一响应
     */
    @PostMapping("/batchStar")
    @Operation(summary = "批量星标", description = "鉴权动作 sys.notify.todo.batchStar；请求体 "
            + "{ ids: string[] }")
    public BaseResponse<Void> batchStar(@RequestBody SysNotifyBatchRequest request) {
        authorize(new AuthorizationContext(ACTION_BATCH_STAR));
        getBaseService().batchStar(idsOf(request));
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 批量删除：POST /backend/sys/notify/todo/batchDelete，请求体 { ids: string[] }
     * 删除只是软删除（field_is_del 置 true）。
     *
     * @param request 批量请求，ids 为待办接收记录 ID 列表
     * @return 统一响应
     */
    @PostMapping("/batchDelete")
    @Operation(summary = "批量删除", description = "鉴权动作 sys.notify.todo.batchDelete；请求体 "
            + "{ ids: string[] }，仅软删除（field_is_del 置 true）")
    public BaseResponse<Void> batchDelete(@RequestBody SysNotifyBatchRequest request) {
        authorize(new AuthorizationContext(ACTION_BATCH_DELETE));
        getBaseService().batchDelete(idsOf(request));
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 后台待办列表：POST /backend/sys/notify/todo/adminList
     * 请求体 { body: { fieldNotifyType, fieldModelName, fieldName, fieldCreateTimeStart, fieldCreateTimeEnd },
     *           pages: { pageNo, pageSize, needPage }, sortField, sortOrder }；
     * 按 sys_notify_todo 表可见所有待办（含接收人数/已办人数），总数经 metadata.total 返回。
     *
     * @param request 后台列表请求（条件 + 分页 + 单条件排序），排序字段取 createTime
     * @return 统一响应，data 为含接收人数/已办人数的待办列表，metadata.total 为总数
     */
    @PostMapping("/adminList")
    @Operation(summary = "后台待办列表", description = "鉴权动作 sys.notify.todo.adminList（仅 SYS_NOTIFY_ADMIN）；"
            + "请求体 { body: { fieldNotifyType, fieldModelName, fieldName, fieldCreateTimeStart, "
            + "fieldCreateTimeEnd }, pages, sortField, sortOrder }，返回含接收人数/已办人数的待办列表，"
            + "排序字段取 createTime")
    public BaseResponse<List<SysNotifyAdminTodoVo>> adminList(@RequestBody SysNotifyAdminListRequest request) {
        authorize(new AuthorizationContext(ACTION_ADMIN_LIST));
        SysNotifyAdminQuery query = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysNotifyAdminTodoVo> list = getBaseService().adminList(query, pages,
                null == request ? null : request.getSortField(),
                null == request ? null : request.getSortOrder());
        long total = getBaseService().adminCount(query);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 提取批量操作的 ids；请求体缺失或为空时抛参数异常，避免空集合执行。
     */
    private static List<String> idsOf(SysNotifyBatchRequest request) {
        if (null == request) {
            return null;
        }
        return request.getIds();
    }
}
