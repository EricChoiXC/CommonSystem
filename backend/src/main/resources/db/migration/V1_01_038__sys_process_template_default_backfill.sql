-- =====================================================================
-- 流程模板默认标记补录（默认模板 backfill）
-- 背景：V1_01_019 仅对当时已存在的模板分类做了一次「存量迁移」（每个分类最新创建的
--       启用模板置为默认）。之后新增的业务分类（如 biz_order / biz_asset 等业务分类）
--       导致这些分类在流程启动（单据提交）时抛出
--       「xx分类未配置默认模板，流程启动失败，请先在「流程管理-模板管理」中设置默认模板」。
-- 规则：对「有启用模板、且完全没有默认模板」的分类，取最新创建的启用模板置为默认，
--       与 V1_01_019 的存量迁移语义一致；唯一性由生成列 field_default_unique 兜底。
-- 安全性：仅处理 default_cnt = 0 的分类，保证不会与已有默认模板冲突；
--       已人工取消默认的分类（default_cnt 仍为 1）不会被本脚本改动。
-- 依据 artificial/docs/设计文档/sys/process/流程核心业务.md「默认模板」。
-- =====================================================================
UPDATE sys_process_template t
JOIN (
    -- 每个分类最新创建的启用模板
    SELECT e.field_id
    FROM (
        SELECT field_id, field_model_name,
               ROW_NUMBER() OVER (PARTITION BY field_model_name
                                  ORDER BY field_create_time DESC, field_id DESC) AS rn
        FROM sys_process_template
        WHERE field_enable = 1
    ) e
    -- 每个分类的默认模板数量（不区分启用状态，避免与生成列唯一键冲突）
    JOIN (
        SELECT field_model_name,
               SUM(CASE WHEN field_default = 1 THEN 1 ELSE 0 END) AS default_cnt
        FROM sys_process_template
        GROUP BY field_model_name
    ) d ON d.field_model_name = e.field_model_name
    WHERE e.rn = 1
      AND d.default_cnt = 0
) r ON t.field_id = r.field_id
SET t.field_default = 1;