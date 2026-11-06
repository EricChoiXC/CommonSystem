package com.common.system.sys.i18n.service;

import com.common.system.sys.i18n.dao.SysI18nLanguageDao;
import com.common.system.sys.i18n.dao.SysI18nTranslationDao;
import com.common.system.sys.i18n.dao.SysI18nUserLanguageDao;
import com.common.system.sys.i18n.model.SysI18nLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多语言-语言维护服务测试
 *
 * @author Eric
 * @date 2026/11/5
 * @description 覆盖语言维护业务规则（任务清单 I18N3-BE-001 验收标准）：
 *              <ul>
 *              <li>save：fieldCode 唯一校验（重复参数错误）、非法语言码拦截、
 *                  field_default 不接受写入（新增一律非默认）、fieldOrder 缺省接末位；</li>
 *              <li>update：默认语言禁止停用（A2）、fieldCode 不可修改；</li>
 *              <li>delete：默认语言禁止删除（A2）、级联 D4（删翻译 + 登记改绑默认语言）；</li>
 *              <li>setDefault：互斥切换（先清旧默认再设新默认）、停用语言拦截、幂等；</li>
 *              <li>缓存联动：删除失效、停用失效、启用刷新。</li>
 *              </ul>
 */
class SysI18nLanguageServiceTest {

    /** 32 位测试 ID（BaseModel.setFieldId 会把非 32 位 ID 自动重新生成） */
    private static final String ID_ZH = padId("lang-zh");
    private static final String ID_EN = padId("lang-en");
    private static final String ID_FR = padId("lang-fr");

    private SysI18nLanguageDao languageDao;
    private SysI18nTranslationDao translationDao;
    private SysI18nUserLanguageDao userLanguageDao;
    private SysI18nCacheService cacheService;
    private SysI18nLanguageService service;

    @BeforeEach
    void setUp() {
        languageDao = mock(SysI18nLanguageDao.class);
        translationDao = mock(SysI18nTranslationDao.class);
        userLanguageDao = mock(SysI18nUserLanguageDao.class);
        cacheService = mock(SysI18nCacheService.class);
        service = new SysI18nLanguageService(languageDao, translationDao, userLanguageDao, cacheService);
    }

    private static String padId(String raw) {
        return String.format("%-32s", raw).replace(' ', '0');
    }

    /** 构造语言行（id 须为已补足的 32 位常量；enable/isDefault/order 可传 null 表示不设置） */
    private SysI18nLanguage language(String id, String code, String name, Boolean enable, Boolean isDefault, Integer order) {
        SysI18nLanguage model = new SysI18nLanguage();
        if (null != id) {
            model.setFieldId(id);
        }
        model.setFieldCode(code);
        model.setFieldName(name);
        model.setFieldEnable(enable);
        model.setFieldDefault(isDefault);
        model.setFieldOrder(order);
        return model;
    }

    /** 默认中文语言行 */
    private SysI18nLanguage zhDefault() {
        return language(ID_ZH, "zh_CN", "中文", true, true, 1);
    }

    @Test
    void saveRejectsDuplicateCode() {
        when(languageDao.selectByCode("en_US")).thenReturn(language(ID_EN, "en_US", "English", true, false, 2));

        SysI18nLanguage body = language(null, "en_US", "English", true, false, 0);
        assertThrows(IllegalArgumentException.class, () -> service.save(body));
        verify(languageDao, never()).insert(any());
    }

    @Test
    void saveRejectsInvalidCodeFormat() {
        // 语言码同时用作 /locales/{code}.json 路径段与资源文件后缀，禁止路径注入字符
        SysI18nLanguage body = language(null, "../etc", "X", true, false, 0);
        assertThrows(IllegalArgumentException.class, () -> service.save(body));
        verify(languageDao, never()).insert(any());
    }

    @Test
    void saveIgnoresRequestedDefaultAndAppendsOrder() {
        // field_default 不接受请求写入：请求 default=true 仍按非默认入库；order 缺省接末位
        when(languageDao.selectByCode("fr_FR")).thenReturn(null);
        when(languageDao.selectAll()).thenReturn(List.of(zhDefault(), language(ID_EN, "en_US", "English", true, false, 2)));

        SysI18nLanguage body = language(null, "fr_FR", "Français", null, true, null);
        service.save(body);

        verify(languageDao).insert(argThat(model ->
                !"zh_CN".equals(model.getFieldCode())
                        && "fr_FR".equals(model.getFieldCode())
                        && Boolean.FALSE.equals(model.getFieldDefault())
                        && Boolean.TRUE.equals(model.getFieldEnable())
                        && Integer.valueOf(3).equals(model.getFieldOrder())));
    }

    @Test
    void saveAdoptsRequestedFieldId() {
        // 前端经 /initID 生成 fieldId 随 body 提交，后端必须采纳（联调发现的回归：漏采纳导致 field_id 为空写库失败）
        when(languageDao.selectByCode("fr_FR")).thenReturn(null);

        SysI18nLanguage body = language(ID_FR, "fr_FR", "Français", null, null, null);
        service.save(body);

        verify(languageDao).insert(argThat(model ->
                ID_FR.equals(model.getFieldId()) && "fr_FR".equals(model.getFieldCode())));
    }

