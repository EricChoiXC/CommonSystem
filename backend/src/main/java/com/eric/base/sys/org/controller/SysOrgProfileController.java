package com.eric.base.sys.org.controller;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.exception.AuthorizationException;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.request.SysOrgProfileOpenidBindRequest;
import com.eric.base.sys.org.request.SysOrgProfileSaveRequest;
import com.eric.base.sys.org.response.SysOrgProfileResponse;
import com.eric.base.sys.org.resolver.ProfileResolver;
import com.eric.base.sys.org.service.SysOrgProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

import static com.eric.base.sys.org.properties.SysOrgProfileProperties.*;

/**
 * 个人资料页接口
 *
 * @author Eric
 * @date 2026/11/11
 * @description 个人资料页（本人自助）后端入口（PF2-BE-002，方案 4.2）：只负责接收请求、
 *              执行最终鉴权并调用 Service 返回统一响应。沿用非 CRUD 先例
 *              （SysOrgAddressBookController）形态：不继承基类，自行注入
 *              {@link ProfileResolver} 实现私有 authorize(action)——未登录抛 UNAUTHORIZED、
 *              无权限抛 FORBIDDEN，复用 AuthorizationException + GlobalExceptionHandler。
 *              <p>本接口组不设权限码、不进 roles.yml（方案 5.6）：所有 action 由
 *              ProfileResolver「登录且账号启用」放行，SYS_ADMIN 由 BaseResolver 自动放行；
 *              接口一律不收 userId 参数，操作对象由会话定位，越权面以此消除。</p>
 *              <p>第三方账号绑定（PF3-BE-002，方案 4.4）：bindUrl/unbind 沿用 authorize
 *              （ACTION_BIND/ACTION_UNBIND）；callback 为第三方浏览器 302 回跳（无
 *              Authorization header）不执行 authorize，操作对象由 Redis state 内绑定的
 *              userId 定位，state 一次性取删防伪造/防重放，方法上以 @SecurityRequirements
 *              空注解取消全局 token 要求（先例 InitController）。</p>
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/org/profile")
@Tag(name = "个人资料", description = "个人资料页（ProfileView.vue）：本人自助加载与保存基础资料、"
        + "头像附件ID读取与第三方账号绑定状态展示；登录且账号启用即可访问，不设权限码")
public class SysOrgProfileController {

    private final SysOrgProfileService profileService;

    private final ProfileResolver profileResolver;

    public SysOrgProfileController(SysOrgProfileService profileService,
                                   ProfileResolver profileResolver) {
        this.profileService = profileService;
        this.profileResolver = profileResolver;
    }

    /**
     * 加载个人资料：GET /backend/sys/org/profile/load
     * 本人自助语义，操作对象由会话定位（UserUtil.getCurrentUserId()），不收 userId 参数。
     *
     * @return 统一响应，data 为个人资料响应（基础资料 + 头像附件ID + 绑定状态与渠道开关）
     */
    @GetMapping("/load")
    @Operation(summary = "加载个人资料", description = "鉴权动作 " + ACTION_LOAD + "（登录且启用即可，"
            + "不设权限码）；本人自助语义，操作对象由会话定位，不收 userId 参数；"
            + "返回基础资料（fieldName/fieldLoginName 只读/fieldPhone/fieldEmail/fieldAddress/"
            + "fieldGender/fieldBirthday/fieldJobStatus/fieldAccountStatus 只读）、"
            + "头像附件ID（sys_att_main 按 model_name+model_id+key 取 field_create_time 降序最新一条）、"
            + "openid 绑定状态与渠道开关（实时判定：总开关 system.openid_bind_enable + 渠道凭证齐全）")
    public BaseResponse<SysOrgProfileResponse> load() {
        authorize(ACTION_LOAD);
        return BaseResponse.success(profileService.load(), null);
    }

    /**
     * 保存个人资料：POST /backend/sys/org/profile/save
     * 请求体 SysOrgProfileSaveRequest（fieldId 为一致性断言入参，登录名只读不接收）。
     *
     * @param request 保存请求
     * @return 统一响应，data 为空
     */
    @PostMapping("/save")
    @Operation(summary = "保存个人资料", description = "鉴权动作 " + ACTION_SAVE + "（登录且启用即可，"
            + "不设权限码）；本人自助语义，操作对象由会话定位，不收 userId 参数；"
            + "请求体 SysOrgProfileSaveRequest，fieldId 须与会话用户一致（一致性断言），"
            + "登录名只读不接收不更新；校验失败抛 BAD_REQUEST 文案（用户名/手机号/邮箱/地址/性别/生日）")
    public BaseResponse<Void> save(@RequestBody SysOrgProfileSaveRequest request) {
        authorize(ACTION_SAVE);
        profileService.save(request);
        return BaseResponse.success(null, null);
    }

    /**
     * 生成第三方授权页跳转地址：POST /backend/sys/org/profile/openid/bindUrl
     * 请求体 SysOrgProfileOpenidBindRequest（channel 合法值 wechat/qq，其余校验在 Service）。
     *
     * @param request 绑定请求（仅承载渠道）
     * @return 统一响应，data 为 {"bindUrl": 第三方授权页完整 URL}（redirect_uri 已 urlencoded）
     */
    @PostMapping("/openid/bindUrl")
    @Operation(summary = "生成第三方授权页跳转地址", description = "鉴权动作 " + ACTION_BIND
            + "（登录且启用即可，不设权限码）；校验总开关（system.openid_bind_enable）与渠道凭证"
            + "（app_id/app_secret，桥接键优先、common_setting 表回退），失败抛 BAD_REQUEST"
            + "（绑定渠道无效/第三方账号绑定未开启/微信绑定未配置/QQ绑定未配置/回调地址未配置）；"
            + "生成一次性 state 存 Redis（绑定 userId+channel，TTL 300 秒，回调时取删防重放），"
            + "data 结构 {\"bindUrl\": 第三方授权页 URL}，redirect_uri="
            + "{redirect_base}/backend/sys/org/profile/openid/callback（已 urlencoded）")
    public BaseResponse<Map<String, Object>> buildOpenidBindUrl(
            @RequestBody SysOrgProfileOpenidBindRequest request) {
        authorize(ACTION_BIND);
        String bindUrl = profileService.buildBindUrl(null == request ? null : request.getChannel());
        return BaseResponse.success(Map.of("bindUrl", bindUrl), null);
    }

    /**
     * 第三方授权回调：GET /backend/sys/org/profile/openid/callback
     * 浏览器 302 跳入、无 Authorization header，不执行 authorize（执行会以未登录误伤
     * 正常回调）；操作对象由 Redis state 内绑定的 userId 定位，state 经 getAndDelete
     * 一次性取删，伪造/过期/重放/缺参均回 expired（缺参不落 JSON 错误页，方案 5.5）。
     *
     * @param code  第三方授权码（第三方错误回跳时缺失，Service 按 oauth_error 回跳）
     * @param state 一次性 state（与 bindUrl 阶段写入 Redis 的值一致；第三方异常回跳可能缺失）
     * @return 302 回跳 {redirect_base}/profile?bind={channel}:{reason}；redirect_base
     *         未配置时抛 IllegalArgumentException（全局异常处理统一转 BAD_REQUEST，
     *         文案「回调地址未配置」——ResponseEntity<Void> 泛型下裸 400 不便携带文本 body）
     */
    @GetMapping("/openid/callback")
    @Operation(summary = "第三方授权回调", description = "无需鉴权（第三方浏览器 302 回跳，"
            + "无 Authorization header，执行 authorize 会以未登录误伤正常回调）：取删 Redis state"
            + "（过期/伪造/缺参回 expired）、复验总开关（disabled）与渠道凭证（unconfigured）、"
            + "授权码换第三方用户信息（oauth_error）、openid 查重（计数>0 即 dup，无本人豁免）后"
            + "覆写 field_openid；302 回跳 {redirect_base}/profile?bind={channel}:{reason}"
            + "（success/expired/disabled/unconfigured/oauth_error/dup）；redirect_base 未配置时"
            + "400（回调地址未配置，经全局异常处理统一转换）")
    @ApiResponse(responseCode = "302", description = "回跳 {redirect_base}/profile?bind={channel}:{reason}")
    @SecurityRequirements
    public ResponseEntity<Void> openidCallback(@RequestParam(required = false) String code,
                                               @RequestParam(required = false) String state) {
        String target = profileService.handleCallback(code, state);
        if (null == target) {
            throw new IllegalArgumentException("回调地址未配置");
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }

    /**
     * 解绑第三方账号：POST /backend/sys/org/profile/openid/unbind
     * 请求体 SysOrgProfileOpenidBindRequest（channel 合法值 wechat/qq）。
     *
     * @param request 解绑请求（仅承载渠道）
     * @return 统一响应，data 为空
     */
    @PostMapping("/openid/unbind")
    @Operation(summary = "解绑第三方账号", description = "鉴权动作 " + ACTION_UNBIND
            + "（登录且启用即可，不设权限码）；本人自助语义，操作对象由会话定位，不收 userId 参数；"
            + "按渠道置空绑定信息，两渠道均未绑定时 field_openid 整列置 NULL；解绑未绑定渠道"
            + "幂等成功；不限制「至少保留一个绑定方式」（本系统不支持第三方登录，解绑不影响登录能力）")
    public BaseResponse<Void> unbindOpenid(@RequestBody SysOrgProfileOpenidBindRequest request) {
        authorize(ACTION_UNBIND);
        profileService.unbind(null == request ? null : request.getChannel());
        return BaseResponse.success(null, null);
    }

    /**
     * 执行最终鉴权：失败时抛出 {@link AuthorizationException}，由全局异常处理器统一转换。
     * <p>未登录或凭证已失效（当前用户为空）时抛出 {@code UNAUTHORIZED}，
     * 已登录但缺少权限时抛出 {@code FORBIDDEN}。</p>
     */
    private void authorize(String action) {
        if (null == UserUtil.getCurrentUser()) {
            throw new AuthorizationException("UNAUTHORIZED", "登录已过期，请重新登录");
        }
        if (!profileResolver.resolve(new AuthorizationContext(action))) {
            throw new AuthorizationException("FORBIDDEN", "no permission for action: " + action);
        }
    }
}
