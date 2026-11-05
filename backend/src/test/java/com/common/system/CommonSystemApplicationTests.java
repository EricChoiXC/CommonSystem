package com.common.system;

import com.common.system.common.utils.IDUtil;
import com.common.system.common.setting.mapper.CommonSettingMapper;
import com.common.system.sys.calendar.mapper.SysCalendarMainMapper;
import com.common.system.sys.calendar.mapper.SysCalendarPersonsMapper;
import com.common.system.sys.org.mapper.SysOrgUserPwdHistoryMapper;
import com.common.system.sys.att.mapper.SysAttFileMapper;
import com.common.system.sys.att.mapper.SysAttMainMapper;
import com.common.system.sys.att.mapper.SysAttOperationMapper;
import com.common.system.sys.dict.mapper.SysDictItemMapper;
import com.common.system.sys.dict.mapper.SysDictTypeMapper;
import com.common.system.sys.i18n.mapper.SysI18nKeyMapper;
import com.common.system.sys.i18n.mapper.SysI18nLanguageMapper;
import com.common.system.sys.i18n.mapper.SysI18nTranslationMapper;
import com.common.system.sys.i18n.mapper.SysI18nUserLanguageMapper;
import com.common.system.sys.i18n.model.SysI18nKey;
import com.common.system.sys.i18n.model.SysI18nLanguage;
import com.common.system.sys.i18n.model.SysI18nTranslation;
import com.common.system.sys.i18n.model.SysI18nUserLanguage;
import com.common.system.sys.notify.mapper.SysNotifyTodoMapper;
import com.common.system.sys.notify.mapper.SysNotifyTodoTargetMapper;
import com.common.system.sys.log.mapper.SysLogOperationMapper;
import com.common.system.sys.org.mapper.SysOrgAddressBookMapper;
import com.common.system.sys.org.mapper.SysOrgAuthMapper;
import com.common.system.sys.org.mapper.SysOrgDeptMapper;
import com.common.system.sys.org.mapper.SysOrgElementMapper;
import com.common.system.sys.org.mapper.SysOrgGroupMapper;
import com.common.system.sys.org.mapper.SysOrgOrgMapper;
import com.common.system.sys.org.mapper.SysOrgPostMapper;
import com.common.system.sys.org.mapper.SysOrgRoleMapper;
import com.common.system.sys.org.mapper.SysOrgUserMapper;
import com.common.system.sys.org.model.SysOrgAuth;
import com.common.system.sys.org.model.SysOrgRole;
import com.common.system.sys.task.mapper.SysTaskExampleMapper;
import com.common.system.sys.task.mapper.SysTaskHistoryMapper;
import com.common.system.sys.task.mapper.SysTaskMainMapper;
import com.common.system.sys.process.mapper.SysProcessAuditNoteMapper;
import com.common.system.sys.process.mapper.SysProcessExampleMapper;
import com.common.system.sys.process.mapper.SysProcessExampleNodeMapper;
import com.common.system.sys.process.mapper.SysProcessExampleWorkitemMapper;
import com.common.system.sys.process.mapper.SysProcessTemplateHistoryMapper;
import com.common.system.sys.process.mapper.SysProcessTemplateMapper;
import com.common.system.sys.process.mapper.SysProcessTemplateNodeMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 应用上下文冒烟测试
 *
 * <p>通过 {@code spring.autoconfigure.exclude} 排除 DataSource 与 MyBatis 自动配置（无需真实数据库），
 * 并以 mock 提供 Mapper bean，保证 UserUtil / SysOrgUserDao 等依赖可装配；
 * 主配置 application.properties 正常加载（含 spring.config.import 引入的 SETTING 公共配置）。</p>
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude="
		+ "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
		+ "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration")
