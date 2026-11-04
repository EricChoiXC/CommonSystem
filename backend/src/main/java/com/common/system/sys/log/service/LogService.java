package com.common.system.sys.log.service;

import com.common.system.common.utils.StringUtil;
import com.common.system.sys.log.enums.SysLogAction;
import com.common.system.sys.log.enums.SysLogResult;
import com.common.system.sys.log.enums.SysLogType;
import com.common.system.sys.log.model.SysLogOperation;
import com.common.system.sys.log.utils.LogContextUtil;
import com.common.system.sys.org.mapper.SysOrgUserMapper;
import com.common.system.sys.org.model.SysOrgUser;
import com.common.system.sys.org.utils.CurrentUserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.regex.Pattern;

/**
 * 日志业务服务（全系统统一操作日志 / 审计日志能力）
 *
 * @author Eric
 * @date 2026/8/8
 * @description 为全系统提供统一的「操作日志 / 审计日志」能力，替代各业务模块自行建表、自行写入的模式。
 *              接入方式：
 *              <ul>
 *              <li>公共 Service 调用：业务 Service 注入本类，在关键业务节点调用 {@link #record(LogRecord)}；</li>
 *              <li>注解 + AOP：Service 方法标注 {@code @OperationLog}，由切面自动采集入参、执行人、结果并落库。</li>
 *              </ul>
 *              记录规则：
 *              <ul>
 *              <li>操作人从 {@link CurrentUserHolder} 获取，姓名按用户信息冗余快照写入（历史事实不依赖联表查询）；</li>
 *              <li>敏感字段（field_password 等）写入前按字段级脱敏，不记录明文；</li>
 *              <li>结果以业务执行结果为准，成功记 SUCCESS，异常记 FAILED 及失败原因；</li>
 *              <li>事务隔离：业务提交成功（afterCommit）后经 {@link LogWriteBuffer} 批量异步落库，
 *                  日志写入失败不影响主业务流程。</li>
 *              </ul>
 */
@Service
public class LogService {

    private static final Logger log = LoggerFactory.getLogger(LogService.class);

    /** 字段级脱敏：field_password / password 的值统一替换为 *** */
    private static final Pattern SENSITIVE_FIELD_PATTERN =
            Pattern.compile("(\"(?:field_)?password\"\\s*:\\s*\")[^\"]*(\")", Pattern.CASE_INSENSITIVE);

    private final LogWriteBuffer logWriteBuffer;

    private final SysOrgUserMapper userMapper;

    public LogService(LogWriteBuffer logWriteBuffer, SysOrgUserMapper userMapper) {
        this.logWriteBuffer = logWriteBuffer;
        this.userMapper = userMapper;
    }

    /**
     * 通用操作日志记录（业务 Service 注入本类后调用）
     *
     * @param record 记录参数；logType / action / result 必填，其余为空时自动补全
     */
    public void record(LogRecord record) {
        if (null == record) {
            return;
        }
        if (null == SysLogType.of(record.getLogType())) {
            throw new IllegalArgumentException("logType must be 1(operation) or 2(login)");
        }
        if (StringUtil.isEmpty(record.getAction())) {
            throw new IllegalArgumentException("action must not be blank");
        }
        if (null == SysLogResult.of(record.getResult())) {
            throw new IllegalArgumentException("result must be SUCCESS or FAILED");
        }
        SysLogOperation logEntry = new SysLogOperation();
        logEntry.initFieldId();
        logEntry.setFieldLogType(record.getLogType().trim());
        logEntry.setFieldModule(truncate(record.getModule(), 50));
        logEntry.setFieldAction(truncate(record.getAction().trim(), 50));
        logEntry.setFieldBusinessId(truncate(record.getBusinessId(), 32));
        logEntry.setFieldBefore(desensitize(truncate(record.getBefore(), 65535)));
        logEntry.setFieldAfter(desensitize(truncate(record.getAfter(), 65535)));
        logEntry.setFieldRemark(truncate(record.getRemark(), 500));
        logEntry.setFieldResult(record.getResult().trim());
        logEntry.setFieldFailReason(truncate(record.getFailReason(), 200));
        logEntry.setFieldLoginName(truncate(record.getLoginName(), 50));
        logEntry.setFieldOperateTime(new Date());
        fillOperator(record, logEntry);
        submit(logEntry);
    }

    /**
     * 登录成功日志：记录用户 ID、登录名、姓名快照、IP、UA、操作时间
     */
    public void recordLoginSuccess(SysOrgUser user, String ip, String userAgent) {
        if (null == user) {
            return;
        }
        LogRecord record = new LogRecord();
        record.setLogType(SysLogType.LOGIN.getValue());
        record.setAction(SysLogAction.LOGIN.getValue());
        record.setResult(SysLogResult.SUCCESS.getValue());
        record.setUserId(user.getFieldId());
        record.setUserName(user.getFieldName());
        record.setLoginName(user.getFieldLoginName());
        record.setIp(ip);
        record.setUserAgent(userAgent);
        record(record);
    }

