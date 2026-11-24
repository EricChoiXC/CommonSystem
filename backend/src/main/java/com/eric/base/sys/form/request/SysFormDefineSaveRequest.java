package com.eric.base.sys.form.request;

import com.eric.base.common.base.model.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单定义保存请求（POST /define/save 请求包装）
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 API 分册 §3.3 POST /define/save 请求契约（F15-BE-006）：请求体
 *              { body: SysFormSaveRequest }——body 承载保存体 { fieldId, fieldFormCode,
 *              fieldFormName, schema? }（载荷字段语义见
 *              {@link SysFormSaveRequest}，分册 §3.3 请求表），pages 不使用；
 *              { body } 包装为仓库 save 通道统一先例
 *              （SysFormulaSaveRequest / SysCalendarSaveRequest / SysDatasourceSaveRequest），
 *              前端 api/form.js saveForm 同口径（F15-FE-001）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "表单定义保存请求：body 承载保存体 { fieldId, fieldFormCode, "
        + "fieldFormName, schema? }，pages 不使用")
public class SysFormDefineSaveRequest extends BaseRequest<SysFormSaveRequest> {

    @Override
    public SysFormSaveRequest getBody() {
        return super.getBody();
    }

    @Override
    public void setBody(SysFormSaveRequest body) {
        super.setBody(body);
    }
}
