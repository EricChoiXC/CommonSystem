package com.common.system.sys.org.service;

import com.common.system.sys.org.dao.SysOrgAuthDao;
import com.common.system.sys.org.dao.SysOrgElementDao;
import com.common.system.sys.org.dao.SysOrgRoleDao;
import com.common.system.sys.org.model.SysOrgAuth;
import com.common.system.sys.org.model.SysOrgElement;
import com.common.system.sys.org.model.SysOrgRole;
import com.common.system.sys.org.response.PreviewTreeChildVo;
import com.common.system.sys.org.response.RolePreviewVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限预览业务服务测试
 *
 * @author Eric
 * @date 2026/8/10
 * @description 覆盖权限预览概览数据（用户节点按登录权限模型读取；非用户节点兜底展示）
 *              与权限树懒加载子节点（用户→角色/上级/岗位/虚拟组织，组织→角色/上级/虚拟组织，
 *              角色→权限，权限为叶子）。注意：BaseModel.setFieldId 仅接受 32 位 ID，
 *              测试中的模型 ID 一律使用 32 位常量。
 */
class SysOrgRolePreviewServiceTest {

    private static final String ORG_ID = "0".repeat(31) + "1";
    private static final String VIRT_ID = "0".repeat(31) + "2";
    private static final String PARENT_ID = "0".repeat(31) + "3";
    private static final String ROLE_ID = "0".repeat(31) + "4";
    private static final String AUTH_ID = "0".repeat(31) + "5";
    private static final String USER_ID = "0".repeat(31) + "6";
    private static final String POST_ID = "0".repeat(31) + "7";

    private SysOrgElementDao elementDao;
    private SysOrgRoleDao roleDao;
    private SysOrgAuthDao authDao;
    private SysOrgRolePreviewService service;

    @BeforeEach
    void setUp() {
        elementDao = mock(SysOrgElementDao.class);
        roleDao = mock(SysOrgRoleDao.class);
        authDao = mock(SysOrgAuthDao.class);
        service = new SysOrgRolePreviewService(elementDao, roleDao, authDao);
    }

    private static SysOrgElement element(String id, String name, boolean virtual, String parentId) {
        SysOrgElement e = new SysOrgElement();
        e.setFieldId(id);
        e.setFieldName(name);
        e.setFieldIsVirtual(virtual);
        e.setFieldParentId(parentId);
        return e;
    }

    /** 构造人员节点（org_type = 3 且非虚拟） */
    private static SysOrgElement userElement(String id, String name, String parentId) {
        SysOrgElement e = element(id, name, false, parentId);
        e.setFieldOrgType(3);
        return e;
    }

    private static SysOrgRole role(String id, String name) {
        SysOrgRole r = new SysOrgRole();
        r.setFieldId(id);
        r.setFieldName(name);
        return r;
    }

    private static SysOrgAuth auth(String id, String code) {
        SysOrgAuth a = new SysOrgAuth();
        a.setFieldId(id);
        a.setFieldCode(code);
        return a;
    }

    /* ==================== 用户节点：按登录权限模型读取 ==================== */

    @Test
    void previewOfUserUsesLoginPermissionModel() {
        SysOrgUserSetup user = userWithSources();
        SysOrgRole role = role(ROLE_ID, "权限管理员");
        when(roleDao.selectListByUserId(USER_ID)).thenReturn(List.of(role));
        when(authDao.selectAuthsByRoleIds(List.of(ROLE_ID))).thenReturn(List.of(auth(AUTH_ID, "SYS_ORG_ROLE_ADMIN")));

        RolePreviewVo vo = service.preview(USER_ID);

        // 与登录权限计算一致：走 selectListByUserId，而非直接关联 selectListByElementId
        verify(roleDao).selectListByUserId(USER_ID);
        verify(roleDao, never()).selectListByElementId(USER_ID);
        assertEquals(USER_ID, vo.getElement().getFieldId());
        // 关联组织架构：直属上级 + 所属岗位 + 所属虚拟组织
        assertEquals(3, vo.getRelatedOrgs().size());
        assertEquals(1, vo.getRoles().size());
        assertEquals("权限管理员", vo.getRoles().get(0).getFieldName());
        assertEquals(1, vo.getAuths().size());
    }

