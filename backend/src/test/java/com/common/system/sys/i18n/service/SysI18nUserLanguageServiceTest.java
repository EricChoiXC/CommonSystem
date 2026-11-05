package com.common.system.sys.i18n.service;

import com.common.system.sys.i18n.dao.SysI18nLanguageDao;
import com.common.system.sys.i18n.dao.SysI18nUserLanguageDao;
import com.common.system.sys.i18n.model.SysI18nLanguage;
import com.common.system.sys.i18n.model.SysI18nUserLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多语言-用户语言服务测试
 *
 * @author Eric
 * @date 2026/11/5
 * @description 覆盖登录语言登记契约（D3）与用户语言 update 接口共用的 upsert 规则：
 *              <ul>
 *              <li>upsert 三分支：无登记插入、语言变化更新、语言未变化幂等跳过；</li>
 *              <li>无效/停用/空语言码按 A4 忽略并回退默认语言（不报错）；</li>
 *              <li>默认语言也不存在（极端场景）时不写库。</li>
 *              </ul>
 */
class SysI18nUserLanguageServiceTest {

    /** 32 位测试语言 ID（BaseModel.setFieldId 会把非 32 位 ID 自动重新生成，造数须先补足 32 位） */
    private static final String LANG_ZH_ID = padId("lang-zh");
    private static final String LANG_EN_ID = padId("lang-en");

    private static final String CODE_ZH = "zh_CN";
    private static final String CODE_EN = "en_US";
    private static final String CODE_INVALID = "xx_XX";

    private SysI18nUserLanguageDao userLanguageDao;
    private SysI18nLanguageDao languageDao;
    private SysI18nUserLanguageService service;

    @BeforeEach
    void setUp() {
        userLanguageDao = mock(SysI18nUserLanguageDao.class);
        languageDao = mock(SysI18nLanguageDao.class);
        service = new SysI18nUserLanguageService(userLanguageDao, languageDao);
    }

    /** 右侧补 '0' 补足 32 位（与内置管理员 '0'.repeat(32) 同款补位方式） */
    private static String padId(String raw) {
        return String.format("%-32s", raw).replace(' ', '0');
    }

    /** 构造语言行（id 须为已补足的 32 位常量） */
    private SysI18nLanguage language(String id, String code, boolean enable) {
        SysI18nLanguage model = new SysI18nLanguage();
        model.setFieldId(id);
        model.setFieldCode(code);
        model.setFieldEnable(enable);
        return model;
    }

    /** 打桩语言表：code → 语言行（仅列出的 code 命中） */
    private void stubLanguage(String code, SysI18nLanguage language) {
        when(languageDao.selectByCode(code)).thenReturn(language);
    }

    /** 构造既有登记行 */
    private SysI18nUserLanguage existRow(String userId, String languageId) {
        SysI18nUserLanguage model = new SysI18nUserLanguage();
        model.setFieldUserId(userId);
        model.setFieldLanguageId(languageId);
        return model;
    }

    @Test
    void upsertInsertsWhenNoRecordExists() {
        stubLanguage(CODE_ZH, language(LANG_ZH_ID, CODE_ZH, true));
        when(userLanguageDao.selectByUserId("user-1")).thenReturn(null);

        service.upsertLanguage("user-1", CODE_ZH);

        verify(userLanguageDao).insert(argThat(model ->
                "user-1".equals(model.getFieldUserId()) && LANG_ZH_ID.equals(model.getFieldLanguageId())));
        verify(userLanguageDao, never()).updateByUserId(any());
    }

    @Test
    void upsertUpdatesWhenLanguageChanged() {
        stubLanguage(CODE_EN, language(LANG_EN_ID, CODE_EN, true));
        when(userLanguageDao.selectByUserId("user-1")).thenReturn(existRow("user-1", LANG_ZH_ID));

        service.upsertLanguage("user-1", CODE_EN);

        verify(userLanguageDao).updateByUserId(argThat(model ->
                "user-1".equals(model.getFieldUserId()) && LANG_EN_ID.equals(model.getFieldLanguageId())));
        verify(userLanguageDao, never()).insert(any());
    }

