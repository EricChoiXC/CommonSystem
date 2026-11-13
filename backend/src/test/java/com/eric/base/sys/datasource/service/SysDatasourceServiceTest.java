package com.eric.base.sys.datasource.service;

import com.eric.base.common.base.model.Pages;
import com.eric.base.common.base.resolver.BaseResolver;
import com.eric.base.common.exception.AuthorizationException;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.resolver.AuthorizationHelper;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.datasource.dao.SysDatasourceDao;
import com.eric.base.sys.datasource.model.SysDatasource;
import com.eric.base.sys.datasource.response.SysDatasourceOptionVo;
import com.eric.base.sys.datasource.response.SysDatasourceTargetVo;
import com.eric.base.sys.datasource.response.SysDatasourceVo;
import com.eric.base.sys.datasource.spi.SysDatasourceConditionDo;
import com.eric.base.sys.datasource.spi.SysDatasourceDefaultDo;
import com.eric.base.sys.datasource.spi.SysDatasourceFieldDo;
import com.eric.base.sys.datasource.spi.SysDatasourceProvider;
import com.eric.base.sys.datasource.spi.SysDatasourceQueryContext;
import com.eric.base.sys.datasource.spi.SysDatasourceQueryResult;
import com.eric.base.sys.org.model.SysOrgUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.eric.base.sys.datasource.properties.SysDatasourceConstants.CS_DATASOURCE_CODE_DUPLICATED;
import static com.eric.base.sys.datasource.properties.SysDatasourceConstants.CS_DATASOURCE_CONDITION_INVALID;
import static com.eric.base.sys.datasource.properties.SysDatasourceConstants.CS_DATASOURCE_FIELD_INVALID;
import static com.eric.base.sys.datasource.properties.SysDatasourceConstants.CS_DATASOURCE_NOT_FOUND;
import static com.eric.base.sys.datasource.properties.SysDatasourceConstants.CS_DATASOURCE_TARGET_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据源业务服务测试
 *
 * @author Eric
 * @date 2026/11/13
 * @description 覆盖任务 F18-BE-004 验收标准（Mockito 纯单测，Dao / Provider / 目标 resolver
 *              用 mock 桩，Registry 与 AuthorizationHelper 用真实实例，MockedStatic&lt;UserUtil&gt;
 *              构造当前登录人——先例 SysOrgProfileServiceTest，风险 R02 应对）：
 *              <ul>
 *              <li>save：正常落库链（条件/字段白名单经 Jackson 序列化、默认启用、审计字段）、
 *                  编码查重（DB 行 + Registry 占用全集）、target 失效、条件 key/op/字面量（A7）、
 *                  字段白名单、损坏 JSON；</li>
 *              <li>update：fieldTarget 锁定以库值为准（入参忽略）、编码查重、行不存在；</li>
 *              <li>enable / delete：委托与保护；</li>
 *              <li>load：JSON 解析回填与损坏报错；list：原文不解析 + 失效 target 标记（R05）；</li>
 *              <li>execute：默认源 / 自定义源解析、固定条件覆盖调用方（§6.4）、条件白名单、
 *                  字面量禁止（A7）、字段白名单全字段回退、排序白名单回退（§6.6）、损坏 JSON、
 *                  停用 / 不存在、target 失效、两段式鉴权 UNAUTHORIZED / FORBIDDEN；</li>
 *              <li>options（A1 不暴露固定条件原文）与 targets 能力清单。</li>
 *              </ul>
 */
class SysDatasourceServiceTest {

    /** 32 位测试 ID（避免 BaseModel.setFieldId 触发重新生成） */
    private static final String USER_ID = "1".repeat(32);
    private static final String DS_ID = "d".repeat(32);
    private static final String OTHER_ROW_ID = "e".repeat(32);

    private static final String TARGET_CODE = "calendar.list";
    private static final String TARGET_ACTION = "sys.calendar.list";
    private static final String CUSTOM_CODE = "calendar.my-created";

    /** 存储态固定条件 JSON（用户日程 = 我创建的，方案 §2.3 示例） */
    private static final String MY_CREATED_CONDITIONS =
            "[{\"key\":\"fieldCreatorId\",\"op\":\"EQ\",\"value\":\"${CURRENT_USER_ID}\"}]";
    /** 存储态返回字段白名单 JSON */
    private static final String MY_CREATED_FIELDS = "[\"fieldId\",\"fieldName\",\"fieldStartTime\"]";

