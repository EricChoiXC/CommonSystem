package com.eric.base.sys.datasource.spi;

import com.eric.base.sys.datasource.enums.SysDatasourceOp;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 数据源字段定义扫描器
 *
 * @author Eric
 * @date 2026/11/16
 * @description 扫描标注 {@link DatasourceField} 的类并归一为 {@link SysDatasourceFieldDo}（A8）：
 *              Query 类经 {@link #conditionFields} 出条件字段定义、Vo 类经 {@link #resultFields}
 *              出返回字段定义。key 取 Java 字段名（FieldProjector 属性名正则校验，违例 fail-fast），
 *              按 order 升序输出，进程内缓存只扫一次；返回字段扫描时类层级存在 getFieldId()
 *              且未覆盖定义的，自动补公共字段 fieldId 标准定义（BaseModel 声明在 common 层，
 *              不得反向依赖本包注解，A8 公共字段规则）。
 */
public final class SysDatasourceFieldScanner {

    /** 属性名口径与 FieldProjector 一致：小写字母开头，仅含字母/数字 */
    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("[a-z][a-zA-Z0-9]*");

    /** 合法值类型（与 SysDatasourceFieldDo#convert 支持的 type 对齐） */
    private static final Set<String> VALID_TYPES = Set.of("STRING", "NUMBER", "DATE", "DATETIME");

    /** 条件字段定义缓存（key=Query 类） */
    private static final Map<Class<?>, List<SysDatasourceFieldDo>> CONDITION_CACHE = new ConcurrentHashMap<>();

    /** 返回字段定义缓存（key=Vo 类） */
    private static final Map<Class<?>, List<SysDatasourceFieldDo>> RESULT_CACHE = new ConcurrentHashMap<>();

    private SysDatasourceFieldScanner() {
    }

    /**
     * 扫描条件字段定义（Query 类）：ops 为空或含非法 SysDatasourceOp 名 fail-fast
     *
     * @param queryClass 查询条件类
     * @return 条件字段定义（不可变，按 order 升序）
     */
    public static List<SysDatasourceFieldDo> conditionFields(Class<?> queryClass) {
        return CONDITION_CACHE.computeIfAbsent(queryClass, clazz -> scan(clazz, true));
    }

    /**
     * 扫描返回字段定义（Vo 类）：sortable 参与 /query 排序白名单
     *
     * @param voClass 结果 VO 类
     * @return 返回字段定义（不可变，按 order 升序，公共字段 fieldId 固定最前）
     */
    public static List<SysDatasourceFieldDo> resultFields(Class<?> voClass) {
        return RESULT_CACHE.computeIfAbsent(voClass, clazz -> scan(clazz, false));
    }

    /* ==================== 内部 ==================== */

    /**
     * 扫描归一：沿父类链取 {@code @DatasourceField} 字段（跳过 static/synthetic），
     * 按 order 排序后转换为 SysDatasourceFieldDo；key 重复 fail-fast
     */
    private static List<SysDatasourceFieldDo> scan(Class<?> clazz, boolean condition) {
        List<Def> defs = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Class<?> type = clazz; null != type && !Object.class.equals(type); type = type.getSuperclass()) {
            for (Field candidate : type.getDeclaredFields()) {
                DatasourceField annotation = candidate.getAnnotation(DatasourceField.class);
                if (null == annotation || candidate.isSynthetic() || Modifier.isStatic(candidate.getModifiers())) {
                    continue;
                }
                if (!keys.add(candidate.getName())) {
                    throw new IllegalStateException("数据源字段定义 key 重复: "
                            + clazz.getName() + "#" + candidate.getName());
                }
                defs.add(new Def(annotation.order(), toFieldDo(candidate, annotation, condition)));
            }
        }
        // 公共字段 fieldId 由 BaseModel 声明（common 层不可标注 spi 注解），返回字段母集内建补齐并固定最前
        if (!condition && !keys.contains("fieldId") && hasFieldIdGetter(clazz)) {
            defs.add(new Def(Integer.MIN_VALUE, commonFieldId()));
        }
        defs.sort(Comparator.comparingInt(Def::order));
        List<SysDatasourceFieldDo> result = new ArrayList<>(defs.size());
        for (Def def : defs) {
            result.add(def.fieldDo());
        }
        return List.copyOf(result);
    }

    /**
     * 注解 → SysDatasourceFieldDo：key=字段名（正则校验）、label 必填、type 空按 Java
     * 字段类型推导；条件字段校验 ops 非空且 ∈ SysDatasourceOp（归一为大写名，与
     * Service 白名单 contains(op.name()) 口径一致）；返回字段忽略 ops（与 FieldDo 语义一致）
     */
    private static SysDatasourceFieldDo toFieldDo(Field field, DatasourceField annotation, boolean condition) {
        if (!FIELD_NAME_PATTERN.matcher(field.getName()).matches()) {
            throw new IllegalStateException("数据源字段名不符合属性名口径 [a-z][a-zA-Z0-9]*: "
                    + field.getDeclaringClass().getName() + "#" + field.getName());
        }
        if (null == annotation.label() || annotation.label().isEmpty()) {
            throw new IllegalStateException("数据源字段 label 必填: " + field.getName());
        }
        SysDatasourceFieldDo def = new SysDatasourceFieldDo();
        def.setKey(field.getName());
        def.setLabel(annotation.label());
        def.setType(resolveType(annotation.type(), field));
        if (condition) {
            if (null == annotation.ops() || 0 == annotation.ops().length) {
                throw new IllegalStateException("数据源条件字段必须声明允许的操作符: " + field.getName());
            }
            List<String> ops = new ArrayList<>(annotation.ops().length);
            for (String op : annotation.ops()) {
                SysDatasourceOp resolved = SysDatasourceOp.of(op);
                if (null == resolved) {
                    throw new IllegalStateException("数据源条件字段操作符非法: " + field.getName() + " -> " + op);
                }
                ops.add(resolved.name());
            }
            def.setOps(List.copyOf(ops));
        }
        def.setLiteralAllowed(annotation.literalAllowed());
        def.setSortable(annotation.sortable());
        return def;
    }

    /**
     * type 推导：注解显式声明优先（须 ∈ 合法集）；缺省按 Java 字段类型——
     * 数值→NUMBER、java.sql.Date/LocalDate→DATE、java.util.Date（含 Timestamp）/
     * LocalDateTime→DATETIME、其余（String 等）→STRING
     */
    private static String resolveType(String declared, Field field) {
        if (null != declared && !declared.isEmpty()) {
            String normalized = declared.trim().toUpperCase();
            if (!VALID_TYPES.contains(normalized)) {
                throw new IllegalStateException("数据源字段 type 非法: " + field.getName() + " -> " + declared);
            }
            return normalized;
        }
        Class<?> type = field.getType();
        if (Number.class.isAssignableFrom(type)
                || (type.isPrimitive() && !boolean.class.equals(type) && !char.class.equals(type))) {
            return "NUMBER";
        }
        if (java.sql.Date.class.equals(type) || LocalDate.class.equals(type)) {
            return "DATE";
        }
        if (java.util.Date.class.isAssignableFrom(type) || LocalDateTime.class.equals(type)) {
            return "DATETIME";
        }
        return "STRING";
    }

    /** 类层级是否存在返回 String 的 getFieldId()（BaseModel 继承链探测） */
    private static boolean hasFieldIdGetter(Class<?> clazz) {
        try {
            return String.class.equals(clazz.getMethod("getFieldId").getReturnType());
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** 公共字段 fieldId 标准定义 */
    private static SysDatasourceFieldDo commonFieldId() {
        SysDatasourceFieldDo def = new SysDatasourceFieldDo();
        def.setKey("fieldId");
        def.setLabel("ID");
        def.setType("STRING");
        return def;
    }

    /**
     * 排序中间载体：order + 归一后定义
     */
    private record Def(int order, SysDatasourceFieldDo fieldDo) {
    }
}
