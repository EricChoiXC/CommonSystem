package com.eric.base.sys.form.request;

import com.eric.base.common.base.model.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单数据列表查询请求
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 API 分册 §4.6 POST /data/list 请求契约（F15-BE-006）：请求体
 *              { body, pages, sortField, sortOrder }——body 承载 {@link SysFormDataQuery}
 *              查询条件（fieldFormId 必填；fieldCreatorId 仅管理员生效，非管理员由 Service
 *              层强制覆盖为会话用户——归属强制过滤优先，API 分册 §4.8），pages 承载分页参数；
 *              排序一期固定创建时间降序（Mapper 固定序，sortField / sortOrder 受理不参与
 *              动态排序，包装先例 SysFormListRequest / SysProcessTemplateRequest）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "表单数据列表请求：body 承载查询条件（fieldFormId 必填，fieldCreatorId "
        + "仅管理员生效），pages 承载分页参数；排序一期固定创建时间降序")
public class SysFormDataListRequest extends BaseRequest<SysFormDataQuery> {

    /** 排序字段（受理不参与动态排序：一期排序固定创建时间降序） */
    @Schema(description = "排序字段：一期排序固定创建时间降序，字段受理不参与动态排序",
            allowableValues = {"createTime"}, defaultValue = "createTime")
    private String sortField;

    /** 排序方向（受理不参与动态排序：一期固定 desc） */
    @Schema(description = "排序方向：一期固定 desc", allowableValues = {"asc", "desc"},
            defaultValue = "desc")
    private String sortOrder;

    @Override
    public SysFormDataQuery getBody() {
        return (SysFormDataQuery) super.getBody();
    }

    @Override
    public void setBody(SysFormDataQuery body) {
        super.setBody(body);
    }
}
