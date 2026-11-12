package com.eric.base.common.base.service;

import com.alibaba.excel.EasyExcel;
import com.eric.base.common.base.annotation.Export;
import com.eric.base.common.base.controller.BaseCrudController;
import com.eric.base.common.base.model.BaseRequest;
import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.base.model.ImportResultVo;
import com.eric.base.common.base.resolver.BaseResolver;
import com.eric.base.common.utils.ImportExportUtils;
import com.eric.base.sys.dict.response.SysDictItemVo;
import com.eric.base.sys.dict.service.SysDictService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 导入导出公共编排层测试（纯单测，无 Spring 上下文）
 *
 * @author Eric
 * @date 2026/11/9
 * @description 覆盖 ImportExportService 导入编排五步（文件前置校验 / 解析 /
 *              通用校验（required、dateFormat、dict 回转）/ rowHandler 模块校验 /
 *              分批 insert 与结果汇总）、ignoreError 两态、fullUpdate 仅透传、
 *              export 码值转换与映射缺失原样写码值、template 仅标题行；
 *              并覆盖 BaseCrudController 导入导出公共通道守卫（三参构造委托 /
 *              四参构造 null Service → IllegalStateException，exportTemplate 不守卫，
 *              params 非法 JSON → IllegalArgumentException）。EasyExcel 全程在
 *              内存 byte[] 上写读，不落盘临时文件。
 */
@ExtendWith(MockitoExtension.class)
class ImportExportServiceTest {

    @Mock
    private SysDictService dictService;

    @Mock
    private IBaseService<TestEntity> baseService;

    @Mock
    private ImportRowHandler<TestImportVo> rowHandler;

    private ImportExportService service;

    @BeforeEach
    void setUp() {
        service = new ImportExportService(dictService);
    }

    /* ==================== 测试 fixture ==================== */

    /**
     * 导入导出 VO fixture：编码(必填, order=1) / 名称(order=2) /
     * 状态(dict=sys_common_status, order=3) / 创建时间(Date, order=4) /
     * 更新时间(LocalDateTime, order=5)。
     */
    // public 修饰：本类位于测试包，被 com.eric.base.common.utils.ImportExportUtils
    // 跨包反射实例化与 setter 赋值，包私有类会导致 IllegalAccessException
    public static class TestImportVo {

        public TestImportVo() {
        }

        @Export(name = "编码", order = 1, required = true)
        private String fieldCode;

        @Export(name = "名称", order = 2)
        private String fieldName;

        @Export(name = "状态", order = 3, dict = "sys_common_status")
        private String fieldStatus;

        @Export(name = "创建时间", order = 4)
        private Date fieldCreateTime;

        @Export(name = "更新时间", order = 5)
        private LocalDateTime fieldUpdateTime;

        public String getFieldCode() {
            return fieldCode;
        }

        public void setFieldCode(String fieldCode) {
            this.fieldCode = fieldCode;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getFieldStatus() {
            return fieldStatus;
        }

        public void setFieldStatus(String fieldStatus) {
            this.fieldStatus = fieldStatus;
        }

        public Date getFieldCreateTime() {
            return fieldCreateTime;
        }

        public void setFieldCreateTime(Date fieldCreateTime) {
            this.fieldCreateTime = fieldCreateTime;
        }

        public LocalDateTime getFieldUpdateTime() {
            return fieldUpdateTime;
        }

        public void setFieldUpdateTime(LocalDateTime fieldUpdateTime) {
            this.fieldUpdateTime = fieldUpdateTime;
        }
    }

    /** 测试实体 fixture：承接 VO 转换结果（供 ArgumentCaptor 断言） */
    static class TestEntity extends com.eric.base.common.base.model.BaseModel {

        private String fieldCode;

        private String fieldStatus;

        private Date fieldCreateTime;

        private LocalDateTime fieldUpdateTime;

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

        public Date getFieldCreateTime() {
            return fieldCreateTime;
        }

        public void setFieldCreateTime(Date fieldCreateTime) {
            this.fieldCreateTime = fieldCreateTime;
        }

        public LocalDateTime getFieldUpdateTime() {
            return fieldUpdateTime;
        }

        public void setFieldUpdateTime(LocalDateTime fieldUpdateTime) {
            this.fieldUpdateTime = fieldUpdateTime;
        }
    }

