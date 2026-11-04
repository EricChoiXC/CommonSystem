package com.common.system.sys.org.controller;

import com.common.system.common.base.controller.BaseCrudController;
import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.base.model.Pages;
import com.common.system.common.config.CurrentUserFilter;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.session.LoginSessionService;
import com.common.system.common.utils.StringUtil;
import com.common.system.sys.log.service.LogService;
import com.common.system.sys.log.utils.LogContextUtil;
import com.common.system.sys.org.model.SysOrgUser;
import com.common.system.sys.org.request.SysOrgLoginRequest;
import com.common.system.sys.org.request.SysOrgUserRequest;
import com.common.system.sys.org.resolver.UserResolver;
import com.common.system.sys.org.response.SysOrgUserResponse;
import com.common.system.sys.org.service.SysOrgUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.common.system.sys.org.properties.SysOrgUserProperties.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户维护接口
 *
 * @author Eric
 * @date 2026/8/3
 * @description 请求进入后端后的入口：只负责接收请求、基础校验、调用 Service 并返回统一响应。
 *              每个接口在执行核心业务逻辑前先通过 UserResolver 完成最终鉴权。
 */
@RestController
@RequestMapping("/backend/sys/org/user")
public class SysOrgUserController extends BaseCrudController<SysOrgUser, SysOrgUserService, UserResolver> {

    private final LoginSessionService loginSessionService;

    private final LogService logService;

    public SysOrgUserController(SysOrgUserService baseService, UserResolver userResolver,
                                LoginSessionService loginSessionService, LogService logService,
                                ApplicationEventPublisher eventPublisher) {
        super(baseService, userResolver, eventPublisher);
        this.loginSessionService = loginSessionService;
        this.logService = logService;
    }

    /**
     * 登录：POST /backend/sys/org/user/login
     * 登录前无身份，不经过 resolver 鉴权；成功签发会话 token（metadata）并返回用户信息（不含密码）；
     * 成功/失败均记录登录日志（失败仅记登录名与原因，不记录密码等敏感信息）。
     * 登录成功时 metadata 中携带 forceChangePwd 标志，指示是否需要强制修改密码。
     */
    @PostMapping("/login")
    public SysOrgUserResponse login(@RequestBody SysOrgLoginRequest request, HttpServletRequest httpRequest) {
        SysOrgUser body = null == request ? null : request.getBody();
        if (null == body || StringUtil.isEmpty(body.getFieldLoginName()) || StringUtil.isEmpty(body.getFieldPassword())) {
            return SysOrgUserResponse.failure("BAD_REQUEST", "登录名与密码不能为空");
        }
        String ip = LogContextUtil.getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        try {
            SysOrgUser user = getBaseService().login(body.getFieldLoginName(), body.getFieldPassword());
            String token = loginSessionService.create(user.getFieldId());
            // 检查是否需要强制修改密码
            boolean forceChangePwd = getBaseService().isPasswordChangeRequired(user.getFieldId());
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(CurrentUserFilter.LOGIN_TOKEN_HEADER, token);
            metadata.put("forceChangePwd", forceChangePwd);
            logService.recordLoginSuccess(user, ip, userAgent);
            return SysOrgUserResponse.success(user, metadata);
        } catch (IllegalArgumentException e) {
            // 登录失败：记录登录日志（登录名 + 失败原因），随后继续抛出由全局异常处理器统一转换
            logService.recordLoginFailure(body.getFieldLoginName(), e.getMessage(), ip, userAgent);
            throw e;
        }
    }

    /**
     * 登出：POST /backend/sys/org/user/logout
     * 从请求头 X-Login-Token 取出凭证并注销会话，幂等；同时记录登出日志。
     */
    @PostMapping("/logout")
    public SysOrgUserResponse logout(HttpServletRequest request) {
        logService.recordLogout(LogContextUtil.getClientIp(request), request.getHeader("User-Agent"));
        loginSessionService.remove(request.getHeader(CurrentUserFilter.LOGIN_TOKEN_HEADER));
        return SysOrgUserResponse.success(null);
    }

    /**
     * 新增：POST /backend/sys/org/user/save
     */
    @PostMapping("/save")
    public SysOrgUserResponse insert(@RequestBody SysOrgUserRequest request) {
        authorize(ACTION_SAVE, request.getBody());
        return SysOrgUserResponse.success(super.insert(request.getBody()));
    }

