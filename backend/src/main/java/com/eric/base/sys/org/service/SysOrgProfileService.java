package com.eric.base.sys.org.service;

import com.eric.base.common.base.model.Pages;
import com.eric.base.common.setting.properties.CommonSettingProperties;
import com.eric.base.common.setting.service.CommonSettingService;
import com.eric.base.common.utils.PinyinUtil;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.att.dao.SysAttMainDao;
import com.eric.base.sys.att.model.SysAttMain;
import com.eric.base.sys.org.dao.SysOrgUserDao;
import com.eric.base.sys.org.model.SysOrgUser;
import com.eric.base.sys.org.model.SysOrgUserOpenidDo;
import com.eric.base.sys.org.properties.SysOrgProfileProperties;
import com.eric.base.sys.org.request.SysOrgProfileSaveRequest;
import com.eric.base.sys.org.response.SysOrgProfileResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 个人资料页业务服务
 *
 * @author Eric
 * @date 2026/11/11
 * @description 个人资料页（本人自助）业务逻辑（PF2-BE-002，方案 4.2 / 4.3）：
 *              操作对象一律由会话定位（{@link UserUtil#getCurrentUserId()}），接口不收 userId 参数，
 *              从根上排除「A 修改 B」的越权路径。load 组装基础资料（sys_org_user JOIN sys_org_element）、
 *              头像附件ID（sys_att_main 按 model_name + model_id + key 取 field_create_time 降序最新一条）、
 *              openid 反序列化（field_openid JSON 串 → SysOrgUserOpenidDo，失败按全未绑定处理）与
 *              渠道开关实时判定（总开关 system.openid_bind_enable + 渠道凭证齐全，桥接键优先、
 *              common_setting 表回退）。save 校验可编辑字段格式后更新本人资料行，
 *              登录名与密码绝不触碰；用户名变更时经 SysOrgUserDao.update 的双表写链路同步
 *              sys_org_element.field_name，并按 SysOrgElementService#save/update 幂等口径重算拼音两列
 *              （PinyinUtil，后端统一重算、前端不传、不可直改）。第三方账号绑定（PF3-BE-002，
 *              方案 4.4）：buildBindUrl 生成一次性 state 存 Redis（TTL 300 秒）后拼授权页 URL；
 *              handleCallback 会话不可用（302 无 token），操作对象取 state 绑定的 userId，
 *              取删 state → 复验开关/凭证 → 换号 → 查重（计数 > 0 即拒绝，无本人豁免，
 *              查重与写入同事务）→ 覆写 field_openid；unbind 按渠道置空、双渠道全空时
 *              整列置 NULL（双表写注解事务）。
 *              OAuth HTTP 与解析在 {@link OpenidClient}，本类只做业务判定；
 *              数据一律经 Dao 访问，不直连 Mapper。
 */
@Service
public class SysOrgProfileService {

    private static final Logger log = LoggerFactory.getLogger(SysOrgProfileService.class);

    /**
     * 手机号格式（方案 4.2）：非空时校验
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /**
     * 邮箱格式：与 SysOrgUserService.EMAIL_PATTERN 同款
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /**
     * 联系地址长度上限（与表列一致，方案 4.2）
     */
    private static final int ADDRESS_MAX_LENGTH = 500;

    /**
     * 第三方账号绑定渠道（方案 4.4）：请求 channel 合法值
     */
    public static final String CHANNEL_WECHAT = "wechat";
    public static final String CHANNEL_QQ = "qq";

    /**
     * 总开关配置键（system.openid_bind_enable，方案 4.4）：管理员运行时可切，缺失按关闭
     */
    private static final String KEY_OPENID_BIND_ENABLE = "openid_bind_enable";

    /**
     * 应用凭证配置类型（SETTING.openid 段，方案 4.4）：部署级凭证，不入设置页
     */
    private static final String SETTING_TYPE_OPENID = "openid";

    /**
     * Redis state 键前缀（方案 4.4）：一次性防 CSRF/防重放，key = 前缀 + state
     */
    private static final String STATE_KEY_PREFIX = "openid_bind_state:";

    /**
     * state 有效期（方案 4.4）：TTL 300 秒，回调取删（getAndDelete 原子）
     */
    private static final Duration STATE_TTL = Duration.ofSeconds(300);

    /**
     * 回调路径（方案 4.4）：redirect_uri = {redirect_base}/backend/sys/org/profile/openid/callback
     */
    private static final String CALLBACK_PATH = "/backend/sys/org/profile/openid/callback";

    /**
     * 回跳查询（方案 4.4）：302 目标 = {redirect_base}/profile?bind={channel}:{reason}
     */
    private static final String PROFILE_BIND_QUERY = "/profile?bind=";

    /**
     * 绑定时间格式（方案 4.1）：field_openid 内层 bindTime 格式
     */
    private static final DateTimeFormatter BIND_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 应用默认时区（Asia/Shanghai）：生日「不晚于当天」按本地日历日比较，
     * 与 JacksonConfig 无时区字符串解析口径一致（date-only 解析为本地零点）
     */
    private static final ZoneId APP_ZONE = ZoneId.systemDefault();

    private final SysOrgUserDao userDao;

    private final SysAttMainDao attDao;

    private final ObjectMapper objectMapper;

    /**
     * 公共配置服务（总开关与 SETTING.openid 段凭证回退，方案 4.4）：字段注入
     * （先例 SysOrgUserDao#sysOrgElementMapper），保持既有三参构造不变、旧单测零感知；
     * 未装配（单测裸构造）时按未开启/未配置处理
     */
    @Autowired
    private CommonSettingService settingService;

    /**
     * Redis 模板（state 一次性防 CSRF/防重放，方案 4.4）：value 为
     * GenericJackson2JsonRedisSerializer（RedisConfig），存取 LinkedHashMap 结构
     */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 第三方 OAuth HTTP 客户端：只做 HTTP 调用与解析，业务判定在本类
     */
    @Autowired
    private OpenidClient openidClient;

    /**
     * 编程式事务模板（方案 4.4「查重与写入同事务」）：回调写库段经此包裹——
     * handleCallback 内含 Redis 取删与 HTTP 换号，不可整方法 @Transactional（持连接等
     * 第三方超时），且同类自调用注解事务不生效，故写库段用 TransactionTemplate 划界；
     * Spring Boot 数据源存在时自动装配，未装配（单测裸构造）时写库段直接执行（行为同序）
     */
    @Autowired(required = false)
    private TransactionTemplate transactionTemplate;

    /**
     * 凭证桥接键（application.properties 三段式环境变量覆盖，方案 4.4）：
     * Spring Environment 优先，值为空回退 common_setting 表 SETTING.openid 段；
     * 未配置时占位解析为空串；字段注入以便单测反射赋值
     */
    @Value("${common.org.openid.wechat-app-id:}")
    private String wechatAppId;

    @Value("${common.org.openid.wechat-app-secret:}")
    private String wechatAppSecret;

    @Value("${common.org.openid.qq-app-id:}")
    private String qqAppId;

    @Value("${common.org.openid.qq-app-secret:}")
    private String qqAppSecret;

    /** 回调基础地址（如 https://erp.example.com） */
    @Value("${common.org.openid.redirect-base:}")
    private String redirectBase;

    public SysOrgProfileService(SysOrgUserDao userDao, SysAttMainDao attDao,
                                ObjectMapper objectMapper) {
        this.userDao = userDao;
        this.attDao = attDao;
        this.objectMapper = objectMapper;
    }

    /**
     * 加载当前登录用户资料（方案 4.2，GET /backend/sys/org/profile/load）：
     * 基础资料取 sys_org_user JOIN sys_org_element（selectById 的 BaseColumns 已含两表字段），
     * 头像附件ID取该用户「头像」类附件的最新一条（field_create_time 降序取一），未上传为 null；
     * openid 由 field_openid JSON 串反序列化，反序列化失败按全未绑定处理；
     * 渠道开关经总开关（system.openid_bind_enable）与渠道凭证齐全实时判定（PF3-BE-002 接通）。
     *
     * @return 个人资料响应（fieldName/fieldLoginName 等取自两表 JOIN 行）
     */
    public SysOrgProfileResponse load() {
        SysOrgUser user = requireCurrentUser();
        SysOrgProfileResponse response = new SysOrgProfileResponse();
        response.setFieldName(user.getFieldName());
        response.setFieldLoginName(user.getFieldLoginName());
        response.setFieldPhone(user.getFieldPhone());
        response.setFieldEmail(user.getFieldEmail());
        response.setFieldAddress(user.getFieldAddress());
        response.setFieldGender(user.getFieldGender());
        response.setFieldBirthday(user.getFieldBirthday());
        response.setFieldJobStatus(user.getFieldJobStatus());
        response.setFieldAccountStatus(user.getFieldAccountStatus());
        response.setAvatarAttId(latestAvatarAttId(user.getFieldId()));
        response.setOpenid(deserializeOpenid(user.getFieldOpenid()));
        response.setOpenidBindEnabled(isBindEnabled());
        response.setWechatEnabled(isWechatEnabled());
        response.setQqEnabled(isQqEnabled());
        return response;
    }

    /**
     * 保存本人资料（方案 4.2，POST /backend/sys/org/profile/save）：
     * 先做一致性断言（fieldId 必须与当前登录用户一致，防并发换号后脏提交），
     * 再做字段格式校验（用户名非空、手机号/邮箱格式、地址长度、性别取值、生日不晚于当天），
     * 校验通过后读取本人现行资料行，仅覆写请求携带（非 null）的字段后整行更新——
     * XML update 对 field_gender / field_job_status / field_account_status 是无条件更新列，
     * 以现行值回填保证未请求字段不被默认值覆盖。用户名变更时经双表写链路同步 element
     * 名称并重算拼音两列；登录名只读不接收、密码绝不触碰。
     *
     * @param request 保存请求（fieldId 为一致性断言入参，登录名不在请求体内）
     */
    @Transactional
    public void save(SysOrgProfileSaveRequest request) {
        String currentUserId = UserUtil.getCurrentUserId();
        if (null == request || !Objects.equals(request.getFieldId(), currentUserId)) {
            throw new IllegalArgumentException("提交人员与会话用户不一致");
        }
        validate(request);
        SysOrgUser current = requireCurrentUser();

        // 以现行整行为基覆写请求字段再整行 update：无条件更新列（field_gender /
        // field_job_status / field_account_status）保持库中现值，避免被模型默认值覆盖
        SysOrgUser updated = current;
        updated.setFieldUpdaterId(null == currentUserId ? null : currentUserId);
        updated.setFieldUpdateTime(new Date());
        if (null != request.getFieldPhone()) {
            updated.setFieldPhone(request.getFieldPhone());
        }
        if (null != request.getFieldEmail()) {
            updated.setFieldEmail(request.getFieldEmail());
        }
        if (null != request.getFieldAddress()) {
            updated.setFieldAddress(request.getFieldAddress());
        }
        if (null != request.getFieldGender()) {
            updated.setFieldGender(request.getFieldGender());
        }
        if (null != request.getFieldBirthday()) {
            updated.setFieldBirthday(request.getFieldBirthday());
        }
        // fieldLoginName / fieldPassword 绝不触碰：登录名只读（方案 4.2，由管理员在
        // 组织架构人员维护中修改），密码仅经 user/changePwd 接口更新；updated 为
        // selectById 现行行，登录名保持库中现值，且 field_password 本就不在
        // XML update 列集合中，不会被意外重置。
        //
        // 用户名变更同步（关键验收点）：请求用户名与 element 现行 field_name 不同
        // （trim 后比较，空串与 null 视为相等，同值幂等跳过）时，把新名与重算拼音
        // 覆写到 updated 上——SysOrgUserDao.update 是双表写（先经
        // SysOrgElementMapper 更新主表，再经 SysOrgUserMapper 更新扩展表，见
        // SysOrgUserDao#update），element 主表的 field_name / field_pinyin /
        // field_pinyin_short 与审计字段经此一次落地，不会先写后被双写旧名回滚；
        // 拼音口径与 SysOrgElementService#save/update 一致（PinyinUtil 后端统一重算，
        // 前端不传、不可直改）。updatePinyin 只更新拼音两列、不含名称，故不经它。
        if (!Objects.equals(trimToNull(request.getFieldName()), trimToNull(current.getFieldName()))) {
            updated.setFieldName(request.getFieldName());
            updated.setFieldPinyin(PinyinUtil.toPinyin(request.getFieldName()));
            updated.setFieldPinyinShort(PinyinUtil.toPinyinShort(request.getFieldName()));
        }

        userDao.update(updated);
    }

    /**
     * 生成第三方授权页跳转地址（方案 4.4，POST /backend/sys/org/profile/openid/bindUrl）：
     * 渠道合法性 → 总开关 → 渠道凭证齐全 → 回调基础地址，逐项校验失败抛
     * IllegalArgumentException（GlobalExceptionHandler 统一转 BAD_REQUEST）；
     * 全部通过后生成 32 位一次性 state 写 Redis（绑 userId + channel 的
     * LinkedHashMap，TTL 300 秒，回调时取删防重放），再经 OpenidClient 拼接授权页 URL。
     *
     * @param channel 绑定渠道（wechat/qq）
     * @return bindUrl：第三方授权页完整 URL（redirect_uri 已 urlencoded）
     */
    public String buildBindUrl(String channel) {
        String normalized = requireChannel(channel);
        if (!isBindEnabled()) {
            throw new IllegalArgumentException("第三方账号绑定未开启");
        }
        if (CHANNEL_WECHAT.equals(normalized)
                && !(hasText(credentialWechatAppId()) && hasText(credentialWechatAppSecret()))) {
            throw new IllegalArgumentException("微信绑定未配置");
        }
        if (CHANNEL_QQ.equals(normalized)
                && !(hasText(credentialQqAppId()) && hasText(credentialQqAppSecret()))) {
            throw new IllegalArgumentException("QQ绑定未配置");
        }
        String base = resolveRedirectBase();
        if (!hasText(base)) {
            throw new IllegalArgumentException("回调地址未配置");
        }
        String userId = requireCurrentUser().getFieldId();
        String state = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> stateValue = new LinkedHashMap<>();
        stateValue.put("userId", userId);
        stateValue.put("channel", normalized);
        // TTL 300 秒一次性 state：回调时 getAndDelete 原子取删，过期/重放均失效
        redisTemplate.opsForValue().set(STATE_KEY_PREFIX + state, stateValue, STATE_TTL);
        String callbackUri = base + CALLBACK_PATH;
        return CHANNEL_WECHAT.equals(normalized)
                ? openidClient.wechatAuthorizeUrl(credentialWechatAppId(), callbackUri, state)
                : openidClient.qqAuthorizeUrl(credentialQqAppId(), callbackUri, state);
    }

    /**
     * 第三方授权回调处理（方案 4.4，GET /backend/sys/org/profile/openid/callback）：
     * 浏览器 302 跳入、无 Authorization header，会话不可用——操作对象一律取 Redis state
     * 内绑定的 userId（getAndDelete 取删，一次性，过期/伪造均回 expired）。复验总开关
     * （关闭 → disabled，防关闭开关后存量跳转仍可完成绑定）与渠道凭证（缺失 →
     * unconfigured）；code 缺失（第三方错误回跳）与换号失败 → oauth_error；查重计数 > 0
     * 即拒绝（方案 4.4 字面口径，无本人豁免——本人未绑定时不可能持有与他人相同的
     * openid，state 一次性已防重放）→ dup；写 field_openid 覆写对应渠道内层后回跳
     * /profile?bind={channel}:{reason}。
     *
     * @param code  第三方授权码（第三方错误回跳时可能为 null）
     * @param state 一次性 state（须与 bindUrl 阶段写入 Redis 的值一致）
     * @return 302 目标 URL；redirect_base 未配置时返回 null（Controller 转 400）
     */
    public String handleCallback(String code, String state) {
        String base = resolveRedirectBase();
        String bindValue = resolveCallbackBindValue(code, state, base);
        if (null == bindValue || null == base) {
            return null;
        }
        return base + PROFILE_BIND_QUERY + bindValue;
    }

    /**
     * 回调处理归集：按方案 4.4 顺序取删 state → 复验开关/凭证 → 换号 → 查重 → 写库，
     * 每步失败返回对应 bind 值（expired/disabled/unconfigured/oauth_error/dup/success）
     */
    private String resolveCallbackBindValue(String code, String state, String base) {
        Object cached = takeState(state);
        // GenericJackson2JsonRedisSerializer 反序列化回来为 LinkedHashMap 形态
        if (!(cached instanceof Map<?, ?> stateValue) || null == stateValue.get("userId")) {
            return "expired";
        }
        String channel = String.valueOf(stateValue.get("channel"));
        String userId = String.valueOf(stateValue.get("userId"));
        if (!isBindEnabled()) {
            return channel + ":disabled";
        }
        if (!credentialReady(channel)) {
            return channel + ":unconfigured";
        }
        if (!hasText(code)) {
            return channel + ":oauth_error";
        }
        OpenidClient.OpenidProfile profile;
        try {
            profile = CHANNEL_WECHAT.equals(channel)
                    ? openidClient.exchangeWechat(code, credentialWechatAppId(), credentialWechatAppSecret())
                    : openidClient.exchangeQq(code, credentialQqAppId(), credentialQqAppSecret(),
                            base + CALLBACK_PATH);
        } catch (Exception e) {
            // 日志不含 code / secret / access_token，只带渠道与异常摘要
            log.warn("第三方账号信息换取失败：channel={}, 原因：{}", channel, e.getMessage());
            return channel + ":oauth_error";
        }
        // 查重与写入同事务（方案 4.4「查重与写入同事务」/ 6.4 并发窗口口径）：仅把
        // 「查重 → 写库」段划入事务，双表写（SysOrgUserDao.update）中途失败整体回滚，
        // 不会 302 success 而留半更新；Redis 取删与 HTTP 换号留在外围不持连接
        return withTransaction(() -> {
            // 查重（方案 4.4 字面）：计数 > 0 即拒绝，无本人豁免（state 一次性已防重放）
            if (userDao.countDuplicateOpenid(profile.getOpenid()) > 0) {
                return channel + ":dup";
            }
            SysOrgUser user = userDao.selectById(userId);
            if (null == user) {
                // state 有效但用户已不存在（TTL 内被删等极端情况）：按 oauth_error 回跳
                return channel + ":oauth_error";
            }
            saveOpenidBinding(user, channel, profile);
            return channel + ":success";
        });
    }

    /**
     * 覆写 field_openid 对应渠道内层（openid/unionid/nickname/bindTime，方案 4.1）后
     * 整串更新本人资料行：以现行 JSON 反序列化为基覆写单渠道，其余渠道绑定不受影响
     */
    private void saveOpenidBinding(SysOrgUser user, String channel, OpenidClient.OpenidProfile profile) {
        SysOrgUserOpenidDo openidDo = deserializeOpenid(user.getFieldOpenid());
        String bindTime = BIND_TIME_FORMATTER.format(LocalDateTime.now());
        if (CHANNEL_WECHAT.equals(channel)) {
            SysOrgUserOpenidDo.Wechat wechat = new SysOrgUserOpenidDo.Wechat();
            wechat.setOpenid(profile.getOpenid());
            wechat.setUnionid(profile.getUnionid());
            wechat.setNickname(profile.getNickname());
            wechat.setBindTime(bindTime);
            openidDo.setWechat(wechat);
        } else {
            SysOrgUserOpenidDo.Qq qq = new SysOrgUserOpenidDo.Qq();
            qq.setOpenid(profile.getOpenid());
            qq.setNickname(profile.getNickname());
            qq.setBindTime(bindTime);
            openidDo.setQq(qq);
        }
        user.setFieldOpenid(serializeOpenid(openidDo));
        user.setFieldUpdaterId(user.getFieldId());
        user.setFieldUpdateTime(new Date());
        userDao.update(user);
    }

    /**
     * 解绑第三方账号（方案 4.4，POST /backend/sys/org/profile/openid/unbind）：
     * 不限制「至少保留一个绑定方式」（本系统不支持第三方登录，解绑不影响登录能力）；
     * 按渠道置空内层对象，两渠道均未绑定时整列置 NULL（clearOpenid 专用 SQL——
     * XML update 对 field_openid 是 <if> 条件写，落不了 NULL），否则覆写 JSON 整串更新；
     * 解绑未绑定渠道幂等成功（最终状态一致为该渠道置空）；双表写经 @Transactional
     * 包裹（同 save 口径，外部调用经代理生效）。
     *
     * @param channel 绑定渠道（wechat/qq）
     */
    @Transactional
    public void unbind(String channel) {
        String normalized = requireChannel(channel);
        SysOrgUser user = requireCurrentUser();
        SysOrgUserOpenidDo openidDo = deserializeOpenid(user.getFieldOpenid());
        if (CHANNEL_WECHAT.equals(normalized)) {
            openidDo.setWechat(null);
        } else {
            openidDo.setQq(null);
        }
        if (null == openidDo.getWechat() && null == openidDo.getQq()) {
            userDao.clearOpenid(user.getFieldId());
            return;
        }
        user.setFieldOpenid(serializeOpenid(openidDo));
        user.setFieldUpdaterId(UserUtil.getCurrentUserId());
        user.setFieldUpdateTime(new Date());
        userDao.update(user);
    }

    /**
     * 事务内执行回调写库段：TransactionTemplate 自动装配存在时经事务包裹
     * （同类自调用注解事务不生效，故不用注解式），未装配（单测裸构造）时直接执行
     */
    private String withTransaction(Supplier<String> action) {
        if (null == transactionTemplate) {
            return action.get();
        }
        return transactionTemplate.execute(status -> action.get());
    }

    /**
     * 取删 state（方案 4.4「getAndDelete 原子取删」）：优先
     * {@code opsForValue().getAndDelete()}（Redis 服务端 GETDEL 命令，≥6.2）。
     * <ul>
     * <li>低版本 Redis（如 5.0.x Windows 便携版）不识别 GETDEL 会抛
     *     RedisCommandExecutionException（ERR unknown command `GETDEL`）——降级为
     *     GET + DEL 两段取删：非原子（并发重放存在极小竞态窗口，与 6.4 查重并发
     *     口径同等量级、内部系统可接受），换取对存量低版本 Redis 部署的兼容；</li>
     * <li>state 值反序列化失败（键下值被外部改写、序列化格式不兼容等）——回调是
     *     浏览器 302 导航，按方案 4.4「任何失败均 302 带 fail_reason，不返回 JSON
     *     错误页」，统一按 state 不存在（expired）处理，不向浏览器抛 500；</li>
     * </ul>
     * 连接级故障（Redis 宕机）在两次调用中同样失败、异常上抛，不做静默吞没。
     *
     * @param state 回调携带的一次性 state（可为 null，统一落前缀键上取删）
     * @return state 绑定值（LinkedHashMap 形态）；不存在/已过期/已消费/值不可读返回 null
     */
    private Object takeState(String state) {
        String key = STATE_KEY_PREFIX + (null == state ? "" : state);
        try {
            return redisTemplate.opsForValue().getAndDelete(key);
        } catch (DataAccessException | SerializationException e) {
            log.warn("Redis GETDEL 取删 state 失败，降级 GET+DEL（建议升级 Redis ≥6.2）：{}", e.getMessage());
            try {
                Object cached = redisTemplate.opsForValue().get(key);
                if (null != cached) {
                    redisTemplate.delete(key);
                }
                return cached;
            } catch (SerializationException se) {
                // GETDEL 未执行（命令不支持）时 GET 可能读到损坏值：一次性语义按 expired 处理
                log.warn("state 值反序列化失败，按 expired 处理：key={}", key);
                return null;
            }
        }
    }

    /**
     * 凭证解析（方案 4.4 配置分层）：Spring Environment 桥接键优先（application.properties
     * 三段式环境变量覆盖），值为空回退 common_setting 表 SETTING.openid 段（下划线 key）；
     * 返回 trim 后的值，均未配置返回 null；未装配配置服务（单测裸构造）按未配置处理
     */
    private String credential(String bridgeValue, String fieldKey) {
        if (hasText(bridgeValue)) {
            return bridgeValue.trim();
        }
        String value = null == settingService ? null : settingService.getValue(SETTING_TYPE_OPENID, fieldKey);
        return hasText(value) ? value.trim() : null;
    }

    private String credentialWechatAppId() {
        return credential(wechatAppId, "wechat_app_id");
    }

    private String credentialWechatAppSecret() {
        return credential(wechatAppSecret, "wechat_app_secret");
    }

    private String credentialQqAppId() {
        return credential(qqAppId, "qq_app_id");
    }

    private String credentialQqAppSecret() {
        return credential(qqAppSecret, "qq_app_secret");
    }

    /**
     * 渠道凭证是否齐全（app_id 与 app_secret trim 后均非空，方案 4.4）
     */
    private boolean credentialReady(String channel) {
        return CHANNEL_WECHAT.equals(channel)
                ? hasText(credentialWechatAppId()) && hasText(credentialWechatAppSecret())
                : hasText(credentialQqAppId()) && hasText(credentialQqAppSecret());
    }

    /**
     * 回调基础地址（方案 4.4）：桥接键 redirect-base 优先，为空回退 common_setting
     * 表 SETTING.openid.redirect_base；均未配置返回 null；统一去尾斜杠防双斜杠
     */
    private String resolveRedirectBase() {
        String value = hasText(redirectBase) ? redirectBase
                : (null == settingService ? null : settingService.getValue(SETTING_TYPE_OPENID, "redirect_base"));
        if (!hasText(value)) {
            return null;
        }
        String base = value.trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * 渠道合法性校验（方案 4.4）：合法值 wechat/qq（trim 后比较），非法抛
     * IllegalArgumentException("绑定渠道无效")
     */
    private static String requireChannel(String channel) {
        String value = null == channel ? null : channel.trim();
        if (!CHANNEL_WECHAT.equals(value) && !CHANNEL_QQ.equals(value)) {
            throw new IllegalArgumentException("绑定渠道无效");
        }
        return value;
    }

    /**
     * openid 文档序列化：Jackson 对 POJO 序列化实际不会失败，防御性转 IllegalStateException
     */
    private String serializeOpenid(SysOrgUserOpenidDo openidDo) {
        try {
            return objectMapper.writeValueAsString(openidDo);
        } catch (Exception e) {
            throw new IllegalStateException("绑定信息序列化失败", e);
        }
    }

    /**
     * 非空判定（trim 后非空串）
     */
    private static boolean hasText(String value) {
        return null != value && !value.trim().isEmpty();
    }

    /**
     * 保存请求字段校验（顺序即方案 4.2 校验顺序），失败抛 IllegalArgumentException
     * 交 GlobalExceptionHandler 统一转 BaseResponse
     */
    private void validate(SysOrgProfileSaveRequest request) {
        if (StringUtil.isEmpty(request.getFieldName())) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (!StringUtil.isEmpty(request.getFieldPhone())
                && !PHONE_PATTERN.matcher(request.getFieldPhone()).matches()) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        if (!StringUtil.isEmpty(request.getFieldEmail())
                && !EMAIL_PATTERN.matcher(request.getFieldEmail()).matches()) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        if (!StringUtil.isEmpty(request.getFieldAddress())
                && request.getFieldAddress().length() > ADDRESS_MAX_LENGTH) {
            throw new IllegalArgumentException("联系地址长度不能超过500");
        }
        if (null != request.getFieldGender()
                && (request.getFieldGender() < 0 || request.getFieldGender() > 2)) {
            throw new IllegalArgumentException("性别取值无效");
        }
        if (null != request.getFieldBirthday()
                && request.getFieldBirthday().toInstant().atZone(APP_ZONE).toLocalDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("生日不能晚于当天");
        }
    }

    /**
     * 读取当前登录用户资料行，不存在（未登录或会话用户已失效）时抛
     * IllegalArgumentException("当前用户不存在")
     */
    private SysOrgUser requireCurrentUser() {
        String userId = UserUtil.getCurrentUserId();
        SysOrgUser user = userDao.selectById(userId);
        if (null == user) {
            throw new IllegalArgumentException("当前用户不存在");
        }
        return user;
    }

    /**
     * 头像附件ID（方案 4.3）：sys_att_main 按 field_model_name='sys_org_user' +
     * field_model_id=当前用户 + field_key='profile' 过滤，field_create_time 降序取
     * 最新一条的 fieldId；未上传返回 null。附件过滤条件经 SysAttMainDao.selectListSorted，
     * Pages 设 needPage=true/pageNo=1/pageSize=1 只取一条（本类不改附件模块任何文件）。
     * 注意必须携带 fieldEnable=true 条件：selectListSorted 的 field_enable 为条件过滤，
     * 不传则已删除（软删除 field_enable=0）的旧头像也会命中，导致「移除头像后 load
     * 仍返回已删除附件ID」（PF5-INT-001 联调发现）
     */
    private String latestAvatarAttId(String userId) {
        SysAttMain criteria = new SysAttMain();
        criteria.setFieldModelName(SysOrgProfileProperties.ATT_MODEL_NAME);
        criteria.setFieldModelId(userId);
        criteria.setFieldKey(SysOrgProfileProperties.ATT_KEY_PROFILE);
        criteria.setFieldEnable(true);
        Pages pages = new Pages();
        pages.setNeedPage(true);
        pages.setPageNo(1);
        pages.setPageSize(1);
        // 排序字段/方向为 Mapper 白名单值：缺省即 field_create_time 降序
        List<SysAttMain> list = attDao.selectListSorted(criteria, pages, null, null);
        return list.isEmpty() ? null : list.get(0).getFieldId();
    }

    /**
     * openid 反序列化：field_openid 为空按全未绑定处理；JSON 非法时降级为
     * 全未绑定（空对象）并 warn，不让脏数据阻断资料加载
     */
    private SysOrgUserOpenidDo deserializeOpenid(String openidJson) {
        if (StringUtil.isEmpty(openidJson)) {
            return new SysOrgUserOpenidDo();
        }
        try {
            return objectMapper.readValue(openidJson, SysOrgUserOpenidDo.class);
        } catch (Exception e) {
            log.warn("解析 field_openid 失败，按全未绑定处理，fieldId={}, json={}",
                    UserUtil.getCurrentUserId(), openidJson, e);
            return new SysOrgUserOpenidDo();
        }
    }

    /**
     * 第三方账号绑定总开关（system.openid_bind_enable，方案 4.4）：
     * common_setting 缺失或非 true 均按关闭；未装配配置服务（单测裸构造）按关闭
     * （PF3-BE-002 接通）
     */
    protected boolean isBindEnabled() {
        return null != settingService && Boolean.TRUE.equals(settingService.getBooleanValue(
                CommonSettingProperties.TYPE_SYSTEM, KEY_OPENID_BIND_ENABLE));
    }

    /**
     * 微信渠道开放 = 总开关开启且微信凭证齐全（app_id 与 app_secret 均非空，方案 4.4）：
     * 凭证桥接键优先，为空回退 common_setting 表 SETTING.openid 段
     */
    protected boolean isWechatEnabled() {
        return isBindEnabled() && hasText(credentialWechatAppId()) && hasText(credentialWechatAppSecret());
    }

    /**
     * QQ 渠道开放 = 总开关开启且 QQ 凭证齐全（app_id 与 app_secret 均非空，方案 4.4）：
     * 凭证桥接键优先，为空回退 common_setting 表 SETTING.openid 段
     */
    protected boolean isQqEnabled() {
        return isBindEnabled() && hasText(credentialQqAppId()) && hasText(credentialQqAppSecret());
    }

    /**
     * trim 归一：null 与空串视为相等（用户名变更判定的口径）
     */
    private static String trimToNull(String value) {
        if (null == value) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
