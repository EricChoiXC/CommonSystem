-- =============================================================
-- 日程模块表结构
-- 依据 artificial/docs/设计文档/sys/calendar/日程功能业务.md
-- =============================================================

-- 日程主表
CREATE TABLE IF NOT EXISTS sys_calendar_main (
    field_id VARCHAR(32) NOT NULL COMMENT 'id',
    field_creator_id VARCHAR(32) NOT NULL COMMENT '创建人id',
    field_create_time DATETIME NOT NULL COMMENT '创建时间',
    field_update_time DATETIME DEFAULT NULL COMMENT '更新时间',
    field_start_time DATETIME NOT NULL COMMENT '开始时间',
    field_end_time DATETIME NOT NULL COMMENT '结束时间',
    field_name VARCHAR(200) NOT NULL COMMENT '标题',
    field_content TEXT DEFAULT NULL COMMENT '内容',
    field_type VARCHAR(20) NOT NULL DEFAULT 'PERSONAL' COMMENT '类型：PERSONAL=个人日程 TEAM=团队日程',
    PRIMARY KEY (field_id),
    KEY idx_creator_id (field_creator_id),
    KEY idx_start_time (field_start_time),
    KEY idx_type (field_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日程主表';

-- 日程人员表
CREATE TABLE IF NOT EXISTS sys_calendar_persons (
    field_id VARCHAR(32) NOT NULL COMMENT 'id',
    field_calendar_id VARCHAR(32) NOT NULL COMMENT '日程id',
    field_person_id VARCHAR(32) NOT NULL COMMENT '人员id',
    field_accept BIT DEFAULT NULL COMMENT '是否接收（可为空，NULL=未确认 1=接收 0=拒绝）',
    PRIMARY KEY (field_id),
    KEY idx_calendar_id (field_calendar_id),
    KEY idx_person_id (field_person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日程人员表';