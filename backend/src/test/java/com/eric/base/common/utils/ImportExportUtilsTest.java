package com.eric.base.common.utils;

import com.alibaba.excel.EasyExcel;
import com.eric.base.common.base.annotation.Export;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导入导出静态工具类测试（纯单测，无 Spring 上下文）
 *
 * @author Eric
 * @date 2026/11/9
 * @description 覆盖方案设计 12 节分支：注解解析与列序、标题回退、
 *              explain 表头逐一比对、码值往返、template 仅标题行、
 *              Map 通道导出/解析、buildModelMap。EasyExcel 全程在内存
 *              byte[] 上写读（ByteArrayInputStream/ByteArrayOutputStream），
 *              不落盘临时文件。
 */
class ImportExportUtilsTest {

    /* ==================== 测试 fixture：带 @Export 的静态内部 VO ==================== */

    /**
     * 列序 fixture：fieldCode(1) / fieldName(2) / fieldRemark(-1) / fieldMemo(-1)。
     * 期望列序 = [编码, 名称, fieldRemark, fieldMemo]（order=-1 按声明顺序追加在后）。
     */
    static class OrderVo {
        @Export(name = "名称", order = 2)
        private String fieldName;

        @Export(name = "编码", order = 1)
        private String fieldCode;

        @Export
        private String fieldRemark;

        @Export
        private String fieldMemo;

        public String getFieldName() {
            return fieldName;
        }

        public String getFieldCode() {
            return fieldCode;
        }

        public String getFieldRemark() {
            return fieldRemark;
        }

        public String getFieldMemo() {
            return fieldMemo;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public void setFieldCode(String fieldCode) {
            this.fieldCode = fieldCode;
        }

        public void setFieldRemark(String fieldRemark) {
            this.fieldRemark = fieldRemark;
        }

        public void setFieldMemo(String fieldMemo) {
            this.fieldMemo = fieldMemo;
        }
    }

    /** 声明顺序 fixture：order 全 -1 → 严格按声明顺序排列 */
    static class DeclaredOrderVo {
        @Export(name = "第二列")
        private String fieldSecond;

        @Export(name = "第一列")
        private String fieldFirst;

        public String getFieldSecond() {
            return fieldSecond;
        }

        public String getFieldFirst() {
            return fieldFirst;
        }

        public void setFieldFirst(String fieldFirst) {
            this.fieldFirst = fieldFirst;
        }
    }

    /** 相同 order fixture：order 相同按声明顺序稳定排序 */
    static class SameOrderVo {
        @Export(name = "A列", order = 1)
        private String fieldA;

        @Export(name = "B列", order = 1)
        private String fieldB;

        public String getFieldA() {
            return fieldA;
        }

        public String getFieldB() {
            return fieldB;
        }

        public void setFieldA(String fieldA) {
            this.fieldA = fieldA;
        }

        public void setFieldB(String fieldB) {
            this.fieldB = fieldB;
        }
    }

    /** 标题回退 fixture：name 为空取 Java 字段名驼峰原样 */
    static class NameFallbackVo {
        @Export
        private String fieldCode;

        public String getFieldCode() {
            return fieldCode;
        }
    }

    /** 码值转换 fixture：fieldStatus 走 sys_common_status 字典 */
    static class DictVo {
        @Export(name = "编码", order = 1)
        private String fieldCode;

        @Export(name = "状态", order = 2, dict = "sys_common_status")
        private String fieldStatus;

        public String getFieldCode() {
            return fieldCode;
        }

        public void setFieldCode(String fieldCode) {
            this.fieldCode = fieldCode;
        }

        public String getFieldStatus() {
            return fieldStatus;
        }

        public void setFieldStatus(String fieldStatus) {
            this.fieldStatus = fieldStatus;
        }
    }

    /** 无 @Export 字段的类：buildModelMap 应返回空列表不抛错 */
    static class PlainVo {
        private String fieldName;

        public String getFieldName() {
            return fieldName;
        }
    }

    /* ==================== fixture 辅助 ==================== */

