package com.eric.base.sys.log.model;

import com.eric.base.common.base.model.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 通用日志模型
 *
 * @author Eric
 * @date 2026/8/8
 * @description 对应 sys_log_operation，操作日志与登录日志共用一张表，
 *              通过 field_log_type 区分；流水表只增不改。
 *              field_module 存 MODEL_KEY（如 SYS-ORG），fieldModuleName 为
 *              查询时经 ModelCacheService 翻译的模块中文名展示字段（不落库）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysLogOperation extends BaseModel {

    /** 日志类型: 1=操作日志 2=登录日志 */
    private String fieldLogType;

    /** 操作人id（登录失败时为空） */
    private String fieldUserId;

    /** 操作人姓名(快照) */
    private String fieldUserName;

    /** 登录名(登录日志) */
    private String fieldLoginName;

    /** 模块(MODEL_KEY，如 SYS-ORG) */
    private String fieldModule;

    /** 模块中文名(展示字段，由 ModelCacheService 翻译，不落库) */
    private String fieldModuleName;

    /** 动作(save/update/delete/submit/import/export/download/changePwd/login/logout/forbidden) */
    private String fieldAction;

    /** 业务文档id */
    private String fieldBusinessId;

    /** 变更前JSON(按需) */
    private String fieldBefore;

    /** 变更后JSON(按需) */
    private String fieldAfter;

    /** 描述/备注 */
    private String fieldRemark;

    /** 客户端IP */
    private String fieldIp;

    /** 客户端UA */
    private String fieldUserAgent;

    /** 结果: SUCCESS=成功 FAILED=失败 */
    private String fieldResult;

    /** 失败原因 */
    private String fieldFailReason;

    /** 操作时间 */
    private Date fieldOperateTime;
}
