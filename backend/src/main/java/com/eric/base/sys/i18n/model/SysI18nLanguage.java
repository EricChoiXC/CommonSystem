package com.eric.base.sys.i18n.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 多语言-语言模型
 *
 * @author Eric
 * @date 2026/11/5
 * @description 对应 sys_i18n_language（V1_01_040）：语言清单维护（名称/编号/启用/默认/排序号）。
 *              field_default 全局仅一条，互斥切换（先清后设）由 SysI18nLanguageService 保证；
 *              默认语言禁止删除与停用（口径 A2）。启动任务保证 zh_CN（默认、order 1）与
 *              en_US（order 2）存在。列清单与附录 A DDL 保持一致。
 *              <p>2026/11/17 F19-DB-003 存量标注批三继承对齐（裁决 A：继承对齐表结构）：
 *              原继承 MainModel 与表结构不符（本表仅含 field_enable 一个审计列，无
 *              field_creator_id / field_create_time / field_updater_id / field_update_time
 *              列），改继承 BaseModel 对齐公共列模板；field_code / field_name / field_enable
 *              重声明为业务列 @ColumnField 标注（重声明字段 Java 类型与 MainModel 原声明
 *              一致），列定义与 dev 库 information_schema 逐列一致；其余 MainModel 属性
 *              经全量 grep（java + mappers + test）确认无代码引用，不加兼容字段；
 *              公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_i18n_language", comment = "多语言-语言表",
        indexes = {
                @TableIndex(name = "idx_i18n_language_code", columns = "field_code")})
public class SysI18nLanguage extends BaseModel {

    /** 重声明：语言码（D5 遮蔽 MainModel.fieldCode） */
    @ColumnField(name = "field_code", comment = "语言码（如 zh_CN、en_US）", type = ColumnType.STRING, length = 20)
    private String fieldCode;

    /** 重声明：名称（D5 遮蔽 MainModel.fieldName） */
    @ColumnField(name = "field_name", comment = "名称（如 中文、English）", type = ColumnType.STRING, length = 200)
    private String fieldName;

    /** 重声明：是否启用（表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_enable", comment = "是否启用", type = ColumnType.BOOLEAN, defaultValue = "b'1'")
    private Boolean fieldEnable;

    /** 是否默认语言（全局仅一条；互斥切换由 Service 同事务先清后设） */
    @ColumnField(name = "field_default", comment = "是否默认语言（系统默认中文 zh_CN，全局仅一条）",
            type = ColumnType.BOOLEAN, defaultValue = "b'0'")
    private Boolean fieldDefault;

    /** 排序号（决定翻译矩阵语言列顺序与 /locales 输出顺序） */
    @ColumnField(name = "field_order", comment = "排序号", type = ColumnType.INT, defaultValue = "0")
    private Integer fieldOrder;
}
