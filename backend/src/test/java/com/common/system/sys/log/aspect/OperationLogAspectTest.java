package com.common.system.sys.log.aspect;

import com.common.system.sys.log.annotation.OperationLog;
import com.common.system.sys.log.enums.SysLogResult;
import com.common.system.sys.log.model.SysLogOperation;
import com.common.system.sys.log.service.LogRecord;
import com.common.system.sys.log.service.LogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 操作日志切面单元测试
 *
 * @author Eric
 * @date 2026/8/8
 * @description 验证 OperationLogAspect 的成功/失败分支、业务文档 ID 提取与 SpEL 备注解析。
 */
class OperationLogAspectTest {

    private LogService logService;

    private OperationLogAspect aspect;

    @BeforeEach
    void setUp() {
        logService = mock(LogService.class);
        aspect = new OperationLogAspect(logService);
    }

    @Test
    void successShouldRecordSuccessLog() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("ok");
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        MethodSignature signature = mockSignature(new String[]{"model"});
        when(joinPoint.getSignature()).thenReturn(signature);

        OperationLog annotation = annotation("SYS-ORG", "save", "");
        Object result = aspect.around(joinPoint, annotation);

        assertEquals("ok", result);
        ArgumentCaptor<LogRecord> captor = ArgumentCaptor.forClass(LogRecord.class);
        verify(logService).record(captor.capture());
        assertEquals(SysLogResult.SUCCESS.getValue(), captor.getValue().getResult());
        assertEquals("SYS-ORG", captor.getValue().getModule());
        assertEquals("save", captor.getValue().getAction());
    }

    @Test
    void failureShouldRecordFailedLogAndRethrow() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        MethodSignature signature = mockSignature(new String[]{"model"});
        when(joinPoint.getSignature()).thenReturn(signature);

        OperationLog annotation = annotation("SYS-ORG", "update", "");
        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> aspect.around(joinPoint, annotation));
        assertEquals("boom", thrown.getMessage());

        ArgumentCaptor<LogRecord> captor = ArgumentCaptor.forClass(LogRecord.class);
        verify(logService).record(captor.capture());
        assertEquals(SysLogResult.FAILED.getValue(), captor.getValue().getResult());
        assertEquals("boom", captor.getValue().getFailReason());
    }

    @Test
    void businessIdShouldExtractFromBaseModelParam() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("ok");
        when(joinPoint.getArgs()).thenReturn(new Object[]{new SysLogOperation() {{
            initFieldId();
        }}});
        MethodSignature signature = mockSignature(new String[]{"body"});
        when(joinPoint.getSignature()).thenReturn(signature);

        aspect.around(joinPoint, annotation("SYS-ORG", "delete", ""));

        ArgumentCaptor<LogRecord> captor = ArgumentCaptor.forClass(LogRecord.class);
        verify(logService).record(captor.capture());
        assertNotNull(captor.getValue().getBusinessId());
    }

    @Test
    void businessIdShouldExtractFromFieldIdStringParam() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("ok");
        when(joinPoint.getArgs()).thenReturn(new Object[]{"doc-abc"});
        MethodSignature signature = mockSignature(new String[]{"fieldId"});
        when(joinPoint.getSignature()).thenReturn(signature);

        aspect.around(joinPoint, annotation("SYS-ORG", "delete", ""));

        ArgumentCaptor<LogRecord> captor = ArgumentCaptor.forClass(LogRecord.class);
        verify(logService).record(captor.capture());
        assertEquals("doc-abc", captor.getValue().getBusinessId());
    }

    @Test
    void remarkShouldResolveSpelFromParam() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("ok");
        SysLogOperation model = new SysLogOperation();
        model.initFieldId();
        when(joinPoint.getArgs()).thenReturn(new Object[]{model});
        MethodSignature signature = mockSignature(new String[]{"body"});
        when(joinPoint.getSignature()).thenReturn(signature);

        aspect.around(joinPoint, annotation("SYS-ORG", "save", "#body.fieldId"));

        ArgumentCaptor<LogRecord> captor = ArgumentCaptor.forClass(LogRecord.class);
        verify(logService).record(captor.capture());
        assertEquals(model.getFieldId(), captor.getValue().getRemark());
    }

    @Test
    void recordingFailureShouldNotBreakMainFlow() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("ok");
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        MethodSignature signature = mockSignature(new String[]{"model"});
        when(joinPoint.getSignature()).thenReturn(signature);
        doThrow(new RuntimeException("log infra down")).when(logService).record(any());

        Object result = aspect.around(joinPoint, annotation("SYS-ORG", "save", ""));

        assertEquals("ok", result);
        verify(logService).record(any());
    }

    private OperationLog annotation(String module, String action, String remark) {
        OperationLog annotation = mock(OperationLog.class);
        when(annotation.module()).thenReturn(module);
        when(annotation.action()).thenReturn(action);
        when(annotation.remark()).thenReturn(remark);
        return annotation;
    }

    private MethodSignature mockSignature(String[] parameterNames) {
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getParameterNames()).thenReturn(parameterNames);
        when(signature.getName()).thenReturn("someMethod");
        when(signature.getDeclaringType()).thenReturn(Object.class);
        return signature;
    }
}
