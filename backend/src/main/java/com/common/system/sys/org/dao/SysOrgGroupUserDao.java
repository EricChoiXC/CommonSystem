package com.common.system.sys.org.dao;

import com.common.system.sys.org.mapper.SysOrgGroupMapper;
import com.common.system.sys.org.model.SysOrgElement;
import com.common.system.sys.org.model.SysOrgGroupUser;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 群组成员明细数据访问对象
 *
 * @author Eric
 * @date 2026/11/4
 * @description 与 SysOrgGroupMapper 一对一对接（sys_org_group_user 明细语句收敛于该 Mapper），
 *              仅分职责暴露成员明细读写，不承载业务逻辑；
 *              全量替换（先删后插）的编排由 SysOrgGroupService 同事务完成。
 */
@Repository
public class SysOrgGroupUserDao {

    private final SysOrgGroupMapper groupMapper;

    public SysOrgGroupUserDao(SysOrgGroupMapper groupMapper) {
        this.groupMapper = groupMapper;
    }

    /**
     * 按群组查询成员人员ID列表（编辑/详情回显用）
     */
    public List<String> selectUserIdsByGroupId(String groupId) {
        return groupMapper.selectUserIdsByGroupId(groupId);
    }

    /**
     * 地址本群组页签成员人员明细（ORG2-BE-004）：按群组返回启用人员完整元素信息
     * （列结构与地址本 selectList 同构），可见性校验由 SysOrgGroupService 前置完成。
     */
    public List<SysOrgElement> selectUsersByGroupId(String groupId) {
        return groupMapper.selectUsersByGroupId(groupId);
    }

    /**
     * 批量插入成员明细（userIds 必须非空，空列表由调用方跳过）
     */
    public int insertGroupUsers(String groupId, List<String> userIds) {
        return groupMapper.insertGroupUsers(groupId, userIds);
    }

    /**
     * 删除群组全部成员明细（编辑时全量替换前清空）
     */
    public int deleteGroupUsersByGroupId(String groupId) {
        return groupMapper.deleteGroupUsersByGroupId(groupId);
    }
}
