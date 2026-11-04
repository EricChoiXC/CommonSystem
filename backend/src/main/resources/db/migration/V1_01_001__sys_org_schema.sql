-- =====================================================================
-- V1: 组织架构模块建表
-- 依据: artificial/docs/设计文档/sys/org/组织架构表结构.md
-- 模型: sys_org_element 为主表（公共字段 + 层级字段），
--       机构/部门/人员/岗位扩展表通过 field_id 与主表一对一关联，
--       由 field_org_type(1=机构 2=部门 3=人员 4=岗位) 区分类型。
-- =====================================================================

-- 组织架构元素主表
CREATE TABLE IF NOT EXISTS sys_org_element (
    field_id          VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_name        VARCHAR(200) NOT NULL COMMENT '名称',
    field_code        VARCHAR(50)  NOT NULL COMMENT '编码',
    field_creator_id  VARCHAR(32)  DEFAULT NULL COMMENT '创建者ID',
    field_create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    field_updater_id  VARCHAR(32)  DEFAULT NULL COMMENT '修改者ID',
    field_update_time TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    field_enable      BIT(1)       NOT NULL DEFAULT b'1' COMMENT '是否启用',
    field_org_type    INT          NOT NULL COMMENT '组织类型: 1=机构 2=部门 3=人员 4=岗位',
    field_parent_id   VARCHAR(32)  DEFAULT NULL COMMENT '父级组织元素ID',
    field_hierarchy   VARCHAR(500) DEFAULT NULL COMMENT '组织层级路径(如 /id1/id2/)',
    field_level       INT          NOT NULL DEFAULT 1 COMMENT '组织层级',
    field_is_virtual  BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否虚拟组织',
    PRIMARY KEY (field_id),
    KEY idx_org_element_parent (field_parent_id),
    KEY idx_org_element_hierarchy (field_hierarchy),
    KEY idx_org_element_type (field_org_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织架构元素主表';

-- 机构扩展表（field_org_type = 1）
CREATE TABLE IF NOT EXISTS sys_org_org (
    field_id        VARCHAR(32)  NOT NULL COMMENT '主键ID(与 sys_org_element.field_id 一致)',
    field_leader_id VARCHAR(32)  DEFAULT NULL COMMENT '机构负责人用户ID',
    field_phone     VARCHAR(50)  DEFAULT NULL COMMENT '联系电话',
    field_email     VARCHAR(200) DEFAULT NULL COMMENT '联系邮箱',
    field_address   VARCHAR(500) DEFAULT NULL COMMENT '联系地址',
    PRIMARY KEY (field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机构扩展表';

-- 部门扩展表（field_org_type = 2）
CREATE TABLE IF NOT EXISTS sys_org_dept (
    field_id        VARCHAR(32)  NOT NULL COMMENT '主键ID(与 sys_org_element.field_id 一致)',
    field_leader_id VARCHAR(32)  DEFAULT NULL COMMENT '部门负责人用户ID',
    field_phone     VARCHAR(50)  DEFAULT NULL COMMENT '联系电话',
    field_address   VARCHAR(500) DEFAULT NULL COMMENT '联系地址',
    PRIMARY KEY (field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门扩展表';

-- 人员扩展表（field_org_type = 3）
CREATE TABLE IF NOT EXISTS sys_org_user (
    field_id              VARCHAR(32)  NOT NULL COMMENT '主键ID(与 sys_org_element.field_id 一致)',
    field_phone           VARCHAR(50)  DEFAULT NULL COMMENT '联系电话',
    field_email           VARCHAR(200) DEFAULT NULL COMMENT '联系邮箱',
    field_address         VARCHAR(500) DEFAULT NULL COMMENT '联系地址',
    field_gender          INT          NOT NULL DEFAULT 0 COMMENT '性别: 0=其他 1=男 2=女',
    field_birthday        DATE         DEFAULT NULL COMMENT '生日',
    field_login_name      VARCHAR(50)  DEFAULT NULL COMMENT '登录名',
    field_password        VARCHAR(255) DEFAULT NULL COMMENT '密码',
    field_job_status      INT          NOT NULL DEFAULT 1 COMMENT '在职状态: 1=在职 0=离职 2=停职 3=休假',
    field_account_status  INT          NOT NULL DEFAULT 1 COMMENT '账号状态: 1=正常 2=锁定 3=密码过期 0=禁用 4=注销',
    field_last_login_time TIMESTAMP    NULL DEFAULT NULL COMMENT '最后登录时间',
    field_last_chg_pwd_time TIMESTAMP  NULL DEFAULT NULL COMMENT '最后修改密码时间',
    PRIMARY KEY (field_id),
    UNIQUE KEY uk_org_user_login_name (field_login_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员扩展表';

-- 岗位扩展表（field_org_type = 4）
CREATE TABLE IF NOT EXISTS sys_org_post (
    field_id        VARCHAR(32) NOT NULL COMMENT '主键ID(与 sys_org_element.field_id 一致)',
    field_leader_id VARCHAR(32) DEFAULT NULL COMMENT '岗位负责人用户ID',
    PRIMARY KEY (field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位扩展表';

-- 角色表
CREATE TABLE IF NOT EXISTS sys_org_role (
    field_id          VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_name        VARCHAR(200) NOT NULL COMMENT '角色名称',
    field_code        VARCHAR(50)  DEFAULT NULL COMMENT '角色编码',
    field_creator_id  VARCHAR(32)  DEFAULT NULL COMMENT '创建者ID',
    field_create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    field_updater_id  VARCHAR(32)  DEFAULT NULL COMMENT '修改者ID',
    field_update_time TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    field_enable      BIT(1)       NOT NULL DEFAULT b'1' COMMENT '是否启用',
    PRIMARY KEY (field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 权限表
CREATE TABLE IF NOT EXISTS sys_org_auth (
    field_id   VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_name VARCHAR(200) DEFAULT NULL COMMENT '权限名称',
    field_code VARCHAR(50)  DEFAULT NULL COMMENT '权限编码',
    field_model VARCHAR(50) DEFAULT NULL COMMENT '所属模块',
    field_description VARCHAR(500) DEFAULT NULL COMMENT '权限描述',
    PRIMARY KEY (field_id),
    UNIQUE KEY uk_org_auth_code (field_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- 角色-权限关联表
CREATE TABLE IF NOT EXISTS sys_org_role_auth (
    field_auth_id VARCHAR(32) NOT NULL COMMENT '权限ID',
    field_role_id VARCHAR(32) NOT NULL COMMENT '角色ID',
    PRIMARY KEY (field_auth_id, field_role_id),
    KEY idx_role_auth_role (field_role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联表';

-- 角色-组织元素关联表
CREATE TABLE IF NOT EXISTS sys_org_role_element (
    field_element_id VARCHAR(32) NOT NULL COMMENT '组织元素ID',
    field_role_id    VARCHAR(32) NOT NULL COMMENT '角色ID',
    PRIMARY KEY (field_element_id, field_role_id),
    KEY idx_role_element_role (field_role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-组织元素关联表';

-- 岗位-人员关联表
CREATE TABLE IF NOT EXISTS sys_org_post_user (
    field_user_id VARCHAR(32) NOT NULL COMMENT '人员ID',
    field_post_id VARCHAR(32) NOT NULL COMMENT '岗位ID',
    PRIMARY KEY (field_user_id, field_post_id),
    KEY idx_post_user_post (field_post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位-人员关联表';

-- 虚拟组织-用户关联表
CREATE TABLE IF NOT EXISTS sys_org_virtual_user (
    field_virtual_id VARCHAR(32) NOT NULL COMMENT '虚拟组织元素ID',
    field_user_id    VARCHAR(32) NOT NULL COMMENT '用户ID',
    PRIMARY KEY (field_virtual_id, field_user_id),
    KEY idx_virtual_user_user (field_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='虚拟组织-用户关联表';

-- 密码历史表
CREATE TABLE IF NOT EXISTS sys_org_pwd_history (
    field_id       VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_user_id  VARCHAR(32)  NOT NULL COMMENT '用户ID',
    field_pwd      VARCHAR(255) NOT NULL COMMENT '历史密码',
    field_version  INT          NOT NULL DEFAULT 1 COMMENT '密码版本',
    PRIMARY KEY (field_id),
    KEY idx_pwd_history_user (field_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密码历史表';

-- 机构管理员表
CREATE TABLE IF NOT EXISTS sys_org_org_manager (
    field_org_id       VARCHAR(32)  NOT NULL COMMENT '机构ID',
    field_user_id      VARCHAR(32)  NOT NULL COMMENT '管理员用户ID',
    field_description  VARCHAR(500) DEFAULT NULL COMMENT '描述',
    PRIMARY KEY (field_org_id, field_user_id),
    KEY idx_org_manager_user (field_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机构管理员表';

-- 部门管理员表
CREATE TABLE IF NOT EXISTS sys_org_dept_manager (
    field_dept_id      VARCHAR(32)  NOT NULL COMMENT '部门ID',
    field_user_id      VARCHAR(32)  NOT NULL COMMENT '管理员用户ID',
    field_description  VARCHAR(500) DEFAULT NULL COMMENT '描述',
    PRIMARY KEY (field_dept_id, field_user_id),
    KEY idx_dept_manager_user (field_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门管理员表';
