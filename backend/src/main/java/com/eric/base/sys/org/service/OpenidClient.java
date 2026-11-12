package com.eric.base.sys.org.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 第三方账号 OAuth HTTP 客户端（PF3-BE-002）
 *
 * @author Eric
 * @date 2026/11/11
 * @description 微信开放平台（扫码登录）与 QQ 互联的授权码换用户信息 HTTP 调用与响应解析，
 *              依据 docs/v1_2-用户管理中心/个人资料页与第三方账号绑定 方案 4.4：
 *              <ul>
 *                <li>微信：access_token 端点为 JSON 响应（网站应用扫码登录响应体含 nickname），
 *                    一次调用即得 openid/unionid/nickname；nickname 为空时回退再调 userinfo 端点补取；</li>
 *                <li>QQ：token 端点返回 form-urlencoded 形式的 text/plain（非 JSON），需手工解析；
 *                    随后 me 端点（fmt=json）取 openid、user/get_user_info 端点取 nickname。</li>
 *              </ul>
 *              本类只做 HTTP 调用与响应解析，不做任何业务判定（开关/查重/写库均在
 *              {@link SysOrgProfileService}）。解析失败或第三方返回错误码时抛
 *              {@link IllegalStateException}，由服务层捕获转 fail_reason=oauth_error；
 *              日志一律不携带 app_secret / access_token。
 *              <p>HTTP 经 {@link RestClient}（Spring 6.1 原生，零新增依赖），在构造时创建
 *              成员字段，单测可直接 new 本类并替换/绕过（真实调用不出网）。</p>
 */
@Component
public class OpenidClient {

    private static final Logger log = LoggerFactory.getLogger(OpenidClient.class);

    /** 微信扫码登录授权页地址（方案 4.4） */
    private static final String WECHAT_AUTHORIZE_URL =
            "https://open.weixin.qq.com/connect/qrconnect";

    /** 微信授权码换 access_token 端点（JSON 响应，网站应用含 nickname） */
    private static final String WECHAT_ACCESS_TOKEN_URL =
            "https://api.weixin.qq.com/sns/oauth2/access_token";

    /** 微信用户信息端点（access_token 响应缺 nickname 时的回退取值） */
    private static final String WECHAT_USERINFO_URL =
            "https://api.weixin.qq.com/sns/userinfo";

    /** QQ 互联授权页地址（方案 4.4） */
    private static final String QQ_AUTHORIZE_URL =
            "https://graph.qq.com/oauth2.0/authorize";

    /** QQ 授权码换 access_token 端点（form-urlencoded 形式的 text/plain 响应，非 JSON） */
    private static final String QQ_ACCESS_TOKEN_URL =
            "https://graph.qq.com/oauth2.0/token";

    /** QQ openid 获取端点（fmt=json 返回 {client_id, openid}） */
    private static final String QQ_ME_URL =
            "https://graph.qq.com/oauth2.0/me";

    /** QQ 用户信息端点（返回 {nickname, ...}） */
    private static final String QQ_USER_INFO_URL =
            "https://graph.qq.com/user/get_user_info";

    /** 授权回调 state 有效期同 Redis TTL（仅用于授权页 URL 的 state 参数透传，实际校验在服务层） */
    public static final long STATE_TTL_SECONDS = 300;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * RestClient 成员：构造时创建（不读任何配置，仅设超时外的默认行为），
     * 单测可子类覆写或直接绕过（不真实出网）
     */
    private final RestClient restClient;

    public OpenidClient() {
        this.restClient = RestClient.builder().build();
    }

    /**
     * 测试可见构造器：单测注入可控 RestClient（或替换实现）
     */
    protected OpenidClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 第三方用户信息解析结果（服务层只消费 openid/unionid/nickname 三字段）
     */
    public static class OpenidProfile {

        /** 第三方 openid（微信/QQ 通用主键） */
        private final String openid;

        /** 微信 unionid（开放平台同一主体下唯一，QQ 渠道为 null） */
        private final String unionid;

        /** 第三方昵称（仅绑定信息展示缓存，不回写 sys_org_element.field_name） */
        private final String nickname;

        public OpenidProfile(String openid, String unionid, String nickname) {
            this.openid = openid;
            this.unionid = unionid;
            this.nickname = nickname;
        }

        public String getOpenid() {
            return openid;
        }

        public String getUnionid() {
            return unionid;
        }

        public String getNickname() {
            return nickname;
        }
    }

    /**
     * 微信授权页 URL 拼接（方案 4.4，纯拼接不联网，便于单测）：
     * https://open.weixin.qq.com/connect/qrconnect?appid={}&redirect_uri={urlencoded}
     * &response_type=code&scope=snsapi_login&state={}#wechat_redirect
     *
     * @param appId       微信开放平台应用 AppID
     * @param redirectUri 回调地址（本系统 /openid/callback，整段 urlencoded）
     * @param state       一次性防 CSRF/防重放 state（服务层已存 Redis TTL 300s）
     * @return 授权页完整 URL
     */
    public String wechatAuthorizeUrl(String appId, String redirectUri, String state) {
        return WECHAT_AUTHORIZE_URL
                + "?appid=" + urlEncode(appId)
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&response_type=code"
                + "&scope=snsapi_login"
                + "&state=" + urlEncode(state)
                + "#wechat_redirect";
    }

