package com.common.system.sys.process.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.sys.process.mapper.SysProcessAuditNoteMapper;
import com.common.system.sys.process.model.SysProcessAuditNote;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 流程审批记录数据访问对象
 *
 * @author Eric
 * @date 2026/8/23
 * @description 与 SysProcessAuditNoteMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysProcessAuditNoteDao extends BaseDao<SysProcessAuditNote, SysProcessAuditNoteMapper> {

    public SysProcessAuditNoteDao(SysProcessAuditNoteMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 按实例查询全部审批记录（按创建时间升序）
     */
    public List<SysProcessAuditNote> selectListByExampleId(String exampleId) {
        return getBaseMapper().selectListByExampleId(exampleId);
    }
}
