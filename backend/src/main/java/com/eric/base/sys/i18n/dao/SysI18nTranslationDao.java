package com.eric.base.sys.i18n.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.i18n.mapper.SysI18nTranslationMapper;
import com.eric.base.sys.i18n.model.SysI18nTranslation;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 多语言-翻译数据访问对象
 *
 * @author Eric
 * @date 2026/11/5
 * @description 与 SysI18nTranslationMapper 一对一对接，不承载业务逻辑；
 *              覆盖规则四分支的编排（缺失插入 / 空标记覆盖 / 人工编辑跳过 / 无资源删除）
 *              由启动同步与部署服务（I18N2-BE-002 / I18N5-BE-001）基于本 Dao 语句组合完成。
 */
@Repository
public class SysI18nTranslationDao extends BaseDao<SysI18nTranslation, SysI18nTranslationMapper> {

    public SysI18nTranslationDao(SysI18nTranslationMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 指定语言码的全部翻译（key 升序）
     */
    public List<SysI18nTranslation> selectByLanguage(String fieldLanguage) {
        return getBaseMapper().selectByLanguage(fieldLanguage);
    }

    /**
     * 指定模块 × 语言码的全部翻译（key 升序）
     */
    public List<SysI18nTranslation> selectByModelAndLanguage(String fieldModel, String fieldLanguage) {
        return getBaseMapper().selectByModelAndLanguage(fieldModel, fieldLanguage);
    }

    /**
     * 按 (field_key, field_language) 查询单条翻译
     */
    public SysI18nTranslation selectByKeyAndLanguage(String fieldKey, String fieldLanguage) {
        return getBaseMapper().selectByKeyAndLanguage(fieldKey, fieldLanguage);
    }

    /**
     * 指定模块 × 语言的翻译总数（分页 count）
     */
    public long countByModelAndLanguage(String fieldModel, String fieldLanguage) {
        return getBaseMapper().countByModelAndLanguage(fieldModel, fieldLanguage);
    }

    /**
     * 不存在则插入（覆盖规则分支一：翻译缺失新增；field_update_time 置空）
     */
    public int insertIfAbsent(SysI18nTranslation model) {
        return getBaseMapper().upsertFromResource(model);
    }

    /**
     * 仅当人工编辑标记为空时覆盖文本（覆盖规则分支二）
     */
    public int updateIfNotEdited(SysI18nTranslation model) {
        return getBaseMapper().updateIfNotEdited(model);
    }

    /**
     * 按主键更新翻译文本并写人工编辑标记（field_update_time = now）
     */
    public int updateMessageEdited(String fieldId, String fieldMessage) {
        return getBaseMapper().updateMessageEdited(fieldId, fieldMessage);
    }

    /**
     * 重置：清人工编辑标记并回写资源文件值
     */
    public int resetToResource(String fieldKey, String fieldLanguage, String fieldMessage) {
        return getBaseMapper().resetToResource(fieldKey, fieldLanguage, fieldMessage);
    }

    /**
     * 删除指定语言码的全部翻译（D4 级联）
     */
    public int deleteByLanguage(String fieldLanguage) {
        return getBaseMapper().deleteByLanguage(fieldLanguage);
    }

    /**
     * 按 (field_key, field_language) 删除单条翻译
     */
    public int deleteByKeyAndLanguage(String fieldKey, String fieldLanguage) {
        return getBaseMapper().deleteByKeyAndLanguage(fieldKey, fieldLanguage);
    }

    /**
     * 指定模块 × 语言码的翻译列表（key 升序，分页可选）
     */
    public List<SysI18nTranslation> selectModelLanguageList(String fieldModel, String fieldLanguage, Pages pages) {
        return getBaseMapper().selectModelLanguageList(fieldModel, fieldLanguage, pages);
    }
}
