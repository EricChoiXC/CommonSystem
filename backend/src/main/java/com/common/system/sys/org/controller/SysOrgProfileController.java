package com.common.system.sys.org.controller;

import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.exception.AuthorizationException;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.org.request.SysOrgProfileSaveRequest;
import com.common.system.sys.org.response.SysOrgProfileResponse;
import com.common.system.sys.org.resolver.ProfileResolver;
import com.common.system.sys.org.service.SysOrgProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.common.system.sys.org.properties.SysOrgProfileProperties.*;

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
            + "openid 绑定状态与渠道开关（本任务占位 false，PF3-BE-002 接通）")
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
