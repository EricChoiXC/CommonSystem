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
 *              明细二选一定位（任务清单 I18N4-FE-001 前端矩阵编辑契约）：
 *              <ul>
 *              <li>fieldId：按翻译记录主键定位（矩阵中已有翻译记录的格子）；</li>
 *              <li>fieldKey + fieldLanguage：按 (key, language) 定位，DB 无记录时补建
 *                  （矩阵中尚未生成翻译记录的空格首次编辑）。</li>
 *              </ul>
 *              写入一律置人工编辑标记（field_update_time = now），完成后刷新受影响语言缓存。
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
    @Schema(description = "翻译批量更新明细：fieldId 或 (fieldKey + fieldLanguage) 二选一定位，"
            + "fieldMessage 为新文本；(key, language) 定位时 DB 无记录则补建")
    public static class Detail {

        /** 翻译记录 ID（sys_i18n_translation.field_id；与 fieldKey+fieldLanguage 二选一） */
        @Schema(description = "翻译记录 ID（与 fieldKey + fieldLanguage 二选一）")
        private String fieldId;

        /** 资源 key（sys_i18n_key.field_key；fieldId 为空时必填，按 (key, language) 定位） */
        @Schema(description = "资源 key（fieldId 为空时必填）")
        private String fieldKey;

        /** 语言码（sys_i18n_language.field_code；fieldId 为空时必填） */
        @Schema(description = "语言码（fieldId 为空时必填）")
        private String fieldLanguage;

        /** 新翻译文本 */
        @Schema(description = "新翻译文本")
        private String fieldMessage;
    }
}
