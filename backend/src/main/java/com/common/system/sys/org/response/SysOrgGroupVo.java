package com.common.system.sys.org.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 群组响应对象
 *
 * @author Eric
 * @date 2026/11/4
 * @description 群组管理列表/详情/新建/编辑共用的响应载体（04-API改动清单 1.1）：
 *              list 仅返回基本字段（成员数 userCount 与所有人名称 fieldOwnerName 由 SQL
 *              COUNT / JOIN 得到，避免 N+1）；load 额外返回成员 userIds/userNames 与
 *              可见范围 viewerIds/viewerNames 供编辑/详情回显；save/update 以本对象返回保存后结果。
 */
@Data
@Schema(description = "群组响应对象：列表返回基本字段，详情额外返回成员与可见范围明细")
public class SysOrgGroupVo {

    /** 群组ID（与 sys_org_element.field_id 同值） */
    @Schema(description = "群组ID（与 sys_org_element.field_id 同值）")
    private String fieldId;

    /** 群组名称 */
    @Schema(description = "群组名称")
    private String fieldName;

    /** 群组类型：COMMON=公共 / PERSONAL=个人 */
    @Schema(description = "群组类型：COMMON=公共 / PERSONAL=个人", allowableValues = {"COMMON", "PERSONAL"})
    private String fieldType;

    /** 所有人ID（PERSONAL=归属人；COMMON=创建人） */
    @Schema(description = "所有人ID（PERSONAL=归属人；COMMON=创建人）")
    private String fieldOwnerId;

    /** 所有人名称（SQL JOIN sys_org_element 回填，列表直接展示） */
    @Schema(description = "所有人名称（JOIN sys_org_element 回填）")
    private String fieldOwnerName;

    /** 是否全员可见（公共群组语义：可见范围明细为空即 true；个人群组恒为 true） */
    @Schema(description = "是否全员可见：true=全员可见 / false=仅可见范围（个人群组恒为 true）")
    private Boolean fieldIsAllViewer;

    /** 是否启用（存储于 sys_org_element 主表） */
    @Schema(description = "是否启用")
    private Boolean fieldEnable;

    /** 成员数量（SQL COUNT(sys_org_group_user) 统计，列表直接展示） */
    @Schema(description = "成员数量（COUNT(sys_org_group_user) 统计）")
    private long userCount;

    /** 创建时间（存储于 sys_org_element 主表） */
    @Schema(description = "创建时间")
    private Date fieldCreateTime;

    /** 修改时间（存储于 sys_org_element 主表） */
    @Schema(description = "修改时间")
    private Date fieldUpdateTime;

    /** 成员人员ID列表（sys_org_group_user，load 详情返回，与成员保存顺序一致） */
    @Schema(description = "成员人员ID列表（load 详情返回）")
    private List<String> userIds;

    /** 成员人员名称列表（与 userIds 一一对应，load 详情返回） */
    @Schema(description = "成员人员名称列表（与 userIds 一一对应）")
    private List<String> userNames;

    /** 可见范围节点ID列表（sys_org_group_viewer，load 详情返回；为空表示全员可见） */
    @Schema(description = "可见范围节点ID列表（load 详情返回；为空表示全员可见）")
    private List<String> viewerIds;

    /** 可见范围节点名称列表（与 viewerIds 一一对应，load 详情返回） */
    @Schema(description = "可见范围节点名称列表（与 viewerIds 一一对应）")
    private List<String> viewerNames;
}
