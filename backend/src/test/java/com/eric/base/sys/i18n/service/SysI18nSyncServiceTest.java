package com.eric.base.sys.i18n.service;

import com.eric.base.sys.i18n.dao.SysI18nKeyDao;
import com.eric.base.sys.i18n.dao.SysI18nLanguageDao;
import com.eric.base.sys.i18n.dao.SysI18nTranslationDao;
import com.eric.base.sys.i18n.model.SysI18nKey;
import com.eric.base.sys.i18n.model.SysI18nLanguage;
import com.eric.base.sys.i18n.model.SysI18nTranslation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多语言资源同步服务测试
 *
 * @author Eric
 * @date 2026/11/5
 * @description 覆盖启动同步核心规则（照 {@code SysDictServiceTest} 构造数据先例）：
 *              <ul>
 *              <li>覆盖规则四分支：翻译缺失新增、field_update_time 为空资源值覆盖、
 *                  非空（人工编辑）跳过、该语言无资源值时删除人工未编辑记录；</li>
 *              <li>初始语言保证：空表插 zh_CN（默认、order 1）+ en_US（order 2），
 *                  已初始化后不重复处理（幂等）；</li>
 *              <li>key 登记：缺失登记、已存在跳过、跨模块重复拦截（key 全局唯一）；</li>
 *              <li>properties UTF-8 读取（中文不乱码，风险 R1）；
 *                  真实 classpath 资源烟雾验证（org 模块 3 个 key）；</li>
 *              <li>同步完成后按启用语言刷新缓存。</li>
 *              </ul>
 */
class SysI18nSyncServiceTest {

    private SysI18nLanguageDao languageDao;
    private SysI18nKeyDao keyDao;
    private SysI18nTranslationDao translationDao;
    private SysI18nCacheService cacheService;
    private SysI18nSyncService service;

    @BeforeEach
    void setUp() {
        languageDao = mock(SysI18nLanguageDao.class);
        keyDao = mock(SysI18nKeyDao.class);
        translationDao = mock(SysI18nTranslationDao.class);
        cacheService = mock(SysI18nCacheService.class);
        service = new SysI18nSyncService(languageDao, keyDao, translationDao, cacheService);
    }

    /** 构造已启用语言行 */
    private SysI18nLanguage language(String code, boolean enable, int order) {
        SysI18nLanguage model = new SysI18nLanguage();
        model.setFieldCode(code);
        model.setFieldEnable(enable);
        model.setFieldOrder(order);
        return model;
    }

    /** 构造 DB 已存在的翻译记录 */
    private SysI18nTranslation translation(String key, String language, String message, Date updateTime) {
        SysI18nTranslation model = new SysI18nTranslation();
        model.setFieldId("t" + "0".repeat(31));
        model.setFieldKey(key);
        model.setFieldLanguage(language);
        model.setFieldMessage(message);
        model.setFieldUpdateTime(updateTime);
        return model;
    }

    /** 默认两语言启用（zh_CN + en_US） */
    private void enableBothLanguages() {
        when(languageDao.selectAll()).thenReturn(List.of(
                language(SysI18nSyncService.DEFAULT_LANGUAGE_CODE, true, 1),
                language(SysI18nSyncService.INIT_LANGUAGE_CODE_EN, true, 2)));
    }

    /** 模拟「key 不存在、翻译不存在、写入成功」的空库 */
    private void mockEmptyDatabase() {
        when(keyDao.selectByKey(anyString())).thenReturn(null);
        when(keyDao.insert(any(SysI18nKey.class))).thenReturn(1);
        when(translationDao.selectByKeyAndLanguage(anyString(), anyString())).thenReturn(null);
        when(translationDao.insert(any(SysI18nTranslation.class))).thenReturn(1);
    }

    /* ==================== 覆盖规则四分支 ==================== */

    @Test
    void insertsMissingTranslationWithNullUpdateTime() {
        // 分支一：翻译缺失 → 新增，field_update_time 置空
        enableBothLanguages();
        mockEmptyDatabase();
        when(cacheService.cacheTranslations(any())).thenReturn(2);

        Map<String, Map<String, String>> bundles = new LinkedHashMap<>();
        bundles.put("zh_CN", Map.of("org.user.save", "保存用户"));
        bundles.put("en_US", Map.of("org.user.save", "Save User"));

        int handled = service.syncModel("org", bundles);

        assertEquals(3, handled); // key 登记 1 + zh_CN 翻译 1 + en_US 翻译 1
        verify(translationDao).insert(argThat(model ->
                "org.user.save".equals(model.getFieldKey())
                        && "zh_CN".equals(model.getFieldLanguage())
                        && "保存用户".equals(model.getFieldMessage())
                        && null == model.getFieldUpdateTime()));
        verify(translationDao).insert(argThat(model ->
                "en_US".equals(model.getFieldLanguage())
                        && "Save User".equals(model.getFieldMessage())
                        && null == model.getFieldUpdateTime()));
        // 缓存刷新在 syncFromResources 入口收尾（见 syncsRealClasspathOrgBundleEndToEnd），
        // syncModel 核心方法不负责缓存
    }

