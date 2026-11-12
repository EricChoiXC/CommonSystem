package com.eric.base.sys.org.service;

import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.org.dao.SysOrgAuthDao;
import com.eric.base.sys.org.dao.SysOrgElementDao;
import com.eric.base.sys.org.dao.SysOrgRoleDao;
import com.eric.base.sys.org.model.SysOrgAuth;
import com.eric.base.sys.org.model.SysOrgElement;
import com.eric.base.sys.org.model.SysOrgRole;
import com.eric.base.sys.org.response.PreviewTreeChildVo;
import com.eric.base.sys.org.response.RolePreviewVo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限预览业务服务
 *
 * @author Eric
 * @date 2026/8/10
 * @description 权限预览页的后端业务，仅对「用户」（人员类型且非虚拟组织）节点提供完整预览：
 *              <ul>
 *                <li>preview：选中用户的概览数据——角色列表/权限列表与登录权限计算模型
 *                    （SysOrgRoleMapper.selectListByUserId / selectAuthsByRoleIds）完全一致，
 *                    即用户自身组织元素、所属岗位、所属虚拟组织、直属机构/部门链路关联角色的并集；
 *                    关联组织架构展示上述角色来源组织（直属上级、所属岗位、所属虚拟组织）</li>
 *                <li>treeChildren：权限树懒加载子节点——用户节点展开显示直接关联角色、直属上级、
 *                    所属岗位、所属虚拟组织（与登录模型四类来源一致），角色节点展开显示直接绑定的权限，
 *                    权限节点为叶子</li>
 *              </ul>
 *              非用户节点（机构/部门/岗位/虚拟组织）仅保留基础兜底逻辑，前端不会触发加载。
 */
@Service
public class SysOrgRolePreviewService {

    /** 权限树节点类型：组织架构 / 角色 / 权限 */
    public static final String NODE_TYPE_ELEMENT = "element";
    public static final String NODE_TYPE_ROLE = "role";
    public static final String NODE_TYPE_AUTH = "auth";

    /** 人员组织类型（sys_org_element.field_org_type: 1=机构 2=部门 3=人员 4=岗位） */
    private static final int ORG_TYPE_USER = 3;

    private final SysOrgElementDao elementDao;
    private final SysOrgRoleDao roleDao;
    private final SysOrgAuthDao authDao;

    public SysOrgRolePreviewService(SysOrgElementDao elementDao, SysOrgRoleDao roleDao, SysOrgAuthDao authDao) {
        this.elementDao = elementDao;
        this.roleDao = roleDao;
        this.authDao = authDao;
    }

    /**
     * 权限预览概览数据：选中用户时角色/权限按登录权限计算模型读取。
     *
     * @param elementId 选中的组织架构元素 ID
     * @return 组织信息 + 关联组织架构 + 角色列表 + 权限并集
     */
    public RolePreviewVo preview(String elementId) {
        if (StringUtil.isEmpty(elementId)) {
            throw new IllegalArgumentException("elementId must not be blank");
        }
        String id = elementId.trim();
        SysOrgElement element = elementDao.selectById(id);
        if (null == element) {
            throw new IllegalArgumentException("组织架构不存在或已删除");
        }
        RolePreviewVo vo = new RolePreviewVo();
        vo.setElement(element);
        if (isUserElement(element)) {
            // 与登录权限计算一致：用户自身组织 + 所属岗位 + 所属虚拟组织 + 直属机构/部门链路
            List<SysOrgRole> roles = roleDao.selectListByUserId(id);
            vo.setRoles(roles);
            List<String> roleIds = roles.stream().map(SysOrgRole::getFieldId).toList();
            vo.setAuths(roleIds.isEmpty() ? List.of() : authDao.selectAuthsByRoleIds(roleIds));
            // 关联组织架构：角色来源组织（直属上级、所属岗位、所属虚拟组织）
            vo.setRelatedOrgs(userRelatedOrgs(element));
        } else {
            // 非用户节点（前端不触发，防御兜底）：按直接关联展示
            vo.setRelatedOrgs(relatedOrgs(element));
            List<SysOrgRole> roles = roleDao.selectListByElementId(id);
            vo.setRoles(roles);
            List<String> roleIds = roles.stream().map(SysOrgRole::getFieldId).toList();
            vo.setAuths(roleIds.isEmpty() ? List.of() : authDao.selectAuthsByRoleIds(roleIds));
        }
        return vo;
    }

