package com.eric.base.common.resolver;

import com.eric.base.common.base.model.BaseModel;
import lombok.Data;

/**
 * 鉴权上下文
 *
 * @author Eric
 * @date 2026/8/4
 * @description 承载接口级鉴权所需的核心参数：接口标识 + 文档 ID；
 *              由前端随请求携带（BaseRequest.params），Controller 在执行核心业务逻辑前
 *              组装并交给对应接口的 resolver 完成最终鉴权。
 */
@Data
public class AuthorizationContext {

    public AuthorizationContext() {
    }

    public AuthorizationContext(String action, String documentId) {
        this.action = action;
        this.documentId = documentId;
    }

    public AuthorizationContext(String action) {
        this.action = action;
    }

    public AuthorizationContext(String action, BaseModel model) {
        this.action = action;
        this.documentId = model.getFieldId();
    }

    public AuthorizationContext(String action, String documentId, String operation, String remark) {
        this.action = action;
        this.documentId = documentId;
        this.operation = operation;
        this.remark = remark;
    }

    /**
     * 接口标识，如 sys.org.user.save
     */
    private String action;

    /**
     * 文档 ID（业务单据/实体的主键），参与最终鉴权
     */
    private String documentId;

    /**
     * 操作类型：view / create / edit / delete / submit / audit / export 等
     */
    private String operation;

    /**
     * 可选的补充上下文（组织、角色、流程状态等），供 resolver 扩展使用
     */
    private String remark;
}
