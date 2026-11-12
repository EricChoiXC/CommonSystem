package com.eric.base.sys.notify.service;

import com.eric.base.common.base.model.Pages;
import com.eric.base.common.cache.ModelCacheService;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.notify.dao.SysNotifyTodoDao;
import com.eric.base.sys.notify.dao.SysNotifyTodoTargetDao;
import com.eric.base.sys.notify.enums.SysNotifyType;
import com.eric.base.sys.notify.model.SysNotifyTodo;
import com.eric.base.sys.notify.model.SysNotifyTodoTarget;
import com.eric.base.sys.notify.request.SysNotifyAdminQuery;
import com.eric.base.sys.notify.request.SysNotifyTodoQuery;
import com.eric.base.sys.notify.response.SysNotifyTodoVo;
import com.eric.base.sys.org.model.SysOrgUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 待办服务业务逻辑测试
 *
 * @author Eric
 * @date 2026/8/12
 * @description 覆盖发送待办（校验与双表写入）、我的待办列表默认类型兜底、
 *              待阅详情自动转已办、星标越权拦截与后端内部自动转已办。
 */
class SysNotifyTodoServiceTest {

    /** 32 位 ID，避免 BaseModel.setFieldId 触发重新生成 */
    private static final String USER_ID = "12345678901234567890123456789001";
    private static final String TARGET_ID = "12345678901234567890123456789002";

    private SysNotifyTodoDao dao;
    private SysNotifyTodoTargetDao targetDao;
    private ModelCacheService modelCacheService;
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    private SysNotifyTodoService service;
    private MockedStatic<UserUtil> userUtil;

    @BeforeEach
    void setUp() {
        dao = mock(SysNotifyTodoDao.class);
        targetDao = mock(SysNotifyTodoTargetDao.class);
        modelCacheService = mock(ModelCacheService.class);
        eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        when(modelCacheService.getModelNames()).thenReturn(Map.of("SYS-ORG", "组织架构和权限模块"));
        service = new SysNotifyTodoService(dao, targetDao, modelCacheService, eventPublisher);
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(USER_ID);
        user.setFieldEnable(true);
        userUtil = org.mockito.Mockito.mockStatic(UserUtil.class);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        userUtil.close();
    }

    /* ==================== 发送待办 ==================== */

    @Test
    void sendWritesTodoAndTargetsForEachReceiver() {
        SysNotifyTodo todo = sampleTodo();
        when(dao.insert(todo)).thenReturn(1);
        when(targetDao.insert(any(SysNotifyTodoTarget.class))).thenReturn(1);

        SysNotifyTodo result = service.send(todo, List.of("u-1", "u-2", ""));

        assertNotNull(result.getFieldId());
        assertEquals(USER_ID, result.getFieldCreatorId());
        assertEquals("SYS-ORG", result.getFieldModelName());
        verify(dao).insert(todo);
        // 空值接收人被跳过，仅写入 u-1 / u-2 两条接收记录
        verify(targetDao, times(2)).insert(argThat(t ->
                t.getFieldTodoId().equals(todo.getFieldId())
                        && Boolean.FALSE.equals(t.getFieldIsDone())
                        && Boolean.FALSE.equals(t.getFieldIsDel())
                        && t.getFieldReceiveTime() != null));
    }

    @Test
    void sendRejectsBlankTitle() {
        SysNotifyTodo todo = sampleTodo();
        todo.setFieldName("  ");
        assertThrows(IllegalArgumentException.class, () -> service.send(todo, List.of("u-1")));
        verify(dao, never()).insert(any());
    }

    @Test
    void sendRejectsIllegalNotifyType() {
        SysNotifyTodo todo = sampleTodo();
        todo.setFieldNotifyType("9");
        assertThrows(IllegalArgumentException.class, () -> service.send(todo, List.of("u-1")));
        verify(dao, never()).insert(any());
    }

    @Test
    void sendRejectsIllegalModelName() {
        SysNotifyTodo todo = sampleTodo();
        todo.setFieldModelName("customer");
        assertThrows(IllegalArgumentException.class, () -> service.send(todo, List.of("u-1")));
        verify(dao, never()).insert(any());
    }

