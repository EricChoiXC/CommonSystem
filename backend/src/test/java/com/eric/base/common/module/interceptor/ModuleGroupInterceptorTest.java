package com.eric.base.common.module.interceptor;

import com.eric.base.common.exception.ModuleDisabledException;
import com.eric.base.common.module.annotation.ModuleGroup;
import com.eric.base.common.module.service.ModuleGroupCacheService;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 模块组开关拦截器测试
 *
 * @author Eric
 * @date 2026/11/12
 * @description 固化修订稿第 2 节行为语义表与 8.2 判定流程（纯单测，不起 Spring 服务）：
 *              非 HandlerMethod 放行、无注解放行且不查缓存、类级注解开启/关闭、
 *              方法级覆盖类级、未登录放行且不查缓存（D2）、未知 key fail-open 放行。
 *              当前登录人经 MockedStatic&lt;UserUtil&gt; 模拟（参照 I18nResolverTest 先例）；
 *              HandlerMethod 用真实实例承载注解反射元数据，请求/响应与缓存服务用 Mockito mock。
 */
class ModuleGroupInterceptorTest {

    /** 类级注解夹具的方法（无方法级注解） */
    private static final String CLASS_KEY = "fixture-class-key";
    /** 方法级覆盖类级夹具：类级 key（关闭侧） */
    private static final String CLASS_CLOSED_KEY = "fixture-class-closed-key";
    /** 方法级覆盖类级夹具：方法级 key（开启侧） */
    private static final String METHOD_OPEN_KEY = "fixture-method-open-key";
    /** 方法级覆盖类级夹具：方法级 key（关闭侧） */
    private static final String METHOD_CLOSED_KEY = "fixture-method-closed-key";

    private HttpServletRequest request;
    private HttpServletResponse response;
    private ModuleGroupCacheService cacheService;
    private ModuleGroupInterceptor interceptor;
    private MockedStatic<UserUtil> userUtil;

    @BeforeEach
    void setUp() {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        cacheService = mock(ModuleGroupCacheService.class);
        interceptor = new ModuleGroupInterceptor(cacheService);
        // 默认桩为已登录，未登录用例在方法内改桩
        SysOrgUser user = new SysOrgUser();
        user.setFieldId("1".repeat(32));
        userUtil = org.mockito.Mockito.mockStatic(UserUtil.class);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        userUtil.close();
    }

    /** 构造真实 HandlerMethod（beanType = 夹具类，注解元数据真实可反射） */
    private static HandlerMethod handler(Object bean, String methodName) throws NoSuchMethodException {
        return new HandlerMethod(bean, bean.getClass().getMethod(methodName));
    }

    @Test
    void nonHandlerMethodPassesThrough() {
        // 静态资源、swagger、SPA 回退等非 HandlerMethod 请求不拦
        assertTrue(interceptor.preHandle(request, response, "not-a-handler-method"));
        assertTrue(interceptor.preHandle(request, response, new Object()));
        verifyNoInteractions(cacheService);
    }

    @Test
    void noAnnotationPassesThroughWithoutCacheCheck() throws NoSuchMethodException {
        // 方法与类均无注解 = 不受开关控制（公共入口放行路径）
        HandlerMethod handlerMethod = handler(new PlainHandler(), "handle");

        assertTrue(interceptor.preHandle(request, response, handlerMethod));
        verifyNoInteractions(cacheService);
    }

    @Test
    void classAnnotationWithEnabledModulePasses() throws NoSuchMethodException {
        // 已登录 + 命中开启模块组的类级注解 → 放行
        when(cacheService.isEnabled(CLASS_KEY)).thenReturn(true);
        HandlerMethod handlerMethod = handler(new ClassAnnotatedHandler(), "handle");

        assertTrue(interceptor.preHandle(request, response, handlerMethod));
        verify(cacheService).isEnabled(CLASS_KEY);
    }

