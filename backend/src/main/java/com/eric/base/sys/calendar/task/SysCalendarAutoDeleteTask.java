package com.eric.base.sys.calendar.task;

import com.eric.base.common.setting.service.CommonSettingService;
import com.eric.base.sys.calendar.dao.SysCalendarMainDao;
import com.eric.base.sys.calendar.dao.SysCalendarPersonsDao;
import com.eric.base.sys.calendar.model.SysCalendarMain;
import com.eric.base.sys.calendar.response.SysCalendarPersonVo;
import com.eric.base.sys.notify.service.SysNotifyTodoService;
import com.eric.base.sys.task.annotation.SysTask;
import com.eric.base.sys.task.annotation.SysTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * 日程自动清理定时任务
 *
 * @author Eric
 * @date 2026/8/21
 * @description 每日执行定时任务，检查配置项 AUTO_DELETE：
 *              如果配置不为0，则按照对应天数，删除结束时间超过当前时间对应天数的日程；
 *              同时日程相关待阅转已阅。
 */
@Component
@SysTask(name = "日程自动清理", cron = "0 0 2 * * ?", params = {})
public class SysCalendarAutoDeleteTask implements SysTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(SysCalendarAutoDeleteTask.class);

    private static final String SETTING_TYPE = "calendar";
    private static final String SETTING_KEY = "AUTO_DELETE";

    private static final String MODEL_NAME = "SYS-CALENDAR";

    private final CommonSettingService commonSettingService;
    private final SysCalendarMainDao calendarMainDao;
    private final SysCalendarPersonsDao personsDao;
    private final SysNotifyTodoService notifyTodoService;

    public SysCalendarAutoDeleteTask(CommonSettingService commonSettingService,
                                     SysCalendarMainDao calendarMainDao,
                                     SysCalendarPersonsDao personsDao,
                                     SysNotifyTodoService notifyTodoService) {
        this.commonSettingService = commonSettingService;
        this.calendarMainDao = calendarMainDao;
        this.personsDao = personsDao;
        this.notifyTodoService = notifyTodoService;
    }

    @Override
    public void execute(Map<String, String> params, Queue<String> logs) {
        // 读取配置项
        Integer autoDeleteDays = commonSettingService.getIntValue(SETTING_TYPE, SETTING_KEY);
        if (null == autoDeleteDays || autoDeleteDays <= 0) {
            logs.add("AUTO_DELETE 配置为 " + autoDeleteDays + "，不执行自动清理");
            log.info("calendar auto delete: AUTO_DELETE={}, skip", autoDeleteDays);
            return;
        }

        // 计算截止时间
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -autoDeleteDays);
        Date deadline = cal.getTime();

        // 查询所有结束时间早于截止时间的日程
        List<SysCalendarMain> expiredList = calendarMainDao.selectByEndTimeBefore(deadline);

        int deleteCount = 0;
        for (SysCalendarMain main : expiredList) {
            // 先处理待阅转已办
            List<SysCalendarPersonVo> persons = personsDao.selectByCalendarId(main.getFieldId());
            for (SysCalendarPersonVo person : persons) {
                notifyTodoService.autoDoneByModel(MODEL_NAME, main.getFieldId(), person.getFieldPersonId());
            }
            // 删除接收人
            personsDao.deleteByCalendarId(main.getFieldId());
            // 删除主表
            calendarMainDao.deleteById(main.getFieldId());
            deleteCount++;
        }

        logs.add("自动清理完成：共删除 " + deleteCount + " 条过期日程（截止时间 " + deadline + "）");
        log.info("calendar auto delete: completed, deleted {} expired calendars (deadline: {})", deleteCount, deadline);
    }
}