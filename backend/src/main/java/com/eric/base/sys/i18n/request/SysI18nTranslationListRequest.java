package com.eric.base.sys.i18n.request;

import com.eric.base.common.base.model.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 多语言-翻译矩阵查询请求对象
 *
 * @author Eric
 * @date 2026/11/5
 * @description 翻译维护矩阵列表请求体：body.fieldModel 指定模块（相对 i18n/ 的
 *              子目录层级名，来自模块聚合下拉），行 = field_key 升序、列 = 启用语言
 *              按 field_order 升序。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "翻译矩阵查询请求体：顶层 fieldModel 指定模块（与 body / pages 平级）")
public class SysI18nTranslationListRequest extends BaseRequest<Object> {

    /** 所属模块（相对 i18n/ 的资源子目录层级名，如 org、process/template） */
    @Schema(description = "所属模块（相对 i18n/ 的资源子目录层级名，如 org、process/template）")
    private String fieldModel;
}
