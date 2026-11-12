package com.eric.base.sys.org.service;

import com.eric.base.common.utils.PasswordUtil;
import com.eric.base.sys.org.dao.SysOrgAuthDao;
import com.eric.base.sys.org.dao.SysOrgElementDao;
import com.eric.base.sys.org.dao.SysOrgPostDao;
import com.eric.base.sys.org.dao.SysOrgRoleDao;
import com.eric.base.sys.org.dao.SysOrgUserDao;
import com.eric.base.sys.org.enums.SysOrgAccountStatus;
import com.eric.base.sys.org.model.SysOrgUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户服务密码加密逻辑测试
 *
 * @author Eric
 * @date 2026/8/7
 */
class SysOrgUserServiceTest {

    /** 32 位 ID，避免 BaseModel.setFieldId 触发重新生成 */
    private static final String ID = "12345678901234567890123456789012";
    private static final String RAW_PWD = "Abc@123456";

    private SysOrgUserDao dao;
    private SysOrgRoleDao roleDao;
    private SysOrgAuthDao authDao;
    private SysOrgPostDao postDao;
    private SysOrgElementDao elementDao;
    private com.eric.base.sys.org.dao.SysOrgUserPwdHistoryDao pwdHistoryDao;
    private com.eric.base.common.setting.service.CommonSettingService commonSettingService;
    private SysOrgUserService service;

    @BeforeEach
    void setUp() {
        dao = mock(SysOrgUserDao.class);
        roleDao = mock(SysOrgRoleDao.class);
        authDao = mock(SysOrgAuthDao.class);
        postDao = mock(SysOrgPostDao.class);
        elementDao = mock(SysOrgElementDao.class);
        pwdHistoryDao = mock(com.eric.base.sys.org.dao.SysOrgUserPwdHistoryDao.class);
        commonSettingService = mock(com.eric.base.common.setting.service.CommonSettingService.class);
        service = new SysOrgUserService(dao, roleDao, authDao, postDao, elementDao, pwdHistoryDao, commonSettingService);
    }

    @Test
    void insertEncodesPasswordBeforePersist() {
        SysOrgUser user = sampleUser();
        when(dao.insert(user)).thenReturn(1);

        service.insert(user);

        assertNotEquals(RAW_PWD, user.getFieldPassword());
        assertTrue(user.getFieldPassword().startsWith("$2"));
        verify(dao).insert(user);
    }

    @Test
    void insertKeepsBlankPasswordUnchanged() {
        SysOrgUser user = sampleUser();
        user.setFieldPassword(null);
        when(dao.insert(user)).thenReturn(1);

        service.insert(user);

        assertNull(user.getFieldPassword());
    }

    @Test
    void updateIgnoresPasswordField() {
        SysOrgUser user = sampleUser();
        when(dao.update(user)).thenReturn(1);

        service.update(user);

        // 密码不随编辑接口更新：请求携带的密码被忽略（置 null），仅通过 changePwd 修改
        assertNull(user.getFieldPassword());
        verify(dao).update(user);
    }

    @Test
    void changePasswordEncodesAndPersistsOnlyPwdFields() {
        when(dao.updatePassword(argThat(m -> true))).thenReturn(1);

        service.changePassword(ID, RAW_PWD);

        verify(dao).updatePassword(argThat(m ->
                ID.equals(m.getFieldId())
                        && m.getFieldPassword() != null
                        && m.getFieldPassword().startsWith("$2")
                        && m.getFieldLastChgPwdTime() != null));
    }

