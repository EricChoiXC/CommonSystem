package com.eric.base.sys.process.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.process.model.SysProcessExampleLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程实例连线 Mapper
 *
 * @author Eric
 * @date 2026/11/19
 * @description 与 sys_process_example_line 表一对一对接；本表为实例创建时从模板连线全量快照
 *              复制的只读图结构，流转引擎按「实例 + 起始节点键」取出边，创建后不再更新。
 */
@Mapper
public interface SysProcessExampleLineMapper extends IBaseMapper<SysProcessExampleLine> {

    /**
     * 按实例查询全部连线（按起始节点键、分支序号升序）
     */
    List<SysProcessExampleLine> selectListByExampleId(@Param("exampleId") String exampleId);

    /**
     * 按「实例 + 起始节点键」查询候选出边（走组合索引 idx_example_line_from，按分支序号升序）
     */
    List<SysProcessExampleLine> selectListByExampleIdAndFromKey(@Param("exampleId") String exampleId,
                                                               @Param("fromKey") String fromKey);

    /**
     * 批量插入实例连线（创建实例时从模板连线全量快照复制）
     */
    int insertBatch(@Param("exampleId") String exampleId,
                    @Param("lines") List<SysProcessExampleLine> lines);

    /**
     * 删除实例全部连线（仅供实例重建/异常清理，正常流程不调用）
     */
    int deleteByExampleId(@Param("exampleId") String exampleId);
}
