package com.common.system.sys.task;

import com.common.system.common.cache.ModelCacheService;
import com.common.system.common.setting.service.CommonSettingService;
import com.common.system.sys.dict.service.SysDictService;
import com.common.system.sys.i18n.service.SysI18nSyncService;
import com.common.system.sys.org.enums.SysOrgAccountStatus;
import com.common.system.sys.org.enums.SysOrgJobStatus;
import com.common.system.sys.org.model.SysOrgUser;
import com.common.system.sys.org.service.SysOrgAuthService;
import com.common.system.sys.org.service.SysOrgElementService;
import com.common.system.sys.org.service.SysOrgRoleService;
import com.common.system.sys.org.service.SysOrgUserService;
import com.common.system.sys.task.service.SysTaskSchedulerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 系统启动任务
 *
 * @author Eric
 * @date 2026/8/7
 * @description 应用启动后按顺序执行内置初始化任务；后续新增任务时在 {@link #run} 中追加调用。
 */
@Component
public class SysStartupTask implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SysStartupTask.class);

    /** 内置管理员用户 ID：'0' 补足 32 位 */
    private static final String ADMIN_USER_ID = "0".repeat(32);

    /** 人员组织类型（sys_org_element.field_org_type: 1=机构 2=部门 3=人员 4=岗位） */
    private static final int ORG_TYPE_USER = 3;

    private final SysOrgUserService sysOrgUserService;
    private final SysOrgElementService sysOrgElementService;
    private final SysOrgAuthService sysOrgAuthService;
    private final SysOrgRoleService sysOrgRoleService;
    private final ModelCacheService modelCacheService;
    private final SysTaskSchedulerService sysTaskSchedulerService;
    private final CommonSettingService commonSettingService;
    private final SysDictService sysDictService;
    private final SysI18nSyncService sysI18nSyncService;

    public SysStartupTask(SysOrgUserService sysOrgUserService,
                          SysOrgElementService sysOrgElementService,
                          SysOrgAuthService sysOrgAuthService,
                          SysOrgRoleService sysOrgRoleService,
                          ModelCacheService modelCacheService,
                          SysTaskSchedulerService sysTaskSchedulerService,
                          CommonSettingService commonSettingService,
                          SysDictService sysDictService,
                          SysI18nSyncService sysI18nSyncService) {
        this.sysOrgUserService = sysOrgUserService;
        this.sysOrgElementService = sysOrgElementService;
        this.sysOrgAuthService = sysOrgAuthService;
        this.sysOrgRoleService = sysOrgRoleService;
        this.modelCacheService = modelCacheService;
        this.sysTaskSchedulerService = sysTaskSchedulerService;
        this.commonSettingService = commonSettingService;
        this.sysDictService = sysDictService;
        this.sysI18nSyncService = sysI18nSyncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 第一个启动任务：初始化内置管理员用户
        initAdminUser();
        // 第二个启动任务：同步 roles.yml 权限到 sys_org_auth
        syncAuthsFromRolesYml();
        // 第三个启动任务：初始化系统管理员角色并默认挂载 SYS_ADMIN 权限与 admin 用户
        ensureSystemAdminRole();
        // 第四个启动任务：缓存各模块 application.yml 的 MODEL_KEY / MODEL_NAME 到 Redis
        cacheModelNames();
        // 第五个启动任务：扫描 @SysTask 任务类写入 sys_task_main 并注册 cron 调度
        registerSysTasks();
        // 第六个启动任务：增量同步各模块 application.yml 的 SETTING 配置到 common_setting
        syncSettingsFromYml();
        // 第七个启动任务：增量导入各模块 dict.yml 系统码值到 sys_dict_type / sys_dict_item
        syncDictFromYml();
        // 第八个启动任务：i18n 初始语言保证 + 扫描 resources/i18n/** 资源按覆盖规则同步 DB + 刷新翻译缓存
        syncI18nFromResources();
        // 第九个启动任务：组织架构存量数据拼音列回填
        backfillElementPinyin();
    }

    /**
     * 第八个启动任务：多语言资源同步（初始语言保证、key 登记、四分支翻译覆盖、缓存刷新）。
     * <p>与部署动作（/backend/sys/i18n/deploy/run）复用 {@link SysI18nSyncService#syncFromResources}
     * 同一规则；人工编辑过的翻译（field_update_time 非空）不会被覆盖。</p>
     */
    private void syncI18nFromResources() {
        sysI18nSyncService.syncFromResources();
    }

    /**
     * 第九个启动任务：组织架构存量数据拼音回填。
     * <p>调 {@link SysOrgElementService#backfillPinyin()}，对 field_pinyin / field_pinyin_short
     * 存空的行按 field_name 重算，按 field_id 游标分批（每批 500），幂等可重跑。</p>
     */
    private void backfillElementPinyin() {
        int count = sysOrgElementService.backfillPinyin();
        if (count > 0) {
            log.info("组织架构存量拼音回填完成，共回写 {} 行", count);
        }
    }

    /**
     * 第七个启动任务：扫描 properties/ 下各模块 dict.yml，
     * 将系统码值增量导入 sys_dict_type / sys_dict_item（field_system = true）。
     */
    private void syncDictFromYml() {
        sysDictService.syncDictFromYml();
    }

    /**
     * 第六个启动任务：扫描 properties/ 下各模块 application.yml 的 SETTING 段，
     * 将配置增量同步到 common_setting（不存在则插入、默认值变更按同步规则更新）。
     */
    private void syncSettingsFromYml() {
        commonSettingService.syncFromYml();
    }

    /**
     * 第五个启动任务：扫描带 {@code @SysTask} 注解的任务类写入 sys_task_main（默认开启），
     * 并对所有启用任务按库中维护的 cron 动态注册调度。
     */
    private void registerSysTasks() {
        sysTaskSchedulerService.onApplicationReady();
    }

    /**
     * 第四个启动任务：扫描 properties/ 下各模块 application.yml，
     * 将 MODEL_KEY → MODEL_NAME 映射以一份 Map（Redis Hash）缓存到 Redis。
     */
    private void cacheModelNames() {
        int count = modelCacheService.cacheModelNames();
        log.info("startup task: cached {} model names to redis", count);
    }

    /**
     * 第一个启动任务：确保内置管理员用户存在。
     * <p>查询 sys_org_user 中 field_id = '0000...0'（'0' 补足 32 位）的用户，
     * 不存在则创建：field_name=管理员、field_code=admin、field_login_name=admin、
     * field_password=123456（由 {@link SysOrgUserService#insert} 以 BCrypt 加密后入库）。</p>
     */
    private void initAdminUser() {
        if (null != sysOrgUserService.selectById(ADMIN_USER_ID)) {
            log.info("startup task: built-in admin user already exists, skip (id={})", ADMIN_USER_ID);
            return;
        }
        SysOrgUser admin = new SysOrgUser();
        admin.setFieldId(ADMIN_USER_ID);
        admin.setFieldName("管理员");
        admin.setFieldCode("admin");
        admin.setFieldLoginName("admin");
        admin.setFieldPassword("123456");
        admin.setFieldOrgType(ORG_TYPE_USER);
        admin.setFieldLevel(1);
        admin.setFieldIsVirtual(false);
        admin.setFieldEnable(true);
        admin.setFieldJobStatus(SysOrgJobStatus.WORKING.getValue());
        admin.setFieldAccountStatus(SysOrgAccountStatus.NORMAL.getValue());
        admin.setFieldCreateTime(new Date());
        sysOrgUserService.insert(admin);
        log.info("startup task: built-in admin user created (id={}, loginName={})", ADMIN_USER_ID, admin.getFieldLoginName());
    }

    /**
     * 第二个启动任务：读取 properties/ 下各模块 roles.yml，
     * 将权限增量同步到 sys_org_auth（field_model 取同目录 application.yml 的 MODEL_KEY）。
     */
    private void syncAuthsFromRolesYml() {
        sysOrgAuthService.syncAuthsFromRolesYml();
    }

    /**
     * 第三个启动任务：确保系统管理员角色存在（field_id = '0' 补足 32 位，field_name = 系统管理员），
     * 并默认挂载 SYS_ADMIN 权限与 admin 用户。
     */
    private void ensureSystemAdminRole() {
        sysOrgRoleService.ensureSystemAdminRole(ADMIN_USER_ID);
    }
}
