package com.common.system.sys.org.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.sys.org.mapper.SysOrgPostMapper;
import com.common.system.sys.org.model.SysOrgPost;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 岗位数据访问对象
 *
 * @author Eric
 * @date 2026/8/8
 * @description 与 SysOrgPostMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysOrgPostDao extends BaseDao<SysOrgPost, SysOrgPostMapper> {

    public SysOrgPostDao(SysOrgPostMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 按用户查询其岗位（仅 field_id、field_name，登录等场景组装用）
     */
    public List<SysOrgPost> selectIdNameListByUserId(String userId) {
        return getBaseMapper().selectIdNameListByUserId(userId);
    }
}