    @Test
    void overwritesTranslationWhenNotEdited() {
        // 分支二：field_update_time 为空（资源导入）→ 资源值覆盖，走条件更新语句
        enableBothLanguages();
        when(keyDao.selectByKey("org.user.save")).thenReturn(null);
        when(keyDao.insert(any(SysI18nKey.class))).thenReturn(1);
        when(translationDao.selectByKeyAndLanguage("org.user.save", "zh_CN"))
                .thenReturn(translation("org.user.save", "zh_CN", "旧文案", null));
        when(translationDao.updateIfNotEdited(any(SysI18nTranslation.class))).thenReturn(1);

        Map<String, Map<String, String>> bundles = new LinkedHashMap<>();
        bundles.put("zh_CN", Map.of("org.user.save", "新文案"));

        int handled = service.syncModel("org", bundles);

        assertEquals(2, handled); // key 登记 1 + 覆盖 1
        verify(translationDao).updateIfNotEdited(argThat(model ->
                "org.user.save".equals(model.getFieldKey())
                        && "zh_CN".equals(model.getFieldLanguage())
                        && "新文案".equals(model.getFieldMessage())));
        verify(translationDao, never()).update(any(SysI18nTranslation.class));
        verify(translationDao, never()).insert(any(SysI18nTranslation.class));
    }

    @Test
    void skipsManuallyEditedTranslation() {
        // 分支三：field_update_time 非空（人工编辑过）→ 跳过，任何写操作都不触发
        enableBothLanguages();
        when(keyDao.selectByKey("org.user.save")).thenReturn(null);
        when(keyDao.insert(any(SysI18nKey.class))).thenReturn(1);
        when(translationDao.selectByKeyAndLanguage("org.user.save", "zh_CN"))
                .thenReturn(translation("org.user.save", "zh_CN", "人工文案", new Date()));

        Map<String, Map<String, String>> bundles = new LinkedHashMap<>();
        bundles.put("zh_CN", Map.of("org.user.save", "资源新值"));

        int handled = service.syncModel("org", bundles);

        assertEquals(1, handled); // 仅 key 登记
        verify(translationDao, never()).insert(any(SysI18nTranslation.class));
        verify(translationDao, never()).update(any(SysI18nTranslation.class));
        verify(translationDao, never()).updateIfNotEdited(any(SysI18nTranslation.class));
        verify(translationDao, never()).deleteById(anyString());
    }

    @Test
    void deletesTranslationWhenResourceMissingAndNotEdited() {
        // 分支四：该语言无资源值 → 删除人工未编辑的记录
        enableBothLanguages();
        when(keyDao.selectByKey("org.user.save")).thenReturn(null);
        when(keyDao.insert(any(SysI18nKey.class))).thenReturn(1);
        when(translationDao.selectByKeyAndLanguage("org.user.save", "en_US"))
                .thenReturn(translation("org.user.save", "en_US", "旧英文", null));

        Map<String, Map<String, String>> bundles = new LinkedHashMap<>();
        bundles.put("zh_CN", Map.of("org.user.save", "保存用户"));
        // en_US 语言包缺失（无 application_en_US.properties）

        int handled = service.syncModel("org", bundles);

        assertEquals(3, handled); // key 登记 1 + zh_CN 新增 1 + en_US 删除 1
        verify(translationDao).deleteById("t" + "0".repeat(31));
    }

    @Test
    void keepsManuallyEditedTranslationWhenResourceMissing() {
        // 分支四的例外：人工编辑过（field_update_time 非空）且资源缺失 → 保留人工成果
        enableBothLanguages();
        when(keyDao.selectByKey("org.user.save")).thenReturn(null);
        when(keyDao.insert(any(SysI18nKey.class))).thenReturn(1);
        when(translationDao.selectByKeyAndLanguage("org.user.save", "en_US"))
                .thenReturn(translation("org.user.save", "en_US", "人工英文", new Date()));

        Map<String, Map<String, String>> bundles = new LinkedHashMap<>();
        bundles.put("zh_CN", Map.of("org.user.save", "保存用户"));

        int handled = service.syncModel("org", bundles);

        assertEquals(2, handled); // key 登记 1 + zh_CN 新增 1；en_US 保留不计数
        verify(translationDao, never()).deleteById(anyString());
    }

