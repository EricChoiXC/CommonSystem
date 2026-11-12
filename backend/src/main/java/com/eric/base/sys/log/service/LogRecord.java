package com.eric.base.sys.log.service;

import lombok.Data;

/**
 * 日志记录参数
 *
 * @author Eric
 * @date 2026/8/8
 * @description 业务 Service 注入 {@link LogService} 后，在关键业务节点调用
 *              {@link LogService#record(LogRecord)} 时使用的记录载体：
 *              适用于需要携带自定义业务描述（备注、变更内容）的场景。
 *              userId / userName / loginName / ip / userAgent 未显式指定时，
 *              由 LogService 从当前用户与请求上下文自动补全。
 */
@Data
public class LogRecord {

    /** 日志类型（必填）：1=操作日志 2=登录日志 */
    private String logType;

    /** 模块（MODEL_KEY，如 SYS-ORG）；登录日志可为空 */
    private String module;

    /** 动作（必填）：save/update/delete/submit/import/export/download/changePwd/login/logout/forbidden */
    private String action;

    /** 业务文档 id（可选，单据类操作建议携带，便于按单据追溯） */
    private String businessId;

    /** 描述/备注（可选） */
    private String remark;

    /** 变更前 JSON（可选，写入前按字段级脱敏） */
    private String before;

    /** 变更后 JSON（可选，写入前按字段级脱敏） */
    private String after;

    /** 结果（必填）：SUCCESS=成功 FAILED=失败 */
    private String result;

    /** 失败原因（可选，失败时建议填写） */
    private String failReason;

    /** 操作人 id（可选，默认取当前登录用户） */
    private String userId;

    /** 操作人姓名快照（可选，默认按当前用户查询补全） */
    private String userName;

    /** 登录名（登录日志使用） */
    private String loginName;

    /** 客户端 IP（可选，默认从请求上下文提取） */
    private String ip;

    /** 客户端 UA（可选，默认从请求上下文提取） */
    private String userAgent;
}
