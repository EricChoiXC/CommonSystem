package com.common.system.sys.org.request;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import lombok.Data;

import java.util.Date;

/**
 * 个人资料页保存请求
 *
 * @author Eric
 * @date 2026/11/11
 * @description 个人资料页（本人自助）保存请求载体（方案 4.2，POST /backend/sys/org/profile/save）。
 *              操作对象由会话定位（UserUtil.getCurrentUser()），接口不收 userId 参数；
 *              fieldId 仅作为「提交人员与会话用户一致」的一致性断言入参，由 Service 校验。
 *              <p>登录名（fieldLoginName）不接收、不更新：登录名为只读字段，
 *              由管理员在组织架构人员维护中修改（方案 4.2）。</p>
 *              字段级格式校验（手机号 ^1[3-9]\d{9}$、生日不晚于当天等）由 SysOrgProfileService
 *              承担（PF2-BE-002 实现），本类仅承载字段与描述。
 */
@Data
@Schema(description = "个人资料保存请求（本人自助，方案 4.2）：操作对象由会话定位，不收 userId；"
        + "登录名只读不接收不更新")
public class SysOrgProfileSaveRequest {

    /** 人员ID（sys_org_element.field_id），与会话用户一致性断言 */
    @Schema(description = "人员ID（须与会话用户一致，一致性断言）", requiredMode = RequiredMode.REQUIRED)
    private String fieldId;

    /** 用户名，可编辑；保存时同步 sys_org_element.field_name 并重算拼音列 */
    @Schema(description = "用户名，可编辑；保存时同步 sys_org_element.field_name 并重算拼音列"
            + "（field_pinyin / field_pinyin_short）")
    private String fieldName;

    /** 手机号，非空时格式 ^1[3-9]\d{9}$ */
    @Schema(description = "手机号，非空时格式 ^1[3-9]\\d{9}$")
    private String fieldPhone;

    /** 邮箱 */
    @Schema(description = "邮箱")
    private String fieldEmail;

    /** 联系地址，长度 ≤ 500 */
    @Schema(description = "联系地址，长度不超过 500")
    private String fieldAddress;

    /** 性别：0=其他 1=男 2=女 */
    @Schema(description = "性别：0=其他 1=男 2=女")
    private Integer fieldGender;

    /** 生日，不晚于当天 */
    @Schema(description = "生日，不晚于当天")
    private Date fieldBirthday;
}
