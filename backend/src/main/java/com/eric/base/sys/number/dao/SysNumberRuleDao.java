package com.eric.base.sys.number.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.number.mapper.SysNumberRuleMapper;
import com.eric.base.sys.number.model.SysNumberRule;
import com.eric.base.sys.number.request.SysNumberRuleQuery;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * 编号规则数据访问对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 与 SysNumberRuleMapper 一对一对接，不承载业务逻辑；
 *              失效接入项告警查询（selectDistinctModelIds）随 NB2-BE-001 补充，
 *              固定接入取号定位（selectByModel）随 NB2-BE-002 补充，
 *              列表查询、编码查重、切换启用与收尾自检计数随 NB3-BE-002 补充。
 */
@Repository
public class SysNumberRuleDao extends BaseDao<SysNumberRule, SysNumberRuleMapper> {

    public SysNumberRuleDao(SysNumberRuleMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 查询全部规则引用的接入项编码（去重），供启动任务失效接入项告警（NB2-BE-001）。
     *
     * @return 接入项编码列表（field_model_id 非空去重）
     */
    public List<String> selectDistinctModelIds() {
        return getBaseMapper().selectDistinctModelIds();
    }

    /**
     * 固定接入取号定位：按接入项（模型名 + 字段）取规则清单（NB2-BE-002）。
     *
     * @param fieldModelName  业务模型名
     * @param fieldModelField 承接编号的字段名
     * @return 该接入项下全部规则（含停用）
     */
    public List<SysNumberRule> selectByModel(String fieldModelName, String fieldModelField) {
        return getBaseMapper().selectByModel(fieldModelName, fieldModelField);
    }

    /**
     * 规则列表查询（NB3-BE-002）：条件与排序见 Mapper，field_rule 原文返回不解析（A4）。
     *
     * @param query 查询条件（可空字段忽略）
     * @param pages 分页参数（可空，空时全量）
     * @return 规则列表（field_rule 为原文字符串）
     */
    public List<SysNumberRule> selectQueryList(SysNumberRuleQuery query, Pages pages) {
        return getBaseMapper().selectQueryList(query, pages);
    }

    /**
     * 规则列表总数（NB3-BE-002）：条件与 {@link #selectQueryList} 一致，供 metadata.total。
     *
     * @param query 查询条件（可空字段忽略）
     * @return 匹配总数
     */
    public long countQueryList(SysNumberRuleQuery query) {
        return getBaseMapper().countQueryList(query);
    }

    /**
     * 规则编码查重（NB3-BE-002，D4）：按编码取规则。
     *
     * @param fieldCode 规则编码
     * @return 同编码规则；不存在时返回 null
     */
    public SysNumberRule selectByCode(String fieldCode) {
        return getBaseMapper().selectByCode(fieldCode);
    }

    /**
     * 同接入项启用规则计数（NB3-BE-002，R2 收尾自检）：切换启用后应恰为 1（D1）。
     *
     * @param fieldModelId 接入项编码
     * @return 启用中规则数
     */
    public long countEnabledByModel(String fieldModelId) {
        return getBaseMapper().countEnabledByModel(fieldModelId);
    }

    /**
     * 切换启用（NB3-BE-002，D1）：停用同接入项下其他已启用规则（排除自身）。
     *
     * @param fieldModelId   接入项编码
     * @param excludeFieldId 排除的规则 ID（本次启用/新建的规则自身）
     * @param updaterId      修改人 ID
     * @param updateTime     修改时间
     * @return 停用的规则行数
     */
    public int disableOtherEnabledByModel(String fieldModelId, String excludeFieldId,
                                          String updaterId, Date updateTime) {
        return getBaseMapper().disableOtherEnabledByModel(fieldModelId, excludeFieldId, updaterId, updateTime);
    }
}