    /** 构造 OrderVo 行：编码/名称固定有值，备注可指定（匿名子类覆盖 getter 以区分各行） */
    private static OrderVo orderVo(String code, String name, String remark) {
        return new OrderVo() {
            @Override
            public String getFieldRemark() {
                return remark;
            }

            @Override
            public String getFieldCode() {
                return code;
            }

            @Override
            public String getFieldName() {
                return name;
            }
        };
    }

    /* ==================== 固定 VO 通道：列序与标题 ==================== */

    @Test
    void exportOrdersByAnnotationOrder() {
        List<OrderVo> list = List.of(orderVo("C001", "张三", "备注"));
        List<List<String>> grid = readGrid(ImportExportUtils.export(list, OrderVo.class));

        // order 2/-1/-1/-1 → 期望列序：编码(1)、名称(2)、fieldRemark(-1 声明在前)、fieldMemo(-1)
        assertIterableEquals(List.of("编码", "名称", "fieldRemark", "fieldMemo"), grid.get(0));
        assertIterableEquals(List.of("C001", "张三", "备注", ""), grid.get(1));
    }

    @Test
    void exportAppendsUnorderedFieldsAfterOrderedOnes() {
        List<OrderVo> list = List.of(orderVo("C001", "张三", "备注"));
        List<List<String>> grid = readGrid(ImportExportUtils.export(list, OrderVo.class));

        // -1 字段追加在有值字段之后，且组内按声明顺序（fieldRemark 在 fieldMemo 前）
        assertIterableEquals(List.of("fieldRemark", "fieldMemo"), grid.get(0).subList(2, 4));
    }

    @Test
    void exportKeepsDeclaredOrderWhenOrderEquals() {
        List<List<String>> grid = readGrid(ImportExportUtils.export(List.of(new SameOrderVo()), SameOrderVo.class));

        // order 相同按声明顺序：A列在前、B列在后
        assertIterableEquals(List.of("A列", "B列"), grid.get(0));
    }

    @Test
    void exportKeepsDeclaredOrderWhenOrderUnset() {
        List<List<String>> grid =
                readGrid(ImportExportUtils.export(List.of(new DeclaredOrderVo()), DeclaredOrderVo.class));

        // order 全部未设置：严格按声明顺序
        assertIterableEquals(List.of("第二列", "第一列"), grid.get(0));
    }

    @Test
    void exportFallsBackToFieldNameWhenNameBlank() {
        List<List<String>> grid =
                readGrid(ImportExportUtils.export(List.of(new NameFallbackVo()), NameFallbackVo.class));

        // name 为空取 Java 字段名驼峰原样，不转下划线
        assertIterableEquals(List.of("fieldCode"), grid.get(0));
    }

    @Test
    void emptyListExportWritesHeaderOnly() {
        byte[] bytes = ImportExportUtils.export(new ArrayList<OrderVo>(), OrderVo.class);
        List<List<String>> grid = readGrid(bytes);

        // 空 list 导出：仅标题行（1 行）
        assertEquals(1, grid.size(), "空 list 仅含标题行");
        assertIterableEquals(List.of("编码", "名称", "fieldRemark", "fieldMemo"), grid.get(0));
    }

    /* ==================== 固定 VO 通道：explain 表头比对 ==================== */

    @Test
    void explainThrowsWhenHeaderMismatch() {
        // 构造标题不符的文件：标题文本与 @Export 声明不同（列数一致）
        List<Map<String, String>> wrongModel = List.of(
                mapOf("field", "fieldCode", "name", "错误标题一"),
                mapOf("field", "fieldName", "name", "错误标题二"),
                mapOf("field", "fieldRemark", "name", "fieldRemark"),
                mapOf("field", "fieldMemo", "name", "fieldMemo"));
        byte[] bytes = ImportExportUtils.export(List.of(mapOf("fieldCode", "C001", "fieldName", "张三",
                "fieldRemark", "r", "fieldMemo", "m")), wrongModel);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ImportExportUtils.explain(new ByteArrayInputStream(bytes), OrderVo.class),
                "表头与 @Export 标题不一致应抛 IllegalArgumentException");
        assertTrue(ex.getMessage().contains("不一致"), "异常消息应含差异说明: " + ex.getMessage());
    }

