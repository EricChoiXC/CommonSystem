package com.common.system.sys.org.mapper;

import com.common.system.common.base.model.Pages;
import com.common.system.sys.org.model.SysOrgElement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 地址本数据访问接口
 *
 * @author Eric
 * @date 2026/8/9
 * @description 地址本选择组件专用查询，仅访问 sys_org_element 主表，不承载写操作。
 *              orgTypes / limitRange 为空时表示不限制，parentId 为空时表示查询根级。
 */
@Mapper
public interface SysOrgAddressBookMapper {

    /**
     * 树查询：parentId 为空返回根级（limitRange 非空时以范围节点为根），
     * 否则返回该节点的直接下级；结果受 orgTypes 类型过滤与 limitRange 范围过滤。
     */
    List<SysOrgElement> selectTree(@Param("orgTypes") List<Integer> orgTypes,
                                   @Param("limitRange") List<String> limitRange,
                                   @Param("parentId") String parentId);

    /**
     * 关键字列表分页查询：名称/编码模糊匹配关键字，受 orgTypes 类型过滤与 limitRange 范围过滤。
     */
    List<SysOrgElement> selectList(@Param("keyword") String keyword,
                                   @Param("orgTypes") List<Integer> orgTypes,
                                   @Param("limitRange") List<String> limitRange,
                                   @Param("pages") Pages pages);

    /**
     * 关键字列表总数：与 selectList 过滤条件完全一致，供分页 metadata.total 使用。
     */
    long countList(@Param("keyword") String keyword,
                   @Param("orgTypes") List<Integer> orgTypes,
                   @Param("limitRange") List<String> limitRange);

    /**
     * 目标回填：按 id 列表读取名称等展示信息。
     */
    List<SysOrgElement> selectByIds(@Param("ids") List<String> ids);
}
