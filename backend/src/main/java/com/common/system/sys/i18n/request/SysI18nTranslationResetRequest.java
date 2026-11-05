package com.common.system.sys.i18n.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 多语言-翻译重置请求对象
 *
 * @author Eric
 * @date 2026/11/5
 * @description 单条重置请求体：按 (fieldKey, fieldLanguage) 定位翻译记录，
 *              清人工编辑标记并回写资源文件值；该语言无对应资源文件（或文件中无此 key）
 *              时由服务删除该翻译记录。
 */
@Data
@Schema(description = "翻译重置请求体：fieldKey + fieldLanguage 定位")
public class SysI18nTranslationResetRequest {

    /** 关键字（sys_i18n_key.field_key） */
    @Schema(description = "关键字（sys_i18n_key.field_key）")
    private String fieldKey;

    /** 语言码（sys_i18n_language.field_code） */
    @Schema(description = "语言码（sys_i18n_language.field_code）")
    private String fieldLanguage;
}
