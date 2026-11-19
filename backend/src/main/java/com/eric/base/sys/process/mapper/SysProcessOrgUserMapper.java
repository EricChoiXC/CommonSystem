package com.eric.base.sys.process.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程侧组织人员只读查询 Mapper
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-002 专用只读语句：审批人/抄送对象解析时按「部门/机构子树」展开人员
 *              （依据 docs/v1_7-流程管理/功能实现方案-改造分析与方案设计.md §5.5「部门→部门下员工、
 *              机构→机构下员工」）。org 模块现有只读入口（地址本 selectTree 仅直接下级、
 *              selectVirtualsByParentId 仅虚拟组织）无法一次取全子树人员，故以本模块 Mapper
 *              承载单条只读 SQL：按 sys_org_element.field_hierarchy 前缀匹配子树内启用人员。
 *              <p>本 Mapper 仅含 SELECT，不写任何 org 表、不改 org 表结构、不引入 org 包外的
 *              新 org 服务；语句形态与 sys_org_element 既有查询（field_org_type / field_enable /
 *              field_hierarchy 索引 idx_org_element_hierarchy）一致。</p>
 */
@Mapper
public interface SysProcessOrgUserMapper {

    /**
     * 按部门/机构子树批量查询人员 ID（field_org_type = 3 且启用）。
     *
     * @param orgIds 部门/机构元素 ID 列表（非空，由调用方保证；层级匹配口径为
     *               {@code field_hierarchy LIKE '%/<orgId>/%'}，含所查询部门/机构自身下挂的人员）
     * @return 人员 ID 列表（按 field_create_time 升序，保证解析顺序确定；同人多部门命中不去重，
     *         由调用方按「去重保序」汇总）
     */
    List<String> selectUserIdsByOrgHierarchy(@Param("orgIds") List<String> orgIds);

    /**
     * 按岗位批量查询岗位关联人员 ID（§5.5「岗位 → 岗位关联用户」）。
     *
     * @param postIds 岗位元素 ID 列表（非空，由调用方保证；空入参由调用方短路，
     *                避免 IN () 语法错误）
     * @return 人员 ID 列表（关联 sys_org_element 且 field_enable = 1，按 field_create_time 升序，
     *         保证解析顺序确定；同人多岗位命中不去重，由调用方按「去重保序」汇总）
     */
    List<String> selectUserIdsByPostIds(@Param("postIds") List<String> postIds);
}
