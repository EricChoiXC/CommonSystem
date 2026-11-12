package com.eric.base.sys.i18n.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.sys.i18n.mapper.SysI18nLanguageMapper;
import com.eric.base.sys.i18n.model.SysI18nLanguage;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 多语言-语言数据访问对象
 *
 * @author Eric
 * @date 2026/11/5
 * @description 与 SysI18nLanguageMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysI18nLanguageDao extends BaseDao<SysI18nLanguage, SysI18nLanguageMapper> {

    public SysI18nLanguageDao(SysI18nLanguageMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 全部语言（按 field_order 升序、field_code 升序稳定排序）
     */
    public List<SysI18nLanguage> selectAll() {
        return getBaseMapper().selectAll();
    }

    /**
     * 按语言码查询语言
     */
    public SysI18nLanguage selectByCode(String fieldCode) {
        return getBaseMapper().selectByCode(fieldCode);
    }

    /**
     * 当前默认语言
     */
    public SysI18nLanguage selectDefault() {
        return getBaseMapper().selectDefault();
    }

    /**
     * 清除全部默认标记（互斥切换第一步）
     */
    public int clearDefault() {
        return getBaseMapper().clearDefault();
    }

    /**
     * 统计语言总数（启动同步初始化判定）
     */
    public long countAll() {
        return getBaseMapper().countAll();
    }
}
