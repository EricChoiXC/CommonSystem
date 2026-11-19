package com.eric.base.sys.process.dao;

import com.eric.base.sys.process.mapper.SysProcessOrgUserMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 流程侧组织人员数据访问对象
 *
 * @author Eric
 * @date 2026/11/19
 * @description 与 {@link SysProcessOrgUserMapper} 一对一对接，不承载业务逻辑；
 *              仅暴露「按部门/机构子树取人员」这一只读入口，供 ProcessHandlerResolver
 *              解析审批人/抄送对象成员使用（P17-BE-002）。
 */
@Repository
public class SysProcessOrgUserDao {

    private final SysProcessOrgUserMapper orgUserMapper;

    public SysProcessOrgUserDao(SysProcessOrgUserMapper orgUserMapper) {
        this.orgUserMapper = orgUserMapper;
    }

    /**
     * 按部门/机构子树批量查询人员 ID（不含无匹配、非启用人员）。
     */
    public List<String> selectUserIdsByOrgHierarchy(List<String> orgIds) {
        return orgUserMapper.selectUserIdsByOrgHierarchy(orgIds);
    }

    /**
     * 按岗位批量查询岗位关联人员 ID（sys_org_post_user 关联 sys_org_element 启用人员，
     * 按人员创建时间升序；空入参由调用方短路，不进入 SQL）。
     */
    public List<String> selectUserIdsByPostIds(List<String> postIds) {
        return orgUserMapper.selectUserIdsByPostIds(postIds);
    }
}
