package com.eric.base.sys.number.model;

import lombok.Data;

/**
 * 取号结果文档对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 对应 generate/acquire 接口响应契约（方案 §3.2）：
 *              { code, recordDetailId, reused }，reused=true 表示幂等命中已有编号
 *              （按 field_scope_key + field_model_id 查到未作废明细，未推进计数器）。
 *              同进程业务后端直接注入 SysNumberGenerateService 时亦消费本对象。
 */
@Data
public class SysNumberAcquireResultDo {

    /** 发出的完整编号（幂等命中时为原编号） */
    private String code;

    /** 取号明细 ID（sys_number_record_detail.field_id；幂等命中时为原明细 ID） */
    private String recordDetailId;

    /** 是否幂等命中已有编号（true=复用原编号，未推进计数器） */
    private boolean reused;
}