    /* ==================== key 登记 ==================== */

    @Test
    void registersMissingKeyAndSkipsExisting() {
        // key 缺失登记 + 已存在跳过；跨模块重复（同 key 已归属其他模块）拦截并告警
        enableBothLanguages();
        mockEmptyDatabase();
        SysI18nKey otherModel = new SysI18nKey();
        otherModel.setFieldKey("shared.key");
        otherModel.setFieldModel("other");
        when(keyDao.selectByKey("shared.key")).thenReturn(otherModel);

        Map<String, Map<String, String>> bundles = new LinkedHashMap<>();
        bundles.put("zh_CN", new LinkedHashMap<>(Map.of("org.user.save", "保存用户", "shared.key", "共享")));

        service.syncModel("org", bundles);

        // org.user.save 登记为 org；shared.key 已归属 other → 不重复登记
        verify(keyDao).insert(argThat(model ->
                "org".equals(model.getFieldModel()) && "org.user.save".equals(model.getFieldKey())));
        verify(keyDao, never()).insert(argThat(model -> "shared.key".equals(model.getFieldKey())));
    }

    /* ==================== 初始语言保证（幂等） ==================== */

    @Test
    void ensuresInitLanguagesOnlyWhenEmpty() {
        // 空表：插入 zh_CN（默认、order 1）+ en_US（order 2）
        when(languageDao.countAll()).thenReturn(0L);
        enableBothLanguages();
        mockEmptyDatabase();
        when(cacheService.cacheTranslations(any())).thenReturn(0);

        service.syncFromResources();

        InOrder inOrder = inOrder(languageDao);
        inOrder.verify(languageDao).countAll();
        verify(languageDao).insert(argThat(lang ->
                SysI18nSyncService.DEFAULT_LANGUAGE_CODE.equals(lang.getFieldCode())
                        && "中文".equals(lang.getFieldName())
                        && Boolean.TRUE.equals(lang.getFieldDefault())
                        && Integer.valueOf(1).equals(lang.getFieldOrder())));
        verify(languageDao).insert(argThat(lang ->
                SysI18nSyncService.INIT_LANGUAGE_CODE_EN.equals(lang.getFieldCode())
                        && "English".equals(lang.getFieldName())
                        && Boolean.TRUE.equals(lang.getFieldEnable())
                        && Integer.valueOf(2).equals(lang.getFieldOrder())));
    }

    @Test
    void skipsInitLanguagesWhenAlreadyInitialized() {
        // 已初始化（管理员维护优先）：不再插入语言行
        when(languageDao.countAll()).thenReturn(2L);
        enableBothLanguages();
        mockEmptyDatabase();
        when(cacheService.cacheTranslations(any())).thenReturn(0);

        service.syncFromResources();

        verify(languageDao, never()).insert(any(SysI18nLanguage.class));
    }

    /* ==================== 重复启动幂等 ==================== */

    @Test
    void secondSyncIsIdempotent() {
        // 重复启动：key 已登记、翻译已存在且 field_update_time 为空 → 覆盖（值相同等价幂等），无新增
        enableBothLanguages();
        SysI18nKey existKey = new SysI18nKey();
        existKey.setFieldKey("org.user.save");
        existKey.setFieldModel("org");
        when(keyDao.selectByKey("org.user.save")).thenReturn(existKey);
        when(translationDao.selectByKeyAndLanguage("org.user.save", "zh_CN"))
                .thenReturn(translation("org.user.save", "zh_CN", "保存用户", null));
        when(translationDao.selectByKeyAndLanguage("org.user.save", "en_US"))
                .thenReturn(translation("org.user.save", "en_US", "Save User", null));
        when(translationDao.updateIfNotEdited(any(SysI18nTranslation.class))).thenReturn(1);

        Map<String, Map<String, String>> bundles = new LinkedHashMap<>();
        bundles.put("zh_CN", Map.of("org.user.save", "保存用户"));
        bundles.put("en_US", Map.of("org.user.save", "Save User"));

        int handled = service.syncModel("org", bundles);

        // key 不重复登记；翻译仅条件覆盖（值相同），无 insert 无 delete
        verify(keyDao, never()).insert(any(SysI18nKey.class));
        verify(translationDao, never()).insert(any(SysI18nTranslation.class));
        verify(translationDao, never()).deleteById(anyString());
        assertEquals(2, handled); // 两条覆盖计数
    }

    /* ==================== properties 读取（UTF-8，风险 R1） ==================== */

