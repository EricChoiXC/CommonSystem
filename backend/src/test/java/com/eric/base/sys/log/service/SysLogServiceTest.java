package com.eric.base.sys.log.service;

import com.eric.base.sys.log.dao.SysLogOperationDao;
import com.eric.base.sys.log.enums.SysLogAction;
import com.eric.base.sys.log.enums.SysLogResult;
import com.eric.base.sys.log.enums.SysLogType;
import com.eric.base.sys.log.model.SysLogOperation;
import com.eric.base.sys.org.mapper.SysOrgUserMapper;
import com.eric.base.sys.org.model.SysOrgUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 日志业务服务单元测试
 *
 * @author Eric
 * @date 2026/8/8
 * @description 验证 LogService 记录规则：字段脱敏、长度截断、参数校验、
 *              登录成功/失败/登出/鉴权拒绝日志构建与批量异步落库路径。
 */
class SysLogServiceTest {

    private SysLogOperationDao logDao;

    private SysOrgUserMapper userMapper;

    private LogWriteBuffer buffer;

    private LogService logService;

    @BeforeEach
    void setUp() {
        logDao = mock(SysLogOperationDao.class);
        userMapper = mock(SysOrgUserMapper.class);
        buffer = new LogWriteBuffer(logDao);
        logService = new LogService(buffer, userMapper);
    }

    @Test
    void recordShouldQueueAndFlushToDao() {
        LogRecord record = new LogRecord();
        record.setLogType(SysLogType.OPERATION.getValue());
        record.setModule("SYS-ORG");
        record.setAction(SysLogAction.SAVE.getValue());
        record.setBusinessId("doc-001");
        record.setRemark("新增组织");
        record.setResult(SysLogResult.SUCCESS.getValue());

        logService.record(record);
        buffer.flush();

        ArgumentCaptor<List<SysLogOperation>> captor = ArgumentCaptor.forClass(List.class);
        verify(logDao).insertBatch(captor.capture());
        SysLogOperation saved = captor.getValue().get(0);
        assertEquals(SysLogType.OPERATION.getValue(), saved.getFieldLogType());
        assertEquals("SYS-ORG", saved.getFieldModule());
        assertEquals(SysLogAction.SAVE.getValue(), saved.getFieldAction());
        assertEquals("doc-001", saved.getFieldBusinessId());
        assertEquals(SysLogResult.SUCCESS.getValue(), saved.getFieldResult());
        assertNotNull(saved.getFieldId());
        assertNotNull(saved.getFieldOperateTime());
    }

    @Test
    void recordShouldDesensitizePasswordBeforeAfter() {
        LogRecord record = new LogRecord();
        record.setLogType(SysLogType.OPERATION.getValue());
        record.setAction(SysLogAction.UPDATE.getValue());
        record.setResult(SysLogResult.SUCCESS.getValue());
        record.setBefore("{\"fieldName\":\"张三\",\"field_password\":\"secret123\"}");
        record.setAfter("{\"fieldName\":\"李四\",\"password\":\"another\"}");

        logService.record(record);
        buffer.flush();

        ArgumentCaptor<List<SysLogOperation>> captor = ArgumentCaptor.forClass(List.class);
        verify(logDao).insertBatch(captor.capture());
        SysLogOperation saved = captor.getValue().get(0);
        assertFalse(saved.getFieldBefore().contains("secret123"));
        assertFalse(saved.getFieldAfter().contains("another"));
        assertTrue(saved.getFieldBefore().contains("***"));
        assertTrue(saved.getFieldAfter().contains("***"));
        // 非敏感字段不受影响
        assertTrue(saved.getFieldBefore().contains("张三"));
    }

    @Test
    void recordShouldTruncateOversizedFields() {
        LogRecord record = new LogRecord();
        record.setLogType(SysLogType.OPERATION.getValue());
        record.setAction(SysLogAction.SUBMIT.getValue());
        record.setResult(SysLogResult.SUCCESS.getValue());
        record.setRemark("r".repeat(600));
        record.setFailReason("f".repeat(300));

        logService.record(record);
        buffer.flush();

        ArgumentCaptor<List<SysLogOperation>> captor = ArgumentCaptor.forClass(List.class);
        verify(logDao).insertBatch(captor.capture());
        SysLogOperation saved = captor.getValue().get(0);
        assertEquals(500, saved.getFieldRemark().length());
        assertEquals(200, saved.getFieldFailReason().length());
    }

    @Test
    void recordShouldRejectInvalidParams() {
        LogRecord emptyType = new LogRecord();
        emptyType.setAction(SysLogAction.SAVE.getValue());
        emptyType.setResult(SysLogResult.SUCCESS.getValue());
        assertThrows(IllegalArgumentException.class, () -> logService.record(emptyType));

        LogRecord emptyAction = new LogRecord();
        emptyAction.setLogType(SysLogType.OPERATION.getValue());
        emptyAction.setResult(SysLogResult.SUCCESS.getValue());
        assertThrows(IllegalArgumentException.class, () -> logService.record(emptyAction));

        LogRecord emptyResult = new LogRecord();
        emptyResult.setLogType(SysLogType.OPERATION.getValue());
        emptyResult.setAction(SysLogAction.SAVE.getValue());
        assertThrows(IllegalArgumentException.class, () -> logService.record(emptyResult));
    }

