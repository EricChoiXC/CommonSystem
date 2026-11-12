package com.eric.base.sys.i18n.mapper;

import com.eric.base.sys.i18n.model.SysI18nUserLanguage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 多语言-用户语言 Mapper
 *
 * @author Eric
 * @date 2026/11/5
 * @description 与 SysI18nUserLanguageMapper.xml 同名对应，一对一操作 sys_i18n_user_language。
 *              表以 field_user_id 为主键（一人一条），无 field_id 公共列，
 *              故不继承 IBaseMapper（接口仅暴露 upsert 语义所需的语句；
 *              先例 SysNotifyTodoTargetMapper 收敛关联表语句于所属模块 Mapper）。
 */
@Mapper
public interface SysI18nUserLanguageMapper {

    /**
     * 按用户查询语言登记（一人一条；无登记返回 null）
     */
    SysI18nUserLanguage selectByUserId(@Param("fieldUserId") String fieldUserId);

    /**
     * 插入用户语言登记（upsert「不存在则插入」分支）
     */
    int insert(@Param("model") SysI18nUserLanguage model);

    /**
     * 更新用户语言登记（upsert「存在则更新」分支）
     */
    int updateByUserId(@Param("model") SysI18nUserLanguage model);

    /**
     * 已选指定语言的登记改绑为目标语言（删除语言级联 D4：更新行不删行）
     */
    int rebindLanguage(@Param("fieldLanguageId") String fieldLanguageId,
                       @Param("targetLanguageId") String targetLanguageId);
}
