package com.eric.base.sys.datasource.request;

import com.eric.base.common.base.model.BaseRequest;
import com.eric.base.sys.datasource.model.SysDatasource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据源管理列表请求
 *
 * @author Eric
 * @date 2026/11/13
 * @description 覆写父类泛型 getter/setter，使 Jackson 在反序列化时能正确识别 body 的具体类型；
 *              body 承载 {@link SysDatasource} 查询条件（fieldName 名称模糊、fieldCode 编码模糊、
 *              fieldTarget 能力精确、fieldEnable 启停精确），pages 承载分页参数；
 *              排序固定创建时间降序，无排序字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "数据源管理列表请求：body 承载查询条件，pages 承载分页参数")
public class SysDatasourceListRequest extends BaseRequest<SysDatasource> {

    @Override
    public SysDatasource getBody() {
        return (SysDatasource) super.getBody();
    }

    @Override
    public void setBody(SysDatasource body) {
        super.setBody(body);
    }
}
