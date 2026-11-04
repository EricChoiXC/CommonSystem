package com.common.system.sys.org.service;

import com.common.system.common.base.model.Pages;
import com.common.system.common.properties.CommonProperties;
import com.common.system.sys.org.dao.SysOrgElementDao;
import com.common.system.sys.org.dao.SysOrgRoleDao;
import com.common.system.sys.org.model.SysOrgAuth;
import com.common.system.sys.org.model.SysOrgElement;
import com.common.system.sys.org.model.SysOrgRole;
import com.common.system.sys.org.properties.SysOrgElementProperties;
import com.common.system.sys.org.request.SysOrgRoleQuery;
import com.common.system.sys.org.response.SysOrgRoleVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色业务服务测试
 *
 * @author Eric
 * @date 2026/8/7
 * @description 覆盖第三个启动任务（系统管理员角色初始化）与角色管理业务
 *              （列表/详情/新建/编辑及 sys_org_role_element / sys_org_role_auth 关联表全量替换），
 *              以及角色主表写入时同事务维护 sys_org_element 镜像行（field_org_type = 5）的同步逻辑。
 *              注意：BaseModel.setFieldId 仅接受 32 位 ID，测试中的模型 ID 一律使用 32 位常量。
 */
class SysOrgRoleServiceTest {

    /** '0' 补足 32 位，与启动任务中的管理员用户/系统管理员角色 ID 保持一致 */
    private static final String ADMIN_USER_ID = "0".repeat(32);

    /** 32 位测试 ID */
    private static final String ROLE_ID = "0".repeat(31) + "1";
    private static final String ROLE_ID_2 = "0".repeat(31) + "2";
    private static final String AUTH_ID = "0".repeat(31) + "3";

    private SysOrgRoleDao roleDao;
    private SysOrgAuthService authService;
    private SysOrgElementDao elementDao;
    private SysOrgRoleService service;

    @BeforeEach
    void setUp() {
        roleDao = mock(SysOrgRoleDao.class);
        authService = mock(SysOrgAuthService.class);
        elementDao = mock(SysOrgElementDao.class);
        service = new SysOrgRoleService(roleDao, authService, elementDao);
    }

    /* ==================== 启动任务 ==================== */

    @Test
    void createsRoleAndLinksAuthAndUserWhenMissing() {
        when(roleDao.selectById(ADMIN_USER_ID)).thenReturn(null);
        when(roleDao.insert(any(SysOrgRole.class))).thenReturn(1);

        SysOrgAuth auth = new SysOrgAuth();
        auth.setFieldId(AUTH_ID);
        when(authService.ensureAuth(anyString(), anyString(), anyString())).thenReturn(auth);
        when(roleDao.countRoleAuth(anyString(), anyString())).thenReturn(0);
        when(roleDao.countRoleElement(anyString(), anyString())).thenReturn(0);
        when(roleDao.insertRoleAuth(anyString(), anyString())).thenReturn(1);
        when(roleDao.insertRoleElement(anyString(), anyString())).thenReturn(1);

        service.ensureSystemAdminRole(ADMIN_USER_ID);

        verify(roleDao).insert(argThat(role ->
                ADMIN_USER_ID.equals(role.getFieldId())
                        && "系统管理员".equals(role.getFieldName())
                        && "SYS_ADMIN".equals(role.getFieldCode())));
        verify(authService).ensureAuth("SYS_ADMIN", "系统管理员", "COMMON");
        verify(roleDao).insertRoleAuth(AUTH_ID, ADMIN_USER_ID);
        verify(roleDao).insertRoleElement(ADMIN_USER_ID, ADMIN_USER_ID);
    }

    @Test
    void skipsCreationWhenRoleExistsAndLinked() {
        SysOrgRole role = new SysOrgRole();
        role.setFieldId(ADMIN_USER_ID);
        when(roleDao.selectById(ADMIN_USER_ID)).thenReturn(role);
        when(authService.ensureAuth(anyString(), anyString(), anyString())).thenReturn(new SysOrgAuth());
        when(roleDao.countRoleAuth(anyString(), anyString())).thenReturn(1);
        when(roleDao.countRoleElement(anyString(), anyString())).thenReturn(1);

        service.ensureSystemAdminRole(ADMIN_USER_ID);

        verify(roleDao, never()).insert(any(SysOrgRole.class));
        verify(roleDao, never()).insertRoleAuth(anyString(), anyString());
        verify(roleDao, never()).insertRoleElement(anyString(), anyString());
    }

