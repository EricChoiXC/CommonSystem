-- =====================================================================
-- V1.06: 公共配置信息表
-- 依据: artificial/docs/设计文档/common/common_setting.md
-- 模型: common_setting 系统配置信息（含公共配置到各模块专有配置）
-- 关联: 配置以 (field_type, field_key) 为唯一键，系统启动时按各模块
--       application.yml 的 SETTING 段增量维护，不做物理外键。
-- 约束: 配置表只维护「配置值 + 默认值 + 更新时间」，无创建人/创建时间；
--       field_value 相比 field_default_value 变更时由应用层决定保留用户值。
-- =====================================================================

-- 公共配置信息表
CREATE TABLE IF NOT EXISTS common_setting (
    field_id            VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_model_name    VARCHAR(50)  NOT NULL COMMENT '模块',
    field_key           VARCHAR(50)  NOT NULL COMMENT '配置字段',
    field_value         VARCHAR(200) NOT NULL COMMENT '配置值',
    field_type          VARCHAR(50)  NOT NULL COMMENT '类型',
    field_update_time   TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    field_default_value VARCHAR(200) NOT NULL COMMENT '默认值',
    PRIMARY KEY (field_id),
    UNIQUE KEY uk_setting_type_key (field_type, field_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共配置信息表';
