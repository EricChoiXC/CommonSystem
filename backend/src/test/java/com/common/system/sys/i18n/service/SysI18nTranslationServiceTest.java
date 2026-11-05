package com.common.system.sys.i18n.service;

import com.common.system.sys.i18n.dao.SysI18nKeyDao;
import com.common.system.sys.i18n.dao.SysI18nLanguageDao;
import com.common.system.sys.i18n.dao.SysI18nTranslationDao;
import com.common.system.sys.i18n.model.SysI18nKey;
import com.common.system.sys.i18n.model.SysI18nLanguage;
import com.common.system.sys.i18n.model.SysI18nTranslation;
import com.common.system.sys.i18n.request.SysI18nTranslationUpdateRequest;
import com.common.system.sys.i18n.response.SysI18nModelVo;
import com.common.system.sys.i18n.response.SysI18nTranslationMatrixVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多语言-翻译维护服务测试
 *
 * @author Eric
 * @date 2026/11/5
 * @description 覆盖翻译维护业务规则（任务清单 I18N4-BE-001 验收标准）：
 *              <ul>
 *              <li>model/list：MODEL_NAME 末段路径匹配（禁止 MODEL_KEY 等值匹配，风险 R4）、
 *                  多段 field_model 取末段、取不到回退 field_model；</li>
 *              <li>translation/list：行 = field_key 升序（Dao 保证）、列 = 启用语言按
 *                  field_order 升序、无翻译的格缺省；</li>
 *              <li>update 批量：按主键写 field_message 并置人工编辑标记、
 *                  刷新受影响语言缓存、空明细幂等、明细缺失/记录不存在参数错误；</li>
 *              <li>reset：有资源值清标记回写、无资源值删记录、key 未登记参数错误、刷新缓存。</li>
 *              </ul>
 */
class SysI18nTranslationServiceTest {

    private static final String KEY_ID_1 = padId("key-1");
    private static final String KEY_ID_2 = padId("key-2");
    private static final String TRANS_ID = padId("trans-1");

    private SysI18nKeyDao keyDao;
    private SysI18nTranslationDao translationDao;
    private SysI18nLanguageDao languageDao;
    private SysI18nCacheService cacheService;
    private SysI18nSyncService syncService;
    private SysI18nTranslationService service;

    @BeforeEach
    void setUp() {
        keyDao = mock(SysI18nKeyDao.class);
        translationDao = mock(SysI18nTranslationDao.class);
        languageDao = mock(SysI18nLanguageDao.class);
        cacheService = mock(SysI18nCacheService.class);
        syncService = mock(SysI18nSyncService.class);
        service = new SysI18nTranslationService(keyDao, translationDao, languageDao, cacheService, syncService);
    }

    private static String padId(String raw) {
        return String.format("%-32s", raw).replace(' ', '0');
    }

    private SysI18nKey key(String id, String fieldKey, String fieldModel) {
        SysI18nKey model = new SysI18nKey();
        model.setFieldId(id);
        model.setFieldKey(fieldKey);
        model.setFieldModel(fieldModel);
        return model;
    }

    private SysI18nLanguage language(String code, String name, boolean enable, int order) {
        SysI18nLanguage model = new SysI18nLanguage();
        model.setFieldId(padId("lang-" + code));
        model.setFieldCode(code);
        model.setFieldName(name);
        model.setFieldEnable(enable);
        model.setFieldOrder(order);
        return model;
    }

    private SysI18nTranslation translation(String key, String code, String message) {
        SysI18nTranslation model = new SysI18nTranslation();
        model.setFieldId(TRANS_ID);
        model.setFieldKey(key);
        model.setFieldLanguage(code);
        model.setFieldMessage(message);
        return model;
    }

    /* ==================== model/list ==================== */

    @Test
    void listModelsMatchesModelNameByLastSegment() {
        // 末段路径匹配：field_model=org 与 MODEL_KEY=SYS-ORG 不同源，禁止等值匹配；
        // 仅按 application.yml 所在目录末段（org）取 MODEL_NAME
        when(keyDao.selectDistinctModels()).thenReturn(List.of("org"));
        Map<String, String> nameBySegment = new LinkedHashMap<>();
        nameBySegment.put("org", "组织架构和权限模块");

        List<SysI18nModelVo> result = service.listModels(nameBySegment);

        assertEquals(1, result.size());
        assertEquals("org", result.get(0).getFieldModel());
        assertEquals("组织架构和权限模块", result.get(0).getFieldModelName());
    }

    @Test
    void listModelsFallsBackToFieldModelWhenNoMatch() {
        // 取不到 MODEL_NAME 回退 field_model 原值；多段 field_model 取末段匹配
        when(keyDao.selectDistinctModels()).thenReturn(List.of("process/template", "unknown"));
        Map<String, String> nameBySegment = new LinkedHashMap<>();
        nameBySegment.put("template", "流程模板");

        List<SysI18nModelVo> result = service.listModels(nameBySegment);

        assertEquals(2, result.size());
        assertEquals("流程模板", result.get(0).getFieldModelName());
        assertEquals("unknown", result.get(1).getFieldModelName());
    }

