package com.common.system.sys.att.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.att.mapper.SysAttOperationMapper;
import com.common.system.sys.att.model.SysAttOperation;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 附件操作日志数据访问对象
 *
 * @author Eric
 * @date 2026/8/7
 * @description 与 SysAttOperationMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysAttOperationDao extends BaseDao<SysAttOperation, SysAttOperationMapper> {

    public SysAttOperationDao(SysAttOperationMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 按附件 ID 查询操作日志，sortOrder 由 Service 层归一化后传入
     */
    public List<SysAttOperation> selectListByAttId(SysAttOperation criteria, Pages pages, String sortOrder) {
        return getBaseMapper().selectListByAttId(criteria, pages, sortOrder);
    }

    /**
     * 按附件 ID 统计操作日志总数（供分页 metadata.total 使用）
     */
    public long selectCountByAttId(SysAttOperation criteria) {
        return getBaseMapper().selectCountByAttId(criteria);
    }
}