    @Test
    void linksAuthOnlyWhenRoleElementAlreadyLinked() {
        SysOrgRole role = new SysOrgRole();
        role.setFieldId(ADMIN_USER_ID);
        when(roleDao.selectById(ADMIN_USER_ID)).thenReturn(role);
        SysOrgAuth auth = new SysOrgAuth();
        auth.setFieldId(AUTH_ID);
        when(authService.ensureAuth(anyString(), anyString(), anyString())).thenReturn(auth);
        when(roleDao.countRoleAuth(anyString(), anyString())).thenReturn(0);
        when(roleDao.countRoleElement(anyString(), anyString())).thenReturn(1);
        when(roleDao.insertRoleAuth(anyString(), anyString())).thenReturn(1);

        service.ensureSystemAdminRole(ADMIN_USER_ID);

        verify(roleDao).insertRoleAuth(AUTH_ID, ADMIN_USER_ID);
        verify(roleDao, never()).insertRoleElement(anyString(), anyString());
    }

    /* ==================== 角色管理 ==================== */

    @Test
    void listFiltersByQueryAndMapsBasicFields() {
        SysOrgRole row = new SysOrgRole();
        row.setFieldId(ROLE_ID);
        row.setFieldName("权限管理员");
        row.setFieldCode("SYS_ORG_ROLE_ADMIN");
        row.setFieldEnable(true);
        row.setFieldCreateTime(new Date());
        row.setFieldUpdateTime(new Date());
        when(roleDao.selectListSorted(any(SysOrgRole.class), any(Pages.class), anyString(), anyString()))
                .thenReturn(List.of(row));

        SysOrgRoleQuery query = new SysOrgRoleQuery();
        query.setFieldName("权限");
        query.setFieldCode("SYS_ORG");
        query.setEnable(true);
        List<SysOrgRoleVo> list = service.list(query, new Pages(), "code", "asc");

        assertEquals(1, list.size());
        SysOrgRoleVo vo = list.get(0);
        assertEquals(ROLE_ID, vo.getFieldId());
        assertEquals("权限管理员", vo.getFieldName());
        assertEquals("SYS_ORG_ROLE_ADMIN", vo.getFieldCode());
        assertEquals(true, vo.getFieldEnable());
        // 列表不查询关联表（避免 N+1）
        assertNull(vo.getElementIds());
        assertNull(vo.getAuthIds());
        verify(roleDao).selectListSorted(argThat(criteria ->
                "权限".equals(criteria.getFieldName())
                        && "SYS_ORG".equals(criteria.getFieldCode())
                        && Boolean.TRUE.equals(criteria.getFieldEnable())),
                any(Pages.class), eq("code"), eq("asc"));
    }

    @Test
    void listNormalizesInvalidSortAndPages() {
        when(roleDao.selectListSorted(any(SysOrgRole.class), any(Pages.class), anyString(), anyString()))
                .thenReturn(List.of());

        service.list(new SysOrgRoleQuery(), new Pages(), "name;DROP", "invalid");

        // 非法排序字段回退 createTime，非法方向回退 desc；分页参数兜底为合法值
        verify(roleDao).selectListSorted(any(SysOrgRole.class), argThat(pages ->
                        pages.getPageNo() >= 1 && pages.getPageSize() >= 1),
                eq("createTime"), eq("desc"));
    }

    @Test
    void countDelegatesToDao() {
        when(roleDao.selectCount(any(SysOrgRole.class))).thenReturn(3L);
        assertEquals(3L, service.count(null));
    }

