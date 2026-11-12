package com.eric.base.sys.i18n.dao;

import com.eric.base.sys.i18n.mapper.SysI18nUserLanguageMapper;
import com.eric.base.sys.i18n.model.SysI18nUserLanguage;
import org.springframework.stereotype.Repository;

/**
 * 多语言-用户语言数据访问对象
 *
 * @author Eric
 * @date 2026/11/5
 * @description 与 SysI18nUserLanguageMapper 一对一对接，不承载业务逻辑。
 *              表以 field_user_id 为主键（无 field_id 公共列），不继承 BaseDao
 *              （先例 SysOrgGroupUserDao：明细表 Dao 为独立类、直接持有 Mapper）；
 *              upsert 的「存在更新 / 不存在插入」编排由 i18n 服务完成。
 */
@Repository
public class SysI18nUserLanguageDao {

    private final SysI18nUserLanguageMapper userLanguageMapper;

    public SysI18nUserLanguageDao(SysI18nUserLanguageMapper userLanguageMapper) {
        this.userLanguageMapper = userLanguageMapper;
    }

    /**
     * 按用户查询语言登记（一人一条；无登记返回 null）
     */
    public SysI18nUserLanguage selectByUserId(String fieldUserId) {
        return userLanguageMapper.selectByUserId(fieldUserId);
    }

    /**
     * 插入用户语言登记
     */
    public int insert(SysI18nUserLanguage model) {
        return userLanguageMapper.insert(model);
    }

    /**
     * 更新用户语言登记
     */
    public int updateByUserId(SysI18nUserLanguage model) {
        return userLanguageMapper.updateByUserId(model);
    }

    /**
     * 已选指定语言的登记改绑为目标语言（D4）
     */
    public int rebindLanguage(String fieldLanguageId, String targetLanguageId) {
        return userLanguageMapper.rebindLanguage(fieldLanguageId, targetLanguageId);
    }
}
