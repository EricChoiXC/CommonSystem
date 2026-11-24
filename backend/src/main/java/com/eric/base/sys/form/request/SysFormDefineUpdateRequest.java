package com.eric.base.sys.form.request;

import com.eric.base.common.base.model.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单定义更新请求（PATCH /define/update 请求包装）
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 API 分册 §3.5 PATCH /define/update 请求契约（F15-BE-006）：请求体
 *              { body: SysFormUpdateRequest }——body 承载更新体 { fieldId, fieldFormName?,
 *              schema? }（载荷字段语义见 {@link SysFormUpdateRequest}，分册 §3.5 请求表；
 *              fieldFormName 与 schema 至少携带一项），{ body } 包装口径与
 *              {@link SysFormDefineSaveRequest} 同源（先例 formula / calendar，前端
 *              api/form.js updateForm 同口径）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "表单定义更新请求：body 承载更新体 { fieldId, fieldFormName?, schema? }"
        + "（至少携带一项），pages 不使用")
public class SysFormDefineUpdateRequest extends BaseRequest<SysFormUpdateRequest> {

    @Override
    public SysFormUpdateRequest getBody() {
        return super.getBody();
    }

    @Override
    public void setBody(SysFormUpdateRequest body) {
        super.setBody(body);
    }
}
