package com.eric.base.sys.number.model;

import lombok.Data;

/**
 * 编号接入项文档对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 对应 numberRule.yml 中 numberRule 段的单个接入项条目（方案 §1.1）：
 *              key 为接入项编码（落 sys_number_rule.field_model_id，保存后锁定 D3），
 *              name 为下拉展示名，modelName / modelField 由下拉选定后带出落库。
 *              启动期由 {@link com.eric.base.sys.number.service.SysNumberModelCacheService}
 *              扫描 classpath*:properties/**\/numberRule.yml 合并为进程内缓存（A1 不入 Redis），
 *              同时作为 NB3-BE-003 modelOptions 下拉数据源读取入口。
 */
@Data
public class SysNumberModelDo {

    /** 接入项编码（numberRule.yml key，即 sys_number_rule.field_model_id） */
    private String key;

    /** 接入项名称（下拉展示名） */
    private String name;

    /** 业务模型 Java 类 SimpleName */
    private String modelName;

    /** 承接编号的字段名（通常 fieldCode） */
    private String modelField;
}
