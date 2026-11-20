package com.eric.base.sys.process.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.process.model.SysProcessExample;
import com.eric.base.sys.process.request.SysProcessExampleQuery;
import com.eric.base.sys.process.response.SysProcessExampleVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程实例 Mapper
 *
 * @author Eric
 * @date 2026/8/23
 * @description 与 sys_process_example 表一对一对接；列表查询经模板/用户关联补全
 *              流程名称（模板名）、发起人名称与当前节点名称。
 */
@Mapper
public interface SysProcessExampleMapper extends IBaseMapper<SysProcessExample> {

    /**
     * 流程实例列表总数（与 {@link #selectListSorted} 过滤条件一致，供分页 metadata.total 使用）；
     * myUserId 由 Service 层按数据范围 scope 从会话注入（admin/缺省为 null，Mapper 不读会话）
     */
    long selectCount(@Param("query") SysProcessExampleQuery query, @Param("myUserId") String myUserId);

    /**
     * 流程实例列表分页查询：流程名称（模板名）/流程编号模糊、所属模块固定、
     * 发起人名称模糊、创建时间区间与流程状态筛选；数据范围 scope（myCreated/myJoined）按
     * myUserId 强制本人过滤（myUserId 由 Service 层从会话注入，Mapper 不读会话）；
     * 单条件排序（createTime，默认降序）。
     */
    List<SysProcessExampleVo> selectListSorted(@Param("query") SysProcessExampleQuery query,
                                               @Param("pages") Pages pages,
                                               @Param("sortField") String sortField,
                                               @Param("sortOrder") String sortOrder,
                                               @Param("myUserId") String myUserId);

    /**
     * 流程监控列表分页查询（v1_7 新增，P17-BE-015，仅管理员）：管理员全量视图，不做本人数据过滤；
     * 过滤条件与 {@link #selectListSorted} 同构 + 在途筛选（inFlightOnly）与异常筛选（exceptionOnly），
     * 行内透出 exceptionFlag（是否异常实例：存在 SYSTEM_ERROR 留痕或状态 PAUSE）与当前节点名称。
     */
    List<SysProcessExampleVo> selectMonitorList(@Param("query") SysProcessExampleQuery query,
                                                @Param("pages") Pages pages,
                                                @Param("sortField") String sortField,
                                                @Param("sortOrder") String sortOrder);

    /**
     * 流程监控列表总数（与 {@link #selectMonitorList} 过滤条件一致，供分页 metadata.total 使用）
     */
    long selectMonitorCount(@Param("query") SysProcessExampleQuery query);

    /**
     * 按所属业务文档查询最近一条流程实例（field_model_name + field_model_id），
     * 供业务模块详情页反查流程实例 id 后嵌入审批面板（如项目/项目变更）。
     *
     * @param modelName 所属模块（process_template 二级配置项，如 sys_org）
     * @param modelId   业务文档 id
     * @return 最近创建的流程实例；不存在时返回 null
     */
    SysProcessExample selectByModelId(@Param("modelName") String modelName,
                                      @Param("modelId") String modelId);
}
