package com.common.system.sys.i18n.request;

import com.common.system.common.base.model.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 多语言-翻译批量更新请求对象
 *
 * @author Eric
 * @date 2026/11/5
 * @description 翻译维护矩阵批量提交请求体：仅提交本次改动项（前端 diff 后生成），
 *              每条明细按翻译记录主键 fieldId 定位、写 fieldMessage 并置人工编辑标记
 *              （field_update_time = now），完成后刷新缓存。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "翻译批量更新请求体：details 为本次改动项明细列表")
public class SysI18nTranslationUpdateRequest {

    /** 改动明细列表（仅提交本次 diff 出的改动项；空列表视为无改动） */
    @Schema(description = "改动明细列表（仅提交本次 diff 出的改动项）")
    private List<Detail> details;

    /**
     * 批量更新明细
     */
    @Data
    @Schema(description = "翻译批量更新明细：fieldId 定位翻译记录，fieldMessage 为新文本")
    public static class Detail {

        /** 翻译记录 ID（sys_i18n_translation.field_id） */
        @Schema(description = "翻译记录 ID")
        private String fieldId;

        /** 新翻译文本 */
        @Schema(description = "新翻译文本")
        private String fieldMessage;
    }
}
