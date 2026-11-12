package com.eric.base.sys.org.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 组织架构元素查询条件
 *
 * @author Eric
 * @date 2026/8/10
 * @description 组织架构管理页右侧列表的查询条件：
 *              按点中的树节点（parentId，根节点时为空）查询其直接下级，
 *              并可叠加名称/编码模糊、组织架构类型、启用状态筛选；
 *              v1.2 组织架构列表页补充 flat 全层级平铺与 keyword 单关键字检索，
 *              两字段缺省（null）时行为与树页现状完全一致（方案 5.3 兼容性要求）。
 */
@Data
@Schema(description = "组织架构元素列表查询条件")
public class SysOrgElementQuery {

    /** 上级组织架构元素ID：为空表示查询根级（field_parent_id 为空） */
    private String parentId;

    /** 名称模糊匹配 */
    private String fieldName;

    /** 编码模糊匹配 */
    private String fieldCode;

    /** 组织架构类型：1=机构 2=部门 3=人员 4=岗位，为空表示全部 */
    private Integer orgType;

    /** 启用状态：为空表示全部 */
    private Boolean enable;

    @Schema(description = "全层级平铺查询：true 时忽略 parentId 语义，返回所有层级元素（组织架构列表页用）；为空或 false 时维持树页按上级查询语义")
    private Boolean flat;

    @Schema(description = "单关键字：对名称 / 编码 / 拼音 / 拼音首字母（field_name / field_code / field_pinyin / field_pinyin_short）四列做 OR 模糊匹配，为空表示不启用")
    private String keyword;
}
