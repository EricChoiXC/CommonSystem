package com.common.system.sys.i18n.service;

import com.alibaba.excel.EasyExcel;
import com.common.system.common.base.model.ImportResultVo;
import com.common.system.common.utils.StringUtil;
import com.common.system.common.utils.YmlResourceUtil;
import com.common.system.sys.i18n.dao.SysI18nKeyDao;
import com.common.system.sys.i18n.dao.SysI18nLanguageDao;
import com.common.system.sys.i18n.dao.SysI18nTranslationDao;
import com.common.system.sys.i18n.model.SysI18nKey;
import com.common.system.sys.i18n.model.SysI18nLanguage;
import com.common.system.sys.i18n.model.SysI18nTranslation;
import com.common.system.sys.i18n.request.SysI18nTranslationUpdateRequest;
import com.common.system.sys.i18n.response.SysI18nModelVo;
import com.common.system.sys.i18n.response.SysI18nTranslationExportVo;
import com.common.system.sys.i18n.response.SysI18nTranslationImportResultVo;
import com.common.system.sys.i18n.response.SysI18nTranslationMatrixVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 多语言-翻译维护服务
 *
 * @author Eric
 * @date 2026/11/5
 * @description 模块聚合查询与翻译矩阵维护（任务清单 I18N4-BE-001，4 接口的业务实现）：
 *              <ul>
 *              <li>model/list：聚合 sys_i18n_key 去重 field_model，显示名按「末段路径」
 *                  匹配各模块 application.yml（classpath 扫描 properties 目录）的
 *                  MODEL_NAME（MODEL_KEY 形如 SYS-ORG
 *                  与 field_model=org 不同源，禁止等值匹配），取不到回退 field_model；</li>
 *              <li>translation/list：行 = field_key 升序，列 = 启用语言按 field_order 升序，
 *                  matrix 以「key → (语言码 → 文本)」双层 Map 返回，无翻译的格缺省；</li>
 *              <li>translation/update 批量：仅对提交的变更记录写 field_message 并置人工编辑
 *                  标记（field_update_time = now），完成后刷新受影响语言的缓存；</li>
 *              <li>translation/reset：清人工编辑标记并回写资源文件当前值（复用启动同步的
 *                  资源读取规则）；该语言无对应资源文件（或文件中无此 key）→ 直接删除该翻译记录。</li>
 *              <li>translation/export：EasyExcel 动态头导出（列头 = 语言码，round-trip）；
 *              <li>translation/import：固定覆盖导入——仅按文件实际包含的 (key, language) 写入，
 *                  越界项（未登记 key / 无效语言列）跳过且不插入资源项（D5）。</li>
 *              </ul>
 */
@Service
public class SysI18nTranslationService {

    private static final Logger log = LoggerFactory.getLogger(SysI18nTranslationService.class);

    /** 模块 application.yml 扫描路径（提供 MODEL_NAME，路径末段为匹配键） */
    private static final String APPLICATION_RESOURCE_PATTERN = "classpath*:properties/**/application.yml";

    /** 模块中文名键（与 ModelCacheService 读取口径一致） */
    private static final String KEY_MODEL_NAME = "MODEL_NAME";

    /** 导出文件名时间戳格式（translations_{model}_{yyyyMMddHHmmss}.xlsx） */
    private static final DateTimeFormatter EXPORT_FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SysI18nKeyDao keyDao;
    private final SysI18nTranslationDao translationDao;
    private final SysI18nLanguageDao languageDao;
    private final SysI18nCacheService cacheService;
    private final SysI18nSyncService syncService;

    public SysI18nTranslationService(SysI18nKeyDao keyDao,
                                     SysI18nTranslationDao translationDao,
                                     SysI18nLanguageDao languageDao,
                                     SysI18nCacheService cacheService,
                                     SysI18nSyncService syncService) {
        this.keyDao = keyDao;
        this.translationDao = translationDao;
        this.languageDao = languageDao;
        this.cacheService = cacheService;
        this.syncService = syncService;
    }

    /* ==================== 模块聚合 ==================== */

