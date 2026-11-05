package com.common.system.sys.i18n.service;

import com.alibaba.excel.EasyExcel;
import com.common.system.sys.i18n.dao.SysI18nKeyDao;
import com.common.system.sys.i18n.dao.SysI18nLanguageDao;
import com.common.system.sys.i18n.dao.SysI18nTranslationDao;
import com.common.system.sys.i18n.model.SysI18nKey;
import com.common.system.sys.i18n.model.SysI18nLanguage;
import com.common.system.sys.i18n.model.SysI18nTranslation;
import com.common.system.sys.i18n.response.SysI18nTranslationImportResultVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * 多语言-翻译导入导出测试
 *
 * @author Eric
 * @date 2026/11/5
 * @description 覆盖任务清单 I18N4-BE-002 验收标准：
 *              <ul>
 *              <li>导出：EasyExcel 动态头（资源名 + 启用语言按 field_order 升序），
 *                  导出文件经 EasyExcel 读回核对（round-trip 基础）；</li>
 *              <li>导入：仅按文件实际包含的 (key, language) 写入并置人工编辑标记
 *                  （field_update_time 非空）；未登记 key 跳过且不插入资源项（D5）、
 *                  无效语言码列跳过；导入后刷新缓存；空文件拒绝。</li>
 *              </ul>
 */
class SysI18nTranslationExcelTest {

    private static final String KEY_ID_1 = padId("key-1");
    private static final String TRANS_ID = padId("trans-1");

    private SysI18nKeyDao keyDao;
    private SysI18nTranslationDao translationDao;
    private SysI18nLanguageDao languageDao;
    private SysI18nCacheService cacheService;
    private SysI18nTranslationService service;

    @BeforeEach
    void setUp() {
        keyDao = mock(SysI18nKeyDao.class);
        translationDao = mock(SysI18nTranslationDao.class);
        languageDao = mock(SysI18nLanguageDao.class);
        cacheService = mock(SysI18nCacheService.class);
        service = new SysI18nTranslationService(keyDao, translationDao, languageDao, cacheService,
                mock(SysI18nSyncService.class));
    }

    private static String padId(String raw) {
        return String.format("%-32s", raw).replace(' ', '0');
    }

    private SysI18nKey key(String fieldKey) {
        SysI18nKey model = new SysI18nKey();
        model.setFieldId(KEY_ID_1);
        model.setFieldKey(fieldKey);
        model.setFieldModel("org");
        return model;
    }

    private SysI18nLanguage language(String code, boolean enable, int order) {
        SysI18nLanguage model = new SysI18nLanguage();
        model.setFieldId(padId("lang-" + code));
        model.setFieldCode(code);
        model.setFieldName(code);
        model.setFieldEnable(enable);
        model.setFieldOrder(order);
        return model;
    }

    private SysI18nTranslation translation(String fieldKey, String code, String message) {
        SysI18nTranslation model = new SysI18nTranslation();
        model.setFieldId(TRANS_ID);
        model.setFieldKey(fieldKey);
        model.setFieldLanguage(code);
        model.setFieldMessage(message);
        return model;
    }