    /**
     * BaseCrudController 测试子类：两个构造分别走三参委托与四参注入。
     * <p>测试类与 BaseCrudController 不同包，protected 公共通道方法无法直接
     * 调用；此处覆写放宽为 public 并 super 委托（仅桥接，不改变行为）。</p>
     */
    static class TestCrudController extends
            BaseCrudController<TestEntity, IBaseService<TestEntity>, BaseResolver> {

        TestCrudController(IBaseService<TestEntity> baseService, BaseResolver baseResolver,
                           ApplicationEventPublisher eventPublisher) {
            super(baseService, baseResolver, eventPublisher);
        }

        TestCrudController(IBaseService<TestEntity> baseService, BaseResolver baseResolver,
                           ApplicationEventPublisher eventPublisher, ImportExportService importExportService) {
            super(baseService, baseResolver, eventPublisher, importExportService);
        }

        @Override
        public ResponseEntity<Resource> exportExcel(BaseRequest<?> request, String fileNamePrefix,
                Class<?> exportVoClass, Function<List<TestEntity>, List<?>> converter) {
            return super.exportExcel(request, fileNamePrefix, exportVoClass, converter);
        }

        @Override
        public <V> BaseResponse<ImportResultVo> importExcel(MultipartFile file, String params,
                Class<V> importVoClass, ImportRowHandler<V> rowHandler, Function<V, TestEntity> toEntity) {
            return super.importExcel(file, params, importVoClass, rowHandler, toEntity);
        }

        @Override
        public ResponseEntity<Resource> exportTemplate(String fileNamePrefix, Class<?> voClass) {
            return super.exportTemplate(fileNamePrefix, voClass);
        }
    }

    /* ==================== fixture 辅助 ==================== */

    /** 导入文件的 modelMap（与 VO 列序一致，不含 required 键——Map 通道 required 校验不参与） */
    private static List<Map<String, String>> importModelMap() {
        return List.of(
                mapOf("field", "fieldCode", "name", "编码"),
                mapOf("field", "fieldName", "name", "名称"),
                mapOf("field", "fieldStatus", "name", "状态"),
                mapOf("field", "fieldCreateTime", "name", "创建时间"),
                mapOf("field", "fieldUpdateTime", "name", "更新时间"));
    }

    /** 构造一行导入数据（空单元格写空串） */
    private static Map<String, String> importRow(String code, String name, String status,
                                                 String createTime, String updateTime) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("fieldCode", code);
        row.put("fieldName", name);
        row.put("fieldStatus", status);
        row.put("fieldCreateTime", createTime);
        row.put("fieldUpdateTime", updateTime);
        return row;
    }

    /** 生成导入 xlsx 字节（Map 通道写出，标题与 VO 声明一致） */
    private static byte[] importFile(List<Map<String, String>> rows) {
        return ImportExportUtils.export(rows, importModelMap());
    }

