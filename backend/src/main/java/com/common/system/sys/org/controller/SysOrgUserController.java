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
import com.common.system.sys.i18n.service.SysI18nUserLanguageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 *              每个接口在执行核心业务逻辑前先通过 UserResolver 完成最终鉴权，
 *              登录/登出因登录前无身份或仅凭 token 注销，不经过 Resolver。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/org/user")
@Tag(name = "组织架构-用户", description = "用户登录登出、增改查删与密码维护；查询类按当前用户可见性裁剪字段")
public class SysOrgUserController extends BaseCrudController<SysOrgUser, SysOrgUserService, UserResolver> {

    private static final Logger log = LoggerFactory.getLogger(SysOrgUserController.class);

    private final LoginSessionService loginSessionService;

    private final LogService logService;

    private final SysI18nUserLanguageService i18nUserLanguageService;

    public SysOrgUserController(SysOrgUserService baseService, UserResolver userResolver,
                                LoginSessionService loginSessionService, LogService logService,
                                SysI18nUserLanguageService i18nUserLanguageService,
                                ApplicationEventPublisher eventPublisher) {
        super(baseService, userResolver, eventPublisher);
        this.loginSessionService = loginSessionService;
        this.logService = logService;
        this.i18nUserLanguageService = i18nUserLanguageService;
    }

    /**
     * 登录：POST /backend/sys/org/user/login
     * 登录前无身份，不经过 resolver 鉴权；成功签发会话 token（metadata）并返回用户信息（不含密码）；
     * 成功/失败均记录登录日志（失败仅记登录名与原因，不记录密码等敏感信息）。
     * 登录成功时 metadata 中携带 forceChangePwd 标志，指示是否需要强制修改密码。
     * 登录语言登记（D3 契约）：body 携带 fieldLanguage 时，登录成功后 upsert
     * sys_i18n_user_language（存在更新、不存在插入）；未携带不登记，既有登录零回归；
     * 无效语言码由 i18n 服务忽略并回退默认语言（A4），登记失败不影响登录结果。
     *
     * @param request      登录请求，body 需含 fieldLoginName 与 fieldPassword，可选 fieldLanguage
     * @param httpRequest  原始请求，用于提取客户端 IP 与 User-Agent 记录登录日志
     * @return 统一响应，data 为用户信息（不含密码），metadata 含 X-Login-Token 与 forceChangePwd；
     *         登录名或密码为空返回 BAD_REQUEST
     */
    @PostMapping("/login")
    @Operation(summary = "登录", description = "无需鉴权（登录前无身份，不走 resolver）；请求体 "
            + "{ body: { fieldLoginName, fieldPassword, fieldLanguage? } }，成功后 metadata 返回 "
            + "X-Login-Token 与 forceChangePwd，成功/失败均记录登录日志；"
            + "fieldLanguage 携带时登录成功后登记用户语言（D3），未携带时行为不变")
    @SecurityRequirements
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
            // 登录语言登记（D3）：仅 body 携带 fieldLanguage 时登记，未携带与既有登录行为完全一致；
            // 登记失败仅记录日志，不影响登录结果（i18n 登记属增强能力）
            if (null != request && StringUtil.isNotEmpty(request.getFieldLanguage())) {
                try {
                    i18nUserLanguageService.upsertLanguage(user.getFieldId(), request.getFieldLanguage());
                } catch (RuntimeException e) {
                    log.warn("failed to register user language after login, userId={}, language={}",
                            user.getFieldId(), request.getFieldLanguage(), e);
                }
            }
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
     *
     * @param request 原始请求，token 取自请求头 X-Login-Token
     * @return 统一响应；凭证缺失或已失效时同样返回成功（幂等）
     */
    @PostMapping("/logout")
    @Operation(summary = "登出", description = "无需鉴权（凭证失效也返回成功，不走 resolver）；"
            + "从请求头 X-Login-Token 读取凭证并注销会话，幂等，同时记录登出日志")
    @SecurityRequirements
    public SysOrgUserResponse logout(HttpServletRequest request) {
        logService.recordLogout(LogContextUtil.getClientIp(request), request.getHeader("User-Agent"));
        loginSessionService.remove(request.getHeader(CurrentUserFilter.LOGIN_TOKEN_HEADER));
        return SysOrgUserResponse.success(null);
    }

