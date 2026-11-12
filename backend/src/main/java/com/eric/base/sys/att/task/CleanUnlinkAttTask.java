package com.eric.base.sys.att.task;

import com.eric.base.sys.task.annotation.SysTask;
import com.eric.base.sys.task.annotation.SysTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Queue;

/**
 * @author Eric
 * @date 2026/8/11
 * @description
 */
@Component
@SysTask(name = "清理未关联实际文档定时任务", cron = "0 0 0 * * ?", params = {"UNLINK_TIME=3600"})
public class CleanUnlinkAttTask implements SysTaskExecutor {

    @Override
    public void execute(Map<String, String> params, Queue<String> logs) {
        logs.add("测试用定时任务");
        for (String key : params.keySet()) {
            logs.add(key + "=" + params.get(key));
        }
        logs.add("定时任务结束");
    }

}