    @Test
    void saveGeneratesFieldIdWhenMissing() {
        // fieldId 缺省时由 BaseModel.setFieldId 兜底生成 32 位 ID（IDUtil.initID）
        when(languageDao.selectByCode("fr_FR")).thenReturn(null);

        SysI18nLanguage body = language(null, "fr_FR", "Français", null, null, null);
        service.save(body);

        verify(languageDao).insert(argThat(model ->
                null != model.getFieldId() && 32 == model.getFieldId().length()));
    }

    @Test
    void updateRejectsDisablingDefaultLanguage() {
        // A2：默认语言禁止停用
        SysI18nLanguage body = language(ID_ZH, null, null, false, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.update(body));
        verify(languageDao, never()).update(any());
    }

    @Test
    void updateDoesNotTouchCodeAndDefault() {
        // fieldCode 与 field_default 不可经 update 修改
        when(languageDao.selectById(ID_EN)).thenReturn(language(ID_EN, "en_US", "English", true, false, 2));

        SysI18nLanguage body = language(ID_EN, "changed", "English-2", true, true, 5);
        service.update(body);

        verify(languageDao).update(argThat(model ->
                ID_EN.equals(model.getFieldId())
                        && null == model.getFieldCode()
                        && null == model.getFieldDefault()
                        && "English-2".equals(model.getFieldName())));
    }

    @Test
    void updateEnableTrueRefreshesCache() {
        when(languageDao.selectById(ID_EN)).thenReturn(language(ID_EN, "en_US", "English", false, false, 2));

        SysI18nLanguage body = language(ID_EN, null, null, true, null, null);
        service.update(body);

        verify(cacheService).cacheTranslations(List.of("en_US"));
    }

    @Test
    void updateEnableFalseEvictsCache() {
        when(languageDao.selectById(ID_EN)).thenReturn(language(ID_EN, "en_US", "English", true, false, 2));

        SysI18nLanguage body = language(ID_EN, null, null, false, null, null);
        service.update(body);

        verify(cacheService).evictLanguage("en_US");
    }

    @Test
    void deleteRejectsDefaultLanguage() {
        // A2：默认语言禁止删除
        when(languageDao.selectById(ID_ZH)).thenReturn(zhDefault());
        assertThrows(IllegalArgumentException.class, () -> service.delete(ID_ZH));
        verify(languageDao, never()).deleteById(anyString());
        verify(translationDao, never()).deleteByLanguage(anyString());
    }

    @Test
    void deleteCascadesTranslationsAndRebindsRegistrations() {
        // D4：删翻译 + 登记改绑默认语言（不删行），同事务完成后失效该语言缓存
        when(languageDao.selectById(ID_EN)).thenReturn(language(ID_EN, "en_US", "English", true, false, 2));
        when(languageDao.selectDefault()).thenReturn(zhDefault());

        service.delete(ID_EN);

        verify(translationDao).deleteByLanguage("en_US");
        verify(userLanguageDao).rebindLanguage(ID_EN, ID_ZH);
        verify(languageDao).deleteById(ID_EN);
        verify(cacheService).evictLanguage("en_US");
    }

    @Test
    void deleteSkipsRebindWhenDefaultLanguageMissing() {
        // 极端场景：默认语言不存在时跳过改绑（不阻断删除）
        when(languageDao.selectById(ID_EN)).thenReturn(language(ID_EN, "en_US", "English", true, false, 2));
        when(languageDao.selectDefault()).thenReturn(null);

        service.delete(ID_EN);

        verify(translationDao).deleteByLanguage("en_US");
        verify(userLanguageDao, never()).rebindLanguage(anyString(), anyString());
        verify(languageDao).deleteById(ID_EN);
    }

    @Test
    void setDefaultSwitchesExclusively() {
        // 互斥切换：先清全部默认标记，再设新默认（同事务，全局恰一条默认语言）
        when(languageDao.selectById(ID_EN)).thenReturn(language(ID_EN, "en_US", "English", true, false, 2));

        SysI18nLanguage result = service.setDefault(ID_EN);

        assertEquals("en_US", result.getFieldCode());
        verify(languageDao).clearDefault();
        verify(languageDao).update(argThat(model -> ID_EN.equals(model.getFieldId())
                && Boolean.TRUE.equals(model.getFieldDefault())));
    }

    @Test
    void setDefaultIsIdempotentWhenAlreadyDefault() {
        when(languageDao.selectById(ID_ZH)).thenReturn(zhDefault());

        service.setDefault(ID_ZH);

        verify(languageDao, never()).clearDefault();
        verify(languageDao, never()).update(any());
    }

    @Test
    void setDefaultRejectsDisabledLanguage() {
        when(languageDao.selectById(ID_EN)).thenReturn(language(ID_EN, "en_US", "English", false, false, 2));

        assertThrows(IllegalArgumentException.class, () -> service.setDefault(ID_EN));
        verify(languageDao, never()).clearDefault();
    }

    @Test
    void listPassesEnableOnlyConditionForLoginDropdown() {
        when(languageDao.selectList(argThat(criteria ->
                        null != criteria && Boolean.TRUE.equals(criteria.getFieldEnable())
                                && null == criteria.getFieldName() && null == criteria.getFieldCode()),
                argThat(pages -> null == pages)))
                .thenReturn(List.of(zhDefault()));

        List<SysI18nLanguage> result = service.list(null, null, true);

        assertEquals(1, result.size());
    }
}
