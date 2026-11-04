package com.common.system.common.setting.service;

import com.common.system.common.base.service.BaseService;
import com.common.system.common.setting.dao.CommonSettingDao;
import com.common.system.common.setting.model.CommonSetting;
import com.common.system.common.utils.StringUtil;
import com.common.system.common.utils.YmlResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 公共配置信息业务服务
 *
 * @author Eric
 * @date 2026/8/19
 * @description 承载「系统配置信息」的后端内部调用能力（不提供对外接口）：
 *              <ul>
 *              <li>启动时扫描各模块 application.yml 的 SETTING 段（SETTING.${field_type}.${field_key}: ${field_default_value}），
 *                  将配置增量同步到 common_setting；</li>
 *              <li>按 (field_type, field_key) 读取/更新配置值，供各模块内部使用；</li>
 *              </ul>
 *              同步规则（依据 artificial/docs/设计文档/common/common_setting.md）：
 *              配置不存在则插入；存在且默认值未变更则跳过；默认值发生变更时，
 *              若 field_value != field_default_value（用户已改过配置值）只更新默认值，
 *              若 field_value = field_default_value（用户未改过）则同时更新配置值与默认值。
 */
@Service
public class CommonSettingService extends BaseService<CommonSetting, CommonSettingDao> {

    private static final Logger log = LoggerFactory.getLogger(CommonSettingService.class);

    /** application.yml 资源扫描路径（提供 MODEL_KEY 与 SETTING 段） */
    private static final String APPLICATION_RESOURCE_PATTERN = "classpath*:properties/**/application.yml";

    private static final String KEY_MODEL_KEY = "MODEL_KEY";
    private static final String KEY_SETTING = "SETTING";

    public CommonSettingService(CommonSettingDao baseDao) {
        super(baseDao);
    }

    /**
     * 系统启动任务：扫描 properties/ 下各模块 application.yml 的 SETTING 段，
     * 将配置增量同步到 common_setting。
     * <p>field_model_name 取同文件 MODEL_KEY（缺失时回退按目录层级推导，如 common → COMMON）。</p>
     *
     * @return 本次处理（新增 + 更新）的配置条数
     */
    @Transactional
    public int syncFromYml() {
        int handled = 0;
        try {
            Resource[] resources = YmlResourceUtil.getResources(APPLICATION_RESOURCE_PATTERN);
            for (Resource resource : resources) {
                Map<String, Object> data = YmlResourceUtil.loadYml(resource);
                Object setting = data.get(KEY_SETTING);
                if (!(setting instanceof Map)) {
                    continue;
                }
                String modelKey = resolveModelKey(data, resource);
                handled += syncSettings((Map<?, ?>) setting, modelKey);
            }
        } catch (IOException e) {
            log.error("failed to load settings from application.yml, pattern={}", APPLICATION_RESOURCE_PATTERN, e);
            throw new IllegalStateException("failed to load settings from application.yml: " + APPLICATION_RESOURCE_PATTERN, e);
        }
        log.info("settings synced from application.yml: handled {} settings", handled);
        return handled;
    }

    /**
     * 按类型读取该类型下全部配置（key → value）。
     *
     * @param fieldType 配置类型
     * @return 配置值集合；无配置时返回空 Map
     */
    public Map<String, String> getValues(String fieldType) {
        Map<String, String> values = new LinkedHashMap<>();
        if (StringUtil.isEmpty(fieldType)) {
            return values;
        }
        for (CommonSetting setting : getBaseDao().selectByType(fieldType)) {
            values.put(setting.getFieldKey(), setting.getFieldValue());
        }
        return values;
    }

    /**
     * 按类型 + 配置字段读取配置值。
     *
     * @return 配置值；配置不存在时返回 null
     */
    public String getValue(String fieldType, String fieldKey) {
        if (StringUtil.isEmpty(fieldType) || StringUtil.isEmpty(fieldKey)) {
            return null;
        }
        CommonSetting setting = getBaseDao().selectByTypeAndKey(fieldType, fieldKey);
        return null == setting ? null : setting.getFieldValue();
    }

    /**
     * 按类型 + 配置字段读取配置值，配置不存在或值为空时返回给定默认值。
     */
    public String getValueOrDefault(String fieldType, String fieldKey, String defaultValue) {
        String value = getValue(fieldType, fieldKey);
        return StringUtil.isEmpty(value) ? defaultValue : value;
    }

