package com.eric.base.sys.datasource.spi;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;

/**
 * 数据源字段定义文档对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description Provider 声明的条件字段 / 返回字段元数据：条件字段供配置表单与执行期
 *              白名单校验（key、op、字面量限制），返回字段供默认全字段与投影白名单校验。
 */
@Data
public class SysDatasourceFieldDo {

    /** 字段名（Java 属性名，须匹配 [a-z][a-zA-Z0-9]*，与 FieldProjector 口径一致） */
    private String key;

    /** 显示名（配置表单与前端表头） */
    private String label;

    /** 值类型：STRING / NUMBER / DATE / DATETIME（Provider 负责类型转换） */
    private String type;

    /** 允许的操作符（SysDatasourceOp 名称）；返回字段定义时忽略 */
    private List<String> ops;

    /** 是否允许字面量值；false 时仅接受运行时变量（如 fieldCreatorId 只收 ${CURRENT_USER_ID}，防越权） */
    private boolean literalAllowed = true;

    /** 是否可作为排序列（供 /query sortField 白名单校验） */
    private boolean sortable = false;

    /**
     * 按 {@link #type} 转换条件值文本（样例代码 §2.5 说明）：
     * STRING 原样返回、NUMBER 转 BigDecimal、DATE（yyyy-MM-dd）转 java.sql.Date、
     * DATETIME（yyyy-MM-dd HH:mm:ss）转 java.sql.Timestamp（均为 java.util.Date 子类）；
     * 类型未知或为空按 STRING 处理。格式非法时抛运行时异常，
     * 由调用方包装为 CS_DATASOURCE_CONDITION_INVALID。
     *
     * @param value 条件值文本（字面量或运行时变量求值结果）
     * @return 类型转换后的值；value 为 null 或空文本时返回 null
     */
    public Object convert(String value) {
        if (null == value || value.isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        return switch (null == type ? "STRING" : type.toUpperCase()) {
            case "NUMBER" -> new BigDecimal(normalized);
            case "DATE" -> Date.valueOf(LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE));
            case "DATETIME" -> Timestamp.valueOf(LocalDateTime.parse(normalized,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            default -> value;
        };
    }
}
