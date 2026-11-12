package com.eric.base.sys.process.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.process.model.SysProcessAuditNote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程审批记录 Mapper
 *
 * @author Eric
 * @date 2026/8/23
 * @description 与 sys_process_audit_note 表一对一对接；流转留痕只增不改，
 *              按实例查询时按创建时间升序返回。
 */
@Mapper
public interface SysProcessAuditNoteMapper extends IBaseMapper<SysProcessAuditNote> {

    /**
     * 按实例查询全部审批记录（按创建时间升序）
     */
    List<SysProcessAuditNote> selectListByExampleId(@Param("exampleId") String exampleId);
}