    /**
     * 编辑：PATCH /backend/sys/org/user/update
     * 密码不随本接口更新（仅通过 changePwd 接口修改），请求携带的密码会被忽略。
     */
    @PatchMapping("/update")
    public SysOrgUserResponse update(@RequestBody SysOrgUserRequest request) {
        authorize(ACTION_UPDATE, request.getBody());
        return SysOrgUserResponse.success(super.update(request.getBody()));
    }

    /**
     * 修改密码：PATCH /backend/sys/org/user/changePwd
     * 仅更新密码与最后修改密码时间，不影响用户其他字段；密码以 BCrypt 加密后入库。
     * 鉴权规则与编辑一致：SYS_ORG_ADMIN / SYS_ORG_USER_MANAGER / 用户本人。
     */
    @PatchMapping("/changePwd")
    public SysOrgUserResponse changePwd(@RequestBody SysOrgUserRequest request) {
        SysOrgUser body = request.getBody();
        if (null == body || StringUtil.isEmpty(body.getFieldId()) || StringUtil.isEmpty(body.getFieldPassword())) {
            return SysOrgUserResponse.failure("BAD_REQUEST", "fieldId and fieldPassword are required");
        }
        String fieldId = body.getFieldId().trim();
        authorize(ACTION_CHANGE_PWD, fieldId);
        getBaseService().changePassword(fieldId, body.getFieldPassword());
        return SysOrgUserResponse.success(null);
    }

    /**
     * 检查当前用户是否需要强制修改密码：GET /backend/sys/org/user/checkPwdChange
     * 返回 metadata 中携带 forceChangePwd 布尔值。
     */
    @GetMapping("/checkPwdChange")
    public BaseResponse<Void> checkPwdChange() {
        String userId = com.common.system.common.utils.UserUtil.getCurrentUserId();
        if (StringUtil.isEmpty(userId)) {
            return BaseResponse.failure("UNAUTHORIZED", "未登录");
        }
        boolean forceChangePwd = getBaseService().isPasswordChangeRequired(userId);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("forceChangePwd", forceChangePwd);
        return BaseResponse.success(null, metadata);
    }

    /**
     * 删除：DELETE /backend/sys/org/user/delete?fieldId=${id}
     */
    @DeleteMapping("/delete")
    public SysOrgUserResponse delete(@RequestParam("fieldId") String fieldId) {
        authorize(ACTION_DELETE, fieldId);
        super.deleteById(fieldId);
        return SysOrgUserResponse.success(null);
    }

    /**
     * 读取单条：GET /backend/sys/org/user/load?fieldId=${id}
     * 返回内容按当前用户可见性裁剪：管理员/人员维护者/本人返回完整信息，
     * 其余有读权用户仅返回普通信息字段，可见性说明在 metadata 中。
     */
    @GetMapping("/load")
    public SysOrgUserResponse load(@RequestParam("fieldId") String fieldId) {
        AuthorizationContext context = new AuthorizationContext(ACTION_LOAD, fieldId);
        authorize(context);
        String visibleFields = getBaseResolver().loadVisibleResult(context);
        if (StringUtil.isEmpty(visibleFields)) {
            return SysOrgUserResponse.failure("E_NOT_FOUND", "未找到该用户");
        }
        return SysOrgUserResponse.success(getBaseService().selectByIdWithFields(fieldId, visibleFields));
    }

    /**
     * 列表查询：POST /backend/sys/org/user/list
     */
    @PostMapping("/list")
    public BaseResponse<List<SysOrgUser>> selectList(@RequestBody SysOrgUserRequest request) {
        SysOrgUser criteria = request.getBody();
        if (null == criteria) {
            criteria = new SysOrgUser();
        }
        Pages pages = request.getPages();
        if (null == pages) {
            pages = new Pages();
        }
        AuthorizationContext context = new AuthorizationContext(ACTION_LOAD);
        authorize(context);
        String visibleFields = getBaseResolver().loadVisibleResult(context);
        if (StringUtil.isEmpty(visibleFields)) {
            return BaseResponse.failure("E_NOT_FOUND", "未找到该用户");
        }
        List<SysOrgUser> list = getBaseService().selectListWithFields(criteria, pages, visibleFields);
        return BaseResponse.success(list, null);
    }
}