    @Test
    void resolveModelNameRejectsModelKeyEqualityMatch() {
        // 反例（R4）：MODEL_KEY=SYS-ORG 不参与等值匹配——映射里没有 "SYS-ORG" 这个末段，
        // 即便 field_model 传 "SYS-ORG" 也只能按末段取值；空映射一律回退原值
        Map<String, String> nameBySegment = new LinkedHashMap<>();
        nameBySegment.put("SYS-ORG", "不应按 MODEL_KEY 命中");

        assertEquals("回退值", service.resolveModelName("回退值", Map.of()));
        // null 模块名安全透传（listModels 实际不会传 null，selectDistinctModels 均为非空值）
        assertNull(service.resolveModelName(null, nameBySegment));
        assertEquals("多语言模块", service.resolveModelName("i18n", Map.of("i18n", "多语言模块")));
    }

    /* ==================== translation/list ==================== */

    @Test
    void translationListBuildsMatrixWithEnabledLanguageColumns() {
        when(keyDao.selectByModel("org")).thenReturn(List.of(
                key(KEY_ID_1, "org.title", "org"),
                key(KEY_ID_2, "org.user", "org")));
        // selectAll 已按 field_order 升序返回；de_DE 停用不进列，fr_FR 启用
        when(languageDao.selectAll()).thenReturn(List.of(
                language("zh_CN", "中文", true, 1),
                language("fr_FR", "Français", true, 2),
                language("de_DE", "Deutsch", false, 3)));
        when(translationDao.selectByModelAndLanguage("org", "zh_CN")).thenReturn(List.of(
                translation("org.title", "zh_CN", "标题"),
                translation("org.user", "zh_CN", "用户")));
        when(translationDao.selectByModelAndLanguage("org", "fr_FR")).thenReturn(List.of(
                translation("org.title", "fr_FR", "Titre")));

        SysI18nTranslationMatrixVo vo = service.translationList("org");

        assertEquals("org", vo.getFieldModel());
        assertEquals(2, vo.getKeys().size());
        // 语言列 = 启用语言按 field_order 升序（停用 de_DE 不在列）
        assertEquals(2, vo.getLanguages().size());
        assertEquals("zh_CN", vo.getLanguages().get(0).getFieldCode());
        assertEquals("fr_FR", vo.getLanguages().get(1).getFieldCode());
        // 矩阵：无翻译的格缺省（org.user × fr_FR）
        assertEquals("标题", vo.getMatrix().get("org.title").get("zh_CN"));
        assertEquals("Titre", vo.getMatrix().get("org.title").get("fr_FR"));
        assertEquals("用户", vo.getMatrix().get("org.user").get("zh_CN"));
        assertNull(vo.getMatrix().get("org.user").get("fr_FR"));
    }

    @Test
    void translationListIgnoresTranslationsOfUnknownKeys() {
        // key 未登记的孤儿翻译不进矩阵（矩阵行以 sys_i18n_key 为准，D5）
        when(keyDao.selectByModel("org")).thenReturn(List.of(key(KEY_ID_1, "org.title", "org")));
        when(languageDao.selectAll()).thenReturn(List.of(language("zh_CN", "中文", true, 1)));
        when(translationDao.selectByModelAndLanguage("org", "zh_CN")).thenReturn(List.of(
                translation("orphan.key", "zh_CN", "孤儿")));

        SysI18nTranslationMatrixVo vo = service.translationList("org");

        assertEquals(1, vo.getMatrix().size());
        assertNull(vo.getMatrix().get("orphan.key"));
    }

    @Test
    void translationListRejectsEmptyModel() {
        assertThrows(IllegalArgumentException.class, () -> service.translationList(null));
        assertThrows(IllegalArgumentException.class, () -> service.translationList(" "));
    }

    /* ==================== update 批量 ==================== */

    @Test
    void updateBatchWritesEditedFlagAndRefreshesCache() {
        SysI18nTranslation exist = translation("org.title", "zh_CN", "旧标题");
        when(translationDao.selectById(TRANS_ID)).thenReturn(exist);

        SysI18nTranslationUpdateRequest request = new SysI18nTranslationUpdateRequest();
        SysI18nTranslationUpdateRequest.Detail detail = new SysI18nTranslationUpdateRequest.Detail();
        detail.setFieldId(TRANS_ID);
        detail.setFieldMessage("新标题");
        request.setDetails(List.of(detail));

        int updated = service.updateBatch(request);

        assertEquals(1, updated);
        // 按主键写 field_message 并置人工编辑标记（field_update_time = now 由 SQL 完成）
        verify(translationDao).updateMessageEdited(TRANS_ID, "新标题");
        verify(cacheService).cacheTranslations(List.of("zh_CN"));
    }

