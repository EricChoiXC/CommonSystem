package com.common.system.sys.i18n.service;

import com.common.system.common.base.model.Pages;
import com.common.system.common.base.service.BaseService;
import com.common.system.common.utils.StringUtil;
import com.common.system.sys.i18n.dao.SysI18nLanguageDao;
import com.common.system.sys.i18n.dao.SysI18nTranslationDao;
import com.common.system.sys.i18n.dao.SysI18nUserLanguageDao;
import com.common.system.sys.i18n.model.SysI18nLanguage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 多语言-语言维护服务
 *
 * @author Eric
 * @date 2026/11/5
 * @description 语言清单维护（任务清单 I18N3-BE-001，5 接口的业务实现）：
 *              <ul>
 *              <li>list：语言列表（免鉴权接口数据源；登录页下拉仅启用语言，管理页全量）；</li>
 *              <li>save/update：fieldCode 唯一性校验；语言码创建后不可修改
 *                  （被 sys_i18n_translation.field_language / 登记数据引用）；
 *                  field_default 不接受请求写入（仅经 setDefault 互斥切换）；</li>
 *              <li>delete：默认语言禁止删除（A2）；级联 D4——同事务删除该语言全部翻译、
 *                  用户语言登记改绑当前默认语言（不删行）；删除后失效该语言缓存；</li>
 *              <li>setDefault：互斥切换——同事务先清全部默认标记再设新默认，
 *                  全局始终恰一条默认语言；停用语言不可设为默认（默认语言必须启用，A2）。</li>
 *              </ul>
 *              语言增删改后联动清理 / 刷新受影响语言的翻译缓存（{@link SysI18nCacheService}）。
 */
@Service
public class SysI18nLanguageService extends BaseService<SysI18nLanguage, SysI18nLanguageDao> {

    private static final Logger log = LoggerFactory.getLogger(SysI18nLanguageService.class);

    private final SysI18nTranslationDao translationDao;
    private final SysI18nUserLanguageDao userLanguageDao;
    private final SysI18nCacheService cacheService;

    public SysI18nLanguageService(SysI18nLanguageDao languageDao,
                                  SysI18nTranslationDao translationDao,
                                  SysI18nUserLanguageDao userLanguageDao,
                                  SysI18nCacheService cacheService) {
        super(languageDao);
        this.translationDao = translationDao;
        this.userLanguageDao = userLanguageDao;
        this.cacheService = cacheService;
    }

    /**
     * 语言列表：按 field_order 升序、field_code 升序稳定排序。
     * <p>条件均可空（免鉴权 /list 直接全量返回，语言行数少不分页）；
     * 登录页下拉传 fieldEnable=true 仅取启用语言。</p>
     *
     * @param fieldName   名称模糊条件（可空）
     * @param fieldCode   语言码模糊条件（可空）
     * @param fieldEnable 启用状态精确条件（可空）
     * @return 语言列表
     */
    public List<SysI18nLanguage> list(String fieldName, String fieldCode, Boolean fieldEnable) {
        SysI18nLanguage criteria = new SysI18nLanguage();
        criteria.setFieldName(StringUtil.isEmpty(fieldName) ? null : fieldName.trim());
        criteria.setFieldCode(StringUtil.isEmpty(fieldCode) ? null : fieldCode.trim());
        criteria.setFieldEnable(fieldEnable);
        return getBaseDao().selectList(criteria, null);
    }

    /**
     * 新增语言：fieldCode / fieldName 必填；fieldCode 唯一；fieldOrder 缺省接在末位；
     * fieldDefault 不接受请求写入（新增语言一律非默认，默认语言仅经 setDefault 切换）。
     *
     * @param body 语言实体（fieldCode、fieldName、fieldEnable?、fieldOrder?）
     * @return 保存后的语言行
     */
    @Transactional
    public SysI18nLanguage save(SysI18nLanguage body) {
        requireBody(body);
        String code = body.getFieldCode().trim();
        requireCodeFormat(code);
        if (null != getBaseDao().selectByCode(code)) {
            throw new IllegalArgumentException("语言码已存在: " + code);
        }
        SysI18nLanguage model = new SysI18nLanguage();
        model.setFieldCode(code);
        model.setFieldName(body.getFieldName().trim());
        // field_default 不接受请求写入：新增语言一律非默认
        model.setFieldDefault(false);
        model.setFieldEnable(null == body.getFieldEnable() ? Boolean.TRUE : body.getFieldEnable());
        model.setFieldOrder(null == body.getFieldOrder() ? nextOrder() : body.getFieldOrder());
        getBaseDao().insert(model);
        log.info("i18n language saved: code={}, name={}", code, model.getFieldName());
        return model;
    }

    /**
     * 编辑语言：按 fieldId 更新名称 / 启用状态 / 排序号。
     * <p>语言码（fieldCode）创建后不可修改（被翻译与用户语言登记引用，改码会导致数据失配）；
     * field_default 不接受请求写入（仅经 setDefault 切换）；
     * 默认语言禁止停用（A2）；更新后联动刷新 / 失效该语言缓存。</p>
     *
     * @param body 语言实体（fieldId 必填；fieldName / fieldEnable / fieldOrder 可选）
     * @return 更新后的语言行
     */
    @Transactional
    public SysI18nLanguage update(SysI18nLanguage body) {
        if (null == body) {
            throw new IllegalArgumentException("语言实体不能为空");
        }
        if (StringUtil.isEmpty(body.getFieldId())) {
            throw new IllegalArgumentException("fieldId 不能为空");
        }
        SysI18nLanguage exist = getBaseDao().selectById(body.getFieldId());
        if (null == exist) {
            throw new IllegalArgumentException("语言不存在");
        }
        if (Boolean.FALSE.equals(body.getFieldEnable()) && Boolean.TRUE.equals(exist.getFieldDefault())) {
            // A2：默认语言禁止停用
            throw new IllegalArgumentException("默认语言禁止停用");
        }
        SysI18nLanguage model = new SysI18nLanguage();
        model.setFieldId(body.getFieldId());
        model.setFieldName(body.getFieldName());
        model.setFieldEnable(body.getFieldEnable());
        model.setFieldOrder(body.getFieldOrder());
        getBaseDao().update(model);
        refreshCacheAfterUpdate(exist.getFieldCode(), body.getFieldEnable());
        return getBaseDao().selectById(body.getFieldId());
    }

