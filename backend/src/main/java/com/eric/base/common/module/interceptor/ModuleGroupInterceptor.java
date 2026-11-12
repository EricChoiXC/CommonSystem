package com.eric.base.common.module.interceptor;

import com.eric.base.common.exception.ModuleDisabledException;
import com.eric.base.common.module.annotation.ModuleGroup;
import com.eric.base.common.module.service.ModuleGroupCacheService;
import com.eric.base.common.utils.UserUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 模块组开关拦截器
 *
 * <p>设计依据 docs/v1_1-common/05-增加接口开关/接口开关机制设计方案-修订稿.md 第 8 节。
 * 判定位于业务鉴权（ACTION_* + resolver）之前：模块未开启时无需再讨论权限，符合「门禁」语义。
 * preHandle 判定流程：</p>
 * <ol>
 * <li>非 {@link HandlerMethod} 放行——静态资源、swagger、SPA 路由回退均非 HandlerMethod，天然不拦；</li>
 * <li>读取 {@link ModuleGroup} 注解：方法级优先，方法级为空时对类级回退（方法级覆盖类级）；</li>
 * <li><b>无注解放行</b>——无注解 = 不受开关控制，这是机制成立的前提：
 *     登录、/init、/initID、setting/load、SSE 订阅、swagger 等公共入口均不打注解，不受开关影响；</li>
 * <li><b>未登录放行</b>——放行给后续最终鉴权（返回 UNAUTHORIZED），
 *     避免向匿名请求暴露模块开关状态（修订稿第 2 节语义表 / 决策 D2）；</li>
 * <li>模块组开启（含缓存 fail-open）→ 放行，进入既有鉴权链路；</li>
 * <li>已登录 + 模块组关闭 → 抛出 {@link ModuleDisabledException}，
 *     经 GlobalExceptionHandler 返回 MODULE_DISABLED（HTTP 200 + code 体系），不进入 Controller 与 resolver。</li>
 * </ol>
 * <p>时序：本拦截器运行于 Spring MVC 层，先于它执行的 CurrentUserFilter（Servlet Filter，
 * 经 SecurityConfig 注册于 /backend/*）已将登录用户 ID 写入 CurrentUserHolder，
 * 故 preHandle 中 {@link UserUtil#getCurrentUser()} 已可用。</p>
 *
 * @author Eric
 * @date 2026/11/12
 * @description 按模块组开关状态拦截 /backend/** 下标注 @ModuleGroup 的接口：
 *              已登录 + 关闭模块组抛 ModuleDisabledException，其余场景一律放行。
 */
@Component
public class ModuleGroupInterceptor implements HandlerInterceptor {

    private final ModuleGroupCacheService moduleGroupCacheService;

    public ModuleGroupInterceptor(ModuleGroupCacheService moduleGroupCacheService) {
        this.moduleGroupCacheService = moduleGroupCacheService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true; // 非 Controller 请求不拦（静态资源、swagger、SPA 回退等）
        }
        ModuleGroup moduleGroup = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), ModuleGroup.class);
        if (null == moduleGroup) {
            // 方法级为空时类级回退（方法级覆盖类级）
            moduleGroup = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(), ModuleGroup.class);
        }
        if (null == moduleGroup) {
            return true; // 无注解 = 不受开关控制
        }
        if (null == UserUtil.getCurrentUser()) {
            return true; // 未登录放行给最终鉴权，不向匿名暴露开关状态（D2）
        }
        if (moduleGroupCacheService.isEnabled(moduleGroup.moduleGroupKey())) {
            return true;
        }
        throw new ModuleDisabledException(moduleGroup.moduleGroupKey());
    }
}
