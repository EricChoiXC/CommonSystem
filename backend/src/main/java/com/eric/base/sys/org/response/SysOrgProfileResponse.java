package com.eric.base.sys.org.response;

import com.eric.base.sys.org.model.SysOrgUserOpenidDo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 个人资料页响应对象
 *
 * @author Eric
 * @date 2026/11/11
 * @description 个人资料页（本人自助）load 接口的响应载体（方案 4.2，
 *              GET /backend/sys/org/profile/load），由 SysOrgProfileService 组装
 *              （PF2-BE-002 实现）：基础资料 JOIN sys_org_element 读取；
 *              avatarAttId 附件查询（sys_att_main 按 field_model_name='sys_org_user' +
 *              field_model_id=当前用户 + field_key='profile'，field_create_time 降序取最新，
 *              未上传为 null）与 openid 反序列化（SysOrgUserOpenidDo，方案 4.1）均在 PF2-BE-002。
 *              <p>渠道开关（openidBindEnabled / wechatEnabled / qqEnabled）依赖
 *              system.openid_bind_enable 总开关与各渠道凭证齐全性（PF3-BE-001 配置化），
 *              本任务先以常量 false 占位，PF3-BE-001 完成后接通。</p>
 */
@Data
@Schema(description = "个人资料响应对象（本人自助 load，方案 4.2）：基础资料 + 头像附件ID + "
        + "第三方账号绑定状态 + 渠道开关")
public class SysOrgProfileResponse {

    /** 用户名（JOIN sys_org_element.field_name） */
    @Schema(description = "用户名（JOIN sys_org_element.field_name）")
    private String fieldName;

    /** 登录名（只读回显，不提供自助修改） */
    @Schema(description = "登录名（只读回显；由管理员在组织架构人员维护中修改）")
    private String fieldLoginName;

    /** 手机号 */
    @Schema(description = "手机号")
    private String fieldPhone;

    /** 邮箱 */
    @Schema(description = "邮箱")
    private String fieldEmail;

    /** 联系地址 */
    @Schema(description = "联系地址")
    private String fieldAddress;

    /** 性别：0=其他 1=男 2=女 */
    @Schema(description = "性别：0=其他 1=男 2=女")
    private Integer fieldGender;

    /** 生日 */
    @Schema(description = "生日")
    private Date fieldBirthday;

    /** 岗位/工作状态（sys_org_user.field_job_status） */
    @Schema(description = "工作状态（sys_org_user.field_job_status）")
    private Integer fieldJobStatus;

    /** 账号状态（只读展示：是否启用，不提供自助修改） */
    @Schema(description = "账号状态（只读展示）")
    private Integer fieldAccountStatus;

    /** 头像附件ID（sys_att_main 按 field_model_name='sys_org_user' + field_model_id=当前用户
     *  + field_key='profile'，以 field_create_time 降序取最新一条；未上传为 null，方案 4.3） */
    @Schema(description = "头像附件ID（sys_att_main 按 field_model_name='sys_org_user' + "
            + "field_model_id=当前用户 + field_key='profile' 以 field_create_time 降序取最新一条；未上传为 null）")
    private String avatarAttId;

    /** 第三方账号绑定状态（sys_org_user.field_openid 反序列化，方案 4.1；未绑定渠道内层为 null） */
    @Schema(description = "第三方账号绑定状态（sys_org_user.field_openid 反序列化，"
            + "含第三方昵称与绑定时间；未绑定渠道内层为 null）")
    private SysOrgUserOpenidDo openid;

    /** 第三方账号绑定总开关（system.openid_bind_enable；本任务先以常量 false 占位，PF3-BE-001 完成后接通） */
    @Schema(description = "第三方账号绑定总开关（system.openid_bind_enable；本任务以 false 占位，PF3-BE-001 接通）")
    private Boolean openidBindEnabled;

    /** 微信渠道开放 = 总开关开启且微信凭证齐全（本任务先以常量 false 占位） */
    @Schema(description = "微信渠道开放（总开关开启且微信凭证齐全；本任务以 false 占位，PF3-BE-001 接通）")
    private Boolean wechatEnabled;

    /** QQ 渠道开放 = 总开关开启且 QQ 凭证齐全（本任务先以常量 false 占位） */
    @Schema(description = "QQ 渠道开放（总开关开启且 QQ 凭证齐全；本任务以 false 占位，PF3-BE-001 接通）")
    private Boolean qqEnabled;
}
