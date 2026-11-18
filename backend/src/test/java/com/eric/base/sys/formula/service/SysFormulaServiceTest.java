package com.eric.base.sys.formula.service;

import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.formula.dao.SysFormulaDao;
import com.eric.base.sys.formula.model.SysFormula;
import com.eric.base.sys.formula.response.SysFormulaFunctionVo;
import com.eric.base.sys.formula.response.SysFormulaOptionVo;
import com.eric.base.sys.formula.response.SysFormulaVo;
import com.eric.base.sys.formula.service.engine.FormulaEngine;
import com.eric.base.sys.formula.service.engine.FormulaFunctionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.eric.base.sys.formula.properties.SysFormulaConstants.CS_FORMULA_CODE_DUPLICATED;
import static com.eric.base.sys.formula.properties.SysFormulaConstants.CS_FORMULA_FUNCTION_NOT_FOUND;
import static com.eric.base.sys.formula.properties.SysFormulaConstants.CS_FORMULA_NOT_FOUND;
import static com.eric.base.sys.formula.properties.SysFormulaConstants.CS_FORMULA_SYNTAX_INVALID;
import static com.eric.base.sys.formula.properties.SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 命名公式管理服务测试
 *
 * @author Eric
 * @date 2026/11/18
 * @description 覆盖任务 F14-BE-007 验收标准（Dao mock 桩 + 真实 SysFormulaValidateService
 *              与 FormulaEngine，MockedStatic 会话态见 FormulaServiceTestSupport——形态
 *              对齐 SysDatasourceServiceTest 先例）：
 *              <ul>
 *              <li>save：正常落库链（依赖解析落 field_dependencies JSON 字符串、默认启用、
 *                  审计字段）、编码全局查重（CS_FORMULA_CODE_DUPLICATED）、保存期强制
 *                  validate 阻断（语法 CS_FORMULA_SYNTAX_INVALID / 未注册函数
 *                  CS_FORMULA_FUNCTION_NOT_FOUND / 返回类型与公式类型不合法
 *                  CS_FORMULA_TYPE_MISMATCH）、宽松模式下引用失效变量不阻断保存（§3.3）、
 *                  路径投影取根码、空依赖存 []；</li>
 *              <li>update：重新强制校验与依赖重写、编码查重排除自身、fieldEnable 不受理、
 *                  行不存在；</li>
 *              <li>enable / delete / load / list / count：委托与保护、依赖 JSON 双形态
 *                  （load 解析为对象、list 原文直出）、损坏 JSON 程序异常；</li>
 *              <li>requireEnabledFormula：启用放行、不存在 / 已停用抛
 *                  CS_FORMULA_NOT_FOUND（供执行/试算侧判断）；</li>
 *              <li>options / functions：启用命名公式下拉（不暴露表达式原文）与函数
 *                  注册中心元数据导出。</li>
 *              </ul>
 */
class SysFormulaServiceTest extends FormulaServiceTestSupport {

    /** 32 位测试公式 ID（避免 BaseModel.setFieldId 触发重新生成） */
    private static final String FORMULA_ID = "f".repeat(32);
    private static final String OTHER_ROW_ID = "e".repeat(32);
    private static final String FORMULA_CODE = "tax.total";
    private static final String TAX_TOTAL_EXPRESSION = "ROUND(${applyAmount} * ${taxRate}, 2)";
    /** 存储态变量依赖 JSON（方案 §4.2 依赖解析产物形态） */
    private static final String TAX_TOTAL_DEPENDENCIES = "[\"applyAmount\",\"taxRate\"]";

    private SysFormulaDao dao;
    private SysFormulaService service;

    @BeforeEach
    void setUpService() {
        dao = mock(SysFormulaDao.class);
        FormulaEngine engine = newEngine();
        service = new SysFormulaService(dao, new SysFormulaValidateService(engine),
                new FormulaFunctionRegistry(builtinProviders(datasourceService)));
    }

    // ---------------------------------------------------------------- save 成功链

