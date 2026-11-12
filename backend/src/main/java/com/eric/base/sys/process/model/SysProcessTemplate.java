package com.eric.base.sys.process.model;

import com.eric.base.common.base.model.MainModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程模板实体
 *
 * @author Eric
 * @date 2026/8/22
 * @description 对应 sys_process_template 表：流程模板主信息。
 *              字段命名遵循公共字段规范（field_ 前缀），公共字段继承 MainModel。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysProcessTemplate extends MainModel {

    /** 所属模块：各模块 application.yml 中 process_template 的分类 key（如 sys_org） */
    private String fieldModelName;

    /** 版本号（初始 1，每次编辑 +1） */
    private Integer fieldVersion;

    /** 备注 */
    private String fieldRemark;

    /**
     * 是否默认模板（每分类最多一个，流程启动未指定模板时使用）。
     * 置为默认时替换同分类现有默认模板（见 SysProcessTemplateService#setDefault）。
     */
    private Boolean fieldDefault;
}