    @Test
    void sendRejectsEmptyReceivers() {
        assertThrows(IllegalArgumentException.class, () -> service.send(sampleTodo(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.send(sampleTodo(), null));
        verify(dao, never()).insert(any());
    }

    /* ==================== 我的待办列表 ==================== */

    @Test
    void myListDefaultsNotifyTypeToTodoAndNormalizesSort() {
        when(targetDao.selectMyList(any(), anyString(), any(Pages.class), anyString(), anyString()))
                .thenReturn(List.of());
        when(targetDao.selectMyCount(any(), anyString())).thenReturn(0L);

        service.myList(new SysNotifyTodoQuery(), null, "receiveTime", "ASC");

        verify(targetDao).selectMyList(argThat(q ->
                        SysNotifyType.TODO.getValue().equals(q.getFieldNotifyType())),
                eq(USER_ID), any(Pages.class), eq("receiveTime"), eq("asc"));
    }

    @Test
    void myListCountUsesSameDefaultType() {
        when(targetDao.selectMyCount(any(), anyString())).thenReturn(3L);
        assertEquals(3L, service.myCount(null));
        verify(targetDao).selectMyCount(argThat(q ->
                SysNotifyType.TODO.getValue().equals(q.getFieldNotifyType())), eq(USER_ID));
    }

    @Test
    void myListTranslatesModelNameFromCache() {
        SysNotifyTodoVo vo = sampleVo(SysNotifyType.TODO.getValue(), false);
        vo.setFieldModelName("SYS-ORG");
        when(targetDao.selectMyList(any(), anyString(), any(Pages.class), anyString(), anyString()))
                .thenReturn(List.of(vo));

        List<SysNotifyTodoVo> result = service.myList(new SysNotifyTodoQuery(), null, "receiveTime", "desc");

        assertEquals("组织架构和权限模块", result.get(0).getFieldModelName());
    }

    @Test
    void myListKeepsUnknownModelNameAsIs() {
        SysNotifyTodoVo vo = sampleVo(SysNotifyType.TODO.getValue(), false);
        vo.setFieldModelName("legacy-text");
        when(targetDao.selectMyList(any(), anyString(), any(Pages.class), anyString(), anyString()))
                .thenReturn(List.of(vo));

        List<SysNotifyTodoVo> result = service.myList(new SysNotifyTodoQuery(), null, "receiveTime", "desc");

        // 未知 key（历史数据）回退原值，保证仍可展示
        assertEquals("legacy-text", result.get(0).getFieldModelName());
    }

    /* ==================== 待阅详情自动转已办 ==================== */

    @Test
    void loadDetailAutoDoneForReadTypeWhenNotDone() {
        SysNotifyTodoVo vo = sampleVo(SysNotifyType.READ.getValue(), false);
        when(targetDao.selectMyVoById(TARGET_ID, USER_ID)).thenReturn(vo);

        SysNotifyTodoVo result = service.loadDetail(TARGET_ID);

        assertTrue(Boolean.TRUE.equals(result.getFieldIsDone()));
        verify(targetDao).updateDone(eq(TARGET_ID), eq(USER_ID), eq(true), any());
    }

    @Test
    void loadDetailKeepsDoneTypeUnchanged() {
        SysNotifyTodoVo vo = sampleVo(SysNotifyType.TODO.getValue(), false);
        when(targetDao.selectMyVoById(TARGET_ID, USER_ID)).thenReturn(vo);

        service.loadDetail(TARGET_ID);

        verify(targetDao, never()).updateDone(anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    void loadDetailRejectsMissingOrOthersTodo() {
        when(targetDao.selectMyVoById(TARGET_ID, USER_ID)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.loadDetail(TARGET_ID));
    }

    /* ==================== 星标越权拦截 ==================== */

    @Test
    void toggleStarRejectsOthersTarget() {
        SysNotifyTodoTarget other = new SysNotifyTodoTarget();
        other.setFieldId(TARGET_ID);
        other.setFieldUserId("another-user");
        other.setFieldIsDel(false);
        when(targetDao.selectById(TARGET_ID)).thenReturn(other);

        assertThrows(IllegalArgumentException.class, () -> service.toggleStar(TARGET_ID));
        verify(targetDao, never()).updateStar(anyString(), anyString(), anyBoolean(), any());
    }

    /* ==================== 已删除记录拦截 ==================== */

    @Test
    void markDoneRejectsDeletedTarget() {
        SysNotifyTodoTarget deleted = new SysNotifyTodoTarget();
        deleted.setFieldId(TARGET_ID);
        deleted.setFieldUserId(USER_ID);
        deleted.setFieldIsDel(true);
        when(targetDao.selectById(TARGET_ID)).thenReturn(deleted);

        assertThrows(IllegalArgumentException.class, () -> service.markDone(TARGET_ID));
        assertThrows(IllegalArgumentException.class, () -> service.markUndone(TARGET_ID));
        verify(targetDao, never()).updateDone(anyString(), anyString(), anyBoolean(), any());
    }

    /* ==================== 批量操作无影响行校验 ==================== */

    @Test
    void batchStarThrowsWhenNoRowsUpdated() {
        when(targetDao.batchUpdateStar(anyList(), anyString(), anyBoolean(), any())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.batchStar(List.of(TARGET_ID)));
        assertThrows(IllegalStateException.class, () -> service.batchDone(List.of(TARGET_ID)));
        assertThrows(IllegalStateException.class, () -> service.batchDelete(List.of(TARGET_ID)));
    }

    @Test
    void batchOperationsRejectNullOrEmptyIds() {
        assertThrows(IllegalArgumentException.class, () -> service.batchStar(null));
        assertThrows(IllegalArgumentException.class, () -> service.batchDone(List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.batchDelete(null));
        verify(targetDao, never()).batchUpdateStar(anyList(), anyString(), anyBoolean(), any());
        verify(targetDao, never()).batchUpdateDone(anyList(), anyString(), anyBoolean(), any());
        verify(targetDao, never()).batchUpdateDel(anyList(), anyString(), anyBoolean(), any());
    }

    /* ==================== 后台待办列表 ==================== */

    @Test
    void adminListDefaultsNotifyTypeToTodo() {
        when(dao.selectAdminList(any(), any(Pages.class), anyString(), anyString())).thenReturn(List.of());
        when(dao.selectAdminCount(any())).thenReturn(0L);

        service.adminList(new SysNotifyAdminQuery(), null, null, null);

        verify(dao).selectAdminList(argThat(q ->
                        SysNotifyType.TODO.getValue().equals(q.getFieldNotifyType())),
                any(Pages.class), isNull(), eq("desc"));
    }

    /* ==================== 后端内部自动转已办 ==================== */

    @Test
    void autoDoneByModelMarksMatchedTargetDone() {
        SysNotifyTodo todo = sampleTodo();
        when(dao.selectByModel("SYS-ORG", "doc-1")).thenReturn(List.of(todo));
        SysNotifyTodoTarget target = new SysNotifyTodoTarget();
        target.setFieldId(TARGET_ID);
        target.setFieldUserId(USER_ID);
        target.setFieldIsDone(false);
        when(targetDao.selectByTodoAndUser(todo.getFieldId(), USER_ID)).thenReturn(target);
        when(targetDao.updateDone(TARGET_ID, USER_ID, true, null)).thenReturn(1);

        service.autoDoneByModel("SYS-ORG", "doc-1", USER_ID);

        verify(targetDao).updateDone(eq(TARGET_ID), eq(USER_ID), eq(true), any());
    }

    @Test
    void autoDoneByModelSkipsAlreadyDone() {
        SysNotifyTodo todo = sampleTodo();
        when(dao.selectByModel("SYS-ORG", "doc-1")).thenReturn(List.of(todo));
        SysNotifyTodoTarget target = new SysNotifyTodoTarget();
        target.setFieldId(TARGET_ID);
        target.setFieldUserId(USER_ID);
        target.setFieldIsDone(true);
        when(targetDao.selectByTodoAndUser(todo.getFieldId(), USER_ID)).thenReturn(target);

        service.autoDoneByModel("SYS-ORG", "doc-1", USER_ID);

        verify(targetDao, never()).updateDone(anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    void autoDoneByModelRejectsBlankArgs() {
        assertThrows(IllegalArgumentException.class, () -> service.autoDoneByModel(" ", "doc-1", USER_ID));
        verify(dao, never()).selectByModel(anyString(), anyString());
    }

    private static SysNotifyTodo sampleTodo() {
        SysNotifyTodo todo = new SysNotifyTodo();
        todo.setFieldName("待办标题");
        todo.setFieldNotifyType(SysNotifyType.TODO.getValue());
        todo.setFieldModelName("SYS-ORG");
        todo.setFieldModelId("doc-1");
        return todo;
    }

    private static SysNotifyTodoVo sampleVo(String notifyType, boolean done) {
        SysNotifyTodoVo vo = new SysNotifyTodoVo();
        vo.setFieldId(TARGET_ID);
        vo.setFieldName("待办标题");
        vo.setFieldNotifyType(notifyType);
        vo.setFieldIsDone(done);
        return vo;
    }
}
