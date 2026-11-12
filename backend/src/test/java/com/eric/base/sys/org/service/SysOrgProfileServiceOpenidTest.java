package com.eric.base.sys.org.service;

import com.eric.base.common.setting.properties.CommonSettingProperties;
import com.eric.base.common.setting.service.CommonSettingService;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.att.dao.SysAttMainDao;
import com.eric.base.sys.org.dao.SysOrgUserDao;
import com.eric.base.sys.org.model.SysOrgUser;
import com.eric.base.sys.org.model.SysOrgUserOpenidDo;
import com.eric.base.sys.org.response.SysOrgProfileResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 个人资料页第三方账号绑定测试（PF3-BE-002）
 *
 * @author Eric
 * @date 2026/11/11
 * @description 覆盖 PF3-BE-002 验收点（方案 4.4）：buildBindUrl 渠道合法性/总开关/渠道凭证/
 *              回调地址逐项校验文案、state 写 Redis（key 前缀 openid_bind_state:、32 位、
 *              TTL 恰 300 秒、值 LinkedHashMap{userId, channel}）、微信/QQ 授权 URL 拼接
 *              （open.weixin.qq.com / graph.qq.com、redirect_uri urlencoded、凭证桥接键优先
 *              与 common_setting 表回退）；handleCallback state 取删一次性（缺失/过期回
 *              expired；服务端不支持 GETDEL 时降级 GET+DEL 取删后分支语义不变），复验总开关（disabled）与凭证（unconfigured）、code 缺失与换号异常
 *              回 oauth_error、查重计数 > 0 即 dup（无本人豁免，update 不触碰）、微信/QQ
 *              成功路径覆写 field_openid（openid/unionid/nickname/bindTime 与另一渠道保留）
 *              并回跳 {redirect_base}/profile?bind={channel}:success；unbind 非法渠道、末渠道
 *              解绑走 clearOpenid 整列置 NULL、部分解绑保留另一渠道、解绑未绑定渠道幂等；
 *              load 渠道开关实时判定（总开关 + 凭证齐全）与未装配依赖 null 容忍；
 *              SysOrgUserOpenidDo 序列化往返写读一致。
 *              注意：不起 Spring 容器，Dao/CommonSettingService/RedisTemplate/OpenidClient
 *              均用 Mockito mock；UserUtil 静态方法经 MockedStatic 模拟；BaseModel.setFieldId
 *              仅接受 32 位 ID，测试模型 ID 一律使用 32 位常量。@Value 桥接键与新增依赖为
 *              字段注入，测试经反射赋值（模拟 Spring 装配）；OpenidClient mock 下
 *              wechatAuthorizeUrl/qqAuthorizeUrl 以 thenCallRealMethod 走真实拼接（纯字符串
 *              拼接、不触碰 RestClient，无出网风险）。
 */
class SysOrgProfileServiceOpenidTest {

    /** 32 位测试 ID（避免 BaseModel.setFieldId 触发重新生成） */
    private static final String USER_ID = "1".repeat(32);

    /** Redis state 键前缀（与 SysOrgProfileService.STATE_KEY_PREFIX 一致，长度 18） */
    private static final String STATE_KEY_PREFIX = "openid_bind_state:";

    /** 回调基础地址（桥接键注入值） */
    private static final String REDIRECT_BASE = "https://example.com";

    /** 仅绑定微信的现行 field_openid JSON */
    private static final String WECHAT_JSON =
            "{\"wechat\":{\"openid\":\"o-old\",\"unionid\":\"u-old\",\"nickname\":\"旧微信昵称\","
                    + "\"bindTime\":\"2026-01-01 08:00:00\"}}";

    /** 仅绑定 QQ 的现行 field_openid JSON */
    private static final String QQ_JSON =
            "{\"qq\":{\"openid\":\"q-old\",\"nickname\":\"QQ昵称\",\"bindTime\":\"2026-01-01 08:00:00\"}}";

