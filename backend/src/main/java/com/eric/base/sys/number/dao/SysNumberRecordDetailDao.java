package com.eric.base.sys.number.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.sys.number.mapper.SysNumberRecordDetailMapper;
import com.eric.base.sys.number.model.SysNumberRecordDetail;
import org.springframework.stereotype.Repository;

/**
 * 编号取号明细数据访问对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 与 SysNumberRecordDetailMapper 一对一对接，不承载业务逻辑；
 *              幂等查询（selectValidByScopeAndModel）随 NB2-BE-002 补充，
 *              删除保护计数（countByScopeKey）随 NB3-BE-002 补充，
 *              明细筛选查询、void 作废等扩展方法随 NB3-BE-005 补充。
 */
@Repository
public class SysNumberRecordDetailDao extends BaseDao<SysNumberRecordDetail, SysNumberRecordDetailMapper> {

    public SysNumberRecordDetailDao(SysNumberRecordDetailMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 幂等预检：按作用域 + 业务单据ID 查未作废明细（方案 §5.3，普通读不加锁）。
     *
     * @param fieldScopeKey 取号作用域键
     * @param fieldModelId  业务单据ID（幂等锚点）
     * @return 最新一条未作废明细；无未作废明细时返回 null
     */
    public SysNumberRecordDetail selectValidByScopeAndModel(String fieldScopeKey, String fieldModelId) {
        return getBaseMapper().selectValidByScopeAndModel(fieldScopeKey, fieldModelId, false);
    }

    /**
     * 幂等二次确认：同查询的锁定读（FOR UPDATE，当前读可见最新提交），
     * 供计数器行锁等待后复核并发同单取号（命中即抛错回滚，不产生同单双编号）。
     *
     * @param fieldScopeKey 取号作用域键
     * @param fieldModelId  业务单据ID（幂等锚点）
     * @return 最新一条未作废明细；无未作废明细时返回 null
     */
    public SysNumberRecordDetail lockValidByScopeAndModel(String fieldScopeKey, String fieldModelId) {
        return getBaseMapper().selectValidByScopeAndModel(fieldScopeKey, fieldModelId, true);
    }

    /**
     * 删除保护计数（NB3-BE-002，D9）：按作用域键统计取号明细行数（含已作废）。
     * 固定接入作用域 = 规则 field_id（明细表无 field_rule_id 列），计数 > 0 拒绝删除。
     *
     * @param fieldScopeKey 取号作用域键
     * @return 该作用域下取号明细行数
     */
    public long countByScopeKey(String fieldScopeKey) {
        return getBaseMapper().countByScopeKey(fieldScopeKey);
    }
}
