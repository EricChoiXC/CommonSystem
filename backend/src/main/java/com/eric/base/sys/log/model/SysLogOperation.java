package com.eric.base.sys.log.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
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
 *              <p>2026/11/17 F19-DB-002 存量标注批二：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField，列定义与 dev 库 information_schema 逐列一致；
 *              field_before / field_after 为 longtext 列（超出注解类型集不可表达），
 *              不标注（VALIDATE 比对为已知 L2 孤儿列豁免项，登记任务清单）；
 *              fieldModuleName 为不入库展示字段，不标注；公共列（field_id 主键）由
 *              引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_log_operation", comment = "通用日志表(操作日志/登录日志)",
        indexes = {
                @TableIndex(name = "idx_log_business", columns = {"field_module", "field_business_id"}),
                @TableIndex(name = "idx_log_type_time", columns = {"field_log_type", "field_operate_time"}),
                @TableIndex(name = "idx_log_user_time", columns = {"field_user_id", "field_operate_time"})})
public class SysLogOperation extends BaseModel {

    /** 日志类型: 1=操作日志 2=登录日志 */
    @ColumnField(name = "field_log_type", comment = "日志类型: 1=操作日志 2=登录日志", type = ColumnType.STRING, length = 1)
    private String fieldLogType;

    /** 操作人id（登录失败时为空） */
    @ColumnField(name = "field_user_id", comment = "操作人id", type = ColumnType.STRING, length = 32, nullable = true)
    private String fieldUserId;

    /** 操作人姓名(快照) */
    @ColumnField(name = "field_user_name", comment = "操作人姓名(快照)", type = ColumnType.STRING, length = 50, nullable = true)
    private String fieldUserName;

    /** 登录名(登录日志) */
    @ColumnField(name = "field_login_name", comment = "登录名(登录日志)", type = ColumnType.STRING, length = 50, nullable = true)
    private String fieldLoginName;

    /** 模块(MODEL_KEY，如 SYS-ORG) */
    @ColumnField(name = "field_module", comment = "模块(MODEL_KEY，如 SYS-ORG)", type = ColumnType.STRING, length = 50, nullable = true)
    private String fieldModule;

    /** 模块中文名(展示字段，由 ModelCacheService 翻译，不落库) */
    private String fieldModuleName;

    /** 动作(save/update/delete/submit/import/export/download/changePwd/login/logout/forbidden) */
    @ColumnField(name = "field_action", comment = "动作(save/update/delete/submit/import/export/download/changePwd/login/logout/forbidden)", type = ColumnType.STRING, length = 50, nullable = true)
    private String fieldAction;

    /** 业务文档id */
    @ColumnField(name = "field_business_id", comment = "业务文档id", type = ColumnType.STRING, length = 32, nullable = true)
    private String fieldBusinessId;

    /** 变更前JSON(按需) */
    private String fieldBefore;

    /** 变更后JSON(按需) */
    private String fieldAfter;

    /** 描述/备注 */
    @ColumnField(name = "field_remark", comment = "描述/备注", type = ColumnType.STRING, length = 500, nullable = true)
    private String fieldRemark;

    /** 客户端IP */
    @ColumnField(name = "field_ip", comment = "客户端IP", type = ColumnType.STRING, length = 50, nullable = true)
    private String fieldIp;

    /** 客户端UA */
    @ColumnField(name = "field_user_agent", comment = "客户端UA", type = ColumnType.STRING, length = 200, nullable = true)
    private String fieldUserAgent;

    /** 结果: SUCCESS=成功 FAILED=失败 */
    @ColumnField(name = "field_result", comment = "结果: SUCCESS=成功 FAILED=失败", type = ColumnType.STRING, length = 10)
    private String fieldResult;

    /** 失败原因 */
    @ColumnField(name = "field_fail_reason", comment = "失败原因", type = ColumnType.STRING, length = 200, nullable = true)
    private String fieldFailReason;

    /** 操作时间 */
    @ColumnField(name = "field_operate_time", comment = "操作时间", type = ColumnType.TIMESTAMP, defaultFunction = DefaultFunction.CURRENT_TIMESTAMP)
    private Date fieldOperateTime;
}