    @Test
    void classAnnotationWithDisabledModuleThrows() throws NoSuchMethodException {
        // 已登录 + 命中关闭模块组的类级注解 → 抛 ModuleDisabledException 且 key 正确
        when(cacheService.isEnabled(CLASS_KEY)).thenReturn(false);
        HandlerMethod handlerMethod = handler(new ClassAnnotatedHandler(), "handle");

        ModuleDisabledException exception = assertThrows(ModuleDisabledException.class,
                () -> interceptor.preHandle(request, response, handlerMethod));
        assertEquals(CLASS_KEY, exception.getModuleGroupKey());
    }

    @Test
    void methodAnnotationOverridesClassAnnotationEnabledMethodWins() throws NoSuchMethodException {
        // 类关闭 + 方法开启：方法级覆盖类级，按方法级 key 判定 → 放行
        when(cacheService.isEnabled(METHOD_OPEN_KEY)).thenReturn(true);
        when(cacheService.isEnabled(CLASS_CLOSED_KEY)).thenReturn(false);
        HandlerMethod handlerMethod = handler(new MethodOverrideHandler(), "methodOpen");

        assertTrue(interceptor.preHandle(request, response, handlerMethod));
        verify(cacheService).isEnabled(METHOD_OPEN_KEY);
        verify(cacheService, never()).isEnabled(CLASS_CLOSED_KEY);
    }

    @Test
    void methodAnnotationOverridesClassAnnotationDisabledMethodWins() throws NoSuchMethodException {
        // 类开启 + 方法关闭：按方法级 key 判定 → 拦截
        when(cacheService.isEnabled(METHOD_CLOSED_KEY)).thenReturn(false);
        when(cacheService.isEnabled(CLASS_CLOSED_KEY)).thenReturn(true);
        HandlerMethod handlerMethod = handler(new MethodOverrideHandler(), "methodClosed");

        ModuleDisabledException exception = assertThrows(ModuleDisabledException.class,
                () -> interceptor.preHandle(request, response, handlerMethod));
        assertEquals(METHOD_CLOSED_KEY, exception.getModuleGroupKey());
    }

    @Test
    void anonymousRequestPassesWithoutCacheCheck() throws NoSuchMethodException {
        // 未登录 + 命中关闭模块组 → 放行给最终鉴权（返回 UNAUTHORIZED），不向匿名暴露开关状态（D2）
        userUtil.when(UserUtil::getCurrentUser).thenReturn(null);
        HandlerMethod handlerMethod = handler(new ClassAnnotatedHandler(), "handle");

        assertDoesNotThrow(() -> assertTrue(interceptor.preHandle(request, response, handlerMethod)));
        verifyNoInteractions(cacheService);
    }

    @Test
    void unknownKeyFailOpenPasses() throws NoSuchMethodException {
        // 未知 key（common_module_group 中不存在）经缓存服务 fail-open 返回 true → 放行
        when(cacheService.isEnabled(CLASS_KEY)).thenReturn(true);
        HandlerMethod handlerMethod = handler(new ClassAnnotatedHandler(), "handle");

        assertTrue(interceptor.preHandle(request, response, handlerMethod));
        verify(cacheService).isEnabled(CLASS_KEY);
    }

    /** 无注解夹具：方法与类均无 @ModuleGroup */
    static class PlainHandler {

        public void handle() {
        }
    }

    /** 类级注解夹具：类 key = fixture-class-key，方法无注解（走类级回退） */
    @ModuleGroup(moduleGroupKey = CLASS_KEY)
    static class ClassAnnotatedHandler {

        public void handle() {
        }
    }

    /** 方法级覆盖类级夹具：类级 key 关闭侧，方法级分别标注开启/关闭 key */
    @ModuleGroup(moduleGroupKey = CLASS_CLOSED_KEY)
    static class MethodOverrideHandler {

        @ModuleGroup(moduleGroupKey = METHOD_OPEN_KEY)
        public void methodOpen() {
        }

        @ModuleGroup(moduleGroupKey = METHOD_CLOSED_KEY)
        public void methodClosed() {
        }
    }
}
