package com.eric.base.sys.datasource.request;

import com.eric.base.common.base.model.BaseRequest;
import com.eric.base.sys.datasource.model.SysDatasource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据源保存请求
 *
 * @author Eric
 * @date 2026/11/13
 * @description 覆写父类泛型 getter/setter，使 Jackson 在反序列化时能正确识别 body 的具体类型；
 *              body 承载数据源保存体：新建时 fieldId 由前端经 /backend/common/initID 预生成，
 *              编辑时必填；fieldTarget 保存后锁定，编辑时后端以库值为准不受理变更。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "数据源保存请求：body 承载保存体，pages 不使用")
public class SysDatasourceSaveRequest extends BaseRequest<SysDatasource> {

    @Override
    public SysDatasource getBody() {
        return (SysDatasource) super.getBody();
    }

    @Override
    public void setBody(SysDatasource body) {
        super.setBody(body);
    }
}
