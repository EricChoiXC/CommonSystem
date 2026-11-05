package com.common.system.sys.i18n.request;

import com.common.system.common.base.model.BaseRequest;
import com.common.system.sys.i18n.model.SysI18nLanguage;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 多语言-语言请求对象
 *
 * @author Eric
 * @date 2026/11/5
 * @description 语言管理页列表查询与保存请求体：body 携带语言条件/实体（fieldName 模糊、
 *              fieldCode 模糊、fieldEnable 精确；保存时 fieldCode/fieldName/fieldEnable/
 *              fieldOrder），覆写父类泛型 getter/setter 供 Jackson 正确反序列化。
 *              field_default 不接受请求写入——默认语言仅经 setDefault 互斥切换。
 */
@Schema(description = "语言管理请求体：body / params / pages")
public class SysI18nLanguageRequest extends BaseRequest<SysI18nLanguage> {

    @Override
    @Schema(description = "语言条件/实体：fieldName（模糊）、fieldCode（模糊）、fieldEnable、fieldOrder；"
            + "field_default 不接受请求写入（仅经 setDefault 切换）")
    public SysI18nLanguage getBody() {
        return super.getBody();
    }

    @Override
    public void setBody(SysI18nLanguage body) {
        super.setBody(body);
    }
}
