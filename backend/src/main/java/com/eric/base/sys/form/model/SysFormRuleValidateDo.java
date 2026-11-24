package com.eric.base.sys.form.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 表单规则动态校验覆盖载荷文档对象
 *
 * @author Eric
 * @date 2026/11/24
 * @description setValidate 动作的 validate 载荷（功能设计 §3.3：min? / max? / pattern? /
 *              message? 四键均可选，至少一项）：覆盖语义为仅覆盖所列项、其余保持字段静态
 *              校验器（承接「条件必填 / 条件范围 / 条则」诉求，与 setRequired 一样在服务端
 *              与静态校验合并为最终校验口径，F15-BE-004）。向后兼容：未知键反序列化忽略、
 *              null 属性不输出。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "setValidate 载荷：{ min?, max?, pattern?, message? }，至少一项，"
        + "覆盖语义 = 仅覆盖所列项（功能设计 §3.3）")
public class SysFormRuleValidateDo {

    /** 条件最小值（数值，覆盖字段静态 min） */
    @Schema(description = "条件最小值（数值）")
    private Number min;

    /** 条件最大值（数值，覆盖字段静态 max） */
    @Schema(description = "条件最大值（数值）")
    private Number max;

    /** 条件正则（覆盖字段静态 pattern） */
    @Schema(description = "条件正则表达式")
    private String pattern;

    /** 校验失败提示文案（不传时用默认文案） */
    @Schema(description = "校验失败提示文案")
    private String message;
}
