-- =====================================================================
-- V1_01_046: 数据源建表（可复用取数配置：查询能力 + 固定条件 + 返回字段）
-- 依据: docs/v1_3-数据源/数据源功能设计方案.md「§4.2 表结构」；
--       任务编号 F18-DB-001（docs/v1_3-数据源/数据源开发任务清单.md 4.5）。
-- 模型: sys_datasource 自定义数据源单表（默认数据源由代码 Provider 注册，不入库）；
--       field_conditions / field_fields 对齐 sys_number_rule.field_rule 先例：
--       存储态为原文 JSON，load/save 请求响应为解析对象（禁止手工拼串）。
-- 约束: field_target 保存后锁定（改目标会使条件/字段白名单悬空，D3 先例）；
--       field_code 全局唯一（须与默认数据源编码共用命名空间，由应用层查重保证）；
--       关联（field_target → Provider）由应用层保证，不建物理外键（全工程惯例）；
--       序号接续 V1_01_045，不跳号。
-- =====================================================================

-- 数据源表
CREATE TABLE IF NOT EXISTS sys_datasource (
    field_id         VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_code       VARCHAR(100) NOT NULL COMMENT '数据源编码，全局唯一（与默认数据源共用命名空间），Service 层查重',
    field_name       VARCHAR(200) NOT NULL COMMENT '数据源名称',
    field_target     VARCHAR(100) NOT NULL COMMENT '目标查询能力编码（Provider code，如 calendar.list），保存后锁定',
    field_conditions JSON         DEFAULT NULL COMMENT '固定条件 JSON：[{key,op,value}]，value 支持 ${CURRENT_USER_ID} 等运行时变量',
    field_fields     JSON         DEFAULT NULL COMMENT '返回字段白名单 JSON：字符串数组，NULL=目标能力默认全字段',
    field_remark     VARCHAR(500) DEFAULT NULL COMMENT '备注',
    field_creator_id VARCHAR(32)  DEFAULT NULL COMMENT '创建者ID',
    field_create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    field_updater_id VARCHAR(32)  DEFAULT NULL COMMENT '修改者ID',
    field_update_time TIMESTAMP   NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    field_enable     BIT(1)       NOT NULL DEFAULT b'1' COMMENT '是否启用',
    PRIMARY KEY (field_id),
    UNIQUE KEY uk_datasource_code (field_code),
    KEY idx_datasource_target (field_target)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源表（自定义数据源配置）';
