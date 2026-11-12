package com.eric.base.sys.org.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.eric.base.sys.org.model.SysOrgElement;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 组织架构元素响应对象
 *
 * @author Eric
 * @date 2026/8/10
 * @description 组织架构管理页列表/详情返回对象：在公共字段基础上，
 *              按 field_org_type LEFT JOIN 各扩展表（sys_org_org / sys_org_dept /
 *              sys_org_user / sys_org_post）并归一化同一语义字段（负责人、电话、邮箱、地址等），
 *              供前端按类型展示差异列；field_password 仅在保存/编辑时接收，任何响应体不输出。
 *              v1.2 补充拼音两列与创建人/修改人名称、上级/负责人编号（fieldParentCode /
 *              fieldLeaderCode 列表页不展示，仅导入导出 converter 消费）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysOrgElementVo extends SysOrgElement {

    /** 名称拼音全称（小写，后端按 field_name 自动生成，前端不传、用户不可改） */
    @Schema(description = "名称拼音全称（小写，后端按 field_name 自动生成）")
    private String fieldPinyin;

    /** 名称拼音简称（首字母小写，后端按 field_name 自动生成，前端不传、用户不可改） */
    @Schema(description = "名称拼音简称（首字母小写，后端按 field_name 自动生成）")
    private String fieldPinyinShort;

    /** 负责人用户ID（机构/部门/岗位） */
    private String fieldLeaderId;

    /** 负责人名称（机构/部门/岗位，由 Service 回填） */
    private String fieldLeaderName;

    /** 负责人编号（负责人用户元素编码；列表页不展示，仅导入导出 converter 消费） */
    @Schema(description = "负责人编号（负责人用户元素编码；列表页不展示，仅导入导出消费）")
    private String fieldLeaderCode;

    /** 上级组织架构编号（按 field_parent_id 关联元素编码；列表页不展示，仅导入导出 converter 消费） */
    @Schema(description = "上级组织架构编号（按 field_parent_id 关联元素编码；列表页不展示，仅导入导出消费）")
    private String fieldParentCode;

    /** 创建人名称（按 field_creator_id 关联组织元素名称，由 SQL LEFT JOIN cu 回填） */
    @Schema(description = "创建人名称（按 field_creator_id 关联组织元素名称）")
    private String fieldCreatorName;

    /** 修改人名称（按 field_updater_id 关联组织元素名称，由 SQL LEFT JOIN uu 回填） */
    @Schema(description = "修改人名称（按 field_updater_id 关联组织元素名称）")
    private String fieldUpdaterName;

    /** 联系电话（机构/部门/人员） */
    private String fieldPhone;

    /** 联系邮箱（机构/人员） */
    private String fieldEmail;

    /** 联系地址（机构/部门/人员） */
    private String fieldAddress;

    /** 性别（人员）：1男 2女 0其他 */
    private Integer fieldGender;

    /** 生日（人员） */
    private Date fieldBirthday;

    /** 登录名（人员，仅管理员/本人可见，Service 层裁剪） */
    private String fieldLoginName;

    /** 在岗状态（人员）：1在职 0离职 2停职 3休假 */
    private Integer fieldJobStatus;

    /** 账号状态（人员）：1正常 2锁定 3密码过期 0禁用 4注销 */
    private Integer fieldAccountStatus;

    /** 密码：仅在保存/编辑人员时接收（WRITE_ONLY），BCrypt 加密后入库，任何响应体不输出 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String fieldPassword;
}
