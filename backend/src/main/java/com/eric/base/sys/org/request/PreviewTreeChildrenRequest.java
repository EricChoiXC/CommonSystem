package com.eric.base.sys.org.request;

import lombok.Data;

/**
 * 权限树子节点加载请求
 *
 * @author Eric
 * @date 2026/8/10
 * @description 权限树懒加载：请求体 { type, id }。
 *              type 取值：element=组织架构节点、role=角色节点；auth 为叶子节点不加载。
 */
@Data
public class PreviewTreeChildrenRequest {

    /** 节点类型：element / role */
    private String type;

    /** 节点 ID：组织架构元素 ID 或角色 ID */
    private String id;
}
