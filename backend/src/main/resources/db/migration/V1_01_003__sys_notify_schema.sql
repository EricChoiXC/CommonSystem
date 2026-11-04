-- =====================================================================
-- V1.03: 通知待办模块建表
-- 依据: artificial/docs/设计文档/sys/notify/通知待办表结构.md
-- 模型: sys_notify_todo 为待办主表，sys_notify_todo_target 通过 field_todo_id
--       与主表一对多关联（一个待办发送给多个接收人）。
-- 通知类型枚举: 1=待办 2=待阅（见通知待办表结构.md）
-- =====================================================================

-- 待办主表
CREATE TABLE IF NOT EXISTS sys_notify_todo (
    field_id          VARCHAR(32)  NOT NULL COMMENT '待办ID',
    field_name        VARCHAR(200) NOT NULL COMMENT '标题',
    field_model_name  VARCHAR(200) DEFAULT NULL COMMENT '来源模块',
    field_model_id    VARCHAR(200) DEFAULT NULL COMMENT '文档id',
    field_creator_id  VARCHAR(32)  DEFAULT NULL COMMENT '创建人',
    field_create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    field_notify_type VARCHAR(1)   NOT NULL COMMENT '通知类型: 1=待办 2=待阅',
    field_url         VARCHAR(200) DEFAULT NULL COMMENT '链接',
    PRIMARY KEY (field_id),
    KEY idx_notify_todo_model (field_model_name, field_model_id),
    KEY idx_notify_todo_type (field_notify_type),
    KEY idx_notify_todo_create (field_create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办主表';

-- 待办接收人表（与 sys_notify_todo 通过 field_todo_id 一对多关联）
CREATE TABLE IF NOT EXISTS sys_notify_todo_target (
    field_id          VARCHAR(32) NOT NULL COMMENT '主键ID',
    field_todo_id     VARCHAR(32) NOT NULL COMMENT '待办id',
    field_user_id     VARCHAR(32) NOT NULL COMMENT '接收者id',
    field_is_done     BIT(1)      NOT NULL DEFAULT b'0' COMMENT '是否已办',
    field_is_star     BIT(1)      NOT NULL DEFAULT b'0' COMMENT '是否收藏',
    field_is_del      BIT(1)      NOT NULL DEFAULT b'0' COMMENT '是否删除',
    field_receive_time TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
    field_done_time   TIMESTAMP   NULL DEFAULT NULL COMMENT '办结时间',
    field_star_time   TIMESTAMP   NULL DEFAULT NULL COMMENT '收藏时间',
    field_del_time    TIMESTAMP   NULL DEFAULT NULL COMMENT '删除时间',
    PRIMARY KEY (field_id),
    KEY idx_notify_target_todo (field_todo_id),
    KEY idx_notify_target_user (field_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办接收人表';
