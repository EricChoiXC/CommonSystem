package com.eric.base.sys.form.service;

import com.eric.base.common.base.model.Pages;
import com.eric.base.common.exception.AuthorizationException;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.form.dao.SysFormDataDao;
import com.eric.base.sys.form.dao.SysFormDao;
import com.eric.base.sys.form.dao.SysFormVersionDao;
import com.eric.base.sys.form.enums.SysFormVersionStatus;
import com.eric.base.sys.form.model.SysFormData;
import com.eric.base.sys.form.model.SysFormDataErrorDo;
import com.eric.base.sys.form.model.SysFormFieldDo;
import com.eric.base.sys.form.model.SysForm;
import com.eric.base.sys.form.model.SysFormRuleActionDo;
import com.eric.base.sys.form.model.SysFormRuleConditionDo;
import com.eric.base.sys.form.model.SysFormRuleDo;
import com.eric.base.sys.form.model.SysFormSchemaDo;
import com.eric.base.sys.form.model.SysFormVersion;
import com.eric.base.sys.form.properties.SysFormConstants;
import com.eric.base.sys.form.request.SysFormDataQuery;
import com.eric.base.sys.form.request.SysFormDataSaveRequest;
import com.eric.base.sys.form.request.SysFormDataValidateRequest;
import com.eric.base.sys.form.response.SysFormDataDetailVo;
import com.eric.base.sys.form.response.SysFormDataSavedVo;
import com.eric.base.sys.form.response.SysFormDataValidateVo;
import com.eric.base.sys.form.response.SysFormDataVo;
import com.eric.base.sys.form.service.engine.FormDataBasicValidator;
import com.eric.base.sys.form.service.engine.FormValidationEngine;
import com.eric.base.sys.org.model.SysOrgRole;
import com.eric.base.sys.org.model.SysOrgUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_DATA_FORBIDDEN;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_DATA_INVALID;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_DATA_NOT_FOUND;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_DISABLED;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_NO_PUBLISHED_VERSION;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_NOT_FOUND;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_UNKNOWN_FIELD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 表单数据引擎服务测试
 *
 * @author Eric
 * @date 2026/11/24
 * @description 覆盖任务 F15-BE-003 验收标准（Dao mock 桩 + 真实 FormDataBasicValidator，
 *              MockedStatic 会话态——形态对齐 SysFormServiceTest / FormulaServiceTestSupport
 *              先例）：保存校验链（S0 资源前置 NOT_FOUND/DISABLED/NO_PUBLISHED_VERSION、
 *              S1 未知字段拒绝含明细行内未知键、S2 必填等基础校验以 CS_FORM_DATA_INVALID
 *              + 首条中文摘要抛出）、载荷归一落库（Schema 字段序 + 明细行结构归一、绑定
 *              businessId 与会话提交人、processInstanceId 一期不写入）、独立校验入口
 *              （data.errors 结构化承载不抛 CS_FORM_DATA_INVALID、S0/S1 仍异常返回）、
 *              回显（实例锁定版本 Schema + 归属判定）、数据归属三种拒绝语义（未登录
 *              UNAUTHORIZED / 非本人非管理员 CS_FORM_DATA_FORBIDDEN（code=FORBIDDEN）/
 *              数据不存在 CS_FORM_DATA_NOT_FOUND）、删除（创建人或管理员）、列表归属强制
 *              过滤（非管理员覆盖 fieldCreatorId、管理员保留传入筛选）。
 */
class SysFormDataServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 32 位测试 ID（避免 BaseModel.setFieldId 触发重新生成） */
    private static final String FORM_ID = "f".repeat(32);
    private static final String VERSION_ID = "a".repeat(32);
    private static final String DATA_ID = "d".repeat(32);
    private static final String USER_ID = "1".repeat(32);
    private static final String OTHER_USER_ID = "9".repeat(32);
    private static final String BUSINESS_ID = "b".repeat(32);

    private SysFormDataDao dataDao;
    private SysFormDao formDao;
    private SysFormVersionDao versionDao;
    private SysFormDataService service;
    private MockedStatic<UserUtil> userUtilMock;

    @BeforeEach
    void setUp() {
        dataDao = mock(SysFormDataDao.class);
        formDao = mock(SysFormDao.class);
        versionDao = mock(SysFormVersionDao.class);
        service = new SysFormDataService(dataDao, formDao, versionDao,
                new FormDataBasicValidator(), new FormValidationEngine());
        userUtilMock = Mockito.mockStatic(UserUtil.class);
        userUtilMock.when(UserUtil::getCurrentUserId).thenReturn(USER_ID);
        userUtilMock.when(UserUtil::getCurrentUser).thenReturn(sessionUser(null));
    }

    @AfterEach
    void tearDown() {
        userUtilMock.close();
    }

    // ---------------------------------------------------------------- save：保存校验链与落库

    @Test
    void savePersistsNormalizedPayloadBoundToSessionUser() {
        mockPublishedForm();

        SysFormDataSaveRequest request = new SysFormDataSaveRequest();
        request.setFieldId(DATA_ID);
        request.setFormId(FORM_ID);
        request.setBusinessId(BUSINESS_ID);
        request.setFormData(validFormData());

        SysFormDataSavedVo vo = service.save(request);

        ArgumentCaptor<SysFormData> captor = ArgumentCaptor.forClass(SysFormData.class);
        verify(dataDao).insert(captor.capture());
        SysFormData row = captor.getValue();
        assertEquals(DATA_ID, row.getFieldId(), "fieldId 沿用前端 getInitID 预取值");
        assertEquals(FORM_ID, row.getFieldFormId());
        assertEquals(VERSION_ID, row.getFieldFormVersionId(), "S0 按主档指针锁定当前发布版本行（原则 3）");
        assertEquals(BUSINESS_ID, row.getFieldBusinessId(), "businessId 随请求绑定");
        assertNull(row.getFieldProcessInstanceId(), "processInstanceId 为 v1.8 预留列，一期不写入");
        assertEquals(USER_ID, row.getFieldCreatorId(), "field_creator_id 由服务端绑定会话用户（R06）");
        assertNotNull(row.getFieldCreateTime());

        String stored = row.getFieldFormData().toString();
        assertTrue(stored.contains("\"applicant\":\"张三\""), "落库为归一化 JSON: " + stored);
        assertTrue(stored.contains("\"payType\":\"transfer\""), stored);
        assertTrue(stored.contains("\"productName\":\"电脑\""), "明细行为扁平行对象数组: " + stored);

        assertEquals(DATA_ID, vo.getFieldId());
        assertEquals("张三", vo.getFormData().get("applicant"), "响应 formData 为服务端最终落库形态");
    }

    @Test
    void saveNormalizesDetailRowStructureAndKeyOrder() {
        mockPublishedForm();

        SysFormDataSaveRequest request = new SysFormDataSaveRequest();
        request.setFieldId(DATA_ID);
        request.setFormId(FORM_ID);
        // 载荷键序打乱：明细行 quantity 在 productName 前、顶层 purchaseDetails 在 applicant 前
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("quantity", 2);
        row.put("productName", "电脑");
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("purchaseDetails", List.of(row));
        formData.put("applicant", "张三");
        formData.put("payType", "cash");
        request.setFormData(formData);

        service.save(request);

        ArgumentCaptor<SysFormData> captor = ArgumentCaptor.forClass(SysFormData.class);
        verify(dataDao).insert(captor.capture());
        String stored = captor.getValue().getFieldFormData().toString();
        assertTrue(stored.indexOf("\"applicant\"") < stored.indexOf("\"payType\"")
                        && stored.indexOf("\"payType\"") < stored.indexOf("\"purchaseDetails\""),
                "顶层键按 Schema 字段序重排: " + stored);
        assertTrue(stored.indexOf("\"productName\"") < stored.indexOf("\"quantity\""),
                "明细行内键按 Schema 行内字段序重排（明细行数据结构归一）: " + stored);
    }

    @Test
    void saveRejectsUnknownForm() {
        when(formDao.selectById(FORM_ID)).thenReturn(null);
        assertFormError(() -> service.save(saveRequest(validFormData())), CS_FORM_NOT_FOUND);
        verify(dataDao, never()).insert(any(SysFormData.class));
    }

    @Test
    void saveRejectsDisabledForm() {
        when(formDao.selectById(FORM_ID)).thenReturn(formRow(Boolean.FALSE));
        String message = assertFormError(() -> service.save(saveRequest(validFormData())), CS_FORM_DISABLED);
        assertTrue(message.contains("停用"), "停用关闭填写入口与数据提交（定义面与数据查询保留）: " + message);
        verify(dataDao, never()).insert(any(SysFormData.class));
    }

    @Test
    void saveRejectsFormWithoutPublishedVersion() {
        when(formDao.selectById(FORM_ID)).thenReturn(formRow(Boolean.TRUE));
        when(versionDao.selectById(VERSION_ID)).thenReturn(null);
        assertFormError(() -> service.save(saveRequest(validFormData())), CS_FORM_NO_PUBLISHED_VERSION);
        verify(dataDao, never()).insert(any(SysFormData.class));
    }

    @Test
    void saveRejectsUnknownField() {
        mockPublishedForm();
        Map<String, Object> formData = validFormData();
        formData.put("hackerField", "注入值");

        String message = assertFormError(() -> service.save(saveRequest(formData)), CS_FORM_UNKNOWN_FIELD);
        assertTrue(message.contains("hackerField"), "未知字段拒绝不做静默裁剪（分册 §7.4①）: " + message);
        verify(dataDao, never()).insert(any(SysFormData.class));
    }

    @Test
    void saveRejectsUnknownRowField() {
        mockPublishedForm();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("productName", "电脑");
        row.put("rowUnknown", "行内未知键");
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("applicant", "张三");
        formData.put("payType", "cash");
        formData.put("purchaseDetails", List.of(row));

        String message = assertFormError(() -> service.save(saveRequest(formData)), CS_FORM_UNKNOWN_FIELD);
        assertTrue(message.contains("rowUnknown"), "明细行内未知键同样拒绝（任意层级）: " + message);
        verify(dataDao, never()).insert(any(SysFormData.class));
    }

    @Test
    void saveRejectsBasicInvalidWithFirstErrorSummary() {
        mockPublishedForm();
        Map<String, Object> formData = validFormData();
        formData.remove("applicant");
        formData.remove("payType");

        String message = assertFormError(() -> service.save(saveRequest(formData)), CS_FORM_DATA_INVALID);
        assertTrue(message.contains("申请人"), "message 含首条错误中文摘要（分册 §7.1 第 12 项）: " + message);
        verify(dataDao, never()).insert(any(SysFormData.class));
    }

    @Test
    void saveRejectsMissingFormData() {
        SysFormDataSaveRequest request = new SysFormDataSaveRequest();
        request.setFieldId(DATA_ID);
        request.setFormId(FORM_ID);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(request));
        assertTrue(error.getMessage().contains("formData"), error.getMessage());
        verify(dataDao, never()).insert(any(SysFormData.class));
    }

    // ---------------------------------------------------------------- validate：独立校验入口

    @Test
    void validatePassReturnsPreviewWithoutPersist() {
        mockPublishedForm();

        SysFormDataValidateRequest request = new SysFormDataValidateRequest();
        request.setFormId(FORM_ID);
        request.setBusinessId(BUSINESS_ID);
        request.setFormData(validFormData());

        SysFormDataValidateVo vo = service.validate(request);

        assertTrue(vo.isPass(), "S2 通过时 pass=true");
        assertNotNull(vo.getErrors());
        assertTrue(vo.getErrors().isEmpty(), "pass=true 时 errors 为空数组");
        assertEquals("张三", vo.getFormData().get("applicant"), "pass=true 回带最终数据预览（同 save 落库形态）");
        verify(dataDao, never()).insert(any(SysFormData.class));
    }

    @Test
    void validateFailCarriesStructuredErrorsWithoutThrowing() {
        mockPublishedForm();

        SysFormDataValidateRequest request = new SysFormDataValidateRequest();
        request.setFormId(FORM_ID);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("quantity", 2);
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("payType", "cash");
        formData.put("purchaseDetails", List.of(row));
        request.setFormData(formData);

        SysFormDataValidateVo vo = service.validate(request);

        assertFalse(vo.isPass(), "S2 失败时 pass=false（字段级结果为正常业务结果，不抛异常）");
        assertNull(vo.getFormData(), "pass=false 时 formData 为 null");
        List<SysFormDataErrorDo> errors = vo.getErrors();
        assertEquals(2, errors.size(), "主表必填 + 明细行内必填两条: " + errors);
        assertEquals("applicant", errors.get(0).getFieldCode());
        assertNull(errors.get(0).getRowIndex(), "主表字段错误 rowIndex 为 null");
        assertEquals("productName", errors.get(1).getFieldCode());
        assertEquals(Integer.valueOf(0), errors.get(1).getRowIndex(), "明细行内错误携带 0 起始行号");
        assertNull(errors.get(0).getRuleId(), "基础校验错误 ruleId 恒 null（动态校验随 F15-BE-004 并联回填）");
        verify(dataDao, never()).insert(any(SysFormData.class));
    }

    @Test
    void validateRejectsUnknownFieldWithErrorCode() {
        mockPublishedForm();
        Map<String, Object> formData = validFormData();
        formData.put("hackerField", "注入值");

        // S1 确定性拒绝仍以 CS_FORM_* 异常返回（与 save 同语义，不以 errors 承载）
        assertFormError(() -> {
            SysFormDataValidateRequest request = new SysFormDataValidateRequest();
            request.setFormId(FORM_ID);
            request.setFormData(formData);
            service.validate(request);
        }, CS_FORM_UNKNOWN_FIELD);
        verify(dataDao, never()).insert(any(SysFormData.class));
    }

    @Test
    void validateRejectsResourcePreconditionsLikeSave() {
        // S0 与 save 同序同语义：资源类拒绝仍以 CS_FORM_* 异常返回
        when(formDao.selectById(FORM_ID)).thenReturn(formRow(Boolean.FALSE));
        SysFormDataValidateRequest request = new SysFormDataValidateRequest();
        request.setFormId(FORM_ID);
        request.setFormData(validFormData());
        assertFormError(() -> service.validate(request), CS_FORM_DISABLED);
    }

    // ---------------------------------------------------------------- 动态校验与权限裁剪（F15-BE-004 并联）

    @Test
    void saveTrimsRuleHiddenFieldFromPersistedPayload() {
        mockPublishedVersionWithSchema(ruleSchemaJson());

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("applicant", "张三");
        formData.put("payType", "cash");
        formData.put("bankAccount", "6222020200112233445");

        SysFormDataSavedVo vo = service.save(saveRequest(formData));

        assertFalse(vo.getFormData().containsKey("bankAccount"),
                "显隐复算判定隐藏的字段值裁剪不落库（§3.6 持久层，响应同落库形态）");
        ArgumentCaptor<SysFormData> captor = ArgumentCaptor.forClass(SysFormData.class);
        verify(dataDao).insert(captor.capture());
        assertFalse(String.valueOf(captor.getValue().getFieldFormData()).contains("bankAccount"),
                "落库 JSON 不含 hidden 字段值: " + captor.getValue().getFieldFormData());
    }

    @Test
    void saveKeepsVisibleDynamicFieldWithTransfer() {
        mockPublishedVersionWithSchema(ruleSchemaJson());

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("applicant", "张三");
        formData.put("payType", "transfer");
        formData.put("bankAccount", "6222020200112233445");

        SysFormDataSavedVo vo = service.save(saveRequest(formData));

        assertEquals("6222020200112233445", vo.getFormData().get("bankAccount"),
                "付款方式=转账时规则 show 生效，银行账号照常落库");
    }

    @Test
    void saveRejectsDynamicRequiredViolation() {
        mockPublishedVersionWithSchema(ruleSchemaJson());

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("applicant", "张三");
        formData.put("payType", "transfer");

        String message = assertFormError(() -> service.save(saveRequest(formData)), CS_FORM_DATA_INVALID);
        assertTrue(message.contains("银行账号"),
                "条件必填（付款方式=转账时银行账号必填）服务端权威裁决: " + message);
        verify(dataDao, never()).insert(any(SysFormData.class));
    }

    @Test
    void validateCarriesRuleIdOnDynamicError() {
        mockPublishedVersionWithSchema(ruleSchemaJson());

        SysFormDataValidateRequest request = new SysFormDataValidateRequest();
        request.setFormId(FORM_ID);
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("applicant", "张三");
        formData.put("payType", "transfer");
        request.setFormData(formData);

        SysFormDataValidateVo vo = service.validate(request);

        assertFalse(vo.isPass(), "动态必填未满足 pass=false");
        assertEquals(1, vo.getErrors().size(), "动态段一条错误: " + vo.getErrors());
        assertEquals("bankAccount", vo.getErrors().get(0).getFieldCode());
        assertEquals("rule_001", vo.getErrors().get(0).getRuleId(),
                "动态校验错误 ruleId 溯源触发规则（分册 §4.4 errors 结构）");
    }

    @Test
    void validatePassesWhenDynamicRequiredRelaxedByRule() {
        // 条件必填反例：payType=cash 时 rule_002 setRequired(false) 覆盖 + hide，bankAccount 缺省通过
        mockPublishedVersionWithSchema(ruleSchemaJson());

        SysFormDataValidateRequest request = new SysFormDataValidateRequest();
        request.setFormId(FORM_ID);
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("applicant", "张三");
        formData.put("payType", "cash");
        request.setFormData(formData);

        SysFormDataValidateVo vo = service.validate(request);

        assertTrue(vo.isPass(), "setRequired(false) 动态覆盖静态基线，hidden 字段不参与必填: " + vo.getErrors());
        assertNotNull(vo.getFormData());
        assertFalse(vo.getFormData().containsKey("bankAccount"), "预览同 save 落库形态（hidden 裁剪）");
    }

    @Test
    void loadClipsPermissionHiddenFieldFromStoredData() {
        when(dataDao.selectById(DATA_ID)).thenReturn(dataRowWithJson(
                "{\"applicant\":\"张三\",\"secretField\":\"机密值\"}"));
        when(formDao.selectById(FORM_ID)).thenReturn(formRow(Boolean.TRUE));
        when(versionDao.selectById(VERSION_ID)).thenReturn(versionRowWithSchema(permissionHiddenSchemaJson()));

        SysFormDataDetailVo vo = service.load(DATA_ID);

        assertFalse(vo.getFormData().containsKey("secretField"),
                "permission=hidden 字段读取不下发（要点第 7 步红线：前端隐藏 ≠ 后端禁止访问）");
        assertEquals("张三", vo.getFormData().get("applicant"), "可见字段照常下发");
    }

    // ---------------------------------------------------------------- load：回显与归属判定

    @Test
    void loadReturnsDetailWithLockedVersionSchema() {
        SysFormData dataRow = dataRow(USER_ID);
        when(dataDao.selectById(DATA_ID)).thenReturn(dataRow);
        when(formDao.selectById(FORM_ID)).thenReturn(formRow(Boolean.TRUE));
        when(versionDao.selectById(VERSION_ID)).thenReturn(versionRow());

        SysFormDataDetailVo vo = service.load(DATA_ID);

        assertEquals(DATA_ID, vo.getFieldId());
        assertEquals(FORM_ID, vo.getFieldFormId());
        assertEquals(VERSION_ID, vo.getFieldFormVersionId(), "实例锁定版本行（原则 2：在途实例用原版本）");
        assertEquals(BUSINESS_ID, vo.getFieldBusinessId());
        assertEquals(USER_ID, vo.getFieldCreatorId());
        assertNotNull(vo.getFieldCreateTime());
        assertEquals("张三", vo.getFormData().get("applicant"), "formData 解析为字段值对象");
        assertNotNull(vo.getSchema(), "回显含实例锁定版本 Schema");
        assertEquals("purchase_apply", vo.getSchema().getFormCode(), "协议头以主档权威元数据归一");
        assertEquals(1, vo.getSchema().getVersion(), "协议头 version 回填行级版本号");
        assertEquals(3, vo.getSchema().getFields().size(), "Schema 主体内容随锁定版本下发");
    }

    @Test
    void loadAllowsFormAdminForForeignData() {
        SysFormData dataRow = dataRow(OTHER_USER_ID);
        when(dataDao.selectById(DATA_ID)).thenReturn(dataRow);
        when(formDao.selectById(FORM_ID)).thenReturn(formRow(Boolean.TRUE));
        when(versionDao.selectById(VERSION_ID)).thenReturn(versionRow());
        userUtilMock.when(UserUtil::getCurrentUser).thenReturn(sessionUser(SysFormConstants.ROLE_SYS_FORM_ADMIN));

        SysFormDataDetailVo vo = service.load(DATA_ID);
        assertEquals(OTHER_USER_ID, vo.getFieldCreatorId(), "SYS_FORM_ADMIN 可查询全部（R06 定稿）");
    }

    @Test
    void loadRejectsForeignDataForNonAdmin() {
        SysFormData dataRow = dataRow(OTHER_USER_ID);
        when(dataDao.selectById(DATA_ID)).thenReturn(dataRow);
        userUtilMock.when(UserUtil::getCurrentUser).thenReturn(sessionUser(null));

        AuthorizationException error = assertThrows(AuthorizationException.class, () -> service.load(DATA_ID));
        assertEquals("FORBIDDEN", error.getCode(), "code = FORBIDDEN（分册 §7.1 第 14 项）");
        assertTrue(error.getMessage().startsWith(CS_FORM_DATA_FORBIDDEN + ":"),
                "message 前缀 CS_FORM_DATA_FORBIDDEN: " + error.getMessage());
    }

    @Test
    void loadRejectsUnknownData() {
        when(dataDao.selectById(DATA_ID)).thenReturn(null);
        assertFormError(() -> service.load(DATA_ID), CS_FORM_DATA_NOT_FOUND);
    }

    @Test
    void loadRejectsWhenNotLoggedIn() {
        when(dataDao.selectById(DATA_ID)).thenReturn(dataRow(USER_ID));
        userUtilMock.when(UserUtil::getCurrentUser).thenReturn(null);
        AuthorizationException error = assertThrows(AuthorizationException.class, () -> service.load(DATA_ID));
        assertEquals("UNAUTHORIZED", error.getCode(), "未登录 → UNAUTHORIZED（R06 三种拒绝语义之一）");
    }

    // ---------------------------------------------------------------- delete：创建人或管理员

    @Test
    void deleteAllowsCreator() {
        when(dataDao.selectById(DATA_ID)).thenReturn(dataRow(USER_ID));
        service.delete(DATA_ID);
        verify(dataDao).deleteById(DATA_ID);
    }

    @Test
    void deleteAllowsFormAdminForForeignData() {
        when(dataDao.selectById(DATA_ID)).thenReturn(dataRow(OTHER_USER_ID));
        userUtilMock.when(UserUtil::getCurrentUser).thenReturn(sessionUser(SysFormConstants.ROLE_SYS_FORM_ADMIN));
        service.delete(DATA_ID);
        verify(dataDao).deleteById(DATA_ID);
    }

    @Test
    void deleteRejectsForeignDataForNonAdmin() {
        when(dataDao.selectById(DATA_ID)).thenReturn(dataRow(OTHER_USER_ID));
        userUtilMock.when(UserUtil::getCurrentUser).thenReturn(sessionUser(null));

        AuthorizationException error = assertThrows(AuthorizationException.class, () -> service.delete(DATA_ID));
        assertEquals("FORBIDDEN", error.getCode());
        assertTrue(error.getMessage().startsWith(CS_FORM_DATA_FORBIDDEN + ":"),
                "删除为创建人或管理员（分册 §4.7）: " + error.getMessage());
        verify(dataDao, never()).deleteById(DATA_ID);
    }

    @Test
    void deleteRejectsUnknownData() {
        when(dataDao.selectById(DATA_ID)).thenReturn(null);
        assertFormError(() -> service.delete(DATA_ID), CS_FORM_DATA_NOT_FOUND);
        verify(dataDao, never()).deleteById(DATA_ID);
    }

    // ---------------------------------------------------------------- list：归属强制过滤 + 分页

    @Test
    void listForcesCreatorFilterForNonAdmin() {
        mockPublishedForm();
        when(dataDao.selectListByQuery(any(SysFormDataQuery.class), any()))
                .thenReturn(List.of(dataRow(USER_ID)));
        userUtilMock.when(UserUtil::getCurrentUser).thenReturn(sessionUser(null));

        SysFormDataQuery query = new SysFormDataQuery();
        query.setFieldFormId(FORM_ID);
        query.setFieldCreatorId(OTHER_USER_ID);
        List<SysFormDataVo> rows = service.list(query, pages());

        ArgumentCaptor<SysFormDataQuery> captor = ArgumentCaptor.forClass(SysFormDataQuery.class);
        verify(dataDao).selectListByQuery(captor.capture(), any());
        assertEquals(USER_ID, captor.getValue().getFieldCreatorId(),
                "非管理员强制覆盖为会话用户（不信任请求体传入的用户标识，API 分册 §4.8）");
        assertEquals(1, rows.size());
        assertNotNull(rows.get(0).getFieldId());
    }

    @Test
    void listKeepsCreatorFilterForFormAdmin() {
        mockPublishedForm();
        when(dataDao.selectListByQuery(any(SysFormDataQuery.class), any())).thenReturn(List.of());
        userUtilMock.when(UserUtil::getCurrentUser).thenReturn(sessionUser(SysFormConstants.ROLE_SYS_FORM_ADMIN));

        SysFormDataQuery query = new SysFormDataQuery();
        query.setFieldFormId(FORM_ID);
        query.setFieldCreatorId(OTHER_USER_ID);
        service.list(query, pages());

        ArgumentCaptor<SysFormDataQuery> captor = ArgumentCaptor.forClass(SysFormDataQuery.class);
        verify(dataDao).selectListByQuery(captor.capture(), any());
        assertEquals(OTHER_USER_ID, captor.getValue().getFieldCreatorId(),
                "fieldCreatorId 筛选仅管理员生效（保留传入条件）");
    }

    @Test
    void countAppliesSameForcedInjection() {
        mockPublishedForm();
        when(dataDao.selectCountByQuery(any(SysFormDataQuery.class))).thenReturn(3L);
        userUtilMock.when(UserUtil::getCurrentUser).thenReturn(sessionUser(null));

        SysFormDataQuery query = new SysFormDataQuery();
        query.setFieldFormId(FORM_ID);
        assertEquals(3L, service.count(query));

        ArgumentCaptor<SysFormDataQuery> captor = ArgumentCaptor.forClass(SysFormDataQuery.class);
        verify(dataDao).selectCountByQuery(captor.capture());
        assertEquals(USER_ID, captor.getValue().getFieldCreatorId(),
                "count 条件与 list 完全一致（含归属强制注入，供 metadata.total）");
    }

    @Test
    void listRejectsMissingFormId() {
        SysFormDataQuery query = new SysFormDataQuery();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.list(query, pages()));
        assertTrue(error.getMessage().contains("fieldFormId"), "fieldFormId 必填（单一表单维度）: " + error.getMessage());
    }

    @Test
    void listRejectsUnknownForm() {
        when(formDao.selectById(FORM_ID)).thenReturn(null);
        SysFormDataQuery query = new SysFormDataQuery();
        query.setFieldFormId(FORM_ID);
        assertFormError(() -> service.list(query, pages()), CS_FORM_NOT_FOUND);
    }

    @Test
    void listRowsDoNotCarryFormDataContent() {
        mockPublishedForm();
        SysFormData row = dataRow(USER_ID);
        row.setFieldFormData("{\"applicant\":\"张三\"}");
        when(dataDao.selectListByQuery(any(SysFormDataQuery.class), any())).thenReturn(List.of(row));

        SysFormDataQuery query = new SysFormDataQuery();
        query.setFieldFormId(FORM_ID);
        List<SysFormDataVo> rows = service.list(query, pages());
        assertEquals(1, rows.size());
        assertEquals(DATA_ID, rows.get(0).getFieldId());
        assertEquals(VERSION_ID, rows.get(0).getFieldFormVersionId());
        assertFalse(rows.get(0).toString().contains("applicant"),
                "列表行不携带 formData 字段值内容（分册 §4.6）: " + rows.get(0));
    }

    // ---------------------------------------------------------------- 测试小件

    private static SysFormDataSaveRequest saveRequest(Map<String, Object> formData) {
        SysFormDataSaveRequest request = new SysFormDataSaveRequest();
        request.setFieldId(DATA_ID);
        request.setFormId(FORM_ID);
        request.setBusinessId(BUSINESS_ID);
        request.setFormData(formData);
        return request;
    }

    /** 合法载荷（Schema 全部字段就位） */
    private static Map<String, Object> validFormData() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("productName", "电脑");
        row.put("quantity", 2);
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("applicant", "张三");
        formData.put("payType", "transfer");
        formData.put("purchaseDetails", List.of(row));
        return formData;
    }

    private void mockPublishedForm() {
        when(formDao.selectById(FORM_ID)).thenReturn(formRow(Boolean.TRUE));
        when(versionDao.selectById(VERSION_ID)).thenReturn(versionRow());
    }

    /** 指定 Schema 原文的发布版本行（F15-BE-004 规则 / 权限用例） */
    private void mockPublishedVersionWithSchema(String schemaJson) {
        when(formDao.selectById(FORM_ID)).thenReturn(formRow(Boolean.TRUE));
        when(versionDao.selectById(VERSION_ID)).thenReturn(versionRowWithSchema(schemaJson));
    }

    /** 指定 Schema 原文的版本行（读取层裁剪用例） */
    private static SysFormVersion versionRowWithSchema(String schemaJson) {
        SysFormVersion row = versionRow();
        row.setFieldSchemaJson(schemaJson);
        return row;
    }

    /**
     * 规则 Schema 存储态原文（功能设计 §2.3 示例 rule_001/002 同构：付款方式=转账时银行
     * 账号必填并显示，否则豁免必填并隐藏——条件必填正反例 + 条件显隐 + hidden 静态初始态）
     */
    private static String ruleSchemaJson() {
        SysFormFieldDo applicant = field("applicant", "input", "申请人");
        applicant.setProps(Map.of("required", true));
        SysFormFieldDo payType = field("payType", "select", "付款方式");
        payType.setProps(Map.of("multi", false,
                "options", List.of(option("cash", "现金"), option("transfer", "转账"))));
        payType.setRules(List.of(
                rule("rule_001", "payType", condition("equal", "transfer"),
                        action("setRequired", "bankAccount", Boolean.TRUE),
                        action("show", "bankAccount", null)),
                rule("rule_002", "payType", condition("notEqual", "transfer"),
                        action("setRequired", "bankAccount", Boolean.FALSE),
                        action("hide", "bankAccount", null))));
        SysFormFieldDo bankAccount = field("bankAccount", "input", "银行账号");
        bankAccount.setProps(new LinkedHashMap<>(Map.of("maxlength", 32)));
        bankAccount.getProps().put("hidden", true);
        return schemaJsonOf(applicant, payType, bankAccount);
    }

    /** permission=hidden 字段 Schema 存储态原文（权限裁剪读取层用例） */
    private static String permissionHiddenSchemaJson() {
        SysFormFieldDo applicant = field("applicant", "input", "申请人");
        SysFormFieldDo secret = field("secretField", "input", "内部备注");
        secret.setProps(Map.of("permission", "hidden"));
        return schemaJsonOf(applicant, secret);
    }

    /** Schema POJO → 存储态原文（Jackson 序列化，杜绝手写 JSON 配平错误） */
    private static String schemaJsonOf(SysFormFieldDo... fields) {
        SysFormSchemaDo schema = new SysFormSchemaDo();
        schema.setFormId(FORM_ID);
        schema.setFormCode("purchase_apply");
        schema.setFormName("采购申请表");
        schema.setVersion(1);
        schema.setFields(List.of(fields));
        try {
            return JSON.writeValueAsString(schema);
        } catch (Exception e) {
            throw new IllegalStateException("测试 Schema 序列化失败", e);
        }
    }

    /** 规则 DSL 构造（event 恒 onChange——服务端复算消费的主力事件） */
    private static SysFormRuleDo rule(String ruleId, String triggerField,
                                      SysFormRuleConditionDo condition, SysFormRuleActionDo... actions) {
        SysFormRuleDo rule = new SysFormRuleDo();
        rule.setRuleId(ruleId);
        rule.setEvent("onChange");
        rule.setTriggerField(triggerField);
        rule.setCondition(condition);
        rule.setActions(List.of(actions));
        return rule;
    }

    private static SysFormRuleConditionDo condition(String operator, Object value) {
        SysFormRuleConditionDo condition = new SysFormRuleConditionDo();
        condition.setOperator(operator);
        condition.setValue(value);
        return condition;
    }

    private static SysFormRuleActionDo action(String type, String target, Object value) {
        SysFormRuleActionDo action = new SysFormRuleActionDo();
        action.setType(type);
        action.setTarget(target);
        action.setValue(value);
        return action;
    }

    /** 指定 formData 原文的数据行（读取层裁剪用例） */
    private static SysFormData dataRowWithJson(String formDataJson) {
        SysFormData row = dataRow(USER_ID);
        row.setFieldFormData(formDataJson);
        return row;
    }

    /**
     * 测试版本 Schema 存储态原文（POJO + Jackson 序列化，杜绝手写 JSON 配平错误）：
     * 必填文本 + 枚举下拉（静态 options）+ 明细表（rowMin/rowMax，行内必填文本 + 数值）
     */
    private static String schemaJson() {
        SysFormFieldDo applicant = field("applicant", "input", "申请人");
        applicant.setProps(Map.of("required", true, "maxlength", 50));
        SysFormFieldDo payType = field("payType", "select", "付款方式");
        payType.setProps(Map.of("multi", false,
                "options", List.of(option("cash", "现金"), option("transfer", "转账"))));
        SysFormFieldDo detail = field("purchaseDetails", "detailTable", "采购明细");
        detail.setProps(Map.of("rowMin", 1, "rowMax", 50));
        SysFormFieldDo productName = field("productName", "input", "商品名称");
        productName.setProps(Map.of("required", true, "maxlength", 100));
        SysFormFieldDo quantity = field("quantity", "number", "数量");
        detail.setChildren(List.of(productName, quantity));
        SysFormSchemaDo schema = new SysFormSchemaDo();
        schema.setFormId(FORM_ID);
        schema.setFormCode("purchase_apply");
        schema.setFormName("采购申请表");
        schema.setVersion(1);
        schema.setFields(List.of(applicant, payType, detail));
        try {
            return JSON.writeValueAsString(schema);
        } catch (Exception e) {
            throw new IllegalStateException("测试 Schema 序列化失败", e);
        }
    }

    private static SysFormFieldDo field(String code, String type, String label) {
        SysFormFieldDo field = new SysFormFieldDo();
        field.setField(code);
        field.setType(type);
        field.setLabel(label);
        return field;
    }

    private static Map<String, Object> option(String value, String label) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("label", label);
        option.put("value", value);
        return option;
    }

    private static SysForm formRow(Boolean enable) {
        SysForm row = new SysForm();
        row.setFieldId(FORM_ID);
        row.setFieldFormCode("purchase_apply");
        row.setFieldFormName("采购申请表");
        row.setFieldStatus("PUBLISHED");
        row.setFieldCurrentVersionId(VERSION_ID);
        row.setFieldEnable(enable);
        row.setFieldCreatorId(USER_ID);
        row.setFieldCreateTime(new Date());
        return row;
    }

    private static SysFormVersion versionRow() {
        SysFormVersion row = new SysFormVersion();
        row.setFieldId(VERSION_ID);
        row.setFieldFormId(FORM_ID);
        row.setFieldVersion(1);
        row.setFieldSchemaJson(schemaJson());
        row.setFieldStatus(SysFormVersionStatus.PUBLISHED.name());
        row.setFieldPublishedAt(new Date());
        row.setFieldEnable(Boolean.TRUE);
        row.setFieldCreatorId(USER_ID);
        row.setFieldCreateTime(new Date());
        return row;
    }

    private static SysFormData dataRow(String creatorId) {
        SysFormData row = new SysFormData();
        row.setFieldId(DATA_ID);
        row.setFieldFormId(FORM_ID);
        row.setFieldFormVersionId(VERSION_ID);
        row.setFieldBusinessId(BUSINESS_ID);
        row.setFieldProcessInstanceId(null);
        row.setFieldFormData("{\"applicant\":\"张三\",\"payType\":\"transfer\"}");
        row.setFieldCreatorId(creatorId);
        row.setFieldCreateTime(new Date());
        return row;
    }

    private static Pages pages() {
        Pages pages = new Pages();
        pages.setPageNo(1);
        pages.setPageSize(10);
        pages.setNeedPage(true);
        return pages;
    }

    /** 会话用户（roleCode 为 null 时无角色——普通登录用户；SYS_FORM_ADMIN 管理员） */
    private static SysOrgUser sessionUser(String roleCode) {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(USER_ID);
        user.setFieldName("张三");
        user.setFieldEnable(Boolean.TRUE);
        if (null != roleCode) {
            SysOrgRole role = new SysOrgRole();
            role.setFieldCode(roleCode);
            role.setFieldName("自定义表单管理员");
            user.setRoles(List.of(role));
        } else {
            user.setRoles(List.of());
        }
        return user;
    }

    /** 断言 IllegalArgumentException 且 message 以期望错误码为前缀，返回完整消息 */
    private static String assertFormError(Runnable runnable, String expectedCode) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, runnable::run);
        assertTrue(null != error.getMessage() && error.getMessage().startsWith(expectedCode + ":"),
                "错误码应为 " + expectedCode + "，实际: " + error.getMessage());
        return error.getMessage();
    }
}
