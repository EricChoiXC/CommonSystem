-- =====================================================================
-- V1.10: 多语言模块建表
-- 依据: docs/设计文档/sys/i18n/多语言管理功能分析报告.md 附录 A（实施基线已冻结，D1–D5）。
-- 模型: sys_i18n_language 语言清单（field_default 全局仅一条，由应用层互斥切换保证）；
--       sys_i18n_key 资源登记（key 仅随 resources/i18n/** 资源文件扫描产生，不提供人工维护入口，D5）；
--       sys_i18n_translation 翻译文本（(field_key, field_language) 业务唯一由代码层保证，不建唯一约束，
--       便于重置删插；field_update_time 为「人工编辑标记」：非空=人工已编辑，启动/部署同步不覆盖）；
--       sys_i18n_user_language 用户语言登记（field_user_id 为主键，一人一条，登录 upsert）。
-- 约束: 不建外键，关联与唯一性由应用层保证；
--       删除语言时级联删除其翻译、用户语言登记改绑默认语言（D4，应用层同事务完成）。
-- =====================================================================

-- 语言表
CREATE TABLE IF NOT EXISTS sys_i18n_language (
    field_id       VARCHAR(32)  NOT NULL COMMENT 'id',
    field_code     VARCHAR(20)  NOT NULL COMMENT '语言码（如 zh_CN、en_US）',
    field_name     VARCHAR(200) NOT NULL COMMENT '名称（如 中文、English）',
    field_enable   BIT(1)       NOT NULL DEFAULT b'1' COMMENT '是否启用',
    field_default  BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否默认语言（系统默认中文 zh_CN，全局仅一条）',
    field_order    INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    PRIMARY KEY (field_id),
    KEY idx_i18n_language_code (field_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多语言-语言表';

-- 资源表
CREATE TABLE IF NOT EXISTS sys_i18n_key (
    field_id       VARCHAR(32)  NOT NULL COMMENT 'id',
    field_key      VARCHAR(200) NOT NULL COMMENT '关键字（资源 properties 的键）',
    field_model    VARCHAR(200) NOT NULL COMMENT '所属模块（相对 i18n/ 的资源子目录层级名，如 org、process/template）',
    PRIMARY KEY (field_id),
    KEY idx_i18n_key_model_key (field_model, field_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多语言-资源表';

-- 翻译表（(field_key, field_language) 业务唯一由代码层保证、不建唯一约束，便于重置删插；
-- field_update_time 非空=人工已编辑，启动/部署同步不覆盖）
CREATE TABLE IF NOT EXISTS sys_i18n_translation (
    field_id            VARCHAR(32)  NOT NULL COMMENT 'id',
    field_key           VARCHAR(200) NOT NULL COMMENT '关键字（sys_i18n_key.field_key）',
    field_language      VARCHAR(20)  NOT NULL COMMENT '语言码（sys_i18n_language.field_code）',
    field_message       TEXT                  COMMENT '翻译文本',
    field_update_time   DATETIME              COMMENT '更新时间（非空=人工已编辑，启动/部署同步不覆盖；为空=资源文件导入）',
    PRIMARY KEY (field_id),
    KEY idx_i18n_translation_key_language (field_key, field_language)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多语言-翻译表';

-- 用户语言表（一人一条，field_user_id 为主键；删除语言时已选该语言的登记改绑默认语言，不删行）
CREATE TABLE IF NOT EXISTS sys_i18n_user_language (
    field_user_id     VARCHAR(32) NOT NULL COMMENT '用户id（sys_org_user.field_id）',
    field_language_id VARCHAR(32) NOT NULL COMMENT '语言id（sys_i18n_language.field_id）',
    PRIMARY KEY (field_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多语言-用户语言表';
