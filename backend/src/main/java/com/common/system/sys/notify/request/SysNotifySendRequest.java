package com.common.system.sys.notify.request;

import com.common.system.common.base.model.BaseRequest;
import com.common.system.sys.notify.model.SysNotifyTodo;

import java.util.List;

/**
 * 发送待办请求对象
 *
 * @author Eric
 * @date 2026/8/12
 * @description body 为待办主表内容（标题/待办类型/来源模块/文档id/链接），
 *              userIds 为接收人（多选）；覆写父类泛型 getter/setter 使 Jackson
 *              能正确识别 body 的具体类型。
 */
public class SysNotifySendRequest extends BaseRequest<SysNotifyTodo> {

    /** 接收人 id 列表（多选） */
    private List<String> userIds;

    @Override
    public SysNotifyTodo getBody() {
        return (SysNotifyTodo) super.getBody();
    }

    @Override
    public void setBody(SysNotifyTodo body) {
        super.setBody(body);
    }

    public List<String> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<String> userIds) {
        this.userIds = userIds;
    }
}