    private SysDatasourceDao dao;
    private SysDatasourceProvider provider;
    private BaseResolver targetResolver;
    private SysDatasourceService service;
    private MockedStatic<UserUtil> userUtil;

    @BeforeEach
    void setUp() {
        dao = mock(SysDatasourceDao.class);
        provider = mock(SysDatasourceProvider.class);
        targetResolver = mock(BaseResolver.class);
        stubProvider();
        SysDatasourceRegistry registry = new SysDatasourceRegistry(List.of(provider));
        service = new SysDatasourceService(dao, registry,
                new AuthorizationHelper(mock(ApplicationEventPublisher.class)));
        // 默认当前登录人 USER_ID（执行编排与审计字段依赖，R02）
        userUtil = mockStatic(UserUtil.class);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user(USER_ID));
        userUtil.when(UserUtil::getCurrentUserId).thenReturn(USER_ID);
        when(targetResolver.resolve(any(AuthorizationContext.class))).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        userUtil.close();
    }

    // ---------------------------------------------------------------- 桩构造

    private void stubProvider() {
        when(provider.getTargetCode()).thenReturn(TARGET_CODE);
        when(provider.getTargetName()).thenReturn("日程列表");
        when(provider.getTargetAction()).thenReturn(TARGET_ACTION);
        when(provider.getTargetPath()).thenReturn("/backend/sys/calendar/list");
        when(provider.getTargetResolver()).thenReturn(targetResolver);
        when(provider.getConditionFields()).thenReturn(List.of(
                field("fieldCreatorId", "STRING", List.of("EQ"), false, false),
                field("fieldName", "STRING", List.of("LIKE"), true, false),
                field("fieldType", "STRING", List.of("EQ"), true, false)));
        // 返回字段 9 项（对齐日程样例：startTime/createTime 可排序）
        when(provider.getResultFields()).thenReturn(List.of(
                field("fieldId", "STRING", null, true, false),
                field("fieldName", "STRING", null, true, false),
                field("fieldType", "STRING", null, true, false),
                field("fieldStartTime", "DATETIME", null, true, true),
                field("fieldEndTime", "DATETIME", null, true, false),
                field("fieldContent", "STRING", null, true, false),
                field("fieldCreatorId", "STRING", null, true, false),
                field("fieldCreateTime", "DATETIME", null, true, true),
                field("fieldUpdateTime", "DATETIME", null, true, false)));
        // 默认数据源【日程列表】：无条件、全字段（方案 §2.1 示例）
        SysDatasourceDefaultDo def = new SysDatasourceDefaultDo();
        def.setCode(TARGET_CODE);
        def.setName("日程列表");
        when(provider.getDefaultDatasources()).thenReturn(List.of(def));
    }

    private SysDatasourceFieldDo field(String key, String type, List<String> ops,
                                       boolean literalAllowed, boolean sortable) {
        SysDatasourceFieldDo field = new SysDatasourceFieldDo();
        field.setKey(key);
        field.setLabel(key);
        field.setType(type);
        field.setOps(ops);
        field.setLiteralAllowed(literalAllowed);
        field.setSortable(sortable);
        return field;
    }

    private SysOrgUser user(String userId) {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(userId);
        user.setFieldEnable(true);
        return user;
    }

    /** 存储态自定义数据源行（fieldConditions / fieldFields 为原文 JSON 字符串） */
    private SysDatasource storedRow(String code, Object conditionsJson, Object fieldsJson, boolean enable) {
        SysDatasource row = new SysDatasource();
        row.setFieldId(DS_ID);
        row.setFieldCode(code);
        row.setFieldName("用户日程");
        row.setFieldTarget(TARGET_CODE);
        row.setFieldConditions(conditionsJson);
        row.setFieldFields(fieldsJson);
        row.setFieldEnable(enable);
        row.setFieldCreatorId(USER_ID);
        row.setFieldCreateTime(new Date());
        return row;
    }

    /** 请求体固定条件（Jackson 反序列化产物为 List&lt;LinkedHashMap&gt; 形态） */
    private List<Map<String, Object>> rawConditions(String key, String op, String value) {
        return List.of(Map.of("key", key, "op", op, "value", value));
    }

    /** 保存体基形（fieldId 经 getInitID 预取） */
    private SysDatasource saveBody() {
        SysDatasource body = new SysDatasource();
        body.setFieldId(DS_ID);
        body.setFieldCode(CUSTOM_CODE);
        body.setFieldName("用户日程");
        body.setFieldTarget(TARGET_CODE);
        body.setFieldConditions(rawConditions("fieldCreatorId", "EQ", "${CURRENT_USER_ID}"));
        body.setFieldFields(List.of("fieldId", "fieldName", "fieldStartTime"));
        body.setFieldRemark("仅本人创建日程");
        return body;
    }

    private void stubCustomRow(Object conditionsJson, Object fieldsJson, boolean enable) {
        when(dao.selectByCode(CUSTOM_CODE)).thenReturn(storedRow(CUSTOM_CODE, conditionsJson, fieldsJson, enable));
    }

    /** 投影行桩：对齐日程返回字段列（SysDatasource 实体无日程列）；public 嵌套类保证反射 getter 可调 */
    public static class RowStub {
        private final String fieldId;
        private final String fieldName;
        private final String fieldType;
        private final Date fieldStartTime;

        public RowStub(String fieldId, String fieldName, String fieldType, Date fieldStartTime) {
            this.fieldId = fieldId;
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.fieldStartTime = fieldStartTime;
        }

        public String getFieldId() {
            return fieldId;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getFieldType() {
            return fieldType;
        }

        public Date getFieldStartTime() {
            return fieldStartTime;
        }
    }

    private Pages pages() {
        Pages pages = new Pages();
        pages.setPageNo(1);
        pages.setPageSize(20);
        pages.setNeedPage(true);
        return pages;
    }

    // ---------------------------------------------------------------- save 成功链

    @Test
    void savePersistsCustomDatasourceWithSerializedJson() {
        when(dao.selectByCode(CUSTOM_CODE)).thenReturn(null);
        when(dao.selectById(DS_ID)).thenReturn(
                storedRow(CUSTOM_CODE, MY_CREATED_CONDITIONS, MY_CREATED_FIELDS, true));

        SysDatasourceVo vo = service.save(saveBody());

        ArgumentCaptor<SysDatasource> captor = ArgumentCaptor.forClass(SysDatasource.class);
        verify(dao).insert(captor.capture());
        SysDatasource inserted = captor.getValue();
        assertEquals(DS_ID, inserted.getFieldId(), "fieldId 沿用前端 getInitID 预取值");
        assertEquals(CUSTOM_CODE, inserted.getFieldCode());
        assertEquals("用户日程", inserted.getFieldName());
        assertEquals(TARGET_CODE, inserted.getFieldTarget());
        assertEquals(Boolean.TRUE, inserted.getFieldEnable(), "新建默认启用");
        assertInstanceOf(String.class, inserted.getFieldConditions(), "落库为 JSON 字符串");
        assertTrue(((String) inserted.getFieldConditions()).contains("fieldCreatorId"));
        assertInstanceOf(String.class, inserted.getFieldFields(), "落库为 JSON 字符串");
        assertEquals("仅本人创建日程", inserted.getFieldRemark());
        assertEquals(USER_ID, inserted.getFieldCreatorId());
        assertEquals(USER_ID, inserted.getFieldUpdaterId());
        assertNotNull(inserted.getFieldCreateTime());
        assertNotNull(inserted.getFieldUpdateTime());

        // 返回 load 解析后的详情：条件为对象数组、字段为字符串数组、目标信息带出
        assertInstanceOf(List.class, vo.getFieldConditions());
        assertInstanceOf(List.class, vo.getFieldFields());
        assertEquals("日程列表", vo.getFieldTargetName());
    }

    // ---------------------------------------------------------------- save 校验拒绝

    @Test
    void saveRejectsDuplicatedDbCode() {
        when(dao.selectByCode(CUSTOM_CODE)).thenReturn(storedRow(CUSTOM_CODE, null, null, true));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(saveBody()));

        assertTrue(ex.getMessage().startsWith(CS_DATASOURCE_CODE_DUPLICATED), ex.getMessage());
        verify(dao, never()).insert(any(SysDatasource.class));
    }

    @Test
    void saveRejectsRegistryOccupiedCode() {
        // DB 无同编码行，但编码被默认数据源编码占用（防伪装默认源，方案 §4.2）
        SysDatasource body = saveBody();
        body.setFieldCode(TARGET_CODE);
        when(dao.selectByCode(TARGET_CODE)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(body));

        assertTrue(ex.getMessage().startsWith(CS_DATASOURCE_CODE_DUPLICATED), ex.getMessage());
        verify(dao, never()).insert(any(SysDatasource.class));
    }

    @Test
    void saveRejectsInvalidTarget() {
        SysDatasource body = saveBody();
        body.setFieldTarget("notify.absent");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(body));

        assertTrue(ex.getMessage().startsWith(CS_DATASOURCE_TARGET_INVALID), ex.getMessage());
        verify(dao, never()).insert(any(SysDatasource.class));
    }

    @Test
    void saveRejectsConditionKeyOutsideWhitelist() {
        SysDatasource body = saveBody();
        body.setFieldConditions(rawConditions("fieldAccept", "EQ", "x"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(body));

        assertTrue(ex.getMessage().startsWith(CS_DATASOURCE_CONDITION_INVALID), ex.getMessage());
    }

    @Test
    void saveRejectsUnsupportedOp() {
        // fieldName 仅允许 LIKE，EQ 不在字段声明 ops 内
        SysDatasource body = saveBody();
        body.setFieldConditions(rawConditions("fieldName", "EQ", "周报"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(body));

        assertTrue(ex.getMessage().startsWith(CS_DATASOURCE_CONDITION_INVALID), ex.getMessage());
    }

    @Test
    void saveRejectsLiteralOnRestrictedField() {
        // A7：fieldCreatorId literalAllowed=false，字面量一律拒绝（防写死他人 ID 越权）
        SysDatasource body = saveBody();
        body.setFieldConditions(rawConditions("fieldCreatorId", "EQ", "zhangsan"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(body));

        assertTrue(ex.getMessage().startsWith(CS_DATASOURCE_CONDITION_INVALID), ex.getMessage());
    }

    @Test
    void saveRejectsFieldOutsideResultFields() {
        SysDatasource body = saveBody();
        body.setFieldFields(List.of("fieldId", "fieldAbsent"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(body));

        assertTrue(ex.getMessage().startsWith(CS_DATASOURCE_FIELD_INVALID), ex.getMessage());
    }

    @Test
    void saveRejectsBrokenConditionsJson() {
        SysDatasource body = saveBody();
        body.setFieldConditions("{bad json");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(body));

        assertTrue(ex.getMessage().startsWith(CS_DATASOURCE_CONDITION_INVALID), ex.getMessage());
    }

    // ---------------------------------------------------------------- update

    @Test
    void updateLocksTargetToStoredValue() {
        when(dao.selectById(DS_ID)).thenReturn(storedRow(CUSTOM_CODE, MY_CREATED_CONDITIONS, null, true));
        when(dao.selectByCode("calendar.my-weekly")).thenReturn(null);
        when(dao.update(any(SysDatasource.class))).thenReturn(1);

        SysDatasource body = saveBody();
        body.setFieldCode("calendar.my-weekly");
        // 入参 fieldTarget 变更不被受理：校验与落库均以库值 calendar.list 为准
        body.setFieldTarget("notify.absent");

        SysDatasourceVo vo = service.update(body);

        ArgumentCaptor<SysDatasource> captor = ArgumentCaptor.forClass(SysDatasource.class);
        verify(dao).update(captor.capture());
        SysDatasource updated = captor.getValue();
        assertNull(updated.getFieldTarget(), "fieldTarget 保存后锁定，不进入更新模型（D3 先例）");
        assertNull(updated.getFieldEnable(), "启用态保持不变，启停走 enable 通道");
        assertEquals("calendar.my-weekly", updated.getFieldCode());
        assertEquals(USER_ID, updated.getFieldUpdaterId());
        // 返回详情按库值 target 带出目标信息
        assertEquals(TARGET_CODE, vo.getFieldTarget());
        assertEquals("日程列表", vo.getFieldTargetName());
    }

    @Test
    void updateRejectsDuplicatedCode() {
        when(dao.selectById(DS_ID)).thenReturn(storedRow(CUSTOM_CODE, MY_CREATED_CONDITIONS, null, true));
        SysDatasource other = storedRow("calendar.other", null, null, true);
        other.setFieldId(OTHER_ROW_ID);
        when(dao.selectByCode("calendar.my-weekly")).thenReturn(other);

        SysDatasource body = saveBody();
        body.setFieldCode("calendar.my-weekly");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.update(body));

        assertTrue(ex.getMessage().startsWith(CS_DATASOURCE_CODE_DUPLICATED), ex.getMessage());
        verify(dao, never()).update(any(SysDatasource.class));
    }

    @Test
    void updateRejectsMissingRow() {
        when(dao.selectById(DS_ID)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.update(saveBody()));
    }

    // ---------------------------------------------------------------- enable / delete

    @Test
    void updateEnableSwitchesState() {
        when(dao.selectById(DS_ID)).thenReturn(storedRow(CUSTOM_CODE, null, null, true));
        when(dao.updateEnable(eq(DS_ID), eq(Boolean.FALSE), eq(USER_ID), any(Date.class))).thenReturn(1);

        service.updateEnable(DS_ID, Boolean.FALSE);

        verify(dao).updateEnable(eq(DS_ID), eq(Boolean.FALSE), eq(USER_ID), any(Date.class));
    }

    @Test
    void updateEnableRejectsZeroAffectedRow() {
        when(dao.selectById(DS_ID)).thenReturn(storedRow(CUSTOM_CODE, null, null, true));
        when(dao.updateEnable(anyString(), any(), anyString(), any(Date.class))).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.updateEnable(DS_ID, Boolean.TRUE));
    }

    @Test
    void updateEnableRequiresFieldEnableValue() {
        assertThrows(IllegalArgumentException.class, () -> service.updateEnable(DS_ID, null));
    }

    @Test
    void deleteRemovesRow() {
        when(dao.selectById(DS_ID)).thenReturn(storedRow(CUSTOM_CODE, null, null, false));

        service.delete(DS_ID);

        verify(dao).deleteById(DS_ID);
    }

    @Test
    void deleteRejectsMissingRow() {
        when(dao.selectById(DS_ID)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.delete(DS_ID));
        verify(dao, never()).deleteById(anyString());
    }

    // ---------------------------------------------------------------- load / list / count

    @Test
    void loadParsesStoredJson() {
        when(dao.selectById(DS_ID))
                .thenReturn(storedRow(CUSTOM_CODE, MY_CREATED_CONDITIONS, MY_CREATED_FIELDS, true));

        SysDatasourceVo vo = service.load(DS_ID);

        List<?> conditions = assertInstanceOf(List.class, vo.getFieldConditions(), "load 解析为对象数组");
        SysDatasourceConditionDo condition = assertInstanceOf(SysDatasourceConditionDo.class, conditions.get(0));
        assertEquals("fieldCreatorId", condition.getKey());
        assertEquals("EQ", condition.getOp());
        assertEquals("${CURRENT_USER_ID}", condition.getValue());
        List<?> fields = assertInstanceOf(List.class, vo.getFieldFields(), "load 解析为字符串数组");
        assertEquals(List.of("fieldId", "fieldName", "fieldStartTime"), fields);
        assertEquals("日程列表", vo.getFieldTargetName());
        assertEquals("/backend/sys/calendar/list", vo.getFieldTargetPath());
    }

    @Test
    void loadRejectsBrokenConditionsJson() {
        when(dao.selectById(DS_ID)).thenReturn(storedRow(CUSTOM_CODE, "{bad json", null, true));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.load(DS_ID));

        assertTrue(ex.getMessage().startsWith(CS_DATASOURCE_CONDITION_INVALID), ex.getMessage());
    }

    @Test
    void listKeepsRawJsonAndMarksInvalidTarget() {
        SysDatasource stale = storedRow("calendar.stale", MY_CREATED_CONDITIONS, null, true);
        stale.setFieldId(OTHER_ROW_ID);
        stale.setFieldTarget("absent.target");
        when(dao.selectList(any(SysDatasource.class), isNull())).thenReturn(List.of(
                storedRow(CUSTOM_CODE, MY_CREATED_CONDITIONS, MY_CREATED_FIELDS, true), stale));

        List<SysDatasourceVo> rows = service.list(new SysDatasource(), null);

        assertEquals(2, rows.size());
        // 列表响应原文 JSON 不解析（列表页不解析 JSON，load 由 Service 解析）
        assertEquals(MY_CREATED_CONDITIONS, rows.get(0).getFieldConditions());
        assertEquals(MY_CREATED_FIELDS, rows.get(0).getFieldFields());
        assertEquals("日程列表", rows.get(0).getFieldTargetName());
        // 失效 target 行：目标信息为 null（R05 管理列表可识别标记）
        assertNull(rows.get(1).getFieldTargetName());
        assertNull(rows.get(1).getFieldTargetPath());
        assertEquals(MY_CREATED_CONDITIONS, rows.get(1).getFieldConditions());
    }

    @Test
    void countDelegatesToDao() {
        when(dao.selectCount(any(SysDatasource.class))).thenReturn(3L);

        assertEquals(3L, service.count(new SysDatasource()));
    }

    // ---------------------------------------------------------------- execute：默认源

    @Test
    void executeDefaultDatasourceQueriesAllFields() {
        when(provider.query(any(SysDatasourceQueryContext.class))).thenReturn(new SysDatasourceQueryResult(
                List.of(new RowStub("r1", "周报例会", "PERSONAL", new Date())), 1));

        SysDatasourceService.SysDatasourceExecution execution =
                service.execute(TARGET_CODE, Map.of("fieldName", "周报"), pages(), null, null);

        // 默认源无条件：调用方条件透传；空字段白名单 = 目标能力全字段
        ArgumentCaptor<SysDatasourceQueryContext> captor = ArgumentCaptor.forClass(SysDatasourceQueryContext.class);
        verify(provider).query(captor.capture());
        assertEquals(Map.of("fieldName", "周报"), captor.getValue().getConditions());
        assertEquals(9, execution.fields().size(), "空字段白名单 = 目标能力默认全字段");
        assertEquals(1, execution.total());
        assertEquals("周报例会", execution.rows().get(0).get("fieldName"));
        // 委托目标 action 鉴权（§5.2 两段式目标段）
        verify(targetResolver).resolve(any(AuthorizationContext.class));
    }

    // ---------------------------------------------------------------- execute：条件合并

    @Test
    void executeCustomMergesFixedOverCallerConditions() {
        stubCustomRow(MY_CREATED_CONDITIONS, MY_CREATED_FIELDS, true);
        when(provider.query(any(SysDatasourceQueryContext.class))).thenReturn(new SysDatasourceQueryResult(
                List.of(new RowStub("r1", "周报例会", "PERSONAL", new Date())), 1));

        // 调用方试图传 fieldCreatorId=他人：被固定条件 ${CURRENT_USER_ID} 覆盖（§6.4）
        SysDatasourceService.SysDatasourceExecution execution = service.execute(CUSTOM_CODE,
                Map.of("fieldCreatorId", "someone-else", "fieldName", "周报"), pages(),
                "fieldStartTime", "desc");

        ArgumentCaptor<SysDatasourceQueryContext> captor = ArgumentCaptor.forClass(SysDatasourceQueryContext.class);
        verify(provider).query(captor.capture());
        Map<String, Object> conditions = captor.getValue().getConditions();
        assertEquals(USER_ID, conditions.get("fieldCreatorId"), "固定条件覆盖调用方同名条件");
        assertEquals("周报", conditions.get("fieldName"), "调用方其余合法条件正常合并");
        // 投影白名单：3 列输出（R04：null 值列不输出，列头以 fields 为准）
        assertEquals(List.of("fieldId", "fieldName", "fieldStartTime"), execution.fields());
        assertEquals(1, execution.rows().size());
        Map<String, Object> row = execution.rows().get(0);
        assertEquals("r1", row.get("fieldId"));
        assertEquals("周报例会", row.get("fieldName"));
        assertFalse(row.containsKey("fieldType"), "白名单外字段不输出");
    }

    @Test
    void executeRejectsCallerLiteralOnRestrictedField() {
        // 自定义源无固定条件时，调用方对 literalAllowed=false 字段传字面量同样拒绝（A7）
        stubCustomRow("[]", "[]", true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.execute(CUSTOM_CODE, Map.of("fieldCreatorId", "zhangsan"), pages(), null, null));

        assertTrue(ex.getMessage().startsWith(CS_DATASOURCE_CONDITION_INVALID), ex.getMessage());
        verify(provider, never()).query(any(SysDatasourceQueryContext.class));
    }

    @Test
    void executeRejectsCallerKeyOutsideWhitelist() {
        stubCustomRow("[]", "[]", true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.execute(CUSTOM_CODE, Map.of("fieldAccept", "x"), pages(), null, null));

        assertTrue(ex.getMessage().startsWith(CS_DATASOURCE_CONDITION_INVALID), ex.getMessage());
    }

    @Test
    void executeRejectsBrokenStoredConditionsJson() {
        stubCustomRow("{bad json", "[]", true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.execute(CUSTOM_CODE, Map.of(), pages(), null, null));

        assertTrue(ex.getMessage().startsWith(CS_DATASOURCE_CONDITION_INVALID), ex.getMessage());
        verify(provider, never()).query(any(SysDatasourceQueryContext.class));
    }

    // ---------------------------------------------------------------- execute：解析与校验

    @Test
    void executeRejectsStoppedOrMissingCode() {
        // 停用：CS_DATASOURCE_NOT_FOUND
        stubCustomRow("[]", "[]", false);
        IllegalArgumentException stoppedEx = assertThrows(IllegalArgumentException.class,
                () -> service.execute(CUSTOM_CODE, Map.of(), pages(), null, null));
        assertTrue(stoppedEx.getMessage().startsWith(CS_DATASOURCE_NOT_FOUND), stoppedEx.getMessage());

        // 不存在：CS_DATASOURCE_NOT_FOUND
        when(dao.selectByCode("calendar.absent")).thenReturn(null);
        IllegalArgumentException missingEx = assertThrows(IllegalArgumentException.class,
                () -> service.execute("calendar.absent", Map.of(), pages(), null, null));
        assertTrue(missingEx.getMessage().startsWith(CS_DATASOURCE_NOT_FOUND), missingEx.getMessage());
    }

    @Test
    void executeRejectsInvalidTarget() {
        // Provider 下线残留：target 不在 Registry（R05）
        SysDatasource stale = storedRow(CUSTOM_CODE, "[]", "[]", true);
        stale.setFieldTarget("absent.target");
        when(dao.selectByCode(CUSTOM_CODE)).thenReturn(stale);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.execute(CUSTOM_CODE, Map.of(), pages(), null, null));

        assertTrue(ex.getMessage().startsWith(CS_DATASOURCE_TARGET_INVALID), ex.getMessage());
    }

    @Test
    void executeSortFieldWhitelistFallsBackOnNonSortable() {
        stubCustomRow("[]", "[]", true);
        when(provider.query(any(SysDatasourceQueryContext.class)))
                .thenReturn(new SysDatasourceQueryResult(List.of(), 0));

        // 可排序白名单内：透传
        service.execute(CUSTOM_CODE, Map.of(), pages(), "fieldStartTime", "asc");
        ArgumentCaptor<SysDatasourceQueryContext> captor = ArgumentCaptor.forClass(SysDatasourceQueryContext.class);
        verify(provider, times(1)).query(captor.capture());
        assertEquals("fieldStartTime", captor.getValue().getSortField());
        assertEquals("asc", captor.getValue().getSortOrder());

        // 非白名单字段（fieldContent 不可排序）：回退 null，由目标能力按默认排序执行（§6.6）
        service.execute(CUSTOM_CODE, Map.of(), pages(), "fieldContent", "desc");
        verify(provider, times(2)).query(captor.capture());
        assertNull(captor.getValue().getSortField(), "非 sortable 字段回退默认排序");
    }

    @Test
    void executeRejectsStoredFieldsOutsideResultFields() {
        stubCustomRow("[]", "[\"fieldAbsent\"]", true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.execute(CUSTOM_CODE, Map.of(), pages(), null, null));

        assertTrue(ex.getMessage().startsWith(CS_DATASOURCE_FIELD_INVALID), ex.getMessage());
    }

    @Test
    void executeEmptyFieldWhitelistMeansAllFields() {
        stubCustomRow("[]", "[]", true);
        when(provider.query(any(SysDatasourceQueryContext.class)))
                .thenReturn(new SysDatasourceQueryResult(List.of(), 0));

        SysDatasourceService.SysDatasourceExecution execution =
                service.execute(CUSTOM_CODE, Map.of(), pages(), null, null);

        assertEquals(9, execution.fields().size(), "[] 等价于 NULL = 目标能力默认全字段");
    }

    // ---------------------------------------------------------------- execute：两段式鉴权

    @Test
    void executeRejectsAnonymous() {
        userUtil.when(UserUtil::getCurrentUser).thenReturn(null);

        AuthorizationException ex = assertThrows(AuthorizationException.class,
                () -> service.execute(TARGET_CODE, Map.of(), pages(), null, null));

        assertEquals("UNAUTHORIZED", ex.getCode());
        verify(provider, never()).query(any(SysDatasourceQueryContext.class));
    }

    @Test
    void executeRejectsForbiddenTargetAction() {
        // 两段式目标段：无目标接口权限（如无日程列表权限）→ FORBIDDEN（§5.2）
        when(targetResolver.resolve(any(AuthorizationContext.class))).thenReturn(false);
        stubCustomRow("[]", "[]", true);

        AuthorizationException ex = assertThrows(AuthorizationException.class,
                () -> service.execute(CUSTOM_CODE, Map.of(), pages(), null, null));

        assertEquals("FORBIDDEN", ex.getCode());
        verify(provider, never()).query(any(SysDatasourceQueryContext.class));
    }

    // ---------------------------------------------------------------- options / targets

    @Test
    void optionsHideFixedConditionsAndSkipInvalidTarget() {
        SysDatasource custom = storedRow(CUSTOM_CODE, MY_CREATED_CONDITIONS, MY_CREATED_FIELDS, true);
        SysDatasource stale = storedRow("calendar.stale", MY_CREATED_CONDITIONS, null, true);
        stale.setFieldId(OTHER_ROW_ID);
        stale.setFieldTarget("absent.target");
        when(dao.selectList(any(SysDatasource.class), isNull())).thenReturn(List.of(custom, stale));

        List<SysDatasourceOptionVo> options = service.options();

        assertEquals(2, options.size(), "失效 target 的启用行不在消费下拉暴露（R05）");
        SysDatasourceOptionVo codeOption = options.get(0);
        assertEquals(TARGET_CODE, codeOption.getCode());
        assertEquals("CODE", codeOption.getSourceType());
        assertEquals(0, codeOption.getConditionCount());
        assertEquals(9, codeOption.getFields().size(), "默认源无字段白名单 = 全字段定义");
        SysDatasourceOptionVo customOption = options.get(1);
        assertEquals(CUSTOM_CODE, customOption.getCode());
        assertEquals("CUSTOM", customOption.getSourceType());
        // A1：仅暴露固定条件数量，不暴露条件原文（OptionVo 结构不含条件原文字段）
        assertEquals(1, customOption.getConditionCount());
        assertEquals(3, customOption.getFields().size(), "自定义按 fieldFields 裁剪有效字段定义");
        assertEquals("/backend/sys/calendar/list", customOption.getTargetPath());
    }

    @Test
    void targetsExposeRegistryCapabilities() {
        List<SysDatasourceTargetVo> targets = service.targets();

        assertEquals(1, targets.size());
        SysDatasourceTargetVo target = targets.get(0);
        assertEquals(TARGET_CODE, target.getCode());
        assertEquals("日程列表", target.getName());
        assertEquals(TARGET_ACTION, target.getTargetAction());
        assertEquals("/backend/sys/calendar/list", target.getTargetPath());
        assertEquals(3, target.getConditionFields().size());
        assertEquals(9, target.getResultFields().size());
    }
}
