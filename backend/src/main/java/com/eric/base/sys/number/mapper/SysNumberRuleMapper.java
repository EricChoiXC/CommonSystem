package com.eric.base.sys.number.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.number.model.SysNumberRule;
import com.eric.base.sys.number.request.SysNumberRuleQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 编号规则数据访问映射
 *
 * @author Eric
 * @date 2026/11/13
 * @description 与 resources/mappers/sys/number/SysNumberRuleMapper.xml 一对一对接，
 *              只做 SQL 映射，不承载业务逻辑；失效接入项告警查询（selectDistinctModelIds）
 *              随 NB2-BE-001 补充，固定接入取号定位（selectByModel）随 NB2-BE-002 补充，
 *              列表查询（selectQueryList/countQueryList）、编码查重（selectByCode）、
 *              切换启用（disableOtherEnabledByModel）与收尾自检计数（countEnabledByModel）
 *              随 NB3-BE-002 补充。
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

    /**
     * 规则列表查询（NB3-BE-002，方案 §3.2 rule/list）：fieldName / fieldCode 模糊、
     * fieldModelId / fieldModelField 精确、fieldEnable 精确；field_rule 原文返回不解析（A4）；
     * 排序固定创建时间降序；pages.needPage=false 时返回全部匹配结果。
     *
     * @param query 查询条件（可空字段忽略）
     * @param pages 分页参数（可空，空时全量）
     * @return 规则列表（field_rule 为原文字符串）
     */
    List<SysNumberRule> selectQueryList(@Param("query") SysNumberRuleQuery query, @Param("pages") Pages pages);

    /**
     * 规则列表总数（NB3-BE-002）：查询条件与 {@link #selectQueryList} 完全一致，
     * 供响应 metadata.total 使用。
     *
     * @param query 查询条件（可空字段忽略）
     * @return 匹配总数
     */
    long countQueryList(@Param("query") SysNumberRuleQuery query);

    /**
     * 规则编码查重（NB3-BE-002，D4 Service 层查重先例 i18n）：按编码取规则。
     *
     * @param fieldCode 规则编码
     * @return 同编码规则；不存在时返回 null
     */
    SysNumberRule selectByCode(@Param("fieldCode") String fieldCode);

    /**
     * 同接入项启用规则计数（NB3-BE-002，切换启用收尾自检 R2）：
     * save / enable 切换后该计数应恰为 1（D1「同接入项至多一条启用」不变式）。
     *
     * @param fieldModelId 接入项编码
     * @return 启用中规则数
     */
    long countEnabledByModel(@Param("fieldModelId") String fieldModelId);

    /**
     * 切换启用（NB3-BE-002，D1）：停用同接入项下其他已启用规则（排除本次启用的规则自身），
     * 与自身置启用同事务执行；同步更新审计字段（对齐 updateEnable 先例）。
     *
     * @param fieldModelId   接入项编码
     * @param excludeFieldId 排除的规则 ID（本次启用/新建的规则自身）
     * @param updaterId      修改人 ID
     * @param updateTime     修改时间
     * @return 停用的规则行数
     */
    int disableOtherEnabledByModel(@Param("fieldModelId") String fieldModelId,
                                   @Param("excludeFieldId") String excludeFieldId,
                                   @Param("updaterId") String updaterId,
                                   @Param("updateTime") Date updateTime);
}
