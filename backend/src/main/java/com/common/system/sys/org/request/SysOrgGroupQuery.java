package com.common.system.sys.org.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 群组查询条件
 *
 * @author Eric
 * @date 2026/11/4
 * @description 群组管理列表页与地址本群组页签共用的查询条件（04-API改动清单 1.1）：
 *              名称模糊、类型精确、是否全员可见、所有人精确；userId / admin 为
 *              服务端可见性过滤（02 文档 2.2 VisibleClause）的入参，由
 *              SysOrgGroupService 按当前登录人与角色计算后传入，不接受前端写入。
 */
@Data
@Schema(description = "群组列表查询条件")
public class SysOrgGroupQuery {

    /** 群组名称模糊匹配 */
    @Schema(description = "群组名称模糊匹配")
    private String fieldName;

    /** 群组类型：COMMON=公共 / PERSONAL=个人，为空表示全部 */
    @Schema(description = "群组类型：COMMON=公共 / PERSONAL=个人，为空表示全部",
            allowableValues = {"COMMON", "PERSONAL"})
    private String fieldType;

    /** 是否全员可见：仅对公共群组有语义；为空表示全部 */
    @Schema(description = "是否全员可见筛选：true/false，仅对公共群组有语义，为空表示全部")
    private Boolean fieldIsAllViewer;

    /** 所有人ID精确匹配（列表「所有人」列筛选） */
    @Schema(description = "所有人ID精确匹配")
    private String fieldOwnerId;

    /** 当前登录用户ID：可见性过滤入参，由 Service 按当前登录人填充，前端传值无效。
     *  @JsonIgnore 阻断请求体绑定（04-API改动清单 1.1 的 body 契约不含本字段），仅服务端 setter 写入。 */
    @Schema(description = "当前登录用户ID（可见性过滤入参，由服务端填充，前端传值无效）", hidden = true)
    @JsonIgnore
    private String userId;

    /** 当前用户是否管理员（SYS_ADMIN / SYS_ORG_ADMIN / SYS_ORG_GROUP_ADMIN）：
     *  管理员不做可见性过滤，由 Service 按角色计算，前端传值无效。
     *  字段名 admin + @JsonIgnore：Lombok 生成 isAdmin()/setAdmin(boolean)，
     *  OGNL 以 query.admin 取值（属性 admin 对应 boolean 的 is 前缀 getter）；
     *  @JsonIgnore 阻断请求体绑定，防止前端伪造管理员绕过可见性过滤。 */
    @Schema(description = "当前用户是否管理员（可见性过滤入参，由服务端按角色计算，前端传值无效）", hidden = true)
    @JsonIgnore
    private boolean admin;
}
