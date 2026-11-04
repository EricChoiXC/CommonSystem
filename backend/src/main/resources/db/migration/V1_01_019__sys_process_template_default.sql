-- =====================================================================
-- 流程模板增加默认模板标记 field_default
-- 规则：一个模板分类（field_model_name）下最多一个 field_default=1 的模板；
-- 流程启动未指定模板（业务文档未配置 field_process_template_id）时使用默认模板，
-- 无默认模板则流程启动失败。
-- 唯一性由生成列 field_default_unique 兜底（field_default=1 时按分类唯一，NULL 不参与唯一约束）。
-- 依据 artificial/docs/设计文档/sys/process/流程核心业务.md「默认模板」。
-- =====================================================================
ALTER TABLE sys_process_template
    ADD COLUMN field_default bit NULL DEFAULT 0 COMMENT '是否默认模板（每分类最多一个，流程启动未指定模板时使用）',
    ADD COLUMN field_default_unique varchar(50) GENERATED ALWAYS AS (
        CASE WHEN field_default = 1 THEN field_model_name ELSE NULL END) STORED COMMENT '默认模板分类唯一键（生成列，field_default=1 时按分类唯一）',
    ADD UNIQUE KEY uk_sys_process_template_default (field_default_unique);

-- 存量数据：每个分类最新创建的启用模板置为默认（保持原「取分类最新启用模板」语义，行为零变化）
UPDATE sys_process_template t
JOIN (
    SELECT field_id, ROW_NUMBER() OVER (PARTITION BY field_model_name ORDER BY field_create_time DESC, field_id DESC) AS rn
    FROM sys_process_template
    WHERE field_enable = 1
) r ON t.field_id = r.field_id AND r.rn = 1
SET t.field_default = 1;
