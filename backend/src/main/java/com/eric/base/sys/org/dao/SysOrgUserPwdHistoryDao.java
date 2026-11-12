package com.eric.base.sys.org.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.sys.org.mapper.SysOrgUserPwdHistoryMapper;
import com.eric.base.sys.org.model.SysOrgUserPwdHistory;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 密码历史数据访问对象
 *
 * @author Eric
 * @date 2026/8/25
 * @description 与 SysOrgUserPwdHistoryMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysOrgUserPwdHistoryDao extends BaseDao<SysOrgUserPwdHistory, SysOrgUserPwdHistoryMapper> {

    public SysOrgUserPwdHistoryDao(SysOrgUserPwdHistoryMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 按用户 ID 查询密码历史（按创建时间降序）
     */
    public List<SysOrgUserPwdHistory> selectByUserId(String userId) {
        return getBaseMapper().selectByUserId(userId);
    }

    /**
     * 删除指定用户超出保留条数的历史记录
     */
    public int deleteExcess(String userId, int keepCount) {
        return getBaseMapper().deleteExcess(userId, keepCount);
    }
}