    /**
     * 模块聚合列表：sys_i18n_key 去重 field_model + 模块显示名（末段路径匹配 MODEL_NAME）。
     *
     * @return 模块聚合项列表（field_model + field_model_name）
     */
    public List<SysI18nModelVo> listModels() {
        return listModels(loadModelNamesBySegment());
    }

    /**
     * 模块聚合列表（package-private 供单元测试传入构造的显示名映射）。
     *
     * @param nameBySegment application.yml 所在目录末段 → MODEL_NAME
     */
    List<SysI18nModelVo> listModels(Map<String, String> nameBySegment) {
        List<SysI18nModelVo> result = new ArrayList<>();
        for (String fieldModel : keyDao.selectDistinctModels()) {
            SysI18nModelVo vo = new SysI18nModelVo();
            vo.setFieldModel(fieldModel);
            vo.setFieldModelName(resolveModelName(fieldModel, nameBySegment));
            result.add(vo);
        }
        return result;
    }

    /**
     * 末段路径匹配模块显示名：field_model 与 MODEL_KEY（如 SYS-ORG）不同源，
     * 禁止等值匹配；取 application.yml 所在目录的末段与 field_model 的末段比对，
     * 取不到回退 field_model 原值。
     *
     * @param fieldModel    模块名（相对 i18n/ 的子目录层级名，如 org、process/template）
     * @param nameBySegment 目录末段 → MODEL_NAME
     * @return 模块显示名；无匹配回退 field_model
     */
    String resolveModelName(String fieldModel, Map<String, String> nameBySegment) {
        if (StringUtil.isEmpty(fieldModel) || null == nameBySegment || nameBySegment.isEmpty()) {
            return fieldModel;
        }
        String segment = fieldModel;
        int lastSlash = fieldModel.lastIndexOf('/');
        if (lastSlash >= 0) {
            segment = fieldModel.substring(lastSlash + 1);
        }
        return nameBySegment.getOrDefault(segment, fieldModel);
    }

