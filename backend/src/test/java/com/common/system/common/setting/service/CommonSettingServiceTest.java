package com.common.system.common.setting.service;

import com.common.system.common.setting.dao.CommonSettingDao;
import com.common.system.common.setting.model.CommonSetting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 公共配置信息业务服务测试
 *
 * @author Eric
 * @date 2026/8/19
 * @description 覆盖配置增量同步规则（不存在插入、默认值未变跳过、默认值变更时按用户是否改过决定更新范围）
 *              与内部读取/维护方法。
 */
class CommonSettingServiceTest {

    private CommonSettingDao dao;
    private CommonSettingService service;

    @BeforeEach
    void setUp() {
        dao = mock(CommonSettingDao.class);
        service = new CommonSettingService(dao);
    }

    /* ==================== 增量同步 ==================== */

    @Test
    void insertsSettingWhenAbsent() {
        when(dao.selectByTypeAndKey("system", "login_lock_time")).thenReturn(null);
        when(dao.insert(any(CommonSetting.class))).thenReturn(1);

        service.syncSetting("system", "login_lock_time", "5", "COMMON");

        verify(dao).insert(argThat(setting ->
                "system".equals(setting.getFieldType())
                        && "login_lock_time".equals(setting.getFieldKey())
                        && "COMMON".equals(setting.getFieldModelName())
                        && "5".equals(setting.getFieldValue())
                        && "5".equals(setting.getFieldDefaultValue())
                        && null != setting.getFieldId()
                        && 32 == setting.getFieldId().length()));
    }

    @Test
    void skipsWhenDefaultUnchanged() {
        CommonSetting exist = new CommonSetting();
        exist.setFieldId("id-1");
        exist.setFieldDefaultValue("3600");
        exist.setFieldValue("3600");
        when(dao.selectByTypeAndKey("system", "login_unusing_time")).thenReturn(exist);

        service.syncSetting("system", "login_unusing_time", "3600", "COMMON");

        verify(dao, never()).insert(any(CommonSetting.class));
        verify(dao, never()).update(any(CommonSetting.class));
    }

    @Test
    void updatesValueAndDefaultWhenUserUnchanged() {
        // field_value = field_default_value（用户未改过）：默认值变更时同时更新配置值与默认值
        CommonSetting exist = new CommonSetting();
        exist.setFieldId("id-1");
        exist.setFieldDefaultValue("3600");
        exist.setFieldValue("3600");
        when(dao.selectByTypeAndKey("system", "login_unusing_time")).thenReturn(exist);
        when(dao.update(any(CommonSetting.class))).thenReturn(1);

        service.syncSetting("system", "login_unusing_time", "7200", "COMMON");

        verify(dao).update(argThat(setting ->
                "7200".equals(setting.getFieldDefaultValue()) && "7200".equals(setting.getFieldValue())));
    }

    @Test
    void updatesDefaultOnlyWhenUserChanged() {
        // field_value != field_default_value（用户改过配置值）：默认值变更时只更新默认值，保留用户配置值
        CommonSetting exist = new CommonSetting();
        exist.setFieldId("id-1");
        exist.setFieldDefaultValue("3600");
        exist.setFieldValue("1800");
        when(dao.selectByTypeAndKey("system", "login_unusing_time")).thenReturn(exist);
        when(dao.update(any(CommonSetting.class))).thenReturn(1);

        service.syncSetting("system", "login_unusing_time", "7200", "COMMON");

        verify(dao).update(argThat(setting ->
                "7200".equals(setting.getFieldDefaultValue()) && null == setting.getFieldValue()));
    }