    /**
     * 权限树子节点懒加载。
     *
     * @param type 节点类型：element=组织架构、role=角色；auth 为叶子不加载
     * @param id   节点 ID
     * @return 子节点列表
     */
    public List<PreviewTreeChildVo> treeChildren(String type, String id) {
        if (StringUtil.isEmpty(id)) {
            return List.of();
        }
        return switch (type) {
            case NODE_TYPE_ROLE -> roleChildren(id.trim());
            case NODE_TYPE_ELEMENT -> elementChildren(id.trim());
            default -> List.of();
        };
    }

    /* ==================== 私有方法 ==================== */

    /**
     * 是否为「用户」节点：人员类型（org_type = 3）且非虚拟组织。
     * 虚拟组织即使类型为人员也不作为登录用户处理（无登录态、无岗位/虚拟组织归属）。
     */
    private static boolean isUserElement(SysOrgElement element) {
        return null != element && !element.isFieldIsVirtual() && element.getFieldOrgType() == ORG_TYPE_USER;
    }

    /**
     * 用户的关联组织架构：角色来源组织（直属上级组织、所属岗位、所属虚拟组织），去重。
     */
    private List<SysOrgElement> userRelatedOrgs(SysOrgElement user) {
        List<SysOrgElement> related = new ArrayList<>();
        // 直属上级组织（挂载部门/机构）
        if (StringUtil.isNotEmpty(user.getFieldParentId())) {
            SysOrgElement parent = elementDao.selectById(user.getFieldParentId());
            if (null != parent) {
                related.add(parent);
            }
        }
        // 所属岗位
        related.addAll(elementDao.selectPostIdNameListByUserId(user.getFieldId()));
        // 所属虚拟组织
        related.addAll(elementDao.selectVirtualIdNameListByUserId(user.getFieldId()));
        return distinct(related);
    }

    private static List<SysOrgElement> distinct(List<SysOrgElement> elements) {
        List<SysOrgElement> result = new ArrayList<>();
        for (SysOrgElement element : elements) {
            if (result.stream().noneMatch(e -> e.getFieldId().equals(element.getFieldId()))) {
                result.add(element);
            }
        }
        return result;
    }

    /**
     * 关联组织架构（非用户节点兜底）：选中非虚拟组织 → 直接关联的虚拟组织架构；选中虚拟组织 → 其归属（上级）组织架构。
     */
    private List<SysOrgElement> relatedOrgs(SysOrgElement element) {
        if (element.isFieldIsVirtual()) {
            // 虚拟组织：展示其归属（上级）组织架构
            if (StringUtil.isEmpty(element.getFieldParentId())) {
                return List.of();
            }
            SysOrgElement owner = elementDao.selectById(element.getFieldParentId());
            return null == owner ? List.of() : List.of(owner);
        }
        // 非虚拟组织：展示其直接关联的虚拟组织架构
        return elementDao.selectVirtualsByParentId(element.getFieldId());
    }

    /**
     * 组织架构节点展开：用户节点按登录模型四类来源展开，其余节点按直接关联展开。
     */
    private List<PreviewTreeChildVo> elementChildren(String elementId) {
        SysOrgElement element = elementDao.selectById(elementId);
        if (null == element) {
            return List.of();
        }
        if (isUserElement(element)) {
            return userChildren(element);
        }
        return orgChildren(element);
    }

