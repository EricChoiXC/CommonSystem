-- =====================================================================
-- 人员扩展表(sys_org_user)新增第三方账号绑定列 field_openid
-- 依据 docs/v1_2-用户管理中心/个人资料页与第三方账号绑定/个人资料页与第三方账号绑定开发方案.md 第 4.1 节。
-- field_openid 存 JSON 字符串，外层结构 { "wechat": {...}, "qq": {...} }：
--       wechat 内层为 openid/unionid/nickname/bindTime，qq 内层为 openid/nickname/bindTime，
--       bindTime 为 "yyyy-MM-dd HH:mm:ss" 格式字符串；结构化 POJO 见 SysOrgUserOpenidDo。
-- 注：方案正文草稿写 AFTER field_account_status，与实际建表列序不符——
--       实际列序 field_account_status 之后还有 field_last_login_time、field_last_chg_pwd_time
--       （见 V1_01_001__sys_org_schema.sql），故按实际表结构放在列尾 field_last_chg_pwd_time 之后。
-- openid 全局唯一、绑定前须查重（见 SysOrgUserMapper.countDuplicateOpenid），不建索引。
-- =====================================================================

ALTER TABLE sys_org_user
    ADD COLUMN field_openid JSON DEFAULT NULL COMMENT '第三方账号绑定信息(JSON)' AFTER field_last_chg_pwd_time;
