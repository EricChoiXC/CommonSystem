package com.eric.base.sys.task;

import com.eric.base.common.cache.ModelCacheService;
import com.eric.base.common.module.service.CommonModuleService;
import com.eric.base.common.setting.service.CommonSettingService;
import com.eric.base.sys.dict.service.SysDictService;
import com.eric.base.sys.i18n.service.SysI18nSyncService;
import com.eric.base.sys.number.service.SysNumberModelCacheService;
import com.eric.base.sys.org.model.SysOrgUser;
import com.eric.base.sys.org.service.SysOrgAuthService;
import com.eric.base.sys.org.service.SysOrgElementService;
import com.eric.base.sys.org.service.SysOrgRoleService;
import com.eric.base.sys.org.service.SysOrgUserService;
import com.eric.base.sys.task.service.SysTaskSchedulerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统启动任务测试
 *
 * @author Eric
 * @date 2026/8/7
 * @description 覆盖第一个启动任务：内置管理员初始化（存在则跳过、不存在则创建）。
 */
class SysStartupTaskTest {

    /** '0' 补足 32 位，与启动任务中的管理员 ID 保持一致 */
    private static final String ADMIN_USER_ID = "0".repeat(32);

    private SysOrgUserService sysOrgUserService;
    private SysOrgElementService sysOrgElementService;
    private SysOrgAuthService sysOrgAuthService;
    private SysOrgRoleService sysOrgRoleService;
    private ModelCacheService modelCacheService;
    private SysTaskSchedulerService sysTaskSchedulerService;
    private CommonSettingService commonSettingService;
    private SysDictService sysDictService;
    private SysI18nSyncService sysI18nSyncService;
    private CommonModuleService commonModuleService;
    private SysNumberModelCacheService sysNumberModelCacheService;
    private SysStartupTask task;

    @BeforeEach
    void setUp() {
        sysOrgUserService = mock(SysOrgUserService.class);
        sysOrgElementService = mock(SysOrgElementService.class);
        sysOrgAuthService = mock(SysOrgAuthService.class);
        sysOrgRoleService = mock(SysOrgRoleService.class);
        modelCacheService = mock(ModelCacheService.class);
        sysTaskSchedulerService = mock(SysTaskSchedulerService.class);
        commonSettingService = mock(CommonSettingService.class);
        sysDictService = mock(SysDictService.class);
        sysI18nSyncService = mock(SysI18nSyncService.class);
        commonModuleService = mock(CommonModuleService.class);
        sysNumberModelCacheService = mock(SysNumberModelCacheService.class);
        task = new SysStartupTask(sysOrgUserService, sysOrgElementService, sysOrgAuthService,
                sysOrgRoleService, modelCacheService, sysTaskSchedulerService, commonSettingService,
                sysDictService, sysI18nSyncService, commonModuleService, sysNumberModelCacheService);
    }

    @Test
    void skipsCreationWhenAdminExists() {
        when(sysOrgUserService.selectById(ADMIN_USER_ID)).thenReturn(new SysOrgUser());

        task.run(null);

        verify(sysOrgUserService, never()).insert(any(SysOrgUser.class));
    }

    @Test
    void createsAdminWhenMissing() {
        when(sysOrgUserService.selectById(ADMIN_USER_ID)).thenReturn(null);
        when(sysOrgUserService.insert(any(SysOrgUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        task.run(null);

        verify(sysOrgUserService).insert(argThat(user ->
                ADMIN_USER_ID.equals(user.getFieldId())
                        && "管理员".equals(user.getFieldName())
                        && "admin".equals(user.getFieldCode())
                        && "admin".equals(user.getFieldLoginName())
                        && "123456".equals(user.getFieldPassword())));
    }

    @Test
    void runsAllStartupTasksInOrder() {
        when(sysOrgUserService.selectById(ADMIN_USER_ID)).thenReturn(new SysOrgUser());

        task.run(null);

        verify(sysOrgAuthService).syncAuthsFromRolesYml();
        verify(sysOrgRoleService).ensureSystemAdminRole(ADMIN_USER_ID);
        verify(sysTaskSchedulerService).onApplicationReady();
        verify(commonSettingService).syncFromYml();
        verify(sysDictService).syncDictFromYml();
        // 第八个启动任务：i18n 资源同步被调用
        verify(sysI18nSyncService).syncFromResources();
        // 第九个启动任务：组织架构存量拼音回填被调用
        verify(sysOrgElementService).backfillPinyin();
        // 第十个启动任务：模块组同步 + 注解校验 + 缓存预热被调用
        verify(commonModuleService).syncFromYml();
        // 第十一个启动任务：编号接入项缓存构建 + 失效接入项告警被调用
        verify(sysNumberModelCacheService).syncFromYml();
    }
}
