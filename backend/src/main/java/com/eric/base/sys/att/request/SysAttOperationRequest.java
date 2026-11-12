package com.eric.base.sys.att.request;

import com.eric.base.common.base.model.BaseRequest;
import com.eric.base.sys.att.model.SysAttOperation;

/**
 * 附件操作日志请求对象
 *
 * @author Eric
 * @date 2026/8/7
 * @description 覆写父类泛型 getter/setter，使 Jackson 在反序列化时能正确识别 body 的具体类型；
 *              sortOrder 承载操作时间排序方向（asc / desc，唯一排序维度）。
 */
public class SysAttOperationRequest extends BaseRequest<SysAttOperation> {

    /** 排序方向：asc / desc（白名单，Service 层归一化），按操作时间排序 */
    private String sortOrder;

    @Override
    public SysAttOperation getBody() {
        return (SysAttOperation) super.getBody();
    }

    @Override
    public void setBody(SysAttOperation body) {
        super.setBody(body);
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }
}
