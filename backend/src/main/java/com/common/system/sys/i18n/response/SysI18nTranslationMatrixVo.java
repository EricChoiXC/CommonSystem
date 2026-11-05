package com.common.system.sys.i18n.response;

import com.common.system.sys.i18n.model.SysI18nKey;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 多语言-翻译矩阵视图对象
 *
 * @author Eric
 * @date 2026/11/5
 * @description 翻译维护矩阵响应：keys 为该模块全部资源项（field_key 升序，行序），
 *              languages 为启用语言列表（field_order 升序，列序，携带语言码与名称），
 *              matrix 以「field_key → (语言码 → 翻译文本)」双层 Map 返回；无翻译的语言格
 *              在 Map 中缺省，前端按空串处理。total 为资源行数（metadata.total）。
 */
@Data
@Schema(description = "翻译矩阵：keys 为资源行，languages 为语言列，matrix[key][language] 为翻译文本")
public class SysI18nTranslationMatrixVo {

    /** 所属模块（相对 i18n/ 的资源子目录层级名） */
    @Schema(description = "所属模块")
    private String fieldModel;

    /** 资源行列表（field_key 升序） */
    @Schema(description = "资源行列表（field_key 升序）")
    private List<SysI18nKey> keys;

    /** 语言列列表（启用语言，field_order 升序） */
    @Schema(description = "语言列列表（启用语言，field_order 升序）")
    private List<LanguageColumn> languages;

    /** 矩阵数据：matrix.get(field_key).get(field_language) = 翻译文本；无翻译的格缺省 */
    @Schema(description = "矩阵数据：key → (语言码 → 翻译文本)")
    private Map<String, Map<String, String>> matrix;

    /**
     * 语言列
     */
    @Data
    @Schema(description = "矩阵语言列")
    public static class LanguageColumn {

        /** 语言码 */
        @Schema(description = "语言码")
        private String fieldCode;

        /** 语言名称 */
        @Schema(description = "语言名称")
        private String fieldName;
    }
}