    /** 用 EasyExcel 构造导入文件字节（与导出列结构一致） */
    private byte[] buildImportFile(List<List<String>> head, List<List<String>> data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out).head(head).sheet("org").doWrite(data);
        return out.toByteArray();
    }

    private MockMultipartFile toUpload(byte[] bytes) {
        return new MockMultipartFile("file", "translations_org.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    /* ==================== 导出 ==================== */

    @Test
    void exportWritesDynamicHeadAndRoundTripRows() {
        when(languageDao.selectAll()).thenReturn(List.of(
                language("zh_CN", true, 1), language("fr_FR", true, 2), language("de_DE", false, 3)));
        when(keyDao.selectByModel("org")).thenReturn(List.of(key("org.title"), key("org.user")));
        when(translationDao.selectByModelAndLanguage("org", "zh_CN")).thenReturn(List.of(
                translation("org.title", "zh_CN", "标题"), translation("org.user", "zh_CN", "用户")));
        when(translationDao.selectByModelAndLanguage("org", "fr_FR")).thenReturn(List.of(
                translation("org.title", "fr_FR", "Titre")));

        byte[] bytes = service.export("org");
        assertNotNull(bytes);

        // 导出文件经 EasyExcel 读回核对（round-trip 基础）
        List<Map<Integer, String>> rows = EasyExcel.read(new ByteArrayInputStream(bytes))
                .sheet(0).headRowNumber(0).doReadSync();
        // 首行列头：资源名 + 启用语言按 field_order 升序（停用 de_DE 不在列）
        assertEquals("资源名", rows.get(0).get(0));
        assertEquals("zh_CN", rows.get(0).get(1));
        assertEquals("fr_FR", rows.get(0).get(2));
        // 数据行：资源名 + 各语言文本
        assertEquals("org | org.title", rows.get(1).get(0));
        assertEquals("标题", rows.get(1).get(1));
        assertEquals("Titre", rows.get(1).get(2));
        assertEquals("org | org.user", rows.get(2).get(0));
        assertEquals("用户", rows.get(2).get(1));
        // 无翻译的格保持行列对齐（空值）
        String userFr = rows.get(2).get(2);
        assertTrue(null == userFr || userFr.isEmpty());
    }

    @Test
    void exportRejectsBlankModel() {
        assertThrows(IllegalArgumentException.class, () -> service.export(null));
        assertThrows(IllegalArgumentException.class, () -> service.export(" "));
    }

    /* ==================== 导入 ==================== */

    @Test
    void importUpdatesRegisteredCellsAndSetsEditedFlag() {
        when(languageDao.selectAll()).thenReturn(List.of(
                language("zh_CN", true, 1), language("fr_FR", true, 2)));
        when(keyDao.selectByKey("org.title")).thenReturn(key("org.title"));
        when(translationDao.selectByKeyAndLanguage("org.title", "zh_CN"))
                .thenReturn(translation("org.title", "zh_CN", "旧"));
        when(translationDao.selectByKeyAndLanguage("org.title", "fr_FR")).thenReturn(null);

        byte[] bytes = buildImportFile(
                List.of(List.of("资源名"), List.of("zh_CN"), List.of("fr_FR")),
                List.of(List.of("org | org.title", "新标题", "Titre")));

        SysI18nTranslationImportResultVo result = service.importTranslations(toUpload(bytes));

        assertEquals(2, result.getUpdated());
        // 已有记录：固定覆盖并置人工编辑标记（field_update_time 由 SQL 置 now）
        verify(translationDao).updateMessageEdited(TRANS_ID, "新标题");
        // 无记录的格：插入且 field_update_time 非空（导入视为人工编辑）
        ArgumentCaptor<SysI18nTranslation> captor = ArgumentCaptor.forClass(SysI18nTranslation.class);
        verify(translationDao).insert(captor.capture());
        assertEquals("org.title", captor.getValue().getFieldKey());
        assertEquals("fr_FR", captor.getValue().getFieldLanguage());
        assertEquals("Titre", captor.getValue().getFieldMessage());
        assertNotNull(captor.getValue().getFieldUpdateTime());
        // 导入后刷新启用语言缓存
        verify(cacheService).cacheTranslations(argThat(codes ->
                null != codes && codes.contains("zh_CN") && codes.contains("fr_FR") && 2 == codes.size()));
    }

    @Test
    void importSkipsUnregisteredKeysAndInvalidLanguageColumns() {
        when(languageDao.selectAll()).thenReturn(List.of(language("zh_CN", true, 1)));
        when(keyDao.selectByKey("org.title")).thenReturn(key("org.title"));
        when(keyDao.selectByKey("ghost.key")).thenReturn(null);
        when(translationDao.selectByKeyAndLanguage("org.title", "zh_CN"))
                .thenReturn(translation("org.title", "zh_CN", "旧"));

        // 文件含未登记 key（ghost.key）与无效语言码列（xx_XX）：均跳过
        byte[] bytes = buildImportFile(
                List.of(List.of("资源名"), List.of("zh_CN"), List.of("xx_XX")),
                List.of(List.of("org | org.title", "新", "X"),
                        List.of("ghost.key", "g", "G")));

        SysI18nTranslationImportResultVo result = service.importTranslations(toUpload(bytes));

        assertEquals(1, result.getUpdated());
        assertEquals(List.of("ghost.key"), result.getSkippedKeys());
        assertEquals(List.of("xx_XX"), result.getSkippedLanguages());
        // D5：不插入资源项
        verify(keyDao, never()).insert(any(SysI18nKey.class));
        // 无效语言列不写翻译
        verify(translationDao, never()).selectByKeyAndLanguage(anyString(), eq("xx_XX"));
        verify(translationDao).updateMessageEdited(TRANS_ID, "新");
    }

    @Test
    void importRejectsEmptyFileAndEmptySheet() {
        // 空文件
        assertThrows(IllegalArgumentException.class,
                () -> service.importTranslations(toUpload(new byte[0])));

        // 无数据行的表
        byte[] bytes = buildImportFile(
                List.of(List.of("资源名"), List.of("zh_CN")),
                List.of());
        assertThrows(IllegalArgumentException.class, () -> service.importTranslations(toUpload(bytes)));

        verify(translationDao, never()).updateMessageEdited(anyString(), anyString());
        verify(cacheService, never()).cacheTranslations(anyList());
    }

    @Test
    void extractKeyParsesModelPrefixAndBareKey() {
        assertEquals("org.title", service.extractKey("org | org.title"));
        assertEquals("org.title", service.extractKey("org.title"));
        // 无模块前缀的裸 key（兼容手工编辑的文件）
        assertEquals("bare_key", service.extractKey("bare_key"));
        assertNull(service.extractKey(null));
        assertNull(service.extractKey("   "));
    }
}
