package com.eric.base.common.utils;

import com.alibaba.excel.EasyExcel;
import com.eric.base.common.base.annotation.Export;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 导入导出静态工具类（公共导入导出方案的底层实现件）
 *
 * @author Eric
 * @date 2026/11/9
 * @description 基于 {@link Export} 注解与 EasyExcel 的纯静态工具，无状态、
 *              不注入任何 Spring Bean。提供两条通道：
 *              <ul>
 *              <li>固定 VO 通道：列元数据来自 VO 字段上的 @Export 注解，
 *              供 ImportExportService 直接调用（含字典映射重载）；</li>
 *              <li>自定义字段 Map 通道：列元数据由 modelMap 运行时给定
 *              （键 field/name/required，dict/dateFormat 首版忽略不实现）。</li>
 *              </ul>
 *              行为口径（方案设计 12 节）：列序按 order 升序、order=-1 按
 *              声明顺序追加在有值字段之后；标题 name 为空取 Java 字段名
 *              驼峰原样；导入表头与声明逐一比对，不一致抛
 *              {@link IllegalArgumentException} 并给出具体差异；解析一律
 *              按「标题文本 → 字段」映射，不依赖固定列下标。单元格一律
 *              读为文本后按 VO 字段类型赋值：String 直接赋值；Date /
 *              LocalDateTime 等非 String 类型保持文本由上层校验，本层不
 *              强转（跳过赋值）。
 */
public final class ImportExportUtils {

    private static final Logger log = LoggerFactory.getLogger(ImportExportUtils.class);

    private ImportExportUtils() {
    }

    /* ==================== 固定 VO 通道 ==================== */

    /**
     * 将列表数据导出为 xlsx 字节（按 @Export 注解构建标题行与列序）。
     *
     * @param list  数据列表；null 或空列表时仅含标题行
     * @param clazz 带 @Export 注解的导出 VO 类
     * @param <T>   VO 类型
     * @return xlsx 文件字节
     */
    public static <T> byte[] export(List<T> list, Class<T> clazz) {
        return export(list, clazz, null);
    }

    /**
     * 将列表数据导出为 xlsx 字节（带字典映射重载，供 ImportExportService 传映射）。
     * <p>dict 字段按 dictMappings 转换为显示值；映射中无对应项时记 warn 日志
     * （字段名 / 码值 / 字典名）后原样写码值（不失败、不抛错）。</p></p>
     *
     * @param list         数据列表；null 或空列表时仅含标题行
     * @param clazz        带 @Export 注解的导出 VO 类
     * @param dictMappings 键 = @Export.dict 的字典名（type_code），
     *                     值 = 码值→显示值映射（field_value→field_name）；null 视为无映射
     * @param <T>          VO 类型
     * @return xlsx 文件字节
     */
    public static <T> byte[] export(List<T> list, Class<T> clazz, Map<String, Map<String, String>> dictMappings) {
        List<ColumnMeta> columns = resolveColumns(clazz);
        List<String> titles = titlesOf(columns);
        List<List<String>> data = new ArrayList<>();
        if (null != list) {
            for (T item : list) {
                List<String> line = new ArrayList<>(columns.size());
                for (ColumnMeta column : columns) {
                    line.add(cellTextOf(item, column, dictMappings));
                }
                data.add(line);
            }
        }
        return writeSheet(titles, data);
    }

    /**
     * 将 xlsx 输入流解析为 VO 列表（固定 VO 通道）。
     * <p>第一行标题与 @Export 标题逐一比对，缺失列 / 多余列 / 标题文本
     * 不一致均抛 {@link IllegalArgumentException}（消息含具体差异）；
     * 通过后按「标题文本 → 字段」映射解析，不依赖固定列下标（列序变化
     * 可容忍，只要标题集合一致）。</p>
     *
     * @param in    xlsx 输入流
     * @param clazz 带 @Export 注解的导出 VO 类
     * @param <T>   VO 类型
     * @return 解析出的 VO 列表（文件无数据行时为空列表）
     */
    public static <T> List<T> explain(InputStream in, Class<T> clazz) {
        List<RowData<T>> rows = explainWithRows(in, clazz);
        List<T> result = new ArrayList<>(rows.size());
        for (RowData<T> rowData : rows) {
            result.add(rowData.getRow());
        }
        return result;
    }

