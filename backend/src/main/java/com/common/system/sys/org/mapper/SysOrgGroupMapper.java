package com.common.system.sys.org.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.org.model.SysOrgElement;
import com.common.system.sys.org.model.SysOrgGroup;
import com.common.system.sys.org.request.SysOrgGroupQuery;
import com.common.system.sys.org.response.SysOrgGroupVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 群组数据访问接口
 *
 * @author Eric
 * @date 2026/11/4
 * @description 群组模块数据访问：sys_org_group 扩展表（与 sys_org_element 同 field_id 一对一）
 *              与两张明细表 sys_org_group_user / sys_org_group_viewer 的 SQL 统一收敛于本接口
 *              与同名 XML（参照 SysOrgRoleMapper 承载 sys_org_role_element / sys_org_role_auth
 *              批量语句的先例），明细 Dao 为本 Mapper 的分职责门面。
 *              <p>列表可见性过滤（02-群组功能分析 2.2 VisibleClause）的入参
 *              {@code userId} / {@code admin} 由 {@link SysOrgGroupQuery} 携带：
 *              Service 按当前登录人与角色填充后传入 selectVoList / countVoList，
 *              Mapper 方法签名不再单列两个参数，保证列表与 count 口径同源。</p>
 */
@Mapper
public interface SysOrgGroupMapper extends IBaseMapper<SysOrgGroup> {

    /* ==================== 群组列表 / 详情（sys.org.group，可见性过滤） ==================== */

    /**
     * 群组列表分页查询：LEFT JOIN sys_org_element 取名称与启用状态、JOIN 所有人名称、
     * 派生表统计成员数 userCount；按 query.userId / query.admin 做可见性过滤
     * （admin 短路 1=1）。sortField / sortOrder 必须为白名单值
     * （createTime / updateTime / name，默认 createTime 降序），经 XML choose/when 映射。
     */
    List<SysOrgGroupVo> selectVoList(@Param("query") SysOrgGroupQuery query,
                                     @Param("pages") Pages pages,
                                     @Param("sortField") String sortField,
                                     @Param("sortOrder") String sortOrder);

    /**
     * 群组列表总数：与 {@link #selectVoList} 过滤条件（含可见性）完全一致，
     * 供分页 metadata.total 使用。
     */
    long countVoList(@Param("query") SysOrgGroupQuery query);

    /**
     * 群组详情：基本字段 + 所有人名称 + 成员数（成员/可见范围明细由
     * selectUserIdsByGroupId / selectViewerIdsByGroupId 等明细查询组装）。
     * 详情不做可见性过滤（区别「不存在」与「不可见」，FORBIDDEN 提示由 Service 返回）。
     */
    SysOrgGroupVo selectVoById(@Param("fieldId") String fieldId);

    /**
     * 按 id 统计对指定用户可见的群组数（load / 地址本 groupUsers 的「可见才可看」校验用，
     * 见 02-群组功能分析 2.3 与 04-API改动清单 2）：可见性口径与 {@link #selectVoList} 的
     * VisibleClause 完全一致；可见返回 1，不可见或不存在返回 0。
     * userId / admin 经 query 携带（Service 按当前登录人与角色填充）。
     */
    long selectCountVisibleById(@Param("groupId") String groupId,
                                @Param("query") SysOrgGroupQuery query);

    /* ==================== 成员明细（sys_org_group_user） ==================== */

    /**
     * 按群组查询成员人员ID列表（编辑/详情回显用），按 field_user_id 排序保证结果确定。
     */
    List<String> selectUserIdsByGroupId(@Param("groupId") String groupId);

    /**
     * 地址本群组页签成员人员明细（ORG2-BE-004）：按群组返回启用人员（field_org_type = 3）
     * 完整元素信息，列结构与地址本 selectList 同构（SysOrgElement），供前端按 orgTypes
     * 求交后直接复用地址本回填/合并逻辑；群组可见性校验由 Service 前置完成，
     * 本语句不重复过滤。按 field_create_time ASC 保证结果确定。
     */
    List<SysOrgElement> selectUsersByGroupId(@Param("groupId") String groupId);

    /**
     * 删除群组全部成员明细（编辑时全量替换前清空）。
     */
    int deleteGroupUsersByGroupId(@Param("groupId") String groupId);

    /**
     * 批量插入成员明细（userIds 必须非空，空列表由调用方跳过，与先例 insertRoleElements 一致）。
     */
    int insertGroupUsers(@Param("groupId") String groupId, @Param("userIds") List<String> userIds);

    /* ==================== 可见范围明细（sys_org_group_viewer） ==================== */

    /**
     * 按群组查询可见范围节点ID列表（编辑/详情回显用），按 field_viewer_id 排序保证结果确定。
     */
    List<String> selectViewerIdsByGroupId(@Param("groupId") String groupId);

    /**
     * 删除群组全部可见范围明细（编辑时全量替换前清空）。
     */
    int deleteGroupViewersByGroupId(@Param("groupId") String groupId);

    /**
     * 批量插入可见范围明细（viewerIds 必须非空，空列表由调用方跳过）。
     */
    int insertGroupViewers(@Param("groupId") String groupId, @Param("viewerIds") List<String> viewerIds);
}
