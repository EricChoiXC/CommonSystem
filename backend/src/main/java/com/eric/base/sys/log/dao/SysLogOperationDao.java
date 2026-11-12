package com.eric.base.sys.log.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.log.mapper.SysLogOperationMapper;
import com.eric.base.sys.log.model.SysLogOperation;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 通用日志数据访问对象
 *
 * @author Eric
 * @date 2026/8/8
 * @description 与 SysLogOperationMapper 一对一对接，不承载业务逻辑。
 *              流水表只增不改：对外仅提供写入与查询，不暴露更新/删除。
 */
@Repository
public class SysLogOperationDao extends BaseDao<SysLogOperation, SysLogOperationMapper> {

    public SysLogOperationDao(SysLogOperationMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 批量插入（日志批量异步落库使用）
     */
    public int insertBatch(List<SysLogOperation> list) {
        if (null == list || list.isEmpty()) {
            return 0;
        }
        return getBaseMapper().insertBatch(list);
    }

    /**
     * 分页查询日志，sortOrder / operateTimeStart / operateTimeEnd 由 Service 层归一化/校验后传入
     */
    public List<SysLogOperation> selectLogList(SysLogOperation criteria, Pages pages, String sortOrder,
                                               String operateTimeStart, String operateTimeEnd) {
        return getBaseMapper().selectLogList(criteria, pages, sortOrder, operateTimeStart, operateTimeEnd);
    }

    /**
     * 统计日志总数（与 selectLogList 条件一致，供分页 metadata.total 使用）
     */
    public long selectLogCount(SysLogOperation criteria, String operateTimeStart, String operateTimeEnd) {
        return getBaseMapper().selectLogCount(criteria, operateTimeStart, operateTimeEnd);
    }
}
