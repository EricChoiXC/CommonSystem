package com.common.system.sys.task.request;

import com.common.system.common.base.model.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 定时任务列表请求对象
 *
 * @author Eric
 * @date 2026/8/15
 * @description 覆写父类泛型 getter/setter，使 Jackson 在反序列化时能正确识别 body 的具体类型；
 *              sortField / sortOrder 承载单条件排序（createTime，Service 层归一化）。
 */
public class SysTaskMainRequest extends BaseRequest<SysTaskMainQuery> {

    /** 排序字段：createTime（白名单，Service 层归一化） */
    @Schema(description = "排序字段：createTime（创建时间），白名单校验", allowableValues = {"createTime"},
            defaultValue = "createTime")
    private String sortField;

    /** 排序方向：asc / desc（白名单，Service 层归一化） */
    @Schema(description = "排序方向：asc / desc，默认 desc", allowableValues = {"asc", "desc"},
            defaultValue = "desc")
    private String sortOrder;

    @Override
    public SysTaskMainQuery getBody() {
        return (SysTaskMainQuery) super.getBody();
    }

    @Override
    public void setBody(SysTaskMainQuery body) {
        super.setBody(body);
    }

    public String getSortField() {
        return sortField;
    }

    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }
}