    /** 双渠道均绑定的现行 field_openid JSON */
    private static final String BOTH_JSON =
            "{\"wechat\":{\"openid\":\"o-old\",\"unionid\":\"u-old\",\"nickname\":\"旧微信昵称\","
                    + "\"bindTime\":\"2026-01-01 08:00:00\"},"
                    + "\"qq\":{\"openid\":\"q-old\",\"nickname\":\"QQ昵称\",\"bindTime\":\"2026-01-01 08:00:00\"}}";

    private SysOrgUserDao userDao;

    private SysAttMainDao attDao;

    private CommonSettingService settingService;

    private OpenidClient openidClient;

    private RedisTemplate<String, Object> redisTemplate;

    private ValueOperations<String, Object> valueOps;

    private SysOrgProfileService service;

    private MockedStatic<UserUtil> userUtil;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        userDao = mock(SysOrgUserDao.class);
        attDao = mock(SysAttMainDao.class);
        settingService = mock(CommonSettingService.class);
        openidClient = mock(OpenidClient.class);
        redisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // 纯拼接方法走真实实现：不触碰 RestClient 成员、无出网风险，
        // 让 buildBindUrl 断言拿到真实授权页 URL（mock 默认返回 null）
        when(openidClient.wechatAuthorizeUrl(anyString(), anyString(), anyString()))
                .thenCallRealMethod();
        when(openidClient.qqAuthorizeUrl(anyString(), anyString(), anyString()))
                .thenCallRealMethod();

        service = new SysOrgProfileService(userDao, attDao, new ObjectMapper());
        // @Value 桥接键与新增依赖为字段注入，测试反射赋值模拟 Spring 装配
        inject("settingService", settingService);
        inject("redisTemplate", redisTemplate);
        inject("openidClient", openidClient);
        // 桥接键默认全空（未配置），各用例按需覆写
        inject("wechatAppId", "");
        inject("wechatAppSecret", "");
        inject("qqAppId", "");
        inject("qqAppSecret", "");
        inject("redirectBase", "");

