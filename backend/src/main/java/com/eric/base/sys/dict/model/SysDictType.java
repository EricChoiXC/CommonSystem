package com.eric.base.sys.dict.model;

import com.eric.base.common.base.model.MainModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 码值类型模型
 *
 * @author Eric
 * @date 2026/9/2
 * @description 对应 sys_dict_type；码值类型按 field_code 排序展示（如 yes_no），
 *              页面左侧展示且不可维护；类型与码值项通过 field_id 一对多关联。
 *              列清单与 artificial/docs/设计文档/sys/dict/码值功能核心业务.md 保持一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysDictType extends MainModel {

    /** 描述 */
    private String fieldDesc;
}