    /**
     * 按类型 + 配置字段读取整数配置值。
     *
     * @return 整数配置值；配置缺失或非数字时返回 null
     */
    public Integer getIntValue(String fieldType, String fieldKey) {
        String value = getValue(fieldType, fieldKey);
        if (StringUtil.isEmpty(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            log.warn("setting value is not a valid integer: type={}, key={}, value={}", fieldType, fieldKey, value);
            return null;
        }
    }

    /**
     * 按类型 + 配置字段读取布尔配置值。
     *
     * @return 布尔配置值；配置缺失时返回 null
     */
    public Boolean getBooleanValue(String fieldType, String fieldKey) {
        String value = getValue(fieldType, fieldKey);
        return StringUtil.isEmpty(value) ? null : Boolean.valueOf(value.trim());
    }

    /**
     * 内部维护：更新配置值（仅更新 field_value 与 field_update_time，不触碰默认值）。
     *
     * @param fieldType 配置类型
     * @param fieldKey  配置字段
     * @param value     新的配置值
     */
    @Transactional
    public void updateValue(String fieldType, String fieldKey, String value) {
        if (StringUtil.isEmpty(fieldType) || StringUtil.isEmpty(fieldKey)) {
            throw new IllegalArgumentException("fieldType 与 fieldKey 不能为空");
        }
        CommonSetting exist = getBaseDao().selectByTypeAndKey(fieldType, fieldKey);
        if (null == exist) {
            throw new IllegalArgumentException("配置不存在: " + fieldType + "." + fieldKey);
        }
        CommonSetting update = new CommonSetting();
        update.setFieldId(exist.getFieldId());
        update.setFieldValue(value);
        update.setFieldUpdateTime(new Date());
        update(update);
        log.info("setting value updated: type={}, key={}, value={}", fieldType, fieldKey, value);
    }

    /**
     * 同步单个配置：不存在则插入；存在且默认值未变更则跳过；
     * 默认值变更时按「用户是否改过配置值」决定只更新默认值或同时更新配置值。
     * <p>package-private 供单元测试直接调用。</p>
     *
     * @return true=本次实际处理（新增或更新）；false=默认值未变更被跳过
     */
    boolean syncSetting(String fieldType, String fieldKey, String defaultValue, String modelKey) {
        CommonSetting exist = getBaseDao().selectByTypeAndKey(fieldType, fieldKey);
        if (null == exist) {
            CommonSetting setting = new CommonSetting();
            setting.setFieldType(fieldType);
            setting.setFieldKey(fieldKey);
            setting.setFieldModelName(modelKey);
            setting.setFieldValue(defaultValue);
            setting.setFieldDefaultValue(defaultValue);
            setting.setFieldUpdateTime(new Date());
            insert(setting);
            log.info("setting inserted: type={}, key={}, value={}, model={}", fieldType, fieldKey, defaultValue, modelKey);
            return true;
        }
        // 默认值未变更：增量跳过
        if (ObjectsEquals(defaultValue, exist.getFieldDefaultValue())) {
            return false;
        }
        CommonSetting update = new CommonSetting();
        update.setFieldId(exist.getFieldId());
        update.setFieldDefaultValue(defaultValue);
        update.setFieldUpdateTime(new Date());
        // field_value = field_default_value（用户未改过）→ 同时更新配置值；否则只更新默认值，保留用户配置值
        if (ObjectsEquals(exist.getFieldValue(), exist.getFieldDefaultValue())) {
            update.setFieldValue(defaultValue);
        }
        update(update);
        log.info("setting default updated: type={}, key={}, value={}, keepUserValue={}",
                fieldType, fieldKey, defaultValue, update.getFieldValue() == null);
        return true;
    }

    /**
     * 解析 SETTING 段，逐个同步配置。
     *
     * @return 本次处理（新增 + 更新）的配置条数
     */
    private int syncSettings(Map<?, ?> setting, String modelKey) {
        int handled = 0;
        for (Map.Entry<?, ?> typeEntry : setting.entrySet()) {
            if (!(typeEntry.getValue() instanceof Map)) {
                continue;
            }
            String fieldType = String.valueOf(typeEntry.getKey());
            for (Map.Entry<?, ?> keyEntry : ((Map<?, ?>) typeEntry.getValue()).entrySet()) {
                Object defaultValue = keyEntry.getValue();
                if (null == defaultValue) {
                    continue;
                }
                if (syncSetting(fieldType, String.valueOf(keyEntry.getKey()), String.valueOf(defaultValue), modelKey)) {
                    handled++;
                }
            }
        }
        return handled;
    }

    /**
     * 计算配置所属模块：优先取当前 application.yml 的 MODEL_KEY；
     * 缺失时回退按 /properties/ 下的文件夹层级推导，全大写并用 '-' 拼接
     * （如 common/application.yml → COMMON，sys/org/application.yml → SYS-ORG）。
     */
    private String resolveModelKey(Map<String, Object> data, Resource appResource) throws IOException {
        Object modelKey = data.get(KEY_MODEL_KEY);
        if (null != modelKey && StringUtil.isNotEmpty(String.valueOf(modelKey))) {
            return String.valueOf(modelKey);
        }
        String url = appResource.getURL().toString();
        int idx = url.indexOf("properties/");
        if (idx < 0) {
            throw new IllegalStateException("unexpected application.yml resource url: " + url);
        }
        String path = url.substring(idx + "properties/".length());
        int lastSlash = path.lastIndexOf('/');
        String dir = lastSlash >= 0 ? path.substring(0, lastSlash) : "";
        return dir.toUpperCase(Locale.ROOT).replace('/', '-');
    }

    private static boolean ObjectsEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
