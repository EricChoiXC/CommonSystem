package com.eric.base.common.source.scanner;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.base.model.MainModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.DataSource;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import com.eric.base.common.source.model.ColumnDef;
import com.eric.base.common.source.model.IndexDef;
import com.eric.base.common.source.model.TableDef;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 表定义扫描器
 *
 * @author Eric
 * @date 2026/11/16
 * @description 从 classpath 扫描 com.eric.base 下标注 {@link TableSource} 的实体类
 *              （ClassPathScanningCandidateComponentProvider + AnnotationTypeFilter，
 *              不实例化 Bean），逐类沿层级归一为 {@link TableDef}（对齐 A8
 *              SysDatasourceFieldScanner 先例：fail-fast、进程内缓存扫一次、结果不可变）。
 *              <p>公共列模板按基类内建（设计方案 §3.3，不开放标注）：BaseModel → 仅
 *              field_id 主键固定最前；MainModel → field_id + 审计五列固定最后。列清单 =
 *              内建主键（最前）+ 显式标注业务列（沿类层级声明序，子类先于父类，单类内
 *              依 getDeclaredFields 返回序即 javac 声明序）+ 内建审计列（最后）。</p>
 *              <p>MainModel 的 fieldName / fieldCode 属业务列、不入模板（D5）：需要入列的
 *              实体在子类重声明同名字段并标注 {@link ColumnField}（字段遮蔽 + Lombok 读写器
 *              覆盖父类实现），子类重声明只取子类定义、父类同名字段不再入列，重声明 Java
 *              类型须与被遮蔽的父类字段一致。</p>
 *              <p>fail-fast 校验清单（设计方案 §3.5 九条）逐条落地，违例抛
 *              IllegalStateException 阻断启动：1 标识符正则与 field_ 前缀；2 表名全局唯一 /
 *              列名表内唯一 / 索引名表内唯一；3 comment 必填非空；4 STRING / DECIMAL 显式
 *              length ≥ 1；5 defaultValue 与 defaultFunction 互斥 + 字面量粗校验；6 索引列
 *              ⊆ 表列集合（含内建列）；7 必须继承 BaseModel / MainModel；8 禁止标注
 *              static / synthetic 字段；9 重声明字段类型一致。</p>
 */
public final class SourceTableScanner {

    /** 标识符白名单：表名 / 列名 / 索引名统一口径（DDL 无绑定变量，注入面由白名单收口） */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");

    /** 列名前缀（全工程规范） */
    private static final String COLUMN_PREFIX = "field_";

    /** 主键列名（公共列模板内建） */
    private static final String PRIMARY_KEY_COLUMN = "field_id";

    /** 扫描基包（全工程根包） */
    private static final String BASE_PACKAGE = "com.eric.base";

    /** 整数字面量（INT / BIGINT 默认值粗校验） */
    private static final Pattern INTEGER_LITERAL = Pattern.compile("[+-]?\\d+");

    /** 小数字面量（DECIMAL 默认值粗校验） */
    private static final Pattern DECIMAL_LITERAL = Pattern.compile("[+-]?\\d+(\\.\\d+)?");

    /** 时间字面量（DATE / DATETIME / TIMESTAMP 默认值粗校验，yyyy-MM-dd[ HH:mm:ss]） */
    private static final Pattern TIME_LITERAL = Pattern.compile("\\d{4}-\\d{2}-\\d{2}( \\d{2}:\\d{2}:\\d{2})?");

    /** 解析缓存（key=实体类，进程内只归一一次，违例不缓存重扫仍抛） */
    private static final Map<Class<?>, TableDef> PARSE_CACHE = new ConcurrentHashMap<>();

    /** classpath 全量扫描缓存（扫一次，结果不可变） */
    private static volatile List<TableDef> scanCache;

    private SourceTableScanner() {
    }

    /**
     * 解析单个实体类为 TableDef（fail-fast 校验 + 公共列模板 + 进程内缓存）
     *
     * @param clazz 标注 @TableSource 的实体类
     * @return 表定义（不可变）
     */
    public static TableDef parse(Class<?> clazz) {
        return PARSE_CACHE.computeIfAbsent(clazz, SourceTableScanner::doParse);
    }

