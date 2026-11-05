package com.common.system.sys.i18n.service;

import com.common.system.common.utils.StringUtil;
import com.common.system.sys.i18n.dao.SysI18nLanguageDao;
import com.common.system.sys.i18n.dao.SysI18nUserLanguageDao;
import com.common.system.sys.i18n.model.SysI18nLanguage;
import com.common.system.sys.i18n.model.SysI18nUserLanguage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 多语言-用户语言服务
 *
 * @author Eric
 * @date 2026/11/5
 * @description 用户语言登记（sys_i18n_user_language，一人一条）维护：
 *              <ul>
 *              <li>登录语言登记（D3 契约）：登录 body 携带 fieldLanguage 时由登录入口
 *                  {@code SysOrgUserController#login} 调用 {@link #upsertLanguage}（存在更新、
 *                  不存在插入）；未携带不调用，既有登录零回归；</li>
 *              <li>用户语言更新接口（/backend/sys/i18n/user/language/update）复用同一 upsert；</li>
 *              <li>无效语言码按假设 A4 忽略并回退默认语言，不报错；顶部栏语言切换不写本表
 *                  （仅前端 locale + localStorage 记忆）。</li>
 *              </ul>
 *              org → i18n 单向依赖（风险 R3）：仅登录入口注入本服务，
 *              i18n 模块不反向注入 org service，防循环依赖。
 */
@Service
public class SysI18nUserLanguageService {

    private static final Logger log = LoggerFactory.getLogger(SysI18nUserLanguageService.class);

    private final SysI18nUserLanguageDao userLanguageDao;
    private final SysI18nLanguageDao languageDao;

    public SysI18nUserLanguageService(SysI18nUserLanguageDao userLanguageDao,
                                      SysI18nLanguageDao languageDao) {
        this.userLanguageDao = userLanguageDao;
        this.languageDao = languageDao;
    }

    /**
     * 按语言码 upsert 当前用户语言登记（存在更新、不存在插入；一人一条）。
     * <p>语言码解析：空/未知/停用语言码按 A4 忽略并回退默认语言；语言表暂无默认语言
     * （极端场景）时不写库。登记语言未变化时不产生更新（幂等）。</p>
     *
     * @param fieldUserId   用户 ID（sys_org_user.field_id）
     * @param fieldLanguage 语言码（如 zh_CN、en_US；可空，空时回退默认语言）
     */
    public void upsertLanguage(String fieldUserId, String fieldLanguage) {
        if (StringUtil.isEmpty(fieldUserId)) {
            return;
        }
        SysI18nLanguage language = resolveLanguage(fieldLanguage);
        if (null == language) {
            log.warn("i18n user language upsert skipped: no language resolved, userId={}, requested={}",
                    fieldUserId, fieldLanguage);
            return;
        }
        upsertUserLanguageRow(fieldUserId, language.getFieldId());
    }

    /* ==================== 内部 ==================== */

    /**
     * 解析语言码对应语言行：非空且为启用语言时按语言码取；否则回退默认语言（A4）。
     *
     * @return 语言行；默认语言也不存在时返回 null（调用方忽略不写）
     */
    private SysI18nLanguage resolveLanguage(String fieldLanguage) {
        if (StringUtil.isNotEmpty(fieldLanguage)) {
            SysI18nLanguage requested = languageDao.selectByCode(fieldLanguage);
            if (null != requested && Boolean.TRUE.equals(requested.getFieldEnable())) {
                return requested;
            }
        }
        return languageDao.selectByCode(I18nService.DEFAULT_LANGUAGE);
    }

    /**
     * upsert 用户语言行：无登记插入；登记语言变化更新；未变化幂等跳过。
     */
    private void upsertUserLanguageRow(String fieldUserId, String fieldLanguageId) {
        SysI18nUserLanguage exist = userLanguageDao.selectByUserId(fieldUserId);
        if (null == exist) {
            SysI18nUserLanguage model = new SysI18nUserLanguage();
            model.setFieldUserId(fieldUserId);
            model.setFieldLanguageId(fieldLanguageId);
            userLanguageDao.insert(model);
            return;
        }
        if (fieldLanguageId.equals(exist.getFieldLanguageId())) {
            // 已是目标语言：幂等跳过，避免无谓写
            return;
        }
        SysI18nUserLanguage model = new SysI18nUserLanguage();
        model.setFieldUserId(fieldUserId);
        model.setFieldLanguageId(fieldLanguageId);
        userLanguageDao.updateByUserId(model);
    }
}