    @Test
    void loadReturnsRoleWithLinkedElementsAndAuths() {
        String e1Id = "0".repeat(31) + "a";
        String e2Id = "0".repeat(31) + "b";
        SysOrgRole row = new SysOrgRole();
        row.setFieldId(ROLE_ID);
        row.setFieldName("权限管理员");
        row.setFieldCode("SYS_ORG_ROLE_ADMIN");
        row.setFieldEnable(true);
        when(roleDao.selectById(ROLE_ID)).thenReturn(row);
        when(roleDao.selectElementIdsByRoleId(ROLE_ID)).thenReturn(Arrays.asList(e1Id, e2Id));
        when(roleDao.selectAuthIdsByRoleId(ROLE_ID)).thenReturn(List.of(AUTH_ID));
        // 回填名称时按入参 elementIds 匹配：mock 的元素 ID 必须与入参一致（32 位）
        when(elementDao.selectIdNameByIds(Arrays.asList(e1Id, e2Id)))
                .thenReturn(Arrays.asList(element(e1Id, "研发部"), element(e2Id, "销售部")));

        SysOrgRoleVo vo = service.load(ROLE_ID);

        assertEquals(ROLE_ID, vo.getFieldId());
        assertEquals(Arrays.asList(e1Id, e2Id), vo.getElementIds());
        assertEquals(Arrays.asList("研发部", "销售部"), vo.getElementNames());
        assertEquals(List.of(AUTH_ID), vo.getAuthIds());
    }

    /** 构造仅含 id/name 的组织元素（ID 保持原值，避免 BaseModel 转换） */
    private static SysOrgElement element(String id, String name) {
        SysOrgElement e = new SysOrgElement();
        e.setFieldId(id);
        e.setFieldName(name);
        return e;
    }

    @Test
    void loadReturnsNullWhenRoleMissing() {
        when(roleDao.selectById(anyString())).thenReturn(null);
        assertNull(service.load(ROLE_ID_2));
    }

    @Test
    void saveRoleInsertsRoleAndReplacesLinks() {
        SysOrgRoleVo vo = new SysOrgRoleVo();
        vo.setFieldId(ROLE_ID_2);
        vo.setFieldName("  测试角色  ");
        vo.setFieldCode("TEST_ROLE");
        vo.setFieldEnable(false);
        vo.setElementIds(Arrays.asList("e1", "e1", "e2"));
        vo.setAuthIds(List.of(AUTH_ID));
        when(roleDao.insert(any(SysOrgRole.class))).thenReturn(1);
        when(roleDao.deleteRoleElements(anyString())).thenReturn(1);
        when(roleDao.deleteRoleAuths(anyString())).thenReturn(1);
        when(roleDao.insertRoleElements(anyString(), anyList())).thenReturn(2);
        when(roleDao.insertRoleAuths(anyString(), anyList())).thenReturn(1);
        when(roleDao.selectById(anyString())).thenReturn(null); // load 回查不存在（仅验证保存链路）

        service.saveRole(vo);

        verify(roleDao).insert(argThat(role ->
                ROLE_ID_2.equals(role.getFieldId())
                        && "测试角色".equals(role.getFieldName())
                        && "TEST_ROLE".equals(role.getFieldCode())
                        && Boolean.FALSE.equals(role.getFieldEnable())));
        // 全量替换：先删后插，去重后写入
        verify(roleDao).deleteRoleElements(ROLE_ID_2);
        verify(roleDao).insertRoleElements(ROLE_ID_2, Arrays.asList("e1", "e2"));
        verify(roleDao).deleteRoleAuths(ROLE_ID_2);
        verify(roleDao).insertRoleAuths(ROLE_ID_2, List.of(AUTH_ID));
    }

    @Test
    void saveRoleDefaultsEnableTrueWhenMissing() {
        SysOrgRoleVo vo = new SysOrgRoleVo();
        vo.setFieldId(ROLE_ID_2);
        vo.setFieldName("默认启用角色");
        when(roleDao.insert(any(SysOrgRole.class))).thenReturn(1);
        when(roleDao.deleteRoleElements(anyString())).thenReturn(1);
        when(roleDao.deleteRoleAuths(anyString())).thenReturn(1);
        when(roleDao.selectById(anyString())).thenReturn(null);

        service.saveRole(vo);

        verify(roleDao).insert(argThat(role -> Boolean.TRUE.equals(role.getFieldEnable())));
        // 无关联时不执行批量插入
        verify(roleDao, never()).insertRoleElements(anyString(), anyList());
        verify(roleDao, never()).insertRoleAuths(anyString(), anyList());
    }

