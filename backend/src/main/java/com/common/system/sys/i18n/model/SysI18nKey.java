package com.common.system.sys.i18n.model;

import com.common.system.common.base.model.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 多语言-资源模型
 *
 * @author Eric
 * @date 2026/11/5
 * @description 对应 sys_i18n_key（V1_01_040）：资源项登记，key 仅随 resources/i18n/**
 *              资源文件扫描产生，不提供人工新增/删除入口（D5）。field_model 为相对 i18n/
 *              的资源子目录层级名（如 org、process/template）。本表无 field_name /
 *              field_code 列（loadModel 仅返回 field_id，见 SysTaskHistory 先例），
 *              不继承 MainModel、无公共审计列。列清单与附录 A DDL 保持一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysI18nKey extends BaseModel {

    /** 关键字（资源 properties 的键，如 org.user.save） */
    private String fieldKey;

    /** 所属模块（相对 i18n/ 的资源子目录层级名，如 org、process/template） */
    private String fieldModel;
}
