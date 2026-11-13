package com.eric.base.sys.number.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.sys.number.mapper.SysNumberRuleMapper;
import com.eric.base.sys.number.model.SysNumberRule;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 编号规则数据访问对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 与 SysNumberRuleMapper 一对一对接，不承载业务逻辑；
 *              失效接入项告警查询（selectDistinctModelIds）随 NB2-BE-001 补充，
 *              固定接入取号定位（selectByModel）随 NB2-BE-002 补充，
 *              列表查询、切换启用等扩展方法随 NB3-BE-002 补充。
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
}
