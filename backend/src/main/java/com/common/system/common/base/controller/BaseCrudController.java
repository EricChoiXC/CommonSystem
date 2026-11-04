package com.common.system.common.base.controller;

import com.common.system.common.base.model.BaseModel;
import com.common.system.common.base.model.BaseRequest;
import com.common.system.common.base.model.Pages;
import com.common.system.common.base.resolver.BaseResolver;
import com.common.system.common.base.service.IBaseService;
import com.common.system.common.exception.AuthorizationException;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.resolver.AuthorizationRejectedEvent;
import com.common.system.common.utils.StringUtil;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.org.model.SysOrgUser;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Objects;

/**
 * @author Eric
 * @date 2026/8/3
 * @description T 继承BaseModel的实体类<br/>
 * S 继承IBaseService的Service<br/>
 * R 继承BaseResolver的解析器<br/>
 * Q 继承BaseRequest的请求对象
 */
public class BaseCrudController<
                T extends BaseModel,
                S extends IBaseService<T>,
                R extends BaseResolver>
        extends BaseController<T, S> {

    private final R baseResolver;

    private final ApplicationEventPublisher eventPublisher;

    protected BaseCrudController(S baseService, R baseResolver, ApplicationEventPublisher eventPublisher) {
        super(baseService);
        this.baseResolver = Objects.requireNonNull(baseResolver, "baseResolver must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    protected final R getBaseResolver() {
        return baseResolver;
    }

    /**
     * 标准新增
     */
    protected T insert(T body) {
        return getBaseService().insert(body);
    }

    /**
     * 标准编辑
     */
    protected T update(T body) {
        return getBaseService().update(body);
    }

    /**
     * 标准删除
     */
    protected void deleteById(String fieldId) {
        getBaseService().deleteById(fieldId);
    }

    /**
     * 标准查询
     */
    protected T selectById(String fieldId) {
        return getBaseService().selectById(fieldId);
    }

    /**
     * 标准列表查询
     */
    protected List<T> selectList(T body, Pages pages) {
        return getBaseService().selectList(body, pages);
    }


    /**
     * 基于请求对象组装鉴权上下文并鉴权，失败时抛出 {@link AuthorizationException}
     */
    protected void authorize(String action, T body) {
        AuthorizationContext context = new AuthorizationContext(action, body);
        authorize(context);
    }

    /**
     * 基于文档 ID 组装鉴权上下文并鉴权，失败时抛出 {@link AuthorizationException}
     */
    protected void authorize(String action, String documentId) {
        AuthorizationContext context = new AuthorizationContext(action, documentId);
        authorize(context);
    }

    /**
     * 执行最终鉴权
     * <p>未登录或凭证已失效（当前用户为空）时抛出 {@code UNAUTHORIZED}，
     * 前端据此清理本地登录态并跳转登录页；已登录但缺少权限时抛出 {@code FORBIDDEN}，
     * 两者由全局异常处理器统一转换为 BaseResponse。</p>
     */
    protected void authorize(AuthorizationContext context) {
        SysOrgUser currentUser = UserUtil.getCurrentUser();
        if (null == currentUser) {
            throw new AuthorizationException("UNAUTHORIZED", "登录已过期，请重新登录");
        }
        if (!getBaseResolver().resolve(context)) {
            // 统一鉴权入口：FORBIDDEN 时发布鉴权拒绝事件，供日志模块记录安全审计日志
            publishAuthorizationRejected(currentUser.getFieldId(), context);
            throw new AuthorizationException("FORBIDDEN",
                    "no permission for action: " + context.getAction());
        }
    }

    /**
     * 发布鉴权拒绝事件（安全审计）：记录当前用户、接口动作、文档 ID、拒绝原因；
     * 审计日志记录失败不影响鉴权流程本身。
     */
    private void publishAuthorizationRejected(String userId, AuthorizationContext context) {
        try {
            eventPublisher.publishEvent(new AuthorizationRejectedEvent(userId, context.getAction(),
                    context.getDocumentId(), "no permission for action: " + context.getAction()));
        } catch (RuntimeException e) {
            // 忽略：审计失败不得阻断鉴权
        }
    }
}
