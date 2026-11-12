package com.eric.base.common.module.request;

import com.eric.base.common.base.model.BaseRequest;
import com.eric.base.common.module.model.CommonModule;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 模块组列表请求对象
 *
 * @author Eric
 * @date 2026/11/12
 * @description 覆写父类泛型 getter/setter，使 Jackson 在反序列化时能正确识别 body 的具体类型；
 *              body 为查询条件预留位（首批无业务筛选字段），pages 遵循 list 通道分页结构，
 *              模块组量极小、默认全量返回（按 field_key 排序），pages 当前不参与查询。
 */
@Schema(description = "模块组列表请求体：{ body: {}, pages: { pageNo, pageSize, needPage } }；"
        + "body 为查询条件预留位（首批无筛选字段），模块组量极小默认全量返回")
public class CommonModuleRequest extends BaseRequest<CommonModule> {

    @Override
    public CommonModule getBody() {
        return (CommonModule) super.getBody();
    }

    @Override
    public void setBody(CommonModule body) {
        super.setBody(body);
    }
}
