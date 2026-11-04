-- =====================================================================
-- 群组模块表结构（组织架构 v1.1：field_org_type = 6）
-- 依据 docs/v1_1/01-组织架构改动/03-数据库改动设计.md 第 2 节。
-- 模型：sys_org_element 为主表（field_org_type = 6，field_parent_id / field_hierarchy 为 NULL，
--       field_level = 1，不参与组织架构树），群组扩展表 sys_org_group 与主表 field_id 同值一对一，
--       形态与 sys_org_org / sys_org_dept / sys_org_post 一致。
-- 成员明细 sys_org_group_user、可见范围明细 sys_org_group_viewer 均为复合主键关联表，
--       保存时全量替换（先删后插），与 sys_org_role_element / sys_org_post_user 风格一致。
-- 不建外键，约束由应用层（SysOrgGroupService）保证。
-- =====================================================================

-- 群组扩展表
CREATE TABLE IF NOT EXISTS sys_org_group (
    field_id             VARCHAR(32)  NOT NULL COMMENT '主键ID(与 sys_org_element.field_id 一致)',
    field_type           VARCHAR(20)  NOT NULL DEFAULT 'PERSONAL' COMMENT '群组类型: COMMON=公共 PERSONAL=个人',
    field_owner_id       VARCHAR(32)  DEFAULT NULL COMMENT '所有人ID(PERSONAL=归属人; COMMON=创建人)',
    field_is_all_viewer  BIT(1)       NOT NULL DEFAULT b'1' COMMENT '是否全员可见(由可见范围明细派生: 明细为空则为1)',
    PRIMARY KEY (field_id),
    KEY idx_org_group_type (field_type),
    KEY idx_org_group_owner (field_owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群组扩展表';

-- 群组-成员关联表：表示该群组包含的用户
CREATE TABLE IF NOT EXISTS sys_org_group_user (
    field_group_id VARCHAR(32) NOT NULL COMMENT '群组ID',
    field_user_id  VARCHAR(32) NOT NULL COMMENT '人员ID(sys_org_element.field_id, field_org_type = 3)',
    PRIMARY KEY (field_group_id, field_user_id),
    KEY idx_org_group_user_user (field_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群组-成员关联表';

-- 群组-可见范围关联表：仅 field_type = COMMON 时使用
CREATE TABLE IF NOT EXISTS sys_org_group_viewer (
    field_group_id  VARCHAR(32) NOT NULL COMMENT '群组ID',
    field_viewer_id VARCHAR(32) NOT NULL COMMENT '可见范围组织架构ID(sys_org_element.field_id, field_org_type IN (1,2,3,4))',
    PRIMARY KEY (field_group_id, field_viewer_id),
    KEY idx_org_group_viewer_viewer (field_viewer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群组-可见范围关联表';
