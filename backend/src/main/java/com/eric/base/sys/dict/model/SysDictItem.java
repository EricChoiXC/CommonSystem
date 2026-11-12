package com.eric.base.sys.dict.model;

import com.eric.base.common.base.model.MainModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 码值项模型
 *
 * @author Eric
 * @date 2026/9/2
 * @description 对应 sys_dict_item；通过 field_type_id 关联 sys_dict_type（一对多）。
 *              field_system = true 的系统码值不可维护（启动时由 dict.yml 增量导入）；
 *              人工新增的码值固定 field_system = false。
 *              列清单与 artificial/docs/设计文档/sys/dict/码值功能核心业务.md 保持一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysDictItem extends MainModel {

    /** 类型id（sys_dict_type.field_id） */
    private String fieldTypeId;

    /** 码值 */
    private String fieldValue;

    /** 是否系统码值（true=启动导入，不可维护） */
    private Boolean fieldSystem;
}
