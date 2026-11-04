package com.common.system.common.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 字段裁剪工具
 *
 * @author Eric
 * @date 2026/8/4
 * @description 按可见字段白名单将对象投影为 Map，只保留命中字段；
 *              值为 null 的字段不输出（不可见字段同样不输出，配合
 *              的 metadata 说明供前端渲染）。
 */
public final class FieldProjector {

    private FieldProjector() {
    }

    /**
     * 按可见字段白名单投影
     *
     * @param source        源对象
     * @param visibleFields 可见字段名（Java 属性名）；为空集合时返回所有字段
     * @return 仅含可见字段的 Map；嵌套对象（如 roles 列表）整体保留
     */
    public static Map<String, Object> project(Object source, Collection<String> visibleFields) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null || visibleFields == null) {
            return result;
        }
        for (String field : visibleFields) {
            Object value = readProperty(source, field);
            if (value != null) {
                result.put(field, value);
            }
        }
        return result;
    }

    /** 属性名格式：小写字母开头，仅含字母/数字，避免拼接出 getClass 等系统方法 */
    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("[a-z][a-zA-Z0-9]*");

    private static Object readProperty(Object source, String field) {
        // "class" 能拼出 Object.getClass()，显式排除系统方法
        if (field == null || "class".equals(field) || !FIELD_NAME_PATTERN.matcher(field).matches()) {
            return null;
        }
        try {
            Method getter = findGetter(source.getClass(), field);
            return getter == null ? null : getter.invoke(source);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    private static Method findGetter(Class<?> type, String field) {
        String suffix = Character.toUpperCase(field.charAt(0)) + field.substring(1);
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
}
