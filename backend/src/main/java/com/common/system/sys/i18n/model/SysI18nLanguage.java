package com.common.system.sys.i18n.model;

import com.common.system.common.base.model.MainModel;
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
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysI18nLanguage extends MainModel {

    /** 是否默认语言（全局仅一条；互斥切换由 Service 同事务先清后设） */
    private Boolean fieldDefault;

    /** 排序号（决定翻译矩阵语言列顺序与 /locales 输出顺序） */
    private Integer fieldOrder;
}