    @Test
    void saveRoleRejectsBlankName() {
        SysOrgRoleVo vo = new SysOrgRoleVo();
        vo.setFieldId(ROLE_ID_2);
        vo.setFieldName("  ");
        assertThrows(IllegalArgumentException.class, () -> service.saveRole(vo));
    }

    @Test
    void updateRoleUpdatesFieldsAndReplacesLinks() {
        SysOrgRole exist = new SysOrgRole();
        exist.setFieldId(ROLE_ID);
        exist.setFieldName("旧角色名");
        exist.setFieldCode("OLD_CODE");
        exist.setFieldEnable(true);
        when(roleDao.selectById(ROLE_ID)).thenReturn(exist);
        when(roleDao.update(any(SysOrgRole.class))).thenReturn(1);
        when(roleDao.deleteRoleElements(anyString())).thenReturn(1);
        when(roleDao.deleteRoleAuths(anyString())).thenReturn(1);
        when(roleDao.insertRoleElements(anyString(), anyList())).thenReturn(1);
        when(roleDao.insertRoleAuths(anyString(), anyList())).thenReturn(1);

        SysOrgRoleVo vo = new SysOrgRoleVo();
        vo.setFieldId(ROLE_ID);
        vo.setFieldName("新角色名");
        vo.setFieldCode("NEW_CODE");
        vo.setFieldEnable(false);
        vo.setElementIds(List.of("e9"));
        vo.setAuthIds(List.of(AUTH_ID));

        service.updateRole(vo);

        verify(roleDao).update(argThat(role ->
                "新角色名".equals(role.getFieldName())
                        && "NEW_CODE".equals(role.getFieldCode())
                        && Boolean.FALSE.equals(role.getFieldEnable())));
        verify(roleDao).deleteRoleElements(ROLE_ID);
        verify(roleDao).insertRoleElements(ROLE_ID, List.of("e9"));
        verify(roleDao).deleteRoleAuths(ROLE_ID);
        verify(roleDao).insertRoleAuths(ROLE_ID, List.of(AUTH_ID));
    }

    @Test
    void updateRoleThrowsWhenRoleMissing() {
        when(roleDao.selectById(anyString())).thenReturn(null);
        SysOrgRoleVo vo = new SysOrgRoleVo();
        vo.setFieldId(ROLE_ID_2);
        vo.setFieldName("角色");
        assertThrows(IllegalArgumentException.class, () -> service.updateRole(vo));
    }

    @Test
    void updateRoleClearsCodeWhenBlank() {
        SysOrgRole exist = new SysOrgRole();
        exist.setFieldId(ROLE_ID);
        exist.setFieldName("旧角色名");
        exist.setFieldCode("OLD_CODE");
        exist.setFieldEnable(true);
        when(roleDao.selectById(ROLE_ID)).thenReturn(exist);
        when(roleDao.update(any(SysOrgRole.class))).thenReturn(1);
        when(roleDao.deleteRoleElements(anyString())).thenReturn(1);
        when(roleDao.deleteRoleAuths(anyString())).thenReturn(1);

        SysOrgRoleVo vo = new SysOrgRoleVo();
        vo.setFieldId(ROLE_ID);
        vo.setFieldName("新角色名");
        vo.setFieldCode("  "); // 清空编码
        vo.setFieldEnable(true);

        service.updateRole(vo);

        // 编码被置为 null 并随 update 提交（Mapper 侧无条件赋值 field_code）
        verify(roleDao).update(argThat(role ->
                "新角色名".equals(role.getFieldName()) && role.getFieldCode() == null));
    }

    /* ==================== 角色镜像行（sys_org_element 同事务双写，field_org_type = 5） ==================== */