    @Test
    void previewOfUserWithoutRolesSkipsAuthQuery() {
        SysOrgUserSetup user = userWithSources();
        when(roleDao.selectListByUserId(USER_ID)).thenReturn(List.of());

        RolePreviewVo vo = service.preview(USER_ID);

        verify(authDao, never()).selectAuthsByRoleIds(anyList());
        assertEquals(0, vo.getAuths().size());
    }

    /** 构造用户节点及其来源组织（直属上级/岗位/虚拟组织）的 mock */
    private SysOrgUserSetup userWithSources() {
        SysOrgUserSetup setup = new SysOrgUserSetup();
        setup.user = userElement(USER_ID, "张三", ORG_ID);
        when(elementDao.selectById(USER_ID)).thenReturn(setup.user);
        when(elementDao.selectById(ORG_ID)).thenReturn(element(ORG_ID, "研发部", false, null));
        when(elementDao.selectPostIdNameListByUserId(USER_ID))
                .thenReturn(List.of(element(POST_ID, "后端工程师", false, null)));
        when(elementDao.selectVirtualIdNameListByUserId(USER_ID))
                .thenReturn(List.of(element(VIRT_ID, "虚拟项目组", true, null)));
        return setup;
    }

    private static final class SysOrgUserSetup {
        private SysOrgElement user;
    }

    @Test
    void userElementChildrenExpandLoginSources() {
        SysOrgUserSetup user = userWithSources();
        when(roleDao.selectListByElementId(USER_ID)).thenReturn(List.of(role(ROLE_ID, "权限管理员")));

        List<PreviewTreeChildVo> children = service.treeChildren("element", USER_ID);

        // 直接角色 + 直属上级 + 所属岗位 + 所属虚拟组织（与登录模型四类来源一致）
        assertEquals(4, children.size());
        assertEquals("role", children.get(0).getType());
        assertEquals("角色", children.get(0).getPrefix());
        assertEquals("权限管理员", children.get(0).getName());
        assertEquals("element", children.get(1).getType());
        assertEquals("上级", children.get(1).getPrefix());
        assertEquals("研发部", children.get(1).getName());
        assertEquals("element", children.get(2).getType());
        assertEquals("岗位", children.get(2).getPrefix());
        assertEquals("后端工程师", children.get(2).getName());
        assertEquals("element", children.get(3).getType());
        assertEquals("虚拟组织", children.get(3).getPrefix());
        assertEquals("虚拟项目组", children.get(3).getName());
    }

    /* ==================== 非用户节点：基础兜底 ==================== */

    @Test
    void previewOfNormalOrgShowsVirtualOrgsAsRelated() {
        SysOrgElement org = element(ORG_ID, "研发中心", false, null);
        when(elementDao.selectById(ORG_ID)).thenReturn(org);
        SysOrgElement virtual = element(VIRT_ID, "虚拟项目组", true, ORG_ID);
        when(elementDao.selectVirtualsByParentId(ORG_ID)).thenReturn(List.of(virtual));
        SysOrgRole role = role(ROLE_ID, "项目管理员");
        when(roleDao.selectListByElementId(ORG_ID)).thenReturn(List.of(role));
        when(authDao.selectAuthsByRoleIds(List.of(ROLE_ID))).thenReturn(List.of(auth(AUTH_ID, "SYS_ORG_ROLE_ADMIN")));

        RolePreviewVo vo = service.preview(ORG_ID);

        assertEquals(ORG_ID, vo.getElement().getFieldId());
        assertEquals(1, vo.getRelatedOrgs().size());
        assertEquals(VIRT_ID, vo.getRelatedOrgs().get(0).getFieldId());
        assertEquals(1, vo.getRoles().size());
        assertEquals(1, vo.getAuths().size());
    }