    /**
     * 扫描各模块 application.yml（classpath*:properties 目录通配），构建「所在目录末段 → MODEL_NAME」映射。
     * <p>扫描失败仅记录日志返回空 Map（显示名回退 field_model，不阻断模块列表）。</p>
     */
    private Map<String, String> loadModelNamesBySegment() {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            Resource[] resources = YmlResourceUtil.getResources(APPLICATION_RESOURCE_PATTERN);
            for (Resource resource : resources) {
                String segment = resolveLastSegment(resource);
                if (StringUtil.isEmpty(segment)) {
                    continue;
                }
                Map<String, Object> data = YmlResourceUtil.loadYml(resource);
                Object modelName = null == data ? null : data.get(KEY_MODEL_NAME);
                if (null != modelName && StringUtil.isNotEmpty(String.valueOf(modelName))) {
                    result.putIfAbsent(segment, String.valueOf(modelName));
                }
            }
        } catch (IOException e) {
            log.warn("failed to load MODEL_NAME from {}, display name falls back to field_model",
                    APPLICATION_RESOURCE_PATTERN, e);
        }
        return result;
    }

    /**
     * 从 application.yml 资源 URL 解析所在目录末段（如 properties/sys/org/application.yml → org）。
     */
    private String resolveLastSegment(Resource resource) {
        try {
            String path = resource.getURL().toString();
            int index = path.indexOf("/properties/");
            if (index < 0) {
                return null;
            }
            String relative = path.substring(index + "/properties/".length());
            if (!relative.endsWith("/application.yml")) {
                return null;
            }
            String dir = relative.substring(0, relative.length() - "/application.yml".length());
            int lastSlash = dir.lastIndexOf('/');
            return lastSlash < 0 ? dir : dir.substring(lastSlash + 1);
        } catch (IOException e) {
            log.warn("failed to resolve application.yml segment: {}", resource, e);
            return null;
        }
    }

    /* ==================== 翻译矩阵 ==================== */

    /**
     * 翻译矩阵：行 = 该模块全部资源项（field_key 升序），列 = 启用语言（field_order 升序），
     * matrix 以「key → (语言码 → 文本)」双层 Map 返回，无翻译的语言格在 Map 中缺省。
     *
     * @param fieldModel 模块名（必填）
     * @return 矩阵响应（keys / languages / matrix）
     */
    public SysI18nTranslationMatrixVo translationList(String fieldModel) {
        if (StringUtil.isEmpty(fieldModel)) {
            throw new IllegalArgumentException("fieldModel 不能为空");
        }
        List<SysI18nKey> keys = keyDao.selectByModel(fieldModel);
        // 语言列：启用语言按 field_order 升序（selectAll 已按 field_order 排序，此处仅过滤启用）
        List<SysI18nTranslationMatrixVo.LanguageColumn> columns = new ArrayList<>();
        List<String> enabledCodes = new ArrayList<>();
        for (SysI18nLanguage language : languageDao.selectAll()) {
            if (Boolean.TRUE.equals(language.getFieldEnable()) && StringUtil.isNotEmpty(language.getFieldCode())) {
                SysI18nTranslationMatrixVo.LanguageColumn column = new SysI18nTranslationMatrixVo.LanguageColumn();
                column.setFieldCode(language.getFieldCode());
                column.setFieldName(language.getFieldName());
                columns.add(column);
                enabledCodes.add(language.getFieldCode());
            }
        }
        // 矩阵：先为每个资源行建空行（无翻译的格缺省），再按语言列填充
        Map<String, Map<String, String>> matrix = new LinkedHashMap<>();
        for (SysI18nKey key : keys) {
            matrix.put(key.getFieldKey(), new LinkedHashMap<>());
        }
        for (String code : enabledCodes) {
            for (SysI18nTranslation translation : translationDao.selectByModelAndLanguage(fieldModel, code)) {
                Map<String, String> row = matrix.get(translation.getFieldKey());
                if (null != row) {
                    row.put(code, translation.getFieldMessage());
                }
            }
        }
        SysI18nTranslationMatrixVo vo = new SysI18nTranslationMatrixVo();
        vo.setFieldModel(fieldModel);
        vo.setKeys(keys);
        vo.setLanguages(columns);
        vo.setMatrix(matrix);
        return vo;
    }

    /* ==================== 批量更新 ==================== */

    /**
     * 批量更新翻译：仅对提交的变更记录写 field_message 并置人工编辑标记
     * （field_update_time = now），完成后刷新受影响语言的缓存。
     * <p>空明细列表视为无改动（幂等返回 0）；明细二选一定位（I18N4-FE-001 前端矩阵契约）：</p>
     * <ul>
     * <li>fieldId：按翻译记录主键定位，记录不存在视为参数错误；</li>
     * <li>fieldKey + fieldLanguage：按 (key, language) 定位，DB 无记录时补建
     *     （矩阵空格首次编辑；key 未在 sys_i18n_key 登记视为参数错误，D5）。</li>
     * </ul>
     * <p>全部改动同事务整体回滚。</p>
     *
     * @param request 批量更新请求（details 为本次改动项）
     * @return 实际更新条数
     */
    @Transactional
    public int updateBatch(SysI18nTranslationUpdateRequest request) {
        if (null == request || null == request.getDetails() || request.getDetails().isEmpty()) {
            // 空列表视为无改动
            return 0;
        }
        Set<String> languages = new LinkedHashSet<>();
        for (SysI18nTranslationUpdateRequest.Detail detail : request.getDetails()) {
            if (null == detail) {
                throw new IllegalArgumentException("改动明细不能为空");
            }
            if (StringUtil.isNotEmpty(detail.getFieldId())) {
                applyUpdateById(detail, languages);
            } else {
                applyUpdateByKeyAndLanguage(detail, languages);
            }
        }
        if (!languages.isEmpty()) {
            cacheService.cacheTranslations(new ArrayList<>(languages));
        }
        log.info("i18n translations updated: count={}, languages={}", request.getDetails().size(), languages);
        return request.getDetails().size();
    }

    /**
     * 按翻译记录主键定位的明细写入：记录必须存在（矩阵中已有翻译记录的格子）。
     */
    private void applyUpdateById(SysI18nTranslationUpdateRequest.Detail detail, Set<String> languages) {
        SysI18nTranslation exist = translationDao.selectById(detail.getFieldId());
        if (null == exist) {
            throw new IllegalArgumentException("翻译记录不存在: " + detail.getFieldId());
        }
        translationDao.updateMessageEdited(detail.getFieldId(), detail.getFieldMessage());
        if (StringUtil.isNotEmpty(exist.getFieldLanguage())) {
            languages.add(exist.getFieldLanguage());
        }
    }

    /**
     * 按 (key, language) 定位的明细写入：DB 无记录时补建（矩阵空格首次编辑，
     * 与导入的「文件包含该格但 DB 无记录」分支同规则）；key 未登记（sys_i18n_key 无此 key）
     * 视为参数错误（D5：key 仅随资源扫描产生，不插入资源项）。
     */
    private void applyUpdateByKeyAndLanguage(SysI18nTranslationUpdateRequest.Detail detail, Set<String> languages) {
        String fieldKey = detail.getFieldKey();
        String fieldLanguage = detail.getFieldLanguage();
        if (StringUtil.isEmpty(fieldKey) || StringUtil.isEmpty(fieldLanguage)) {
            throw new IllegalArgumentException("改动明细 fieldId 为空时 fieldKey 与 fieldLanguage 不能为空");
        }
        if (null == keyDao.selectByKey(fieldKey)) {
            throw new IllegalArgumentException("资源项未登记: " + fieldKey);
        }
        SysI18nTranslation exist = translationDao.selectByKeyAndLanguage(fieldKey, fieldLanguage);
        if (null == exist) {
            SysI18nTranslation model = new SysI18nTranslation();
            model.initFieldId();
            model.setFieldKey(fieldKey);
            model.setFieldLanguage(fieldLanguage);
            model.setFieldMessage(detail.getFieldMessage());
            model.setFieldUpdateTime(new Date());
            translationDao.insert(model);
        } else {
            translationDao.updateMessageEdited(exist.getFieldId(), detail.getFieldMessage());
        }
        languages.add(fieldLanguage);
    }

    /* ==================== 重置 ==================== */

    /**
     * 重置单条翻译：清人工编辑标记并回写资源文件当前值（复用启动同步的资源读取规则）；
     * 该语言无对应资源文件（或文件中无此 key）→ 直接删除该翻译记录。
     * <p>资源项未登记（sys_i18n_key 无此 key）视为参数错误（D5：key 仅随资源扫描产生）。</p>
     *
     * @param fieldKey      资源 key
     * @param fieldLanguage 语言码
     */
    @Transactional
    public void reset(String fieldKey, String fieldLanguage) {
        if (StringUtil.isEmpty(fieldKey)) {
            throw new IllegalArgumentException("fieldKey 不能为空");
        }
        if (StringUtil.isEmpty(fieldLanguage)) {
            throw new IllegalArgumentException("fieldLanguage 不能为空");
        }
        SysI18nKey key = keyDao.selectByKey(fieldKey);
        if (null == key) {
            throw new IllegalArgumentException("资源项不存在: " + fieldKey);
        }
        String resourceValue = syncService.readResourceValue(key.getFieldModel(), fieldLanguage, fieldKey);
        if (null == resourceValue) {
            // 分支：该语言无对应资源文件（或文件中无此 key）→ 直接删除该翻译记录
            translationDao.deleteByKeyAndLanguage(fieldKey, fieldLanguage);
            log.info("i18n translation reset to absent: key={}, language={} (no resource value)", fieldKey, fieldLanguage);
        } else {
            // 清人工编辑标记（field_update_time 置空）并回写资源文件值
            translationDao.resetToResource(fieldKey, fieldLanguage, resourceValue);
            log.info("i18n translation reset to resource: key={}, language={}", fieldKey, fieldLanguage);
        }
        cacheService.cacheTranslations(List.of(fieldLanguage));
    }

    /* ==================== 导出 ==================== */

    /**
     * 导出指定模块全量翻译为 xlsx 字节（EasyExcel 动态头，round-trip 可回传导入）：
     * 列 = 资源名（模块 + key）+ 按 field_order 升序的各启用语言列；
     * 首行为语言码列头（导入按列头反解析语言）。
     *
     * @param fieldModel 模块名（必填）
     * @return xlsx 文件字节
     */
    public byte[] export(String fieldModel) {
        if (StringUtil.isEmpty(fieldModel)) {
            throw new IllegalArgumentException("fieldModel 不能为空");
        }
        List<SysI18nTranslationExportVo> rows = buildExportRows(fieldModel);
        List<List<String>> head = buildExportHead(rows);
        List<List<String>> data = buildExportData(rows);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out)
                    .head(head)
                    .sheet(fieldModel)
                    .doWrite(data);
            log.info("i18n translations exported: model={}, rows={}, languages={}",
                    fieldModel, data.size(), head.size() - 1);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("failed to export i18n translations: " + fieldModel, e);
        }
    }

    /**
     * 导出文件名：translations_{model}_{yyyyMMddHHmmss}.xlsx（带时间戳，时间取生成时刻）。
     *
     * @param fieldModel 模块名
     * @return 导出文件名
     */
    public String buildExportFileName(String fieldModel) {
        return "translations_" + fieldModel + "_"
                + LocalDateTime.now().format(EXPORT_FILE_TIMESTAMP) + ".xlsx";
    }

    /**
     * 组装导出行：资源行（field_key 升序）× 启用语言列（field_order 升序）；
     * 无翻译的语言格以空串写出（保持 round-trip 行列对齐）。
     */
    private List<SysI18nTranslationExportVo> buildExportRows(String fieldModel) {
        List<String> languageCodes = enabledLanguageCodes();
        Map<String, Map<String, String>> byKey = new LinkedHashMap<>();
        for (SysI18nKey key : keyDao.selectByModel(fieldModel)) {
            byKey.put(key.getFieldKey(), new LinkedHashMap<>());
        }
        for (String code : languageCodes) {
            for (SysI18nTranslation translation : translationDao.selectByModelAndLanguage(fieldModel, code)) {
                Map<String, String> row = byKey.get(translation.getFieldKey());
                if (null != row) {
                    row.put(code, translation.getFieldMessage());
                }
            }
        }
        List<SysI18nTranslationExportVo> rows = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : byKey.entrySet()) {
            SysI18nTranslationExportVo vo = new SysI18nTranslationExportVo();
            vo.setResourceName(fieldModel + " | " + entry.getKey());
            List<String> messages = new ArrayList<>();
            for (String code : languageCodes) {
                String message = entry.getValue().get(code);
                messages.add(null == message ? "" : message);
            }
            vo.setMessages(messages);
            vo.setLanguageCodes(languageCodes);
            rows.add(vo);
        }
        return rows;
    }

    /**
     * 导出动态表头：第 1 列固定「资源名」，其后为语言码列头（与 messages 顺序一致）。
     */
    private List<List<String>> buildExportHead(List<SysI18nTranslationExportVo> rows) {
        List<List<String>> head = new ArrayList<>();
        head.add(List.of("资源名"));
        List<String> languageCodes = rows.isEmpty() ? enabledLanguageCodes() : rows.get(0).getLanguageCodes();
        for (String code : languageCodes) {
            head.add(List.of(code));
        }
        return head;
    }

    /**
     * 导出数据行：资源名 + 各语言文本（与表头列序对齐）。
     */
    private List<List<String>> buildExportData(List<SysI18nTranslationExportVo> rows) {
        List<List<String>> data = new ArrayList<>();
        for (SysI18nTranslationExportVo row : rows) {
            List<String> line = new ArrayList<>();
            line.add(row.getResourceName());
            line.addAll(row.getMessages());
            data.add(line);
        }
        return data;
    }

    /* ==================== 导入 ==================== */

    /**
     * 导入翻译 xlsx（固定覆盖导入）：仅按文件实际包含的 (key, language) 写入 field_message
     * 并置人工编辑标记（field_update_time = now）；
     * 越界项一律跳过且不插入资源项（D5）：文件中出现但 sys_i18n_key 未登记的 key、
     * 无效（非启用）语言码的列分别记入 skippedKeys / skippedLanguages。
     * <p>结果映射（公共方案 9.1 口径，Q6 继承 ImportResultVo）：</p>
     * <ul>
     * <li>total = 文件实际包含的 (key, language) 格数（EasyExcel 空单元格不入集合，
     *     按解析到的有值格计，同一文件重复 (key, language) 只计一格）；</li>
     * <li>success = 实际写入的翻译格数；</li>
     * <li>failed = 跳过格数汇总：未登记 key 覆盖的全部格 + 已登记 key 中无效语言列覆盖的格；</li>
     * <li>errors = 按跳过 key / 语言逐项生成（field = key 或语言码，message = 跳过原因，
     *     row = 0 表示非行级跳过项）。</li>
     * </ul>
     *
     * @param file    上传的 xlsx 文件
     * @return 导入结果（公共字段 total/success/failed/errors + 跳过项反馈）
     */
    @Transactional
    public SysI18nTranslationImportResultVo importTranslations(MultipartFile file) {
        if (null == file || file.isEmpty()) {
            throw new IllegalArgumentException("导入文件不能为空");
        }
        ImportSheet sheet = readImportSheet(file);
        List<String> languageCodes = sheet.languageCodes;
        // 启用语言码集合：无效（非启用）语言码列整体跳过
        Set<String> enabledCodes = new LinkedHashSet<>(enabledLanguageCodes());
        SysI18nTranslationImportResultVo result = new SysI18nTranslationImportResultVo();
        result.setSkippedKeys(new ArrayList<>());
        result.setSkippedLanguages(new ArrayList<>());
        int updated = 0;
        int skippedCells = 0;
        int totalCells = 0;
        for (String code : languageCodes) {
            if (!enabledCodes.contains(code)) {
                result.getSkippedLanguages().add(code);
            }
        }
        // (key, language) → 文本；同一文件重复 (key, language) 以后出现的行为准
        Map<String, Map<String, String>> cellByLanguage = sheet.cells;
        for (Map.Entry<String, Map<String, String>> entry : cellByLanguage.entrySet()) {
            String key = entry.getKey();
            Map<String, String> cells = entry.getValue();
            totalCells += cells.size();
            SysI18nKey registered = keyDao.selectByKey(key);
            if (null == registered) {
                // 越界项：未登记 key 跳过，不插入资源项（D5）；其覆盖的全部格计入跳过格数
                result.getSkippedKeys().add(key);
                skippedCells += cells.size();
                continue;
            }
            for (Map.Entry<String, String> cell : cells.entrySet()) {
                String code = cell.getKey();
                if (!enabledCodes.contains(code)) {
                    // 已登记 key × 无效语言列的格：计入跳过格数
                    skippedCells++;
                    continue;
                }
                SysI18nTranslation exist = translationDao.selectByKeyAndLanguage(key, code);
                if (null == exist) {
                    // 文件包含该格但 DB 无记录：按固定覆盖导入写入（置人工编辑标记）
                    SysI18nTranslation model = new SysI18nTranslation();
                    model.initFieldId();
                    model.setFieldKey(key);
                    model.setFieldLanguage(code);
                    model.setFieldMessage(cell.getValue());
                    model.setFieldUpdateTime(new Date());
                    translationDao.insert(model);
                } else {
                    // 固定覆盖：写文本并置人工编辑标记（导入视为人工编辑）
                    translationDao.updateMessageEdited(exist.getFieldId(), cell.getValue());
                }
                updated++;
            }
        }
        // 公共字段映射（9.1）：success = 原 updated 计数，failed = 跳过格数汇总
        result.setTotal(totalCells);
        result.setSuccess(updated);
        result.setFailed(skippedCells);
        // errors：按跳过 key / 语言逐项生成（field = key 或语言码，row = 0 表示非行级跳过项）
        List<ImportResultVo.RowError> errors = new ArrayList<>();
        for (String skippedKey : result.getSkippedKeys()) {
            errors.add(new ImportResultVo.RowError(0, skippedKey, "资源项未登记，已跳过该 key 的全部翻译"));
        }
        for (String code : result.getSkippedLanguages()) {
            errors.add(new ImportResultVo.RowError(0, code, "语言码未启用，已跳过该语言列的全部翻译"));
        }
        result.setErrors(errors);
        if (!enabledCodes.isEmpty()) {
            cacheService.cacheTranslations(new ArrayList<>(enabledCodes));
        }
        log.info("i18n translations imported: total={}, success={}, failed={}, skippedKeys={}, skippedLanguages={}",
                totalCells, updated, skippedCells, result.getSkippedKeys(), result.getSkippedLanguages());
        return result;
    }

    /**
     * 解析导入 xlsx：首行 = 「资源名」+ 语言码列头；数据行按「资源名」列反解析模块与 key，
     * 组装 key → (语言码 → 文本)。资源名格式与导出一致（"模块 | key"）。
     */
    private ImportSheet readImportSheet(MultipartFile file) {
        List<Map<Integer, String>> rawRows;
        try (java.io.InputStream in = file.getInputStream()) {
            // headRowNumber(0)：全部行读取（第 0 行 = 表头行，由本方法自行解析语言码列头）
            rawRows = EasyExcel.read(in).sheet(0).headRowNumber(0).doReadSync();
        } catch (IOException e) {
            throw new IllegalArgumentException("导入文件读取失败", e);
        }
        if (rawRows.isEmpty()) {
            throw new IllegalArgumentException("导入文件为空");
        }
        if (rawRows.size() <= 1) {
            // 仅表头无数据行：视为无效导入文件
            throw new IllegalArgumentException("导入文件无数据行");
        }
        ImportSheet sheet = new ImportSheet();
        Map<Integer, String> headRow = rawRows.get(0);
        int maxIndex = 0;
        for (Integer idx : headRow.keySet()) {
            if (null != idx && idx > maxIndex) {
                maxIndex = idx;
            }
        }
        List<String> languageCodes = new ArrayList<>();
        for (int col = 1; col <= maxIndex; col++) {
            String text = headRow.get(col);
            if (null != text) {
                text = text.trim();
            }
            if (StringUtil.isEmpty(text)) {
                continue;
            }
            languageCodes.add(text);
        }
        sheet.languageCodes = languageCodes;
        sheet.cells = new LinkedHashMap<>();
        for (int i = 1; i < rawRows.size(); i++) {
            Map<Integer, String> raw = rawRows.get(i);
            String resourceName = raw.get(0);
            if (StringUtil.isEmpty(resourceName)) {
                continue;
            }
            String key = extractKey(resourceName.trim());
            if (StringUtil.isEmpty(key)) {
                continue;
            }
            Map<String, String> cells = sheet.cells.computeIfAbsent(key, k -> new LinkedHashMap<>());
            // 语言列与列号对齐：第 col 个语言码在第 col + 1 列
            for (int col = 0; col < languageCodes.size(); col++) {
                String value = raw.get(col + 1);
                if (null != value) {
                    cells.put(languageCodes.get(col), value);
                }
            }
        }
        return sheet;
    }

    /**
     * 从资源名列反解析 key：格式「模块 | key」（与导出一致）；兼容无「模块 | 」前缀的裸 key。
     */
    String extractKey(String resourceName) {
        if (StringUtil.isEmpty(resourceName)) {
            return null;
        }
        int separator = resourceName.indexOf('|');
        return separator < 0 ? resourceName.trim() : resourceName.substring(separator + 1).trim();
    }

    /**
     * 启用语言码列表（field_order 升序；导出列头与导入列校验共用）。
     */
    private List<String> enabledLanguageCodes() {
        List<String> codes = new ArrayList<>();
        for (SysI18nLanguage language : languageDao.selectAll()) {
            if (Boolean.TRUE.equals(language.getFieldEnable()) && StringUtil.isNotEmpty(language.getFieldCode())) {
                codes.add(language.getFieldCode());
            }
        }
        return codes;
    }

    /**
     * 导入文件解析结果（语言码列头 + key → (语言码 → 文本) 单元格）。
     */
    private static class ImportSheet {
        private List<String> languageCodes;
        private Map<String, Map<String, String>> cells;
    }
}
