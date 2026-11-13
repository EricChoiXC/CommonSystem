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
 *              随 NB2-BE-002 补充，明细筛选查询与 void 作废更新随 NB3-BE-005 补充。
 */
@Mapper
public interface SysNumberRecordDetailMapper extends IBaseMapper<SysNumberRecordDetail> {

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