    @Test
    void saveRoleInsertsRoleMirrorWithFixedTreeFields() {
        SysOrgRoleVo vo = new SysOrgRoleVo();
        vo.setFieldId(ROLE_ID_2);
        vo.setFieldName("镜像角色");
        vo.setFieldCode("MIRROR_ROLE");
        vo.setFieldEnable(false);
        when(roleDao.insert(any(SysOrgRole.class))).thenReturn(1);
        when(roleDao.selectById(anyString())).thenReturn(null); // load 回查不存在（仅验证保存链路）

        service.saveRole(vo);

        // 镜像行缺失时走插入：orgType=5、level=1、parentId/hierarchy 为 null、isVirtual=false、名称/编码/启用同步
        verify(elementDao).insert(argThat(mirror ->
                ROLE_ID_2.equals(mirror.getFieldId())
                        && "镜像角色".equals(mirror.getFieldName())
                        && "MIRROR_ROLE".equals(mirror.getFieldCode())
                        && Boolean.FALSE.equals(mirror.getFieldEnable())
                        && SysOrgElementProperties.ORG_TYPE_ROLE == mirror.getFieldOrgType()
                        && 1 == mirror.getFieldLevel()
                        && null == mirror.getFieldParentId()
                        && null == mirror.getFieldHierarchy()
                        && !mirror.isFieldIsVirtual()
                        && null != mirror.getFieldCreateTime()
                        && null != mirror.getFieldUpdateTime()));
        verify(elementDao, never()).updateRoleMirror(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateRoleSyncsMirrorByNameCodeEnable() {
        SysOrgRole exist = new SysOrgRole();
        exist.setFieldId(ROLE_ID);
        exist.setFieldName("旧角色名");
        exist.setFieldCode("OLD_CODE");
        exist.setFieldEnable(true);
        when(roleDao.selectById(ROLE_ID)).thenReturn(exist);
        when(roleDao.update(any(SysOrgRole.class))).thenReturn(1);
        when(elementDao.selectById(ROLE_ID)).thenReturn(new SysOrgElement()); // 镜像行已存在
        when(elementDao.updateRoleMirror(any(), any(), any(), any(), any(), any())).thenReturn(1);

        SysOrgRoleVo vo = new SysOrgRoleVo();
        vo.setFieldId(ROLE_ID);
        vo.setFieldName("新角色名");
        vo.setFieldCode("NEW_CODE");
        vo.setFieldEnable(false);

        service.updateRole(vo);

        verify(elementDao).updateRoleMirror(eq(ROLE_ID), eq("新角色名"), eq("NEW_CODE"), eq(false), any(), any());
        verify(elementDao, never()).insert(any(SysOrgElement.class));
    }

    @Test
    void updateRoleSyncsMirrorCodeClearedToNull() {
        SysOrgRole exist = new SysOrgRole();
        exist.setFieldId(ROLE_ID);
        exist.setFieldName("旧角色名");
        exist.setFieldCode("OLD_CODE");
        exist.setFieldEnable(true);
        when(roleDao.selectById(ROLE_ID)).thenReturn(exist);
        when(roleDao.update(any(SysOrgRole.class))).thenReturn(1);
        when(elementDao.selectById(ROLE_ID)).thenReturn(new SysOrgElement()); // 镜像行已存在
        when(elementDao.updateRoleMirror(any(), any(), any(), any(), any(), any())).thenReturn(1);

        SysOrgRoleVo vo = new SysOrgRoleVo();
        vo.setFieldId(ROLE_ID);
        vo.setFieldName("新角色名");
        vo.setFieldCode("  "); // 清空编码
        vo.setFieldEnable(true);

        service.updateRole(vo);

        // 镜像行编码同步清空（updateRoleMirror 对 field_code 无条件赋值）
        verify(elementDao).updateRoleMirror(eq(ROLE_ID), eq("新角色名"), isNull(), eq(true), any(), any());
    }

    @Test
    void updateRoleHealsMirrorWhenSyncUpdateReturnsZero() {
        SysOrgRole exist = new SysOrgRole();
        exist.setFieldId(ROLE_ID);
        exist.setFieldName("新角色名");
        exist.setFieldEnable(true);
        when(roleDao.selectById(ROLE_ID)).thenReturn(exist);
        when(roleDao.update(any(SysOrgRole.class))).thenReturn(1);
        when(elementDao.selectById(ROLE_ID)).thenReturn(new SysOrgElement()); // 查时存在…
        when(elementDao.updateRoleMirror(any(), any(), any(), any(), any(), any())).thenReturn(0); // …更新时 0 行（孤儿）

        SysOrgRoleVo vo = new SysOrgRoleVo();
        vo.setFieldId(ROLE_ID);
        vo.setFieldName("新角色名");
        vo.setFieldEnable(true);

        service.updateRole(vo);

        // 自愈：updateRoleMirror 返回 0 行时补插入镜像行，保持幂等
        verify(elementDao).updateRoleMirror(eq(ROLE_ID), eq("新角色名"), isNull(), eq(true), any(), any());
        verify(elementDao).insert(argThat(mirror ->
                ROLE_ID.equals(mirror.getFieldId())
                        && SysOrgElementProperties.ORG_TYPE_ROLE == mirror.getFieldOrgType()
                        && 1 == mirror.getFieldLevel()
                        && Boolean.TRUE.equals(mirror.getFieldEnable())));
    }

    @Test
    void ensureSystemAdminRoleSyncsExistingMirrorWithoutDuplicateInsert() {
        SysOrgRole role = new SysOrgRole();
        role.setFieldId(ADMIN_USER_ID);
        // 已存在分支：角色行来自库，名称/编码/启用为实际值，镜像行随其同步
        role.setFieldName("系统管理员");
        role.setFieldCode(CommonProperties.ROLE_ADMIN);
        role.setFieldEnable(true);
        when(roleDao.selectById(ADMIN_USER_ID)).thenReturn(role);
        when(authService.ensureAuth(anyString(), anyString(), anyString())).thenReturn(new SysOrgAuth());
        when(roleDao.countRoleAuth(anyString(), anyString())).thenReturn(1);
        when(roleDao.countRoleElement(anyString(), anyString())).thenReturn(1);
        when(elementDao.selectById(ADMIN_USER_ID)).thenReturn(new SysOrgElement()); // 镜像行已存在
        when(elementDao.updateRoleMirror(any(), any(), any(), any(), any(), any())).thenReturn(1);

        service.ensureSystemAdminRole(ADMIN_USER_ID);

        // 幂等：镜像行已存在时仅同步名称/编码/启用，不重复插入
        verify(elementDao).updateRoleMirror(eq(ADMIN_USER_ID), eq("系统管理员"), eq("SYS_ADMIN"), eq(true), any(), any());
        verify(elementDao, never()).insert(any(SysOrgElement.class));
    }

    @Test
    void ensureSystemAdminRoleInsertsMirrorWhenCreated() {
        when(roleDao.selectById(ADMIN_USER_ID)).thenReturn(null);
        when(roleDao.insert(any(SysOrgRole.class))).thenReturn(1);
        when(authService.ensureAuth(anyString(), anyString(), anyString())).thenReturn(new SysOrgAuth());
        when(roleDao.countRoleAuth(anyString(), anyString())).thenReturn(1);
        when(roleDao.countRoleElement(anyString(), anyString())).thenReturn(1);

        service.ensureSystemAdminRole(ADMIN_USER_ID);

        // 新建分支：角色插入后镜像行同步插入，固定树字段就位
        verify(elementDao).insert(argThat(mirror ->
                ADMIN_USER_ID.equals(mirror.getFieldId())
                        && "系统管理员".equals(mirror.getFieldName())
                        && "SYS_ADMIN".equals(mirror.getFieldCode())
                        && SysOrgElementProperties.ORG_TYPE_ROLE == mirror.getFieldOrgType()
                        && 1 == mirror.getFieldLevel()
                        && null == mirror.getFieldParentId()
                        && null == mirror.getFieldHierarchy()
                        && Boolean.TRUE.equals(mirror.getFieldEnable())
                        && null != mirror.getFieldUpdateTime()));
    }

    @Test
    void saveRolePropagatesMirrorInsertFailure() {
        SysOrgRoleVo vo = new SysOrgRoleVo();
        vo.setFieldId(ROLE_ID_2);
        vo.setFieldName("异常角色");
        when(roleDao.insert(any(SysOrgRole.class))).thenReturn(1);
        when(roleDao.selectById(anyString())).thenReturn(null);
        when(elementDao.insert(any(SysOrgElement.class))).thenThrow(new RuntimeException("mirror insert failed"));

        // 镜像行插入异常不捕获吞掉，向上传播（回滚语义由 @Transactional 保证）
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.saveRole(vo));
        assertEquals("mirror insert failed", thrown.getMessage());
        // 角色插入已发生，但异常向上传播，由 Spring 事务统一回滚
        verify(roleDao).insert(any(SysOrgRole.class));
        verify(elementDao).insert(any(SysOrgElement.class));
    }
}
