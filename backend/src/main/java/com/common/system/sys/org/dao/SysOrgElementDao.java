package com.common.system.sys.org.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.org.mapper.SysOrgElementMapper;
import com.common.system.sys.org.model.SysOrgElement;
import com.common.system.sys.org.request.SysOrgElementQuery;
import com.common.system.sys.org.response.SysOrgElementVo;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * 组织架构元素数据访问对象
 *
 * @author Eric
 * @date 2026/8/8
 * @description 与 SysOrgElementMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysOrgElementDao extends BaseDao<SysOrgElement, SysOrgElementMapper> {

    public SysOrgElementDao(SysOrgElementMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 按用户查询其所属虚拟组织（仅 field_id、field_name，登录等场景组装用）
     */
    public List<SysOrgElement> selectVirtualIdNameListByUserId(String userId) {
        return getBaseMapper().selectVirtualIdNameListByUserId(userId);
    }

    /**
     * 按用户查询其所属岗位（仅 field_id、field_name，登录/权限预览场景组装用）
     */
    public List<SysOrgElement> selectPostIdNameListByUserId(String userId) {
        return getBaseMapper().selectPostIdNameListByUserId(userId);
    }

    /**
     * 管理页列表查询（含扩展字段与父级/负责人名称，支持单条件排序与分页）
     */
    public List<SysOrgElementVo> selectVoList(SysOrgElementQuery query, Pages pages,
                                              String sortField, String sortOrder) {
        return getBaseMapper().selectVoList(query, pages, sortField, sortOrder);
    }

    /**
     * 管理页列表总数（与列表过滤条件一致）
     */
    public long countVoList(SysOrgElementQuery query) {
        return getBaseMapper().countVoList(query);
    }

    /**
     * 管理页详情（含扩展字段与父级/负责人名称）
     */
    public SysOrgElementVo selectVoById(String fieldId) {
        return getBaseMapper().selectVoById(fieldId);
    }

    /**
     * 启停用：仅更新 field_enable 与修改人/修改时间
     */
    public int updateEnable(String fieldId, Boolean enable, String updaterId, Date updateTime) {
        return getBaseMapper().updateEnable(fieldId, enable, updaterId, updateTime);
    }

    /**
     * 层级重算：仅更新 field_hierarchy / field_level 与修改人/修改时间
     */
    public int updateHierarchy(String fieldId, String hierarchy, int level, String updaterId, Date updateTime) {
        return getBaseMapper().updateHierarchy(fieldId, hierarchy, level, updaterId, updateTime);
    }

    /**
     * 更新父级：显式写入 field_parent_id（parentId 为 null 时清空为根级）
     */
    public int updateParentId(String fieldId, String parentId) {
        return getBaseMapper().updateParentId(fieldId, parentId);
    }

    /**
     * 拼音存量回填游标查询：取 field_id 大于 lastId 且拼音两列存空的行（升序限量），
     * 供 SysStartupTask 启动时分批回填；lastId 传空串从头开始
     */
    public List<SysOrgElement> selectPinyinBackfillList(String lastId, int limit) {
        return getBaseMapper().selectPinyinBackfillList(lastId, limit);
    }

    /**
     * 拼音回写：仅更新 field_pinyin / field_pinyin_short 两列（含置 null）
     */
    public int updatePinyin(String fieldId, String fieldPinyin, String fieldPinyinShort) {
        return getBaseMapper().updatePinyin(fieldId, fieldPinyin, fieldPinyinShort);
    }

    /**
     * 轻量批量查询（不过滤启用状态），供父级名称/负责人名称回填
     */
    public List<SysOrgElement> selectIdNameByIds(List<String> ids) {
        return getBaseMapper().selectIdNameByIds(ids);
    }

    /**
     * 统计指定节点的直接下级数量（删除前校验用）
     */
    public long countChildren(String parentId) {
        return getBaseMapper().countChildren(parentId);
    }

    /* ==================== 角色管理 / 权限预览（sys.org.role / sys.org.preview） ==================== */

    /**
     * 按上级组织查询其直接关联的虚拟组织架构（权限预览用，仅返回启用节点）
     */
    public List<SysOrgElement> selectVirtualsByParentId(String parentId) {
        return getBaseMapper().selectVirtualsByParentId(parentId);
    }

    /* ==================== 角色镜像行维护（sys.org.role → sys_org_element 同事务双写） ==================== */

    /**
     * 角色镜像行同步更新：仅更新 field_name / field_code / field_enable 与修改人/修改时间，
     * 不触碰 field_level / field_org_type / field_is_virtual 等固定字段；
     * code 为 null 时同步清空镜像行编码。返回 0 行表示镜像行缺失，由调用方补插入自愈。
     */
    public int updateRoleMirror(String fieldId, String name, String code, Boolean enable,
                                String updaterId, Date updateTime) {
        return getBaseMapper().updateRoleMirror(fieldId, name, code, enable, updaterId, updateTime);
    }
}
