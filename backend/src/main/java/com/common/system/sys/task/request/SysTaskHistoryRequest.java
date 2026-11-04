package com.common.system.sys.task.request;

import com.common.system.common.base.model.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 执行记录列表请求对象
 *
 * @author Eric
 * @date 2026/8/15
 * @description 覆写父类泛型 getter/setter，使 Jackson 在反序列化时能正确识别 body 的具体类型；
 *              sortField / sortOrder 承载单条件排序（startTime / endTime，Service 层归一化）。
 */
public class SysTaskHistoryRequest extends BaseRequest<SysTaskHistoryQuery> {

    /** 排序字段：startTime / endTime（白名单，Service 层归一化） */
    @Schema(description = "排序字段：startTime（执行开始时间）/ endTime（执行结束时间）",
            allowableValues = {"startTime", "endTime"}, defaultValue = "startTime")
    private String sortField;

    /** 排序方向：asc / desc（白名单，Service 层归一化） */
    @Schema(description = "排序方向：asc / desc，默认 desc", allowableValues = {"asc", "desc"},
            defaultValue = "desc")
    private String sortOrder;

    @Override
    public SysTaskHistoryQuery getBody() {
        return (SysTaskHistoryQuery) super.getBody();
    }

    @Override
    public void setBody(SysTaskHistoryQuery body) {
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