    /**
     * 删除语言：默认语言禁止删除（A2）；级联 D4（同事务）——
     * 删除该语言全部翻译、已选该语言的用户登记改绑当前默认语言（不删行）；删除后失效该语言缓存。
     *
     * @param fieldId 语言 ID
     */
    @Transactional
    public void delete(String fieldId) {
        if (StringUtil.isEmpty(fieldId)) {
            throw new IllegalArgumentException("fieldId 不能为空");
        }
        SysI18nLanguage exist = getBaseDao().selectById(fieldId);
        if (null == exist) {
            throw new IllegalArgumentException("语言不存在");
        }
        if (Boolean.TRUE.equals(exist.getFieldDefault())) {
            // A2：默认语言禁止删除
            throw new IllegalArgumentException("默认语言禁止删除");
        }
        // D4 级联一：删除该语言全部翻译
        translationDao.deleteByLanguage(exist.getFieldCode());
        // D4 级联二：已选该语言的登记改绑当前默认语言（不删行）
        SysI18nLanguage defaultLanguage = getBaseDao().selectDefault();
        if (null != defaultLanguage && StringUtil.isNotEmpty(defaultLanguage.getFieldId())) {
            userLanguageDao.rebindLanguage(exist.getFieldId(), defaultLanguage.getFieldId());
        }
        getBaseDao().deleteById(fieldId);
        cacheService.evictLanguage(exist.getFieldCode());
        log.info("i18n language deleted: code={}, translations purged, registrations rebound to default",
                exist.getFieldCode());
    }

    /**
     * 设默认语言：互斥切换——同事务先清全部默认标记再设新默认，全局始终恰一条默认语言。
     * <p>停用语言不可设为默认（A2：默认语言必须启用）；已是默认语言时幂等返回。</p>
     *
     * @param fieldId 目标语言 ID
     * @return 切换后的默认语言行
     */
    @Transactional
    public SysI18nLanguage setDefault(String fieldId) {
        if (StringUtil.isEmpty(fieldId)) {
            throw new IllegalArgumentException("fieldId 不能为空");
        }
        SysI18nLanguage exist = getBaseDao().selectById(fieldId);
        if (null == exist) {
            throw new IllegalArgumentException("语言不存在");
        }
        if (Boolean.FALSE.equals(exist.getFieldEnable())) {
            throw new IllegalArgumentException("停用语言不可设为默认");
        }
        if (Boolean.TRUE.equals(exist.getFieldDefault())) {
            // 幂等：已是默认语言直接返回
            return exist;
        }
        // 互斥切换：先清旧默认，再设新默认（同事务保证全局恰一条默认语言）
        getBaseDao().clearDefault();
        SysI18nLanguage update = new SysI18nLanguage();
        update.setFieldId(fieldId);
        update.setFieldDefault(true);
        getBaseDao().update(update);
        log.info("i18n default language switched to {}", exist.getFieldCode());
        return getBaseDao().selectById(fieldId);
    }

    /* ==================== 内部 ==================== */

    /**
     * 基础校验：实体非空、语言码与名称必填。
     */
    private void requireBody(SysI18nLanguage body) {
        if (null == body) {
            throw new IllegalArgumentException("语言实体不能为空");
        }
        if (StringUtil.isEmpty(body.getFieldCode())) {
            throw new IllegalArgumentException("fieldCode 不能为空");
        }
        if (StringUtil.isEmpty(body.getFieldName())) {
            throw new IllegalArgumentException("fieldName 不能为空");
        }
    }

    /**
     * 语言码格式校验：字母数字与下划线（如 zh_CN、en_US），防止 URL 路径注入
     * （语言码同时用作 /locales/{code}.json 路径段与资源文件后缀）。
     */
    private void requireCodeFormat(String code) {
        if (!code.matches("[A-Za-z0-9_]{1,20}")) {
            throw new IllegalArgumentException("语言码仅允许字母、数字与下划线: " + code);
        }
    }

    /**
     * 末位排序号：现有最大 field_order + 1（空表从 1 起）。
     */
    private int nextOrder() {
        int max = 0;
        for (SysI18nLanguage language : getBaseDao().selectAll()) {
            max = Math.max(max, null == language.getFieldOrder() ? 0 : language.getFieldOrder());
        }
        return max + 1;
    }

    /**
     * 编辑后缓存联动：停用 → 失效该语言缓存；启用/其他字段变化 → 刷新该语言缓存。
     * （启用语言集合决定 /locales 输出与矩阵语言列，停用语言不再对外提供。）
     */
    private void refreshCacheAfterUpdate(String code, Boolean newEnable) {
        if (Boolean.FALSE.equals(newEnable)) {
            cacheService.evictLanguage(code);
            return;
        }
        cacheService.cacheTranslations(List.of(code));
    }
}
