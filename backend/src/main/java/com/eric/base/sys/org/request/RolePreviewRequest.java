package com.eric.base.sys.org.request;

import lombok.Data;

/**
 * 权限预览请求
 *
 * @author Eric
 * @date 2026/8/10
 * @description 权限预览页右侧数据加载：请求体 { elementId }，elementId 为左侧树点中的组织架构元素 ID。
 */
@Data
public class RolePreviewRequest {

    /** 选中的组织架构元素 ID */
    private String elementId;
}
