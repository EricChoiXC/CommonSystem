package com.common.system.sys.org.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限树子节点
 *
 * @author Eric
 * @date 2026/8/10
 * @description 权限树的节点单元，前端按 type 渲染图标并拼接 prefix + name 展示。
 *              <ul>
 *                <li>element：组织架构节点，展开后显示直接关联角色、直属上级、直接关联的虚拟组织架构</li>
 *                <li>role：角色节点，展开后显示该角色直接绑定的权限</li>
 *                <li>auth：权限节点，叶子</li>
 *              </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreviewTreeChildVo {

    /** 节点类型：element 组织架构 / role 角色 / auth 权限 */
    private String type;

    /** 节点 ID：组织架构元素 ID / 角色 ID / 权限 ID */
    private String id;

    /** 节点名称（组织架构名 / 角色名 / 权限名或编码） */
    private String name;

    /** 节点展示前缀（角色/上级/虚拟组织/权限），用于直观标识分配来源 */
    private String prefix;
}