    @Test
    void savePersistsFormulaWithSerializedDependencies() {
        when(dao.selectByCode(FORMULA_CODE)).thenReturn(null);
        when(dao.selectById(FORMULA_ID)).thenReturn(storedRow(FORMULA_CODE, TAX_TOTAL_EXPRESSION,
                TAX_TOTAL_DEPENDENCIES, "NUMBER", true));

        SysFormulaVo vo = service.save(saveBody());

        ArgumentCaptor<SysFormula> captor = ArgumentCaptor.forClass(SysFormula.class);
        verify(dao).insert(captor.capture());
        SysFormula inserted = captor.getValue();
        assertEquals(FORMULA_ID, inserted.getFieldId(), "fieldId 沿用前端 getInitID 预取值");
        assertEquals(FORMULA_CODE, inserted.getFieldCode());
        assertEquals("含税金额", inserted.getFieldName());
        assertEquals("FIELD", inserted.getFieldType());
        assertEquals(TAX_TOTAL_EXPRESSION, inserted.getFieldExpression());
        assertEquals(TAX_TOTAL_DEPENDENCIES, inserted.getFieldDependencies(), "依赖解析落 field_dependencies（JSON 字符串）");
        assertEquals("NUMBER", inserted.getFieldReturnType());
        assertEquals(Boolean.TRUE, inserted.getFieldEnable(), "新建默认启用");
        assertEquals(USER_ID, inserted.getFieldCreatorId(), "审计字段取当前会话用户");
        assertEquals(USER_ID, inserted.getFieldUpdaterId());
        assertNotNull(inserted.getFieldCreateTime());
        assertNotNull(inserted.getFieldUpdateTime());

        // 返回 load 解析后的详情：依赖为变量码数组（JSON 双形态之 load 形态）
        assertInstanceOf(List.class, vo.getFieldDependencies());
        assertEquals(List.of("applyAmount", "taxRate"), vo.getFieldDependencies());
        assertEquals(TAX_TOTAL_EXPRESSION, vo.getFieldExpression());
    }

    @Test
    void savePersistsProjectionRootCodeAsDependency() {
        // 路径投影 ${details.amount} 依赖取根码 details（引擎提取口径）
        when(dao.selectByCode("detail.sum")).thenReturn(null);
        when(dao.selectById(FORMULA_ID)).thenReturn(storedRow("detail.sum", "SUM(${details.amount})",
                "[\"details\"]", "NUMBER", true));

        SysFormula body = saveBody();
        body.setFieldCode("detail.sum");
        body.setFieldName("明细汇总");
        body.setFieldExpression("SUM(${details.amount})");
        service.save(body);

        ArgumentCaptor<SysFormula> captor = ArgumentCaptor.forClass(SysFormula.class);
        verify(dao).insert(captor.capture());
        assertEquals("[\"details\"]", captor.getValue().getFieldDependencies());
    }

    @Test
    void saveStoresEmptyDependenciesAsJsonArray() {
        // 表达式无变量引用：空依赖统一存 []（update 通道可清除既有值，语义与 NULL 等价）
        when(dao.selectByCode("const.pi")).thenReturn(null);
        when(dao.selectById(FORMULA_ID)).thenReturn(storedRow("const.pi", "1 + 1", "[]", "NUMBER", true));

        SysFormula body = saveBody();
        body.setFieldCode("const.pi");
        body.setFieldExpression("1 + 1");
        body.setFieldRemark(null);
        service.save(body);

        ArgumentCaptor<SysFormula> captor = ArgumentCaptor.forClass(SysFormula.class);
        verify(dao).insert(captor.capture());
        assertEquals("[]", captor.getValue().getFieldDependencies());
    }

    // ---------------------------------------------------------------- save 校验拒绝与查重

    @Test
    void saveRejectsDuplicatedCode() {
        when(dao.selectByCode(FORMULA_CODE))
                .thenReturn(storedRow(FORMULA_CODE, TAX_TOTAL_EXPRESSION, TAX_TOTAL_DEPENDENCIES, "NUMBER", true));

        assertFormulaError(() -> service.save(saveBody()), CS_FORMULA_CODE_DUPLICATED);
        verify(dao, never()).insert(any(SysFormula.class));
    }