    /**
     * 扫描给定实体类清单：逐类解析 + 表名全局唯一校验，按表名升序输出
     * （类发现可注入的归一入口，classpath 全量扫描 {@link #scan()} 复用）
     *
     * @param classes 实体类清单
     * @return 表定义清单（不可变，按表名升序）
     */
    public static List<TableDef> scan(List<Class<?>> classes) {
        Map<String, Class<?>> registered = new HashMap<>();
        List<TableDef> defs = new ArrayList<>(classes.size());
        for (Class<?> clazz : classes) {
            TableDef def = parse(clazz);
            Class<?> existing = registered.putIfAbsent(def.getName(), clazz);
            if (null != existing) {
                throw new IllegalStateException("表名全局唯一违例: " + def.getName()
                        + " 同时标注于 " + existing.getName() + " 与 " + clazz.getName());
            }
            defs.add(def);
        }
        defs.sort(Comparator.comparing(TableDef::getName));
        return List.copyOf(defs);
    }

    /**
     * classpath 全量扫描 com.eric.base 下标注 @TableSource 的实体类（进程内缓存扫一次）
     *
     * @return 表定义清单（不可变，按表名升序）
     */
    public static List<TableDef> scan() {
        List<TableDef> cached = scanCache;
        if (null != cached) {
            return cached;
        }
        synchronized (SourceTableScanner.class) {
            if (null == scanCache) {
                scanCache = scan(discoverClasses());
            }
            return scanCache;
        }
    }

    /* ==================== 内部 ==================== */

