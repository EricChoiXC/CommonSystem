package com.eric.base.sys.form.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 表单数据校验错误项文档对象
 *
 * @author Eric
 * @date 2026/11/24
 * @description 表单数据基础 / 动态校验的单条字段级错误描述（功能设计 §6.1 第⑤步定稿：
 *              errors 数组含 fieldCode / 规则 ID / 错误文案），元素 { fieldCode, rowIndex,
 *              ruleId, message }：
 *              <ul>
 *              <li>fieldCode：字段码；明细行内字段错误为行内字段码；</li>
 *              <li>rowIndex：仅明细行内错误携带（0 起始行号），主表字段错误为 null；</li>
 *              <li>ruleId：触发错误的规则 ID；基础校验（必填 / 类型 / 长度 / 枚举 /
 *                  行数）为 null——动态校验错误由 F15-BE-004 FormValidationEngine
 *                  并联产出时回填；</li>
 *              <li>message：中文错误文案（附字段 label 与违规说明）。</li>
 *              </ul>
 *              消费方：/data/validate 的 data.errors 结构化承载（API 分册 §4.4，字段级
 *              结果为正常业务结果不抛异常）；/data/save 校验失败以首条错误中文摘要随
 *              CS_FORM_DATA_INVALID message 前缀抛出（分册 §7.1 第 12 项）。
 */
@Data
@Schema(description = "表单数据校验错误项：{ fieldCode, rowIndex, ruleId, message }，"
        + "基础校验 ruleId 为 null（动态校验错误由规则 ID 触发）")
public class SysFormDataErrorDo {

    /** 字段码；明细行内字段错误为行内字段码 */
    @Schema(description = "字段码；明细行内字段错误为行内字段码")
    private String fieldCode;

    /** 明细行行号（0 起始）；仅明细行内错误携带，主表字段错误为 null */
    @Schema(description = "明细行行号（0 起始）；仅明细行内错误携带，主表字段错误为 null")
    private Integer rowIndex;

    /** 触发错误的规则 ID；基础校验（必填/类型/长度/枚举/行数）为 null */
    @Schema(description = "触发错误的规则 ID；基础校验为 null，动态校验错误由规则触发")
    private String ruleId;

    /** 中文错误文案（附字段 label 与违规说明） */
    @Schema(description = "中文错误文案")
    private String message;

    public SysFormDataErrorDo() {
    }

    public SysFormDataErrorDo(String fieldCode, Integer rowIndex, String ruleId, String message) {
        this.fieldCode = fieldCode;
        this.rowIndex = rowIndex;
        this.ruleId = ruleId;
        this.message = message;
    }
}