    @Test
    void upsertIsIdempotentWhenLanguageUnchanged() {
        stubLanguage(CODE_ZH, language(LANG_ZH_ID, CODE_ZH, true));
        when(userLanguageDao.selectByUserId("user-1")).thenReturn(existRow("user-1", LANG_ZH_ID));

        service.upsertLanguage("user-1", CODE_ZH);

        verify(userLanguageDao, never()).insert(any());
        verify(userLanguageDao, never()).updateByUserId(any());
    }

    @Test
    void invalidLanguageFallsBackToDefaultLanguage() {
        // A4：无效语言码忽略并回退默认语言，不报错
        stubLanguage(CODE_INVALID, null);
        stubLanguage(CODE_ZH, language(LANG_ZH_ID, CODE_ZH, true));
        when(userLanguageDao.selectByUserId("user-1")).thenReturn(null);

        service.upsertLanguage("user-1", CODE_INVALID);

        verify(userLanguageDao).insert(argThat(model -> LANG_ZH_ID.equals(model.getFieldLanguageId())));
    }

    @Test
    void emptyLanguageFallsBackToDefaultLanguage() {
        stubLanguage(CODE_ZH, language(LANG_ZH_ID, CODE_ZH, true));
        when(userLanguageDao.selectByUserId("user-1")).thenReturn(null);

        service.upsertLanguage("user-1", "");

        verify(userLanguageDao).insert(argThat(model -> LANG_ZH_ID.equals(model.getFieldLanguageId())));
    }

    @Test
    void disabledRequestedLanguageFallsBackToDefaultLanguage() {
        // 停用语言不能作为用户语言：回退默认语言
        stubLanguage(CODE_EN, language(LANG_EN_ID, CODE_EN, false));
        stubLanguage(CODE_ZH, language(LANG_ZH_ID, CODE_ZH, true));
        when(userLanguageDao.selectByUserId("user-1")).thenReturn(null);

        service.upsertLanguage("user-1", CODE_EN);

        verify(userLanguageDao).insert(argThat(model -> LANG_ZH_ID.equals(model.getFieldLanguageId())));
    }

    @Test
    void skipsWriteWhenDefaultLanguageMissing() {
        // 极端场景：默认语言也不存在（语言表为空）→ 不写库
        stubLanguage(CODE_INVALID, null);
        stubLanguage(CODE_ZH, null);

        service.upsertLanguage("user-1", CODE_INVALID);

        verify(userLanguageDao, never()).insert(any());
        verify(userLanguageDao, never()).updateByUserId(any());
    }

    @Test
    void skipsWhenUserIdEmpty() {
        service.upsertLanguage("", CODE_ZH);

        verify(userLanguageDao, never()).selectByUserId(anyString());
        verify(userLanguageDao, never()).insert(any());
        verify(userLanguageDao, never()).updateByUserId(any());
    }

    @Test
    void fallbackUsesI18nServiceDefaultLanguageConstant() {
        // 契约：回退语言与 I18nService.DEFAULT_LANGUAGE（zh_CN）一致
        assertEquals(CODE_ZH, I18nService.DEFAULT_LANGUAGE);
        stubLanguage(I18nService.DEFAULT_LANGUAGE, language(LANG_ZH_ID, I18nService.DEFAULT_LANGUAGE, true));
        when(userLanguageDao.selectByUserId("user-2")).thenReturn(null);

        service.upsertLanguage("user-2", null);

        verify(userLanguageDao).insert(argThat(model -> LANG_ZH_ID.equals(model.getFieldLanguageId())));
        verify(languageDao, never()).selectByCode(eq(CODE_EN));
    }
}