    /**
     * classpath 发现 com.eric.base 下标注 @TableSource 的实体类（不实例化 Bean，
     * 按字节码加载不触发初始化）
     */
    private static List<Class<?>> discoverClasses() {
        ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(TableSource.class));
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition candidate : provider.findCandidateComponents(BASE_PACKAGE)) {
            String className = candidate.getBeanClassName();
            try {
                classes.add(Class.forName(className, false, SourceTableScanner.class.getClassLoader()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("扫描发现的实体类无法加载: " + className, e);
            }
        }
        return classes;
    }

    /**
     * 逐类归一：fail-fast 校验（§3.5 九条）+ 公共列模板内建 + 索引归一
     */
    private static TableDef doParse(Class<?> clazz) {
        TableSource tableSource = clazz.getAnnotation(TableSource.class);
        if (null == tableSource) {
            throw new IllegalStateException("实体类未标注 @TableSource: " + clazz.getName());
        }
        if (!BaseModel.class.isAssignableFrom(clazz)) {
            // 规则 7：标注类必须继承 BaseModel / MainModel（决定公共列模板）
            throw new IllegalStateException("标注类必须继承 BaseModel/MainModel: " + clazz.getName());
        }
        if (!IDENTIFIER_PATTERN.matcher(tableSource.name()).matches()) {
            // 规则 1：表名标识符正则
            throw new IllegalStateException("表名不符合标识符规范 [a-z][a-z0-9_]*: "
                    + tableSource.name() + " (" + clazz.getName() + ")");
        }
        if (null == tableSource.comment() || tableSource.comment().isBlank()) {
            // 规则 3：表 COMMENT 必填非空
            throw new IllegalStateException("表 COMMENT 必填非空: " + clazz.getName());
        }
        DataSource dataSource = clazz.getAnnotation(DataSource.class);
        String dataSourceName = null == dataSource ? null : dataSource.name();

        // 公共列模板（§3.3）：内建列名先占位，业务列与内建列同名即规则 2 违例
        Set<String> columnNames = new HashSet<>();
        columnNames.add(PRIMARY_KEY_COLUMN);
        List<ColumnDef> auditColumns = MainModel.class.isAssignableFrom(clazz) ? builtinAuditColumns() : List.of();
        for (ColumnDef audit : auditColumns) {
            columnNames.add(audit.getName());
        }

        // 业务列：沿类层级（子类先于父类）收集 @ColumnField 字段
        List<ColumnDef> businessColumns = new ArrayList<>();
        Set<String> claimedFields = new HashSet<>();
        for (Class<?> type = clazz; null != type && !Object.class.equals(type); type = type.getSuperclass()) {
            for (Field candidate : type.getDeclaredFields()) {
                ColumnField annotation = candidate.getAnnotation(ColumnField.class);
                if (null == annotation) {
                    continue;
                }
                if (Modifier.isStatic(candidate.getModifiers()) || candidate.isSynthetic()) {
                    // 规则 8：static / synthetic 字段不允许标注
                    throw new IllegalStateException("@ColumnField 不允许标注在 static/synthetic 字段: "
                            + clazz.getName() + "#" + candidate.getName());
                }
                if (!claimedFields.add(candidate.getName())) {
                    // D5 字段遮蔽：子类重声明只取子类定义，父类同名字段不再入列
                    continue;
                }
                checkShadowedFieldType(clazz, candidate);
                ColumnDef def = toColumnDef(clazz, candidate, annotation);
                if (!columnNames.add(def.getName())) {
                    // 规则 2：列名表内唯一（含内建列）
                    throw new IllegalStateException("列名表内唯一违例: " + def.getName()
                            + " (" + clazz.getName() + "#" + candidate.getName() + ")");
                }
                businessColumns.add(def);
            }
        }

        // 列清单 = 内建主键（最前）+ 业务列（声明序）+ 内建审计列（最后）
        List<ColumnDef> columns = new ArrayList<>(1 + businessColumns.size() + auditColumns.size());
        columns.add(builtinFieldId());
        columns.addAll(businessColumns);
        columns.addAll(auditColumns);

        // 索引归一（规则 1/2/6）
        List<IndexDef> indexes = new ArrayList<>(tableSource.indexes().length);
        Set<String> indexNames = new HashSet<>();
        for (TableIndex index : tableSource.indexes()) {
            if (!IDENTIFIER_PATTERN.matcher(index.name()).matches()) {
                throw new IllegalStateException("索引名不符合标识符规范 [a-z][a-z0-9_]*: "
                        + index.name() + " (" + clazz.getName() + ")");
            }
            if (!indexNames.add(index.name())) {
                throw new IllegalStateException("索引名表内唯一违例: "
                        + index.name() + " (" + clazz.getName() + ")");
            }
            if (0 == index.columns().length) {
                throw new IllegalStateException("索引列不能为空: " + index.name() + " (" + clazz.getName() + ")");
            }
            for (String column : index.columns()) {
                if (!columnNames.contains(column)) {
                    // 规则 6：索引 columns ⊆ 该表列集合（含内建列）
                    throw new IllegalStateException("索引列必须是该表列集合的子集: " + index.name()
                            + " -> " + column + " (" + clazz.getName() + ")");
                }
            }
            indexes.add(new IndexDef(index.name(), List.of(index.columns()), index.unique(), index.type()));
        }
        return new TableDef(tableSource.name(), tableSource.comment(), dataSourceName, columns, indexes);
    }

    /**
     * 规则 9：重声明字段 Java 类型必须与被遮蔽的父类字段一致（沿父类链取最近同名声明，
     * 含未标注的 MainModel.fieldName / fieldCode）
     */
    private static void checkShadowedFieldType(Class<?> clazz, Field field) {
        for (Class<?> type = field.getDeclaringClass().getSuperclass();
             null != type && !Object.class.equals(type); type = type.getSuperclass()) {
            Field shadowed = findDeclaredField(type, field.getName());
            if (null != shadowed) {
                if (!shadowed.getType().equals(field.getType())) {
                    throw new IllegalStateException("重声明字段类型与被遮蔽的父类字段不一致: "
                            + clazz.getName() + "#" + field.getName()
                            + " (" + field.getType().getName() + " -> " + shadowed.getType().getName() + ")");
                }
                return;
            }
        }
    }

    /**
     * 注解 → ColumnDef：列名正则 + field_ 前缀（规则 1）、comment 必填非空（规则 3）、
     * STRING / DECIMAL 显式 length ≥ 1（规则 4）、defaultValue 与 defaultFunction 互斥
     * 及字面量粗校验（规则 5）；length / decimalLength 仅对应类型有意义，其余归一为 0
     */
    private static ColumnDef toColumnDef(Class<?> clazz, Field field, ColumnField annotation) {
        if (!IDENTIFIER_PATTERN.matcher(annotation.name()).matches()) {
            throw new IllegalStateException("列名不符合标识符规范 [a-z][a-z0-9_]*: " + annotation.name()
                    + " (" + clazz.getName() + "#" + field.getName() + ")");
        }
        if (!annotation.name().startsWith(COLUMN_PREFIX)) {
            throw new IllegalStateException("列名必须以 field_ 为前缀: " + annotation.name()
                    + " (" + clazz.getName() + "#" + field.getName() + ")");
        }
        if (null == annotation.comment() || annotation.comment().isBlank()) {
            throw new IllegalStateException("列 COMMENT 必填非空: " + annotation.name()
                    + " (" + clazz.getName() + "#" + field.getName() + ")");
        }
        boolean lengthMatters = ColumnType.STRING.equals(annotation.type())
                || ColumnType.DECIMAL.equals(annotation.type());
        if (lengthMatters && annotation.length() < 1) {
            throw new IllegalStateException(annotation.type() + " 列必须显式 length ≥ 1: " + annotation.name()
                    + " (" + clazz.getName() + "#" + field.getName() + ")");
        }
        int length = lengthMatters ? annotation.length() : 0;
        int decimalLength = ColumnType.DECIMAL.equals(annotation.type()) ? annotation.decimalLength() : 0;
        String defaultValue = null;
        if (!ColumnField.DEFAULT_NONE.equals(annotation.defaultValue())) {
            if (DefaultFunction.NONE != annotation.defaultFunction()) {
                throw new IllegalStateException("defaultValue 与 defaultFunction 互斥: " + annotation.name()
                        + " (" + clazz.getName() + "#" + field.getName() + ")");
            }
            checkDefaultValueLiteral(clazz, field, annotation);
            defaultValue = annotation.defaultValue();
        }
        return new ColumnDef(annotation.name(), annotation.comment(), annotation.type(), length, decimalLength,
                annotation.nullable(), defaultValue, annotation.defaultFunction(), false);
    }

    /**
     * 规则 5 字面量粗校验：数字列收数字（INT/BIGINT 整数、DECIMAL 允许小数）、
     * 时间列收 yyyy-MM-dd[ HH:mm:ss] 格式；其余类型不校验（含 BOOLEAN b'0'/b'1'）
     */
    private static void checkDefaultValueLiteral(Class<?> clazz, Field field, ColumnField annotation) {
        String literal = annotation.defaultValue();
        boolean matches = switch (annotation.type()) {
            case INT, BIGINT -> INTEGER_LITERAL.matcher(literal).matches();
            case DECIMAL -> DECIMAL_LITERAL.matcher(literal).matches();
            case DATE, DATETIME, TIMESTAMP -> TIME_LITERAL.matcher(literal).matches();
            default -> true;
        };
        if (!matches) {
            throw new IllegalStateException("defaultValue 字面量与列类型不匹配: " + annotation.name()
                    + " -> " + literal + " (" + clazz.getName() + "#" + field.getName() + ")");
        }
    }

    /**
     * 查找类中声明的同名字段（不触发访问校验外的任何行为）
     */
    private static Field findDeclaredField(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /**
     * 内建主键列（固定最前）：field_id VARCHAR(32) NOT NULL PRIMARY KEY，对齐全工程建表脚本
     */
    private static ColumnDef builtinFieldId() {
        return new ColumnDef(PRIMARY_KEY_COLUMN, "主键ID", ColumnType.STRING, 32, 0,
                false, null, DefaultFunction.NONE, true);
    }

    /**
     * 内建审计五列（固定最后，§3.3 评审修正口径，对齐 V1_01_046 与全工程建表脚本）：
     * 创建者 / 创建时间 DEFAULT CURRENT_TIMESTAMP、修改者 / 修改时间 NULL DEFAULT
     * CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP、启用位 BIT(1) NOT NULL DEFAULT b'1'
     */
    private static List<ColumnDef> builtinAuditColumns() {
        return List.of(
                new ColumnDef("field_creator_id", "创建者ID", ColumnType.STRING, 32, 0,
                        true, null, DefaultFunction.NONE, false),
                new ColumnDef("field_create_time", "创建时间", ColumnType.TIMESTAMP, 0, 0,
                        false, null, DefaultFunction.CURRENT_TIMESTAMP, false),
                new ColumnDef("field_updater_id", "修改者ID", ColumnType.STRING, 32, 0,
                        true, null, DefaultFunction.NONE, false),
                new ColumnDef("field_update_time", "修改时间", ColumnType.TIMESTAMP, 0, 0,
                        true, null, DefaultFunction.ON_UPDATE_CURRENT_TIMESTAMP, false),
                new ColumnDef("field_enable", "是否启用", ColumnType.BOOLEAN, 0, 0,
                        false, "b'1'", DefaultFunction.NONE, false));
    }
}
