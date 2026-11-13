-- =====================================================================
-- V1_01_044: 编号机制建表（编号规则配置与业务自取号）
-- 依据: docs/v1_5-编号功能/编号机制实现方案.md「§核心表和字段（mermaid 与索引表）」「§6」；
--       任务编号 NB1-DB-001（docs/v1_5-编号功能/00-研发任务清单.md 5.1）。
-- 模型: sys_number_rule 编号规则表（规则单表 JSON，field_rule 结构见方案 §2，
--       同一接入项允许多条规则、同一时刻至多一条启用，切换式启用由应用层同事务保证）；
--       sys_number_record 取号计数表（作用域 × 流水项 × 周期一行，原子自增锚点）；
--       sys_number_record_detail 取号明细表（每次取号一行，支撑幂等/作废/补号/审计）。
-- 关联: sys_number_record.field_rule_id、sys_number_record_detail 与
--       sys_number_rule 的关联由应用层保证，不建物理外键（全工程惯例）。
-- 约束: sys_number_record / sys_number_record_detail 属流水类表，只增不改，
--       不设 field_enable / field_update_time / field_updater_id
--       （对齐 sys_log_operation、sys_task_history 先例）；
--       作废为例外更新：仅置 field_void_time / field_void_reason 两字段，不回退计数器（方案 §5.4）；
--       field_code（发出的完整编号）列宽对齐业务单号列宽 varchar(64)（方案 §2.3 C5）；
--       序号接续 V1_01_043（已被 common_module_group_schema 占用），接续递增（D10）。
-- =====================================================================

-- 编号规则表
CREATE TABLE IF NOT EXISTS sys_number_rule (
    field_id            VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_code          VARCHAR(50)  NOT NULL COMMENT '规则编码，人工维护，Service 层查重',
    field_name          VARCHAR(200) NOT NULL COMMENT '编号规则名称',
    field_rule          JSON         DEFAULT NULL COMMENT '编号规则 JSON（SysNumberRuleConfigDo，结构见方案 §2）',
    field_model_id      VARCHAR(100) NOT NULL COMMENT '所属接入项编码（numberRule.yml key），保存后锁定',
    field_model_name    VARCHAR(200) DEFAULT NULL COMMENT '所属模型名（下拉带出）',
    field_model_field   VARCHAR(100) DEFAULT NULL COMMENT '所属模型字段（下拉带出）',
    field_creator_id    VARCHAR(32)  DEFAULT NULL COMMENT '创建者ID',
    field_create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    field_updater_id    VARCHAR(32)  DEFAULT NULL COMMENT '修改者ID',
    field_update_time   TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    field_enable        BIT(1)       NOT NULL DEFAULT b'1' COMMENT '是否启用（同接入项至多一条启用，切换式启用）',
    PRIMARY KEY (field_id),
    KEY idx_number_rule_code (field_code),
    KEY idx_number_rule_model (field_model_name, field_model_field)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='编号规则表';

-- 取号计数表（作用域 × 流水项 × 周期一行；流水类表，只增不改，无启用/更新审计字段）
CREATE TABLE IF NOT EXISTS sys_number_record (
    field_id                 VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_rule_id            VARCHAR(32)  DEFAULT NULL COMMENT '编号规则ID（非固定接入为空）',
    field_scope_key          VARCHAR(200) NOT NULL COMMENT '取号作用域键（固定接入=规则field_id，非固定接入={modelName}:{宿主记录ID}）',
    field_serial_id          VARCHAR(64)  NOT NULL COMMENT '流水号项ID（field_rule 中 seq 项 item.id）',
    field_period             VARCHAR(32)  NOT NULL COMMENT '周期键（NONE=ALL/DAY=yyyyMMdd/WEEK=yyyyWww/MONTH=yyyyMM/YEAR=yyyy）',
    field_serial_no          INT          NOT NULL COMMENT '当前流水号（该作用域+流水项+周期已取到的最大值，首号按 start 初始化）',
    field_record_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近取号时间',
    field_record_finish_time TIMESTAMP    NULL DEFAULT NULL COMMENT '封存时间（周期结束或人工重置时置值，NULL=使用中）',
    PRIMARY KEY (field_id),
    UNIQUE KEY uk_number_record_scope (field_scope_key, field_serial_id, field_period),
    KEY idx_number_record_rule (field_rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='编号取号计数表';

-- 取号明细表（每次取号一行；流水类表只增不改，作废为例外更新：仅 void 两字段）
CREATE TABLE IF NOT EXISTS sys_number_record_detail (
    field_id          VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_scope_key   VARCHAR(200) NOT NULL COMMENT '取号作用域键',
    field_period      VARCHAR(32)  NOT NULL COMMENT '周期键',
    field_serial_id   VARCHAR(64)  NOT NULL COMMENT '流水号项ID（field_rule 中 seq 项 item.id）',
    field_serial_no   INT          NOT NULL COMMENT '本次取到的流水号',
    field_model_name  VARCHAR(200) NOT NULL COMMENT '业务模型名',
    field_model_id    VARCHAR(32)  NOT NULL COMMENT '业务单据ID，幂等锚点',
    field_code        VARCHAR(64)  NOT NULL COMMENT '发出的完整编号（对齐业务单号列宽 varchar(64)）',
    field_record_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '取号时间',
    field_creator_id  VARCHAR(32)  DEFAULT NULL COMMENT '取号操作人',
    field_void_time   TIMESTAMP    NULL DEFAULT NULL COMMENT '作废时间，NULL=有效',
    field_void_reason VARCHAR(200) DEFAULT NULL COMMENT '作废原因',
    PRIMARY KEY (field_id),
    UNIQUE KEY uk_number_record_detail_no (field_scope_key, field_serial_id, field_period, field_serial_no),
    KEY idx_number_record_detail_model (field_model_name, field_model_id),
    KEY idx_number_record_detail_code (field_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='编号取号明细表';
