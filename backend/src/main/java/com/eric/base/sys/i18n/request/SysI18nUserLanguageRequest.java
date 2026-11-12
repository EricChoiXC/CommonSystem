package com.eric.base.sys.i18n.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 多语言-用户语言更新请求对象
 *
 * @author Eric
 * @date 2026/11/5
 * @description 当前登录用户语言更新请求体：仅 fieldLanguage（语言码）；
 *              对当前登录用户 upsert 用户语言表，无效语言码由服务忽略并回退默认语言（A4）。
 */
@Data
@Schema(description = "用户语言更新请求体：仅 fieldLanguage")
public class SysI18nUserLanguageRequest {

    /** 语言码（sys_i18n_language.field_code，如 zh_CN、en_US） */
    @Schema(description = "语言码（sys_i18n_language.field_code），无效语言码忽略并回退默认语言")
    private String fieldLanguage;
}
