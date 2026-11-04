package com.common.system.sys.org.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.org.model.SysOrgElement;
import com.common.system.sys.org.request.SysOrgElementQuery;
import com.common.system.sys.org.response.SysOrgElementVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 组织架构元素数据访问接口
 *
 * @author Eric
 * @date 2026/8/5
 * @description 组织架构树主表(sys_org_element)数据访问
 */
@Mapper
public interface SysOrgElementMapper extends IBaseMapper<SysOrgElement> {

    /**
     * 按用户查询其所属虚拟组织（轻量：仅 field_id、field_name），供登录等场景组装；
     * 虚拟组织与用户的关系由 sys_org_virtual_user 表承载。
     */
    List<SysOrgElement> selectVirtualIdNameListByUserId(@Param("userId") String userId);

    /**
     * 按用户查询其所属岗位（轻量：仅 field_id、field_name），供登录/权限预览场景组装；
     * 岗位与用户的关系由 sys_org_post_user 表承载。
     */
    List<SysOrgElement> selectPostIdNameListByUserId(@Param("userId") String userId);

    /**
     * 管理页列表查询：按查询条件 + 单条件排序 + 分页返回组织架构元素（含扩展字段与父级/负责人名称）。
     * sortField / sortOrder 必须为白名单值（见 SysOrgElementProperties），由 Service 层归一化后传入。
     */
    List<SysOrgElementVo> selectVoList(@Param("query") SysOrgElementQuery query,
                                       @Param("pages") Pages pages,
                                       @Param("sortField") String sortField,
                                       @Param("sortOrder") String sortOrder);

    /**
     * 管理页列表总数：与 {@link #selectVoList} 过滤条件一致，供分页 metadata.total 使用。
     */
    long countVoList(@Param("query") SysOrgElementQuery query);

    /**
     * 管理页详情：按 id 读取组织架构元素（含扩展字段与父级/负责人名称）。
     */
    SysOrgElementVo selectVoById(@Param("fieldId") String fieldId);

    /**
     * 启停用：仅更新 field_enable 与修改人/修改时间，不触碰其他字段。
     */
    int updateEnable(@Param("fieldId") String fieldId,
                     @Param("enable") Boolean enable,
                     @Param("updaterId") String updaterId,
                     @Param("updateTime") Date updateTime);

    /**
     * 层级重算：仅更新 field_hierarchy / field_level 与修改人/修改时间（供父级变更后递归修正子树）。
     */
    int updateHierarchy(@Param("fieldId") String fieldId,
                        @Param("hierarchy") String hierarchy,
                        @Param("level") int level,
                        @Param("updaterId") String updaterId,
                        @Param("updateTime") Date updateTime);

    /**
     * 更新父级：显式写入 field_parent_id（parentId 为 null 时清空为根级），
     * 供父级变更场景使用——通用 update 的 <if> 语义无法将字段置 NULL。
     */
    int updateParentId(@Param("fieldId") String fieldId, @Param("parentId") String parentId);

    /**
     * 轻量批量查询：仅返回 field_id、field_name（不过滤启用状态），
     * 供父级名称/负责人名称等关联信息回填使用。
     */
    List<SysOrgElement> selectIdNameByIds(@Param("ids") List<String> ids);

    /**
     * 统计指定节点的直接下级数量（删除前校验用）。
     */
    long countChildren(@Param("parentId") String parentId);

    /* ==================== 角色管理 / 权限预览（sys.org.role / sys.org.preview） ==================== */

    /**
     * 按上级组织查询其直接关联的虚拟组织架构（权限预览用，仅返回启用节点）。
     */
    List<SysOrgElement> selectVirtualsByParentId(@Param("parentId") String parentId);

    /* ==================== 角色镜像行维护（sys.org.role → sys_org_element 同事务双写） ==================== */

    /**
     * 角色镜像行同步更新：仅更新 field_name / field_code / field_enable 与修改人/修改时间，
     * 不触碰 field_level / field_org_type / field_is_virtual 等固定字段
     * （通用 update 对这三项无条件赋值，禁止用于镜像行更新，见 01-角色纳入组织架构分析.md 3.1）；
     * code 为 null 时同步清空镜像行编码。返回 0 行表示镜像行缺失，由调用方补插入自愈。
     */
    int updateRoleMirror(@Param("fieldId") String fieldId,
                         @Param("name") String name,
                         @Param("code") String code,
                         @Param("enable") Boolean enable,
                         @Param("updaterId") String updaterId,
                         @Param("updateTime") Date updateTime);
}
