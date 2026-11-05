package com.common.system.sys.i18n.service;

import com.common.system.common.utils.StringUtil;
import com.common.system.sys.i18n.dao.SysI18nLanguageDao;
import com.common.system.sys.i18n.dao.SysI18nTranslationDao;
import com.common.system.sys.i18n.model.SysI18nLanguage;
import com.common.system.sys.i18n.model.SysI18nTranslation;
import com.common.system.sys.i18n.response.SysI18nDeployResultVo;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多语言-部署与语言包服务
 *
 * @author Eric
 * @date 2026/11/5
 * @description 部署动作与前端语言包（任务清单 I18N5-BE-001）：
 *              <ul>
 *              <li>deploy：手动触发与启动同规则的「资源 → DB 同步」→ 刷新后端缓存。
 *                  复用 {@link SysI18nSyncService#syncFromResources()}（启动任务与部署
 *                  共用同一同步实现，保证两条入口行为一致）；D2 决策下前端语言包为动态
 *                  接口（{@code /locales/{code}.json}），缓存刷新即生效，无产物落盘；</li>
 *              <li>localePackage：仅输出<b>启用语言</b>的全量翻译（扁平 {key: message}）；
 *                  未启用或未知语言码返回错误，不泄露空包。数据源缓存优先
 *                  （{@link SysI18nCacheService#getMessages}），缓存为空（如启动同步未执行、
 *                  缓存被清）时直接读 DB——语言包由部署后的 DB / 缓存支撑，读路径无副作用
 *                  （不回写缓存，写路径统一由同步 / 维护动作负责）。</li>
 *              </ul>
 */
@Service
public class SysI18nDeployService {

    private final SysI18nSyncService syncService;
    private final SysI18nLanguageDao languageDao;
    private final SysI18nTranslationDao translationDao;
    private final SysI18nCacheService cacheService;

    public SysI18nDeployService(SysI18nSyncService syncService,
                                SysI18nLanguageDao languageDao,
                                SysI18nTranslationDao translationDao,
                                SysI18nCacheService cacheService) {
        this.syncService = syncService;
        this.languageDao = languageDao;
        this.translationDao = translationDao;
        this.cacheService = cacheService;
    }

    /**
     * 执行部署：手动触发与启动同规则的「资源 → DB 同步」→ 刷新后端缓存，
     * 返回同步资源数与各语言翻译条数（部署页面展示用）。
     *
     * @return 同步结果（资源模块数、登记 key 数、各语言翻译条数）
     */
    public SysI18nDeployResultVo deploy() {
        return syncService.syncFromResources();
    }

    /**
     * 前端语言包：语言码对应的扁平翻译 Map（{key: message}）。
     * <p>仅启用语言可取；未启用或未知语言码抛参数错误（{@code /locales/{code}.json}
     * 侧经全局异常处理返回错误响应，不泄露空包）。缓存优先，缓存为空回退 DB 直读。</p>
     *
     * @param code 语言码（如 zh_CN）
     * @return 扁平翻译 Map；启用语言暂无翻译时为空 Map
     */
    public Map<String, String> localePackage(String code) {
        if (StringUtil.isEmpty(code)) {
            throw new IllegalArgumentException("语言码不能为空");
        }
        if (!isEnabledLanguage(code)) {
            throw new IllegalArgumentException("语言未启用或不存在: " + code);
        }
        Map<String, String> messages = cacheService.getMessages(code);
        if (!messages.isEmpty()) {
            return messages;
        }
        // 缓存为空（如启动同步未执行 / 缓存被清）→ 直读 DB；读路径不回写缓存
        Map<String, String> fromDb = new LinkedHashMap<>();
        for (SysI18nTranslation translation : translationDao.selectByLanguage(code)) {
            fromDb.put(translation.getFieldKey(), translation.getFieldMessage());
        }
        return fromDb;
    }

    /**
     * 语言码是否为启用语言（/locales 仅输出启用语言，未启用或未知一律拒绝）。
     */
    private boolean isEnabledLanguage(String code) {
        for (SysI18nLanguage language : languageDao.selectAll()) {
            if (code.equals(language.getFieldCode()) && Boolean.TRUE.equals(language.getFieldEnable())) {
                return true;
            }
        }
        return false;
    }
}
