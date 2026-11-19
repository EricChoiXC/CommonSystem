package com.eric.base.sys.process.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.sys.process.mapper.SysProcessExampleLineMapper;
import com.eric.base.sys.process.model.SysProcessExampleLine;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 流程实例连线数据访问对象
 *
 * @author Eric
 * @date 2026/11/19
 * @description 与 SysProcessExampleLineMapper 一对一对接，不承载业务逻辑；
 *              实例创建时「节点快照 + 连线快照」由实例 Service 在同一事务内编排写入，
 *              连线快照创建后不可变，正常流程不做更新与删除。
 */
@Repository
public class SysProcessExampleLineDao extends BaseDao<SysProcessExampleLine, SysProcessExampleLineMapper> {

    public SysProcessExampleLineDao(SysProcessExampleLineMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 按实例查询全部连线（按起始节点键、分支序号升序）
     */
    public List<SysProcessExampleLine> selectListByExampleId(String exampleId) {
        return getBaseMapper().selectListByExampleId(exampleId);
    }

    /**
     * 按「实例 + 起始节点键」查询候选出边
     */
    public List<SysProcessExampleLine> selectListByExampleIdAndFromKey(String exampleId, String fromKey) {
        return getBaseMapper().selectListByExampleIdAndFromKey(exampleId, fromKey);
    }

    /**
     * 批量插入实例连线（创建实例时从模板连线全量快照复制）
     */
    public int insertBatch(String exampleId, List<SysProcessExampleLine> lines) {
        return getBaseMapper().insertBatch(exampleId, lines);
    }

    /**
     * 删除实例全部连线（仅供实例重建/异常清理，正常流程不调用）
     */
    public int deleteByExampleId(String exampleId) {
        return getBaseMapper().deleteByExampleId(exampleId);
    }
}
