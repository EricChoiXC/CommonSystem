package com.common.system.sys.task;

import com.common.system.common.cache.ModelCacheService;
import com.common.system.common.setting.service.CommonSettingService;
import com.common.system.sys.dict.service.SysDictService;
import com.common.system.sys.org.model.SysOrgUser;
import com.common.system.sys.org.service.SysOrgAuthService;
import com.common.system.sys.org.service.SysOrgRoleService;
import com.common.system.sys.org.service.SysOrgUserService;
import com.common.system.sys.task.service.SysTaskSchedulerService;
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
    private SysOrgAuthService sysOrgAuthService;
    private SysOrgRoleService sysOrgRoleService;
    private ModelCacheService modelCacheService;
    private SysTaskSchedulerService sysTaskSchedulerService;
    private CommonSettingService commonSettingService;
    private SysDictService sysDictService;
    private SysStartupTask task;

    @BeforeEach
    void setUp() {
        sysOrgUserService = mock(SysOrgUserService.class);
        sysOrgAuthService = mock(SysOrgAuthService.class);
        sysOrgRoleService = mock(SysOrgRoleService.class);
        modelCacheService = mock(ModelCacheService.class);
        sysTaskSchedulerService = mock(SysTaskSchedulerService.class);
        commonSettingService = mock(CommonSettingService.class);
        sysDictService = mock(SysDictService.class);
        task = new SysStartupTask(sysOrgUserService, sysOrgAuthService, sysOrgRoleService,
                modelCacheService, sysTaskSchedulerService, commonSettingService, sysDictService);
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
                        && "admin".equals(user.getFieldPassword())));
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
    }
}
