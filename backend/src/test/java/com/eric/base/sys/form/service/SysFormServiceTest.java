package com.eric.base.sys.form.service;

import com.eric.base.common.base.model.Pages;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.form.dao.SysFormDataDao;
import com.eric.base.sys.form.dao.SysFormDao;
import com.eric.base.sys.form.dao.SysFormVersionDao;
import com.eric.base.sys.form.enums.SysFormStatus;
import com.eric.base.sys.form.enums.SysFormVersionStatus;
import com.eric.base.sys.form.model.SysForm;
import com.eric.base.sys.form.model.SysFormFieldDo;
import com.eric.base.sys.form.model.SysFormSchemaDo;
import com.eric.base.sys.form.model.SysFormVersion;
import com.eric.base.sys.form.properties.SysFormConstants;
import com.eric.base.sys.form.request.SysFormRollbackRequest;
import com.eric.base.sys.form.request.SysFormSaveRequest;
import com.eric.base.sys.form.request.SysFormUpdateRequest;
import com.eric.base.sys.form.response.SysFormVersionCreatedVo;
import com.eric.base.sys.form.response.SysFormVersionVo;
import com.eric.base.sys.form.response.SysFormVo;
import com.eric.base.sys.form.service.engine.FormSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Date;
import java.util.List;

import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_CODE_DUPLICATED;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_HAS_DATA;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_NO_DRAFT;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_NOT_FOUND;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_SCHEMA_INVALID;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_SCHEMA_LIMIT_EXCEEDED;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_VERSION_NOT_FOUND;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_VERSION_STATE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 表单定义与版本管理服务测试
 *
 * @author Eric
 * @date 2026/11/24
 * @description 覆盖任务 F15-BE-002 验收标准（Dao mock 桩 + 真实 FormSchemaValidator，
 *              MockedStatic 会话态——形态对齐 SysFormulaServiceTest / FormulaServiceTestSupport
 *              先例）：编码全局查重（CS_FORM_CODE_DUPLICATED）、保存 / 更新前 Schema 结构校验
 *              阻断（CS_FORM_SCHEMA_INVALID / CS_FORM_SCHEMA_LIMIT_EXCEEDED）、发布校验链
 *              P1 无草稿拒绝（CS_FORM_NO_DRAFT）、发布快照（草稿行原地冻结 + 版本号分配 +
 *              原当前版本退役 + 主档指针切换与状态同步）、已发布版本禁改（更新只写 DRAFT 行 +
 *              冻结行防御性拒绝 CS_FORM_VERSION_STATE_INVALID）、回滚产生新版本（复制副本、
 *              不回拨指针、草稿目标与跨表单目标拒绝）、删除前置实例数据校验（CS_FORM_HAS_DATA）、
 *              启停用只更新自身、版本列表（草稿行 fieldVersion null 透出）。
 */
class SysFormServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 32 位测试 ID（避免 BaseModel.setFieldId 触发重新生成） */
    private static final String FORM_ID = "f".repeat(32);
    private static final String DRAFT_ID = "d".repeat(32);
    private static final String V1_ID = "a".repeat(32);
    private static final String V2_ID = "b".repeat(32);
    private static final String USER_ID = "1".repeat(32);
    private static final String FORM_CODE = "purchase.request";

    /** 非法 Schema 样例：同一数据层级字段码重复（校验器 CS_FORM_SCHEMA_INVALID） */
    private static final String DUPLICATED_CODE_SCHEMA = "{\"fields\":["
            + "{\"field\":\"amount\",\"type\":\"input\",\"label\":\"金额A\"},"
            + "{\"field\":\"amount\",\"type\":\"input\",\"label\":\"金额B\"}]}";

    /** 非法 Schema 样例：type 白名单外（ADR-10 未注册 type） */
    private static final String UNKNOWN_TYPE_SCHEMA =
            "{\"fields\":[{\"field\":\"amount\",\"type\":\"widget\",\"label\":\"金额\"}]}";

    private SysFormDao formDao;
    private SysFormVersionDao versionDao;
    private SysFormDataDao dataDao;
    private SysFormService service;
    private MockedStatic<UserUtil> userUtilMock;

    @BeforeEach
    void setUp() {
        formDao = mock(SysFormDao.class);
        versionDao = mock(SysFormVersionDao.class);
        dataDao = mock(SysFormDataDao.class);
        service = new SysFormService(formDao, versionDao, dataDao, new FormSchemaValidator());
        userUtilMock = Mockito.mockStatic(UserUtil.class);
        userUtilMock.when(UserUtil::getCurrentUserId).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        userUtilMock.close();
    }

    // ---------------------------------------------------------------- save：主档 + 草稿行

    @Test
    void savePersistsMasterAndBlankDraftWhenSchemaAbsent() {
        when(formDao.selectByCode(FORM_CODE)).thenReturn(null);
        when(formDao.selectById(FORM_ID)).thenReturn(formRow(SysFormStatus.DRAFT, null));
        when(versionDao.selectList(any(SysFormVersion.class), any()))
                .thenReturn(List.of(draftRow(FORM_ID, null)));

        SysFormSaveRequest request = new SysFormSaveRequest();
        request.setFieldId(FORM_ID);
        request.setFieldFormCode(FORM_CODE);
        request.setFieldFormName("采购申请");

        SysFormVo vo = service.save(request);

        ArgumentCaptor<SysForm> masterCaptor = ArgumentCaptor.forClass(SysForm.class);
        verify(formDao).insert(masterCaptor.capture());
        SysForm master = masterCaptor.getValue();
        assertEquals(FORM_ID, master.getFieldId(), "fieldId 沿用前端 getInitID 预取值");
        assertEquals(FORM_CODE, master.getFieldFormCode());
        assertEquals("采购申请", master.getFieldFormName());
        assertEquals(SysFormStatus.DRAFT.name(), master.getFieldStatus(), "新建主档为 DRAFT（从未发布）");
        assertNull(master.getFieldCurrentVersionId(), "从未发布无当前版本指针");
        assertEquals(Boolean.TRUE, master.getFieldEnable(), "新建默认启用");
        assertEquals(USER_ID, master.getFieldCreatorId(), "审计字段取当前会话用户");
        assertEquals(USER_ID, master.getFieldUpdaterId());

        ArgumentCaptor<SysFormVersion> draftCaptor = ArgumentCaptor.forClass(SysFormVersion.class);
        verify(versionDao).insert(draftCaptor.capture());
        SysFormVersion draft = draftCaptor.getValue();
        assertEquals(FORM_ID, draft.getFieldFormId());
        assertNull(draft.getFieldVersion(), "草稿行不分配版本号（发布时原地冻结回填）");
        assertEquals(SysFormVersionStatus.DRAFT.name(), draft.getFieldStatus());
        assertNull(draft.getFieldPublishedAt());
        assertNotNull(draft.getFieldSchemaJson(), "save 缺省时空白草稿初始化");
        assertTrue(draft.getFieldSchemaJson().toString().contains("\"fields\":[]"),
                "空白草稿 fields 为空数组: " + draft.getFieldSchemaJson());
        assertEquals(Boolean.TRUE, draft.getFieldEnable(), "版本行 field_enable 内建列恒 TRUE（无业务语义）");

        assertEquals(Boolean.TRUE, vo.getHasDraft(), "响应 hasDraft 按草稿行存在性预判");
        assertNull(vo.getFieldCurrentVersionId());
    }

    @Test
    void saveNormalizesSchemaHeaderAndIgnoresCarriedVersion() {
        when(formDao.selectByCode(FORM_CODE)).thenReturn(null);
        when(formDao.selectById(FORM_ID)).thenReturn(formRow(SysFormStatus.DRAFT, null));
        when(versionDao.selectList(any(SysFormVersion.class), any()))
                .thenReturn(List.of(draftRow(FORM_ID, null)));

        SysFormSchemaDo carried = schema("stale-form", "stale-code", "旧协议头", 9,
                field("amount", "number", "金额"));
        SysFormSaveRequest request = new SysFormSaveRequest();
        request.setFieldId(FORM_ID);
        request.setFieldFormCode(FORM_CODE);
        request.setFieldFormName("采购申请");
        request.setSchema(carried);
        service.save(request);

        ArgumentCaptor<SysFormVersion> draftCaptor = ArgumentCaptor.forClass(SysFormVersion.class);
        verify(versionDao).insert(draftCaptor.capture());
        String stored = draftCaptor.getValue().getFieldSchemaJson().toString();
        assertTrue(stored.contains("\"formId\":\"" + FORM_ID + "\""), "协议头 formId 以请求体顶层元数据归一: " + stored);
        assertTrue(stored.contains("\"formCode\":\"" + FORM_CODE + "\""), "协议头 formCode 以请求体顶层元数据归一");
        assertTrue(stored.contains("\"formName\":\"采购申请\""), "协议头 formName 以请求体顶层元数据归一");
        assertFalse(stored.contains("\"version\""), "version 由服务端管理，请求携带一律忽略: " + stored);
        assertFalse(stored.contains("stale"), "不信任请求载荷协议头: " + stored);
        assertTrue(stored.contains("\"amount\""), "fields 主体内容保留");
    }

    @Test
    void saveRejectsDuplicatedCode() {
        when(formDao.selectByCode(FORM_CODE)).thenReturn(formRow(SysFormStatus.DRAFT, null));

        SysFormSaveRequest request = saveRequest();
        String message = assertFormError(() -> service.save(request), CS_FORM_CODE_DUPLICATED);
        assertTrue(message.contains(FORM_CODE), message);
        verify(formDao, never()).insert(any(SysForm.class));
        verify(versionDao, never()).insert(any(SysFormVersion.class));
    }

    @Test
    void saveBlocksInvalidSchemaBeforePersist() {
        when(formDao.selectByCode(FORM_CODE)).thenReturn(null);

        SysFormSchemaDo invalid = parse(DUPLICATED_CODE_SCHEMA);
        SysFormSaveRequest request = saveRequest();
        request.setSchema(invalid);
        String message = assertFormError(() -> service.save(request), CS_FORM_SCHEMA_INVALID);
        assertTrue(message.contains("amount"), "逐条中文原因随 message 附带: " + message);
        verify(formDao, never()).insert(any(SysForm.class));
        verify(versionDao, never()).insert(any(SysFormVersion.class));
    }

    @Test
    void saveBlocksUnknownTypeSchema() {
        when(formDao.selectByCode(FORM_CODE)).thenReturn(null);

        SysFormSaveRequest request = saveRequest();
        request.setSchema(parse(UNKNOWN_TYPE_SCHEMA));
        assertFormError(() -> service.save(request), CS_FORM_SCHEMA_INVALID);
        verify(formDao, never()).insert(any(SysForm.class));
    }

    // ---------------------------------------------------------------- update：只写 DRAFT 行

    @Test
    void updateRewritesDraftRowInPlaceAndUpdatesName() {
        SysForm exist = formRow(SysFormStatus.PUBLISHED, V2_ID);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        when(formDao.update(any(SysForm.class))).thenReturn(1);
        when(versionDao.update(any(SysFormVersion.class))).thenReturn(1);
        when(versionDao.selectList(any(SysFormVersion.class), any()))
                .thenReturn(List.of(draftRow(FORM_ID, "{\"fields\":[]}")));

        SysFormUpdateRequest request = new SysFormUpdateRequest();
        request.setFieldId(FORM_ID);
        request.setFieldFormName("采购申请单");
        request.setSchema(schema(FORM_ID, FORM_CODE, "采购申请单", null,
                field("amount", "number", "金额")));

        service.update(request);

        ArgumentCaptor<SysForm> metaCaptor = ArgumentCaptor.forClass(SysForm.class);
        verify(formDao).update(metaCaptor.capture());
        assertEquals(FORM_ID, metaCaptor.getValue().getFieldId());
        assertEquals("采购申请单", metaCaptor.getValue().getFieldFormName());
        assertEquals(USER_ID, metaCaptor.getValue().getFieldUpdaterId());
        assertNull(metaCaptor.getValue().getFieldFormCode(), "编码禁改（更新不受理编码）");

        ArgumentCaptor<SysFormVersion> rewriteCaptor = ArgumentCaptor.forClass(SysFormVersion.class);
        verify(versionDao).update(rewriteCaptor.capture());
        SysFormVersion rewrite = rewriteCaptor.getValue();
        assertEquals(DRAFT_ID, rewrite.getFieldId(), "原地重写既有 DRAFT 行");
        String stored = rewrite.getFieldSchemaJson().toString();
        assertTrue(stored.contains("\"formCode\":\"" + FORM_CODE + "\""), "协议头以主档权威元数据归一: " + stored);
        assertTrue(stored.contains("\"formName\":\"采购申请单\""), "协议头名称取更新后终值");
        assertFalse(stored.contains("\"version\""), "草稿行不分配版本号");
        assertEquals(USER_ID, rewrite.getFieldUpdaterId());
        verify(versionDao, never()).insert(any(SysFormVersion.class));
    }

    @Test
    void updateCreatesDraftRowForPublishedFormWithoutDraft() {
        SysForm exist = formRow(SysFormStatus.PUBLISHED, V2_ID);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        when(versionDao.selectList(any(SysFormVersion.class), any())).thenReturn(List.of());

        SysFormUpdateRequest request = new SysFormUpdateRequest();
        request.setFieldId(FORM_ID);
        request.setSchema(schema(FORM_ID, FORM_CODE, "采购申请", null, field("amount", "number", "金额")));
        service.update(request);

        ArgumentCaptor<SysFormVersion> draftCaptor = ArgumentCaptor.forClass(SysFormVersion.class);
        verify(versionDao).insert(draftCaptor.capture());
        SysFormVersion draft = draftCaptor.getValue();
        assertEquals(FORM_ID, draft.getFieldFormId(), "再编辑入口：主档已发布且无草稿行时自动创建 DRAFT 行");
        assertEquals(SysFormVersionStatus.DRAFT.name(), draft.getFieldStatus());
        assertNull(draft.getFieldVersion());
        assertNotNull(draft.getFieldSchemaJson());
        verify(formDao, never()).update(any(SysForm.class));
    }

    @Test
    void updateRejectsEmptyPayload() {
        SysFormUpdateRequest request = new SysFormUpdateRequest();
        request.setFieldId(FORM_ID);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.update(request));
        assertTrue(error.getMessage().contains("至少携带一项"), error.getMessage());
        verify(formDao, never()).update(any(SysForm.class));
        verify(versionDao, never()).update(any(SysFormVersion.class));
    }

    @Test
    void updateDefensivelyRejectsFrozenDraftRow() {
        SysForm exist = formRow(SysFormStatus.PUBLISHED, V2_ID);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        when(versionDao.selectList(any(SysFormVersion.class), any()))
                .thenReturn(List.of(draftRow(FORM_ID, "{\"fields\":[]}")));
        // WHERE 守护 field_status='DRAFT'：行已被并发冻结为终态时影响行数 0（契约层不可达，双保险防御）
        when(versionDao.update(any(SysFormVersion.class))).thenReturn(0);

        SysFormUpdateRequest request = new SysFormUpdateRequest();
        request.setFieldId(FORM_ID);
        request.setSchema(schema(FORM_ID, FORM_CODE, "采购申请", null, field("amount", "number", "金额")));
        assertFormError(() -> service.update(request), CS_FORM_VERSION_STATE_INVALID);
        verify(versionDao, never()).insert(any(SysFormVersion.class));
    }

    @Test
    void updateRejectsUnknownForm() {
        when(formDao.selectById(FORM_ID)).thenReturn(null);
        SysFormUpdateRequest request = new SysFormUpdateRequest();
        request.setFieldId(FORM_ID);
        request.setFieldFormName("任意");
        assertFormError(() -> service.update(request), CS_FORM_NOT_FOUND);
    }

    // ---------------------------------------------------------------- enable：只更新自身一行

    @Test
    void updateEnableOnlyTouchesOwnRow() {
        SysForm exist = formRow(SysFormStatus.PUBLISHED, V2_ID);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        when(formDao.updateEnable(eq(FORM_ID), eq(Boolean.FALSE), eq(USER_ID), any(Date.class))).thenReturn(1);

        service.updateEnable(FORM_ID, Boolean.FALSE);

        verify(formDao).updateEnable(eq(FORM_ID), eq(Boolean.FALSE), eq(USER_ID), any(Date.class));
        verify(formDao, never()).update(any(SysForm.class));
        verify(versionDao, never()).update(any(SysFormVersion.class));
    }

    @Test
    void updateEnableRejectsMissingForm() {
        when(formDao.selectById(FORM_ID)).thenReturn(null);
        assertFormError(() -> service.updateEnable(FORM_ID, Boolean.TRUE), CS_FORM_NOT_FOUND);
    }

    @Test
    void updateEnableRejectsNullEnable() {
        assertThrows(IllegalArgumentException.class, () -> service.updateEnable(FORM_ID, null));
        verify(formDao, never()).updateEnable(anyString(), any(), anyString(), any());
    }

    // ---------------------------------------------------------------- delete：实例数据禁删

    @Test
    void deleteRejectsWhenInstanceDataExists() {
        SysForm exist = formRow(SysFormStatus.PUBLISHED, V2_ID);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        when(dataDao.existsByFormId(FORM_ID)).thenReturn(true);

        String message = assertFormError(() -> service.delete(FORM_ID), CS_FORM_HAS_DATA);
        assertTrue(message.contains("停用"), "管理引导：需保留数据时用停用达成不再使用: " + message);
        verify(versionDao, never()).deleteByFormId(anyString());
        verify(formDao, never()).deleteById(anyString());
    }

    @Test
    void deleteRemovesVersionsThenMaster() {
        SysForm exist = formRow(SysFormStatus.DRAFT, null);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        when(dataDao.existsByFormId(FORM_ID)).thenReturn(false);

        service.delete(FORM_ID);

        InOrder order = inOrder(versionDao, formDao);
        order.verify(versionDao).deleteByFormId(FORM_ID);
        order.verify(formDao).deleteById(FORM_ID);
    }

    @Test
    void deleteRejectsUnknownForm() {
        when(formDao.selectById(FORM_ID)).thenReturn(null);
        assertFormError(() -> service.delete(FORM_ID), CS_FORM_NOT_FOUND);
    }

    // ---------------------------------------------------------------- publish：发布校验链与快照

    @Test
    void publishFreezesDraftAndSwitchesPointerOnFirstPublish() {
        SysForm exist = formRow(SysFormStatus.DRAFT, null);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        when(versionDao.selectList(any(SysFormVersion.class), any()))
                .thenReturn(List.of(draftRow(FORM_ID, "{\"fields\":["
                        + "{\"field\":\"amount\",\"type\":\"number\",\"label\":\"金额\"}]}")));
        when(versionDao.selectMaxVersion(FORM_ID)).thenReturn(null);
        when(versionDao.freezeDraft(eq(DRAFT_ID), eq(1), any(Date.class), eq(USER_ID), any(Date.class))).thenReturn(1);
        when(formDao.updateCurrentVersionId(eq(FORM_ID), eq(SysFormStatus.PUBLISHED.name()),
                eq(DRAFT_ID), eq(USER_ID), any(Date.class))).thenReturn(1);

        SysFormVersionCreatedVo vo = service.publish(FORM_ID);

        assertEquals(DRAFT_ID, vo.getFieldVersionId(), "发布 = 草稿行原地冻结，版本行 ID 即草稿行 ID");
        assertEquals(1, vo.getFieldVersion(), "首次发布版本号为 1（现存最大 + 1，无发布版本时 0 兜底）");
        verify(versionDao).freezeDraft(eq(DRAFT_ID), eq(1), any(Date.class), eq(USER_ID), any(Date.class));
        verify(versionDao, never()).deprecate(anyString(), anyString(), any(Date.class));
        verify(formDao).updateCurrentVersionId(eq(FORM_ID), eq(SysFormStatus.PUBLISHED.name()),
                eq(DRAFT_ID), eq(USER_ID), any(Date.class));
    }

    @Test
    void publishDeprecatesPreviousVersionAndAllocatesNext() {
        SysForm exist = formRow(SysFormStatus.PUBLISHED, V2_ID);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        when(versionDao.selectList(any(SysFormVersion.class), any()))
                .thenReturn(List.of(draftRow(FORM_ID, "{\"fields\":["
                        + "{\"field\":\"amount\",\"type\":\"number\",\"label\":\"金额\"}]}")));
        when(versionDao.selectMaxVersion(FORM_ID)).thenReturn(2);
        when(versionDao.freezeDraft(eq(DRAFT_ID), eq(3), any(Date.class), eq(USER_ID), any(Date.class))).thenReturn(1);
        when(versionDao.deprecate(eq(V2_ID), eq(USER_ID), any(Date.class))).thenReturn(1);
        when(formDao.updateCurrentVersionId(eq(FORM_ID), eq(SysFormStatus.PUBLISHED.name()),
                eq(DRAFT_ID), eq(USER_ID), any(Date.class))).thenReturn(1);

        SysFormVersionCreatedVo vo = service.publish(FORM_ID);

        assertEquals(3, vo.getFieldVersion(), "版本号 = 现存最大 + 1（单调递增）");
        verify(versionDao).deprecate(eq(V2_ID), eq(USER_ID), any(Date.class));
        verify(formDao).updateCurrentVersionId(eq(FORM_ID), eq(SysFormStatus.PUBLISHED.name()),
                eq(DRAFT_ID), eq(USER_ID), any(Date.class));
    }

    @Test
    void publishRejectsWithoutDraft() {
        SysForm exist = formRow(SysFormStatus.PUBLISHED, V2_ID);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        when(versionDao.selectList(any(SysFormVersion.class), any())).thenReturn(List.of());

        String message = assertFormError(() -> service.publish(FORM_ID), CS_FORM_NO_DRAFT);
        assertTrue(message.contains("草稿"), message);
        verify(versionDao, never()).freezeDraft(anyString(), any(), any(Date.class), anyString(), any(Date.class));
        verify(formDao, never()).updateCurrentVersionId(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void publishRejectsInvalidDraftSchema() {
        SysForm exist = formRow(SysFormStatus.DRAFT, null);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        when(versionDao.selectList(any(SysFormVersion.class), any()))
                .thenReturn(List.of(draftRow(FORM_ID, DUPLICATED_CODE_SCHEMA)));

        assertFormError(() -> service.publish(FORM_ID), CS_FORM_SCHEMA_INVALID);
        verify(versionDao, never()).freezeDraft(anyString(), any(), any(Date.class), anyString(), any(Date.class));
        verify(formDao, never()).updateCurrentVersionId(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void publishRejectsOversizedDraftSchema() {
        SysForm exist = formRow(SysFormStatus.DRAFT, null);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        // 超过 MAX_SCHEMA_JSON_LENGTH（524288）的存储原文：校验器解析前先查体积（P3）
        when(versionDao.selectList(any(SysFormVersion.class), any()))
                .thenReturn(List.of(draftRow(FORM_ID, "x".repeat(SysFormConstants.MAX_SCHEMA_JSON_LENGTH + 1))));

        assertFormError(() -> service.publish(FORM_ID), CS_FORM_SCHEMA_LIMIT_EXCEEDED);
        verify(versionDao, never()).freezeDraft(anyString(), any(), any(Date.class), anyString(), any(Date.class));
    }

    @Test
    void publishDefensivelyRejectsConcurrentFreeze() {
        SysForm exist = formRow(SysFormStatus.DRAFT, null);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        when(versionDao.selectList(any(SysFormVersion.class), any()))
                .thenReturn(List.of(draftRow(FORM_ID, "{\"fields\":["
                        + "{\"field\":\"amount\",\"type\":\"number\",\"label\":\"金额\"}]}")));
        when(versionDao.selectMaxVersion(FORM_ID)).thenReturn(null);
        // 并发发布：另一管理员已冻结草稿行（WHERE 守护 DRAFT 使本次影响行数 0）
        when(versionDao.freezeDraft(anyString(), any(), any(Date.class), anyString(), any(Date.class))).thenReturn(0);

        assertFormError(() -> service.publish(FORM_ID), CS_FORM_VERSION_STATE_INVALID);
        verify(formDao, never()).updateCurrentVersionId(anyString(), anyString(), anyString(), anyString(), any());
    }

    // ---------------------------------------------------------------- rollback：重发布副本

    @Test
    void rollbackCopiesTargetIntoNewPublishedVersion() {
        SysForm exist = formRow(SysFormStatus.PUBLISHED, V2_ID);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        String targetSchema = "{\"formId\":\"" + FORM_ID + "\",\"formCode\":\"" + FORM_CODE
                + "\",\"formName\":\"采购申请\",\"fields\":["
                + "{\"field\":\"amount\",\"type\":\"number\",\"label\":\"金额\"}]}";
        SysFormVersion target = publishedRow(V1_ID, 1, SysFormVersionStatus.DEPRECATED, targetSchema);
        when(versionDao.selectById(V1_ID)).thenReturn(target);
        when(versionDao.selectMaxVersion(FORM_ID)).thenReturn(2);
        when(versionDao.deprecate(eq(V2_ID), eq(USER_ID), any(Date.class))).thenReturn(1);
        when(formDao.updateCurrentVersionId(eq(FORM_ID), eq(SysFormStatus.PUBLISHED.name()),
                anyString(), eq(USER_ID), any(Date.class))).thenReturn(1);

        SysFormRollbackRequest request = new SysFormRollbackRequest();
        request.setFieldId(FORM_ID);
        request.setFieldVersionId(V1_ID);
        SysFormVersionCreatedVo vo = service.rollback(request);

        ArgumentCaptor<SysFormVersion> copyCaptor = ArgumentCaptor.forClass(SysFormVersion.class);
        verify(versionDao).insert(copyCaptor.capture());
        SysFormVersion copy = copyCaptor.getValue();
        assertNotNull(copy.getFieldId(), "回滚副本为新增版本行（32 位新 ID）");
        assertEquals(FORM_ID, copy.getFieldFormId());
        assertEquals(3, copy.getFieldVersion(), "回滚副本版本号 = 现存最大 + 1（不回拨指针）");
        assertEquals(SysFormVersionStatus.PUBLISHED.name(), copy.getFieldStatus(), "回滚 = 一步发布，不经草稿态");
        assertNotNull(copy.getFieldPublishedAt(), "published_at 为回滚时刻");
        assertEquals(targetSchema, copy.getFieldSchemaJson().toString(), "复制目标版本内容（快照原样）");
        assertEquals(USER_ID, copy.getFieldCreatorId());
        assertEquals(3, vo.getFieldVersion());
        assertEquals(copy.getFieldId(), vo.getFieldVersionId());
        verify(versionDao).deprecate(eq(V2_ID), eq(USER_ID), any(Date.class));
        verify(formDao).updateCurrentVersionId(eq(FORM_ID), eq(SysFormStatus.PUBLISHED.name()),
                eq(copy.getFieldId()), eq(USER_ID), any(Date.class));
        verify(versionDao, never()).freezeDraft(anyString(), any(), any(Date.class), anyString(), any(Date.class));
    }

    @Test
    void rollbackRejectsDraftTarget() {
        SysForm exist = formRow(SysFormStatus.PUBLISHED, V2_ID);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        when(versionDao.selectById(DRAFT_ID)).thenReturn(draftRow(FORM_ID, "{\"fields\":[]}"));

        SysFormRollbackRequest request = new SysFormRollbackRequest();
        request.setFieldId(FORM_ID);
        request.setFieldVersionId(DRAFT_ID);
        String message = assertFormError(() -> service.rollback(request), CS_FORM_VERSION_STATE_INVALID);
        assertTrue(message.contains("草稿"), "草稿不是历史发布版本，语义混用拒绝: " + message);
        verify(versionDao, never()).insert(any(SysFormVersion.class));
    }

    @Test
    void rollbackRejectsMissingTarget() {
        SysForm exist = formRow(SysFormStatus.PUBLISHED, V2_ID);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        when(versionDao.selectById(V1_ID)).thenReturn(null);

        SysFormRollbackRequest request = new SysFormRollbackRequest();
        request.setFieldId(FORM_ID);
        request.setFieldVersionId(V1_ID);
        assertFormError(() -> service.rollback(request), CS_FORM_VERSION_NOT_FOUND);
        verify(versionDao, never()).insert(any(SysFormVersion.class));
    }

    @Test
    void rollbackRejectsForeignTarget() {
        SysForm exist = formRow(SysFormStatus.PUBLISHED, V2_ID);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        // 目标行存在但归属其它表单：按目标不存在处理，避免跨表单探测
        SysFormVersion foreign = publishedRow(V1_ID, 1, SysFormVersionStatus.DEPRECATED, "{\"fields\":[]}");
        foreign.setFieldFormId("9".repeat(32));
        when(versionDao.selectById(V1_ID)).thenReturn(foreign);

        SysFormRollbackRequest request = new SysFormRollbackRequest();
        request.setFieldId(FORM_ID);
        request.setFieldVersionId(V1_ID);
        assertFormError(() -> service.rollback(request), CS_FORM_VERSION_NOT_FOUND);
        verify(versionDao, never()).insert(any(SysFormVersion.class));
    }

    // ---------------------------------------------------------------- load / list / versions

    @Test
    void loadReturnsDraftAndPublishedSchema() {
        SysForm exist = formRow(SysFormStatus.PUBLISHED, V2_ID);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        when(versionDao.selectList(any(SysFormVersion.class), any()))
                .thenReturn(List.of(draftRow(FORM_ID, "{\"formId\":\"stale\",\"formCode\":\"stale\","
                        + "\"formName\":\"stale\",\"fields\":["
                        + "{\"field\":\"amount\",\"type\":\"number\",\"label\":\"金额\"}]}")));
        when(versionDao.selectById(V2_ID)).thenReturn(publishedRow(V2_ID, 2, SysFormVersionStatus.PUBLISHED,
                "{\"fields\":[{\"field\":\"amount\",\"type\":\"number\",\"label\":\"金额\"}]}"));

        SysFormVo vo = service.load(FORM_ID);

        assertEquals(Boolean.TRUE, vo.getHasDraft());
        assertNotNull(vo.getDraftSchema());
        assertEquals(FORM_ID, vo.getDraftSchema().getFormId(), "load 响应协议头以主档元数据归一");
        assertEquals(FORM_CODE, vo.getDraftSchema().getFormCode());
        assertNull(vo.getDraftSchema().getVersion(), "草稿载荷 version 恒 null（版本号仅发布动作分配）");
        assertNotNull(vo.getPublishedSchema());
        assertEquals(2, vo.getPublishedSchema().getVersion(), "发布载荷协议头 version 回填行级版本号");
        assertEquals(V2_ID, vo.getFieldCurrentVersionId());
    }

    @Test
    void loadMarksCleanPublishedFormWithoutDraft() {
        SysForm exist = formRow(SysFormStatus.PUBLISHED, V2_ID);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        when(versionDao.selectList(any(SysFormVersion.class), any())).thenReturn(List.of());
        when(versionDao.selectById(V2_ID)).thenReturn(publishedRow(V2_ID, 2, SysFormVersionStatus.PUBLISHED,
                "{\"fields\":[]}"));

        SysFormVo vo = service.load(FORM_ID);

        assertEquals(Boolean.FALSE, vo.getHasDraft(), "已发布未再编辑无草稿行");
        assertNull(vo.getDraftSchema());
        assertNotNull(vo.getPublishedSchema());
    }

    @Test
    void loadRejectsUnknownForm() {
        when(formDao.selectById(FORM_ID)).thenReturn(null);
        assertFormError(() -> service.load(FORM_ID), CS_FORM_NOT_FOUND);
    }

    @Test
    void listMarksHasDraftAndCurrentVersion() {
        SysForm published = formRow(SysFormStatus.PUBLISHED, V2_ID);
        SysForm draft = formRow(SysFormStatus.DRAFT, null);
        draft.setFieldId("e".repeat(32));
        when(formDao.selectList(any(SysForm.class), any())).thenReturn(List.of(published, draft));
        // 全表草稿行索引：仅 published 表单存在 DRAFT 行
        when(versionDao.selectList(any(SysFormVersion.class), any()))
                .thenReturn(List.of(draftRow(FORM_ID, null)));
        when(versionDao.selectVersionNumberById(V2_ID)).thenReturn(2);

        List<SysFormVo> rows = service.list(new SysForm(), new Pages());

        assertEquals(2, rows.size());
        SysFormVo publishedVo = rows.get(0);
        assertEquals(Boolean.TRUE, publishedVo.getHasDraft());
        assertEquals(2, publishedVo.getFieldCurrentVersion(), "列表行 fieldCurrentVersion 按指针轻量点查");
        assertNull(publishedVo.getDraftSchema(), "列表行不携带 Schema 内容");
        assertNull(publishedVo.getPublishedSchema());
        SysFormVo draftVo = rows.get(1);
        assertEquals(Boolean.FALSE, draftVo.getHasDraft());
        assertNull(draftVo.getFieldCurrentVersion(), "从未发布无当前版本号");
    }

    @Test
    void listRejectsIllegalStatusFilter() {
        SysForm body = new SysForm();
        body.setFieldStatus("CLOSED");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.list(body, null));
        assertTrue(error.getMessage().contains("fieldStatus"), error.getMessage());
    }

    @Test
    void countDelegatesNormalizedCriteria() {
        when(formDao.selectCount(any(SysForm.class))).thenReturn(7L);
        SysForm body = new SysForm();
        body.setFieldFormCode("  " + FORM_CODE + "  ");
        assertEquals(7L, service.count(body));
        ArgumentCaptor<SysForm> criteriaCaptor = ArgumentCaptor.forClass(SysForm.class);
        verify(formDao).selectCount(criteriaCaptor.capture());
        assertEquals(FORM_CODE, criteriaCaptor.getValue().getFieldFormCode(), "查询条件 trim 归一");
    }

    @Test
    void versionsMapsRowsKeepingDraftFirstOrder() {
        SysForm exist = formRow(SysFormStatus.PUBLISHED, V2_ID);
        when(formDao.selectById(FORM_ID)).thenReturn(exist);
        // Mapper 排序契约（草稿行恒排最前，其余 field_version 降序）由 selectByFormId 保证，Service 透传
        SysFormVersion draft = draftRow(FORM_ID, "{\"fields\":[]}");
        SysFormVersion v2 = publishedRow(V2_ID, 2, SysFormVersionStatus.PUBLISHED, "{\"fields\":[]}");
        SysFormVersion v1 = publishedRow(V1_ID, 1, SysFormVersionStatus.DEPRECATED, "{\"fields\":[]}");
        when(versionDao.selectByFormId(FORM_ID)).thenReturn(List.of(draft, v2, v1));

        List<SysFormVersionVo> rows = service.versions(FORM_ID);

        assertEquals(3, rows.size());
        assertEquals(DRAFT_ID, rows.get(0).getFieldId());
        assertNull(rows.get(0).getFieldVersion(), "草稿行 fieldVersion 为 null（版本号仅发布动作分配）");
        assertEquals(SysFormVersionStatus.DRAFT.name(), rows.get(0).getFieldStatus());
        assertNull(rows.get(0).getFieldPublishedAt(), "草稿行无发布时刻");
        assertEquals(2, rows.get(1).getFieldVersion());
        assertEquals(SysFormVersionStatus.PUBLISHED.name(), rows.get(1).getFieldStatus());
        assertEquals(1, rows.get(2).getFieldVersion());
        assertEquals(SysFormVersionStatus.DEPRECATED.name(), rows.get(2).getFieldStatus());
    }

    @Test
    void versionsRejectsUnknownForm() {
        when(formDao.selectById(FORM_ID)).thenReturn(null);
        assertFormError(() -> service.versions(FORM_ID), CS_FORM_NOT_FOUND);
    }

    // ---------------------------------------------------------------- 测试小件

    private static SysFormSaveRequest saveRequest() {
        SysFormSaveRequest request = new SysFormSaveRequest();
        request.setFieldId(FORM_ID);
        request.setFieldFormCode(FORM_CODE);
        request.setFieldFormName("采购申请");
        return request;
    }

    private static SysForm formRow(SysFormStatus status, String currentVersionId) {
        SysForm row = new SysForm();
        row.setFieldId(FORM_ID);
        row.setFieldFormCode(FORM_CODE);
        row.setFieldFormName("采购申请");
        row.setFieldStatus(status.name());
        row.setFieldCurrentVersionId(currentVersionId);
        row.setFieldEnable(Boolean.TRUE);
        row.setFieldCreatorId(USER_ID);
        row.setFieldCreateTime(new Date());
        return row;
    }

    private static SysFormVersion draftRow(String formId, String schemaJson) {
        SysFormVersion row = new SysFormVersion();
        row.setFieldId(DRAFT_ID);
        row.setFieldFormId(formId);
        row.setFieldVersion(null);
        row.setFieldSchemaJson(schemaJson);
        row.setFieldStatus(SysFormVersionStatus.DRAFT.name());
        row.setFieldEnable(Boolean.TRUE);
        row.setFieldCreatorId(USER_ID);
        row.setFieldCreateTime(new Date());
        return row;
    }

    private static SysFormVersion publishedRow(String rowId, int version, SysFormVersionStatus status,
                                               String schemaJson) {
        SysFormVersion row = new SysFormVersion();
        row.setFieldId(rowId);
        row.setFieldFormId(FORM_ID);
        row.setFieldVersion(version);
        row.setFieldSchemaJson(schemaJson);
        row.setFieldStatus(status.name());
        row.setFieldPublishedAt(new Date());
        row.setFieldEnable(Boolean.TRUE);
        row.setFieldCreatorId(USER_ID);
        row.setFieldCreateTime(new Date());
        return row;
    }

    /** 构造 Schema 协议对象（协议头可控，供归一断言；调用方按需携带陈旧头/版本号验证归一） */
    private static SysFormSchemaDo schema(String formId, String formCode, String formName,
                                          Integer version, SysFormFieldDo... fields) {
        SysFormSchemaDo schema = new SysFormSchemaDo();
        schema.setFormId(formId);
        schema.setFormCode(formCode);
        schema.setFormName(formName);
        schema.setVersion(version);
        schema.setFields(List.of(fields));
        return schema;
    }

    private static SysFormFieldDo field(String code, String type, String label) {
        SysFormFieldDo field = new SysFormFieldDo();
        field.setField(code);
        field.setType(type);
        field.setLabel(label);
        return field;
    }

    private static SysFormSchemaDo parse(String json) {
        try {
            return JSON.readValue(json, SysFormSchemaDo.class);
        } catch (Exception e) {
            throw new IllegalStateException("测试样例 JSON 非法", e);
        }
    }

    /** 断言 IllegalArgumentException 且 message 以期望错误码为前缀，返回完整消息 */
    private static String assertFormError(Runnable runnable, String expectedCode) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, runnable::run);
        assertTrue(null != error.getMessage() && error.getMessage().startsWith(expectedCode + ":"),
                "错误码应为 " + expectedCode + "，实际: " + error.getMessage());
        return error.getMessage();
    }
}
