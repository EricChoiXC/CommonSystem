-- =====================================================================
-- V1.04: 日志模块建表
-- 依据: artificial/docs/设计文档/sys/log/日志表结构.md
-- 模型: sys_log_operation 通用日志表，操作日志与登录日志共用一张表，
--       field_log_type 区分（1=操作日志 2=登录日志）。
-- 约束: 流水表只增不改，不设 field_enable / field_update_time / field_updator_id
--       等变更类公共字段；不建外键（历史快照设计，不依赖业务表数据完整性）。
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_log_operation (
    field_id           VARCHAR(32)   NOT NULL COMMENT '主键ID',
    field_log_type     VARCHAR(1)    NOT NULL COMMENT '日志类型: 1=操作日志 2=登录日志',
    field_user_id      VARCHAR(32)   DEFAULT NULL COMMENT '操作人id',
    field_user_name    VARCHAR(50)   DEFAULT NULL COMMENT '操作人姓名(快照)',
    field_login_name   VARCHAR(50)   DEFAULT NULL COMMENT '登录名(登录日志)',
    field_module       VARCHAR(50)   DEFAULT NULL COMMENT '模块(MODEL_KEY，如 SYS-ORG)',
    field_action       VARCHAR(50)   DEFAULT NULL COMMENT '动作(save/update/delete/submit/import/export/download/changePwd/login/logout/forbidden)',
    field_business_id  VARCHAR(32)   DEFAULT NULL COMMENT '业务文档id',
    field_before       LONGTEXT      DEFAULT NULL COMMENT '变更前JSON(按需)',
    field_after        LONGTEXT      DEFAULT NULL COMMENT '变更后JSON(按需)',
    field_remark       VARCHAR(500)  DEFAULT NULL COMMENT '描述/备注',
    field_ip           VARCHAR(50)   DEFAULT NULL COMMENT '客户端IP',
    field_user_agent   VARCHAR(200)  DEFAULT NULL COMMENT '客户端UA',
    field_result       VARCHAR(10)   NOT NULL COMMENT '结果: SUCCESS=成功 FAILED=失败',
    field_fail_reason  VARCHAR(200)  DEFAULT NULL COMMENT '失败原因',
    field_operate_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (field_id),
    KEY idx_log_type_time (field_log_type, field_operate_time),
    KEY idx_log_user_time (field_user_id, field_operate_time),
    KEY idx_log_business (field_module, field_business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用日志表(操作日志/登录日志)';
