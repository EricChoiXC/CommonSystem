package com.eric.base.sys.org.request;

import lombok.Data;

import java.util.List;

/**
 * 地址本目标回填请求体
 *
 * @author Eric
 * @date 2026/8/9
 * @description 根据 id 列表读取名称等展示信息，供地址本组件已选项回填使用。
 */
@Data
public class SysOrgAddressBookLoad {

    /** 组织架构元素 fieldId 列表 */
    private List<String> ids;
}
