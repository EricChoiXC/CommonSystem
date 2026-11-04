-- =====================================================================
-- V1.08: 流程实例相关建表
-- 依据: artificial/docs/设计文档/sys/process/流程表结构.md
-- 模型: sys_process_example 流程实例主表；
--       sys_process_example_node 流程实例节点（创建实例时从模板节点快照复制）；
--       sys_process_example_workitem 流程工作项（进入审批节点时为每个审批人创建）；
--       sys_process_audit_note 审批记录（起草/审批/管理员操作的流转留痕）。
-- 关联: 实例与节点、工作项、审批记录均为一对多，由 field_example_id 关联，不做物理外键；
--       工作项经 field_node_id 关联实例节点，审批记录经 field_workitem_id 关联工作项。
-- 约束: 流程状态枚举 ABANDON/DRAFT/PROCESSING/PAUSE/FINISH/RETURN（field_status）；
--       节点类型枚举 START/END/DRAFT/APPROVE/ROBORT（field_node_type）；
--       节点状态枚举 WAITING/PROCESSING/FINISH/PASS/REJECT（field_status）；
--       工作项状态枚举 PROCESSING/FINISH/ABANDON/PAUSE（field_status）；
--       审批操作枚举 DRAFTER_SUBMIT/DRAFTER_BACK/DRAFTER_ABANDON/HANDLER_PASS/HANDLER_RETURN/
--       AUTO_PASS/ADMIN_PAUSE/ADMIN_RESUME/ADMIN_JUMP/ADMIN_ABANDON/ADMIN_FINISH（field_handle_type）；
--       field_node_config 为模板节点配置快照（JSON 字符串）；
--       field_config 为工作项配置（如驳回后的「重新提交后流转方式」，JSON 字符串）。
-- =====================================================================

-- 流程实例主表
CREATE TABLE IF NOT EXISTS sys_process_example (
    field_id               VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_template_id      VARCHAR(32)  NOT NULL COMMENT '模板id',
    field_template_version INT          NOT NULL DEFAULT 1 COMMENT '模板版本号(创建实例时的模板版本)',
    field_model_id         VARCHAR(32)  DEFAULT NULL COMMENT '所属模块id(业务文档id)',
    field_model_name       VARCHAR(200) NOT NULL COMMENT '所属模块(template_model二级配置项,如 biz_purchase_order)',
    field_code             VARCHAR(50)  DEFAULT NULL COMMENT '流程编号',
    field_creator_id       VARCHAR(32)  NOT NULL COMMENT '创建人(起草人)',
    field_create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    field_finish_time      TIMESTAMP    NULL DEFAULT NULL COMMENT '完成时间',
    field_status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '流程状态: ABANDON=废弃 DRAFT=草稿 PROCESSING=审批中 PAUSE=暂停 FINISH=完成 RETURN=驳回',
    PRIMARY KEY (field_id),
    KEY idx_example_template (field_template_id),
    KEY idx_example_model (field_model_name),
    KEY idx_example_creator (field_creator_id),
    KEY idx_example_status (field_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例主表';

-- 流程实例节点表
CREATE TABLE IF NOT EXISTS sys_process_example_node (
    field_id          VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_example_id  VARCHAR(32)  NOT NULL COMMENT '流程实例id',
    field_node_no     INT          NOT NULL COMMENT '节点编号',
    field_node_name   VARCHAR(200) NOT NULL COMMENT '节点名称',
    field_node_type   VARCHAR(20)  NOT NULL COMMENT '节点类型: START=开始节点 END=结束节点 DRAFT=草稿节点 APPROVE=审批节点 ROBORT=机器人节点',
    field_node_config JSON         DEFAULT NULL COMMENT '节点配置(JSON对象,模板节点快照)',
    field_status      VARCHAR(20)  NOT NULL DEFAULT 'WAITING' COMMENT '节点状态: WAITING=等待处理 PROCESSING=审批中 FINISH=完成 PASS=跳过 REJECT=驳回',
    PRIMARY KEY (field_id),
    KEY idx_example_node_example (field_example_id),
    KEY idx_example_node_no (field_example_id, field_node_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例节点表';

-- 流程工作项表
CREATE TABLE IF NOT EXISTS sys_process_example_workitem (
    field_id              VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_example_id      VARCHAR(32)  NOT NULL COMMENT '流程实例id',
    field_node_id         VARCHAR(32)  NOT NULL COMMENT '节点id(流程实例节点id)',
    field_handler_id      VARCHAR(32)  NOT NULL COMMENT '处理人id',
    field_expect_handler_id VARCHAR(32) DEFAULT NULL COMMENT '期望处理人id(原配置审批人,跳过时与处理人不一致)',
    field_create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    field_finish_time     TIMESTAMP    NULL DEFAULT NULL COMMENT '完成时间',
    field_status          VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING' COMMENT '工作项状态: PROCESSING=审批中 FINISH=完成 ABANDON=废弃 PAUSE=暂停',
    field_config          JSON         DEFAULT NULL COMMENT '工作项配置(JSON对象,如驳回后重新提交流转方式)',
    PRIMARY KEY (field_id),
    KEY idx_workitem_example (field_example_id),
    KEY idx_workitem_node (field_node_id),
    KEY idx_workitem_handler (field_handler_id),
    KEY idx_workitem_status (field_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程工作项表';

-- 审批记录表
CREATE TABLE IF NOT EXISTS sys_process_audit_note (
    field_id           VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_example_id   VARCHAR(32)  NOT NULL COMMENT '流程实例id',
    field_workitem_id  VARCHAR(32)  DEFAULT NULL COMMENT '工作项id(自动跳过等无工作项的操作可为空)',
    field_handler_id   VARCHAR(32)  DEFAULT NULL COMMENT '处理人id(起草人/审批人/管理员)',
    field_content      VARCHAR(200) DEFAULT NULL COMMENT '审批内容(意见/说明)',
    field_create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    field_handle_type  VARCHAR(20)  NOT NULL COMMENT '处理操作: DRAFTER_SUBMIT=起草人提交 DRAFTER_BACK=起草人收回 DRAFTER_ABANDON=起草人废弃 HANDLER_PASS=处理人通过 HANDLER_RETURN=处理人驳回 AUTO_PASS=自动跳过 ADMIN_PAUSE=管理员暂停 ADMIN_RESUME=管理员恢复 ADMIN_JUMP=管理员跳转 ADMIN_ABANDON=管理员作废 ADMIN_FINISH=管理员办结',
    PRIMARY KEY (field_id),
    KEY idx_audit_example (field_example_id),
    KEY idx_audit_workitem (field_workitem_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程审批记录表';
