package com.common.system.sys.i18n.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.i18n.model.SysI18nTranslation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 多语言-翻译 Mapper
 *
 * @author Eric
 * @date 2026/11/5
 * @description 与 SysI18nTranslationMapper.xml 同名对应，一对一操作 sys_i18n_translation。
 *              (field_key, field_language) 业务唯一由代码层保证、不建唯一约束；
 *              field_update_time 为「人工编辑标记」（非空 = 人工已编辑，同步不覆盖）。
 */
@Mapper
public interface SysI18nTranslationMapper extends IBaseMapper<SysI18nTranslation> {

    /**
     * 指定语言码的全部翻译（key 升序），供缓存全量装载与 /locales 输出
     */
    List<SysI18nTranslation> selectByLanguage(@Param("fieldLanguage") String fieldLanguage);

    /**
     * 指定模块 × 语言码的全部翻译（key 升序），供翻译矩阵列组装
     */
    List<SysI18nTranslation> selectByModelAndLanguage(@Param("fieldModel") String fieldModel,
                                                      @Param("fieldLanguage") String fieldLanguage);

    /**
     * 按 (field_key, field_language) 查询单条翻译（覆盖规则分支判定与重置定位用）
     */
    SysI18nTranslation selectByKeyAndLanguage(@Param("fieldKey") String fieldKey,
                                              @Param("fieldLanguage") String fieldLanguage);

    /**
     * 指定模块 × 语言的翻译总数（与 selectByModelAndLanguage 口径一致，分页 count 用）
     */
    long countByModelAndLanguage(@Param("fieldModel") String fieldModel,
                                 @Param("fieldLanguage") String fieldLanguage);

    /**
     * 按 (field_key, field_language) 覆盖规则同步写入：不存在插入（field_update_time 置空）、
     * 存在且 field_update_time IS NULL 时覆盖文本、非空跳过由调用方判定后执行本语句更新。
     */
    int upsertFromResource(@Param("model") SysI18nTranslation model);

    /**
     * 按资源同步覆盖：仅当 field_update_time IS NULL 时覆盖文本（人工编辑过的跳过）。
     * 与 upsertFromResource 拆分：本语句仅对已存在记录做条件更新。
     */
    int updateIfNotEdited(@Param("model") SysI18nTranslation model);

    /**
     * 批量按主键更新翻译文本并写人工编辑标记（field_update_time = now），
     * 供翻译维护批量提交
     */
    int updateMessageEdited(@Param("fieldId") String fieldId,
                            @Param("fieldMessage") String fieldMessage);

    /**
     * 重置：清 field_update_time 并回写资源文件值（按 (field_key, field_language) 定位）
     */
    int resetToResource(@Param("fieldKey") String fieldKey,
                        @Param("fieldLanguage") String fieldLanguage,
                        @Param("fieldMessage") String fieldMessage);

    /**
     * 删除指定语言码的全部翻译（删除语言级联 D4）
     */
    int deleteByLanguage(@Param("fieldLanguage") String fieldLanguage);

    /**
     * 按 (field_key, field_language) 删除单条翻译（重置时该语言无资源文件值的删插语义）
     */
    int deleteByKeyAndLanguage(@Param("fieldKey") String fieldKey,
                               @Param("fieldLanguage") String fieldLanguage);

    /**
     * 指定模块 × 语言码的翻译列表（key 升序，分页可选）
     */
    List<SysI18nTranslation> selectModelLanguageList(@Param("fieldModel") String fieldModel,
                                                     @Param("fieldLanguage") String fieldLanguage,
                                                     @Param("pages") Pages pages);
}
