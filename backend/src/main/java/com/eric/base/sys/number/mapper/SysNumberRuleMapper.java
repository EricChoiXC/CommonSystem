package com.eric.base.sys.number.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.number.model.SysNumberRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 编号规则数据访问映射
 *
 * @author Eric
 * @date 2026/11/13
 * @description 与 resources/mappers/sys/number/SysNumberRuleMapper.xml 一对一对接，
 *              只做 SQL 映射，不承载业务逻辑；失效接入项告警查询（selectDistinctModelIds）
 *              随 NB2-BE-001 补充，固定接入取号定位（selectByModel）随 NB2-BE-002 补充，
 *              列表查询分支与切换启用等业务 SQL 随 NB3-BE-002 补充。
 */
@Mapper
public interface SysNumberRuleMapper extends IBaseMapper<SysNumberRule> {

    /**
     * 查询全部规则引用的接入项编码（去重），供启动任务失效接入项告警（NB2-BE-001）。
     *
     * @return 接入项编码列表（field_model_id 非空去重）
     */
    List<String> selectDistinctModelIds();

    /**
     * 固定接入取号定位（NB2-BE-002）：按接入项（模型名 + 字段）取规则清单，
     * 由此区分「无规则（CRM_NUMBER_RULE_NOT_FOUND）」与「无启用规则（CRM_NUMBER_RULE_DISABLED）」；
     * 同接入项启用规则唯一由 save/enable 切换机制保证（D1），走 idx_number_rule_model 索引。
     *
     * @param fieldModelName 业务模型名（sys_number_rule.field_model_name）
     * @param fieldModelField 承接编号的字段名（sys_number_rule.field_model_field）
     * @return 该接入项下全部规则（含停用）
     */
    List<SysNumberRule> selectByModel(@Param("fieldModelName") String fieldModelName,
                                      @Param("fieldModelField") String fieldModelField);
}