    @Test
    void syncsAllSettingsFromClasspath() {
        // 覆盖系统启动任务：扫描 properties/**/application.yml 的 SETTING 段增量写入
        when(dao.selectByTypeAndKey(anyString(), anyString())).thenReturn(null);
        when(dao.insert(any(CommonSetting.class))).thenReturn(1);

        int handled = service.syncFromYml();

        // common/application.yml 的 SETTING 段共 16 条（database 7 + redis 5 + system 4），
        // sys/process/application.yml 的 SETTING 段共 7 条（process 3 + notify 4，
        // 见 artificial/docs/设计文档/sys/process/流程核心业务.md「流程公共配置」），
        // 其余为各业务模块后续补充的设置项（sys/calendar 等）
        verify(dao, atLeast(23)).insert(any(CommonSetting.class));
        // field_model_name 取同文件 MODEL_KEY（COMMON），field_value 初始等于默认值
        verify(dao).insert(argThat(setting ->
                "database".equals(setting.getFieldType())
                        && "db_type".equals(setting.getFieldKey())
                        && "COMMON".equals(setting.getFieldModelName())
                        && "mysql".equals(setting.getFieldValue())));
        verify(dao).insert(argThat(setting ->
                "database".equals(setting.getFieldType())
                        && "db_driver".equals(setting.getFieldKey())
                        && "com.mysql.cj.jdbc.Driver".equals(setting.getFieldValue())));
        verify(dao).insert(argThat(setting ->
                "redis".equals(setting.getFieldType())
                        && "redis_host".equals(setting.getFieldKey())
                        && "localhost".equals(setting.getFieldValue())));
        verify(dao).insert(argThat(setting ->
                "system".equals(setting.getFieldType())
                        && "login_open_lock".equals(setting.getFieldKey())
                        && "true".equals(setting.getFieldValue())));
        verify(dao).insert(argThat(setting ->
                "process".equals(setting.getFieldType())
                        && "auto_handle_pass".equals(setting.getFieldKey())
                        && "true".equals(setting.getFieldValue())));
        verify(dao).insert(argThat(setting ->
                "notify".equals(setting.getFieldType())
                        && "notify_handle_subject".equals(setting.getFieldKey())));
        assertEquals(35, handled);
    }

    /* ==================== 内部读取 ==================== */

    @Test
    void readsValueWhenPresent() {
        CommonSetting exist = new CommonSetting();
        exist.setFieldValue("3600");
        when(dao.selectByTypeAndKey("system", "login_unusing_time")).thenReturn(exist);

        assertEquals("3600", service.getValue("system", "login_unusing_time"));
        assertEquals("3600", service.getValueOrDefault("system", "login_unusing_time", "1"));
    }

    @Test
    void readsNullWhenMissing() {
        when(dao.selectByTypeAndKey(anyString(), anyString())).thenReturn(null);

        assertNull(service.getValue("system", "not_exist"));
        assertEquals("fallback", service.getValueOrDefault("system", "not_exist", "fallback"));
        assertNull(service.getIntValue("system", "not_exist"));
        assertNull(service.getBooleanValue("system", "not_exist"));
        assertEquals(0, service.getValues("database").size());
    }

    @Test
    void readsIntAndBooleanValue() {
        CommonSetting intSetting = new CommonSetting();
        intSetting.setFieldValue("300");
        when(dao.selectByTypeAndKey("system", "login_locking_time")).thenReturn(intSetting);
        CommonSetting boolSetting = new CommonSetting();
        boolSetting.setFieldValue("true");
        when(dao.selectByTypeAndKey("system", "login_open_lock")).thenReturn(boolSetting);

        assertEquals(Integer.valueOf(300), service.getIntValue("system", "login_locking_time"));
        assertEquals(Boolean.TRUE, service.getBooleanValue("system", "login_open_lock"));
    }

    @Test
    void readsValuesByType() {
        CommonSetting a = new CommonSetting();
        a.setFieldKey("db_type");
        a.setFieldValue("mysql");
        CommonSetting b = new CommonSetting();
        b.setFieldKey("db_host");
        b.setFieldValue("localhost");
        when(dao.selectByType("database")).thenReturn(Arrays.asList(a, b));

        Map<String, String> values = service.getValues("database");

        assertEquals(2, values.size());
        assertEquals("mysql", values.get("db_type"));
        assertEquals("localhost", values.get("db_host"));
    }

    /* ==================== 内部维护 ==================== */

    @Test
    void updatesExistingValue() {
        CommonSetting exist = new CommonSetting();
        exist.setFieldId("id-1");
        when(dao.selectByTypeAndKey("system", "login_unusing_time")).thenReturn(exist);
        when(dao.update(any(CommonSetting.class))).thenReturn(1);

        service.updateValue("system", "login_unusing_time", "1800");

        verify(dao).update(argThat(setting ->
                "1800".equals(setting.getFieldValue()) && null != setting.getFieldUpdateTime()));
    }

    @Test
    void rejectsUpdateWhenSettingMissing() {
        when(dao.selectByTypeAndKey("system", "not_exist")).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateValue("system", "not_exist", "1"));
    }
}
