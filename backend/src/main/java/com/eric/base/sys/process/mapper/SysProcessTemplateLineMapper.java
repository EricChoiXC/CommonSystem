package com.eric.base.sys.process.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.process.model.SysProcessTemplateLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程模板连线 Mapper
 *
 * @author Eric
 * @date 2026/11/19
 * @description 与 sys_process_template_line 表一对一对接；连线是 v1_7 起流转的唯一依据，
 *              随模板保存全量替换（先按模板删旧、再批量插新，同事务）。
 */
@Mapper
public interface SysProcessTemplateLineMapper extends IBaseMapper<SysProcessTemplateLine> {

    /**
     * 按模板查询全部连线（按起始节点键、分支序号升序）
     */
    List<SysProcessTemplateLine> selectListByTemplateId(@Param("templateId") String templateId);

    /**
     * 按「模板 + 起始节点键」查询候选出边（走组合索引 idx_template_line_from，按分支序号升序）
     */
    List<SysProcessTemplateLine> selectListByTemplateIdAndFromKey(@Param("templateId") String templateId,
                                                                 @Param("fromKey") String fromKey);

    /**
     * 删除模板全部连线（保存全量替换前清空）
     */
    int deleteByTemplateId(@Param("templateId") String templateId);

    /**
     * 批量插入模板连线（保存时全量重建）
     */
    int insertBatch(@Param("templateId") String templateId,
                    @Param("lines") List<SysProcessTemplateLine> lines);
}
