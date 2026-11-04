package com.common.system.sys.notify.request;

import lombok.Data;

import java.util.List;

/**
 * 批量操作请求对象
 *
 * @author Eric
 * @date 2026/8/12
 * @description 批量已办 / 批量转未办 / 批量星标 / 批量删除共用，
 *              ids 为待办接收记录（sys_notify_todo_target.field_id）列表。
 */
@Data
public class SysNotifyBatchRequest {

    /** 接收记录 id 列表 */
    private List<String> ids;
}
