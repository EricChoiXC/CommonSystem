package com.eric.base.sys.process.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.process.model.SysProcessTemplateHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程模板历史版本 Mapper
 *
 * @author Eric
 * @date 2026/8/22
 * @description 与 sys_process_template_history 表一对一对接；流水只增不改。
 */
@Mapper
public interface SysProcessTemplateHistoryMapper extends IBaseMapper<SysProcessTemplateHistory> {

    /**
     * 按模板查询历史版本（按版本号升序）
     */
    List<SysProcessTemplateHistory> selectListByTemplateId(@Param("templateId") String templateId);
}
