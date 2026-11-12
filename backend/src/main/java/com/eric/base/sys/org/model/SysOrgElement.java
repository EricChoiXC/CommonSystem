package com.eric.base.sys.org.model;

import com.eric.base.common.base.model.MainModel;
import lombok.Data;

/**
 * @author Eric
 * @date 2026/8/5
 * @description
 */
@Data
public class SysOrgElement extends MainModel {

    /** 父级组织 */
    private SysOrgOrg fieldParent;

    /** 父级组织ID */
    private String fieldParentId;

    /** 父级组织名称 */
    private String fieldParentName;

    /** 组织层级id */
    private String fieldHierarchy;

    /** 组织层级 */
    private int fieldLevel;

    /** 组织类型 */
    private int fieldOrgType;

    /** 是否虚拟组织 */
    private boolean fieldIsVirtual;

    /** 名称拼音全称(小写,后端按 field_name 自动生成) */
    private String fieldPinyin;

    /** 名称拼音简称(首字母小写,后端按 field_name 自动生成) */
    private String fieldPinyinShort;
}
