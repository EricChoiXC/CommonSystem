package com.common.system.sys.org.service;

import com.common.system.common.base.model.Pages;
import com.common.system.common.exception.AuthorizationException;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.org.dao.SysOrgElementDao;
import com.common.system.sys.org.dao.SysOrgGroupDao;
import com.common.system.sys.org.dao.SysOrgGroupUserDao;
import com.common.system.sys.org.dao.SysOrgGroupViewerDao;
import com.common.system.sys.org.model.SysOrgElement;
import com.common.system.sys.org.model.SysOrgGroup;
import com.common.system.sys.org.model.SysOrgRole;
import com.common.system.sys.org.model.SysOrgUser;
import com.common.system.sys.org.request.SysOrgGroupQuery;
import com.common.system.sys.org.request.SysOrgGroupRequest;
import com.common.system.sys.org.response.SysOrgGroupVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static com.common.system.common.properties.CommonProperties.ROLE_ADMIN;
import static com.common.system.sys.org.properties.SysOrgElementProperties.ORG_TYPE_DEPT;
import static com.common.system.sys.org.properties.SysOrgElementProperties.ORG_TYPE_GROUP;
import static com.common.system.sys.org.properties.SysOrgElementProperties.ORG_TYPE_ORG;
import static com.common.system.sys.org.properties.SysOrgElementProperties.ORG_TYPE_ROLE;
import static com.common.system.sys.org.properties.SysOrgElementProperties.ORG_TYPE_USER;
import static com.common.system.sys.org.properties.SysOrgGroupProperties.GROUP_TYPE_COMMON;
import static com.common.system.sys.org.properties.SysOrgGroupProperties.GROUP_TYPE_PERSONAL;
import static com.common.system.sys.org.properties.SysOrgGroupProperties.ROLE_SYS_ORG_ADMIN;
import static com.common.system.sys.org.properties.SysOrgGroupProperties.ROLE_SYS_ORG_GROUP_ADMIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 群组业务服务测试
 *
 * @author Eric
 * @date 2026/11/5
 * @description 覆盖 ORG2-BE-002 验收矩阵（2 种身份 × 3 种操作 × 2 类群组）：
 *              普通用户建 COMMON 被拒 / 改删他人个人群组 FORBIDDEN / 可见但无维护权 FORBIDDEN /
 *              不可改类型；管理员可建改删公共群组与自己的个人群组；PERSONAL 保存后 viewer 空 +
 *              isAllViewer=true；COMMON 明细非空 isAllViewer=false；成员含停用/非人员被拒；
 *              可见范围含虚拟组织/角色被拒；删除四表顺序；element 主行镜像（field_org_type = 6）
 *              与归属恒保持原值；resolveAdmin 角色判定；list/count 可见性入参填充。
 *              ORG2-BE-004：单条可见性改走 SQL 口径（selectCountVisibleById，默认桩可见、
 *              不可见用例显式覆盖为 0）；地址本 groupList 固定 createTime desc，
 *              groupUsers 覆盖 E_NOT_FOUND / FORBIDDEN / orgTypes 求交 / 成员明细。
 *              注意：BaseModel.setFieldId 仅接受 32 位 ID，测试中的模型 ID 一律使用 32 位常量；
 *              当前登录人经 MockedStatic&lt;UserUtil&gt; 模拟（参照 SysProcessExampleServiceTest 先例）。
 */
class SysOrgGroupServiceTest {

    /** 32 位测试 ID（避免 BaseModel.setFieldId 触发重新生成） */
    private static final String UID_NORMAL = "1".repeat(32);
    private static final String UID_ADMIN = "2".repeat(32);
    private static final String UID_OTHER = "3".repeat(32);
    private static final String USER_ID = "4".repeat(32);
    private static final String DISABLED_USER_ID = "5".repeat(32);
    private static final String ORG_ID = "6".repeat(32);
    private static final String POST_ID = "8".repeat(32);
    private static final String GROUP_ID = "9".repeat(32);
    private static final String VIRTUAL_DEPT_ID = "a".repeat(32);
    private static final String ROLE_ELEM_ID = "b".repeat(32);

    private SysOrgGroupDao groupDao;
    private SysOrgGroupUserDao groupUserDao;
    private SysOrgGroupViewerDao groupViewerDao;
    private SysOrgElementDao elementDao;
    private SysOrgGroupService service;
    private MockedStatic<UserUtil> userUtil;

    @BeforeEach
    void setUp() {
        groupDao = mock(SysOrgGroupDao.class);
        groupUserDao = mock(SysOrgGroupUserDao.class);
        groupViewerDao = mock(SysOrgGroupViewerDao.class);
        elementDao = mock(SysOrgElementDao.class);
        service = new SysOrgGroupService(groupDao, groupUserDao, groupViewerDao, elementDao);
        // 默认当前登录人为普通用户（无任何角色）
        userUtil = mockStatic(UserUtil.class);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(principal(UID_NORMAL));
        // 可见性统一走 SQL 口径（VisibleClause，ORG2-BE-004）：默认桩为可见（COUNT > 0），
        // 不可见用例显式覆盖为 0；服务端填充 userId/admin 的断言见各用例 argThat
        when(groupDao.selectCountVisibleById(anyString(), any(SysOrgGroupQuery.class))).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        userUtil.close();
    }

    /* ==================== save：类型强制与归属 ==================== */

