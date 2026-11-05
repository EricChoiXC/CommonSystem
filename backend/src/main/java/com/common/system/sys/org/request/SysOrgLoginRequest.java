package com.common.system.sys.org.request;

import com.common.system.common.base.model.BaseRequest;
import com.common.system.sys.org.model.SysOrgUser;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 登录请求对象
 *
 * @author Eric
 * @date 2026/8/7
 * @description 登录接口专用请求体：body 携带 field_login_name 与 field_password，
 *              其余用户字段忽略。覆写父类泛型 getter/setter 供 Jackson 正确反序列化。
 *              fieldLanguage（D3 登录语言登记契约）：可选，登录页选择的语言码；
 *              登录成功后后端 upsert sys_i18n_user_language（存在更新、不存在插入），
 *              未携带时行为与既有登录完全一致（零回归）；无效语言码由 i18n 服务
 *              忽略并回退默认语言（A4），不影响登录结果。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "登录请求体：body 携带 fieldLoginName / fieldPassword / fieldLanguage")
public class SysOrgLoginRequest extends BaseRequest<SysOrgUser> {

    /**
     * 登录语言码（D3 契约，可选）：登录成功后登记为用户语言；未携带不登记（既有行为不变）
     */
    @Schema(description = "登录语言码（可选，如 zh_CN、en_US）；登录成功后登记为用户语言，"
            + "未携带时行为不变，无效语言码忽略并回退默认语言")
    private String fieldLanguage;

    @Override
    public SysOrgUser getBody() {
        return (SysOrgUser) super.getBody();
    }

    @Override
    public void setBody(SysOrgUser body) {
        super.setBody(body);
    }
}
