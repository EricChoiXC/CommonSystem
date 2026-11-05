package com.common.system.sys.i18n.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.sys.i18n.model.SysI18nLanguage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 多语言-语言 Mapper
 *
 * @author Eric
 * @date 2026/11/5
 * @description 与 SysI18nLanguageMapper.xml 同名对应，一对一操作 sys_i18n_language。
 */
@Mapper
public interface SysI18nLanguageMapper extends IBaseMapper<SysI18nLanguage> {

    /**
     * 全部语言（按 field_order 升序、field_code 升序稳定排序），供语言管理列表与
     * 登录页/顶部栏语言下拉（仅启用语言由 enableOnly 过滤）复用
     */
    List<SysI18nLanguage> selectAll();

    /**
     * 按语言码查询语言（field_code 唯一性校验与启动同步使用）
     */
    SysI18nLanguage selectByCode(@Param("fieldCode") String fieldCode);

    /**
     * 当前默认语言（全局恰一条；无默认时返回 null，启动同步据此保证初始默认语言）
     */
    SysI18nLanguage selectDefault();

    /**
     * 清除全部默认标记（setDefault 互斥切换第一步，与设新默认同事务）
     */
    int clearDefault();

    /**
     * 统计语言总数（启动同步「无记录时初始化 zh_CN + en_US」的判定条件）
     */
    long countAll();
}
