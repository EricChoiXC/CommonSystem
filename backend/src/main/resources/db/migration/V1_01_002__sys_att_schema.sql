-- =====================================================================
-- V1.02: 附件模块建表
-- 依据: artificial/docs/设计文档/sys/attachment/附件表结构.md
-- 模型: sys_att_main 为附件主表，sys_att_file 通过 field_id 与主表一对一关联
--       （上传时两表同 ID 写入），sys_att_operation 记录附件操作日志。
-- 操作类型枚举(见附件核心业务.md)：1=上传 2=修改 3=删除 4=后台删除 5=下载 6=浏览
-- =====================================================================

-- 附件主表
CREATE TABLE IF NOT EXISTS sys_att_main (
    field_id          VARCHAR(32)  NOT NULL COMMENT '附件ID',
    field_name        VARCHAR(200) NOT NULL COMMENT '文件名',
    field_file_type   VARCHAR(50)  DEFAULT NULL COMMENT '文件类型(扩展名)',
    field_model_name  VARCHAR(200) DEFAULT NULL COMMENT '模块名',
    field_model_id    VARCHAR(200) DEFAULT NULL COMMENT '文档id',
    field_key         VARCHAR(200) DEFAULT NULL COMMENT '关键字',
    field_enable      BIT(1)       NOT NULL DEFAULT b'1' COMMENT '是否有效',
    field_creator_id  VARCHAR(32)  DEFAULT NULL COMMENT '上传者id',
    field_create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    field_download    INT          NOT NULL DEFAULT 0 COMMENT '下载次数',
    field_view        INT          NOT NULL DEFAULT 0 COMMENT '预览次数',
    PRIMARY KEY (field_id),
    KEY idx_att_main_model (field_model_name, field_model_id),
    KEY idx_att_main_key (field_key),
    KEY idx_att_main_creator (field_creator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='附件主表';

-- 附件文件表（与 sys_att_main 通过 field_id 一对一关联）
CREATE TABLE IF NOT EXISTS sys_att_file (
    field_id          VARCHAR(32)  NOT NULL COMMENT '文件ID(与 sys_att_main.field_id 一致)',
    field_name        VARCHAR(200) NOT NULL COMMENT '文件名',
    field_file_type   VARCHAR(50)  DEFAULT NULL COMMENT '文件类型(扩展名)',
    field_file_path   VARCHAR(200) DEFAULT NULL COMMENT '保存路径(相对存储根目录)',
    field_create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    field_file_size   BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小(字节)',
    field_is_delete   BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='附件文件表';

-- 附件操作日志表
CREATE TABLE IF NOT EXISTS sys_att_operation (
    field_id          VARCHAR(32)  NOT NULL COMMENT '主键ID',
    field_att_id      VARCHAR(32)  NOT NULL COMMENT '操作附件id',
    field_user_id     VARCHAR(32)  DEFAULT NULL COMMENT '操作人id',
    field_operate_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    field_operate_type VARCHAR(1)  NOT NULL COMMENT '操作类型: 1=上传 2=修改 3=删除 4=后台删除 5=下载 6=浏览',
    field_remark      VARCHAR(200) DEFAULT NULL COMMENT '操作备注',
    PRIMARY KEY (field_id),
    KEY idx_att_operation_att (field_att_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='附件操作日志表';