    @Test
    void saveBlocksOnSyntaxInvalidExpression() {
        // 保存期强制 validate：语法不通过阻断保存（首错 CS_FORMULA_SYNTAX_INVALID，方案 §5.1）
        when(dao.selectByCode(FORMULA_CODE)).thenReturn(null);

        SysFormula body = saveBody();
        body.setFieldExpression("ROUND(${applyAmount} * ${taxRate}, 2");

        assertFormulaError(() -> service.save(body), CS_FORMULA_SYNTAX_INVALID);
        verify(dao, never()).insert(any(SysFormula.class));
    }

    @Test
    void saveBlocksOnUnregisteredFunction() {
        when(dao.selectByCode(FORMULA_CODE)).thenReturn(null);

        SysFormula body = saveBody();
        body.setFieldExpression("NOT_A_FUNCTION(${applyAmount})");

        assertFormulaError(() -> service.save(body), CS_FORMULA_FUNCTION_NOT_FOUND);
        verify(dao, never()).insert(any(SysFormula.class));
    }

    @Test
    void saveBlocksOnInvalidReturnType() {
        when(dao.selectByCode(FORMULA_CODE)).thenReturn(null);

        SysFormula body = saveBody();
        body.setFieldReturnType("TEXT");

        assertFormulaError(() -> service.save(body), CS_FORMULA_TYPE_MISMATCH);
        verify(dao, never()).insert(any(SysFormula.class));
    }

    @Test
    void saveBlocksOnInvalidFormulaType() {
        when(dao.selectByCode(FORMULA_CODE)).thenReturn(null);

        SysFormula body = saveBody();
        body.setFieldType("NOT_A_TYPE");

        assertFormulaError(() -> service.save(body), CS_FORMULA_TYPE_MISMATCH);
        verify(dao, never()).insert(any(SysFormula.class));
    }

