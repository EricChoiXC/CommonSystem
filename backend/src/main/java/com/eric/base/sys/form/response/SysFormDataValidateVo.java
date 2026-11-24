package com.eric.base.sys.form.response;

import com.eric.base.sys.form.model.SysFormDataErrorDo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 独立校验表单数据响应
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 API 分册 §4.4 POST /data/validate 响应契约（F15-BE-003）：
 *              data = { pass, errors, formData }。校验结果为正常业务结果、非异常（HTTP 200、
 *              code 为成功，对齐公式 validate / simulate 先例）：
 *              <ul>
 *              <li>pass：S2/S4 字段级校验是否全部通过；</li>
 *              <li>errors：字段级错误明细 { fieldCode, rowIndex, ruleId, message }，
 *                  pass = true 时为空数组；</li>
 *              <li>formData：pass = true 时回带服务端重算 / 裁剪后的最终数据预览（同 save
 *                  落库形态）；pass = false 时为 null。</li>
 *              </ul>
 *              承载边界：S0 / S1 资源与确定性拒绝仍以 CS_FORM_* 错误码异常返回（与 save
 *              同语义），不以 errors 承载。
 */
@Data
@Schema(description = "独立校验响应：{ pass, errors, formData }——校验结果为正常业务结果"
        + "（HTTP 200、code 为成功）；S0/S1 资源与确定性拒绝仍以 CS_FORM_* 异常返回")
public class SysFormDataValidateVo {

    /** S2/S4 字段级校验是否全部通过 */
    @Schema(description = "S2/S4 字段级校验是否全部通过")
    private boolean pass;

    /** 字段级错误明细（pass = true 时为空数组） */
    @Schema(description = "字段级错误明细 { fieldCode, rowIndex, ruleId, message }；"
            + "pass = true 时为空数组")
    private List<SysFormDataErrorDo> errors;

    /** pass = true 时回带服务端最终数据预览（同 save 落库形态）；pass = false 时为 null */
    @Schema(description = "pass = true 时回带服务端重算/裁剪后的最终数据预览；pass = false 时为 null")
    private Map<String, Object> formData;
}
