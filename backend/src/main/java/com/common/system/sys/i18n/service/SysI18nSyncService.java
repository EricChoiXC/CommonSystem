package com.common.system.sys.i18n.service;

import com.common.system.common.utils.StringUtil;
import com.common.system.common.utils.YmlResourceUtil;
import com.common.system.sys.i18n.dao.SysI18nKeyDao;
import com.common.system.sys.i18n.dao.SysI18nLanguageDao;
import com.common.system.sys.i18n.dao.SysI18nTranslationDao;
import com.common.system.sys.i18n.model.SysI18nKey;
import com.common.system.sys.i18n.model.SysI18nLanguage;
import com.common.system.sys.i18n.model.SysI18nTranslation;
import com.common.system.sys.i18n.response.SysI18nDeployResultVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 多语言资源扫描与同步服务
 *
 * <p>「资源文件 → DB 同步」唯一实现，启动任务（第 8 个启动任务）与部署动作
 * （I18N5-BE-001）复用同一规则，保证两条入口行为一致（任务清单强顺序约束）：</p>
 * <ol>
 * <li>初始语言保证：{@code sys_i18n_language} 无记录时插入 zh_CN（默认、order 1）+
 *     en_US（order 2）；已初始化后不重复处理（管理员维护优先）；</li>
 * <li>扫描 {@code classpath*:i18n/**}/application.properties：field_model = 相对 i18n/
 *     的子目录层级名；key 登记 {@code sys_i18n_key}（key 全局唯一，跨模块重复拦截）；
 *     value 作为 zh_CN 翻译写入；</li>
 * <li>扫描 {@code application_${language_code}.properties}，按启用语言写入对应翻译；</li>
 * <li>覆盖规则四分支：翻译缺失→新增；field_update_time IS NULL→资源值覆盖；
 *     非空（人工编辑）→跳过；该语言无对应资源文件（或文件中无此 key）→删除人工未编辑的记录；</li>
 * <li>同步完成后刷新翻译缓存（{@link SysI18nCacheService}）。</li>
 * </ol>
 * <p>properties 含中文时必须以 UTF-8 读取（{@link InputStreamReader} + {@link Properties#load(Reader)}），
 * 禁止默认 ISO-8859-1 的 {@code load(InputStream)}（风险 R1）。</p>
 *
 * @author Eric
 * @date 2026/11/5
 */
@Service
public class SysI18nSyncService {

    private static final Logger log = LoggerFactory.getLogger(SysI18nSyncService.class);

    /** i18n 资源扫描路径（相对 resources/，field_model = 相对 i18n/ 的子目录层级名） */
    public static final String I18N_RESOURCE_PATTERN = "classpath*:i18n/**/application*.properties";

    /** 资源文件名（默认语言资源：资源登记 + zh_CN 翻译来源） */
    public static final String BASE_RESOURCE_NAME = "application.properties";

    /** 默认语言码（启动初始化：默认、order 1） */
    public static final String DEFAULT_LANGUAGE_CODE = I18nService.DEFAULT_LANGUAGE;

    /** 初始语言码（启动初始化：order 2） */
    public static final String INIT_LANGUAGE_CODE_EN = "en_US";

    private final SysI18nLanguageDao languageDao;
    private final SysI18nKeyDao keyDao;
    private final SysI18nTranslationDao translationDao;
    private final SysI18nCacheService cacheService;

    public SysI18nSyncService(SysI18nLanguageDao languageDao,
                              SysI18nKeyDao keyDao,
                              SysI18nTranslationDao translationDao,
                              SysI18nCacheService cacheService) {
        this.languageDao = languageDao;
        this.keyDao = keyDao;
        this.translationDao = translationDao;
        this.cacheService = cacheService;
    }

    /* ==================== 入口 ==================== */

    /**
     * 执行「资源文件 → DB」同步（启动任务与部署动作共用入口）：
     * 初始语言保证 → 资源扫描四分支同步 → 缓存刷新，整体一个事务。
     *
     * @return 同步结果（资源模块数、登记 key 数、各语言翻译条数）
     */
    @Transactional
    public SysI18nDeployResultVo syncFromResources() {
        Map<String, Map<String, Map<String, String>>> resourceBundles;
        try {
            ensureInitLanguages();
            resourceBundles = scanResourceBundles();
        } catch (IOException e) {
            // 启动同步失败按既有先例（SysDictService#syncDictFromYml）转 IllegalStateException，
            // 由启动任务日志暴露；部署动作经 GlobalExceptionHandler 返回错误响应
            log.error("failed to scan i18n resources, pattern={}", I18N_RESOURCE_PATTERN, e);
            throw new IllegalStateException("failed to scan i18n resources: " + I18N_RESOURCE_PATTERN, e);
        }
        SyncCounter counter = new SyncCounter();
        for (Map.Entry<String, Map<String, Map<String, String>>> bundle : resourceBundles.entrySet()) {
            syncModel(bundle.getKey(), bundle.getValue(), counter);
        }
        List<String> enabledLanguages = enabledLanguageCodes();
        int cached = cacheService.cacheTranslations(enabledLanguages);
        log.info("i18n sync from resources finished: models={}, keys handled={}, messages handled={}, cached={}",
                resourceBundles.size(), counter.keyCount, counter.messageCount, cached);
        SysI18nDeployResultVo result = new SysI18nDeployResultVo();
        result.setSyncedModelCount(resourceBundles.size());
        result.setSyncedKeyCount(counter.keyCount);
        // 各语言翻译条数（部署动作展示用；以 DB 当前值为准）
        Map<String, Integer> languageCounts = new LinkedHashMap<>();
        for (String language : enabledLanguages) {
            languageCounts.put(language, translationDao.selectByLanguage(language).size());
        }
        result.setLanguageCounts(languageCounts);
        return result;
    }

    /**
     * 初始语言保证：语言表无记录时插入 zh_CN（默认、order 1）+ en_US（order 2）。
     * <p>已初始化后不重复处理（管理员维护的语言清单优先，含停用 en_US 的场景）。</p>
     */
    private void ensureInitLanguages() {
        if (languageDao.countAll() > 0) {
            return;
        }
        insertInitLanguage(DEFAULT_LANGUAGE_CODE, "中文", true, 1);
        insertInitLanguage(INIT_LANGUAGE_CODE_EN, "English", true, 2);
        log.info("i18n init languages ensured: {}(default) + {}", DEFAULT_LANGUAGE_CODE, INIT_LANGUAGE_CODE_EN);
    }

    /**
     * 插入初始语言行。
     */
    private void insertInitLanguage(String code, String name, boolean isDefault, int order) {
        SysI18nLanguage language = new SysI18nLanguage();
        language.initFieldId();
        language.setFieldCode(code);
        language.setFieldName(name);
        language.setFieldEnable(true);
        language.setFieldDefault(isDefault);
        language.setFieldOrder(order);
        languageDao.insert(language);
    }

    /* ==================== 资源扫描 ==================== */

    /**
     * 扫描 classpath i18n 资源，组装 field_model → 语言码 → (key → 资源值) 的资源包：
     * {@code application.properties} 归默认语言（zh_CN），{@code application_${code}.properties}
     * 按文件名归对应语言；未启用语言的资源包保留在结果中，由同步核心按启用语言过滤。
     * <p>package-private 供单元测试核对扫描口径；同步核心另见 {@link #syncModel}。</p>
     *
     * @return field_model → 语言码 → properties 键值
     * @throws IOException 资源扫描/读取失败
     */
    Map<String, Map<String, Map<String, String>>> scanResourceBundles() throws IOException {
        Map<String, Map<String, Map<String, String>>> bundles = new LinkedHashMap<>();
        Resource[] resources = YmlResourceUtil.getResources(I18N_RESOURCE_PATTERN);
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (null == filename || !filename.startsWith("application")) {
                continue;
            }
            String model = resolveModel(resource);
            if (StringUtil.isEmpty(model)) {
                continue;
            }
            String language = resolveLanguage(filename);
            if (StringUtil.isEmpty(language)) {
                continue;
            }
            bundles.computeIfAbsent(model, k -> new LinkedHashMap<>())
                    .put(language, loadProperties(resource));
        }
        return bundles;
    }

    /**
     * 从资源文件名解析语言码：application.properties → 默认语言（zh_CN）；
     * application_${code}.properties → ${code}；其余文件名返回 null（跳过）。
     */
    String resolveLanguage(String filename) {
        if (BASE_RESOURCE_NAME.equals(filename)) {
            return DEFAULT_LANGUAGE_CODE;
        }
        if (filename.startsWith("application_") && filename.endsWith(".properties")) {
            return filename.substring("application_".length(), filename.length() - ".properties".length());
        }
        return null;
    }

    /**
     * 从资源 URL 解析 field_model（相对 i18n/ 的子目录层级名，如 org、process/template）。
     */
    private String resolveModel(Resource resource) {
        try {
            String path = resource.getURL().toString();
            int index = path.indexOf("/i18n/");
            if (index < 0) {
                return null;
            }
            String relative = path.substring(index + "/i18n/".length());
            int lastSlash = relative.lastIndexOf('/');
            if (lastSlash <= 0) {
                // i18n/ 根目录下的 application.properties 无模块归属，跳过
                return null;
            }
            return relative.substring(0, lastSlash);
        } catch (IOException e) {
            log.warn("failed to resolve i18n resource model: {}", resource, e);
            return null;
        }
    }

    /**
     * 以 UTF-8 读取 properties 资源（含中文时禁止默认 ISO-8859-1，风险 R1）。
     */
    public Map<String, String> loadProperties(Resource resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = resource.getInputStream();
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            result.put(name, properties.getProperty(name));
        }
        return result;
    }

    /**
     * 读取指定模块、语言、key 的资源文件当前值（翻译重置复用，规则与启动同步一致）：
     * application.properties 归默认语言，application_${code}.properties 归对应语言。
     *
     * @param fieldModel 模块名（相对 i18n/ 的子目录层级名）
     * @param language   语言码
     * @param key        资源 key
     * @return 资源值；该语言无对应资源文件或文件中无此 key 时返回 null
     */
    public String readResourceValue(String fieldModel, String language, String key) {
        try {
            Map<String, Map<String, Map<String, String>>> bundles = scanResourceBundles();
            Map<String, Map<String, String>> languageBundles = bundles.get(fieldModel);
            if (null == languageBundles) {
                return null;
            }
            Map<String, String> keyValues = languageBundles.get(language);
            if (null == keyValues) {
                return null;
            }
            return keyValues.get(key);
        } catch (IOException e) {
            log.error("failed to read i18n resource value, model={}, language={}, key={}",
                    fieldModel, language, key, e);
            throw new IllegalStateException("failed to scan i18n resources: " + I18N_RESOURCE_PATTERN, e);
        }
    }

    /* ==================== 同步核心 ==================== */

    /**
     * 同步单个模块（核心同步逻辑，package-private 供单元测试直接传入构造数据）：
     * key 登记 → 默认语言翻译 → 其他启用语言翻译。
     *
     * @param fieldModel 模块名（相对 i18n/ 的子目录层级名）
     * @param bundles    语言码 → (key → 资源值)；必须含默认语言条目（application.properties）
     * @return 本次处理（登记 + 新增 + 覆盖 + 清理）的记录条数
     */
    int syncModel(String fieldModel, Map<String, Map<String, String>> bundles) {
        return syncModel(fieldModel, bundles, new SyncCounter());
    }

    /**
     * 同步单个模块（带计数器复用版本，供 syncFromResources 聚合统计）。
     */
    private int syncModel(String fieldModel, Map<String, Map<String, String>> bundles, SyncCounter counter) {
        if (StringUtil.isEmpty(fieldModel) || null == bundles || bundles.isEmpty()) {
            return 0;
        }
        Map<String, String> base = bundles.get(DEFAULT_LANGUAGE_CODE);
        if (null == base) {
            // application.properties 缺失：无登记来源，跳过该模块（key 登记以默认资源为准）
            log.warn("i18n model {} has no base application.properties, skip", fieldModel);
            return 0;
        }
        List<String> enabledLanguages = enabledLanguageCodes();
        int handled = 0;
        for (Map.Entry<String, String> entry : base.entrySet()) {
            String key = entry.getKey();
            if (StringUtil.isEmpty(key)) {
                continue;
            }
            handled += registerKey(fieldModel, key, counter);
            // 默认语言翻译来源 = application.properties（资源登记与中文翻译一体）
            handled += syncTranslation(key, DEFAULT_LANGUAGE_CODE, entry.getValue(), counter);
            // 其他启用语言翻译来源 = application_${code}.properties（无文件或文件中无此 key 时传 null）
            for (String language : enabledLanguages) {
                if (DEFAULT_LANGUAGE_CODE.equals(language)) {
                    continue;
                }
                Map<String, String> langBundle = bundles.get(language);
                handled += syncTranslation(key, language, null == langBundle ? null : langBundle.get(key), counter);
            }
        }
        return handled;
    }
    /**
     * 登记资源 key（D5：key 仅随资源文件扫描产生）。
     * <p>key 全局唯一：跨模块重复登记时跳过并告警（保留首个登记，避免翻译归属错乱）。</p>
     *
     * @return 本次实际写入（新增）条数
     */
    private int registerKey(String fieldModel, String key, SyncCounter counter) {
        SysI18nKey exist = keyDao.selectByKey(key);
        if (null != exist) {
            if (!fieldModel.equals(exist.getFieldModel())) {
                log.warn("i18n key {} already registered under model {}, skip duplicate registration from {}",
                        key, exist.getFieldModel(), fieldModel);
            }
            return 0;
        }
        SysI18nKey model = new SysI18nKey();
        model.initFieldId();
        model.setFieldKey(key);
        model.setFieldModel(fieldModel);
        keyDao.insert(model);
        counter.keyCount++;
        return 1;
    }

    /**
     * 单条翻译四分支同步。
     * <p>resourceValue 语义：null = 该语言无对应资源文件或文件中无此 key（分支四）；
     * 非空（含空串）= 资源值（缺失插入 / 人工未编辑覆盖 / 人工编辑跳过）。
     * 人工编辑判定（field_update_time 是否为空）在 SELECT 与条件 UPDATE 两处由 DB 保证：
     * 覆盖走 {@code updateIfNotEdited}（WHERE 含 field_update_time IS NULL），查改竞态下
     * 人工编辑过的记录不会被覆盖。</p>
     *
     * @param key           资源 key
     * @param language      语言码
     * @param resourceValue 资源值；null 表示该语言无对应资源值
     * @return 本次实际写入（新增/覆盖/删除）条数
     */
    private int syncTranslation(String key, String language, String resourceValue, SyncCounter counter) {
        SysI18nTranslation exist = translationDao.selectByKeyAndLanguage(key, language);
        if (null == exist) {
            if (null == resourceValue) {
                // DB 无记录且无资源值：无事可做
                return 0;
            }
            // 分支一：翻译缺失 → 新增（field_update_time 置空）
            SysI18nTranslation model = new SysI18nTranslation();
            model.initFieldId();
            model.setFieldKey(key);
            model.setFieldLanguage(language);
            model.setFieldMessage(resourceValue);
            translationDao.insert(model);
            counter.messageCount++;
            return 1;
        }
        if (null == resourceValue) {
            // 分支四：该语言无对应资源值 → 删除人工未编辑的记录（人工编辑过的保留）
            if (null == exist.getFieldUpdateTime()) {
                translationDao.deleteById(exist.getFieldId());
                counter.messageCount++;
                return 1;
            }
            return 0;
        }
        if (null == exist.getFieldUpdateTime()) {
            // 分支二：人工未编辑 → 资源值覆盖（条件 UPDATE 兜住 field_update_time IS NULL，保持为空）
            SysI18nTranslation update = new SysI18nTranslation();
            update.setFieldKey(key);
            update.setFieldLanguage(language);
            update.setFieldMessage(resourceValue);
            translationDao.updateIfNotEdited(update);
            counter.messageCount++;
            return 1;
        }
        // 分支三：人工编辑过（field_update_time 非空）→ 跳过，保留人工成果
        return 0;
    }

    /**
     * 当前启用语言码列表（按 field_order 升序；同步只写入启用语言的翻译）。
     */
    private List<String> enabledLanguageCodes() {
        List<String> codes = new ArrayList<>();
        for (SysI18nLanguage language : languageDao.selectAll()) {
            if (Boolean.TRUE.equals(language.getFieldEnable())
                    && StringUtil.isNotEmpty(language.getFieldCode())) {
                codes.add(language.getFieldCode());
            }
        }
        return codes;
    }

    /**
     * 同步计数器（登记 key 数 + 翻译写入条数，跨模块聚合）。
     */
    private static class SyncCounter {
        private int keyCount;
        private int messageCount;
    }
}