    @Test
    void previewOfVirtualOrgShowsOwnerAsRelated() {
        SysOrgElement virtual = element(VIRT_ID, "虚拟项目组", true, ORG_ID);
        when(elementDao.selectById(VIRT_ID)).thenReturn(virtual);
        SysOrgElement owner = element(ORG_ID, "研发中心", false, null);
        when(elementDao.selectById(ORG_ID)).thenReturn(owner);
        when(roleDao.selectListByElementId(VIRT_ID)).thenReturn(List.of());

        RolePreviewVo vo = service.preview(VIRT_ID);

        assertEquals(1, vo.getRelatedOrgs().size());
        assertEquals(ORG_ID, vo.getRelatedOrgs().get(0).getFieldId());
        // 无角色时不查询权限并集
        verify(authDao, never()).selectAuthsByRoleIds(anyList());
        assertEquals(0, vo.getAuths().size());
    }

    @Test
    void previewThrowsWhenElementMissing() {
        when(elementDao.selectById(anyString())).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.preview(ORG_ID));
    }

    @Test
    void orgElementChildrenListRolesParentAndVirtuals() {
        SysOrgRole role = role(ROLE_ID, "权限管理员");
        when(roleDao.selectListByElementId(ORG_ID)).thenReturn(List.of(role));
        SysOrgElement org = element(ORG_ID, "研发中心", false, PARENT_ID);
        when(elementDao.selectById(ORG_ID)).thenReturn(org);
        when(elementDao.selectById(PARENT_ID)).thenReturn(element(PARENT_ID, "总公司", false, null));
        when(elementDao.selectVirtualsByParentId(ORG_ID))
                .thenReturn(List.of(element(VIRT_ID, "虚拟项目组", true, ORG_ID)));

        List<PreviewTreeChildVo> children = service.treeChildren("element", ORG_ID);

        assertEquals(3, children.size());
        assertEquals("role", children.get(0).getType());
        assertEquals("角色", children.get(0).getPrefix());
        assertEquals("权限管理员", children.get(0).getName());
        assertEquals("element", children.get(1).getType());
        assertEquals("上级", children.get(1).getPrefix());
        assertEquals("总公司", children.get(1).getName());
        assertEquals("element", children.get(2).getType());
        assertEquals("虚拟组织", children.get(2).getPrefix());
    }

    @Test
    void elementChildrenSkipsParentWhenRoot() {
        when(roleDao.selectListByElementId(ORG_ID)).thenReturn(List.of());
        SysOrgElement org = element(ORG_ID, "根机构", false, null);
        when(elementDao.selectById(ORG_ID)).thenReturn(org);
        when(elementDao.selectVirtualsByParentId(ORG_ID)).thenReturn(List.of());

        List<PreviewTreeChildVo> children = service.treeChildren("element", ORG_ID);

        assertEquals(0, children.size());
    }

    @Test
    void virtualElementChildrenSkipParentToAvoidCycle() {
        // 虚拟组织 V 挂载在 O 下：展开 V 不应返回「上级 O」，否则 O→V→O 无限互指
        when(roleDao.selectListByElementId(VIRT_ID)).thenReturn(List.of());
        SysOrgElement virtual = element(VIRT_ID, "虚拟项目组", true, ORG_ID);
        when(elementDao.selectById(VIRT_ID)).thenReturn(virtual);
        when(elementDao.selectVirtualsByParentId(VIRT_ID)).thenReturn(List.of());

        List<PreviewTreeChildVo> children = service.treeChildren("element", VIRT_ID);

        assertEquals(0, children.size());
        verify(elementDao, never()).selectById(ORG_ID);
    }

    @Test
    void roleChildrenReturnDirectAuths() {
        when(authDao.selectAuthsByRoleId(ROLE_ID)).thenReturn(List.of(auth(AUTH_ID, "SYS_ORG_ROLE_ADMIN")));

        List<PreviewTreeChildVo> children = service.treeChildren("role", ROLE_ID);

        assertEquals(1, children.size());
        assertEquals("auth", children.get(0).getType());
        assertEquals("权限", children.get(0).getPrefix());
        assertEquals("SYS_ORG_ROLE_ADMIN", children.get(0).getName());
    }

    @Test
    void authNodeHasNoChildren() {
        assertEquals(0, service.treeChildren("auth", AUTH_ID).size());
        assertEquals(0, service.treeChildren("unknown", AUTH_ID).size());
    }
}
