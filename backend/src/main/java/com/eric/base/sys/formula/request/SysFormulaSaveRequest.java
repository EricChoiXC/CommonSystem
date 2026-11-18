package com.eric.base.sys.formula.request;

import com.eric.base.common.base.model.BaseRequest;
import com.eric.base.sys.formula.model.SysFormula;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 命名公式保存请求
 *
 * @author Eric
 * @date 2026/11/18
 * @description 公式定义器 /save 与 /update 共用请求（F14-BE-006，DOC-002 §4.11.4 契约）：
 *              覆写父类泛型 getter/setter，使 Jackson 在反序列化时能正确识别 body 的具体类型；
 *              body 承载保存体 {@link SysFormula}——载荷为 { fieldId, fieldCode, fieldName,
 *              fieldType, fieldExpression, fieldReturnType, fieldRemark }（对齐
 *              SysDatasourceSaveRequest 先例）。新建 fieldId 由前端经 GET /backend/common/initID
 *              预生成、编辑必填；fieldDependencies 不受理——保存期由 Service 解析
 *              fieldExpression 提取变量码后序列化落库，请求体传入值一律忽略；
 *              fieldEnable 编辑不受理（启停走 /enable 通道，新建默认启用）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "命名公式保存请求（save / update 共用）：body 承载保存体，pages 不使用")
public class SysFormulaSaveRequest extends BaseRequest<SysFormula> {

    @Override
    public SysFormula getBody() {
        return (SysFormula) super.getBody();
    }

    @Override
    public void setBody(SysFormula body) {
        super.setBody(body);
    }
}
