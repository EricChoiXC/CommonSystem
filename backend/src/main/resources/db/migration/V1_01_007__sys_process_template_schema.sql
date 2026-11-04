-- =====================================================================
-- V1.07: 流程模板模块建表
-- 依据: artificial/docs/设计文档/sys/process/流程表结构.md
-- 模型: sys_process_template 流程模板主表；
--       sys_process_template_node 模板节点（含固定节点与审批节点配置）；
--       sys_process_template_history 模板历史版本（更新模板时归档旧版本，
--       模板+节点统一序列化保存到 field_content，只增不改）。
-- 关联: 模板与节点、模板与历史均为一对多，由 field_template_id 关联，不做物理外键。
-- 约束: 节点类型枚举 START/END/DRAFT/APPROVE/ROBORT（field_node_type）；
--       field_node_config 以 JSON 字符串保存节点配置（审批人/审批模式/触发条件等）；
--       field_content 以 JSON 字符串保存历史版本的模板与全部节点信息。
-- =====================================================================

-- 流程模板主表
CREATE TABLE IF NOT EXISTS sys_process_template (
    field_id          VARCHAR(32)   NOT NULL COMMENT '主键ID',
    field_name        VARCHAR(200)  NOT NULL COMMENT '模板名称',
    field_model_name  VARCHAR(200)  NOT NULL COMMENT '所属模块(template_model二级配置项,如 biz_purchase_order)',
    field_code        VARCHAR(50)   DEFAULT NULL COMMENT '模板编号',
    field_version     INT           NOT NULL DEFAULT 1 COMMENT '版本号',
    field_remark      VARCHAR(2000) DEFAULT NULL COMMENT '备注',
    field_creator_id  VARCHAR(32)   DEFAULT NULL COMMENT '创建人',
    field_create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    field_updater_id  VARCHAR(32)   DEFAULT NULL COMMENT '修改人',
    field_update_time TIMESTAMP     NULL DEFAULT NULL COMMENT '修改时间',
    field_enable      BIT(1)        NOT NULL DEFAULT b'1' COMMENT '是否启用',
    PRIMARY KEY (field_id),
    KEY idx_template_model_name (field_model_name),
    KEY idx_template_enable (field_enable)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程模板主表';

-- 模板节点表
CREATE TABLE IF NOT EXISTS sys_process_template_node (
    field_id           VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_template_id  VARCHAR(32)  NOT NULL COMMENT '模板id',
    field_no           INT          NOT NULL COMMENT '节点编号',
    field_node_name    VARCHAR(200) NOT NULL COMMENT '节点名称',
    field_node_type    VARCHAR(20)  NOT NULL COMMENT '节点类型: START=开始节点 END=结束节点 DRAFT=草稿节点 APPROVE=审批节点 ROBORT=机器人节点',
    field_node_config  JSON         DEFAULT NULL COMMENT '节点配置(JSON对象)',
    PRIMARY KEY (field_id),
    KEY idx_template_node_template (field_template_id),
    KEY idx_template_node_no (field_template_id, field_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程模板节点表';

-- 模板历史版本表
CREATE TABLE IF NOT EXISTS sys_process_template_history (
    field_id           VARCHAR(32) NOT NULL COMMENT '主键ID',
    field_template_id  VARCHAR(32) NOT NULL COMMENT '模板id',
    field_version      INT         NOT NULL COMMENT '历史版本号',
    field_content      JSON        NOT NULL COMMENT '流程定义历史(模板+节点序列化)',
    PRIMARY KEY (field_id),
    KEY idx_template_history_template (field_template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程模板历史版本表';
