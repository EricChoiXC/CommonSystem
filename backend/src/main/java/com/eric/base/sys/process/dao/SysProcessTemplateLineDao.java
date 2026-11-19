package com.eric.base.sys.process.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.sys.process.mapper.SysProcessTemplateLineMapper;
import com.eric.base.sys.process.model.SysProcessTemplateLine;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 流程模板连线数据访问对象
 *
 * @author Eric
 * @date 2026/11/19
 * @description 与 SysProcessTemplateLineMapper 一对一对接，不承载业务逻辑；
 *              模板保存时「删旧插新」由模板 Service 在同一事务内编排。
 */
@Repository
public class SysProcessTemplateLineDao extends BaseDao<SysProcessTemplateLine, SysProcessTemplateLineMapper> {

    public SysProcessTemplateLineDao(SysProcessTemplateLineMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 按模板查询全部连线（按起始节点键、分支序号升序）
     */
    public List<SysProcessTemplateLine> selectListByTemplateId(String templateId) {
        return getBaseMapper().selectListByTemplateId(templateId);
    }

    /**
     * 按「模板 + 起始节点键」查询候选出边
     */
    public List<SysProcessTemplateLine> selectListByTemplateIdAndFromKey(String templateId, String fromKey) {
        return getBaseMapper().selectListByTemplateIdAndFromKey(templateId, fromKey);
    }

    /**
     * 删除模板全部连线（保存全量替换前清空）
     */
    public int deleteByTemplateId(String templateId) {
        return getBaseMapper().deleteByTemplateId(templateId);
    }

    /**
     * 批量插入模板连线（保存时全量重建）
     */
    public int insertBatch(String templateId, List<SysProcessTemplateLine> lines) {
        return getBaseMapper().insertBatch(templateId, lines);
    }
}
