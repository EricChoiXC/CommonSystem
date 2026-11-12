package com.eric.base.sys.org.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 地址本群组成员人员查询请求
 *
 * @author Eric
 * @date 2026/11/6
 * @description 地址本「群组」页签右侧成员列表的请求载体（04-API改动清单 2 / ORG2-BE-004）：
 *              POST /backend/sys/org/addressbook/groupUsers，请求体 { groupId, orgTypes? }，
 *              orgTypes 为空时服务端默认 [3]（仅人员）；成员本身均为人员（field_org_type = 3），
 *              Service 将返回结果与 orgTypes 求交（不含 3 即空列表）。
 */
@Data
@Schema(description = "地址本群组成员查询请求：groupId 必传，orgTypes 为空默认 [3] 仅人员")
public class SysOrgAddressBookGroupUsersRequest {

    /** 群组 ID（sys_org_group.field_id，与 sys_org_element.field_id 同值），必传 */
    @Schema(description = "群组 ID（sys_org_group.field_id），必传；不存在返回 E_NOT_FOUND「未找到该群组」，"
            + "对当前用户不可见返回 FORBIDDEN「无权查看该群组」")
    private String groupId;

    /** 组织架构类型数字数组：1=机构 2=部门 3=人员 4=岗位 5=角色 6=群组；
     *  成员均为人员(3)，求交后不含 3 时返回空列表；为空默认 [3] */
    @Schema(description = "组织架构类型：1=机构 2=部门 3=人员 4=岗位 5=角色 6=群组；"
            + "成员均为人员(3)，不含 3 时返回空列表；为空默认 [3] 仅人员")
    private List<Integer> orgTypes;
}
