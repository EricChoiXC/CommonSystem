package com.common.system.sys.org.request;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import lombok.Data;

/**
 * 第三方账号绑定/解绑请求
 *
 * @author Eric
 * @date 2026/11/11
 * @description 个人资料页第三方账号绑定/解绑请求载体（PF3-BE-002，方案 4.4，
 *              POST /backend/sys/org/profile/openid/bindUrl 与 /openid/unbind 共用）。
 *              操作对象由会话定位（UserUtil.getCurrentUserId()），接口不收 userId 参数；
 *              channel 合法值 wechat/qq，格式与配置校验由 SysOrgProfileService 承担，
 *              本类仅承载字段与描述。
 */
@Data
@Schema(description = "第三方账号绑定/解绑请求（本人自助，方案 4.4）：操作对象由会话定位，不收 userId；"
        + "channel 合法值 wechat/qq")
public class SysOrgProfileOpenidBindRequest {

    /** 绑定渠道：wechat=微信扫码登录，qq=QQ 互联 */
    @Schema(description = "绑定渠道，合法值：wechat（微信扫码登录）/ qq（QQ 互联）",
            requiredMode = RequiredMode.REQUIRED)
    private String channel;
}
