package com.common.system.sys.org.service;

import com.common.system.sys.org.dao.SysOrgAuthDao;
import com.common.system.sys.org.model.SysOrgAuth;
import com.common.system.sys.org.response.AuthGroupVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限业务服务测试
 *
 * @author Eric
 * @date 2026/8/7
 * @description 覆盖第二个启动任务：roles.yml 权限增量同步（不存在插入、已存在跳过、空名称补全）与模型解析。
 */
class SysOrgAuthServiceTest {

    private SysOrgAuthDao dao;
    private SysOrgAuthService service;

    @BeforeEach
    void setUp() {
        dao = mock(SysOrgAuthDao.class);
        service = new SysOrgAuthService(dao);
    }

    @Test
    void insertsAuthWhenAbsent() {
        when(dao.selectByCode("SYS_ADMIN")).thenReturn(null);
        when(dao.insert(any(SysOrgAuth.class))).thenReturn(1);

        service.syncAuth("SYS_ADMIN", "COMMON", null, null);

        verify(dao).insert(argThat(auth ->
                "SYS_ADMIN".equals(auth.getFieldCode())
                        && "COMMON".equals(auth.getFieldModel())
                        && auth.getFieldName() == null));
    }

    @Test
    void insertsAuthWithChineseNameWhenProvided() {
        when(dao.selectByCode("SYS_ORG_ADMIN")).thenReturn(null);
        when(dao.insert(any(SysOrgAuth.class))).thenReturn(1);

        service.syncAuth("SYS_ORG_ADMIN", "SYS-ORG", "组织架构模块管理员", "拥有该模块下所有权限");

        verify(dao).insert(argThat(auth ->
                "SYS_ORG_ADMIN".equals(auth.getFieldCode())
                        && "SYS-ORG".equals(auth.getFieldModel())
                        && "组织架构模块管理员".equals(auth.getFieldName())
                        && "拥有该模块下所有权限".equals(auth.getFieldDescription())));
    }

    @Test
    void skipsWhenAuthExistsWithName() {
        SysOrgAuth exist = new SysOrgAuth();
        exist.setFieldName("系统管理员");
        when(dao.selectByCode("SYS_ADMIN")).thenReturn(exist);

        service.syncAuth("SYS_ADMIN", "COMMON", "系统管理员", null);

        verify(dao, never()).insert(any(SysOrgAuth.class));
        verify(dao, never()).update(any(SysOrgAuth.class));
    }

    @Test
    void fillsNameWhenStoredNameIsBlank() {
        SysOrgAuth exist = new SysOrgAuth();
        when(dao.selectByCode("SYS_ADMIN")).thenReturn(exist);
        when(dao.update(any(SysOrgAuth.class))).thenReturn(1);

        service.syncAuth("SYS_ADMIN", "COMMON", "系统管理员", null);

        verify(dao).update(argThat(auth -> "系统管理员".equals(auth.getFieldName())));
    }

    @Test
    void ignoresPlaceholderName() {
        when(dao.selectByCode("SYS_ADMIN")).thenReturn(null);
        when(dao.insert(any(SysOrgAuth.class))).thenReturn(1);

        // 占位符（无中文）与空值都不应写入库中名称
        service.syncAuth("SYS_ADMIN", "COMMON", "?????", null);

        verify(dao).insert(argThat(auth -> auth.getFieldName() == null));
    }

    @Test
    void syncsAllRolesResourcesFromClasspath() {
        when(dao.selectByCode(anyString())).thenReturn(null);
        when(dao.insert(any(SysOrgAuth.class))).thenReturn(1);

        service.syncAuthsFromRolesYml();

        // common/roles.yml(1) + sys/att/roles.yml(1) + sys/org/roles.yml(6)
        verify(dao, atLeast(8)).insert(any(SysOrgAuth.class));
        // field_model 取同目录 application.yml 的 MODEL_KEY
        verify(dao).insert(argThat(auth ->
                "SYS_ADMIN".equals(auth.getFieldCode()) && "COMMON".equals(auth.getFieldModel())));
        verify(dao).insert(argThat(auth ->
                "SYS_ORG_ADMIN".equals(auth.getFieldCode()) && "SYS-ORG".equals(auth.getFieldModel())));
    }

    @Test
    void ensureAuthReturnsExistingWhenPresent() {
        SysOrgAuth exist = new SysOrgAuth();
        when(dao.selectByCode("SYS_ADMIN")).thenReturn(exist);

        SysOrgAuth result = service.ensureAuth("SYS_ADMIN", "系统管理员", "COMMON");

        assertEquals(exist, result);
        verify(dao, never()).insert(any(SysOrgAuth.class));
    }

    @Test
    void ensureAuthCreatesWhenAbsent() {
        when(dao.selectByCode("SYS_ADMIN")).thenReturn(null);
        when(dao.insert(any(SysOrgAuth.class))).thenReturn(1);

        SysOrgAuth result = service.ensureAuth("SYS_ADMIN", "系统管理员", "COMMON");

        assertEquals("SYS_ADMIN", result.getFieldCode());
        assertEquals("COMMON", result.getFieldModel());
        assertEquals("系统管理员", result.getFieldName());
        assertNotNull(result.getFieldId());
        assertEquals(32, result.getFieldId().length());
    }

    /* ==================== 权限可选项分组（角色编辑页） ==================== */

    @Test
    void listAuthOptionsGroupsByModelWithYmlNames() {
        SysOrgAuth orgAuth = new SysOrgAuth();
        orgAuth.setFieldId("auth-org");
        orgAuth.setFieldName("组织架构模块管理员");
        orgAuth.setFieldCode("SYS_ORG_ADMIN");
        orgAuth.setFieldModel("SYS-ORG");
        SysOrgAuth commonAuth = new SysOrgAuth();
        commonAuth.setFieldId("auth-common");
        commonAuth.setFieldName("系统管理员");
        commonAuth.setFieldCode("SYS_ADMIN");
        commonAuth.setFieldModel("COMMON");
        when(dao.selectList(isNull(), isNull())).thenReturn(Arrays.asList(orgAuth, commonAuth));

        List<AuthGroupVo> groups = service.listAuthOptions();

        // 分组按 model 字典序：COMMON 在前
        assertEquals(2, groups.size());
        AuthGroupVo common = groups.get(0);
        assertEquals("COMMON", common.getModel());
        // 分组标题读取 application.yml 的 MODEL_NAME（classpath 中的 common/application.yml）
        assertEquals("公共", common.getModelName());
        assertEquals(1, common.getAuths().size());
        assertEquals("系统管理员", common.getAuths().get(0).getFieldName());
        AuthGroupVo org = groups.get(1);
        assertEquals("SYS-ORG", org.getModel());
        assertEquals("组织架构和权限模块", org.getModelName());
        assertEquals("SYS_ORG_ADMIN", org.getAuths().get(0).getFieldCode());
    }

    @Test
    void listAuthOptionsFallsBackModelWhenYmlNameMissing() {
        SysOrgAuth auth = new SysOrgAuth();
        auth.setFieldId("auth-unknown");
        auth.setFieldName("未知模块权限");
        auth.setFieldCode("UNKNOWN_AUTH");
        auth.setFieldModel("UNKNOWN-MODEL");
        when(dao.selectList(isNull(), isNull())).thenReturn(List.of(auth));

        List<AuthGroupVo> groups = service.listAuthOptions();

        assertEquals(1, groups.size());
        // application.yml 中无 UNKNOWN-MODEL 的 MODEL_NAME 中文名时回退 field_model 原值
        assertEquals("UNKNOWN-MODEL", groups.get(0).getModelName());
    }
}