    /**
     * QQ 授权页 URL 拼接（方案 4.4，纯拼接不联网，便于单测）：
     * https://graph.qq.com/oauth2.0/authorize?response_type=code&client_id={}
     * &redirect_uri={urlencoded}&state={}
     *
     * @param appId       QQ 互联应用 AppID（client_id）
     * @param redirectUri 回调地址（本系统 /openid/callback，整段 urlencoded）
     * @param state       一次性防 CSRF/防重放 state（服务层已存 Redis TTL 300s）
     * @return 授权页完整 URL
     */
    public String qqAuthorizeUrl(String appId, String redirectUri, String state) {
        return QQ_AUTHORIZE_URL
                + "?response_type=code"
                + "&client_id=" + urlEncode(appId)
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&state=" + urlEncode(state);
    }

    /**
     * 微信授权码换用户信息（方案 4.4）：
     * GET access_token 端点（JSON：{access_token, openid, unionid, nickname, errcode?}）——
     * 网站应用扫码登录的 access_token 响应体即含 nickname，直接采用；响应缺 nickname 时
     * 回退再调一次 userinfo 端点补取。unionid 仅在响应携带时带回（未绑定开放平台主体为空）。
     *
     * @param code 第三方回调携带的授权码
     * @param appId 微信开放平台应用 AppID
     * @param appSecret 微信开放平台应用 AppSecret
     * @return 解析出的用户信息（openid 必有，unionid/nickname 可能为 null）
     * @throws IllegalStateException HTTP 失败、响应非 JSON 或第三方返回 errcode 非 0
     */
    public OpenidProfile exchangeWechat(String code, String appId, String appSecret) {
        String body = getForJson(WECHAT_ACCESS_TOKEN_URL
                + "?appid=" + urlEncode(appId)
                + "&secret=" + urlEncode(appSecret)
                + "&code=" + urlEncode(code)
                + "&grant_type=authorization_code");
        JsonNode node = parseJson(body, "wechat access_token");
        // 微信业务错误：HTTP 200 但 errcode 非 0（如 code 已用/过期）
        if (node.hasNonNull("errcode") && node.get("errcode").asInt() != 0) {
            log.warn("微信 access_token 响应错误码 errcode={}", node.get("errcode").asInt());
            throw new IllegalStateException("wechat oauth error, errcode=" + node.get("errcode").asInt());
        }
        String accessToken = requireField(node, "access_token", "wechat access_token");
        String openid = requireField(node, "openid", "wechat access_token");
        String unionid = node.hasNonNull("unionid") ? node.get("unionid").asText() : null;
        // 网站应用扫码登录 access_token 响应即含 nickname，直接采用；
        // 个别账号类型响应缺 nickname 时回退 userinfo 端点补取一次
        String nickname = node.hasNonNull("nickname") ? node.get("nickname").asText() : null;
        if (null == nickname || nickname.isEmpty()) {
            nickname = fetchWechatNickname(accessToken, openid);
        }
        return new OpenidProfile(openid, unionid, nickname);
    }

    /**
     * QQ 授权码换用户信息（方案 4.4）：
     * ① token 端点返回 form-urlencoded 形式字符串（access_token=xxx&expires_in=xxx），
     *    Content-Type 为 text/plain 而非 JSON，按字符串取回后手工解析；
     * ② me 端点（fmt=json）取 openid；
     * ③ user/get_user_info 端点取 nickname。
     *
     * @param code 第三方回调携带的授权码
     * @param appId QQ 互联应用 AppID（client_id / oauth_consumer_key）
     * @param appSecret QQ 互联应用 AppSecret（client_secret）
     * @param redirectUri 与取 token 请求一致的回调地址（QQ 强制校验与授权页 redirect_uri 相同）
     * @return 解析出的用户信息（openid/nickname 必有，unionid 恒 null——QQ 无 unionid 概念）
     * @throws IllegalStateException HTTP 失败、响应无法解析或关键字段缺失
     */
    public OpenidProfile exchangeQq(String code, String appId, String appSecret, String redirectUri) {
        // QQ token 端点返回 text/plain 的 form-urlencoded 形式（access_token=...&expires_in=...），
        // 非 JSON，按字符串取回手工解析
        String body = getForText(QQ_ACCESS_TOKEN_URL
                + "?grant_type=authorization_code"
                + "&client_id=" + urlEncode(appId)
                + "&client_secret=" + urlEncode(appSecret)
                + "&code=" + urlEncode(code)
                + "&redirect_uri=" + urlEncode(redirectUri));
        Map<String, String> form = parseFormUrlencoded(body, "qq access_token");
        String accessToken = requireFormField(form, "access_token", "qq access_token");

        // me 端点 fmt=json 返回 {client_id, openid}（原回调形式 callback( {...} ); 已由 fmt=json 消除）
        String meBody = getForJson(QQ_ME_URL + "?access_token=" + urlEncode(accessToken) + "&fmt=json");
        JsonNode meNode = parseJson(meBody, "qq me");
        String openid = requireField(meNode, "openid", "qq me");

        // get_user_info 端点取昵称
        String infoBody = getForJson(QQ_USER_INFO_URL
                + "?access_token=" + urlEncode(accessToken)
                + "&oauth_consumer_key=" + urlEncode(appId)
                + "&openid=" + urlEncode(openid));
        JsonNode infoNode = parseJson(infoBody, "qq user info");
        String nickname = infoNode.hasNonNull("nickname") ? infoNode.get("nickname").asText() : null;
        return new OpenidProfile(openid, null, nickname);
    }

