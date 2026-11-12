-- =====================================================================
-- V1_01_043: 模块组状态表（接口开关机制）
-- 依据: docs/v1_1-common/05-增加接口开关/接口开关机制设计方案-修订稿.md 第 4.1 节
--       docs/v1_1-common/05-增加接口开关/接口开关机制数据库设计分册.md 第 2 / 5 节
-- 模型: common_module_group 模块组开关状态（模块组级接口启停）
-- 关联: field_key 全局唯一（kebab-case，幂等同步键与 @ModuleGroup 注解引用键），
--       系统启动时按各模块 moduleGroup.yml 的 module-group 段增量维护，
--       不做物理外键。
-- 约束: 配置型表（与 common_setting 同），只维护「模块组元信息 + 启用状态 +
--       更新时间」，无创建人/创建时间/更新人字段；field_update_time 由数据库
--       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP 自动维护；
--       field_enable 遵循全工程 BIT(1) 启用位惯例（先例 sys_org_element 等）。
-- =====================================================================

-- 模块组状态表
CREATE TABLE IF NOT EXISTS common_module_group (
    field_id          VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_key         VARCHAR(200) NOT NULL COMMENT '模块组标识（kebab-case，全局唯一）',
    field_name        VARCHAR(200) NOT NULL COMMENT '模块组名称',
    field_enable      BIT(1)       NOT NULL DEFAULT b'1' COMMENT '模块组状态 0 关闭 1 开启',
    field_remark      VARCHAR(200) NULL COMMENT '模块组描述',
    field_update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (field_id),
    UNIQUE KEY uk_module_key (field_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块组状态表';
