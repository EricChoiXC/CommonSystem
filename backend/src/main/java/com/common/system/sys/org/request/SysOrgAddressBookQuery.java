package com.common.system.sys.org.request;

import lombok.Data;

import java.util.List;

/**
 * 地址本查询条件
 *
 * @author Eric
 * @date 2026/8/9
 * @description 地址本组件(AddressBook.vue)树查询与关键字列表查询共用的查询条件：
 *              tree 接口直接以本对象作为请求体（平铺），list 接口作为 BaseRequest.body 承载。
 *              orgTypes 为空表示不限制组织架构类型；limitRange 为空表示不限制范围。
 */
@Data
public class SysOrgAddressBookQuery {

    /** 关键字：名称或编码模糊匹配，可为空 */
    private String keyword;

    /** 组织架构类型数字数组：1=机构 2=部门 3=人员 4=岗位，为空表示全部 */
    private List<Integer> orgTypes;

    /** 限制的组织架构范围：组织架构元素 fieldId 值数组，为空表示不限制 */
    private List<String> limitRange;

    /** 上级组织架构元素 ID：为空表示查询根级（limitRange 非空时以范围节点为根） */
    private String parentId;
}
