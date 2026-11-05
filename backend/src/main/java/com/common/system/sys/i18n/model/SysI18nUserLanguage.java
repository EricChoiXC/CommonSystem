package com.common.system.sys.i18n.model;

import com.common.system.common.base.model.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 多语言-用户语言模型
 *
 * @author Eric
 * @date 2026/11/5
 * @description 对应 sys_i18n_user_language（V1_01_040）：用户默认语言登记，
 *              field_user_id 为主键（一人一条），登录成功后 upsert（存在更新、不存在插入）；
 *              顶部栏语言切换不写此表。删除语言时已选该语言的登记改绑当前默认语言（D4，不删行）。
 *              实体继承 BaseModel 仅为满足 Dao/Mapper 泛型约束，不映射公共 field_id 等
 *              列（先例 SysOrgGroupUser）。列清单与附录 A DDL 保持一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysI18nUserLanguage extends BaseModel {

    /** 用户id（sys_org_user.field_id，主键，一人一条） */
    private String fieldUserId;

    /** 语言id（sys_i18n_language.field_id） */
    private String fieldLanguageId;
}
