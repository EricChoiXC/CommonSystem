package com.common.system.sys.i18n.service;

import com.common.system.common.utils.StringUtil;
import com.common.system.sys.i18n.dao.SysI18nLanguageDao;
import com.common.system.sys.i18n.model.SysI18nLanguage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

/**
 * 多语言读取服务
 *
 * <p>供各模块服务类读取翻译文案的统一入口（任务清单 I18N2-BE-001）：</p>
 * <ul>
 * <li>{@link #message(String, String, Object...)}：按 key + 语言码读取翻译文本，
 *     缺语言回退默认语言、缺 key 回退 key 本身；支持 {@link MessageFormat} 风格占位符参数
 *     （如 {@code {0}}、{@code {1,number}}）；</li>
 * <li>{@link #defaultLanguage()}：解析当前默认语言码（无记录回退系统默认 zh_CN）。</li>
 * </ul>
 * <p>读取链路：缓存（Redis 优先 + 进程内兜底，见 {@link SysI18nCacheService}）→
 * 默认语言回退 → key 兜底。缓存缺失该语言条目时按需从 DB 懒加载一次
 * （如新增语言后未执行部署动作即调用 message 的过渡场景），避免缓存穿透。</p>
 *
 * @author Eric
 * @date 2026/11/5
 */
@Service
public class I18nService {

    private static final Logger log = LoggerFactory.getLogger(I18nService.class);

    /** 系统默认语言码（启动任务初始语言保证：zh_CN 默认、order 1） */
    public static final String DEFAULT_LANGUAGE = "zh_CN";

    private final SysI18nCacheService cacheService;
    private final SysI18nLanguageDao languageDao;

    public I18nService(SysI18nCacheService cacheService, SysI18nLanguageDao languageDao) {
        this.cacheService = cacheService;
        this.languageDao = languageDao;
    }

    /**
     * 读取翻译文本：缺语言回退默认语言，缺 key 回退 key 本身。
     *
     * @param key      资源关键字（如 org.user.save）
     * @param language 语言码（如 zh_CN；空时使用默认语言）
     * @param args     占位符参数（MessageFormat 风格；空时不做格式化）
     * @return 翻译文本；两级回退后仍缺失时返回 key 本身
     */
    public String message(String key, String language, Object... args) {
        String text = resolveMessage(key, language);
        if (null == args || args.length == 0 || StringUtil.isEmpty(text)) {
            return text;
        }
        try {
            return new MessageFormat(text).format(args);
        } catch (IllegalArgumentException e) {
            // 文案含非法占位符（如单引号规则冲突）时回退原文，不因格式化失败阻断业务
            log.warn("failed to format i18n message, key={}, language={}", key, language, e);
            return text;
        }
    }

    /**
     * 当前默认语言码（如 zh_CN）。
     * <p>查 DB（默认语言由 setDefault 互斥维护，保证实时性）；无默认语言记录时
     * 回退系统默认 zh_CN（与启动任务初始语言保证一致）。</p>
     *
     * @return 默认语言码；异常兜底 zh_CN
     */
    public String defaultLanguage() {
        try {
            SysI18nLanguage language = languageDao.selectDefault();
            if (null != language && StringUtil.isNotEmpty(language.getFieldCode())) {
                return language.getFieldCode();
            }
        } catch (Exception e) {
            log.warn("failed to resolve default language, fallback to {}", DEFAULT_LANGUAGE, e);
        }
        return DEFAULT_LANGUAGE;
    }

    /* ==================== 内部 ==================== */

    /**
     * 两级回退解析翻译文本（默认语言在本次解析内只解析一次）。
     */
    private String resolveMessage(String key, String language) {
        if (StringUtil.isEmpty(key)) {
            return "";
        }
        String fallback = defaultLanguage();
        String target = StringUtil.isNotEmpty(language) ? language : fallback;
        String text = messageFromCache(key, target);
        if (StringUtil.isEmpty(text) && !target.equals(fallback)) {
            // 目标语言无此 key：回退默认语言
            text = messageFromCache(key, fallback);
        }
        if (StringUtil.isEmpty(text)) {
            // 两级回退后仍缺失：返回 key 本身（便于现场发现漏翻）
            return key;
        }
        return text;
    }

    /**
     * 从缓存读取单条翻译；缓存无该语言条目时按需从 DB 懒加载一次。
     *
     * @return 翻译文本；无此 key 返回 null（由调用方走回退）
     */
    private String messageFromCache(String key, String language) {
        Map<String, String> messages = cacheService.getMessages(language);
        if (messages.containsKey(key)) {
            return messages.get(key);
        }
        if (messages.isEmpty()) {
            // 缓存无该语言条目（如新语言未部署）：懒加载一次，避免缓存穿透
            cacheService.cacheTranslations(List.of(language));
            messages = cacheService.getMessages(language);
            if (messages.containsKey(key)) {
                return messages.get(key);
            }
        }
        return null;
    }
}
