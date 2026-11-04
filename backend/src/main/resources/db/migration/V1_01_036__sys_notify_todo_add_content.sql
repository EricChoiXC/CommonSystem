-- 待办内容：与通知待办表结构.md 的 field_content 定义保持一致。
ALTER TABLE sys_notify_todo
    ADD COLUMN field_content TEXT NULL COMMENT '内容' AFTER field_url;
