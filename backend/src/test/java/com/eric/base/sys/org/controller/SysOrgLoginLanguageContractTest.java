package com.eric.base.sys.org.controller;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.session.LoginSessionService;
import com.eric.base.sys.i18n.service.SysI18nUserLanguageService;
import com.eric.base.sys.log.service.LogService;
import com.eric.base.sys.org.model.SysOrgUser;
import com.eric.base.sys.org.request.SysOrgLoginRequest;
import com.eric.base.sys.org.resolver.UserResolver;
import com.eric.base.sys.org.response.SysOrgUserResponse;
import com.eric.base.sys.org.service.SysOrgUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录语言登记契约测试（D3，任务清单 I18N6-BE-001）
 *
 * @author Eric
 * @date 2026/11/5
 * @description 固化登录入口的语言登记行为：
 *              <ul>
 *              <li>既有登录零回归：body 不携带 fieldLanguage 时不调用语言登记，
 *                  登录响应结构与既有一致（metadata 含 X-Login-Token / forceChangePwd）；</li>
 *              <li>携带 fieldLanguage 时登录成功后按用户 ID + 语言码 upsert 用户语言表；</li>
 *              <li>登记抛出异常不影响登录结果（登记属增强能力）。</li>
 *              </ul>
 */
class SysOrgLoginLanguageContractTest {

    /** 32 位测试用户 ID（BaseModel.setFieldId 会把非 32 位 ID 自动重新生成） */
    private static final String USER_ID = "1".repeat(32);

    private SysOrgUserService userService;
    private SysI18nUserLanguageService i18nUserLanguageService;
    private LoginSessionService loginSessionService;
    private SysOrgUserController controller;
    private HttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        userService = mock(SysOrgUserService.class);
        i18nUserLanguageService = mock(SysI18nUserLanguageService.class);
        loginSessionService = mock(LoginSessionService.class);
        controller = new SysOrgUserController(userService, mock(UserResolver.class),
                loginSessionService, mock(LogService.class),
                i18nUserLanguageService, mock(ApplicationEventPublisher.class));
        httpRequest = mock(HttpServletRequest.class);
        when(loginSessionService.create(USER_ID)).thenReturn("token-123");
    }

    /** 构造登录成功用户 */
    private SysOrgUser loginUser() {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(USER_ID);
        user.setFieldLoginName("admin");
        user.setFieldEnable(true);
        return user;
    }

    /** 构造登录请求 */
    private SysOrgLoginRequest loginRequest(String language) {
        SysOrgUser body = new SysOrgUser();
        body.setFieldLoginName("admin");
        body.setFieldPassword("pwd");
        SysOrgLoginRequest request = new SysOrgLoginRequest();
        request.setBody(body);
        request.setFieldLanguage(language);
        return request;
    }

    @Test
    void loginWithoutLanguageKeepsLegacyBehavior() {
        // 既有登录零回归：不携带 fieldLanguage 不触发语言登记
        when(userService.login(anyString(), anyString())).thenReturn(loginUser());
        when(userService.isPasswordChangeRequired(USER_ID)).thenReturn(false);

        SysOrgUserResponse response = controller.login(loginRequest(null), httpRequest);

        assertEquals("SUCCESS", response.getCode());
        assertNotNull(response.getMetadata().get("X-Login-Token"));
        verify(i18nUserLanguageService, never()).upsertLanguage(anyString(), anyString());
    }

    @Test
    void loginWithLanguageRegistersUserLanguage() {
        // D3：携带 fieldLanguage 时登录成功后 upsert 用户语言表
        when(userService.login(anyString(), anyString())).thenReturn(loginUser());
        when(userService.isPasswordChangeRequired(USER_ID)).thenReturn(false);

        SysOrgUserResponse response = controller.login(loginRequest("en_US"), httpRequest);

        assertEquals("SUCCESS", response.getCode());
        verify(i18nUserLanguageService).upsertLanguage(eq(USER_ID), eq("en_US"));
    }

    @Test
    void loginSucceedsEvenWhenLanguageRegistrationFails() {
        // 登记失败仅记录日志，不影响登录结果
        when(userService.login(anyString(), anyString())).thenReturn(loginUser());
        when(userService.isPasswordChangeRequired(USER_ID)).thenReturn(false);
        org.mockito.Mockito.doThrow(new IllegalStateException("db down"))
                .when(i18nUserLanguageService).upsertLanguage(anyString(), anyString());

        SysOrgUserResponse response = controller.login(loginRequest("en_US"), httpRequest);

        assertEquals("SUCCESS", response.getCode());
        assertNotNull(response.getMetadata().get("X-Login-Token"));
        Map<String, Object> metadata = response.getMetadata();
        assertEquals(Boolean.FALSE, metadata.get("forceChangePwd"));
    }
}
