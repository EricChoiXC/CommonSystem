package com.common.system.common.base.service;

import com.common.system.common.base.annotation.Export;
import com.common.system.common.base.model.BaseModel;
import com.common.system.common.base.model.ImportResultVo;
import com.common.system.common.utils.ImportExportUtils;
import com.common.system.sys.dict.response.SysDictItemVo;
import com.common.system.sys.dict.service.SysDictService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 导入导出公共编排层（公共导入导出方案的 Spring 侧实现件）
 *
 * @author Eric
 * @date 2026/11/9
 * @description 组合 {@link ImportExportUtils}（静态解析 / 写出）与
 *              {@link SysDictService}（码值字典），为各模块提供三条公共通道：
 *              <ul>
 *              <li>导出 {@link #export}：码值 → 显示值转换 + Excel 写出；</li>
 *              <li>导入 {@link #importExcel}：解析 → 通用校验 → 码值回转 →
 *              模块行级校验（{@link ImportRowHandler}）→ 分批 insert → 汇总，
 *              整体单事务（{@code @Transactional}，失败整体回滚）；</li>
 *              <li>模板 {@link #template}：仅标题行的空模板字节。</li>
 *              </ul>
 *              导入口径（方案设计 7 节）：params 公共开关仅 {@code ignoreError}
 *              （Boolean，默认 false，兼容字符串 "true"）；{@code fullUpdate}
 *              公共层仅透传不解释，留给 rowHandler / 转换函数自行消费；
 *              未知 params 键不校验。同一行多个错误只记首个（校验顺序：
 *              required → dateFormat → dict → rowHandler，首个失败即记该行错误，
 *              不再校验后续项）。文件大小校验不自实现（容器 CS_ATT_MAX_FILE_SIZE
 *              拦截）。
 */
@Service
public class ImportExportService {

    private static final Logger log = LoggerFactory.getLogger(ImportExportService.class);

    /** 导入分批写库批次大小（本版单事务内逐条插入，批次边界仅记 debug 日志） */
    public static final int IMPORT_BATCH_SIZE = 200;

    /** params 公共开关键：失败行是否跳过（true 跳过并记 errors，false 任一行失败整体抛出） */
    private static final String PARAM_IGNORE_ERROR = "ignoreError";

    /** @Export.dateFormat 缺省格式（注解已带同值默认，此处兜底显式置空场景） */
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private final SysDictService dictService;

    /**
     * 构造器注入码值服务（Q3 决策：直接注入 SysDictService，不引 DictLookup）。
     *
     * @param dictService 码值服务
     */
    public ImportExportService(SysDictService dictService) {
        this.dictService = Objects.requireNonNull(dictService, "dictService must not be null");
    }

    /* ==================== 导出 / 模板 ==================== */

    /**
     * 导出：码值转换 + Excel 写出。
     * <p>解析 exportVoClass 上 {@code @Export(dict != "")} 字段，对每个字典
     * type_code 调 {@link SysDictService#itemsByTypeCode} 构建「码值 → 显示值」
     * 映射（全部一次性构建后交工具层），再按 @Export 列序写出；无对应启用项的
     * 码值原样写出（工具层记 warn）。</p>
     *
     * @param rows          数据列表（导出 VO）；null 或空列表时仅含标题行
     * @param exportVoClass 带 @Export 注解的导出 VO 类
     * @param <T>           导出 VO 类型
     * @return xlsx 文件字节
     */
    public <T> byte[] export(List<?> rows, Class<T> exportVoClass) {
        Objects.requireNonNull(exportVoClass, "exportVoClass must not be null");
        return exportTyped(castRows(rows), exportVoClass);
    }

    /** 泛型桥接：List<?> 经 uncheck cast 收窄为 List<T>（导出只读，cast 安全） */
    @SuppressWarnings("unchecked")
    private static <T> List<T> castRows(List<?> rows) {
        return (List<T>) rows;
    }

    /**
     * 导出（带字典映射）：解析 @Export(dict != "") 字段 → 构建「码值 → 显示值」
     * 映射（全部一次性构建）→ 工具层按列序写出；无对应启用项的码值原样写出
     * （工具层记 warn）。
     *
     * @param rows          数据列表（导出 VO）；null 或空列表时仅含标题行
     * @param exportVoClass 带 @Export 注解的导出 VO 类
     * @param <T>           导出 VO 类型
     * @return xlsx 文件字节
     */
    private <T> byte[] exportTyped(List<T> rows, Class<T> exportVoClass) {
        return ImportExportUtils.export(rows, exportVoClass, buildDictMappings(exportVoClass));
    }

    /**
     * 模板：仅标题行的空模板 xlsx 字节。
     *
     * @param voClass 带 @Export 注解的导入导出 VO 类
     * @param <T>     VO 类型
     * @return 仅含标题行的 xlsx 文件字节
     */
    public <T> byte[] template(Class<T> voClass) {
        return ImportExportUtils.template(voClass);
    }

    /* ==================== 导入编排（方案设计 7 节五步） ==================== */

    /**
     * 导入编排：解析 → 通用校验 → 码值回转 → 模块校验（rowHandler）→ 分批 insert → 汇总。
     * <p>整体单事务：任一行校验失败（ignoreError=false）或写库异常时抛出
     * {@link IllegalArgumentException} / 原始异常，整体回滚（GlobalExceptionHandler
     * 兜底转 BaseResponse）。</p>
     * <p>同一行多个错误只记首个：校验顺序 required → dateFormat → dict →
     * rowHandler，首个失败即记该行错误并跳过后续校验项。</p>
     *
     * @param file          上传的 xlsx 文件
     * @param params        导入参数（公共开关 ignoreError；fullUpdate 仅透传不解释；
     *                      null 视为空 Map，未知键不校验）
     * @param importVoClass 带 @Export 注解的导入 VO 类
     * @param rowHandler    模块行级业务校验扩展点；null 时跳过模块校验
     * @param toEntity      VO → 实体转换函数
     * @param baseService   模块业务服务（分批 insert 落库）
     * @param <V>           导入 VO 类型
     * @param <T>           实体类型
     * @return 导入结果：total / success / failed / errors
     */
    @Transactional
    public <V, T extends BaseModel> ImportResultVo importExcel(
            MultipartFile file, Map<String, Object> params, Class<V> importVoClass,
            ImportRowHandler<V> rowHandler, Function<V, T> toEntity, IBaseService<T> baseService) {
        /* ---- 第 1 步：params（公共开关 ignoreError；fullUpdate 仅透传不消费） ---- */
        Map<String, Object> paramMap = null == params ? Map.of() : params;
        boolean ignoreError = resolveIgnoreError(paramMap.get(PARAM_IGNORE_ERROR));

        /* ---- 第 2 步：文件前置校验（大小校验由容器 CS_ATT_MAX_FILE_SIZE 拦截） ---- */
        if (null == file || file.isEmpty()) {
            throw new IllegalArgumentException("导入文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (null == originalFilename || !originalFilename.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("仅支持 .xlsx 文件");
        }

        /* ---- 第 3 步：解析 + 通用校验（一次解析拿 VO + 原始文本 + 行号） ---- */
        List<ImportExportUtils.RowData<V>> rows;
        try (InputStream in = new ByteArrayInputStream(file.getBytes())) {
            rows = ImportExportUtils.explainWithRows(in, importVoClass);
        } catch (IllegalArgumentException e) {
            // 表头不一致 / 文件损坏：保持工具层消息原样上抛（BAD_REQUEST 兜底）
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("导入文件解析失败: " + e.getMessage(), e);
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("导入文件无数据行");
        }

        // @Export 列元数据（列序无关，按字段名组织；dict / required / dateFormat 供各校验项使用）
        List<ColumnMeta> columns = resolveMetas(importVoClass);
        // 字典映射：dict 名 →（显示值 → 码值）反转映射（循环外一次性构建）
        Map<String, Map<String, String>> displayToCode = buildDisplayToCode(columns);

        List<ImportResultVo.RowError> errors = new ArrayList<>();
        List<ImportExportUtils.RowData<V>> validRows = new ArrayList<>();
        for (ImportExportUtils.RowData<V> rowData : rows) {
            RowFailure error = validateRow(rowData, columns, displayToCode, rowHandler);
            if (null != error) {
                errors.add(new ImportResultVo.RowError(rowData.getRowNo(), error.field(), error.message()));
                if (!ignoreError) {
                    // ignoreError = false：任一行错误即抛（消息用首个行错误），insert 不执行
                    throw new IllegalArgumentException(error.message());
                }
                continue;
            }
            validRows.add(rowData);
        }

        /* ---- 第 5 步：分批写库（校验通过的行逐条 toEntity + insert，异常直接上抛整体回滚） ---- */
        int success = 0;
        for (int i = 0; i < validRows.size(); i++) {
            T entity = toEntity.apply(validRows.get(i).getRow());
            baseService.insert(entity);
            success++;
            if ((i + 1) % IMPORT_BATCH_SIZE == 0) {
                log.debug("导入写库进度: 已插入 {} / {} 条", i + 1, validRows.size());
            }
        }

        /* ---- 第 6 步：组装结果（ignoreError=true 且全部失败时也返回 failed=total） ---- */
        ImportResultVo result = new ImportResultVo();
        result.setTotal(rows.size());
        result.setSuccess(success);
        result.setFailed(errors.size());
        result.setErrors(errors);
        return result;
    }

    /* ==================== 私有实现 ==================== */

    /** 列校验元数据：字段 + 标题 + 字典名 + 日期格式 */
    private static class ColumnMeta {
        final Field field;
        final String title;
        final String dict;
        final String dateFormat;

        ColumnMeta(Field field, String title, String dict, String dateFormat) {
            this.field = field;
            this.title = title;
            this.dict = null == dict ? "" : dict;
            this.dateFormat = null == dateFormat || dateFormat.isEmpty() ? DEFAULT_DATE_FORMAT : dateFormat;
        }
    }

    /** 行校验结果：field 为 Java 字段名（行级错误为空串）+ 消息 */
    private record RowFailure(String field, String message) {
    }

    /** 解析 VO 类上 @Export 注解为列校验元数据（声明顺序即可，校验不依赖列序） */
    private List<ColumnMeta> resolveMetas(Class<?> voClass) {
        List<ColumnMeta> columns = new ArrayList<>();
        for (Field field : voClass.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (java.lang.reflect.Modifier.isStatic(modifiers) || java.lang.reflect.Modifier.isTransient(modifiers)) {
                continue;
            }
            Export anno = field.getAnnotation(Export.class);
            if (null == anno) {
                continue;
            }
            String title = anno.name().isEmpty() ? field.getName() : anno.name();
            columns.add(new ColumnMeta(field, title, anno.dict(), anno.dateFormat()));
        }
        return columns;
    }

    /** 导出用字典映射：dict 名 →（码值 → 显示值），仅取启用项；无启用项的字典放空映射（原样写码值） */
    private Map<String, Map<String, String>> buildDictMappings(Class<?> voClass) {
        Map<String, Map<String, String>> mappings = new HashMap<>();
        for (Field field : voClass.getDeclaredFields()) {
            Export anno = field.getAnnotation(Export.class);
            if (null == anno || anno.dict().isEmpty() || mappings.containsKey(anno.dict())) {
                continue;
            }
            mappings.put(anno.dict(), loadCodeToDisplay(anno.dict()));
        }
        return mappings;
    }

    /** 导入用反转映射：dict 名 →（显示值 → 码值）；type_code 未知（无启用项）时不放键 → 记「无效码值」 */
    private Map<String, Map<String, String>> buildDisplayToCode(List<ColumnMeta> columns) {
        Map<String, Map<String, String>> displayToCode = new HashMap<>();
        for (ColumnMeta column : columns) {
            if (column.dict.isEmpty() || displayToCode.containsKey(column.dict)) {
                continue;
            }
            Map<String, String> reversed = new HashMap<>();
            for (SysDictItemVo item : dictService.itemsByTypeCode(column.dict)) {
                if (null != item.getFieldValue() && null != item.getFieldName()) {
                    reversed.put(item.getFieldName(), item.getFieldValue());
                }
            }
            displayToCode.put(column.dict, reversed);
        }
        return displayToCode;
    }

    /** 查询字典启用项并构建「码值 → 显示值」映射 */
    private Map<String, String> loadCodeToDisplay(String typeCode) {
        Map<String, String> mapping = new HashMap<>();
        for (SysDictItemVo item : dictService.itemsByTypeCode(typeCode)) {
            if (null != item.getFieldValue() && null != item.getFieldName()) {
                mapping.put(item.getFieldValue(), item.getFieldName());
            }
        }
        if (mapping.isEmpty()) {
            log.warn("导入导出码值转换: 字典 [{}] 无启用项, 相关字段将原样写码值 / 记无效码值", typeCode);
        }
        return mapping;
    }

    /**
     * 单行校验（required → dateFormat → dict → rowHandler，同一行多错只记首个）。
     * 通过返回 null；失败返回行错误（field=Java 字段名，rowHandler 错误为空串）。
     */
    private <V> RowFailure validateRow(ImportExportUtils.RowData<V> rowData, List<ColumnMeta> columns,
                                       Map<String, Map<String, String>> displayToCode,
                                       ImportRowHandler<V> rowHandler) {
        V row = rowData.getRow();
        Map<String, String> rawByField = rowData.getRawByField();
        for (ColumnMeta column : columns) {
            String rawText = rawByField.getOrDefault(column.field.getName(), "");
            Class<?> fieldType = column.field.getType();

            // a. required：String 取 getter 值，Date/LocalDateTime 取原始文本；null/空串 → 行错误
            if (isRequired(column.field)) {
                String value = isStringField(fieldType) ? textOf(readProperty(row, column.field)) : rawText;
                if (value.isEmpty()) {
                    return new RowFailure(column.field.getName(), column.title + " 不能为空");
                }
            }

            // b. dateFormat：Date/LocalDateTime 原始文本非空时解析并回写时间对象
            if ((Date.class.equals(fieldType) || LocalDateTime.class.equals(fieldType)) && !rawText.isEmpty()) {
                try {
                    writeDateTime(row, column.field, fieldType, rawText, column.dateFormat);
                } catch (ParseException | IllegalArgumentException e) {
                    return new RowFailure(column.field.getName(), "日期格式错误");
                }
            }

            // c. dict 回转：显示值 → 码值；查不到（含 type_code 无启用项）→ 行错误
            if (!column.dict.isEmpty() && isStringField(fieldType)) {
                String display = textOf(readProperty(row, column.field));
                if (!display.isEmpty()) {
                    Map<String, String> reversed = displayToCode.get(column.dict);
                    String code = null == reversed ? null : reversed.get(display);
                    if (null == code) {
                        return new RowFailure(column.field.getName(), "无效码值");
                    }
                    writeProperty(row, column.field, String.class, code);
                }
            }
        }

        // d. 模块行级业务校验（rowHandler 为 null 时跳过）；返回非 null 非空串 → 行错误（field 空串）
        if (null != rowHandler) {
            String message = rowHandler.validate(row, rowData.getRowNo());
            if (null != message && !message.isEmpty()) {
                return new RowFailure("", message);
            }
        }
        return null;
    }

    /** 读取 @Export.required（字段未标注 / 标注缺省时 false） */
    private static boolean isRequired(Field field) {
        Export anno = field.getAnnotation(Export.class);
        return null != anno && anno.required();
    }

    /** 解析 ignoreError 开关：Boolean 直取；字符串 "true"（忽略大小写）兼容；其余 false */
    private static boolean resolveIgnoreError(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return "true".equalsIgnoreCase(s.trim());
        }
        return null != value && Boolean.parseBoolean(String.valueOf(value));
    }

    /** String 字段判断（dict 回转与 required 的 getter 口径仅对 String 字段生效） */
    private static boolean isStringField(Class<?> fieldType) {
        return String.class.equals(fieldType);
    }

    /** null → 空串（getter 取值归一） */
    private static String textOf(Object value) {
        return null == value ? "" : String.valueOf(value);
    }

    /** 读取属性值（getXxx / isXxx），无 getter 或读取失败返回 null */
    private static Object readProperty(Object bean, Field field) {
        Method getter = findAccessor(bean.getClass(), "get", field.getName());
        if (null == getter) {
            getter = findAccessor(bean.getClass(), "is", field.getName());
        }
        if (null == getter) {
            return null;
        }
        try {
            return getter.invoke(bean);
        } catch (Exception e) {
            return null;
        }
    }

    /** 按字段类型查找 setter 并写回（Date / LocalDateTime / String） */
    private static void writeProperty(Object bean, Field field, Class<?> paramType, Object value) {
        Method setter = findAccessor(bean.getClass(), "set", field.getName(), paramType);
        if (null == setter) {
            throw new IllegalStateException("字段缺少 setter: " + field.getName());
        }
        try {
            setter.invoke(bean, value);
        } catch (Exception e) {
            throw new IllegalStateException("字段赋值失败: " + field.getName(), e);
        }
    }

    /** 日期原始文本按 dateFormat 解析为 Date / LocalDateTime 并反射写回字段 */
    private static void writeDateTime(Object bean, Field field, Class<?> fieldType,
                                      String text, String dateFormat) throws ParseException {
        if (Date.class.equals(fieldType)) {
            Date date = new SimpleDateFormat(dateFormat).parse(text);
            writeProperty(bean, field, Date.class, date);
            return;
        }
        LocalDateTime dateTime = LocalDateTime.parse(text, DateTimeFormatter.ofPattern(dateFormat));
        writeProperty(bean, field, LocalDateTime.class, dateTime);
    }

    /** 查找公共方法：get/set/is + 首字母大写字段名（带参时按参数类型精确匹配） */
    private static Method findAccessor(Class<?> type, String prefix, String fieldName, Class<?>... paramTypes) {
        String suffix = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        try {
            return type.getMethod(prefix + suffix, paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
