-- =====================================================================
-- V1.12: 组织架构名称拼音检索列（OL1-DB-001）
-- 模型: sys_org_element 增补两列，值由后端按 field_name 自动生成维护，
--       field_pinyin 名称拼音全称(小写)、field_pinyin_short 首字母拼音简称(小写)。
-- 约束: 不建索引——关键字检索为 %kw% 前缀通配，索引无效；
--       组织架构数据量万级内全表扫描可接受。
-- =====================================================================

ALTER TABLE sys_org_element
    ADD COLUMN field_pinyin       VARCHAR(1000) DEFAULT NULL COMMENT '名称拼音全称(小写,后端按 field_name 自动生成)' AFTER field_name,
    ADD COLUMN field_pinyin_short VARCHAR(300)  DEFAULT NULL COMMENT '名称拼音简称(首字母小写,后端按 field_name 自动生成)' AFTER field_pinyin;
