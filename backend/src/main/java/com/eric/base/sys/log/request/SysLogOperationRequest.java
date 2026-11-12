package com.eric.base.sys.log.request;

import com.eric.base.common.base.model.BaseRequest;
import com.eric.base.sys.log.model.SysLogOperation;

/**
 * 日志查询请求对象
 *
 * @author Eric
 * @date 2026/8/8
 * @description 覆写父类泛型 getter/setter，使 Jackson 在反序列化时能正确识别 body 的具体类型；
 *              sortOrder 承载操作时间排序方向（asc / desc，唯一排序维度）；
 *              operateTimeStart / operateTimeEnd 承载操作时间范围筛选（yyyy-MM-dd HH:mm:ss）。
 */
public class SysLogOperationRequest extends BaseRequest<SysLogOperation> {

    /** 排序方向：asc / desc（白名单，Service 层归一化），按操作时间排序 */
    private String sortOrder;

    /** 操作时间范围起（yyyy-MM-dd HH:mm:ss，含） */
    private String operateTimeStart;

    /** 操作时间范围止（yyyy-MM-dd HH:mm:ss，含） */
    private String operateTimeEnd;

    @Override
    public SysLogOperation getBody() {
        return (SysLogOperation) super.getBody();
    }

    @Override
    public void setBody(SysLogOperation body) {
        super.setBody(body);
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getOperateTimeStart() {
        return operateTimeStart;
    }

    public void setOperateTimeStart(String operateTimeStart) {
        this.operateTimeStart = operateTimeStart;
    }

    public String getOperateTimeEnd() {
        return operateTimeEnd;
    }

    public void setOperateTimeEnd(String operateTimeEnd) {
        this.operateTimeEnd = operateTimeEnd;
    }
}