    @Test
    void changePasswordRejectsBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> service.changePassword("  ", RAW_PWD));
        assertThrows(IllegalArgumentException.class, () -> service.changePassword(ID, "  "));
    }

    @Test
    void changePasswordRejectsOutOfRangeLength() {
        assertThrows(IllegalArgumentException.class, () -> service.changePassword(ID, "12345"));
        assertThrows(IllegalArgumentException.class, () -> service.changePassword(ID, "123456789012345678901234567890123"));
    }

    @Test
    void changePasswordThrowsWhenNoRowAffected() {
        // dao.updatePassword 默认 mock 返回 0，模拟目标用户不存在
        assertThrows(IllegalStateException.class, () -> service.changePassword(ID, RAW_PWD));
    }

    @Test
    void loginSucceedsAndClearsPasswordAndUpdatesLastLoginTime() {
        SysOrgUser user = sampleUser();
        user.setFieldEnable(true);
        user.setFieldAccountStatus(SysOrgAccountStatus.NORMAL.getValue());
        user.setFieldPassword(PasswordUtil.encode(RAW_PWD));
        when(dao.selectByLoginName("admin")).thenReturn(user);
        when(dao.update(user)).thenReturn(1);
        when(roleDao.selectIdNameListByUserId(ID)).thenReturn(java.util.List.of());
        when(authDao.selectCodesByUserId(ID)).thenReturn(java.util.List.of());
        when(postDao.selectIdNameListByUserId(ID)).thenReturn(java.util.List.of());
        when(elementDao.selectVirtualIdNameListByUserId(ID)).thenReturn(java.util.List.of());

        SysOrgUser result = service.login("admin", RAW_PWD);

        assertEquals(ID, result.getFieldId());
        assertNull(result.getFieldPassword());
        org.junit.jupiter.api.Assertions.assertNotNull(result.getFieldLastLoginTime());
        verify(dao).update(argThat(m -> m.getFieldLastLoginTime() != null));
        // 登录成功组装角色/权限/岗位/虚拟组织列表
        org.junit.jupiter.api.Assertions.assertNotNull(result.getRoles());
        org.junit.jupiter.api.Assertions.assertNotNull(result.getAuths());
        org.junit.jupiter.api.Assertions.assertNotNull(result.getPosts());
        org.junit.jupiter.api.Assertions.assertNotNull(result.getVirtuals());
        verify(roleDao).selectIdNameListByUserId(ID);
        verify(authDao).selectCodesByUserId(ID);
        verify(postDao).selectIdNameListByUserId(ID);
        verify(elementDao).selectVirtualIdNameListByUserId(ID);
    }

    @Test
    void loginRejectsBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> service.login("  ", RAW_PWD));
        assertThrows(IllegalArgumentException.class, () -> service.login("admin", "  "));
        verify(dao, never()).selectByLoginName(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void loginRejectsUnknownUserWithGenericMessage() {
        when(dao.selectByLoginName("nobody")).thenReturn(null);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.login("nobody", RAW_PWD));

        assertEquals("账号或密码错误", ex.getMessage());
    }

    @Test
    void loginRejectsWrongPasswordWithGenericMessage() {
        SysOrgUser user = sampleUser();
        user.setFieldEnable(true);
        user.setFieldAccountStatus(SysOrgAccountStatus.NORMAL.getValue());
        user.setFieldPassword(PasswordUtil.encode(RAW_PWD));
        when(dao.selectByLoginName("admin")).thenReturn(user);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.login("admin", "wrong-password"));

        assertEquals("账号或密码错误", ex.getMessage());
        verify(dao, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void loginRejectsDisabledAccount() {
        SysOrgUser user = sampleUser();
        user.setFieldEnable(false);
        user.setFieldAccountStatus(SysOrgAccountStatus.NORMAL.getValue());
        user.setFieldPassword(PasswordUtil.encode(RAW_PWD));
        when(dao.selectByLoginName("admin")).thenReturn(user);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.login("admin", RAW_PWD));

        assertEquals("账号已被禁用", ex.getMessage());
    }

    @Test
    void loginRejectsAbnormalAccountStatus() {
        SysOrgUser user = sampleUser();
        user.setFieldEnable(true);
        user.setFieldAccountStatus(SysOrgAccountStatus.LOCKED.getValue());
        user.setFieldPassword(PasswordUtil.encode(RAW_PWD));
        when(dao.selectByLoginName("admin")).thenReturn(user);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.login("admin", RAW_PWD));

        assertEquals("账号状态异常，请联系管理员", ex.getMessage());
    }

    private static SysOrgUser sampleUser() {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(ID);
        user.setFieldPassword(RAW_PWD);
        return user;
    }
}