    @Test
    void saveRejectsBlankRequiredFields() {
        SysFormula body = saveBody();
        body.setFieldCode("  ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.save(body));
        assertTrue(ex.getMessage().contains("fieldCode"));
        verify(dao, never()).insert(any(SysFormula.class));
    }

    // ---------------------------------------------------------------- update

    @Test
    void updateRevalidatesAndRewritesDependencies() {
        // 编辑：编码查重排除自身（selectByCode 命中同 ID 行放行），依赖随新表达式重写；
        // update 后 load 重读（stub 第二次返回更新后存储态行）
        when(dao.selectById(FORMULA_ID))
                .thenReturn(storedRow(FORMULA_CODE, TAX_TOTAL_EXPRESSION, TAX_TOTAL_DEPENDENCIES, "NUMBER", true))
                .thenReturn(storedRow(FORMULA_CODE, "ROUND(${applyAmount} * ${taxRate} * ${discount}, 2)",
                        "[\"applyAmount\",\"taxRate\",\"discount\"]", "NUMBER", true));
        when(dao.selectByCode(FORMULA_CODE))
                .thenReturn(storedRow(FORMULA_CODE, TAX_TOTAL_EXPRESSION, TAX_TOTAL_DEPENDENCIES, "NUMBER", true));
        when(dao.update(any(SysFormula.class))).thenReturn(1);

        SysFormula body = saveBody();
        body.setFieldExpression("ROUND(${applyAmount} * ${taxRate} * ${discount}, 2)");
        body.setFieldRemark("含税金额（含折扣）");
        SysFormulaVo vo = service.update(body);

        ArgumentCaptor<SysFormula> captor = ArgumentCaptor.forClass(SysFormula.class);
        verify(dao).update(captor.capture());
        SysFormula updated = captor.getValue();
        assertEquals(FORMULA_ID, updated.getFieldId());
        assertEquals("[\"applyAmount\",\"taxRate\",\"discount\"]", updated.getFieldDependencies(), "依赖随表达式重写");
        assertEquals(USER_ID, updated.getFieldUpdaterId());
        assertNotNull(updated.getFieldUpdateTime());
        assertNull(updated.getFieldEnable(), "fieldEnable 编辑不受理（启停走 /enable 通道）");
        assertInstanceOf(List.class, vo.getFieldDependencies());
        assertEquals(List.of("applyAmount", "taxRate", "discount"), vo.getFieldDependencies());
    }

    @Test
    void updateRejectsDuplicatedCodeOnOtherRow() {
        // 编辑行存在（OTHER_ROW_ID），但新编码命中他人行（FORMULA_ID）→ 查重拒绝
        when(dao.selectById(OTHER_ROW_ID))
                .thenReturn(storedRow(OTHER_ROW_ID, OTHER_ROW_ID, "1 + 1", "[]", "NUMBER", true));
        when(dao.selectByCode(FORMULA_CODE))
                .thenReturn(storedRow(FORMULA_CODE, TAX_TOTAL_EXPRESSION, TAX_TOTAL_DEPENDENCIES, "NUMBER", true));

        SysFormula body = saveBody();
        body.setFieldId(OTHER_ROW_ID);

        assertFormulaError(() -> service.update(body), CS_FORMULA_CODE_DUPLICATED);
        verify(dao, never()).update(any(SysFormula.class));
    }

    @Test
    void updateBlocksOnInvalidExpression() {
        when(dao.selectById(FORMULA_ID))
                .thenReturn(storedRow(FORMULA_CODE, TAX_TOTAL_EXPRESSION, TAX_TOTAL_DEPENDENCIES, "NUMBER", true));

        SysFormula body = saveBody();
        body.setFieldExpression("ROUND(${applyAmount} * ");

        assertFormulaError(() -> service.update(body), CS_FORMULA_SYNTAX_INVALID);
        verify(dao, never()).update(any(SysFormula.class));
    }

    @Test
    void updateRequiresExistence() {
        when(dao.selectById(FORMULA_ID)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.update(saveBody()));
        assertTrue(ex.getMessage().contains("公式不存在"));
    }

    // ---------------------------------------------------------------- enable / delete

    @Test
    void updateEnableDelegatesToDedicatedStatement() {
        // 启停用只更新自身一行：走 updateEnable 专属语句，仅更新 field_enable 与审计字段
        when(dao.selectById(FORMULA_ID))
                .thenReturn(storedRow(FORMULA_CODE, TAX_TOTAL_EXPRESSION, TAX_TOTAL_DEPENDENCIES, "NUMBER", true));
        when(dao.updateEnable(eq(FORMULA_ID), eq(false), eq(USER_ID), any(Date.class))).thenReturn(1);

        service.updateEnable(FORMULA_ID, false);

        verify(dao).updateEnable(eq(FORMULA_ID), eq(false), eq(USER_ID), any(Date.class));
        verify(dao, never()).update(any(SysFormula.class));
    }

    @Test
    void updateEnableRejectsNullFlag() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateEnable(FORMULA_ID, null));
        assertTrue(ex.getMessage().contains("fieldEnable"));
    }

    @Test
    void updateEnableFailsWhenRowMissing() {
        when(dao.selectById(FORMULA_ID)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateEnable(FORMULA_ID, true));
        assertTrue(ex.getMessage().contains("公式不存在"));
    }

    @Test
    void updateEnableFailsWhenNoRowAffected() {
        when(dao.selectById(FORMULA_ID))
                .thenReturn(storedRow(FORMULA_CODE, TAX_TOTAL_EXPRESSION, TAX_TOTAL_DEPENDENCIES, "NUMBER", true));
        when(dao.updateEnable(any(), any(), any(), any())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.updateEnable(FORMULA_ID, true));
    }

    @Test
    void deleteRemovesRow() {
        when(dao.selectById(FORMULA_ID))
                .thenReturn(storedRow(FORMULA_CODE, TAX_TOTAL_EXPRESSION, TAX_TOTAL_DEPENDENCIES, "NUMBER", true));

        service.delete(FORMULA_ID);

        verify(dao).deleteById(FORMULA_ID);
    }

    @Test
    void deleteRequiresExistence() {
        when(dao.selectById(FORMULA_ID)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.delete(FORMULA_ID));
        assertTrue(ex.getMessage().contains("公式不存在"));
    }

    // ---------------------------------------------------------------- load / list / count（JSON 双形态）

    @Test
    void loadParsesDependenciesToObject() {
        when(dao.selectById(FORMULA_ID))
                .thenReturn(storedRow(FORMULA_CODE, TAX_TOTAL_EXPRESSION, TAX_TOTAL_DEPENDENCIES, "NUMBER", true));

        SysFormulaVo vo = service.load(FORMULA_ID);

        assertInstanceOf(List.class, vo.getFieldDependencies(), "load 响应依赖解析为对象");
        assertEquals(List.of("applyAmount", "taxRate"), vo.getFieldDependencies());
    }

    @Test
    void loadToleratesNullDependencies() {
        // NULL=表达式未引用任何变量（存储态语义），load 返回 null
        when(dao.selectById(FORMULA_ID))
                .thenReturn(storedRow(FORMULA_CODE, "1 + 1", null, "NUMBER", true));

        assertNull(service.load(FORMULA_ID).getFieldDependencies());
    }

    @Test
    void loadFailsOnDamagedDependenciesJson() {
        // 损坏 JSON 属存储态程序异常（依赖仅由保存期引擎提取落库），抛 IllegalStateException
        when(dao.selectById(FORMULA_ID))
                .thenReturn(storedRow(FORMULA_CODE, TAX_TOTAL_EXPRESSION, "{damaged", "NUMBER", true));

        assertThrows(IllegalStateException.class, () -> service.load(FORMULA_ID));
    }

    @Test
    void loadRequiresExistence() {
        when(dao.selectById(FORMULA_ID)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.load(FORMULA_ID));
        assertTrue(ex.getMessage().contains("公式不存在"));
    }

    @Test
    void listKeepsRawDependenciesJson() {
        when(dao.selectList(any(SysFormula.class), any(Pages.class)))
                .thenReturn(List.of(storedRow(FORMULA_CODE, TAX_TOTAL_EXPRESSION, TAX_TOTAL_DEPENDENCIES,
                        "NUMBER", true)));

        List<SysFormulaVo> rows = service.list(queryBody(), pages());

        assertEquals(1, rows.size());
        assertEquals(TAX_TOTAL_DEPENDENCIES, rows.get(0).getFieldDependencies(), "列表响应依赖原文直出不解析");
        assertEquals(FORMULA_CODE, rows.get(0).getFieldCode());
    }

    @Test
    void countDelegatesToDao() {
        when(dao.selectCount(any(SysFormula.class))).thenReturn(7L);

        assertEquals(7L, service.count(queryBody()));
    }

    // ---------------------------------------------------------------- 执行 / 试算侧钩子

    @Test
    void requireEnabledFormulaReturnsEnabledRow() {
        when(dao.selectByCode(FORMULA_CODE))
                .thenReturn(storedRow(FORMULA_CODE, TAX_TOTAL_EXPRESSION, TAX_TOTAL_DEPENDENCIES, "NUMBER", true));

        SysFormula formula = service.requireEnabledFormula(FORMULA_CODE);

        assertEquals(FORMULA_ID, formula.getFieldId());
        assertEquals(TAX_TOTAL_EXPRESSION, formula.getFieldExpression());
    }

    @Test
    void requireEnabledFormulaRejectsMissingCode() {
        when(dao.selectByCode(FORMULA_CODE)).thenReturn(null);

        assertFormulaError(() -> service.requireEnabledFormula(FORMULA_CODE), CS_FORMULA_NOT_FOUND);
    }

    @Test
    void requireEnabledFormulaRejectsDisabledFormula() {
        // 命名公式停用后执行报 CS_FORMULA_NOT_FOUND（供执行/试算侧判断，方案 §3.3 / §5.3）
        when(dao.selectByCode(FORMULA_CODE))
                .thenReturn(storedRow(FORMULA_CODE, TAX_TOTAL_EXPRESSION, TAX_TOTAL_DEPENDENCIES, "NUMBER", false));

        String message = assertFormulaError(() -> service.requireEnabledFormula(FORMULA_CODE), CS_FORMULA_NOT_FOUND);
        assertTrue(message.contains("停用"), "停用场景消息应说明已停用: " + message);
    }

    // ---------------------------------------------------------------- options / functions（组件读取聚合）

    @Test
    void optionsReturnsEnabledFormulasWithoutExpression() {
        when(dao.selectList(any(SysFormula.class), isNull()))
                .thenReturn(List.of(storedRow(FORMULA_CODE, TAX_TOTAL_EXPRESSION, TAX_TOTAL_DEPENDENCIES,
                        "NUMBER", true)));

        List<SysFormulaOptionVo> options = service.options();

        assertEquals(1, options.size());
        SysFormulaOptionVo vo = options.get(0);
        assertEquals(FORMULA_CODE, vo.getFieldCode());
        assertEquals("含税金额", vo.getFieldName());
        assertEquals("FIELD", vo.getFieldType());
        assertEquals("NUMBER", vo.getFieldReturnType());
    }

    @Test
    void functionsExportsRegistryMetadata() {
        List<SysFormulaFunctionVo> functions = service.functions();

        assertFalse(functions.isEmpty(), "内置九类函数应全部导出");
        Set<String> codes = functions.stream().map(SysFormulaFunctionVo::getFunctionCode).collect(Collectors.toSet());
        assertTrue(codes.contains("ROUND"), "MATH 类 ROUND 应在导出清单");
        assertTrue(codes.contains("DS_QUERY"), "DATA 类 DS_QUERY 应在导出清单");
        for (SysFormulaFunctionVo vo : functions) {
            assertNotNull(vo.getFunctionCode());
            assertNotNull(vo.getCategory());
            assertNotNull(vo.getReturnType());
        }
        SysFormulaFunctionVo round = functions.stream()
                .filter(vo -> "ROUND".equals(vo.getFunctionCode())).findFirst().orElseThrow();
        assertEquals("MATH", round.getCategory());
        assertEquals("NUMBER", round.getReturnType());
        assertEquals(2, round.getParameters().size(), "ROUND(number[, scale]) 两个参数位");
        assertEquals("number", round.getParameters().get(0).getName());
        assertTrue(round.getParameters().get(0).isRequired());
        assertEquals("scale", round.getParameters().get(1).getName());
        assertFalse(round.getParameters().get(1).isRequired());
        assertEquals("0", round.getParameters().get(1).getDefaultValue());
    }

    // ---------------------------------------------------------------- 桩构造

    /** 存储态命名公式行（fieldDependencies 为原文 JSON 字符串） */
    private SysFormula storedRow(String id, String code, String expression, Object dependenciesJson,
                                 String returnType, boolean enable) {
        SysFormula row = new SysFormula();
        row.setFieldId(id);
        row.setFieldCode(code);
        row.setFieldName("含税金额");
        row.setFieldType("FIELD");
        row.setFieldExpression(expression);
        row.setFieldDependencies(dependenciesJson);
        row.setFieldReturnType(returnType);
        row.setFieldRemark("跨模块复用的公共公式");
        row.setFieldEnable(enable);
        row.setFieldCreatorId(USER_ID);
        row.setFieldCreateTime(new Date());
        return row;
    }

    /** 存储态命名公式行（ID 固定 FORMULA_ID 的便捷形态） */
    private SysFormula storedRow(String code, String expression, Object dependenciesJson,
                                 String returnType, boolean enable) {
        return storedRow(FORMULA_ID, code, expression, dependenciesJson, returnType, enable);
    }

    /** 保存体基形（fieldId 经 getInitID 预取，方案 §3.2 含税金额样例） */
    private SysFormula saveBody() {
        SysFormula body = new SysFormula();
        body.setFieldId(FORMULA_ID);
        body.setFieldCode(FORMULA_CODE);
        body.setFieldName("含税金额");
        body.setFieldType("FIELD");
        body.setFieldExpression(TAX_TOTAL_EXPRESSION);
        body.setFieldReturnType("NUMBER");
        body.setFieldRemark("含税金额口径");
        return body;
    }

    /** 查询条件基形（编码模糊 + 类型精确） */
    private SysFormula queryBody() {
        SysFormula body = new SysFormula();
        body.setFieldCode("tax");
        body.setFieldType("FIELD");
        return body;
    }

    /** 分页参数（页大小 20） */
    private Pages pages() {
        Pages pages = new Pages();
        pages.setPageNo(1);
        pages.setPageSize(20);
        pages.setNeedPage(true);
        return pages;
    }
}
