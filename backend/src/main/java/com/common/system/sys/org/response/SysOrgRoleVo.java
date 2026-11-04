package com.common.system.sys.org.response;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 角色响应对象
 *
 * @author Eric
 * @date 2026/8/10
 * @description 角色管理列表/详情/新建/编辑共用的响应与请求载体：
 *              list 仅返回基本字段；load 额外返回关联组织架构（elementIds/elementNames）
 *              与已选权限（authIds）；save/update 接收 elementIds/authIds 并全量替换关联表。
 */
@Data
public class SysOrgRoleVo {

    /** 角色 ID */
    private String fieldId;

    /** 角色名称 */
    private String fieldName;

    /** 角色编码 */
    private String fieldCode;

    /** 是否启用 */
    private Boolean fieldEnable;

    /** 创建时间 */
    private Date fieldCreateTime;

    /** 修改时间 */
    private Date fieldUpdateTime;

    /** 关联组织架构 ID 列表（sys_org_role_element） */
    private List<String> elementIds;

    /** 关联组织架构名称列表（编辑回显用，与 elementIds 一一对应） */
    private List<String> elementNames;

    /** 已选权限 ID 列表（sys_org_role_auth） */
    private List<String> authIds;
}