    /**
     * 微信 userinfo 端点补取昵称（access_token 响应缺 nickname 时的回退路径）
     */
    private String fetchWechatNickname(String accessToken, String openid) {
        try {
            String body = getForJson(WECHAT_USERINFO_URL
                    + "?access_token=" + urlEncode(accessToken)
                    + "&openid=" + urlEncode(openid));
            JsonNode node = parseJson(body, "wechat userinfo");
            return node.hasNonNull("nickname") ? node.get("nickname").asText() : null;
        } catch (IllegalStateException e) {
            // 昵称仅作展示缓存，补取失败不阻断绑定，降级为空昵称
            log.warn("微信 userinfo 补取昵称失败（不阻断绑定）：{}", e.getMessage());
            return null;
        }
    }

    /**
     * GET 取回 JSON 文本（retrieve() 对 4xx/5xx 默认抛异常，由调用方 catch 转 IllegalStateException）
     */
    private String getForJson(String url) {
        return getForText(url, MediaType.APPLICATION_JSON);
    }

    /**
     * GET 取回原始文本（QQ token 端点为 text/plain，不做 JSON 降解）
     */
    private String getForText(String url) {
        return getForText(url, MediaType.TEXT_PLAIN);
    }

    /**
     * GET 取回文本：Accept 按 expected 请求，响应一律以字符串返回交由各自解析路径处理；
     * RestClient retrieve() 对非 2xx 默认抛 RestClientResponseException，此处统一包成
     * IllegalStateException（日志只带 URL 路径，不带 secret / access_token 参数）
     */
    private String getForText(String url, MediaType expected) {
        try {
            return restClient.get()
                    .uri(url)
                    .accept(expected)
                    .retrieve()
                    .body(String.class);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("第三方 OAuth 请求失败：{}，原因：{}", stripQuery(url), e.getMessage());
            throw new IllegalStateException("第三方 OAuth 请求失败: " + e.getClass().getSimpleName());
        }
    }

    /**
     * 解析 JSON 文本为 JsonNode，非法时抛 IllegalStateException（附上下文标签便于定位渠道）
     */
    private JsonNode parseJson(String body, String context) {
        if (null == body || body.isEmpty()) {
            throw new IllegalStateException("第三方 OAuth 响应为空: " + context);
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("第三方 OAuth 响应解析失败: " + context);
        }
    }

    /**
     * 解析 form-urlencoded 形式字符串（QQ token 端点）：
     * access_token=xxx&expires_in=7776000&refresh_token=xxx，键值均不做 decode 以外的处理
     */
    private Map<String, String> parseFormUrlencoded(String body, String context) {
        Map<String, String> form = new HashMap<>();
        if (null == body || body.isEmpty()) {
            throw new IllegalStateException("第三方 OAuth 响应为空: " + context);
        }
        for (String pair : body.trim().split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            form.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
        return form;
    }

    /**
     * 取 JSON 必需字段，缺失/为空抛 IllegalStateException
     */
    private String requireField(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (null == value || value.isNull() || value.asText().isEmpty()) {
            throw new IllegalStateException("第三方 OAuth 响应缺少 " + field + ": " + context);
        }
        return value.asText();
    }

    /**
     * 取 form-urlencoded 必需字段，缺失/为空抛 IllegalStateException
     */
    private String requireFormField(Map<String, String> form, String field, String context) {
        String value = form.get(field);
        if (null == value || value.isEmpty()) {
            throw new IllegalStateException("第三方 OAuth 响应缺少 " + field + ": " + context);
        }
        return value;
    }

    /**
     * URL 参数 encode（UTF-8，空值返回空串）
     */
    private static String urlEncode(String value) {
        if (null == value || value.isEmpty()) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 日志脱敏：仅保留 URL 路径部分，去掉含 secret / access_token 的 query
     */
    private static String stripQuery(String url) {
        int query = url.indexOf('?');
        return query > 0 ? url.substring(0, query) : url;
    }
}
