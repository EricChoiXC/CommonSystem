package com.common.system.sys.log.aspect;

import com.common.system.common.base.model.BaseModel;
import com.common.system.common.utils.StringUtil;
import com.common.system.sys.log.annotation.OperationLog;
import com.common.system.sys.log.enums.SysLogAction;
import com.common.system.sys.log.enums.SysLogResult;
import com.common.system.sys.log.enums.SysLogType;
import com.common.system.sys.log.service.LogRecord;
import com.common.system.sys.log.service.LogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * 操作日志切面
 *
 * @author Eric
 * @date 2026/8/8
 * @description 拦截标注 {@link OperationLog} 的 Service 方法，在方法执行成功后自动记录操作日志：
 *              <ul>
 *              <li>业务文档 ID：从方法参数自动提取（BaseModel 的 fieldId / 参数名为 fieldId、documentId、id 的 String）；</li>
 *              <li>结果：以业务执行结果为准，成功记 SUCCESS，异常记 FAILED 及失败原因（异常继续上抛）；
 *                  FAILED 日志由 LogService 直接入队，不挂在主事务 afterCommit 上，回滚也不丢失；</li>
 *              <li>备注：支持 SpEL 表达式引用方法参数，解析失败回退原字符串；</li>
 *              <li>落库经 {@link LogService} 走事务隔离 + 批量异步，不影响主业务流程。</li>
 *              </ul>
 *              <p>@Order(0) 声明切面在事务拦截器外层，方法返回时事务已完成（提交/回滚），
 *              记录时的事务状态确定，避免与事务拦截器顺序不定的行为差异。</p>
 */
@Aspect
@Component
@Order(0)
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

    private final LogService logService;

    public OperationLogAspect(LogService logService) {
        this.logService = logService;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            record(joinPoint, operationLog, SysLogResult.SUCCESS.getValue(), null);
            return result;
        } catch (Throwable throwable) {
            record(joinPoint, operationLog, SysLogResult.FAILED.getValue(), throwable.getMessage());
            throw throwable;
        }
    }

    private void record(ProceedingJoinPoint joinPoint, OperationLog operationLog, String result, String failReason) {
        try {
            LogRecord record = new LogRecord();
            record.setLogType(SysLogType.OPERATION.getValue());
            record.setModule(operationLog.module());
            record.setAction(operationLog.action());
            record.setBusinessId(extractBusinessId(joinPoint));
            record.setRemark(resolveRemark(operationLog.remark(), joinPoint));
            record.setResult(result);
            record.setFailReason(failReason);
            logService.record(record);
        } catch (Exception e) {
            // 日志采集失败不影响主业务流程
            log.warn("failed to record operation log for {}.{}",
                    joinPoint.getSignature().getDeclaringType().getSimpleName(),
                    joinPoint.getSignature().getName(), e);
        }
    }

    /**
     * 提取业务文档 ID：优先取第一个非空 BaseModel 参数的 fieldId；
     * 其次取参数名为 fieldId / documentId / id 的非空 String 参数。
     */
    private String extractBusinessId(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (null == args) {
            args = new Object[0];
        }
        String[] parameterNames = parameterNames(joinPoint);
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof BaseModel model && StringUtil.isNotEmpty(model.getFieldId())) {
                return model.getFieldId();
            }
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String value && StringUtil.isNotEmpty(value)
                    && isDocumentIdParameter(parameterNames, i)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isDocumentIdParameter(String[] parameterNames, int index) {
        if (null == parameterNames || index >= parameterNames.length) {
            return false;
        }
        String name = parameterNames[index];
        return "fieldId".equals(name) || "documentId".equals(name) || "id".equals(name);
    }

    /**
     * 解析备注：以 # 开头的按 SpEL 表达式求值（可引用方法参数），解析失败回退原字符串
     */
    private String resolveRemark(String remark, ProceedingJoinPoint joinPoint) {
        if (StringUtil.isEmpty(remark) || !remark.trim().startsWith("#")) {
            return StringUtil.isEmpty(remark) ? null : remark;
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] parameterNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();
            StandardEvaluationContext context = new StandardEvaluationContext();
            if (null != parameterNames) {
                for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                    context.setVariable(parameterNames[i], args[i]);
                }
            }
            Object value = SPEL_PARSER.parseExpression(remark.trim()).getValue(context);
            return null == value ? null : String.valueOf(value);
        } catch (Exception e) {
            return remark;
        }
    }

    private String[] parameterNames(ProceedingJoinPoint joinPoint) {
        if (joinPoint.getSignature() instanceof MethodSignature signature) {
            return signature.getParameterNames();
        }
        return null;
    }
}
