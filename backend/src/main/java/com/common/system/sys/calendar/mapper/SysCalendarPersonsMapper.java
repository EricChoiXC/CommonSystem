package com.common.system.sys.calendar.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.sys.calendar.model.SysCalendarPersons;
import com.common.system.sys.calendar.response.SysCalendarPersonVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 日程人员表 Mapper
 *
 * @author Eric
 * @date 2026/8/21
 */
@Mapper
public interface SysCalendarPersonsMapper extends IBaseMapper<SysCalendarPersons> {

    /**
     * 按日程id查询接收人列表
     */
    List<SysCalendarPersonVo> selectByCalendarId(@Param("calendarId") String calendarId);

    /**
     * 按日程id删除接收人
     */
    int deleteByCalendarId(@Param("calendarId") String calendarId);

    /**
     * 批量插入接收人
     */
    int batchInsert(@Param("list") List<SysCalendarPersons> list);

    /**
     * 更新接收状态
     */
    int updateAccept(@Param("fieldId") String fieldId,
                     @Param("personId") String personId,
                     @Param("accept") Boolean accept);

    /**
     * 查询用户在某日程的接收记录
     */
    SysCalendarPersons selectByCalendarAndPerson(@Param("calendarId") String calendarId,
                                                  @Param("personId") String personId);
}