    @Test
    void updateBatchEmptyDetailsIsNoop() {
        assertEquals(0, service.updateBatch(null));

        SysI18nTranslationUpdateRequest request = new SysI18nTranslationUpdateRequest();
        request.setDetails(List.of());
        assertEquals(0, service.updateBatch(request));

        verify(translationDao, never()).updateMessageEdited(anyString(), anyString());
        verify(cacheService, never()).cacheTranslations(anyList());
    }

    @Test
    void updateBatchRejectsMissingDetailOrRecord() {
        SysI18nTranslationUpdateRequest request = new SysI18nTranslationUpdateRequest();
        SysI18nTranslationUpdateRequest.Detail noId = new SysI18nTranslationUpdateRequest.Detail();
        noId.setFieldMessage("x");
        request.setDetails(List.of(noId));
        assertThrows(IllegalArgumentException.class, () -> service.updateBatch(request));

        SysI18nTranslationUpdateRequest request2 = new SysI18nTranslationUpdateRequest();
        SysI18nTranslationUpdateRequest.Detail unknown = new SysI18nTranslationUpdateRequest.Detail();
        unknown.setFieldId(padId("ghost"));
        unknown.setFieldMessage("x");
        request2.setDetails(List.of(unknown));
        when(translationDao.selectById(unknown.getFieldId())).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.updateBatch(request2));
        verify(translationDao, never()).updateMessageEdited(anyString(), anyString());
    }

    @Test
    void updateBatchAggregatesLanguagesForCacheRefresh() {
        String transZh = padId("trans-zh");
        String transEn = padId("trans-en");
        when(translationDao.selectById(transZh)).thenReturn(translation("k", "zh_CN", "旧"));
        when(translationDao.selectById(transEn)).thenReturn(translation("k", "en_US", "old"));

        SysI18nTranslationUpdateRequest request = new SysI18nTranslationUpdateRequest();
        SysI18nTranslationUpdateRequest.Detail d1 = new SysI18nTranslationUpdateRequest.Detail();
        d1.setFieldId(transZh);
        d1.setFieldMessage("新");
        SysI18nTranslationUpdateRequest.Detail d2 = new SysI18nTranslationUpdateRequest.Detail();
        d2.setFieldId(transEn);
        d2.setFieldMessage("new");
        request.setDetails(List.of(d1, d2));

        service.updateBatch(request);

        // 受影响语言集合一次性刷新（zh_CN + en_US，去重聚合）
        verify(cacheService).cacheTranslations(argThat(codes -> null != codes
                && codes.contains("zh_CN") && codes.contains("en_US") && 2 == codes.size()));
    }

    /* ==================== reset ==================== */

    @Test
    void resetWritesResourceValueAndClearsEditedFlag() {
        // 有资源值：清人工编辑标记并回写资源文件当前值（复用启动同步的资源读取规则）
        when(keyDao.selectByKey("org.title")).thenReturn(key(KEY_ID_1, "org.title", "org"));
        when(syncService.readResourceValue("org", "zh_CN", "org.title")).thenReturn("资源标题");

        service.reset("org.title", "zh_CN");

        verify(translationDao).resetToResource("org.title", "zh_CN", "资源标题");
        verify(translationDao, never()).deleteByKeyAndLanguage(anyString(), anyString());
        verify(cacheService).cacheTranslations(List.of("zh_CN"));
    }

    @Test
    void resetDeletesTranslationWhenNoResourceValue() {
        // 该语言无对应资源文件（或文件中无此 key）→ 直接删除该翻译记录
        when(keyDao.selectByKey("org.title")).thenReturn(key(KEY_ID_1, "org.title", "org"));
        when(syncService.readResourceValue("org", "fr_FR", "org.title")).thenReturn(null);

        service.reset("org.title", "fr_FR");

        verify(translationDao).deleteByKeyAndLanguage("org.title", "fr_FR");
        verify(translationDao, never()).resetToResource(anyString(), anyString(), anyString());
        verify(cacheService).cacheTranslations(List.of("fr_FR"));
    }

    @Test
    void resetRejectsUnregisteredKeyAndBlankParams() {
        // 资源项未登记（sys_i18n_key 无此 key）视为参数错误（D5）
        when(keyDao.selectByKey("ghost.key")).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.reset("ghost.key", "zh_CN"));
        assertThrows(IllegalArgumentException.class, () -> service.reset(null, "zh_CN"));
        assertThrows(IllegalArgumentException.class, () -> service.reset("org.title", null));
        verify(translationDao, never()).resetToResource(anyString(), anyString(), anyString());
    }
}