    @Test
    void recordLoginFailureShouldKeepLoginNameWithoutUserId() {
        logService.recordLoginFailure("nobody", "账号或密码错误", "127.0.0.1", "ua-test");
        buffer.flush();

        ArgumentCaptor<List<SysLogOperation>> captor = ArgumentCaptor.forClass(List.class);
        verify(logDao).insertBatch(captor.capture());
        SysLogOperation saved = captor.getValue().get(0);
        assertEquals(SysLogType.LOGIN.getValue(), saved.getFieldLogType());
        assertEquals(SysLogAction.LOGIN.getValue(), saved.getFieldAction());
        assertEquals(SysLogResult.FAILED.getValue(), saved.getFieldResult());
        assertEquals("nobody", saved.getFieldLoginName());
        assertNull(saved.getFieldUserId());
        assertEquals("账号或密码错误", saved.getFieldFailReason());
        assertEquals("127.0.0.1", saved.getFieldIp());
        assertEquals("ua-test", saved.getFieldUserAgent());
    }

    @Test
    void recordLoginSuccessShouldFillUserSnapshot() {
        SysOrgUser user = new SysOrgUser();
        user.initFieldId();
        user.setFieldName("张三");
        user.setFieldLoginName("zhangsan");
        logService.recordLoginSuccess(user, "192.168.1.10", null);
        buffer.flush();

        ArgumentCaptor<List<SysLogOperation>> captor = ArgumentCaptor.forClass(List.class);
        verify(logDao).insertBatch(captor.capture());
        SysLogOperation saved = captor.getValue().get(0);
        assertEquals(SysLogResult.SUCCESS.getValue(), saved.getFieldResult());
        assertEquals(user.getFieldId(), saved.getFieldUserId());
        assertEquals("张三", saved.getFieldUserName());
        assertEquals("zhangsan", saved.getFieldLoginName());
    }

    @Test
    void recordLogoutShouldResolveCurrentUserSnapshot() {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId("u-002");
        user.setFieldName("李四");
        user.setFieldLoginName("lisi");
        when(userMapper.selectById("u-002")).thenReturn(user);

        com.eric.base.sys.org.utils.CurrentUserHolder.set("u-002");
        try {
            logService.recordLogout(null, null);
        } finally {
            com.eric.base.sys.org.utils.CurrentUserHolder.clear();
        }
        buffer.flush();

        ArgumentCaptor<List<SysLogOperation>> captor = ArgumentCaptor.forClass(List.class);
        verify(logDao).insertBatch(captor.capture());
        SysLogOperation saved = captor.getValue().get(0);
        assertEquals(SysLogType.LOGIN.getValue(), saved.getFieldLogType());
        assertEquals(SysLogAction.LOGOUT.getValue(), saved.getFieldAction());
        assertEquals("u-002", saved.getFieldUserId());
        assertEquals("李四", saved.getFieldUserName());
        assertEquals("lisi", saved.getFieldLoginName());
    }

    @Test
    void recordForbiddenShouldKeepDocumentIdAndReason() {
        logService.recordForbidden("u-003", "sys.org.user.delete", "doc-009",
                "no permission for action: sys.org.user.delete");
        buffer.flush();

        ArgumentCaptor<List<SysLogOperation>> captor = ArgumentCaptor.forClass(List.class);
        verify(logDao).insertBatch(captor.capture());
        SysLogOperation saved = captor.getValue().get(0);
        assertEquals(SysLogType.OPERATION.getValue(), saved.getFieldLogType());
        assertEquals(SysLogAction.FORBIDDEN.getValue(), saved.getFieldAction());
        assertEquals("doc-009", saved.getFieldBusinessId());
        assertEquals(SysLogResult.FAILED.getValue(), saved.getFieldResult());
        assertEquals("u-003", saved.getFieldUserId());
        assertTrue(saved.getFieldFailReason().contains("sys.org.user.delete"));
    }

    @Test
    void recordInsideActiveTransactionShouldNotQueueBeforeCommit() {
        // 手动开启事务同步（无真实事务时 isActualTransactionActive=false，日志直接入队）
        TransactionSynchronizationManager.initSynchronization();
        try {
            LogRecord record = new LogRecord();
            record.setLogType(SysLogType.OPERATION.getValue());
            record.setAction(SysLogAction.DELETE.getValue());
            record.setResult(SysLogResult.SUCCESS.getValue());
            logService.record(record);
            // 无真实事务：立即入队
            verify(logDao, never()).insertBatch(anyList());
            buffer.flush();
            verify(logDao).insertBatch(anyList());
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
}
