package com.common.system.sys.att.request;

import com.common.system.common.base.model.BaseRequest;
import com.common.system.sys.att.model.SysAttMain;

/**
 * 附件主表请求对象
 *
 * @author Eric
 * @date 2026/8/7
 * @description 覆写父类泛型 getter/setter，使 Jackson 在反序列化时能正确识别 body 的具体类型；
 *              sortField / sortOrder 承载单条件排序（createTime / download / view + asc / desc）。
 */
public class SysAttMainRequest extends BaseRequest<SysAttMain> {

    /** 排序字段：createTime / download / view（白名单，Service 层归一化） */
    private String sortField;

    /** 排序方向：asc / desc（白名单，Service 层归一化） */
    private String sortOrder;

    @Override
    public SysAttMain getBody() {
        return (SysAttMain) super.getBody();
    }

    @Override
    public void setBody(SysAttMain body) {
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
