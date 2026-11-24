package com.eric.base.sys.form.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 保存表单数据响应
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 API 分册 §4.3 POST /data/save 响应契约（F15-BE-003）：
 *              data = { fieldId, formData }——formData 为服务端最终落库形态（公式重算值
 *              回填、隐藏字段裁剪后 JSON），宿主以此刷新展示（功能设计 §6.3「保存响应携带
 *              的最终数据」）。一期保存校验链 S3（显隐复算裁剪）/ S6（公式重算）随
 *              F15-BE-004 / F15-BE-005 并联后，formData 即裁剪与重算后的最终形态；本任务
 *              为归一化载荷（Schema 字段序、明细行结构归一）。
 */
@Data
@Schema(description = "保存表单数据响应：formData 为服务端最终落库形态，宿主以此刷新展示")
public class SysFormDataSavedVo {

    /** 数据行 ID（沿用请求预生成值） */
    @Schema(description = "数据行 ID（沿用请求 fieldId）")
    private String fieldId;

    /** 服务端最终落库形态字段值对象（公式重算值回填、隐藏字段裁剪后 JSON） */
    @Schema(description = "服务端最终落库形态字段值对象 { 字段码: 值 }（公式重算值回填、"
            + "隐藏字段裁剪后 JSON；一期为归一化载荷，裁剪与重算随 F15-BE-004/005 并联）")
    private Map<String, Object> formData;
}