    /** 构造上传文件（文件名以 .xlsx 结尾） */
    private static MultipartFile uploadFile(byte[] bytes) {
        return new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    /** 构造码值项 */
    private static SysDictItemVo dictItem(String value, String name) {
        SysDictItemVo item = new SysDictItemVo();
        item.setFieldValue(value);
        item.setFieldName(name);
        return item;
    }

    /** stub 码值字典：sys_common_status → 1=启用、0=禁用 */
    private void stubDict() {
        when(dictService.itemsByTypeCode("sys_common_status"))
                .thenReturn(List.of(dictItem("1", "启用"), dictItem("0", "禁用")));
    }

    /** VO → 实体转换：拷贝四字段并收集 VO（供时间回写断言） */
    private Function<TestImportVo, TestEntity> toEntity(List<TestImportVo> convertedVos) {
        return vo -> {
            convertedVos.add(vo);
            TestEntity entity = new TestEntity();
            entity.setFieldCode(vo.getFieldCode());
            entity.setFieldStatus(vo.getFieldStatus());
            entity.setFieldCreateTime(vo.getFieldCreateTime());
            entity.setFieldUpdateTime(vo.getFieldUpdateTime());
            return entity;
        };
    }

    /* ==================== 导入编排：文件前置校验 ==================== */

    @Test
    void importThrowsWhenFileNull() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importExcel(null, null, TestImportVo.class, rowHandler,
                        vo -> new TestEntity(), baseService),
                "file = null 应抛「导入文件不能为空」");
        assertEquals("导入文件不能为空", ex.getMessage());
    }

    @Test
    void importThrowsWhenFileEmpty() {
        MultipartFile file = new MockMultipartFile("file", "import.xlsx", "application/octet-stream", new byte[0]);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importExcel(file, null, TestImportVo.class, rowHandler,
                        vo -> new TestEntity(), baseService),
                "空文件应抛「导入文件不能为空」");
        assertEquals("导入文件不能为空", ex.getMessage());
    }

    @Test
    void importThrowsWhenNotXlsx() {
        MultipartFile file = new MockMultipartFile("file", "import.xls", "application/octet-stream",
                importFile(List.of(importRow("C001", "张三", "启用", "", ""))));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importExcel(file, null, TestImportVo.class, rowHandler,
                        vo -> new TestEntity(), baseService),
                "非 .xlsx 文件应抛「仅支持 .xlsx 文件」");
        assertEquals("仅支持 .xlsx 文件", ex.getMessage());
    }

    @Test
    void importThrowsWhenNoDataRows() {
        // template 仅标题行 → 无数据行
        MultipartFile file = uploadFile(ImportExportUtils.template(TestImportVo.class));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importExcel(file, null, TestImportVo.class, rowHandler,
                        vo -> new TestEntity(), baseService),
                "无数据行应抛「导入文件无数据行」");
        assertEquals("导入文件无数据行", ex.getMessage());
    }

    /* ==================== 导入编排：required 校验 ==================== */

    @Test
    void importRequiredBlankThrowsWhenIgnoreErrorFalse() {
        stubDict();
        MultipartFile file = uploadFile(importFile(List.of(
                importRow("", "张三", "启用", "", ""),
                importRow("C002", "李四", "禁用", "", ""))));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importExcel(file, null, TestImportVo.class, rowHandler,
                        vo -> new TestEntity(), baseService),
                "ignoreError 缺省（false）时必填空值应整体抛出");
        assertEquals("编码 不能为空", ex.getMessage());
        verify(baseService, never()).insert(any());
    }

    @Test
    void importRequiredBlankSkipsRowWhenIgnoreErrorTrue() {
        stubDict();
        MultipartFile file = uploadFile(importFile(List.of(
                importRow("", "张三", "启用", "", ""),
                importRow("C002", "李四", "禁用", "", ""))));
        ImportResultVo result = service.importExcel(file, Map.<String, Object>of("ignoreError", true), TestImportVo.class,
                rowHandler, vo -> new TestEntity(), baseService);

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getSuccess());
        assertEquals(1, result.getFailed());
        assertEquals(1, result.getErrors().size());
        assertEquals(2, result.getErrors().get(0).getRow());
        assertEquals("fieldCode", result.getErrors().get(0).getField());
        assertEquals("编码 不能为空", result.getErrors().get(0).getMessage());
        verify(baseService, times(1)).insert(any());
    }

    /* ==================== 导入编排：dateFormat 校验与回写 ==================== */

    @Test
    void importDateInvalidReturnsRowError() {
        stubDict();
        MultipartFile file = uploadFile(importFile(List.of(
                importRow("C001", "张三", "启用", "2026/01/02", ""))));
        ImportResultVo result = service.importExcel(file, Map.<String, Object>of("ignoreError", true), TestImportVo.class,
                rowHandler, vo -> new TestEntity(), baseService);

        assertEquals(1, result.getErrors().size());
        assertEquals(2, result.getErrors().get(0).getRow());
        assertEquals("fieldCreateTime", result.getErrors().get(0).getField());
        assertEquals("日期格式错误", result.getErrors().get(0).getMessage());
        assertEquals(0, result.getSuccess());
        verify(baseService, never()).insert(any());
    }

    @Test
    void importDateParsedAndWrittenBackToVo() {
        stubDict();
        MultipartFile file = uploadFile(importFile(List.of(
                importRow("C001", "张三", "启用", "2026-01-02 03:04:05", "2026-02-03 04:05:06"))));
        List<TestImportVo> convertedVos = new ArrayList<>();
        service.importExcel(file, null, TestImportVo.class, rowHandler, toEntity(convertedVos), baseService);

        // VO 的时间字段被回写为 Date / LocalDateTime 对象
        assertEquals(1, convertedVos.size());
        assertNotNull(convertedVos.get(0).getFieldCreateTime());
        assertEquals("2026-01-02 03:04:05",
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(convertedVos.get(0).getFieldCreateTime()));
        assertEquals(LocalDateTime.of(2026, 2, 3, 4, 5, 6), convertedVos.get(0).getFieldUpdateTime());

        ArgumentCaptor<TestEntity> captor = ArgumentCaptor.forClass(TestEntity.class);
        verify(baseService).insert(captor.capture());
        assertNotNull(captor.getValue().getFieldCreateTime());
        assertEquals(LocalDateTime.of(2026, 2, 3, 4, 5, 6), captor.getValue().getFieldUpdateTime());
    }

    /* ==================== 导入编排：dict 码值回转 ==================== */

    @Test
    void importDictDisplayConvertedToCode() {
        stubDict();
        MultipartFile file = uploadFile(importFile(List.of(
                importRow("C001", "张三", "启用", "", ""))));
        ArgumentCaptor<TestEntity> captor = ArgumentCaptor.forClass(TestEntity.class);
        service.importExcel(file, null, TestImportVo.class, rowHandler, vo -> {
            TestEntity entity = new TestEntity();
            entity.setFieldCode(vo.getFieldCode());
            entity.setFieldStatus(vo.getFieldStatus());
            return entity;
        }, baseService);

        // 显示值「启用」回转为码值「1」
        verify(baseService).insert(captor.capture());
        assertEquals("1", captor.getValue().getFieldStatus());
    }

    @Test
    void importDictInvalidDisplayReturnsRowError() {
        stubDict();
        MultipartFile file = uploadFile(importFile(List.of(
                importRow("C001", "张三", "未知状态", "", ""))));
        ImportResultVo result = service.importExcel(file, Map.<String, Object>of("ignoreError", true), TestImportVo.class,
                rowHandler, vo -> new TestEntity(), baseService);

        assertEquals(1, result.getErrors().size());
        assertEquals("fieldStatus", result.getErrors().get(0).getField());
        assertEquals("无效码值", result.getErrors().get(0).getMessage());
        verify(baseService, never()).insert(any());
    }

    @Test
    void importDictUnknownTypeCodeReturnsRowError() {
        // type_code 无启用项（空列表）→「无效码值」
        when(dictService.itemsByTypeCode("sys_common_status")).thenReturn(List.of());
        MultipartFile file = uploadFile(importFile(List.of(
                importRow("C001", "张三", "启用", "", ""))));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importExcel(file, null, TestImportVo.class, rowHandler,
                        vo -> new TestEntity(), baseService),
                "type_code 未知时显示值回转不到码值应抛（ignoreError 缺省 false）");
        assertEquals("无效码值", ex.getMessage());
    }

    @Test
    void importDictEmptyCellSkipsConversion() {
        stubDict();
        // dict 字段为空单元格：不参与回转、不记无效码值，行正常通过
        MultipartFile file = uploadFile(importFile(List.of(
                importRow("C001", "张三", "", "", ""))));
        ImportResultVo result = service.importExcel(file, null, TestImportVo.class, rowHandler, vo -> {
            TestEntity entity = new TestEntity();
            entity.setFieldCode(vo.getFieldCode());
            entity.setFieldStatus(vo.getFieldStatus());
            return entity;
        }, baseService);

        assertEquals(1, result.getSuccess());
        assertEquals(0, result.getFailed());
        ArgumentCaptor<TestEntity> captor = ArgumentCaptor.forClass(TestEntity.class);
        verify(baseService).insert(captor.capture());
        assertEquals("", captor.getValue().getFieldStatus());
    }

    /* ==================== 导入编排：rowHandler 模块校验 ==================== */

    @Test
    void importRowHandlerErrorRecordedWithEmptyField() {
        stubDict();
        when(rowHandler.validate(any(), anyInt())).thenReturn("编码已存在");
        MultipartFile file = uploadFile(importFile(List.of(
                importRow("C001", "张三", "启用", "", ""))));
        ImportResultVo result = service.importExcel(file, Map.<String, Object>of("ignoreError", true), TestImportVo.class,
                rowHandler, vo -> new TestEntity(), baseService);

        assertEquals(1, result.getErrors().size());
        // 行级（非字段）错误：field 为空串
        assertEquals("", result.getErrors().get(0).getField());
        assertEquals("编码已存在", result.getErrors().get(0).getMessage());
        assertEquals(2, result.getErrors().get(0).getRow());
        verify(baseService, never()).insert(any());
    }

    @Test
    void importRowHandlerNullSkipped() {
        stubDict();
        MultipartFile file = uploadFile(importFile(List.of(
                importRow("C001", "张三", "启用", "", ""))));
        ImportResultVo result = service.importExcel(file, null, TestImportVo.class, null,
                vo -> new TestEntity(), baseService);

        // rowHandler 为 null：跳过模块校验，行正常写库
        assertEquals(1, result.getSuccess());
        verify(baseService).insert(any());
        verify(rowHandler, never()).validate(any(), anyInt());
    }

    /* ==================== 导入编排：ignoreError 两态与汇总 ==================== */

    @Test
    void importIgnoreErrorFalseThrowsAndNeverInserts() {
        stubDict();
        MultipartFile file = uploadFile(importFile(List.of(
                importRow("C001", "张三", "启用", "", ""),
                importRow("C002", "李四", "未知状态", "", ""),
                importRow("C003", "王五", "禁用", "", ""))));
        assertThrows(IllegalArgumentException.class,
                () -> service.importExcel(file, null, TestImportVo.class, rowHandler,
                        vo -> new TestEntity(), baseService),
                "ignoreError=false 任一行错误即抛");
        verify(baseService, never()).insert(any());
    }

    @Test
    void importIgnoreErrorTrueSkipsFailedAndInsertsValid() {
        stubDict();
        MultipartFile file = uploadFile(importFile(List.of(
                importRow("C001", "张三", "启用", "", ""),
                importRow("C002", "李四", "未知状态", "", ""),
                importRow("C003", "王五", "禁用", "", ""))));
        ImportResultVo result = service.importExcel(file, Map.<String, Object>of("ignoreError", Boolean.TRUE), TestImportVo.class,
                rowHandler, vo -> new TestEntity(), baseService);

        assertEquals(3, result.getTotal());
        assertEquals(2, result.getSuccess());
        assertEquals(1, result.getFailed());
        assertEquals(1, result.getErrors().size());
        assertEquals(3, result.getErrors().get(0).getRow());
        verify(baseService, times(2)).insert(any());
    }

    @Test
    void importIgnoreErrorTrueAllFailedStillReturnsResult() {
        stubDict();
        MultipartFile file = uploadFile(importFile(List.of(
                importRow("", "张三", "启用", "", ""),
                importRow("", "李四", "禁用", "", ""))));
        ImportResultVo result = service.importExcel(file, Map.<String, Object>of("ignoreError", true), TestImportVo.class,
                rowHandler, vo -> new TestEntity(), baseService);

        // 全部行失败：空写库仍返回 failed = total
        assertEquals(2, result.getTotal());
        assertEquals(0, result.getSuccess());
        assertEquals(2, result.getFailed());
        verify(baseService, never()).insert(any());
    }

    @Test
    void importSuccessFullFlowThreeRows() {
        stubDict();
        MultipartFile file = uploadFile(importFile(List.of(
                importRow("C001", "张三", "启用", "2026-01-02 03:04:05", ""),
                importRow("C002", "李四", "禁用", "", "2026-02-03 04:05:06"),
                importRow("C003", "王五", "启用", "", ""))));
        ArgumentCaptor<TestEntity> captor = ArgumentCaptor.forClass(TestEntity.class);
        ImportResultVo result = service.importExcel(file, null, TestImportVo.class, rowHandler, vo -> {
            TestEntity entity = new TestEntity();
            entity.setFieldCode(vo.getFieldCode());
            entity.setFieldStatus(vo.getFieldStatus());
            return entity;
        }, baseService);

        assertEquals(3, result.getTotal());
        assertEquals(3, result.getSuccess());
        assertEquals(0, result.getFailed());
        assertTrue(result.getErrors().isEmpty(), "成功导入 errors 应为空");
        verify(baseService, times(3)).insert(captor.capture());
        assertEquals("C003", captor.getAllValues().get(2).getFieldCode());
        assertEquals("1", captor.getAllValues().get(2).getFieldStatus());
    }

    /* ==================== 导入编排：params 透传 ==================== */

    @Test
    void importIgnoreErrorStringTrueCompatible() {
        stubDict();
        // params 公共开关兼容字符串 "true"
        MultipartFile file = uploadFile(importFile(List.of(
                importRow("", "张三", "启用", "", ""))));
        ImportResultVo result = service.importExcel(file, Map.<String, Object>of("ignoreError", "true"), TestImportVo.class,
                rowHandler, vo -> new TestEntity(), baseService);

        assertEquals(1, result.getTotal());
        assertEquals(0, result.getSuccess());
        assertEquals(1, result.getFailed());
    }

    @Test
    void importFullUpdatePassThroughOnly() {
        stubDict();
        // fullUpdate 公共层仅透传不解释：流程不受影响，rowHandler 收到原 VO
        MultipartFile file = uploadFile(importFile(List.of(
                importRow("C001", "张三", "启用", "", ""))));
        ArgumentCaptor<TestImportVo> voCaptor = ArgumentCaptor.forClass(TestImportVo.class);
        ArgumentCaptor<Integer> rowCaptor = ArgumentCaptor.forClass(Integer.class);
        ImportResultVo result = service.importExcel(file, Map.<String, Object>of("fullUpdate", true, "ignoreError", true),
                TestImportVo.class, rowHandler, vo -> new TestEntity(), baseService);

        assertEquals(1, result.getSuccess());
        verify(rowHandler).validate(voCaptor.capture(), rowCaptor.capture());
        assertEquals("C001", voCaptor.getValue().getFieldCode());
        assertEquals("张三", voCaptor.getValue().getFieldName());
        assertEquals(2, rowCaptor.getValue());
    }

    @Test
    void importUnknownParamsKeyIgnored() {
        stubDict();
        // 未知 params 键不校验：流程不受影响
        MultipartFile file = uploadFile(importFile(List.of(
                importRow("C001", "张三", "启用", "", ""))));
        ImportResultVo result = service.importExcel(file, Map.<String, Object>of("unknownKey", "x"), TestImportVo.class,
                rowHandler, vo -> new TestEntity(), baseService);
        assertEquals(1, result.getSuccess());
    }

    /* ==================== export / template ==================== */

    @Test
    void exportConvertsDictDisplayValue() {
        stubDict();
        TestImportVo vo = new TestImportVo();
        vo.setFieldCode("C001");
        vo.setFieldStatus("1");
        byte[] bytes = service.export(List.of(vo), TestImportVo.class);

        List<List<String>> grid = readGrid(bytes);
        assertIterableEquals(List.of("编码", "名称", "状态", "创建时间", "更新时间"), grid.get(0));
        // 码值「1」按字典映射转显示值「启用」
        assertEquals("启用", grid.get(1).get(2));
    }

    @Test
    void exportWritesRawCodeWhenDictHasNoItems() {
        // type_code 无启用项：原样写码值 + warn 日志（不抛错）
        when(dictService.itemsByTypeCode("sys_common_status")).thenReturn(List.of());
        TestImportVo vo = new TestImportVo();
        vo.setFieldCode("C001");
        vo.setFieldStatus("1");
        byte[] bytes = service.export(List.of(vo), TestImportVo.class);

        List<List<String>> grid = readGrid(bytes);
        assertEquals("1", grid.get(1).get(2));
    }

    @Test
    void exportEmptyRowsWritesHeaderOnly() {
        stubDict();
        byte[] bytes = service.export(List.of(), TestImportVo.class);
        List<List<String>> grid = readGrid(bytes);
        assertEquals(1, grid.size(), "空列表仅含标题行");
        assertIterableEquals(List.of("编码", "名称", "状态", "创建时间", "更新时间"), grid.get(0));
    }

    @Test
    void templateContainsHeaderRowOnly() {
        byte[] bytes = service.template(TestImportVo.class);
        List<List<String>> grid = readGrid(bytes);

        assertEquals(1, grid.size(), "template 仅含标题行");
        assertIterableEquals(List.of("编码", "名称", "状态", "创建时间", "更新时间"), grid.get(0));
    }

    /* ==================== BaseCrudController 公共通道守卫 ==================== */

    @Test
    void controllerThreeArgConstructorGuardsImportExport() {
        TestCrudController controller = new TestCrudController(baseService, new BaseResolver(),
                org.mockito.Mockito.mock(ApplicationEventPublisher.class));
        // 3 参构造 = 4 参构造第 4 参 null：exportExcel / importExcel 守卫抛 IllegalStateException
        IllegalStateException exportEx = assertThrows(IllegalStateException.class,
                () -> controller.exportExcel(null, "dict", TestImportVo.class, list -> List.of()));
        assertEquals("当前模块未接入导入导出公共通道", exportEx.getMessage());
        IllegalStateException importEx = assertThrows(IllegalStateException.class,
                () -> controller.importExcel(null, null, TestImportVo.class, null, vo -> new TestEntity()));
        assertEquals("当前模块未接入导入导出公共通道", importEx.getMessage());
    }

    @Test
    void controllerFourArgNullServiceGuardsImportExport() {
        TestCrudController controller = new TestCrudController(baseService, new BaseResolver(),
                org.mockito.Mockito.mock(ApplicationEventPublisher.class), null);
        assertThrows(IllegalStateException.class,
                () -> controller.exportExcel(null, "dict", TestImportVo.class, list -> List.of()));
        assertThrows(IllegalStateException.class,
                () -> controller.importExcel(null, null, TestImportVo.class, null, vo -> new TestEntity()));
    }

    @Test
    void controllerExportTemplateWorksWithoutService() {
        // exportTemplate 不守卫：走静态 ImportExportUtils，3 参构造（未接入）也可直接调
        TestCrudController controller = new TestCrudController(baseService, new BaseResolver(),
                org.mockito.Mockito.mock(ApplicationEventPublisher.class));
        ResponseEntity<Resource> response = controller.exportTemplate("dict", TestImportVo.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String disposition = response.getHeaders().getFirst("Content-Disposition");
        assertNotNull(disposition);
        assertTrue(disposition.contains("dict_template.xlsx"), "文件名应为 {prefix}_template.xlsx: " + disposition);
        assertNotNull(response.getBody());
    }

    @Test
    void controllerExportExcelStreamsFile() {
        stubDict();
        when(baseService.selectList(any(), any())).thenReturn(List.of(new TestEntity()));
        TestCrudController controller = new TestCrudController(baseService, new BaseResolver(),
                org.mockito.Mockito.mock(ApplicationEventPublisher.class), service);

        ResponseEntity<Resource> response =
                controller.exportExcel(null, "dict", TestImportVo.class, list -> List.of(new TestImportVo()));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String disposition = response.getHeaders().getFirst("Content-Disposition");
        assertNotNull(disposition);
        assertTrue(disposition.contains(".xlsx"), "导出文件名应以 .xlsx 结尾: " + disposition);
        // 全量查询口径：criteria / pages 均传 null
        verify(baseService).selectList(isNull(), isNull());
    }

    @Test
    void controllerImportExcelReturnsResult() {
        stubDict();
        TestCrudController controller = new TestCrudController(baseService, new BaseResolver(),
                org.mockito.Mockito.mock(ApplicationEventPublisher.class), service);

        MultipartFile file = uploadFile(importFile(List.of(importRow("C001", "张三", "启用", "", ""))));
        BaseResponse<ImportResultVo> response = controller.importExcel(file, "{\"ignoreError\":true}",
                TestImportVo.class, null, vo -> new TestEntity());

        assertEquals("SUCCESS", response.getCode());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().getTotal());
        assertEquals(1, response.getData().getSuccess());
        assertNull(response.getMetadata());
    }

    @Test
    void controllerImportExcelRejectsMalformedParams() {
        TestCrudController controller = new TestCrudController(baseService, new BaseResolver(),
                org.mockito.Mockito.mock(ApplicationEventPublisher.class), service);
        MultipartFile file = uploadFile(importFile(List.of(importRow("C001", "张三", "启用", "", ""))));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.importExcel(file, "{bad json", TestImportVo.class, null, vo -> new TestEntity()),
                "params 非法 JSON 应抛「导入配置解析失败」");
        assertEquals("导入配置解析失败", ex.getMessage());
    }

    /* ==================== 读回辅助（测试自用） ==================== */

    private static Map<String, String> mapOf(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static List<List<String>> readGrid(byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            List<Map<Integer, String>> rows = EasyExcel.read(in).sheet(0).headRowNumber(0).doReadSync();
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
        } catch (Exception e) {
            throw new IllegalStateException("测试读回 xlsx 失败", e);
        }
    }
}
