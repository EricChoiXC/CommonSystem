package com.eric.base.sys.i18n.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
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
 *              <p>2026/11/17 F19-DB-004 存量标注批四：类级 @TableSource + 业务列
 *              @ColumnField 注解纳管（primaryKeyColumns 显式声明单列主键 field_user_id，
 *              引擎不再内建 field_id），列定义与 dev 库 information_schema 逐列一致；
 *              该表无公共列模板内建列、无二级索引。实体继承 BaseModel 仅为满足
 *              Dao/Mapper 泛型约束（先例 SysOrgGroupUser）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_i18n_user_language", comment = "多语言-用户语言表", primaryKeyColumns = {"field_user_id"})
public class SysI18nUserLanguage extends BaseModel {

    /** 用户id（sys_org_user.field_id，主键，一人一条） */
    @ColumnField(name = "field_user_id", comment = "用户id（sys_org_user.field_id）", type = ColumnType.STRING, length = 32)
    private String fieldUserId;

    /** 语言id（sys_i18n_language.field_id） */
    @ColumnField(name = "field_language_id", comment = "语言id（sys_i18n_language.field_id）", type = ColumnType.STRING, length = 32)
    private String fieldLanguageId;
}
