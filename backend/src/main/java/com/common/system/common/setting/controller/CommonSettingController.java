package com.common.system.common.setting.controller;

import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.setting.service.CommonSettingService;
import com.common.system.common.utils.RoleUtil;
import com.common.system.common.utils.StringUtil;
import com.common.system.common.utils.UserUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.common.system.common.setting.properties.CommonSettingProperties.*;

/**
 * 公共配置接口
 *
 * @author Eric
 * @date 2026/8/25
 * @description 提供系统公共配置的读取与更新接口。
 *              GET  /backend/common/setting/load   — 读取全部系统配置
 *              PATCH /backend/common/setting/update — 更新单个配置项（仅 SYS_ADMIN）
 *              <p>load 用于登录页等未登录场景读取邮箱/电话登录开关，故不设角色限制；
 *              update 的鉴权统一经 {@link RoleUtil#isSuperAdmin} 判定（超管角色编码见
 *              {@code CommonProperties.ROLE_ADMIN}），避免散落的权限码字面量。</p>
 */
@RestController
@RequestMapping("/backend/common/setting")
public class CommonSettingController {

    private final CommonSettingService commonSettingService;

    public CommonSettingController(CommonSettingService commonSettingService) {
        this.commonSettingService = commonSettingService;
    }

    /**
     * 读取系统配置：返回所有 system 类型的配置项
     * GET /backend/common/setting/load
     * <p>登录页需要据此显示邮箱/电话登录入口（登录前无身份），故本接口不做角色限制，
     * 与调用方共用同一份 system 配置读取逻辑。</p>
     */
    @GetMapping("/load")
    public BaseResponse<Map<String, String>> load() {
        Map<String, String> values = commonSettingService.getValues(TYPE_SYSTEM);
        return BaseResponse.success(values, null);
    }

    /**
     * 更新单个配置项
     * PATCH /backend/common/setting/update
     * 请求体：{ "fieldKey": "xxx", "fieldValue": "yyy" }
     * 仅 SYS_ADMIN 权限可执行。
     */
    @PatchMapping("/update")
    public BaseResponse<Void> update(@RequestBody Map<String, String> body) {
        // 鉴权：仅 SYS_ADMIN 可更新配置
        if (!isSysAdmin()) {
            return BaseResponse.failure("FORBIDDEN", "仅系统管理员可修改公共配置");
        }

        String fieldKey = body.get("fieldKey");
        String fieldValue = body.get("fieldValue");
        if (StringUtil.isEmpty(fieldKey) || fieldValue == null) {
            return BaseResponse.failure("BAD_REQUEST", "fieldKey 与 fieldValue 不能为空");
        }
        try {
            commonSettingService.updateValue(TYPE_SYSTEM, fieldKey, fieldValue);
            return BaseResponse.success(null, null);
        } catch (IllegalArgumentException e) {
            return BaseResponse.failure("BAD_REQUEST", e.getMessage());
        }
    }

    /**
     * 当前用户是否为系统管理员（SYS_ADMIN）。
     */
    private static boolean isSysAdmin() {
        return RoleUtil.isSuperAdmin(UserUtil.getCurrentUser());
    }
}
