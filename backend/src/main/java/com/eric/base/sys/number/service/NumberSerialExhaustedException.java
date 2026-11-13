package com.eric.base.sys.number.service;

import com.eric.base.sys.number.model.SysNumberRuleConfigDo;

/**
 * 流水号用尽异常
 *
 * @author Eric
 * @date 2026/11/13
 * @description seq 超出 length 容量（如 length=3 且取号流水 > 999，方案 §2.3 C6 /
 *              §5.3）时抛出，承载 CRM_NUMBER_SERIAL_EXHAUSTED 错误语义。
 *              取号失败不截断续发（避免超出 C5 的 64 位估算），业务保存/提交随取号一并失败；
 *              异常沿 @Transactional 向上抛出时计数器原子推进一并回滚，
 *              计数器不永久越过容量。错误码常量随 NB3-BE-001 落 SysNumberConstants，
 *              本任务以异常类型占位（对齐 NumberRuleInvalidException 先例）；
 *              继承 IllegalArgumentException，命中 GlobalExceptionHandler 既有
 *              BAD_REQUEST 分支，前端可读 message。
 */
public class NumberSerialExhaustedException extends IllegalArgumentException {

    /** 错误码：流水超出 length 容量（C6） */
    public static final String CODE = "CRM_NUMBER_SERIAL_EXHAUSTED";

    public NumberSerialExhaustedException(String message) {
        super(CODE + ": " + message);
    }

    public String getCode() {
        return CODE;
    }

    /**
     * 构造携带规则项上下文的异常信息（流水项 id / 长度 / 容量 / 本次取号值）
     */
    public static NumberSerialExhaustedException of(SysNumberRuleConfigDo.Item seqItem, int serialNo, int capacity) {
        return new NumberSerialExhaustedException("流水号已用尽：流水项 " + seqItem.getId()
                + " 长度 " + seqItem.getLength() + " 容量 " + capacity
                + "，本次取号 " + serialNo + " 超出容量，业务保存已随取号失败（不截断续发）");
    }
}