    /**
     * 登录失败日志：仅记录登录名（此时可能无对应用户，field_user_id 为空）与失败原因，不记录密码等敏感信息
     */
    public void recordLoginFailure(String loginName, String failReason, String ip, String userAgent) {
        LogRecord record = new LogRecord();
        record.setLogType(SysLogType.LOGIN.getValue());
        record.setAction(SysLogAction.LOGIN.getValue());
        record.setResult(SysLogResult.FAILED.getValue());
        record.setLoginName(loginName);
        record.setFailReason(failReason);
        record.setIp(ip);
        record.setUserAgent(userAgent);
        record(record);
    }

    /**
     * 登出日志：记录当前登录用户与请求环境
     */
    public void recordLogout(String ip, String userAgent) {
        String userId = CurrentUserHolder.get();
        SysOrgUser user = StringUtil.isEmpty(userId) ? null : userMapper.selectById(userId);
        LogRecord record = new LogRecord();
        record.setLogType(SysLogType.LOGIN.getValue());
        record.setAction(SysLogAction.LOGOUT.getValue());
        record.setResult(SysLogResult.SUCCESS.getValue());
        record.setUserId(userId);
        record.setUserName(null == user ? null : user.getFieldName());
        record.setLoginName(null == user ? null : user.getFieldLoginName());
        record.setIp(ip);
        record.setUserAgent(userAgent);
        record(record);
    }

    /**
     * 鉴权拒绝日志（安全审计）：由统一鉴权入口在 resolver 返回 FORBIDDEN 时触发，
     * 记录当前用户、接口动作、文档 ID、拒绝原因，用于定位越权尝试。
     */
    public void recordForbidden(String userId, String action, String documentId, String reason) {
        LogRecord record = new LogRecord();
        record.setLogType(SysLogType.OPERATION.getValue());
        record.setModule("SYS-LOG");
        record.setAction(SysLogAction.FORBIDDEN.getValue());
        record.setBusinessId(documentId);
        record.setResult(SysLogResult.FAILED.getValue());
        record.setFailReason(truncate(reason, 200));
        record.setUserId(userId);
        record(record);
    }

    /**
     * 补全操作人与请求环境：userId 默认取当前登录用户，姓名按用户信息冗余快照写入；
     * IP / UA 默认从请求上下文提取。
     */
    private void fillOperator(LogRecord record, SysLogOperation logEntry) {
        String userId = record.getUserId();
        if (StringUtil.isEmpty(userId)) {
            userId = CurrentUserHolder.get();
            record.setUserId(userId);
        }
        String userName = record.getUserName();
        if (StringUtil.isEmpty(userName) && StringUtil.isNotEmpty(userId)) {
            SysOrgUser user = userMapper.selectById(userId);
            if (null != user) {
                userName = user.getFieldName();
            }
        }
        logEntry.setFieldUserId(truncate(userId, 32));
        logEntry.setFieldUserName(truncate(userName, 50));
        logEntry.setFieldIp(truncate(StringUtil.isEmpty(record.getIp()) ? LogContextUtil.getClientIp() : record.getIp(), 50));
        logEntry.setFieldUserAgent(truncate(
                StringUtil.isEmpty(record.getUserAgent()) ? LogContextUtil.getUserAgent() : record.getUserAgent(), 200));
    }

    /**
     * 事务隔离提交：
     * <ul>
     * <li>SUCCESS 且当前存在真实事务时注册 afterCommit 回调，业务提交成功后才入队异步落库；</li>
     * <li>FAILED 与无事务场景直接入队——失败日志代表主业务已失败，必须独立落库，
     *     不能挂在主事务的 afterCommit 上（否则回滚时失败原因日志一并丢失）；</li>
     * <li>日志写入失败不影响主业务流程。</li>
     * </ul>
     */
    private void submit(SysLogOperation logEntry) {
        boolean failed = SysLogResult.FAILED.getValue().equals(logEntry.getFieldResult());
        if (!failed && TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    logWriteBuffer.offer(logEntry);
                }
                // afterCompletion 回滚时不写入（默认不覆盖）
            });
        } else {
            logWriteBuffer.offer(logEntry);
        }
    }

    /**
     * 字段级脱敏：before / after 变更 JSON 中的 password 类敏感字段值替换为 ***。
     * <p>注意：本方法仅兜底匹配标准 JSON 中的双引号字符串值（field_password / password），
     * 无法覆盖转义引号、非标准格式或其他敏感键名；调用方在构造 before / after 时
     * 必须自行确保不写入明文密码等敏感信息（遵循「敏感信息不写入变更内容与备注」的记录规则）。</p>
     */
    private static String desensitize(String json) {
        if (StringUtil.isEmpty(json)) {
            return json;
        }
        return SENSITIVE_FIELD_PATTERN.matcher(json).replaceAll("$1***$2");
    }

    /**
     * 按 Unicode 码点截断字符串到指定最大字符数，避免切断代理对（emoji/生僻字）导致
     * utf8mb4 列写入失败；null 原样返回。
     */
    private static String truncate(String value, int maxLength) {
        if (null == value) {
            return null;
        }
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= maxLength) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxLength));
    }
}
