package com.common.system.sys.org.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 群组保存/更新请求对象
 *
 * @author Eric
 * @date 2026/11/4
 * @description 群组新建(save)与编辑(update)共用的请求载体（04-API改动清单 1.1）：
 *              基本字段 + 成员 userIds + 可见范围 viewerIds（全量替换明细，先删后插）。
 *              fieldOwnerId 与 fieldIsAllViewer 由 SysOrgGroupService 派生维护，不接受前端写入：
 *              新建时 fieldOwnerId 取当前登录人、编辑时保持原值（管理员维护他人群组也不改归属）；
 *              fieldIsAllViewer 一律按「viewerIds 是否为空」派生（02 文档 1.3/D4），
 *              请求体中的该字段被忽略；PERSONAL 类型不写可见范围明细、恒为全员可见。
 */
@Data
@Schema(description = "群组保存/更新请求：fieldOwnerId 与 fieldIsAllViewer 由服务端派生，前端传值无效")
public class SysOrgGroupRequest {

    /** 群组ID：新建时由前端经 initID 预取，编辑时必传 */
    @Schema(description = "群组ID：新建时经 /backend/common/initID 预取，编辑时必传")
    private String fieldId;

    /** 群组名称，最长 200（与 sys_org_element.field_name 一致） */
    @Schema(description = "群组名称，最长 200")
    private String fieldName;

    /** 群组类型：COMMON=公共 / PERSONAL=个人；普通用户仅可 PERSONAL（Service 强制） */
    @Schema(description = "群组类型：COMMON=公共 / PERSONAL=个人；普通用户仅可创建个人群组",
            allowableValues = {"COMMON", "PERSONAL"})
    private String fieldType;

    /** 群组成员人员ID列表（sys_org_group_user，全量替换） */
    @Schema(description = "群组成员人员ID列表（field_org_type = 3，保存时全量替换）")
    private List<String> userIds;

    /** 可见范围组织架构节点ID列表（sys_org_group_viewer，限机构/部门/岗位/人员，全量替换；为空即全员可见） */
    @Schema(description = "可见范围节点ID列表（机构/部门/岗位/人员，保存时全量替换；为空即全员可见；个人群组忽略）")
    private List<String> viewerIds;

    /** 是否启用：null 时保存默认启用 */
    @Schema(description = "是否启用：null 时默认 true")
    private Boolean fieldEnable;
}
