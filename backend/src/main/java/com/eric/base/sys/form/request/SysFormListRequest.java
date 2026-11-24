package com.eric.base.sys.form.request;

import com.eric.base.common.base.model.BaseRequest;
import com.eric.base.sys.form.model.SysForm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单定义列表查询请求
 *
 * @author Eric
 * @date 2026/11/24
 * @description 对应 API 分册 §3.2 POST /define/list 请求契约（F15-BE-006）：请求体
 *              { body, pages, sortField, sortOrder }——body 承载 {@link SysForm} 查询条件
 *              （fieldFormCode / fieldFormName 模糊匹配，fieldStatus / fieldEnable 精确
 *              匹配，均为空表示全量；创建时间区间筛选一期未实现，请求传入忽略不报错），
 *              pages 承载分页参数；排序一期固定创建时间降序（Mapper 固定序，sortField /
 *              sortOrder 受理不参与动态排序，对齐 SysProcessTemplateRequest 的 sort
 *              字段形态与 SysDatasourceListRequest 的列表包装先例）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "表单定义列表请求：body 承载查询条件，pages 承载分页参数；"
        + "排序一期固定创建时间降序")
public class SysFormListRequest extends BaseRequest<SysForm> {

    /** 排序字段（受理不参与动态排序：一期排序固定创建时间降序） */
    @Schema(description = "排序字段：一期排序固定创建时间降序，字段受理不参与动态排序",
            allowableValues = {"createTime"}, defaultValue = "createTime")
    private String sortField;

    /** 排序方向（受理不参与动态排序：一期固定 desc） */
    @Schema(description = "排序方向：一期固定 desc", allowableValues = {"asc", "desc"},
            defaultValue = "desc")
    private String sortOrder;

    @Override
    public SysForm getBody() {
        return (SysForm) super.getBody();
    }

    @Override
    public void setBody(SysForm body) {
        super.setBody(body);
    }
}