    @Test
    void explainThrowsWhenColumnMissing() {
        // 缺一列的文件（仅 3 列）
        List<Map<String, String>> missingModel = List.of(
                mapOf("field", "fieldCode", "name", "编码"),
                mapOf("field", "fieldName", "name", "名称"),
                mapOf("field", "fieldRemark", "name", "fieldRemark"));
        byte[] bytes = ImportExportUtils.export(List.of(
                mapOf("fieldCode", "C001", "fieldName", "张三", "fieldRemark", "r")), missingModel);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ImportExportUtils.explain(new ByteArrayInputStream(bytes), OrderVo.class),
                "缺列应抛 IllegalArgumentException 并报缺失列");
        assertTrue(ex.getMessage().contains("fieldMemo"), "异常消息应含缺失列名: " + ex.getMessage());
    }

    @Test
    void explainThrowsWhenExtraColumnPresent() {
        // 多一列的文件
        List<Map<String, String>> extraModel = List.of(
                mapOf("field", "fieldCode", "name", "编码"),
                mapOf("field", "fieldName", "name", "名称"),
                mapOf("field", "fieldRemark", "name", "fieldRemark"),
                mapOf("field", "fieldMemo", "name", "fieldMemo"),
                mapOf("field", "fieldExtra", "name", "多余列"));
        byte[] bytes = ImportExportUtils.export(List.of(mapOf(
                "fieldCode", "C001", "fieldName", "张三", "fieldRemark", "r",
                "fieldMemo", "m", "fieldExtra", "x")), extraModel);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ImportExportUtils.explain(new ByteArrayInputStream(bytes), OrderVo.class),
                "多列应抛 IllegalArgumentException 并报多余列");
        assertTrue(ex.getMessage().contains("多余列"), "异常消息应含多余列说明: " + ex.getMessage());
    }

    @Test
    void explainParsesByTitleRegardlessOfColumnOrder() {
        // 两份标题集合一致但列序来源不同的文件互相解析：均按标题匹配，不依赖固定下标
        byte[] normal = ImportExportUtils.export(List.of(orderVo("C001", "张三", "备注A")), OrderVo.class);

        // 用 modelMap 写出列序打乱（标题集合一致）的文件：备注 → 名称 → 编码
        List<Map<String, String>> shuffledModel = List.of(
                mapOf("field", "fieldRemark", "name", "fieldRemark"),
                mapOf("field", "fieldMemo", "name", "fieldMemo"),
                mapOf("field", "fieldName", "name", "名称"),
                mapOf("field", "fieldCode", "name", "编码"));
        byte[] shuffled = ImportExportUtils.export(
                List.of(mapOf("fieldCode", "C002", "fieldName", "李四", "fieldRemark", "备注B", "fieldMemo", "m")),
                shuffledModel);

        List<OrderVo> parsedFromNormal = ImportExportUtils.explain(new ByteArrayInputStream(normal), OrderVo.class);
        assertEquals(1, parsedFromNormal.size());
        assertEquals("C001", parsedFromNormal.get(0).getFieldCode());
        assertEquals("张三", parsedFromNormal.get(0).getFieldName());
        assertEquals("备注A", parsedFromNormal.get(0).getFieldRemark());
        assertEquals("", parsedFromNormal.get(0).getFieldMemo());

        // 列序打乱的文件同样可解析（标题集合一致即可）
        List<OrderVo> parsedFromShuffled = ImportExportUtils.explain(new ByteArrayInputStream(shuffled), OrderVo.class);
        assertEquals(1, parsedFromShuffled.size());
        assertEquals("C002", parsedFromShuffled.get(0).getFieldCode());
        assertEquals("李四", parsedFromShuffled.get(0).getFieldName());
        assertEquals("备注B", parsedFromShuffled.get(0).getFieldRemark());
        // shuffled 文件数据行显式含 fieldMemo="m"（文件列位第 2、VO 声明序第 4），解析得 "m" 恰证明按标题匹配、不依赖下标
        assertEquals("m", parsedFromShuffled.get(0).getFieldMemo());
    }

    @Test
    void explainRoundTripsExportedFile() {
        List<OrderVo> source = List.of(orderVo("C001", "张三", "备注"));
        byte[] bytes = ImportExportUtils.export(source, OrderVo.class);

        List<OrderVo> parsed = ImportExportUtils.explain(new ByteArrayInputStream(bytes), OrderVo.class);
        assertEquals(1, parsed.size());
        assertEquals("C001", parsed.get(0).getFieldCode());
        assertEquals("张三", parsed.get(0).getFieldName());
        assertEquals("备注", parsed.get(0).getFieldRemark());
        assertEquals("", parsed.get(0).getFieldMemo());
    }

    /* ==================== 固定 VO 通道：码值往返与 template ==================== */

    @Test
    void exportConvertsDictValueWithMapping() {
        DictVo vo = new DictVo();
        vo.setFieldCode("C001");
        vo.setFieldStatus("1");
        Map<String, Map<String, String>> dictMappings =
                Map.of("sys_common_status", Map.of("1", "正常"));
        byte[] bytes = ImportExportUtils.export(List.of(vo), DictVo.class, dictMappings);

        // dict 字段按映射转为显示值
        List<List<String>> grid = readGrid(bytes);
        assertIterableEquals(List.of("编码", "状态"), grid.get(0));
        assertIterableEquals(List.of("C001", "正常"), grid.get(1));
    }

    @Test
    void exportWritesRawCodeWhenMappingAbsent() {
        DictVo vo = new DictVo();
        vo.setFieldCode("C001");
        vo.setFieldStatus("9");
        // 映射无 "9" 对应项：原样写码值，不失败不抛错
        Map<String, Map<String, String>> dictMappings =
                Map.of("sys_common_status", Map.of("1", "正常"));
        byte[] bytes = ImportExportUtils.export(List.of(vo), DictVo.class, dictMappings);

        List<List<String>> grid = readGrid(bytes);
        assertIterableEquals(List.of("C001", "9"), grid.get(1));
    }

    @Test
    void exportWritesRawCodeWhenDictMappingMissing() {
        DictVo vo = new DictVo();
        vo.setFieldCode("C001");
        vo.setFieldStatus("1");
        // dictMappings 为 null：全部原样写码值
        byte[] bytes = ImportExportUtils.export(List.of(vo), DictVo.class, null);

        List<List<String>> grid = readGrid(bytes);
        assertIterableEquals(List.of("C001", "1"), grid.get(1));
    }

    @Test
    void templateContainsHeaderRowOnly() {
        byte[] bytes = ImportExportUtils.template(OrderVo.class);
        List<List<String>> grid = readGrid(bytes);

        assertEquals(1, grid.size(), "template 仅含标题行");
        assertIterableEquals(List.of("编码", "名称", "fieldRemark", "fieldMemo"), grid.get(0));

        // template 可直接回读为空列表（表头校验通过、无数据行）
        List<OrderVo> parsed = ImportExportUtils.explain(new ByteArrayInputStream(bytes), OrderVo.class);
        assertTrue(parsed.isEmpty());
    }

    /* ==================== Map 通道 ==================== */

    /** Map 通道 modelMap fixture：field/name/required */
    private static List<Map<String, String>> mapModel() {
        return List.of(
                mapOf("field", "fieldCode", "name", "编码", "required", "true"),
                mapOf("field", "fieldName", "name", "名称"),
                mapOf("field", "fieldRemark", "name", "备注"));
    }

    @Test
    void mapChannelExportFollowsModelMapOrderAndFiltersImplicitKeys() {
        // 行数据含 modelMap 未声明的 fieldSecret：不应出现在文件中（隐式列过滤）
        Map<String, String> row = new LinkedHashMap<>();
        row.put("fieldCode", "C001");
        row.put("fieldSecret", "机密");
        row.put("fieldName", "张三");
        row.put("fieldRemark", "备注");
        byte[] bytes = ImportExportUtils.export(List.of(row), mapModel());

        List<List<String>> grid = readGrid(bytes);
        // 列序 = modelMap 顺序；隐式列被过滤
        assertIterableEquals(List.of("编码", "名称", "备注"), grid.get(0));
        assertIterableEquals(List.of("C001", "张三", "备注"), grid.get(1));
    }

    @Test
    void mapChannelExportWritesEmptyStringWhenKeyMissing() {
        // 行数据缺 fieldRemark：写空串
        byte[] bytes =
                ImportExportUtils.export(List.of(mapOf("fieldCode", "C001", "fieldName", "张三")), mapModel());

        List<List<String>> grid = readGrid(bytes);
        assertIterableEquals(List.of("C001", "张三", ""), grid.get(1));
    }

    @Test
    void mapChannelEmptyListWritesHeaderOnly() {
        byte[] bytes = ImportExportUtils.export(new ArrayList<Map<String, String>>(), mapModel());
        List<List<String>> grid = readGrid(bytes);

        assertEquals(1, grid.size(), "空 list 仅标题行");
        assertIterableEquals(List.of("编码", "名称", "备注"), grid.get(0));
    }

    @Test
    void mapChannelExportUsesFieldAsTitleWhenNameBlank() {
        List<Map<String, String>> model = List.of(
                mapOf("field", "fieldCode", "name", ""),
                mapOf("field", "fieldName", "name", "名称"));
        byte[] bytes = ImportExportUtils.export(List.of(mapOf("fieldCode", "C001", "fieldName", "张三")), model);

        List<List<String>> grid = readGrid(bytes);
        // name 空（或缺失）回退 field 作为标题
        assertIterableEquals(List.of("fieldCode", "名称"), grid.get(0));
    }

    @Test
    void mapChannelExplainParsesByTitleWithShuffledColumns() {
        // 文件列序与 modelMap 不同：仍按标题匹配解析
        List<Map<String, String>> shuffledModel = List.of(
                mapOf("field", "fieldRemark", "name", "备注"),
                mapOf("field", "fieldName", "name", "名称"),
                mapOf("field", "fieldCode", "name", "编码"));
        byte[] bytes = ImportExportUtils.export(List.of(
                mapOf("fieldCode", "C001", "fieldName", "张三", "fieldRemark", "备注A")), shuffledModel);

        List<Map<String, String>> parsed = ImportExportUtils.explain(new ByteArrayInputStream(bytes), mapModel());
        assertEquals(1, parsed.size());
        // 返回 Map 仅含 modelMap 声明的 field（无多余列时全量返回）
        assertIterableEquals(List.of("fieldCode", "fieldName", "fieldRemark"),
                List.copyOf(parsed.get(0).keySet()));
        assertEquals("C001", parsed.get(0).get("fieldCode"));
        assertEquals("张三", parsed.get(0).get("fieldName"));
        assertEquals("备注A", parsed.get(0).get("fieldRemark"));
    }

    @Test
    void mapChannelExplainThrowsWhenHeaderMismatch() {
        // 表头与 modelMap 不一致（modelMap 要求 3 列，文件只有 2 列）
        List<Map<String, String>> shortModel = List.of(
                mapOf("field", "fieldCode", "name", "编码"),
                mapOf("field", "fieldName", "name", "名称"));
        byte[] bytes =
                ImportExportUtils.export(List.of(mapOf("fieldCode", "C001", "fieldName", "张三")), shortModel);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ImportExportUtils.explain(new ByteArrayInputStream(bytes), mapModel()),
                "Map 通道表头不一致应抛 IllegalArgumentException");
        assertTrue(ex.getMessage().contains("备注") || ex.getMessage().contains("fieldRemark"),
                "异常消息应含缺失列: " + ex.getMessage());
    }

    @Test
    void mapChannelExplainThrowsOnEmptyRequiredCell() {
        byte[] bytes = ImportExportUtils.export(List.of(
                mapOf("fieldCode", "", "fieldName", "张三", "fieldRemark", "r"),
                mapOf("fieldCode", "C002", "fieldName", "李四", "fieldRemark", "r")), mapModel());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ImportExportUtils.explain(new ByteArrayInputStream(bytes), mapModel()),
                "required 列空串应抛 IllegalArgumentException");
        // 行号从 2 起计（表头是第 1 行），消息含行号与列名
        assertTrue(ex.getMessage().contains("2"), "异常消息应含行号 2: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("编码"), "异常消息应含列名: " + ex.getMessage());
    }

    @Test
    void mapChannelExplainRequiredIsCaseInsensitive() {
        // required = "TRUE"（大写）：同样生效
        List<Map<String, String>> upperModel = List.of(
                mapOf("field", "fieldCode", "name", "编码", "required", "TRUE"),
                mapOf("field", "fieldName", "name", "名称"));
        byte[] bytes = ImportExportUtils.export(List.of(
                mapOf("fieldCode", "", "fieldName", "张三")), upperModel);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ImportExportUtils.explain(new ByteArrayInputStream(bytes), upperModel),
                "required = \"TRUE\" 忽略大小写同样生效");
        assertTrue(ex.getMessage().contains("编码"));
    }

    @Test
    void mapChannelExplainAcceptsFilledRequiredCell() {
        byte[] bytes = ImportExportUtils.export(List.of(
                mapOf("fieldCode", "C001", "fieldName", "张三", "fieldRemark", "r")), mapModel());

        List<Map<String, String>> parsed = ImportExportUtils.explain(new ByteArrayInputStream(bytes), mapModel());
        assertEquals(1, parsed.size());
        assertEquals("C001", parsed.get(0).get("fieldCode"));
    }

    @Test
    void mapChannelExplainReturnsOnlyDeclaredFields() {
        // 文件含多余列：返回 Map 不含未声明 field
        List<Map<String, String>> fileModel = List.of(
                mapOf("field", "fieldCode", "name", "编码"),
                mapOf("field", "fieldName", "name", "名称"),
                mapOf("field", "fieldExtra", "name", "多余列"));
        byte[] bytes = ImportExportUtils.export(List.of(mapOf(
                "fieldCode", "C001", "fieldName", "张三", "fieldExtra", "x")), fileModel);
        List<Map<String, String>> model = List.of(
                mapOf("field", "fieldCode", "name", "编码"),
                mapOf("field", "fieldName", "name", "名称"));

        List<Map<String, String>> parsed = ImportExportUtils.explain(new ByteArrayInputStream(bytes), model);
        assertEquals(1, parsed.size());
        assertTrue(!parsed.get(0).containsKey("fieldExtra"), "多余列不返回");
        assertIterableEquals(List.of("fieldCode", "fieldName"), List.copyOf(parsed.get(0).keySet()));
    }

    /* ==================== buildModelMap ==================== */

    @Test
    void buildModelMapOrdersAndAppendsUnorderedFields() {
        List<Map<String, String>> modelMap = ImportExportUtils.buildModelMap(OrderVo.class);

        // 列序同导出规则；name 空回退 field
        assertEquals(4, modelMap.size());
        assertEquals("fieldCode", modelMap.get(0).get("field"));
        assertEquals("编码", modelMap.get(0).get("name"));
        assertEquals("fieldName", modelMap.get(1).get("field"));
        assertEquals("名称", modelMap.get(1).get("name"));
        assertEquals("fieldRemark", modelMap.get(2).get("field"));
        assertEquals("fieldRemark", modelMap.get(2).get("name"), "name 空回退 field 同值");
        assertEquals("fieldMemo", modelMap.get(3).get("field"));
    }

    @Test
    void buildModelMapKeepsDeclaredOrderWhenOrderUnset() {
        List<Map<String, String>> modelMap = ImportExportUtils.buildModelMap(DeclaredOrderVo.class);

        assertIterableEquals(List.of("fieldSecond", "fieldFirst"),
                List.of(modelMap.get(0).get("field"), modelMap.get(1).get("field")));
    }

    @Test
    void buildModelMapPutsRequiredOnlyWhenTrue() {
        List<Map<String, String>> modelMap = ImportExportUtils.buildModelMap(DictVo.class);

        // @Export 未声明 required：不放 required 键（Javadoc 口径）
        for (Map<String, String> item : modelMap) {
            assertTrue(!item.containsKey("required"), "未声明 required 的列不应含 required 键");
        }
    }

    @Test
    void buildModelMapReturnsEmptyListWithoutExportAnnotation() {
        List<Map<String, String>> modelMap = ImportExportUtils.buildModelMap(PlainVo.class);

        // 无 @Export 字段的类返回空列表不抛错
        assertTrue(modelMap.isEmpty());
    }

    /* ==================== explainWithRows:带行上下文解析 ==================== */

    @Test
    void explainWithRowsReturnsRowNoFromTwoAndRawTexts() {
        byte[] bytes = ImportExportUtils.export(List.of(
                orderVo("C001", "张三", "备注A"),
                orderVo("C002", "李四", "备注B")), OrderVo.class);

        List<ImportExportUtils.RowData<OrderVo>> rows =
                ImportExportUtils.explainWithRows(new ByteArrayInputStream(bytes), OrderVo.class);

        assertEquals(2, rows.size());
        // 文件行号从 2 起（1 为标题行）
        assertEquals(2, rows.get(0).getRowNo());
        assertEquals(3, rows.get(1).getRowNo());
        // rawByField 含全部 @Export 列的原始文本（按列序声明字段）
        Map<String, String> rawFirst = rows.get(0).getRawByField();
        assertEquals(4, rawFirst.size());
        assertEquals("C001", rawFirst.get("fieldCode"));
        assertEquals("张三", rawFirst.get("fieldName"));
        assertEquals("备注A", rawFirst.get("fieldRemark"));
        assertEquals("", rawFirst.get("fieldMemo"));
    }

    @Test
    void explainWithRowsRowMatchesExplainResult() {
        byte[] bytes = ImportExportUtils.export(List.of(orderVo("C001", "张三", "备注")), OrderVo.class);

        List<OrderVo> explained = ImportExportUtils.explain(new ByteArrayInputStream(bytes), OrderVo.class);
        List<ImportExportUtils.RowData<OrderVo>> withRows =
                ImportExportUtils.explainWithRows(new ByteArrayInputStream(bytes), OrderVo.class);

        // 抽取 row 与 explain 解析结果一致（String 字段赋值口径相同）
        assertEquals(1, explained.size());
        assertEquals(1, withRows.size());
        OrderVo row = withRows.get(0).getRow();
        assertEquals(explained.get(0).getFieldCode(), row.getFieldCode());
        assertEquals(explained.get(0).getFieldName(), row.getFieldName());
        assertEquals(explained.get(0).getFieldRemark(), row.getFieldRemark());
        assertEquals(explained.get(0).getFieldMemo(), row.getFieldMemo());
    }

    @Test
    void explainWithRowsEmptyDataReturnsEmptyList() {
        byte[] bytes = ImportExportUtils.template(OrderVo.class);
        List<ImportExportUtils.RowData<OrderVo>> rows =
                ImportExportUtils.explainWithRows(new ByteArrayInputStream(bytes), OrderVo.class);
        assertTrue(rows.isEmpty(), "template 仅标题行 → 无数据行返回空列表");
    }

    /* ==================== 读回辅助（测试自用） ==================== */

    private static Map<String, String> mapOf(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static List<Map<Integer, String>> explainRawRows(byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            // headRowNumber(0)：全部行读取（第 0 行 = 表头行），与工具类导入口径一致
            return EasyExcel.read(in).sheet(0).headRowNumber(0).doReadSync();
        } catch (Exception e) {
            throw new IllegalStateException("测试读回 xlsx 失败", e);
        }
    }

    private static List<List<String>> readGrid(byte[] bytes) {
        List<Map<Integer, String>> rows = explainRawRows(bytes);
        // 全表最大列号（以表头行为准）：空串单元格 EasyExcel 可能不写出 cell，需补齐
        int globalMax = 0;
        for (Map<Integer, String> row : rows) {
            for (Integer idx : row.keySet()) {
                if (null != idx && idx > globalMax) {
                    globalMax = idx;
                }
            }
        }
        List<List<String>> grid = new ArrayList<>();
        for (Map<Integer, String> row : rows) {
            List<String> line = new ArrayList<>();
            for (int col = 0; col <= globalMax; col++) {
                String text = row.get(col);
                line.add(null == text ? "" : text);
            }
            grid.add(line);
        }
        return grid;
    }
}
