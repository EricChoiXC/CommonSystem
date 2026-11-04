-- =====================================================================
-- V1.09: 码值模块建表
-- 依据: artificial/docs/设计文档/sys/dict/码值功能核心业务.md
-- 模型: sys_dict_type 为码值类型（按 field_code 排序展示，不可维护），
--       sys_dict_item 通过 field_type_id 与类型一对多关联（按 field_value 排序展示）。
-- 系统码值: 启动时读取 properties/{MODEL}/dict.yml 增量导入，field_system = true；
--           人工新增码值固定 field_system = false。
-- =====================================================================

-- 码值类型
CREATE TABLE IF NOT EXISTS sys_dict_type (
    field_id          VARCHAR(32)  NOT NULL COMMENT 'id',
    field_code        VARCHAR(50)  NOT NULL COMMENT '编号',
    field_name        VARCHAR(200) NOT NULL COMMENT '名称',
    field_desc        VARCHAR(200) DEFAULT NULL COMMENT '描述',
    field_enable      BIT(1)       NOT NULL DEFAULT b'1' COMMENT '是否启用',
    PRIMARY KEY (field_id),
    KEY idx_dict_type_code (field_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='码值类型';

-- 码值项（与 sys_dict_type 通过 field_type_id 一对多关联）
CREATE TABLE IF NOT EXISTS sys_dict_item (
    field_id          VARCHAR(32) NOT NULL COMMENT 'id',
    field_type_id     VARCHAR(32) NOT NULL COMMENT '类型id',
    field_value       VARCHAR(50) NOT NULL COMMENT '码值',
    field_name        VARCHAR(200) NOT NULL COMMENT '显示值',
    field_enable      BIT(1)      NOT NULL DEFAULT b'1' COMMENT '是否启用',
    field_system      BIT(1)      NOT NULL DEFAULT b'0' COMMENT '是否系统码值',
    PRIMARY KEY (field_id),
    KEY idx_dict_item_type (field_type_id),
    KEY idx_dict_item_value (field_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='码值项';