        // 默认当前登录人为 USER_ID 本人（接口不收 userId，操作对象由会话定位）
        userUtil = mockStatic(UserUtil.class);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user());
        userUtil.when(UserUtil::getCurrentUserId).thenReturn(USER_ID);
        when(userDao.selectById(USER_ID)).thenReturn(user());
    }

    @AfterEach
    void tearDown() {
        userUtil.close();
    }

    /* ==================== buildBindUrl：校验分支 ==================== */

    @Test
    void buildBindUrlRejectsInvalidChannel() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.buildBindUrl("douyin"));
        assertEquals("绑定渠道无效", ex.getMessage());
        // 非法渠道在校验链最前端拒绝，不产生任何 state
        verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void buildBindUrlRejectsWhenDisabled() {
        enableBind(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.buildBindUrl("wechat"));
        assertEquals("第三方账号绑定未开启", ex.getMessage());
        verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void buildBindUrlRejectsMissingWechatCredentials() {
        enableBind(true);

        // 桥接键与 common_setting 表均未配置 → 微信凭证缺失
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.buildBindUrl("wechat"));
        assertEquals("微信绑定未配置", ex.getMessage());
        verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void buildBindUrlRejectsMissingQqCredentials() {
        enableBind(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.buildBindUrl("qq"));
        assertEquals("QQ绑定未配置", ex.getMessage());
        verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void buildBindUrlRejectsMissingRedirectBase() {
        enableBind(true);
        bridgeWechat("wx-app-id", "wx-app-secret");
        // redirect_base 桥接键与 common_setting 表均未配置

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.buildBindUrl("wechat"));
        assertEquals("回调地址未配置", ex.getMessage());
        verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
    }

    /* ==================== buildBindUrl：成功路径 ==================== */

    @Test
    void buildBindUrlWritesStateAndBuildsWechatUrl() {
        enableBind(true);
        bridgeWechat("wx-app-id", "wx-app-secret");
        inject("redirectBase", REDIRECT_BASE);

        String url = service.buildBindUrl("wechat");

        // state 写 Redis：key 前缀 openid_bind_state:、state 为 32 位十六进制、TTL 恰 300 秒
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> valueCaptor =
                ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());
        String key = keyCaptor.getValue();
        assertTrue(key.startsWith(STATE_KEY_PREFIX), "key 应以前缀 openid_bind_state: 开头");
        String state = key.substring(STATE_KEY_PREFIX.length());
        assertTrue(state.matches("[0-9a-f]{32}"), "state 应为 32 位无连字符十六进制串");
        assertEquals(Duration.ofSeconds(300), ttlCaptor.getValue(), "TTL 应恰为 300 秒");
        // state 值绑定 userId + channel（LinkedHashMap 形态，RedisConfig 序列化器可直存）
        Object captured = valueCaptor.getValue();
        assertTrue(captured instanceof Map, "state 值应为 Map 结构");
        assertTrue(captured instanceof java.util.LinkedHashMap, "state 值应为 LinkedHashMap 形态");
        assertEquals(USER_ID, ((Map<?, ?>) captured).get("userId"));
        assertEquals("wechat", ((Map<?, ?>) captured).get("channel"));

        // 微信授权页 URL：纯拼接（真实方法）、redirect_uri urlencoded、state 与 Redis 一致
        assertTrue(url.startsWith("https://open.weixin.qq.com/connect/qrconnect?"), url);
        assertTrue(url.contains("appid=wx-app-id"), url);
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fexample.com%2Fbackend%2Fsys%2Forg"
                + "%2Fprofile%2Fopenid%2Fcallback"), "redirect_uri 应为 urlencoded 回调地址：" + url);
        assertTrue(url.contains("response_type=code"), url);
        assertTrue(url.contains("scope=snsapi_login"), url);
        assertTrue(url.contains("state=" + state), "state 参数应与 Redis 写入值一致：" + url);
        assertTrue(url.endsWith("#wechat_redirect"), url);
    }

    @Test
    void buildBindUrlBuildsQqUrl() {
        enableBind(true);
        bridgeQq("qq-app-id", "qq-app-secret");
        inject("redirectBase", REDIRECT_BASE);

        String url = service.buildBindUrl("qq");

        assertTrue(url.startsWith("https://graph.qq.com/oauth2.0/authorize?"), url);
        assertTrue(url.contains("response_type=code"), url);
        assertTrue(url.contains("client_id=qq-app-id"), url);
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fexample.com%2Fbackend%2Fsys%2Forg"
                + "%2Fprofile%2Fopenid%2Fcallback"), url);
        assertTrue(url.matches(".*state=[0-9a-f]{32}.*"), url);
    }

    @Test
    void buildBindUrlFallsBackToCommonSettingCredentials() {
        enableBind(true);
        // 桥接键全空：凭证与 redirect_base 回退 common_setting 表 SETTING.openid 段（下划线 key）
        when(settingService.getValue("openid", "wechat_app_id")).thenReturn("wx-set-id");
        when(settingService.getValue("openid", "wechat_app_secret")).thenReturn("wx-set-secret");
        when(settingService.getValue("openid", "redirect_base")).thenReturn("https://fallback.example.com/");

        String url = service.buildBindUrl("wechat");

        assertTrue(url.contains("appid=wx-set-id"), url);
        // 回退地址去尾斜杠后拼接回调路径
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Ffallback.example.com%2Fbackend%2Fsys"
                + "%2Forg%2Fprofile%2Fopenid%2Fcallback"), url);
    }

    /* ==================== handleCallback：失败分支 ==================== */

    @Test
    void handleCallbackReturnsExpiredWhenStateMissing() {
        // state 过期/伪造：getAndDelete 返回 null，回跳不带渠道前缀的 expired
        when(valueOps.getAndDelete(anyString())).thenReturn(null);
        inject("redirectBase", REDIRECT_BASE);

        String url = service.handleCallback("auth-code", "missing-state");

        assertEquals(REDIRECT_BASE + "/profile?bind=expired", url);
        // 第三方异常回跳缺参：Controller 以 required=false 透传 null，同样按 expired 302 回跳
        assertEquals(REDIRECT_BASE + "/profile?bind=expired", service.handleCallback("auth-code", null));
    }

    @Test
    void handleCallbackFallsBackToGetAndDeleteWhenGetdelUnsupported() {
        // 低版本 Redis 服务端（<6.2）不识别 GETDEL：getAndDelete 抛 RedisSystemException，
        // 降级 GET+DEL 两段取删后按既有分支继续（PF5-INT-001 联调缺陷修复回归）
        when(valueOps.getAndDelete(anyString())).thenThrow(new RedisSystemException(
                "Error in execution", new RuntimeException("ERR unknown command `GETDEL`")));
        when(valueOps.get(anyString())).thenReturn(stateValue("wechat", USER_ID));
        inject("redirectBase", REDIRECT_BASE);
        enableBind(true);
        bridgeWechat("wx-app-id", "wx-app-secret");
        when(openidClient.exchangeWechat(anyString(), anyString(), anyString()))
                .thenReturn(new OpenidClient.OpenidProfile("o-fb", null, "降级路径昵称"));
        when(userDao.countDuplicateOpenid("o-fb")).thenReturn(0L);
        when(userDao.selectById(USER_ID)).thenReturn(user(null));

        String url = service.handleCallback("auth-code", "state-1");

        assertEquals(REDIRECT_BASE + "/profile?bind=wechat:success", url);
        // 降级路径取到 state 后删除该键（一次性语义保持），写库照常
        verify(redisTemplate).delete(STATE_KEY_PREFIX + "state-1");
        verify(userDao).update(any(SysOrgUser.class));
    }

    @Test
    void handleCallbackFallbackTreatsMissingStateAsExpired() {
        // 降级路径 GET 也取不到值：与原子路径同样回 expired，且不触发删除
        when(valueOps.getAndDelete(anyString())).thenThrow(new RedisSystemException(
                "Error in execution", new RuntimeException("ERR unknown command `GETDEL`")));
        when(valueOps.get(anyString())).thenReturn(null);
        inject("redirectBase", REDIRECT_BASE);

        String url = service.handleCallback("auth-code", "stale-state");

        assertEquals(REDIRECT_BASE + "/profile?bind=expired", url);
        verify(redisTemplate, never()).delete(anyString());
        verify(userDao, never()).countDuplicateOpenid(anyString());
    }

    @Test
    void handleCallbackFallbackTreatsCorruptStateValueAsExpired() {
        // state 值损坏（被外部改写 / 序列化格式不兼容）：回调为浏览器 302 导航，
        // 按方案 4.4「任何失败均 302 带 fail_reason」降级为 expired，不落 JSON 错误页
        when(valueOps.getAndDelete(anyString())).thenThrow(new SerializationException(
                "Could not read JSON: missing type id property '@class'"));
        when(valueOps.get(anyString())).thenThrow(new SerializationException(
                "Could not read JSON: missing type id property '@class'"));
        inject("redirectBase", REDIRECT_BASE);

        String url = service.handleCallback("auth-code", "corrupt-state");

        assertEquals(REDIRECT_BASE + "/profile?bind=expired", url);
        verify(redisTemplate, never()).delete(anyString());
        verify(userDao, never()).update(any(SysOrgUser.class));
    }

    @Test
    void handleCallbackReturnsNullWhenRedirectBaseMissing() {
        when(valueOps.getAndDelete(anyString())).thenReturn(null);
        // redirect_base 未配置：返回 null，Controller 转 400（回调地址未配置）

        assertNull(service.handleCallback("auth-code", "missing-state"));
    }

    @Test
    void handleCallbackReturnsDisabledWhenBindDisabled() {
        when(valueOps.getAndDelete(anyString())).thenReturn(stateValue("qq", USER_ID));
        inject("redirectBase", REDIRECT_BASE);
        // 总开关关闭（getBooleanValue 默认 null）：复验失败回 disabled，防存量跳转完成绑定
        enableBind(false);

        String url = service.handleCallback("auth-code", "state-1");

        assertEquals(REDIRECT_BASE + "/profile?bind=qq:disabled", url);
    }

    @Test
    void handleCallbackReturnsOauthErrorWhenCodeBlank() {
        when(valueOps.getAndDelete(anyString())).thenReturn(stateValue("wechat", USER_ID));
        inject("redirectBase", REDIRECT_BASE);
        enableBind(true);
        bridgeWechat("wx-app-id", "wx-app-secret");
        // 第三方错误回跳（code 缺失）：不发起换号，直接回 oauth_error

        String url = service.handleCallback(null, "state-1");

        assertEquals(REDIRECT_BASE + "/profile?bind=wechat:oauth_error", url);
        verify(openidClient, never()).exchangeWechat(any(), any(), any());
    }

    @Test
    void handleCallbackReturnsOauthErrorWhenExchangeFails() {
        when(valueOps.getAndDelete(anyString())).thenReturn(stateValue("wechat", USER_ID));
        inject("redirectBase", REDIRECT_BASE);
        enableBind(true);
        bridgeWechat("wx-app-id", "wx-app-secret");
        when(openidClient.exchangeWechat(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("第三方 OAuth 响应解析失败"));
        when(userDao.selectById(USER_ID)).thenReturn(user(WECHAT_JSON));

        String url = service.handleCallback("auth-code", "state-1");

        assertEquals(REDIRECT_BASE + "/profile?bind=wechat:oauth_error", url);
        // 换号失败不应走到查重与写库
        verify(userDao, never()).countDuplicateOpenid(anyString());
        verify(userDao, never()).update(any(SysOrgUser.class));
    }

    @Test
    void handleCallbackReturnsDupWhenOpenidTaken() {
        when(valueOps.getAndDelete(anyString())).thenReturn(stateValue("wechat", USER_ID));
        inject("redirectBase", REDIRECT_BASE);
        enableBind(true);
        bridgeWechat("wx-app-id", "wx-app-secret");
        when(openidClient.exchangeWechat(anyString(), anyString(), anyString()))
                .thenReturn(new OpenidClient.OpenidProfile("o-dup", "u-dup", "第三方昵称"));
        when(userDao.countDuplicateOpenid("o-dup")).thenReturn(2L);
        when(userDao.selectById(USER_ID)).thenReturn(user(WECHAT_JSON));

        String url = service.handleCallback("auth-code", "state-1");

        // 方案 4.4 字面口径：计数 > 0 即拒绝，无本人豁免（state 一次性已防重放）
        assertEquals(REDIRECT_BASE + "/profile?bind=wechat:dup", url);
        verify(userDao, never()).update(any(SysOrgUser.class));
    }

    /* ==================== handleCallback：成功路径 ==================== */

    @Test
    void handleCallbackBindsWechatAndKeepsQq() throws Exception {
        when(valueOps.getAndDelete(anyString())).thenReturn(stateValue("wechat", USER_ID));
        inject("redirectBase", REDIRECT_BASE);
        enableBind(true);
        bridgeWechat("wx-app-id", "wx-app-secret");
        when(openidClient.exchangeWechat(eq("auth-code"), eq("wx-app-id"), eq("wx-app-secret")))
                .thenReturn(new OpenidClient.OpenidProfile("o-new", "u-union", "微信昵称"));
        when(userDao.countDuplicateOpenid("o-new")).thenReturn(0L);
        // 现行已绑 QQ：覆写微信内层不得影响 QQ 内层
        when(userDao.selectById(USER_ID)).thenReturn(user(QQ_JSON));

        String url = service.handleCallback("auth-code", "state-1");

        assertEquals(REDIRECT_BASE + "/profile?bind=wechat:success", url);
        ArgumentCaptor<SysOrgUser> captor = ArgumentCaptor.forClass(SysOrgUser.class);
        verify(userDao).update(captor.capture());
        SysOrgUser updated = captor.getValue();
        assertEquals(USER_ID, updated.getFieldId());
        // 写库 JSON 反序列化回读：微信内层为最新绑定信息
        SysOrgUserOpenidDo parsed = new ObjectMapper()
                .readValue(updated.getFieldOpenid(), SysOrgUserOpenidDo.class);
        assertNotNull(parsed.getWechat());
        assertEquals("o-new", parsed.getWechat().getOpenid());
        assertEquals("u-union", parsed.getWechat().getUnionid());
        assertEquals("微信昵称", parsed.getWechat().getNickname());
        assertNotNull(parsed.getWechat().getBindTime());
        assertTrue(parsed.getWechat().getBindTime()
                .matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"), "bindTime 应为方案 4.1 格式");
        // 原有 QQ 绑定保留
        assertNotNull(parsed.getQq());
        assertEquals("q-old", parsed.getQq().getOpenid());
    }

    @Test
    void handleCallbackBindsQqOnFirstBind() throws Exception {
        when(valueOps.getAndDelete(anyString())).thenReturn(stateValue("qq", USER_ID));
        inject("redirectBase", REDIRECT_BASE);
        enableBind(true);
        bridgeQq("qq-app-id", "qq-app-secret");
        // exchangeQq 第四参为与授权页一致的 redirect_uri（QQ 强制校验）
        when(openidClient.exchangeQq(eq("auth-code"), eq("qq-app-id"), eq("qq-app-secret"),
                eq(REDIRECT_BASE + "/backend/sys/org/profile/openid/callback")))
                .thenReturn(new OpenidClient.OpenidProfile("q-new", null, "QQ新昵称"));
        when(userDao.countDuplicateOpenid("q-new")).thenReturn(0L);
        // 从未绑定（field_openid 为空）：首次绑定 QQ
        when(userDao.selectById(USER_ID)).thenReturn(user(null));

        String url = service.handleCallback("auth-code", "state-1");

        assertEquals(REDIRECT_BASE + "/profile?bind=qq:success", url);
        ArgumentCaptor<SysOrgUser> captor = ArgumentCaptor.forClass(SysOrgUser.class);
        verify(userDao).update(captor.capture());
        String json = captor.getValue().getFieldOpenid();
        assertTrue(json.contains("\"openid\":\"q-new\""), json);
        assertTrue(json.contains("\"nickname\":\"QQ新昵称\""), json);
        // 未绑定渠道语义断言：反序列化后 wechat 内层为 null（方案 4.1「未绑定渠道内层为 null」；
        // Jackson 默认序列化会输出 "wechat":null 键，功能上与 4.1 示例等价，不断言原始串形态）
        SysOrgUserOpenidDo parsedFirstBind = new ObjectMapper()
                .readValue(json, SysOrgUserOpenidDo.class);
        assertNull(parsedFirstBind.getWechat());
    }

    @Test
    void handleCallbackRunsBindingInsideTransaction() {
        when(valueOps.getAndDelete(anyString())).thenReturn(stateValue("wechat", USER_ID));
        inject("redirectBase", REDIRECT_BASE);
        enableBind(true);
        bridgeWechat("wx-app-id", "wx-app-secret");
        when(openidClient.exchangeWechat(anyString(), anyString(), anyString()))
                .thenReturn(new OpenidClient.OpenidProfile("o-tx", null, "事务昵称"));
        when(userDao.countDuplicateOpenid("o-tx")).thenReturn(0L);
        when(userDao.selectById(USER_ID)).thenReturn(user(null));
        // 事务模板注入：execute 直通回调，验证「查重→写库」段经事务执行（方案 4.4 同事务口径）
        TransactionTemplate txTemplate = mock(TransactionTemplate.class);
        when(txTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        inject("transactionTemplate", txTemplate);

        String url = service.handleCallback("auth-code", "state-1");

        assertEquals(REDIRECT_BASE + "/profile?bind=wechat:success", url);
        verify(txTemplate).execute(any());
        verify(userDao).update(any(SysOrgUser.class));
    }

    /* ==================== unbind ==================== */

    @Test
    void unbindRejectsInvalidChannel() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.unbind("douyin"));
        assertEquals("绑定渠道无效", ex.getMessage());
        verify(userDao, never()).clearOpenid(anyString());
        verify(userDao, never()).update(any(SysOrgUser.class));
    }

    @Test
    void unbindClearsColumnWhenLastChannelUnbound() {
        when(userDao.selectById(USER_ID)).thenReturn(user(WECHAT_JSON));

        assertDoesNotThrow(() -> service.unbind("wechat"));

        // 双渠道全空：field_openid 整列置 NULL 走专用 SQL（通用 update 条件写落不了 NULL）
        verify(userDao).clearOpenid(USER_ID);
        verify(userDao, never()).update(any(SysOrgUser.class));
    }

    @Test
    void unbindKeepsOtherChannelWhenPartialUnbind() throws Exception {
        when(userDao.selectById(USER_ID)).thenReturn(user(BOTH_JSON));

        service.unbind("wechat");

        // 双渠道绑定解绑微信：QQ 内层保留，不走整列置 NULL
        verify(userDao, never()).clearOpenid(anyString());
        ArgumentCaptor<SysOrgUser> captor = ArgumentCaptor.forClass(SysOrgUser.class);
        verify(userDao).update(captor.capture());
        SysOrgUserOpenidDo parsed = new ObjectMapper()
                .readValue(captor.getValue().getFieldOpenid(), SysOrgUserOpenidDo.class);
        assertNull(parsed.getWechat());
        assertNotNull(parsed.getQq());
        assertEquals("q-old", parsed.getQq().getOpenid());
    }

    @Test
    void unbindIdempotentForUnboundChannel() {
        when(userDao.selectById(USER_ID)).thenReturn(user(null));

        // 解绑从未绑定的渠道：幂等成功，最终状态与「无绑定」一致（整列置 NULL）
        assertDoesNotThrow(() -> service.unbind("qq"));

        verify(userDao).clearOpenid(USER_ID);
        verify(userDao, never()).update(any(SysOrgUser.class));
    }

    /* ==================== load：渠道开关实时判定 ==================== */

    @Test
    void loadChannelFlagsReflectConfiguration() {
        enableBind(true);
        // 微信凭证齐全（桥接键）、QQ 凭证缺失 → 渠道开关逐渠道独立判定
        bridgeWechat("wx-app-id", "wx-app-secret");

        SysOrgProfileResponse response = service.load();
        assertTrue(Boolean.TRUE.equals(response.getOpenidBindEnabled()));
        assertTrue(Boolean.TRUE.equals(response.getWechatEnabled()));
        assertFalse(Boolean.TRUE.equals(response.getQqEnabled()));
    }

    @Test
    void loadChannelFlagsDisabledWhenSwitchOff() {
        enableBind(false);
        bridgeWechat("wx-app-id", "wx-app-secret");
        bridgeQq("qq-app-id", "qq-app-secret");

        SysOrgProfileResponse response = service.load();
        // 总开关关闭：凭证再齐全渠道也不开放
        assertFalse(Boolean.TRUE.equals(response.getOpenidBindEnabled()));
        assertFalse(Boolean.TRUE.equals(response.getWechatEnabled()));
        assertFalse(Boolean.TRUE.equals(response.getQqEnabled()));
    }

    @Test
    void loadChannelFlagsEnabledWithoutCredentials() {
        enableBind(true);

        SysOrgProfileResponse response = service.load();
        assertTrue(Boolean.TRUE.equals(response.getOpenidBindEnabled()));
        assertFalse(Boolean.TRUE.equals(response.getWechatEnabled()));
        assertFalse(Boolean.TRUE.equals(response.getQqEnabled()));
    }

    @Test
    void loadChannelFlagsTolerateMissingSettingService() throws Exception {
        // 未装配 CommonSettingService（裸构造）：开关按关闭处理，不抛 NPE
        inject("settingService", null);

        SysOrgProfileResponse response = service.load();
        assertFalse(Boolean.TRUE.equals(response.getOpenidBindEnabled()));
        assertFalse(Boolean.TRUE.equals(response.getWechatEnabled()));
        assertFalse(Boolean.TRUE.equals(response.getQqEnabled()));
    }

    /* ==================== 序列化往返 ==================== */

    @Test
    void openidDoSerializationRoundTrip() throws Exception {
        SysOrgUserOpenidDo source = new SysOrgUserOpenidDo();
        SysOrgUserOpenidDo.Wechat wechat = new SysOrgUserOpenidDo.Wechat();
        wechat.setOpenid("o-r");
        wechat.setUnionid("u-r");
        wechat.setNickname("微信昵称");
        wechat.setBindTime("2026-11-11 10:00:00");
        source.setWechat(wechat);
        SysOrgUserOpenidDo.Qq qq = new SysOrgUserOpenidDo.Qq();
        qq.setOpenid("q-r");
        qq.setNickname("QQ昵称");
        qq.setBindTime("2026-11-11 10:00:01");
        source.setQq(qq);

        // 写读一致（@Data equals 逐字段比较）
        ObjectMapper mapper = new ObjectMapper();
        SysOrgUserOpenidDo parsed = mapper.readValue(
                mapper.writeValueAsString(source), SysOrgUserOpenidDo.class);
        assertEquals(source, parsed);
        assertEquals(source.getWechat(), parsed.getWechat());
        assertEquals(source.getQq(), parsed.getQq());
    }

    /* ==================== 测试夹具 ==================== */

    /**
     * 反射注入 SysOrgProfileService 私有字段（模拟 Spring 字段装配：@Autowired/@Value）
     */
    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = SysOrgProfileService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void inject(String fieldName, Object value) {
        try {
            inject(service, fieldName, value);
        } catch (Exception e) {
            throw new IllegalStateException("反射注入失败: " + fieldName, e);
        }
    }

    /**
     * 总开关 stub：common_setting 的 system.openid_bind_enable
     */
    private void enableBind(boolean enabled) {
        when(settingService.getBooleanValue(CommonSettingProperties.TYPE_SYSTEM, "openid_bind_enable"))
                .thenReturn(enabled);
    }

    /**
     * 微信凭证桥接键注入（Spring Environment 优先级路径）
     */
    private void bridgeWechat(String appId, String appSecret) {
        try {
            inject("wechatAppId", appId);
            inject("wechatAppSecret", appSecret);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * QQ 凭证桥接键注入（Spring Environment 优先级路径）
     */
    private void bridgeQq(String appId, String appSecret) {
        try {
            inject("qqAppId", appId);
            inject("qqAppSecret", appSecret);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Redis state 值（服务端写入的 LinkedHashMap{userId, channel} 形态）
     */
    private static Map<String, Object> stateValue(String channel, String userId) {
        Map<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("userId", userId);
        value.put("channel", channel);
        return value;
    }

    private static SysOrgUser user() {
        return user(null);
    }

    private static SysOrgUser user(String openidJson) {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(USER_ID);
        user.setFieldName("张三");
        user.setFieldLoginName("zhangsan");
        user.setFieldOpenid(openidJson);
        return user;
    }
}
