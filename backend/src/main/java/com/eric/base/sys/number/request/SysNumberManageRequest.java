package com.eric.base.sys.number.request;

import com.eric.base.common.base.model.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 取号明细列表请求对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 覆写父类泛型 getter/setter，使 Jackson 在反序列化时能正确识别 body 的具体类型；
 *              body 承载 {@link SysNumberManageQuery} 查询条件，pages 承载分页参数
 *              （取号明细列表排序固定取号时间降序，无排序字段）。
 */
@Schema(description = "取号明细列表请求：body 承载查询条件，pages 承载分页参数")
public class SysNumberManageRequest extends BaseRequest<SysNumberManageQuery> {

    @Override
    public SysNumberManageQuery getBody() {
        return (SysNumberManageQuery) super.getBody();
    }

    @Override
    public void setBody(SysNumberManageQuery body) {
        super.setBody(body);
    }
}