@Import(CommonSystemApplicationTests.MockMapperConfig.class)
class CommonSystemApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void testIDUtil() {
		for (int i = 0; i < 10; i++) {
			System.out.println(IDUtil.initID());
		}
	}

	@Autowired
	private Environment environment;

	@Test
	void commonSettingsImportedFromYml() {
		// SETTING 段经 application.properties 的 spring.config.import 以 flat 键（保留 SETTING. 前缀）进入 environment
		assertEquals("localhost", environment.getProperty("SETTING.database.db_host"));
		assertEquals("root", environment.getProperty("SETTING.database.db_login_name"));
		assertEquals("com.mysql.cj.jdbc.Driver", environment.getProperty("SETTING.database.db_driver"));
		assertEquals("6379", environment.getProperty("SETTING.redis.redis_port"));
		assertEquals("5s", environment.getProperty("SETTING.redis.redis_timeout"));
	}

	@Test
	void applicationPropertiesReferToSettings() {
		// application.properties 占位符引用 SETTING 并完成解析（getProperty 返回解析后的值；
		// 本机未设置 CS_* 环境变量时回退 SETTING 默认值，故以下断言依赖默认环境）
		assertEquals("jdbc:mysql://localhost:3306/common"
						+ "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
						+ "&allowPublicKeyRetrieval=true&useSSL=false",
				environment.getProperty("spring.datasource.url"));
		assertEquals("com.mysql.cj.jdbc.Driver", environment.getProperty("spring.datasource.driver-class-name"));
		assertEquals("root", environment.getProperty("spring.datasource.username"));
		assertEquals("root", environment.getProperty("spring.datasource.password"));
		assertEquals("localhost", environment.getProperty("spring.data.redis.host"));
		assertEquals("6379", environment.getProperty("spring.data.redis.port"));
		assertEquals("0", environment.getProperty("spring.data.redis.database"));
		assertEquals("5s", environment.getProperty("spring.data.redis.timeout"));
	}

	@TestConfiguration
	static class MockMapperConfig {

		@Bean
		SysAttFileMapper sysAttFileMapper() {
			return Mockito.mock(SysAttFileMapper.class);
		}

		@Bean
		SysAttMainMapper sysAttMainMapper() {
			return Mockito.mock(SysAttMainMapper.class);
		}

		@Bean
		SysAttOperationMapper sysAttOperationMapper() {
			return Mockito.mock(SysAttOperationMapper.class);
		}

		@Bean
		SysOrgAddressBookMapper sysOrgAddressBookMapper() {
			return Mockito.mock(SysOrgAddressBookMapper.class);
		}

		@Bean
		SysOrgDeptMapper sysOrgDeptMapper() {
			return Mockito.mock(SysOrgDeptMapper.class);
		}

		@Bean
		SysOrgGroupMapper sysOrgGroupMapper() {
			return Mockito.mock(SysOrgGroupMapper.class);
		}

		@Bean
		SysOrgOrgMapper sysOrgOrgMapper() {
			return Mockito.mock(SysOrgOrgMapper.class);
		}

		@Bean
		SysOrgUserMapper sysOrgUserMapper() {
			SysOrgUserMapper mapper = Mockito.mock(SysOrgUserMapper.class);
			// 启动任务(SysStartupTask)会查询内置管理员，这里默认返回"已存在"，
			// 避免测试上下文（无真实数据库）触发真实的双表插入。
			Mockito.when(mapper.selectById(Mockito.anyString())).thenReturn(new com.common.system.sys.org.model.SysOrgUser());
			return mapper;
		}

		@Bean
		SysOrgRoleMapper sysOrgRoleMapper() {
			SysOrgRoleMapper mapper = Mockito.mock(SysOrgRoleMapper.class);
			// 启动任务(SysStartupTask)会查询系统管理员角色并检查关联，这里默认"角色已存在、关联已建立"，
			// 避免测试上下文（无真实数据库）触发真实写入。
			Mockito.when(mapper.selectById(Mockito.anyString())).thenReturn(new SysOrgRole());
			Mockito.when(mapper.countRoleAuth(Mockito.anyString(), Mockito.anyString())).thenReturn(1);
			Mockito.when(mapper.countRoleElement(Mockito.anyString(), Mockito.anyString())).thenReturn(1);
			return mapper;
		}

		@Bean
		SysOrgAuthMapper sysOrgAuthMapper() {
			SysOrgAuthMapper mapper = Mockito.mock(SysOrgAuthMapper.class);
			// 启动任务(SysStartupTask)会按编码查询权限，这里默认"权限已存在"，
			// 避免测试上下文（无真实数据库）触发真实写入；syncAuth 可能补全名称/描述后
			// 走 update 分支，因此 insert/update 均返回 1 行。
			Mockito.when(mapper.selectByCode(Mockito.anyString())).thenReturn(new SysOrgAuth());
			Mockito.when(mapper.insert(Mockito.any())).thenReturn(1);
			Mockito.when(mapper.update(Mockito.any())).thenReturn(1);
			return mapper;
		}

		@Bean
		SysOrgElementMapper sysOrgElementMapper() {
			return Mockito.mock(SysOrgElementMapper.class);
		}

		@Bean
		SysOrgPostMapper sysOrgPostMapper() {
			return Mockito.mock(SysOrgPostMapper.class);
		}

		@Bean
		SysNotifyTodoMapper sysNotifyTodoMapper() {
			return Mockito.mock(SysNotifyTodoMapper.class);
		}

		@Bean
		SysDictTypeMapper sysDictTypeMapper() {
			SysDictTypeMapper mapper = Mockito.mock(SysDictTypeMapper.class);
			// 启动任务(SysStartupTask)会增量导入 dict.yml 系统码值，
			// 这里默认"类型不存在、插入成功"，避免测试上下文（无真实数据库）触发真实写库。
			Mockito.when(mapper.selectByCode(Mockito.anyString())).thenReturn(null);
			Mockito.when(mapper.insert(Mockito.any())).thenReturn(1);
			Mockito.when(mapper.update(Mockito.any())).thenReturn(1);
			return mapper;
		}

		@Bean
		SysDictItemMapper sysDictItemMapper() {
			SysDictItemMapper mapper = Mockito.mock(SysDictItemMapper.class);
			// 同上：码值项默认"不存在、插入成功"。
			Mockito.when(mapper.selectByTypeIdAndValue(Mockito.anyString(), Mockito.anyString())).thenReturn(null);
			Mockito.when(mapper.insert(Mockito.any())).thenReturn(1);
			Mockito.when(mapper.update(Mockito.any())).thenReturn(1);
			return mapper;
		}

		@Bean
		SysNotifyTodoTargetMapper sysNotifyTodoTargetMapper() {
			return Mockito.mock(SysNotifyTodoTargetMapper.class);
		}

		@Bean
		SysLogOperationMapper sysLogOperationMapper() {
			return Mockito.mock(SysLogOperationMapper.class);
		}

		@Bean
		CommonSettingMapper commonSettingMapper() {
			CommonSettingMapper mapper = Mockito.mock(CommonSettingMapper.class);
			// 启动任务(SysStartupTask)会增量同步 application.yml 的 SETTING 配置到 common_setting，
			// 这里默认“配置不存在、插入/更新均成功”，避免测试上下文（无真实数据库）触发真实写库。
			Mockito.when(mapper.selectByTypeAndKey(Mockito.anyString(), Mockito.anyString())).thenReturn(null);
			Mockito.when(mapper.insert(Mockito.any())).thenReturn(1);
			Mockito.when(mapper.update(Mockito.any())).thenReturn(1);
			return mapper;
		}

		@Bean
		SysTaskMainMapper sysTaskMainMapper() {
			SysTaskMainMapper mapper = Mockito.mock(SysTaskMainMapper.class);
			// 启动任务(SysStartupTask)会扫描登记定时任务并重新调度，这里默认
			// "无已登记任务、列表为空"，避免测试上下文（无真实数据库）触发真实写入。
			Mockito.when(mapper.selectList(Mockito.any(), Mockito.any())).thenReturn(java.util.Collections.emptyList());
			return mapper;
		}

		@Bean
		SysTaskExampleMapper sysTaskExampleMapper() {
			return Mockito.mock(SysTaskExampleMapper.class);
		}

		@Bean
		SysTaskHistoryMapper sysTaskHistoryMapper() {
			return Mockito.mock(SysTaskHistoryMapper.class);
		}

		@Bean
		SysProcessTemplateMapper sysProcessTemplateMapper() {
			return Mockito.mock(SysProcessTemplateMapper.class);
		}

		@Bean
		SysProcessTemplateNodeMapper sysProcessTemplateNodeMapper() {
			return Mockito.mock(SysProcessTemplateNodeMapper.class);
		}

		@Bean
		SysProcessTemplateHistoryMapper sysProcessTemplateHistoryMapper() {
			return Mockito.mock(SysProcessTemplateHistoryMapper.class);
		}

		@Bean
		SysProcessExampleMapper sysProcessExampleMapper() {
			return Mockito.mock(SysProcessExampleMapper.class);
		}

		@Bean
		SysProcessExampleNodeMapper sysProcessExampleNodeMapper() {
			return Mockito.mock(SysProcessExampleNodeMapper.class);
		}

		@Bean
		SysProcessExampleWorkitemMapper sysProcessExampleWorkitemMapper() {
			return Mockito.mock(SysProcessExampleWorkitemMapper.class);
		}

		@Bean
		SysProcessAuditNoteMapper sysProcessAuditNoteMapper() {
			return Mockito.mock(SysProcessAuditNoteMapper.class);
		}

		/* ==================== 系统模块补充 Mapper（日历/用户密码历史） ==================== */

		@Bean
		SysCalendarMainMapper sysCalendarMainMapper() {
			return Mockito.mock(SysCalendarMainMapper.class);
		}

		@Bean
		SysCalendarPersonsMapper sysCalendarPersonsMapper() {
			return Mockito.mock(SysCalendarPersonsMapper.class);
		}

		@Bean
		SysOrgUserPwdHistoryMapper sysOrgUserPwdHistoryMapper() {
			return Mockito.mock(SysOrgUserPwdHistoryMapper.class);
		}

		/* ==================== 多语言模块 Mapper（i18n，4 个） ==================== */

		@Bean
		SysI18nLanguageMapper sysI18nLanguageMapper() {
			SysI18nLanguageMapper mapper = Mockito.mock(SysI18nLanguageMapper.class);
			// 多语言模块 Dao/服务依赖本 Mapper；测试上下文（无真实数据库）默认
			// "语言表为空、按码查询不存在、插入成功"，保证后续启动同步（I18N2-BE-002）
			// 走"不存在、插入成功"分支而不真实写库。
			Mockito.when(mapper.selectByCode(Mockito.anyString())).thenReturn(new SysI18nLanguage());
			Mockito.when(mapper.selectDefault()).thenReturn(new SysI18nLanguage());
			Mockito.when(mapper.countAll()).thenReturn(1L);
			Mockito.when(mapper.insert(Mockito.any())).thenReturn(1);
			Mockito.when(mapper.update(Mockito.any())).thenReturn(1);
			return mapper;
		}

		@Bean
		SysI18nKeyMapper sysI18nKeyMapper() {
			SysI18nKeyMapper mapper = Mockito.mock(SysI18nKeyMapper.class);
			// 资源登记默认"不存在、插入成功"，同步幂等分支不真实写库。
			Mockito.when(mapper.selectByModelAndKey(Mockito.anyString(), Mockito.anyString())).thenReturn(new SysI18nKey());
			Mockito.when(mapper.countByModelAndKey(Mockito.anyString(), Mockito.anyString())).thenReturn(1L);
			Mockito.when(mapper.insert(Mockito.any())).thenReturn(1);
			Mockito.when(mapper.update(Mockito.any())).thenReturn(1);
			return mapper;
		}

		@Bean
		SysI18nTranslationMapper sysI18nTranslationMapper() {
			SysI18nTranslationMapper mapper = Mockito.mock(SysI18nTranslationMapper.class);
			// 翻译默认"已存在（人工编辑过）"，避免同步覆盖分支真实写库。
			Mockito.when(mapper.selectByKeyAndLanguage(Mockito.anyString(), Mockito.anyString()))
					.thenReturn(new SysI18nTranslation());
			Mockito.when(mapper.insert(Mockito.any())).thenReturn(1);
			Mockito.when(mapper.update(Mockito.any())).thenReturn(1);
			Mockito.when(mapper.updateIfNotEdited(Mockito.any())).thenReturn(0);
			return mapper;
		}

		@Bean
		SysI18nUserLanguageMapper sysI18nUserLanguageMapper() {
			SysI18nUserLanguageMapper mapper = Mockito.mock(SysI18nUserLanguageMapper.class);
			// 用户语言登记默认"已存在"，登录 upsert 走"存在则更新"分支不真实写库。
			Mockito.when(mapper.selectByUserId(Mockito.anyString())).thenReturn(new SysI18nUserLanguage());
			Mockito.when(mapper.insert(Mockito.any(SysI18nUserLanguage.class))).thenReturn(1);
			Mockito.when(mapper.updateByUserId(Mockito.any())).thenReturn(1);
			Mockito.when(mapper.rebindLanguage(Mockito.anyString(), Mockito.anyString())).thenReturn(0);
			return mapper;
		}
	}

}
