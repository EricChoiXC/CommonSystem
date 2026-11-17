package com.eric.base.sys.datasource.spi;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.DataSourceField;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据源字段定义扫描器测试
 *
 * @author Eric
 * @date 2026/11/16
 * @description 覆盖任务 F18-BE-002A 验收标准（A8）：注解扫描归一（key 取 Java 字段名）、
 *              order 升序与父类链扫描、进程内缓存、条件字段 ops 非空且 ∈ SysDatasourceOp
 *              （归一大写）、属性名正则与 type/label 违例 fail-fast、公共字段 fieldId
 *              返回字段母集内建补齐固定最前、static 字段跳过。
 */
class SysDatasourceFieldScannerTest {

    // ---------------------------------------------------------------- 扫描桩类

    /**
     * 条件 Query 桩：显式 order、多操作符、小写操作符归一、type 推导
     * （String→STRING、BigDecimal/int→NUMBER、LocalDateTime→DATETIME）与
     * 显式声明优先、literalAllowed=false（A7）、未标注字段与 static 字段不进定义
     */
    static class SampleQuery {
        @DataSourceField(label = "标题", ops = "LIKE", order = 1)
        private String fieldName;

        @DataSourceField(label = "类型", ops = {"EQ", "NE"}, order = 2)
        private String fieldType;

        @DataSourceField(label = "状态", ops = "eq", order = 3)
        private String fieldStatus;

        @DataSourceField(label = "金额", ops = "GE", order = 4)
        private BigDecimal fieldAmount;

        @DataSourceField(label = "数量", ops = "GE", order = 5)
        private int fieldCount;

        @DataSourceField(label = "开始时间-起", ops = "GE", order = 6)
        private LocalDateTime fieldStartTimeStart;

        @DataSourceField(label = "业务日期", ops = "EQ", type = "DATE", order = 7)
        private String fieldBizDate;

        @DataSourceField(label = "创建人", ops = "EQ", literalAllowed = false, order = 8)
        private String fieldCreatorId;

        /** 未标注字段：不进条件白名单 */
        private String year;

        /** static 标注字段：跳过（若被扫描，缺 ops 会在条件校验抛异常） */
        @DataSourceField(label = "静态字段", order = 9)
        private static String staticField;
    }

    /** 返回 Vo 桩：继承 BaseModel，fieldId 内建补齐固定最前；java.util.Date 推导 DATETIME */
    static class SampleVo extends BaseModel {
        @DataSourceField(label = "标题", order = 1)
        private String fieldName;

        @DataSourceField(label = "开始时间", sortable = true, order = 2)
        private java.util.Date fieldStartTime;

        @DataSourceField(label = "金额", order = 3)
        private BigDecimal fieldAmount;
    }

    /** 父类链桩：父类标注字段随子类扫描输出 */
    static class ParentQuery {
        @DataSourceField(label = "父字段", ops = "EQ", order = 10)
        private String parentField;
    }

    static class ChildQuery extends ParentQuery {
        @DataSourceField(label = "子字段", ops = "LIKE", order = 1)
        private String childField;
    }

    /** 父子链同名标注字段：key 重复 fail-fast */
    static class DuplicateParent {
        @DataSourceField(label = "重复", ops = "EQ")
        private String fieldName;
    }

    static class DuplicateChild extends DuplicateParent {
        @DataSourceField(label = "重复", ops = "EQ")
        private String fieldName;
    }

    /** 条件字段缺 ops：fail-fast */
    static class MissingOpsQuery {
        @DataSourceField(label = "无操作符")
        private String fieldName;
    }

    /** 条件字段操作符非法：fail-fast */
    static class BadOpQuery {
        @DataSourceField(label = "非法操作符", ops = "NOT_AN_OP")
        private String fieldName;
    }

    /** 字段名违反属性名口径（大写字母开头）：fail-fast */
    static class BadNameQuery {
        @DataSourceField(label = "非法字段名", ops = "EQ")
        private String FieldName;
    }

    /** label 为空：fail-fast */
    static class BadLabelQuery {
        @DataSourceField(label = "", ops = "EQ")
        private String fieldName;
    }

    /** 显式 type 非法：fail-fast */
    static class BadTypeQuery {
        @DataSourceField(label = "非法类型", ops = "EQ", type = "TEXT")
        private String fieldName;
    }

    /** 自带 fieldId 标注的 Vo：不再内建补齐（不重复） */
    static class FieldIdAnnotatedVo extends BaseModel {
        @DataSourceField(label = "编号", order = 1)
        private String fieldId;

        @DataSourceField(label = "标题", order = 2)
        private String fieldName;
    }

    /** 未继承 BaseModel 的普通 Vo：无 getFieldId()，不补 fieldId */
    static class PlainVo {
        @DataSourceField(label = "标题", order = 1)
        private String fieldName;
    }

    // ---------------------------------------------------------------- 条件字段扫描