    /**
     * 新增：POST /backend/sys/org/user/save
     *
     * @param request 用户请求，body 为用户对象
     * @return 统一响应，data 为保存后的用户
     */
    @PostMapping("/save")
    @Operation(summary = "新增用户", description = "鉴权动作 sys.org.user.save；请求体 { body: 用户对象 }")
    public SysOrgUserResponse insert(@RequestBody SysOrgUserRequest request) {
        authorize(ACTION_SAVE, request.getBody());
        return SysOrgUserResponse.success(super.insert(request.getBody()));
    }

    /**
     * 编辑：PATCH /backend/sys/org/user/update
     * 密码不随本接口更新（仅通过 changePwd 接口修改），请求携带的密码会被忽略。
     *
     * @param request 用户请求，body 必须携带 fieldId
     * @return 统一响应，data 为更新后的用户
     */
    @PatchMapping("/update")
    @Operation(summary = "编辑用户", description = "鉴权动作 sys.org.user.update；请求体 { body: 用户对象 }，"
            + "密码不随本接口更新，携带的密码会被忽略")
    public SysOrgUserResponse update(@RequestBody SysOrgUserRequest request) {
        authorize(ACTION_UPDATE, request.getBody());
        return SysOrgUserResponse.success(super.update(request.getBody()));
    }

    /**
     * 修改密码：PATCH /backend/sys/org/user/changePwd
     * 仅更新密码与最后修改密码时间，不影响用户其他字段；密码以 BCrypt 加密后入库。
     * 鉴权规则与编辑一致：SYS_ORG_ADMIN / SYS_ORG_USER_MANAGER / 用户本人。
     *
     * @param request 用户请求，body 需含 fieldId 与 fieldPassword
     * @return 统一响应；缺 fieldId 或 fieldPassword 返回 BAD_REQUEST
     */
    @PatchMapping("/changePwd")
    @Operation(summary = "修改密码", description = "鉴权动作 sys.org.user.changePwd（SYS_ORG_ADMIN / "
            + "SYS_ORG_USER_MANAGER / 用户本人）；请求体 { body: { fieldId, fieldPassword } }，"
            + "密码以 BCrypt 加密后入库，不修改其他字段")
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
     *
     * @return 统一响应，metadata.forceChangePwd 为是否需要强制改密；未登录返回 UNAUTHORIZED
     */
    @GetMapping("/checkPwdChange")
    @Operation(summary = "检查是否需强制改密", description = "鉴权动作 sys.org.user.checkPwdChange；"
            + "metadata.forceChangePwd 指示当前用户是否需要强制修改密码")
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
     *
     * @param fieldId 用户 ID
     * @return 统一响应
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除用户", description = "鉴权动作 sys.org.user.delete")
    public SysOrgUserResponse delete(
            @Parameter(description = "用户 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_DELETE, fieldId);
        super.deleteById(fieldId);
        return SysOrgUserResponse.success(null);
    }

    /**
     * 读取单条：GET /backend/sys/org/user/load?fieldId=${id}
     * 返回内容按当前用户可见性裁剪：管理员/人员维护者/本人返回完整信息，
     * 其余有读权用户仅返回普通信息字段，可见性说明在 metadata 中。
     *
     * @param fieldId 用户 ID
     * @return 统一响应，data 为按可见性裁剪后的用户信息；不可见或不存在返回 E_NOT_FOUND
     */
    @GetMapping("/load")
    @Operation(summary = "用户详情", description = "鉴权动作 sys.org.user.load；返回内容按当前用户可见性裁剪"
            + "（管理员/人员维护者/本人返回完整信息，其余仅返回普通信息字段），可见性说明在 metadata 中")
    public SysOrgUserResponse load(
            @Parameter(description = "用户 ID", required = true) @RequestParam("fieldId") String fieldId) {
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
     *
     * @param request 用户列表请求（body 为查询条件，pages 为分页）
     * @return 统一响应，data 为按当前用户可见性裁剪字段后的用户列表
     */
    @PostMapping("/list")
    @Operation(summary = "用户列表查询", description = "鉴权动作 sys.org.user.list；请求体 "
            + "{ body: 查询条件, pages }，返回字段按当前用户可见性裁剪")
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
