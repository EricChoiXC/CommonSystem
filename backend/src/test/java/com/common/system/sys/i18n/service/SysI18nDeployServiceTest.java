package com.common.system.sys.i18n.service;

import com.common.system.sys.i18n.dao.SysI18nLanguageDao;
import com.common.system.sys.i18n.dao.SysI18nTranslationDao;
import com.common.system.sys.i18n.model.SysI18nLanguage;
import com.common.system.sys.i18n.model.SysI18nTranslation;
import com.common.system.sys.i18n.response.SysI18nDeployResultVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 多语言-部署与语言包服务测试
 *
 * @author Eric
 * @date 2026/11/5
 * @description 覆盖任务清单 I18N5-BE-001 验收标准：
 *              <ul>
 *              <li>deploy：复用启动同步入口 syncFromResources（同一实现，规则一致）；</li>
 *              <li>localePackage：仅输出启用语言的全量翻译（扁平 {key: message}）；
 *                  缓存优先，缓存为空回退 DB 直读（读路径无副作用）；</li>
 *              <li>未启用或未知语言码返回错误，不泄露空包；空语言码拒绝。</li>
 *              </ul>
 */
class SysI18nDeployServiceTest {

    private SysI18nSyncService syncService;
    private SysI18nLanguageDao languageDao;
    private SysI18nTranslationDao translationDao;
    private SysI18nCacheService cacheService;
    private SysI18nDeployService service;

    @BeforeEach
    void setUp() {
        syncService = mock(SysI18nSyncService.class);
        languageDao = mock(SysI18nLanguageDao.class);
        translationDao = mock(SysI18nTranslationDao.class);
        cacheService = mock(SysI18nCacheService.class);
        service = new SysI18nDeployService(syncService, languageDao, translationDao, cacheService);
    }

    private SysI18nLanguage language(String code, boolean enable) {
        SysI18nLanguage model = new SysI18nLanguage();
        model.setFieldId(String.format("%-32s", "lang-" + code).replace(' ', '0'));
        model.setFieldCode(code);
        model.setFieldName(code);
        model.setFieldEnable(enable);
        return model;
    }

    private SysI18nTranslation translation(String key, String message) {
        SysI18nTranslation model = new SysI18nTranslation();
        model.setFieldId(String.format("%-32s", "trans-" + key).replace(' ', '0'));
        model.setFieldKey(key);
        model.setFieldLanguage("zh_CN");
        model.setFieldMessage(message);
        return model;
    }

    @Test
    void deployReusesStartupSyncImplementation() {
        // 部署与启动共用同一同步实现（任务清单强顺序约束：两条入口行为一致）
        SysI18nDeployResultVo expected = new SysI18nDeployResultVo();
        expected.setSyncedModelCount(2);
        expected.setSyncedKeyCount(10);
        when(syncService.syncFromResources()).thenReturn(expected);

        SysI18nDeployResultVo result = service.deploy();

        assertSame(expected, result);
        assertEquals(2, result.getSyncedModelCount());
    }

    @Test
    void localePackageReturnsCachedMessagesForEnabledLanguage() {
        // 启用语言 + 缓存命中：直接返回缓存（不读 DB）
        when(languageDao.selectAll()).thenReturn(List.of(language("zh_CN", true)));
        Map<String, String> cached = Map.of("org.title", "标题", "org.user", "用户");
        when(cacheService.getMessages("zh_CN")).thenReturn(cached);

        Map<String, String> result = service.localePackage("zh_CN");

        assertEquals(2, result.size());
        assertEquals("标题", result.get("org.title"));
        verifyNoInteractions(translationDao);
    }

    @Test
    void localePackageFallsBackToDbWhenCacheEmpty() {
        // 缓存为空（启动同步未执行 / 缓存被清）→ 直读 DB；读路径不回写缓存
        when(languageDao.selectAll()).thenReturn(List.of(language("zh_CN", true)));
        when(cacheService.getMessages("zh_CN")).thenReturn(Map.of());
        when(translationDao.selectByLanguage("zh_CN")).thenReturn(List.of(
                translation("org.title", "标题"),
                translation("org.user", "用户")));

        Map<String, String> result = service.localePackage("zh_CN");

        assertEquals("标题", result.get("org.title"));
        assertEquals("用户", result.get("org.user"));
        verify(cacheService, never()).cacheTranslations(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void localePackageRejectsDisabledLanguage() {
        // 停用语言：拒绝，不泄露空包
        when(languageDao.selectAll()).thenReturn(List.of(
                language("zh_CN", true), language("de_DE", false)));

        assertThrows(IllegalArgumentException.class, () -> service.localePackage("de_DE"));
        verifyNoInteractions(cacheService, translationDao);
    }

    @Test
    void localePackageRejectsUnknownLanguage() {
        // 未知语言码（未登记）：拒绝
        when(languageDao.selectAll()).thenReturn(List.of(language("zh_CN", true)));

        assertThrows(IllegalArgumentException.class, () -> service.localePackage("xx_XX"));
    }

    @Test
    void localePackageRejectsBlankCode() {
        assertThrows(IllegalArgumentException.class, () -> service.localePackage(null));
        assertThrows(IllegalArgumentException.class, () -> service.localePackage(" "));
    }
}