    /**
     * 解析 xlsx 为带行上下文的列表（固定 VO 通道）。
     * <p>表头比对口径与 {@link #explain(InputStream, Class)} 完全一致（第一行标题
     * 与 @Export 标题逐一比对，缺失列 / 多余列 / 标题文本不一致均抛
     * {@link IllegalArgumentException}）；按「标题文本 → 字段」映射解析，不依赖
     * 固定列下标。除解析后的 VO 外，同步返回文件行号（从 2 起，1 为标题行）与
     * 各 @Export 列的原始单元格文本（String 字段已赋值到 VO，非 String 字段
     * 保持 null，原始文本供编排层做必填 / 日期校验与时间对象回写）。</p>
     *
     * @param in    xlsx 输入流
     * @param clazz 带 @Export 注解的导入 VO 类
     * @param <T>   VO 类型
     * @return 带行上下文的解析结果列表（文件无数据行时为空列表）
     */
    public static <T> List<RowData<T>> explainWithRows(InputStream in, Class<T> clazz) {
        List<ColumnMeta> columns = resolveColumns(clazz);
        List<Map<Integer, String>> rows = readAllRows(in);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("导入文件为空，缺少标题行");
        }
        List<String> expectedTitles = titlesOf(columns);
        checkHeader(expectedTitles, rows.get(0));
        // 标题文本 → 文件实际列下标（同名标题取先出现者）
        Map<String, Integer> titleIndex = indexTitles(rows.get(0));
        List<RowData<T>> result = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            Map<Integer, String> raw = rows.get(i);
            T bean = newInstance(clazz);
            Map<String, String> rawByField = new LinkedHashMap<>();
            for (ColumnMeta column : columns) {
                Integer col = titleIndex.get(column.title);
                if (null == col) {
                    // 表头已校验通过，不会走到这里；防御性跳过避免串列
                    continue;
                }
                String text = raw.get(col);
                String normalized = null == text ? "" : text;
                setFieldValue(bean, column.field, normalized);
                rawByField.put(column.field.getName(), normalized);
            }
            // Excel 行号：表头是第 1 行，数据行下标 i（0 起）对应第 i + 1 行（从 2 起）
            result.add(new RowData<>(bean, i + 1, rawByField));
        }
        return result;
    }

    /**
     * 解析结果：VO + 文件行号 + 各列原始文本（供编排层做时间 / 必填校验与时间对象回写）。
     *
     * @param <T> 解析后的 VO 类型
     */
    public static class RowData<T> {

        /** 解析后的 VO（String 字段已赋值，Date / LocalDateTime 等非 String 字段保持 null） */
        private final T row;

        /** 文件行号，从 2 起（1 为标题行） */
        private final int rowNo;

        /** @Export 字段名（Java 字段名）→ 原始单元格文本（缺失 / null 单元格归一为空串） */
        private final Map<String, String> rawByField;

        /**
         * 全参构造
         *
         * @param row        解析后的 VO
         * @param rowNo      文件行号，从 2 起（1 为标题行）
         * @param rawByField @Export 字段名 → 原始单元格文本
         */
        public RowData(T row, int rowNo, Map<String, String> rawByField) {
            this.row = row;
            this.rowNo = rowNo;
            this.rawByField = rawByField;
        }

        /** 获取解析后的 VO */
        public T getRow() {
            return row;
        }

        /** 获取文件行号（从 2 起，1 为标题行） */
        public int getRowNo() {
            return rowNo;
        }

        /** 获取 @Export 字段名 → 原始单元格文本映射 */
        public Map<String, String> getRawByField() {
            return rawByField;
        }
    }

    /**
     * 生成仅含标题行的空模板 xlsx 字节（固定 VO 通道）。
     *
     * @param clazz 带 @Export 注解的导出 VO 类
     * @param <T>   VO 类型
     * @return 仅含标题行的 xlsx 文件字节
     */
    public static <T> byte[] template(Class<T> clazz) {
        return writeSheet(titlesOf(resolveColumns(clazz)), new ArrayList<>());
    }

    /* ==================== 自定义字段 Map 通道 ==================== */

    /**
     * 自定义字段导出：列元数据由 modelMap 运行时给定，返回 xlsx 字节。
     * <p>列序 = modelMap 顺序；标题 = 各项 name（空则取 field）；数据行
     * 按 field 取值，key 缺失或值为 null 写空串；modelMap 未声明的 key
     * 不导出（隐式列过滤）；list 为空或 null 时仅标题行。</p>
     *
     * @param list     行数据（key = 自定义字段 field）
     * @param modelMap 列元数据，按列序排列，每项含 field/name（required 可选）
     * @return xlsx 文件字节
     */
    public static byte[] export(List<Map<String, String>> list, List<Map<String, String>> modelMap) {
        List<MapChannelColumn> columns = resolveMapColumns(modelMap);
        List<List<String>> data = new ArrayList<>();
        if (null != list) {
            for (Map<String, String> row : list) {
                List<String> line = new ArrayList<>(columns.size());
                for (MapChannelColumn column : columns) {
                    String value = null == row ? null : row.get(column.field);
                    line.add(null == value ? "" : value);
                }
                data.add(line);
            }
        }
        return writeSheet(mapTitlesOf(columns), data);
    }

    /**
     * 自定义字段解析：按标题行文本与 field 映射读入，返回以 field 为 key 的行列表。
     * 表头与 modelMap 标题（name 空取 field）比对：声明列缺失或标题文本
     * 不一致抛 {@link IllegalArgumentException}（消息含缺失列）；文件多余列
     * 容忍、不抛错也不返回。required 为 "true"
     * （忽略大小写）的列，数据行值为 null 或空串时抛
     * {@link IllegalArgumentException}，消息含行号（从 2 起）与列名；
     * 返回 Map 仅含 modelMap 声明的 field（文件多余列不返回）。
     * modelMap 的 dict/dateFormat 键首版忽略不实现。</p>
     *
     * @param in       xlsx 输入流
     * @param modelMap 列元数据，每项含 field/name（required 可选，"true" 必填）
     * @return 以 field 为 key 的行列表
     */
    public static List<Map<String, String>> explain(InputStream in, List<Map<String, String>> modelMap) {
        List<MapChannelColumn> columns = resolveMapColumns(modelMap);
        List<Map<Integer, String>> rows = readAllRows(in);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("导入文件为空，缺少标题行");
        }
        List<String> expectedTitles = mapTitlesOf(columns);
        // Map 通道：缺列抛（无法解析且会误伤 required 校验），多余列容忍、仅不返回
        checkHeader(expectedTitles, rows.get(0), false);
        Map<String, Integer> titleIndex = indexTitles(rows.get(0));
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            Map<Integer, String> raw = rows.get(i);
            // Excel 行号：表头是第 1 行，数据行下标 i（0 起）对应第 i + 1 行（从 2 起）
            int rowNumber = i + 1;
            Map<String, String> line = new LinkedHashMap<>();
            for (MapChannelColumn column : columns) {
                Integer col = titleIndex.get(column.title);
                String text = (null == col) ? "" : raw.get(col);
                if (null == text) {
                    text = "";
                }
                if (column.required && text.isEmpty()) {
                    throw new IllegalArgumentException(
                            "导入文件第 " + rowNumber + " 行 [" + column.title + "] 为必填列，不能为空");
                }
                line.put(column.field, text);
            }
            result.add(line);
        }
        return result;
    }

    /* ==================== 反射辅助 ==================== */

    /**
     * 解析 VO 类上 @Export 注解，生成 Map 通道的 modelMap（键：field/name/required）。
     * <p>按列序（order 升序；order=-1 按声明顺序追加在有值字段之后；order 相同
     * 按声明顺序）返回 List&lt;Map&lt;String,String&gt;&gt;，每项必含 field（Java 字段名）
     * 与 name（注解 name，为空则与 field 同值）；required 口径：仅当注解
     * required = true 时放 "true"，其余情况不放该键。无 @Export 字段的类
     * 返回空列表，不抛错。</p>
     *
     * @param voClass 带 @Export 注解的导出 VO 类
     * @return modelMap（可能为空列表）
     */
    public static List<Map<String, String>> buildModelMap(Class<?> voClass) {
        List<Map<String, String>> modelMap = new ArrayList<>();
        for (ColumnMeta column : resolveColumns(voClass)) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("field", column.field.getName());
            item.put("name", column.title);
            if (column.required) {
                item.put("required", "true");
            }
            modelMap.add(item);
        }
        return modelMap;
    }

    /* ==================== 私有实现 ==================== */

    /** VO 列元数据：Java 字段 + 解析后的标题 + 是否必填 + 字典名 + 日期格式 */
    private static class ColumnMeta {
        final Field field;
        final String title;
        final boolean required;
        final String dict;
        final String dateFormat;

        ColumnMeta(Field field, String title, boolean required, String dict, String dateFormat) {
            this.field = field;
            this.title = title;
            this.required = required;
            this.dict = null == dict ? "" : dict;
            this.dateFormat = null == dateFormat ? "" : dateFormat;
        }
    }

    /** Map 通道列元数据：field + 标题 + 是否必填 */
    private static class MapChannelColumn {
        final String field;
        final String title;
        final boolean required;

        MapChannelColumn(String field, String title, boolean required) {
            this.field = field;
            this.title = title;
            this.required = required;
        }
    }

    /**
     * 解析类上 @Export 注解为列元数据：order 升序稳定排序（order=-1 视为
     * 最大值追加在后，组内保持声明顺序）；标题 name 为空取 Java 字段名。
     */
    private static List<ColumnMeta> resolveColumns(Class<?> clazz) {
        List<OrderedColumn> ordered = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (java.lang.reflect.Modifier.isStatic(modifiers) || java.lang.reflect.Modifier.isTransient(modifiers)) {
                continue;
            }
            Export anno = field.getAnnotation(Export.class);
            if (null == anno) {
                continue;
            }
            String title = anno.name().isEmpty() ? field.getName() : anno.name();
            ordered.add(new OrderedColumn(
                    new ColumnMeta(field, title, anno.required(), anno.dict(), anno.dateFormat()),
                    anno.order()));
        }
        // List.sort 稳定排序：order 相同保持 getDeclaredFields 的声明顺序
        ordered.sort(Comparator.comparingInt(OrderedColumn::sortKey));
        List<ColumnMeta> columns = new ArrayList<>(ordered.size());
        for (OrderedColumn item : ordered) {
            columns.add(item.meta);
        }
        return columns;
    }

    /** 带排序键的列：order=-1 视为最大值（追加在有值字段之后） */
    private static class OrderedColumn {
        final ColumnMeta meta;
        final int order;

        OrderedColumn(ColumnMeta meta, int order) {
            this.meta = meta;
            this.order = order;
        }

        int sortKey() {
            return order < 0 ? Integer.MAX_VALUE : order;
        }
    }

    /** modelMap → Map 通道列元数据：标题 name 空取 field；required 为 "true"（忽略大小写）生效 */
    private static List<MapChannelColumn> resolveMapColumns(List<Map<String, String>> modelMap) {
        List<MapChannelColumn> columns = new ArrayList<>();
        if (null == modelMap) {
            return columns;
        }
        for (Map<String, String> item : modelMap) {
            String field = item.get("field");
            if (null == field || field.isEmpty()) {
                continue;
            }
            String name = item.get("name");
            String title = (null == name || name.isEmpty()) ? field : name;
            String required = item.get("required");
            boolean mustFill = null != required && "true".equalsIgnoreCase(required.trim());
            columns.add(new MapChannelColumn(field, title, mustFill));
        }
        return columns;
    }

    /** VO 列标题列表（按列序） */
    private static List<String> titlesOf(List<ColumnMeta> columns) {
        List<String> titles = new ArrayList<>(columns.size());
        for (ColumnMeta column : columns) {
            titles.add(column.title);
        }
        return titles;
    }

    /** Map 通道列标题列表（按 modelMap 顺序） */
    private static List<String> mapTitlesOf(List<MapChannelColumn> columns) {
        List<String> titles = new ArrayList<>(columns.size());
        for (MapChannelColumn column : columns) {
            titles.add(column.title);
        }
        return titles;
    }

    /** 单元格文本：getter 取值 → 日期按 dateFormat 转 String → 字典映射转显示值（无映射原样码值） */
    private static String cellTextOf(Object bean, ColumnMeta column, Map<String, Map<String, String>> dictMappings) {
        String text = textOf(readProperty(bean, column.field), column.dateFormat);
        if (column.dict.isEmpty()) {
            return text;
        }
        Map<String, String> mapping = null == dictMappings ? null : dictMappings.get(column.dict);
        if (null == mapping) {
            return text;
        }
        String display = mapping.get(text);
        if (null == display) {
            // 映射中无对应项：原样写码值，不失败不抛错；空值单元格静默，非空码值记 warn 便于排查字典缺失
            if (null != text && !text.isEmpty()) {
                log.warn("导出码值转换: 字段 [{}] 的码值 [{}] 在字典 [{}] 中无对应启用项, 原样写出",
                        column.field.getName(), text, column.dict);
            }
            return text;
        }
        return display;
    }

    /** 读取属性值（getXxx / isXxx），无 getter 或读取失败返回 null */
    private static Object readProperty(Object bean, Field field) {
        String suffix = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
        Method getter = findGetter(bean.getClass(), suffix);
        if (null == getter) {
            return null;
        }
        try {
            return getter.invoke(bean);
        } catch (Exception e) {
            return null;
        }
    }

    private static Method findGetter(Class<?> type, String suffix) {
        try {
            return type.getMethod("get" + suffix);
        } catch (NoSuchMethodException ignored) {
            // 尝试 boolean 的 is 前缀
        }
        try {
            return type.getMethod("is" + suffix);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    /** 值转文本：null → 空串；Date / LocalDateTime 按 dateFormat 格式化；其余 String.valueOf */
    private static String textOf(Object value, String dateFormat) {
        if (null == value) {
            return "";
        }
        if (value instanceof Date date) {
            return new SimpleDateFormat(dateFormat.isEmpty() ? "yyyy-MM-dd HH:mm:ss" : dateFormat).format(date);
        }
        if (value instanceof LocalDateTime dateTime) {
            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern(dateFormat.isEmpty() ? "yyyy-MM-dd HH:mm:ss" : dateFormat);
            return dateTime.format(formatter);
        }
        return String.valueOf(value);
    }

    /** 按 VO 字段类型赋值：String 直接赋值；非 String 类型（Date/LocalDateTime 等）保持文本由上层校验，本层不强转 */
    private static void setFieldValue(Object bean, Field field, String text) {
        if (!field.getType().equals(String.class)) {
            return;
        }
        String suffix = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
        Method setter = findSetter(bean.getClass(), suffix, String.class);
        if (null == setter) {
            return;
        }
        try {
            setter.invoke(bean, text);
        } catch (Exception e) {
            throw new IllegalStateException("字段赋值失败: " + field.getName(), e);
        }
    }

    private static Method findSetter(Class<?> type, String suffix, Class<?> paramType) {
        try {
            return type.getMethod("set" + suffix, paramType);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static <T> T newInstance(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("无法实例化导入 VO: " + clazz.getName(), e);
        }
    }

    /**
     * EasyExcel 写出：head = 标题行（List&lt;List&lt;String&gt;&gt;），数据 = List&lt;List&lt;String&gt;&gt;。
     */
    private static byte[] writeSheet(List<String> titles, List<List<String>> data) {
        List<List<String>> head = new ArrayList<>(titles.size());
        for (String title : titles) {
            head.add(List.of(title));
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out).head(head).sheet("Sheet1").doWrite(data);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("导出文件写出失败", e);
        }
    }

    /** EasyExcel 读入全部行（headRowNumber(0)：第 0 行 = 表头行，由本类自行比对解析） */
    private static List<Map<Integer, String>> readAllRows(InputStream in) {
        try {
            return EasyExcel.read(in).sheet(0).headRowNumber(0).doReadSync();
        } catch (Exception e) {
            throw new IllegalArgumentException("导入文件解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 表头逐一比对（固定 VO 通道）：缺失列 / 多余列 / 标题文本不一致均抛
     * {@link IllegalArgumentException}，消息含具体差异（顺序无关，只比集合）。
     */
    private static void checkHeader(List<String> expectedTitles, Map<Integer, String> headRow) {
        checkHeader(expectedTitles, headRow, true);
    }

    /**
     * 表头逐一比对：缺失列一律抛（报缺失列名）；reportExtra = true 时多余列
     * 也抛（固定 VO 通道严格逐一比对），false 时多余列仅忽略不返回（Map 通道）。
     */
    private static void checkHeader(List<String> expectedTitles, Map<Integer, String> headRow, boolean reportExtra) {
        Set<String> expected = new LinkedHashSet<>(expectedTitles);
        Set<String> actual = new LinkedHashSet<>();
        for (String text : actualTitles(headRow)) {
            actual.add(text);
        }
        List<String> missing = new ArrayList<>();
        for (String title : expectedTitles) {
            if (!actual.contains(title)) {
                missing.add(title);
            }
        }
        List<String> extra = new ArrayList<>();
        for (String title : actual) {
            if (!expected.contains(title)) {
                extra.add(title);
            }
        }
        if (!missing.isEmpty() || (reportExtra && !extra.isEmpty())) {
            StringBuilder message = new StringBuilder("导入文件表头与模板不一致");
            if (!missing.isEmpty()) {
                message.append("，缺失列: ").append(missing);
            }
            if (!extra.isEmpty()) {
                message.append("，多余列: ").append(extra);
            }
            throw new IllegalArgumentException(message.toString());
        }
    }

    /** 文件实际标题（按列序的非空 trim 文本；同名重复列只保留首个用于比对） */
    private static List<String> actualTitles(Map<Integer, String> headRow) {
        int maxIndex = 0;
        for (Integer idx : headRow.keySet()) {
            if (null != idx && idx > maxIndex) {
                maxIndex = idx;
            }
        }
        List<String> titles = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int col = 0; col <= maxIndex; col++) {
            String text = headRow.get(col);
            text = null == text ? "" : text.trim();
            if (text.isEmpty() || !seen.add(text)) {
                continue;
            }
            titles.add(text);
        }
        return titles;
    }

    /** 标题文本 → 文件实际列下标（同名取先出现者） */
    private static Map<String, Integer> indexTitles(Map<Integer, String> headRow) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : headRow.entrySet()) {
            String text = entry.getValue();
            if (null == text) {
                continue;
            }
            text = text.trim();
            if (!text.isEmpty() && !index.containsKey(text)) {
                index.put(text, entry.getKey());
            }
        }
        return index;
    }
}
