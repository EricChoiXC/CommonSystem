-- =====================================================================
-- V1_01_045: 编号取号明细表唯一键放宽（reset 重开重发支持）
-- 依据: docs/v1_5-编号功能/编号机制实现方案.md「§5.4 重置、补号、作废语义」——
--       重置 reset 封存计数器行后，下次取号按 start 重开，「会造成重号」属预期语义；
--       任务编号 NB3-BE-005（docs/v1_5-编号功能/00-研发任务清单.md 5.9）。
-- 背景: NB2-BE-002 遗留收口点——reset 封存后重开按 start 起号时，重开区段序号可能与
--       既有明细（同 scope + serialId + period + serialNo）冲突，原唯一键会阻断取号；
--       明细为流水表只增不改（审计保留），重开重发发生在不同取号时刻。
-- 变更: uk_number_record_detail_no 放宽为含 field_record_time（取号时间）：
--       重开重发因取号时刻不同不再被旧明细阻断，同时保留同批次（同取号时刻）内防重兜底；
--       幂等预检（valid 明细按 scope+model）与删除保护计数（countByScopeKey）不受影响。
-- 序号: 接续 V1_01_044（sys_number_schema），接续递增。
-- =====================================================================

ALTER TABLE sys_number_record_detail
    DROP KEY uk_number_record_detail_no,
    ADD UNIQUE KEY uk_number_record_detail_no (field_scope_key, field_serial_id, field_period, field_serial_no, field_record_time);
