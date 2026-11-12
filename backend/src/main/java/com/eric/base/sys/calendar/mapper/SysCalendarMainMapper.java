package com.eric.base.sys.calendar.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.calendar.model.SysCalendarMain;
import com.eric.base.sys.calendar.request.SysCalendarQuery;
import com.eric.base.sys.calendar.response.SysCalendarPersonVo;
import com.eric.base.sys.calendar.response.SysCalendarVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 日程主表 Mapper
 *
 * @author Eric
 * @date 2026/8/21
 */
@Mapper
public interface SysCalendarMainMapper extends IBaseMapper<SysCalendarMain> {

    /**
     * 查询日程列表（含创建人/接收人过滤）
     */
    List<SysCalendarVo> selectCalendarList(@Param("model") SysCalendarQuery query,
                                           @Param("userId") String userId,
                                           @Param("pages") com.eric.base.common.base.model.Pages pages,
                                           @Param("sortField") String sortField,
                                           @Param("sortOrder") String sortOrder);

    /**
     * 查询日程列表总数
     */
    long selectCalendarCount(@Param("model") SysCalendarQuery query,
                             @Param("userId") String userId);

    /**
     * 查询当月日程（日历视图）
     */
    List<SysCalendarVo> selectMonthList(@Param("model") SysCalendarQuery query,
                                        @Param("userId") String userId);

    /**
     * 查询日程详情
     */
    SysCalendarVo selectCalendarDetail(@Param("fieldId") String fieldId);

    /**
     * 查询结束时间早于指定时间的日程（供自动清理任务使用）
     */
    List<SysCalendarMain> selectByEndTimeBefore(@Param("deadline") Date deadline);
}