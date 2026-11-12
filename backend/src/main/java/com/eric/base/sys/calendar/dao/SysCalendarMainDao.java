package com.eric.base.sys.calendar.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.calendar.mapper.SysCalendarMainMapper;
import com.eric.base.sys.calendar.model.SysCalendarMain;
import com.eric.base.sys.calendar.request.SysCalendarQuery;
import com.eric.base.sys.calendar.response.SysCalendarVo;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * 日程主表 DAO
 *
 * @author Eric
 * @date 2026/8/21
 */
@Repository
public class SysCalendarMainDao extends BaseDao<SysCalendarMain, SysCalendarMainMapper> {

    public SysCalendarMainDao(SysCalendarMainMapper baseMapper) {
        super(baseMapper);
    }

    public List<SysCalendarVo> selectCalendarList(SysCalendarQuery query, String userId,
                                                  Pages pages, String sortField, String sortOrder) {
        return getBaseMapper().selectCalendarList(query, userId, pages, sortField, sortOrder);
    }

    public long selectCalendarCount(SysCalendarQuery query, String userId) {
        return getBaseMapper().selectCalendarCount(query, userId);
    }

    public List<SysCalendarVo> selectMonthList(SysCalendarQuery query, String userId) {
        return getBaseMapper().selectMonthList(query, userId);
    }

    public SysCalendarVo selectCalendarDetail(String fieldId) {
        return getBaseMapper().selectCalendarDetail(fieldId);
    }

    public List<SysCalendarMain> selectByEndTimeBefore(Date deadline) {
        return getBaseMapper().selectByEndTimeBefore(deadline);
    }
}