    @Test
    void conditionFieldsScanNormalizeAndSort() {
        List<SysDatasourceFieldDo> defs = SysDatasourceFieldScanner.conditionFields(SampleQuery.class);

        assertEquals(8, defs.size(), "未标注字段与 static 字段不进条件定义");
        assertEquals(List.of("fieldName", "fieldType", "fieldStatus", "fieldAmount",
                "fieldCount", "fieldStartTimeStart", "fieldBizDate", "fieldCreatorId"),
                defs.stream().map(SysDatasourceFieldDo::getKey).toList(), "按 order 升序输出");

        SysDatasourceFieldDo first = defs.get(0);
        assertEquals("标题", first.getLabel());
        assertEquals("STRING", first.getType(), "String 缺省推导 STRING");
        assertEquals(List.of("LIKE"), first.getOps());
        assertTrue(first.isLiteralAllowed());

        assertEquals(List.of("EQ", "NE"), defs.get(1).getOps(), "多操作符保留声明顺序");
        assertEquals(List.of("EQ"), defs.get(2).getOps(), "小写操作符归一为大写枚举名");
        assertEquals("NUMBER", defs.get(3).getType(), "BigDecimal 推导 NUMBER");
        assertEquals("NUMBER", defs.get(4).getType(), "基本类型 int 推导 NUMBER");
        assertEquals("DATETIME", defs.get(5).getType(), "LocalDateTime 推导 DATETIME");
        assertEquals("DATE", defs.get(6).getType(), "注解显式 type 优先于缺省推导");
        assertFalse(defs.get(7).isLiteralAllowed(), "A7 字段 literalAllowed=false 显式保留");
    }

    @Test
    void conditionFieldsScanAlongParentClassChain() {
        List<SysDatasourceFieldDo> defs = SysDatasourceFieldScanner.conditionFields(ChildQuery.class);

        assertEquals(List.of("childField", "parentField"), defs.stream()
                .map(SysDatasourceFieldDo::getKey).toList(), "父类链标注字段按 order 升序合并");
    }

    // ---------------------------------------------------------------- 返回字段扫描

    @Test
    void resultFieldsScanPrependCommonFieldId() {
        List<SysDatasourceFieldDo> defs = SysDatasourceFieldScanner.resultFields(SampleVo.class);

        assertEquals(4, defs.size(), "公共字段 fieldId 内建补齐");
        assertEquals("fieldId", defs.get(0).getKey(), "公共字段 fieldId 固定最前");
        assertEquals("ID", defs.get(0).getLabel());
        assertEquals("STRING", defs.get(0).getType());
        assertNull(defs.get(0).getOps(), "返回字段定义不承载 ops");
        assertFalse(defs.get(0).isSortable());

        assertEquals("fieldName", defs.get(1).getKey());
        assertEquals("DATETIME", defs.get(2).getType(), "java.util.Date 推导 DATETIME");
        assertTrue(defs.get(2).isSortable(), "sortable 标注保留供排序白名单");
        assertNull(defs.get(2).getOps(), "返回字段定义忽略 ops");
    }

    @Test
    void resultFieldsSkipPrependWhenFieldIdAnnotated() {
        List<SysDatasourceFieldDo> defs = SysDatasourceFieldScanner.resultFields(FieldIdAnnotatedVo.class);

        assertEquals(List.of("fieldId", "fieldName"), defs.stream()
                .map(SysDatasourceFieldDo::getKey).toList(), "已标注 fieldId 不再内建补齐");
    }

    @Test
    void resultFieldsSkipPrependWithoutFieldIdGetter() {
        List<SysDatasourceFieldDo> defs = SysDatasourceFieldScanner.resultFields(PlainVo.class);

        assertEquals(List.of("fieldName"), defs.stream()
                .map(SysDatasourceFieldDo::getKey).toList(), "无 getFieldId() 的类不补公共字段");
    }

    // ---------------------------------------------------------------- 缓存与不可变

    @Test
    void scanResultCachedAndImmutable() {
        List<SysDatasourceFieldDo> first = SysDatasourceFieldScanner.conditionFields(SampleQuery.class);
        List<SysDatasourceFieldDo> second = SysDatasourceFieldScanner.conditionFields(SampleQuery.class);
        assertSame(first, second, "条件字段定义进程内缓存只扫一次");

        List<SysDatasourceFieldDo> resultFirst = SysDatasourceFieldScanner.resultFields(SampleVo.class);
        assertSame(resultFirst, SysDatasourceFieldScanner.resultFields(SampleVo.class),
                "返回字段定义进程内缓存只扫一次");

        assertThrows(UnsupportedOperationException.class, () -> first.add(new SysDatasourceFieldDo()),
                "扫描结果不可变");
    }

    // ---------------------------------------------------------------- fail-fast 违例

    @Test
    void duplicateKeyAlongParentChainFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SysDatasourceFieldScanner.conditionFields(DuplicateChild.class));
        assertTrue(error.getMessage().contains("key 重复"), "父子链同名标注字段应报 key 重复");
    }

    @Test
    void conditionFieldWithoutOpsFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SysDatasourceFieldScanner.conditionFields(MissingOpsQuery.class));
        assertTrue(error.getMessage().contains("必须声明允许的操作符"));
    }

    @Test
    void conditionFieldWithIllegalOpFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SysDatasourceFieldScanner.conditionFields(BadOpQuery.class));
        assertTrue(error.getMessage().contains("操作符非法"));
    }

    @Test
    void fieldNameAgainstProjectorPatternFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SysDatasourceFieldScanner.conditionFields(BadNameQuery.class));
        assertTrue(error.getMessage().contains("属性名口径"), "key 取 Java 字段名须过 FieldProjector 正则");
    }

    @Test
    void emptyLabelFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SysDatasourceFieldScanner.conditionFields(BadLabelQuery.class));
        assertTrue(error.getMessage().contains("label 必填"));
    }

    @Test
    void illegalDeclaredTypeFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SysDatasourceFieldScanner.conditionFields(BadTypeQuery.class));
        assertTrue(error.getMessage().contains("type 非法"));
    }
}
