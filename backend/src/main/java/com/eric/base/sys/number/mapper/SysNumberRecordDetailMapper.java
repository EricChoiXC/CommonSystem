package com.eric.base.sys.number.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.number.model.SysNumberRecordDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 编号取号明细数据访问映射
 *
 * @author Eric
 * @date 2026/11/13
 * @description 与 resources/mappers/sys/number/SysNumberRecordDetailMapper.xml 一对一对接，
 *              只做 SQL 映射，不承载业务逻辑；幂等查询（selectValidByScopeAndModel）
 *              随 NB2-BE-002 补充，删除保护计数（countByScopeKey）随 NB3-BE-002 补充，
 *              明细筛选查询与 void 作废更新随 NB3-BE-005 补充。
 */
@Mapper
public interface SysNumberRecordDetailMapper extends IBaseMapper<SysNumberRecordDetail> {

    /**
     * 删除保护计数（NB3-BE-002，D9）：按作用域键统计取号明细行数。
     * 固定接入作用域 = 规则 field_id（明细表无 field_rule_id 列，方案 §5.1），
     * 计数 > 0 时规则拒绝删除（CRM_NUMBER_RULE_IN_USE），仅可停用。
     *
     * @param fieldScopeKey 取号作用域键
     * @return 该作用域下取号明细行数（含已作废）
     */
    long countByScopeKey(@Param("fieldScopeKey") String fieldScopeKey);

    /**
     * 幂等查询（NB2-BE-002，方案 §5.3）：按作用域 + 业务单据ID 查未作废明细，
     * 命中返回原编号（reused=true）不推进计数器；走 idx_number_record_detail_model 索引。
     *
     * @param fieldScopeKey 取号作用域键
     * @param fieldModelId  业务单据ID（幂等锚点）
     * @param lockForUpdate true 加 FOR UPDATE 锁定读（当前读，可见最新提交，
     *                      供计数器行锁等待后的幂等二次确认）；false 普通读（幂等预检，
     *                      不加锁避免同单并发取号在明细索引上的间隙锁死锁）
     * @return 最新一条未作废明细；无未作废明细时返回 null
     */
    SysNumberRecordDetail selectValidByScopeAndModel(@Param("fieldScopeKey") String fieldScopeKey,
                                                     @Param("fieldModelId") String fieldModelId,
                                                     @Param("lockForUpdate") boolean lockForUpdate);
}