    /**
     * 用户节点展开：直接关联角色 + 直属上级组织 + 所属岗位 + 所属虚拟组织
     * （与登录权限计算模型 SysOrgRoleMapper.selectListByUserId 的四类来源一致）。
     */
    private List<PreviewTreeChildVo> userChildren(SysOrgElement user) {
        List<PreviewTreeChildVo> children = new ArrayList<>();
        // 用户自身组织元素直接关联的角色
        for (SysOrgRole role : roleDao.selectListByElementId(user.getFieldId())) {
            children.add(new PreviewTreeChildVo(NODE_TYPE_ROLE, role.getFieldId(),
                    displayName(role.getFieldName(), role.getFieldCode()), "角色"));
        }
        // 直属上级组织架构（挂载部门/机构）
        if (StringUtil.isNotEmpty(user.getFieldParentId())) {
            SysOrgElement parent = elementDao.selectById(user.getFieldParentId());
            if (null != parent) {
                children.add(new PreviewTreeChildVo(NODE_TYPE_ELEMENT, parent.getFieldId(),
                        displayName(parent.getFieldName(), parent.getFieldCode()), "上级"));
            }
        }
        // 所属岗位（sys_org_post_user）
        for (SysOrgElement post : elementDao.selectPostIdNameListByUserId(user.getFieldId())) {
            children.add(new PreviewTreeChildVo(NODE_TYPE_ELEMENT, post.getFieldId(),
                    displayName(post.getFieldName(), post.getFieldCode()), "岗位"));
        }
        // 所属虚拟组织（sys_org_virtual_user）
        for (SysOrgElement virtual : elementDao.selectVirtualIdNameListByUserId(user.getFieldId())) {
            children.add(new PreviewTreeChildVo(NODE_TYPE_ELEMENT, virtual.getFieldId(),
                    displayName(virtual.getFieldName(), virtual.getFieldCode()), "虚拟组织"));
        }
        return children;
    }

    /**
     * 非用户组织节点展开：直接关联角色 + 直属上级 + 直接关联的虚拟组织架构。
     * <p>虚拟组织节点不返回直属上级：其 field_parent_id 指向挂载组织，已在树的上层，
     * 继续展开上级会与「挂载组织 → 虚拟组织」形成 O↔V 无限互指环。</p>
     */
    private List<PreviewTreeChildVo> orgChildren(SysOrgElement element) {
        List<PreviewTreeChildVo> children = new ArrayList<>();
        // 直接关联角色
        for (SysOrgRole role : roleDao.selectListByElementId(element.getFieldId())) {
            children.add(new PreviewTreeChildVo(NODE_TYPE_ROLE, role.getFieldId(),
                    displayName(role.getFieldName(), role.getFieldCode()), "角色"));
        }
        // 直属上级组织架构（虚拟组织不展开上级，防环）
        if (!element.isFieldIsVirtual() && StringUtil.isNotEmpty(element.getFieldParentId())) {
            SysOrgElement parent = elementDao.selectById(element.getFieldParentId());
            if (null != parent) {
                children.add(new PreviewTreeChildVo(NODE_TYPE_ELEMENT, parent.getFieldId(),
                        displayName(parent.getFieldName(), parent.getFieldCode()), "上级"));
            }
        }
        // 直接关联的虚拟组织架构
        for (SysOrgElement virtual : elementDao.selectVirtualsByParentId(element.getFieldId())) {
            children.add(new PreviewTreeChildVo(NODE_TYPE_ELEMENT, virtual.getFieldId(),
                    displayName(virtual.getFieldName(), virtual.getFieldCode()), "虚拟组织"));
        }
        return children;
    }

    /**
     * 角色节点展开：该角色直接绑定的权限（叶子）。
     */
    private List<PreviewTreeChildVo> roleChildren(String roleId) {
        List<PreviewTreeChildVo> children = new ArrayList<>();
        for (SysOrgAuth auth : authDao.selectAuthsByRoleId(roleId)) {
            children.add(new PreviewTreeChildVo(NODE_TYPE_AUTH, auth.getFieldId(),
                    displayName(auth.getFieldName(), auth.getFieldCode()), "权限"));
        }
        return children;
    }

    private static String displayName(String name, String code) {
        return StringUtil.isEmpty(name) ? (StringUtil.isEmpty(code) ? "-" : code) : name;
    }
}