    @Test
    void loadsPropertiesAsUtf8WithoutMojibake() throws IOException {
        // 含中文的 properties 以 UTF-8 读取（禁默认 ISO-8859-1）：写入临时文件（UTF-8）再读回
        File temp = File.createTempFile("i18n_utf8_", ".properties");
        temp.deleteOnExit();
        Files.writeString(temp.toPath(), "org.user.save=保存用户\norg.element.parent=上级组织\n",
                StandardCharsets.UTF_8);

        Map<String, String> data = service.loadProperties(new FileSystemResource(temp));

        assertEquals("保存用户", data.get("org.user.save"));
        assertEquals("上级组织", data.get("org.element.parent"));
    }

    /* ==================== 真实 classpath 资源烟雾验证 ==================== */

    @Test
    void scansRealClasspathOrgBundle() throws IOException {
        // 烟雾：真实 classpath 上 org 模块资源（zh_CN + en_US）被扫描并解析 field_model=org
        Map<String, Map<String, Map<String, String>>> bundles = service.scanResourceBundles();

        assertTrue(bundles.containsKey("org"));
        Map<String, Map<String, String>> orgBundle = bundles.get("org");
        assertTrue(orgBundle.containsKey("zh_CN"));
        assertTrue(orgBundle.containsKey("en_US"));
        assertEquals("保存用户", orgBundle.get("zh_CN").get("org.user.save"));
        assertEquals("Save User", orgBundle.get("en_US").get("org.user.save"));
        // 语言码解析：application.properties → 默认语言；application_xx.properties → xx
        assertEquals("zh_CN", service.resolveLanguage("application.properties"));
        assertEquals("en_US", service.resolveLanguage("application_en_US.properties"));
        assertNull(service.resolveLanguage("messages.properties"));
    }

    @Test
    void syncsRealClasspathOrgBundleEndToEnd() {
        // 端到端（mock Dao）：以真实扫描结果跑同步，验证 org 模块 3 个 key + 两语言翻译。
        // i18n/frontend 资源模块同批被扫描（A1 前端文案资源集），translation 断言按 org. 前缀限定。
        enableBothLanguages();
        mockEmptyDatabase();
        when(cacheService.cacheTranslations(any())).thenReturn(6);

        service.syncFromResources();

        // zh_CN 与 en_US 各 3 条翻译插入，key 登记在 org 模块下
        verify(translationDao, org.mockito.Mockito.times(3)).insert(argThat(model ->
                SysI18nSyncService.DEFAULT_LANGUAGE_CODE.equals(model.getFieldLanguage())
                        && model.getFieldKey().startsWith("org.")));
        verify(translationDao, org.mockito.Mockito.times(3)).insert(argThat(model ->
                SysI18nSyncService.INIT_LANGUAGE_CODE_EN.equals(model.getFieldLanguage())
                        && model.getFieldKey().startsWith("org.")));
        verify(keyDao, org.mockito.Mockito.times(3)).insert(argThat(model ->
                "org".equals(model.getFieldModel())));
        verify(cacheService).cacheTranslations(eq(List.of("zh_CN", "en_US")));
    }

    /* ==================== 边界 ==================== */

    @Test
    void skipsModelWithoutBaseProperties() {
        // 缺 application.properties（无默认语言条目）：整个模块跳过，key 与翻译都不写
        enableBothLanguages();

        Map<String, Map<String, String>> bundles = new LinkedHashMap<>();
        bundles.put("en_US", Map.of("org.user.save", "Save User"));

        int handled = service.syncModel("org", bundles);

        assertEquals(0, handled);
        verify(keyDao, never()).insert(any(SysI18nKey.class));
        verify(translationDao, never()).insert(any(SysI18nTranslation.class));
    }

    @Test
    void syncsOnlyEnabledLanguages() {
        // 停用的语言不写入翻译（管理员停用 en_US 后，同步不再维护其翻译）
        when(languageDao.selectAll()).thenReturn(List.of(
                language(SysI18nSyncService.DEFAULT_LANGUAGE_CODE, true, 1),
                language(SysI18nSyncService.INIT_LANGUAGE_CODE_EN, false, 2)));
        mockEmptyDatabase();
        when(cacheService.cacheTranslations(any())).thenReturn(1);

        Map<String, Map<String, String>> bundles = new LinkedHashMap<>();
        bundles.put("zh_CN", Map.of("org.user.save", "保存用户"));
        bundles.put("en_US", Map.of("org.user.save", "Save User"));

        int handled = service.syncModel("org", bundles);

        // 仅 zh_CN 翻译 + key 登记
        assertEquals(2, handled);
        verify(translationDao, never()).insert(argThat(model ->
                "en_US".equals(model.getFieldLanguage())));
    }
}
