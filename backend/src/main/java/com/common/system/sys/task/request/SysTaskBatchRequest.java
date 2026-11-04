package com.common.system.sys.task.request;

import lombok.Data;

import java.util.List;

/**
 * 批量操作请求对象
 *
 * @author Eric
 * @date 2026/8/15
 * @description 批量停用/启用：请求体 { ids: string[] }
 */
@Data
public class SysTaskBatchRequest {

    /** 任务id列表 */
    private List<String> ids;
}