    @Test
    void normalUserCannotSaveCommon() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.selectVoById(anyString())).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(request(GROUP_ID, "越权群组", GROUP_TYPE_COMMON, null, null)));

        assertEquals("普通用户仅可创建个人群组", ex.getMessage());
        verify(groupDao, never()).insert(any(SysOrgGroup.class));
        verify(elementDao, never()).insert(any(SysOrgElement.class));
        verify(groupUserDao, never()).insertGroupUsers(anyString(), anyList());
        verify(groupViewerDao, never()).insertGroupViewers(anyString(), anyList());
    }

    @Test
    void normalUserSavesPersonalWithDerivedAllViewerAndMirror() {
        loginAs(principal(UID_NORMAL));
        when(elementDao.selectById(USER_ID)).thenReturn(element(USER_ID, "张三", ORG_TYPE_USER, true, false));
        when(groupDao.insert(any(SysOrgGroup.class))).thenReturn(1);
        when(groupDao.selectVoById(GROUP_ID)).thenReturn(vo(GROUP_ID, GROUP_TYPE_PERSONAL, UID_NORMAL, true));

        SysOrgGroupVo result = service.save(request(GROUP_ID, "我的群组", GROUP_TYPE_PERSONAL, List.of(USER_ID), null));

        assertEquals(GROUP_ID, result.getFieldId());
        verify(groupDao).insert(argThat(g ->
                GROUP_ID.equals(g.getFieldId())
                        && GROUP_TYPE_PERSONAL.equals(g.getFieldType())
                        && UID_NORMAL.equals(g.getFieldOwnerId())
                        && Boolean.TRUE.equals(g.getFieldIsAllViewer())
                        && Boolean.TRUE.equals(g.getFieldEnable())
                        && UID_NORMAL.equals(g.getFieldCreatorId())
                        && null != g.getFieldCreateTime()
                        && null != g.getFieldUpdateTime()));
        verify(groupUserDao).insertGroupUsers(GROUP_ID, List.of(USER_ID));
        // PERSONAL 不写可见范围明细，field_is_all_viewer 恒为 true
        verify(groupViewerDao, never()).insertGroupViewers(anyString(), anyList());
        // 镜像行缺失 → 插入主行（field_org_type = 6，不参与树）
        verify(elementDao).insert(argThat(m ->
                GROUP_ID.equals(m.getFieldId())
                        && ORG_TYPE_GROUP == m.getFieldOrgType()
                        && 1 == m.getFieldLevel()
                        && null == m.getFieldParentId()
                        && null == m.getFieldHierarchy()
                        && !m.isFieldIsVirtual()
                        && UID_NORMAL.equals(m.getFieldCreatorId())
                        && null != m.getFieldUpdateTime()));
    }

    @Test
    void adminSavesCommonWithDetailsAndAllViewerFalse() {
        loginAs(principal(UID_ADMIN, ROLE_SYS_ORG_GROUP_ADMIN));
        when(elementDao.selectById(USER_ID)).thenReturn(element(USER_ID, "张三", ORG_TYPE_USER, true, false));
        when(elementDao.selectById(ORG_ID)).thenReturn(element(ORG_ID, "总公司", ORG_TYPE_ORG, true, false));
        when(groupDao.insert(any(SysOrgGroup.class))).thenReturn(1);
        when(groupDao.selectVoById(GROUP_ID)).thenReturn(vo(GROUP_ID, GROUP_TYPE_COMMON, UID_ADMIN, false));
        when(elementDao.selectById(GROUP_ID)).thenReturn(element(GROUP_ID, "项目A组", ORG_TYPE_GROUP, true, false));
        when(elementDao.updateRoleMirror(anyString(), anyString(), any(), any(), any(), any())).thenReturn(1);

        SysOrgGroupVo result = service.save(request(GROUP_ID, "项目A组", GROUP_TYPE_COMMON, List.of(USER_ID), List.of(ORG_ID)));

        assertEquals(GROUP_ID, result.getFieldId());
        verify(groupDao).insert(argThat(g ->
                GROUP_TYPE_COMMON.equals(g.getFieldType())
                        && UID_ADMIN.equals(g.getFieldOwnerId())
                        && Boolean.FALSE.equals(g.getFieldIsAllViewer())));
        verify(groupUserDao).insertGroupUsers(GROUP_ID, List.of(USER_ID));
        verify(groupViewerDao).insertGroupViewers(GROUP_ID, List.of(ORG_ID));
        // 镜像行已存在 → 经 updateRoleMirror 仅同步名称/编码(null)/启用/审计，不重复插入
        verify(elementDao).updateRoleMirror(eq(GROUP_ID), eq("项目A组"), isNull(), any(), any(), any());
        verify(elementDao, never()).insert(any(SysOrgElement.class));
    }

    @Test
    void adminSavesCommonAllViewerWhenNoViewers() {
        loginAs(principal(UID_ADMIN, ROLE_SYS_ORG_GROUP_ADMIN));
        when(groupDao.insert(any(SysOrgGroup.class))).thenReturn(1);
        when(groupDao.selectVoById(GROUP_ID)).thenReturn(vo(GROUP_ID, GROUP_TYPE_COMMON, UID_ADMIN, true));

        service.save(request(GROUP_ID, "全员可见组", GROUP_TYPE_COMMON, null, null));

        // COMMON 可见范围为空 → 派生全员可见，且不执行明细插入
        verify(groupDao).insert(argThat(g -> Boolean.TRUE.equals(g.getFieldIsAllViewer())));
        verify(groupViewerDao, never()).insertGroupViewers(anyString(), anyList());
        verify(groupUserDao, never()).insertGroupUsers(anyString(), anyList());
    }

    @Test
    void saveRejectsBlankName() {
        loginAs(principal(UID_NORMAL));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(request(GROUP_ID, "  ", GROUP_TYPE_PERSONAL, null, null)));
        assertEquals("群组名称不能为空", ex.getMessage());
    }

    @Test
    void saveRejectsInvalidType() {
        loginAs(principal(UID_NORMAL));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(request(GROUP_ID, "群组", "OTHER", null, null)));
        assertEquals("无效的群组类型: OTHER", ex.getMessage());
    }

    @Test
    void saveThrowsUnauthorizedWhenNotLoggedIn() {
        userUtil.when(UserUtil::getCurrentUser).thenReturn(null);
        SysOrgGroupRequest request = request(GROUP_ID, "群组", GROUP_TYPE_PERSONAL, null, null);
        AuthorizationException ex = assertThrows(AuthorizationException.class, () -> service.save(request));
        assertEquals("UNAUTHORIZED", ex.getCode());
        assertEquals("登录已过期，请重新登录", ex.getMessage());
    }

    @Test
    void saveThrowsWhenInsertAffectsNoRow() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.insert(any(SysOrgGroup.class))).thenReturn(0);
        assertThrows(IllegalStateException.class,
                () -> service.save(request(GROUP_ID, "群组", GROUP_TYPE_PERSONAL, null, null)));
    }

    /* ==================== save：成员与可见范围校验 ==================== */

    @Test
    void saveRejectsDisabledGroupUser() {
        loginAs(principal(UID_NORMAL));
        when(elementDao.selectById(DISABLED_USER_ID))
                .thenReturn(element(DISABLED_USER_ID, "李四", ORG_TYPE_USER, false, false));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(request(GROUP_ID, "群组", GROUP_TYPE_PERSONAL, List.of(DISABLED_USER_ID), null)));

        assertEquals("群组成员必须为启用的人员", ex.getMessage());
        verify(groupDao, never()).insert(any(SysOrgGroup.class));
    }

    @Test
    void saveRejectsNonUserMember() {
        loginAs(principal(UID_NORMAL));
        when(elementDao.selectById(ORG_ID)).thenReturn(element(ORG_ID, "总公司", ORG_TYPE_ORG, true, false));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(request(GROUP_ID, "群组", GROUP_TYPE_PERSONAL, List.of(ORG_ID), null)));

        assertEquals("群组成员必须为启用的人员", ex.getMessage());
        verify(groupDao, never()).insert(any(SysOrgGroup.class));
    }

    @Test
    void saveRejectsMissingGroupUser() {
        loginAs(principal(UID_NORMAL));
        when(elementDao.selectById(anyString())).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(request(GROUP_ID, "群组", GROUP_TYPE_PERSONAL, List.of(USER_ID), null)));

        assertEquals("群组成员必须为启用的人员", ex.getMessage());
    }

    @Test
    void saveRejectsVirtualOrgViewer() {
        loginAs(principal(UID_ADMIN, ROLE_SYS_ORG_GROUP_ADMIN));
        when(elementDao.selectById(VIRTUAL_DEPT_ID))
                .thenReturn(element(VIRTUAL_DEPT_ID, "虚拟部门", ORG_TYPE_DEPT, true, true));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(request(GROUP_ID, "群组", GROUP_TYPE_COMMON, null, List.of(VIRTUAL_DEPT_ID))));

        assertEquals("可见范围仅支持机构/部门/岗位/人员", ex.getMessage());
        verify(groupDao, never()).insert(any(SysOrgGroup.class));
    }

    @Test
    void saveRejectsRoleViewer() {
        loginAs(principal(UID_ADMIN, ROLE_SYS_ORG_GROUP_ADMIN));
        when(elementDao.selectById(ROLE_ELEM_ID))
                .thenReturn(element(ROLE_ELEM_ID, "角色镜像", ORG_TYPE_ROLE, true, false));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(request(GROUP_ID, "群组", GROUP_TYPE_COMMON, null, List.of(ROLE_ELEM_ID))));

        assertEquals("可见范围仅支持机构/部门/岗位/人员", ex.getMessage());
    }

    @Test
    void saveRejectsMissingViewer() {
        loginAs(principal(UID_ADMIN, ROLE_SYS_ORG_GROUP_ADMIN));
        when(elementDao.selectById(anyString())).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(request(GROUP_ID, "群组", GROUP_TYPE_COMMON, null, List.of(ORG_ID))));

        assertEquals("可见范围仅支持机构/部门/岗位/人员", ex.getMessage());
    }

    /* ==================== update：归属与权限 ==================== */

    @Test
    void normalUserCannotUpdateOthersPersonalGroup() {
        loginAs(principal(UID_NORMAL));
        // 他人个人群组对普通用户不可见（SQL 口径 COUNT = 0）→ FORBIDDEN（无权查看）
        when(groupDao.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, GROUP_TYPE_PERSONAL, UID_OTHER, true));
        when(groupDao.selectCountVisibleById(eq(GROUP_ID), any(SysOrgGroupQuery.class))).thenReturn(0L);

        AuthorizationException ex = assertThrows(AuthorizationException.class,
                () -> service.update(request(GROUP_ID, "改名", GROUP_TYPE_PERSONAL, null, null)));

        assertEquals("FORBIDDEN", ex.getCode());
        assertEquals("无权查看该群组", ex.getMessage());
        verify(groupDao, never()).update(any(SysOrgGroup.class));
    }

    @Test
    void normalUserCannotDeleteOthersPersonalGroup() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, GROUP_TYPE_PERSONAL, UID_OTHER, true));
        when(groupDao.selectCountVisibleById(eq(GROUP_ID), any(SysOrgGroupQuery.class))).thenReturn(0L);

        AuthorizationException ex = assertThrows(AuthorizationException.class, () -> service.delete(GROUP_ID));

        assertEquals("FORBIDDEN", ex.getCode());
        verify(groupDao, never()).deleteById(anyString());
        verify(elementDao, never()).deleteById(anyString());
    }

    @Test
    void normalUserCannotMaintainVisibleCommonGroup() {
        loginAs(principal(UID_NORMAL));
        // 全员可见公共群组：可见但无维护权 → FORBIDDEN（无权维护）
        when(groupDao.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, GROUP_TYPE_COMMON, UID_OTHER, true));

        AuthorizationException ex = assertThrows(AuthorizationException.class,
                () -> service.update(request(GROUP_ID, "越权编辑", GROUP_TYPE_COMMON, null, null)));

        assertEquals("FORBIDDEN", ex.getCode());
        assertEquals("无权维护该群组", ex.getMessage());
        verify(groupDao, never()).update(any(SysOrgGroup.class));
    }

    @Test
    void normalUserCannotChangeFieldType() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, GROUP_TYPE_PERSONAL, UID_NORMAL, true));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.update(request(GROUP_ID, "我的群组", GROUP_TYPE_COMMON, null, null)));

        assertEquals("普通用户不可修改群组类型", ex.getMessage());
        verify(groupDao, never()).update(any(SysOrgGroup.class));
    }

    @Test
    void normalUserUpdatesOwnPersonalGroupAndClearsViewers() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, GROUP_TYPE_PERSONAL, UID_NORMAL, true));
        when(elementDao.selectById(USER_ID)).thenReturn(element(USER_ID, "张三", ORG_TYPE_USER, true, false));
        when(groupDao.update(any(SysOrgGroup.class))).thenReturn(1);
        when(groupDao.selectVoById(GROUP_ID)).thenReturn(vo(GROUP_ID, GROUP_TYPE_PERSONAL, UID_NORMAL, true));
        when(elementDao.selectById(GROUP_ID)).thenReturn(element(GROUP_ID, "旧名", ORG_TYPE_GROUP, true, false));
        when(elementDao.updateRoleMirror(anyString(), anyString(), any(), any(), any(), any())).thenReturn(1);

        service.update(request(GROUP_ID, "新名字", GROUP_TYPE_PERSONAL, List.of(USER_ID), List.of(ORG_ID)));

        verify(groupDao).update(argThat(g ->
                "新名字".equals(g.getFieldName())
                        && GROUP_TYPE_PERSONAL.equals(g.getFieldType())
                        // fieldOwnerId 恒保持原值：不写入 update 参数（XML 对 null 跳过）
                        && null == g.getFieldOwnerId()
                        && Boolean.TRUE.equals(g.getFieldIsAllViewer())));
        // 明细全量替换（先删后插）；PERSONAL 清空可见范围明细且不回插
        verify(groupUserDao).deleteGroupUsersByGroupId(GROUP_ID);
        verify(groupUserDao).insertGroupUsers(GROUP_ID, List.of(USER_ID));
        verify(groupViewerDao).deleteGroupViewersByGroupId(GROUP_ID);
        verify(groupViewerDao, never()).insertGroupViewers(anyString(), anyList());
        // 主行仅同步名称/编码(null)/启用/审计字段
        verify(elementDao).updateRoleMirror(eq(GROUP_ID), eq("新名字"), isNull(), any(), any(), any());
    }

    @Test
    void adminUpdatesCommonGroupKeepingOwnerAndReplacingDetails() {
        loginAs(principal(UID_ADMIN, ROLE_SYS_ORG_GROUP_ADMIN));
        when(groupDao.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, GROUP_TYPE_COMMON, UID_OTHER, true));
        when(elementDao.selectById(USER_ID)).thenReturn(element(USER_ID, "张三", ORG_TYPE_USER, true, false));
        when(elementDao.selectById(ORG_ID)).thenReturn(element(ORG_ID, "研发部", ORG_TYPE_DEPT, true, false));
        when(groupDao.update(any(SysOrgGroup.class))).thenReturn(1);
        when(groupDao.selectVoById(GROUP_ID)).thenReturn(vo(GROUP_ID, GROUP_TYPE_COMMON, UID_OTHER, false));
        when(elementDao.selectById(GROUP_ID)).thenReturn(element(GROUP_ID, "旧名", ORG_TYPE_GROUP, true, false));
        when(elementDao.updateRoleMirror(anyString(), anyString(), any(), any(), any(), any())).thenReturn(1);

        service.update(request(GROUP_ID, "项目A组", GROUP_TYPE_COMMON, List.of(USER_ID), List.of(ORG_ID)));

        // 管理员维护他人公共群组：归属保持原值（不写入 update 参数）
        verify(groupDao).update(argThat(g ->
                "项目A组".equals(g.getFieldName())
                        && GROUP_TYPE_COMMON.equals(g.getFieldType())
                        && null == g.getFieldOwnerId()
                        && Boolean.FALSE.equals(g.getFieldIsAllViewer())));
        verify(groupUserDao).deleteGroupUsersByGroupId(GROUP_ID);
        verify(groupUserDao).insertGroupUsers(GROUP_ID, List.of(USER_ID));
        verify(groupViewerDao).deleteGroupViewersByGroupId(GROUP_ID);
        verify(groupViewerDao).insertGroupViewers(GROUP_ID, List.of(ORG_ID));
        verify(elementDao).updateRoleMirror(eq(GROUP_ID), eq("项目A组"), isNull(), any(), any(), any());
        verify(elementDao, never()).insert(any(SysOrgElement.class));
    }

    @Test
    void adminChangesPersonalToCommonAndInsertsViewers() {
        loginAs(principal(UID_ADMIN, ROLE_SYS_ORG_GROUP_ADMIN));
        when(groupDao.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, GROUP_TYPE_PERSONAL, UID_ADMIN, true));
        when(elementDao.selectById(ORG_ID)).thenReturn(element(ORG_ID, "研发部", ORG_TYPE_DEPT, true, false));
        when(groupDao.update(any(SysOrgGroup.class))).thenReturn(1);
        when(groupDao.selectVoById(GROUP_ID)).thenReturn(vo(GROUP_ID, GROUP_TYPE_COMMON, UID_ADMIN, false));
        when(elementDao.selectById(GROUP_ID)).thenReturn(element(GROUP_ID, "旧名", ORG_TYPE_GROUP, true, false));
        when(elementDao.updateRoleMirror(anyString(), anyString(), any(), any(), any(), any())).thenReturn(1);

        service.update(request(GROUP_ID, "升级公共组", GROUP_TYPE_COMMON, null, List.of(ORG_ID)));

        // 管理员可改类型：PERSONAL → COMMON，可见范围明细回插
        verify(groupDao).update(argThat(g -> GROUP_TYPE_COMMON.equals(g.getFieldType())));
        verify(groupViewerDao).insertGroupViewers(GROUP_ID, List.of(ORG_ID));
    }

    @Test
    void updateHealsMirrorWhenSyncReturnsZero() {
        loginAs(principal(UID_ADMIN, ROLE_SYS_ORG_GROUP_ADMIN));
        when(groupDao.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, GROUP_TYPE_COMMON, UID_OTHER, true));
        when(groupDao.update(any(SysOrgGroup.class))).thenReturn(1);
        // 查时存在、更新时 0 行（孤儿）→ 补插入自愈
        when(elementDao.selectById(GROUP_ID)).thenReturn(element(GROUP_ID, "旧名", ORG_TYPE_GROUP, true, false));
        when(elementDao.updateRoleMirror(anyString(), anyString(), any(), any(), any(), any())).thenReturn(0);
        when(groupDao.selectVoById(GROUP_ID)).thenReturn(vo(GROUP_ID, GROUP_TYPE_COMMON, UID_OTHER, true));

        service.update(request(GROUP_ID, "自愈群组", GROUP_TYPE_COMMON, null, null));

        verify(elementDao).insert(argThat(m ->
                GROUP_ID.equals(m.getFieldId())
                        && ORG_TYPE_GROUP == m.getFieldOrgType()
                        && 1 == m.getFieldLevel()
                        && null == m.getFieldHierarchy()));
    }

    @Test
    void updateThrowsNotFoundWhenGroupMissing() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.selectById(anyString())).thenReturn(null);

        AuthorizationException ex = assertThrows(AuthorizationException.class,
                () -> service.update(request(GROUP_ID, "群组", GROUP_TYPE_PERSONAL, null, null)));

        assertEquals("E_NOT_FOUND", ex.getCode());
        assertEquals("未找到该群组", ex.getMessage());
    }

    @Test
    void updateThrowsUnauthorizedWhenNotLoggedIn() {
        userUtil.when(UserUtil::getCurrentUser).thenReturn(null);
        when(groupDao.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, GROUP_TYPE_PERSONAL, UID_NORMAL, true));
        AuthorizationException ex = assertThrows(AuthorizationException.class,
                () -> service.update(request(GROUP_ID, "群组", GROUP_TYPE_PERSONAL, null, null)));
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    /* ==================== delete：四表顺序 ==================== */

    @Test
    void adminDeletesCommonGroupInFourTableOrder() {
        loginAs(principal(UID_ADMIN, ROLE_SYS_ORG_GROUP_ADMIN));
        when(groupDao.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, GROUP_TYPE_COMMON, UID_OTHER, true));
        when(groupDao.deleteById(GROUP_ID)).thenReturn(1);

        service.delete(GROUP_ID);

        // 物理删除四表（02 文档 2.4）：user → viewer → group → element，同事务
        InOrder inOrder = inOrder(groupUserDao, groupViewerDao, groupDao, elementDao);
        inOrder.verify(groupUserDao).deleteGroupUsersByGroupId(GROUP_ID);
        inOrder.verify(groupViewerDao).deleteGroupViewersByGroupId(GROUP_ID);
        inOrder.verify(groupDao).deleteById(GROUP_ID);
        inOrder.verify(elementDao).deleteById(GROUP_ID);
    }

    @Test
    void normalUserDeletesOwnPersonalGroupInFourTableOrder() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, GROUP_TYPE_PERSONAL, UID_NORMAL, true));
        when(groupDao.deleteById(GROUP_ID)).thenReturn(1);

        service.delete(GROUP_ID);

        InOrder inOrder = inOrder(groupUserDao, groupViewerDao, groupDao, elementDao);
        inOrder.verify(groupUserDao).deleteGroupUsersByGroupId(GROUP_ID);
        inOrder.verify(groupViewerDao).deleteGroupViewersByGroupId(GROUP_ID);
        inOrder.verify(groupDao).deleteById(GROUP_ID);
        inOrder.verify(elementDao).deleteById(GROUP_ID);
    }

    @Test
    void deleteThrowsNotFoundWhenGroupMissing() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.selectById(anyString())).thenReturn(null);

        AuthorizationException ex = assertThrows(AuthorizationException.class, () -> service.delete(GROUP_ID));

        assertEquals("E_NOT_FOUND", ex.getCode());
        assertEquals("未找到该群组", ex.getMessage());
        verify(groupDao, never()).deleteById(anyString());
    }

    @Test
    void deleteThrowsWhenGroupRowAffectsNoRow() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, GROUP_TYPE_PERSONAL, UID_NORMAL, true));
        when(groupDao.deleteById(GROUP_ID)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.delete(GROUP_ID));
        // group 行删除失败（0 行）时不再删除主行，交由事务回滚
        verify(elementDao, never()).deleteById(anyString());
    }

    /* ==================== load：可见性与明细组装 ==================== */

    @Test
    void loadThrowsNotFoundWhenGroupMissing() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.selectVoById(anyString())).thenReturn(null);

        AuthorizationException ex = assertThrows(AuthorizationException.class, () -> service.load(GROUP_ID));

        assertEquals("E_NOT_FOUND", ex.getCode());
        assertEquals("未找到该群组", ex.getMessage());
    }

    @Test
    void loadThrowsForbiddenWhenPersonalGroupOfOthers() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.selectVoById(GROUP_ID)).thenReturn(vo(GROUP_ID, GROUP_TYPE_PERSONAL, UID_OTHER, true));
        when(groupDao.selectCountVisibleById(eq(GROUP_ID), any(SysOrgGroupQuery.class))).thenReturn(0L);

        AuthorizationException ex = assertThrows(AuthorizationException.class, () -> service.load(GROUP_ID));

        assertEquals("FORBIDDEN", ex.getCode());
        assertEquals("无权查看该群组", ex.getMessage());
        verify(groupUserDao, never()).selectUserIdsByGroupId(anyString());
    }

    @Test
    void loadThrowsForbiddenWhenViewerScopeMisses() {
        loginAs(principal(UID_NORMAL));
        // 非全员可见公共群组、可见范围 SQL 判定（VisibleClause）未命中 → FORBIDDEN；
        // 命中链路（层级/岗位/本人）由 SQL 承担，Service 层仅验证门面计数与入参填充
        when(groupDao.selectVoById(GROUP_ID)).thenReturn(vo(GROUP_ID, GROUP_TYPE_COMMON, UID_OTHER, false));
        when(groupDao.selectCountVisibleById(eq(GROUP_ID), any(SysOrgGroupQuery.class))).thenReturn(0L);

        AuthorizationException ex = assertThrows(AuthorizationException.class, () -> service.load(GROUP_ID));

        assertEquals("FORBIDDEN", ex.getCode());
        assertEquals("无权查看该群组", ex.getMessage());
        // 可见性入参由服务端填充：userId = 当前登录人、admin = 角色判定（普通用户 false）
        verify(groupDao).selectCountVisibleById(eq(GROUP_ID), argThat(q ->
                UID_NORMAL.equals(q.getUserId()) && !q.isAdmin()));
    }

    @Test
    void loadAssemblesDetailsForOwnPersonalGroup() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.selectVoById(GROUP_ID)).thenReturn(vo(GROUP_ID, GROUP_TYPE_PERSONAL, UID_NORMAL, true));
        when(groupUserDao.selectUserIdsByGroupId(GROUP_ID)).thenReturn(List.of(USER_ID));
        // PERSONAL 无可见范围明细：viewer 查询返回空列表（全员可见）
        when(groupViewerDao.selectViewerIdsByGroupId(GROUP_ID)).thenReturn(List.of());
        when(elementDao.selectIdNameByIds(List.of(USER_ID)))
                .thenReturn(List.of(element(USER_ID, "张三", ORG_TYPE_USER, true, false)));

        SysOrgGroupVo result = service.load(GROUP_ID);

        assertEquals(List.of(USER_ID), result.getUserIds());
        assertEquals(List.of("张三"), result.getUserNames());
        // PERSONAL 无可见范围明细：恒为空列表（全员可见）
        assertEquals(List.of(), result.getViewerIds());
        assertEquals(List.of(), result.getViewerNames());
    }

    @Test
    void loadVisibleWhenUserHierarchyHitsViewerDept() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.selectVoById(GROUP_ID)).thenReturn(vo(GROUP_ID, GROUP_TYPE_COMMON, UID_OTHER, false));
        // 可见性命中链路（层级/岗位/本人）由 SQL 口径承担（setUp 默认桩 selectCountVisibleById = 1）
        when(groupViewerDao.selectViewerIdsByGroupId(GROUP_ID)).thenReturn(List.of(ORG_ID));
        when(groupUserDao.selectUserIdsByGroupId(GROUP_ID)).thenReturn(List.of(USER_ID));
        // 名称回填被调用两次（成员 + 可见范围），统一桩返回两节点，resolveElementNames 按 id 过滤保序
        when(elementDao.selectIdNameByIds(anyList())).thenReturn(List.of(
                element(USER_ID, "张三", ORG_TYPE_USER, true, false),
                element(ORG_ID, "研发部", ORG_TYPE_DEPT, true, false)));

        SysOrgGroupVo result = service.load(GROUP_ID);

        assertEquals(List.of(USER_ID), result.getUserIds());
        assertEquals(List.of("张三"), result.getUserNames());
        assertEquals(List.of(ORG_ID), result.getViewerIds());
        assertEquals(List.of("研发部"), result.getViewerNames());
    }

    @Test
    void loadVisibleWhenUserOnViewerPost() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.selectVoById(GROUP_ID)).thenReturn(vo(GROUP_ID, GROUP_TYPE_COMMON, UID_OTHER, false));
        // 可见性命中链路（岗位）由 SQL 口径承担（setUp 默认桩 selectCountVisibleById = 1）
        when(groupViewerDao.selectViewerIdsByGroupId(GROUP_ID)).thenReturn(List.of(POST_ID));
        // mock 默认返回 null 会触发名称回填 NPE，按 Dao 契约桩为空列表
        when(elementDao.selectIdNameByIds(anyList())).thenReturn(List.of());

        SysOrgGroupVo result = service.load(GROUP_ID);

        assertEquals(GROUP_ID, result.getFieldId());
    }

    @Test
    void loadVisibleWhenViewerIsSelf() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.selectVoById(GROUP_ID)).thenReturn(vo(GROUP_ID, GROUP_TYPE_COMMON, UID_OTHER, false));
        // 可见性命中链路（可见范围含本人）由 SQL 口径承担（setUp 默认桩 selectCountVisibleById = 1）
        when(groupViewerDao.selectViewerIdsByGroupId(GROUP_ID)).thenReturn(List.of(UID_NORMAL));
        // mock 默认返回 null 会触发名称回填 NPE，按 Dao 契约桩为空列表
        when(elementDao.selectIdNameByIds(anyList())).thenReturn(List.of());

        SysOrgGroupVo result = service.load(GROUP_ID);

        assertEquals(GROUP_ID, result.getFieldId());
    }

    /* ==================== list / count：可见性入参填充 ==================== */

    @Test
    void listFillsVisibilityInputsAndNormalizesSortForNormalUser() {
        when(groupDao.selectVoList(any(), any(), anyString(), anyString()))
                .thenReturn(List.of(vo(GROUP_ID, GROUP_TYPE_PERSONAL, UID_NORMAL, true)));

        List<SysOrgGroupVo> list = service.list(new SysOrgGroupQuery(), new Pages(), "name;DROP", "invalid");

        assertEquals(1, list.size());
        // 非法排序字段回退 createTime、方向回退 desc；userId/admin 由服务端填充
        verify(groupDao).selectVoList(argThat(q ->
                        UID_NORMAL.equals(q.getUserId()) && !q.isAdmin()),
                any(Pages.class), eq("createTime"), eq("desc"));
    }

    @Test
    void listMarksAdminWithoutVisibilityFiltering() {
        loginAs(principal(UID_ADMIN, ROLE_SYS_ORG_GROUP_ADMIN));
        when(groupDao.selectVoList(any(), any(), anyString(), anyString())).thenReturn(List.of());

        service.list(new SysOrgGroupQuery(), new Pages(), "updateTime", "asc");

        verify(groupDao).selectVoList(argThat(q ->
                        UID_ADMIN.equals(q.getUserId()) && q.isAdmin()),
                any(Pages.class), eq("updateTime"), eq("asc"));
    }

    @Test
    void countFillsVisibilityInputs() {
        when(groupDao.countVoList(any())).thenReturn(5L);

        assertEquals(5L, service.count(null));

        verify(groupDao).countVoList(argThat(q -> UID_NORMAL.equals(q.getUserId()) && !q.isAdmin()));
    }

    @Test
    void listThrowsUnauthorizedWhenNotLoggedIn() {
        userUtil.when(UserUtil::getCurrentUser).thenReturn(null);
        AuthorizationException ex = assertThrows(AuthorizationException.class,
                () -> service.list(new SysOrgGroupQuery(), new Pages(), null, null));
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    /* ==================== groupList / groupUsers：地址本群组页签（ORG2-BE-004） ==================== */

    @Test
    void groupListFillsVisibilityInputsAndUsesCreateTimeDesc() {
        when(groupDao.selectVoList(any(), any(Pages.class), anyString(), anyString()))
                .thenReturn(List.of(vo(GROUP_ID, GROUP_TYPE_PERSONAL, UID_NORMAL, true)));
        SysOrgGroupQuery query = new SysOrgGroupQuery();
        query.setFieldName("项目");

        List<SysOrgGroupVo> list = service.groupList(query, new Pages());

        assertEquals(1, list.size());
        // 复用 list 的可见性过滤分页：userId/admin 由服务端填充，固定 createTime desc
        verify(groupDao).selectVoList(argThat(q ->
                        UID_NORMAL.equals(q.getUserId()) && !q.isAdmin() && "项目".equals(q.getFieldName())),
                any(Pages.class), eq("createTime"), eq("desc"));
    }

    @Test
    void groupListAsAdminSkipsVisibilityFiltering() {
        loginAs(principal(UID_ADMIN, ROLE_SYS_ORG_GROUP_ADMIN));
        when(groupDao.selectVoList(any(), any(Pages.class), anyString(), anyString())).thenReturn(List.of());

        service.groupList(new SysOrgGroupQuery(), new Pages());

        verify(groupDao).selectVoList(argThat(q ->
                        UID_ADMIN.equals(q.getUserId()) && q.isAdmin()),
                any(Pages.class), eq("createTime"), eq("desc"));
    }

    @Test
    void groupUsersThrowsNotFoundWhenGroupMissing() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.selectById(anyString())).thenReturn(null);

        AuthorizationException ex = assertThrows(AuthorizationException.class,
                () -> service.groupUsers(GROUP_ID, null));

        assertEquals("E_NOT_FOUND", ex.getCode());
        assertEquals("未找到该群组", ex.getMessage());
        verify(groupUserDao, never()).selectUsersByGroupId(anyString());
    }

    @Test
    void groupUsersThrowsForbiddenWhenGroupNotViewable() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, GROUP_TYPE_PERSONAL, UID_OTHER, true));
        when(groupDao.selectCountVisibleById(eq(GROUP_ID), any(SysOrgGroupQuery.class))).thenReturn(0L);

        AuthorizationException ex = assertThrows(AuthorizationException.class,
                () -> service.groupUsers(GROUP_ID, null));

        assertEquals("FORBIDDEN", ex.getCode());
        assertEquals("无权查看该群组", ex.getMessage());
        // 可见性入参由服务端填充：userId = 当前登录人、admin = 角色判定（普通用户 false）
        verify(groupDao).selectCountVisibleById(eq(GROUP_ID), argThat(q ->
                UID_NORMAL.equals(q.getUserId()) && !q.isAdmin()));
        verify(groupUserDao, never()).selectUsersByGroupId(anyString());
    }

    @Test
    void groupUsersThrowsUnauthorizedWhenNotLoggedIn() {
        userUtil.when(UserUtil::getCurrentUser).thenReturn(null);
        when(groupDao.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, GROUP_TYPE_PERSONAL, UID_NORMAL, true));

        AuthorizationException ex = assertThrows(AuthorizationException.class,
                () -> service.groupUsers(GROUP_ID, null));

        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    @Test
    void groupUsersDefaultsToUserTypeAndReturnsMemberDetails() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, GROUP_TYPE_COMMON, UID_OTHER, true));
        // setUp 默认桩 selectCountVisibleById = 1（可见）
        List<SysOrgElement> members = List.of(
                element(USER_ID, "张三", ORG_TYPE_USER, true, false),
                element(DISABLED_USER_ID, "李四", ORG_TYPE_USER, true, false));
        when(groupUserDao.selectUsersByGroupId(GROUP_ID)).thenReturn(members);

        // orgTypes 为空 → 默认 [3] 仅人员，查询成员明细并原样返回
        assertEquals(members, service.groupUsers(GROUP_ID, null));
        verify(groupUserDao).selectUsersByGroupId(GROUP_ID);
    }

    @Test
    void groupUsersIntersectsOrgTypesAndSkipsQueryWhenNoOverlap() {
        loginAs(principal(UID_NORMAL));
        when(groupDao.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, GROUP_TYPE_COMMON, UID_OTHER, true));

        // orgTypes 不含 3：成员均为人员(3)，交集为空，直接空列表且不查成员明细
        assertEquals(List.of(), service.groupUsers(GROUP_ID, List.of(1, 2, 4, 5, 6)));
        verify(groupUserDao, never()).selectUsersByGroupId(anyString());
    }

    @Test
    void groupUsersRejectsBlankGroupId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.groupUsers("  ", null));

        assertEquals("groupId must not be blank", ex.getMessage());
    }

    /* ==================== resolveAdmin ==================== */

    @Test
    void resolveAdminRecognizesGroupAdminRoles() {
        loginAs(principal(UID_NORMAL, ROLE_SYS_ORG_GROUP_ADMIN));
        assertTrue(service.resolveAdmin(UID_NORMAL));

        loginAs(principal(UID_NORMAL, ROLE_SYS_ORG_ADMIN));
        assertTrue(service.resolveAdmin(UID_NORMAL));

        // SYS_ADMIN 由 RoleUtil.isSuperAdmin 自动包含所有权限
        loginAs(principal(UID_NORMAL, ROLE_ADMIN));
        assertTrue(service.resolveAdmin(UID_NORMAL));

        loginAs(principal(UID_NORMAL));
        assertFalse(service.resolveAdmin(UID_NORMAL));
    }

    @Test
    void resolveAdminReturnsFalseForNullOrMismatchedUid() {
        assertFalse(service.resolveAdmin(null));
        assertFalse(service.resolveAdmin(" "));

        // uid 与当前登录人不一致 → 一律非管理员（防伪造）
        loginAs(principal(UID_NORMAL, ROLE_SYS_ORG_GROUP_ADMIN));
        assertFalse(service.resolveAdmin(UID_OTHER));
    }

    /* ==================== 测试辅助 ==================== */

    private void loginAs(SysOrgUser user) {
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user);
    }

    /** 构造当前登录人：fieldEnable=true，角色仅携带 field_code（RoleUtil 判定口径） */
    private static SysOrgUser principal(String uid, String... roleCodes) {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(uid);
        user.setFieldEnable(true);
        List<SysOrgRole> roles = new ArrayList<>();
        for (String roleCode : roleCodes) {
            SysOrgRole role = new SysOrgRole();
            role.setFieldCode(roleCode);
            roles.add(role);
        }
        user.setRoles(roles);
        return user;
    }

    /** 构造组织架构节点 */
    private static SysOrgElement element(String id, String name, int orgType, boolean enable, boolean virtual) {
        SysOrgElement e = new SysOrgElement();
        e.setFieldId(id);
        e.setFieldName(name);
        e.setFieldOrgType(orgType);
        e.setFieldEnable(enable);
        e.setFieldIsVirtual(virtual);
        return e;
    }

    /** 构造群组行 */
    private static SysOrgGroup group(String id, String type, String ownerId, boolean isAllViewer) {
        SysOrgGroup g = new SysOrgGroup();
        g.setFieldId(id);
        g.setFieldType(type);
        g.setFieldOwnerId(ownerId);
        g.setFieldIsAllViewer(isAllViewer);
        g.setFieldEnable(true);
        return g;
    }

    /** 构造群组 Vo（load 回查桩） */
    private static SysOrgGroupVo vo(String id, String type, String ownerId, boolean isAllViewer) {
        SysOrgGroupVo v = new SysOrgGroupVo();
        v.setFieldId(id);
        v.setFieldType(type);
        v.setFieldOwnerId(ownerId);
        v.setFieldIsAllViewer(isAllViewer);
        return v;
    }

    /** 构造保存/更新请求 */
    private static SysOrgGroupRequest request(String id, String name, String type,
                                              List<String> userIds, List<String> viewerIds) {
        SysOrgGroupRequest r = new SysOrgGroupRequest();
        r.setFieldId(id);
        r.setFieldName(name);
        r.setFieldType(type);
        r.setUserIds(userIds);
        r.setViewerIds(viewerIds);
        return r;
    }
}
