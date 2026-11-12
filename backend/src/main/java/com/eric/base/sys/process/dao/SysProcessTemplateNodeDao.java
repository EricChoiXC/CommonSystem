package com.eric.base.sys.process.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.sys.process.mapper.SysProcessTemplateNodeMapper;
import com.eric.base.sys.process.model.SysProcessTemplateNode;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 流程模板节点数据访问对象
 *
 * @author Eric
 * @date 2026/8/22
 * @description 与 SysProcessTemplateNodeMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysProcessTemplateNodeDao extends BaseDao<SysProcessTemplateNode, SysProcessTemplateNodeMapper> {

    public SysProcessTemplateNodeDao(SysProcessTemplateNodeMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 按模板查询全部节点（按节点编号升序）
     */
    public List<SysProcessTemplateNode> selectListByTemplateId(String templateId) {
        return getBaseMapper().selectListByTemplateId(templateId);
    }

    /**
     * 删除模板全部节点（编辑全量替换前清空）
     */
    public int deleteByTemplateId(String templateId) {
        return getBaseMapper().deleteByTemplateId(templateId);
    }

    /**
     * 批量插入模板节点
     */
    public int insertBatch(String templateId, List<SysProcessTemplateNode> nodes) {
        return getBaseMapper().insertBatch(templateId, nodes);
    }
}
