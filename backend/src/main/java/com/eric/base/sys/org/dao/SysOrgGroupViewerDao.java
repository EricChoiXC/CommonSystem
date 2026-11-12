package com.eric.base.sys.org.dao;

import com.eric.base.sys.org.mapper.SysOrgGroupMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 群组可见范围明细数据访问对象
 *
 * @author Eric
 * @date 2026/11/4
 * @description 与 SysOrgGroupMapper 一对一对接（sys_org_group_viewer 明细语句收敛于该 Mapper），
 *              仅分职责暴露可见范围明细读写，不承载业务逻辑；
 *              全量替换（先删后插）与 field_is_all_viewer 派生计算的编排由 SysOrgGroupService 完成。
 */
@Repository
public class SysOrgGroupViewerDao {

    private final SysOrgGroupMapper groupMapper;

    public SysOrgGroupViewerDao(SysOrgGroupMapper groupMapper) {
        this.groupMapper = groupMapper;
    }

    /**
     * 按群组查询可见范围节点ID列表（编辑/详情回显用；为空表示全员可见）
     */
    public List<String> selectViewerIdsByGroupId(String groupId) {
        return groupMapper.selectViewerIdsByGroupId(groupId);
    }

    /**
     * 批量插入可见范围明细（viewerIds 必须非空，空列表由调用方跳过）
     */
    public int insertGroupViewers(String groupId, List<String> viewerIds) {
        return groupMapper.insertGroupViewers(groupId, viewerIds);
    }

    /**
     * 删除群组全部可见范围明细（编辑时全量替换前清空）
     */
    public int deleteGroupViewersByGroupId(String groupId) {
        return groupMapper.deleteGroupViewersByGroupId(groupId);
    }
}
