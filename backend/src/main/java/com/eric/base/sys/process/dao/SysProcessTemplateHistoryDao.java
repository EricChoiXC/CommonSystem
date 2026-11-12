package com.eric.base.sys.process.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.sys.process.mapper.SysProcessTemplateHistoryMapper;
import com.eric.base.sys.process.model.SysProcessTemplateHistory;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 流程模板历史版本数据访问对象
 *
 * @author Eric
 * @date 2026/8/22
 * @description 与 SysProcessTemplateHistoryMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysProcessTemplateHistoryDao extends BaseDao<SysProcessTemplateHistory, SysProcessTemplateHistoryMapper> {

    public SysProcessTemplateHistoryDao(SysProcessTemplateHistoryMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 按模板查询历史版本（按版本号升序）
     */
    public List<SysProcessTemplateHistory> selectListByTemplateId(String templateId) {
        return getBaseMapper().selectListByTemplateId(templateId);
    }
}
