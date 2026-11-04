package com.common.system.sys.calendar.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.sys.calendar.mapper.SysCalendarPersonsMapper;
import com.common.system.sys.calendar.model.SysCalendarPersons;
import com.common.system.sys.calendar.response.SysCalendarPersonVo;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 日程人员表 DAO
 *
 * @author Eric
 * @date 2026/8/21
 */
@Repository
public class SysCalendarPersonsDao extends BaseDao<SysCalendarPersons, SysCalendarPersonsMapper> {

    public SysCalendarPersonsDao(SysCalendarPersonsMapper baseMapper) {
        super(baseMapper);
    }

    public List<SysCalendarPersonVo> selectByCalendarId(String calendarId) {
        return getBaseMapper().selectByCalendarId(calendarId);
    }

    public int deleteByCalendarId(String calendarId) {
        return getBaseMapper().deleteByCalendarId(calendarId);
    }

    public int batchInsert(List<SysCalendarPersons> list) {
        return getBaseMapper().batchInsert(list);
    }

    public int updateAccept(String fieldId, String personId, Boolean accept) {
        return getBaseMapper().updateAccept(fieldId, personId, accept);
    }

    public SysCalendarPersons selectByCalendarAndPerson(String calendarId, String personId) {
        return getBaseMapper().selectByCalendarAndPerson(calendarId, personId);
    }
}