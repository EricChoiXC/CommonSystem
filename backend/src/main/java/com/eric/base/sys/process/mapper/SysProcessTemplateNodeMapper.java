package com.eric.base.sys.process.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.process.model.SysProcessTemplateNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程模板节点 Mapper
 *
 * @author Eric
 * @date 2026/8/22
 * @description 与 sys_process_template_node 表一对一对接；节点随模板编辑全量替换
 *              （先删后插），节点配置以 JSON 字符串读写。
 */
@Mapper
public interface SysProcessTemplateNodeMapper extends IBaseMapper<SysProcessTemplateNode> {

    /**
     * 按模板查询全部节点（按节点编号升序）
     */
    List<SysProcessTemplateNode> selectListByTemplateId(@Param("templateId") String templateId);

    /**
     * 删除模板全部节点（编辑全量替换前清空）
     */
    int deleteByTemplateId(@Param("templateId") String templateId);

    /**
     * 批量插入模板节点
     */
    int insertBatch(@Param("templateId") String templateId,
                    @Param("nodes") List<SysProcessTemplateNode> nodes);
}
