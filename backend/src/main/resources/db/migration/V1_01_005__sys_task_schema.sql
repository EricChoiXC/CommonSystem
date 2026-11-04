-- =====================================================================
-- V1.05: 定时任务模块建表
-- 依据: artificial/docs/设计文档/sys/task/定时任务表结构.md
-- 模型: sys_task_main 任务主表；sys_task_example 任务执行实例
--       （一个任务同时只执行一个实例，应用层保证）；
--       sys_task_history 任务执行历史（流水，只增不改）。
-- 关联: sys_task_history.field_group 与 sys_task_example.field_group 同组对应；
--       任务主表与实例表的「一对一」由应用运行态保证，不建外键。
-- 约束: 流水表只增不改，不设 field_enable / field_update_time / field_updator_id；
--       field_params 以 JSON 字符串保存，所有值按字符串类型存储。
-- =====================================================================

-- 定时任务主表
CREATE TABLE IF NOT EXISTS sys_task_main (
    field_id          VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_name        VARCHAR(200) NOT NULL COMMENT '任务名',
    field_enable      BIT(1)       NOT NULL DEFAULT b'1' COMMENT '是否启用',
    field_cron        VARCHAR(200) NOT NULL COMMENT 'cron表达式',
    field_class       VARCHAR(200) NOT NULL COMMENT '任务类名',
    field_params      JSON         DEFAULT NULL COMMENT '参数(JSON对象，值均为字符串)',
    field_status      VARCHAR(20)  NOT NULL DEFAULT 'WAITING' COMMENT '状态: WAITING=等待中 RUNNING=运行中 FINISH=完成 BLOCK=阻塞 FAIL=失败',
    field_create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登记时间',
    PRIMARY KEY (field_id),
    UNIQUE KEY uk_task_main_class (field_class),
    KEY idx_task_main_enable (field_enable)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务主表';

-- 任务执行实例表
CREATE TABLE IF NOT EXISTS sys_task_example (
    field_id       VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_status   VARCHAR(20)  NOT NULL COMMENT '状态: WAITING=等待中 RUNNING=运行中 FINISH=完成 BLOCK=阻塞 FAIL=失败',
    field_start_time TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    field_group    VARCHAR(200) NOT NULL COMMENT '运行组',
    PRIMARY KEY (field_id),
    KEY idx_task_example_group (field_group),
    KEY idx_task_example_status (field_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行实例表';

-- 任务执行历史表
CREATE TABLE IF NOT EXISTS sys_task_history (
    field_id        VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_task_id   VARCHAR(32)  NOT NULL COMMENT '任务id',
    field_result    VARCHAR(1)   NOT NULL COMMENT '结果: 1=成功 0=失败',
    field_start_time TIMESTAMP   NOT NULL COMMENT '开始时间',
    field_end_time  TIMESTAMP    NULL DEFAULT NULL COMMENT '结束时间',
    field_group     VARCHAR(200) NOT NULL COMMENT '执行组',
    field_params    JSON         DEFAULT NULL COMMENT '执行参数(JSON对象，值均为字符串)',
    field_log       TEXT         DEFAULT NULL COMMENT '日志',
    PRIMARY KEY (field_id),
    KEY idx_task_history_task (field_task_id),
    KEY idx_task_history_start (field_start_time),
    KEY idx_task_history_end (field_end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行历史表';
