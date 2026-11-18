package com.eric.base.sys.formula.request;

import com.eric.base.common.base.model.BaseRequest;
import com.eric.base.sys.formula.model.SysFormula;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 命名公式列表查询请求
 *
 * @author Eric
 * @date 2026/11/18
 * @description 公式定义器 /list 接口请求（F14-BE-006，DOC-002 §4.11.4 契约）：
 *              覆写父类泛型 getter/setter，使 Jackson 在反序列化时能正确识别 body 的具体类型；
 *              body 承载 {@link SysFormula} 查询条件（fieldCode / fieldName 模糊匹配，
 *              fieldType / fieldEnable 精确匹配，均为空表示全量），pages 承载分页参数；
 *              排序固定创建时间降序，无排序字段（对齐 SysDatasourceListRequest 先例）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "命名公式列表请求：body 承载查询条件，pages 承载分页参数")
public class SysFormulaQuery extends BaseRequest<SysFormula> {

    @Override
    public SysFormula getBody() {
        return (SysFormula) super.getBody();
    }

    @Override
    public void setBody(SysFormula body) {
        super.setBody(body);
    }
}
