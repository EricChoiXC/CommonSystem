package com.eric.base.sys.org.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.org.model.SysOrgPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 岗位数据访问接口
 *
 * @author Eric
 * @date 2026/8/5
 * @description 岗位表(sys_org_post)数据访问
 */
@Mapper
public interface SysOrgPostMapper extends IBaseMapper<SysOrgPost> {

    /**
     * 按用户查询其岗位（轻量：仅 field_id、field_name），供登录等场景组装；
     * 岗位人员关系由 sys_org_post_user 表承载。
     */
    List<SysOrgPost> selectIdNameListByUserId(@Param("userId") String userId);
}
