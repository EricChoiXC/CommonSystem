package com.common.system.sys.org.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.org.mapper.SysOrgElementMapper;
import com.common.system.sys.org.mapper.SysOrgUserMapper;
import com.common.system.sys.org.model.SysOrgUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户数据访问对象
 *
 * @author Eric
 * @date 2026/8/3
 * @description 与 SysOrgUserMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysOrgUserDao extends BaseDao<SysOrgUser, SysOrgUserMapper> {

    public SysOrgUserDao(SysOrgUserMapper baseMapper) {
        super(baseMapper);
    }

    @Autowired
    private SysOrgElementMapper sysOrgElementMapper;

    public SysOrgElementMapper getSysOrgElementMapper() {
        return sysOrgElementMapper;
    }

    @Override
    public int insert(SysOrgUser model) {
        getSysOrgElementMapper().insert(model);
        return getBaseMapper().insert(model);
    }

    @Override
    public int update(SysOrgUser model) {
        getSysOrgElementMapper().update(model);
        return getBaseMapper().update(model);
    }

    @Override
    public int deleteById(String fieldId) {
        getSysOrgElementMapper().deleteById(fieldId);
        return getBaseMapper().deleteById(fieldId);
    }

    /**
     * 仅更新密码与最后修改密码时间，不触碰其他字段
     */
    public int updatePassword(SysOrgUser model) {
        return getBaseMapper().updatePassword(model);
    }

    /**
     * 按登录名查询用户（登录专用，额外携带 BCrypt 密码哈希供校验）
     */
    public SysOrgUser selectByLoginName(String loginName) {
        return getBaseMapper().selectByLoginName(loginName);
    }

    /**
     * 按邮箱查询用户
     */
    public SysOrgUser selectByEmail(String email) {
        return getBaseMapper().selectByEmail(email);
    }

    /**
     * 按电话查询用户
     */
    public SysOrgUser selectByPhone(String phone) {
        return getBaseMapper().selectByPhone(phone);
    }

    public SysOrgUser selectByIdWithFields(String id, String visibility) {
        return getBaseMapper().selectByIdWithFields(id, visibility);
    }

    public List<SysOrgUser> selectListWithFields(SysOrgUser criteria, Pages pages, String visibleFields) {
        return getBaseMapper().selectListWithFields(criteria, pages, visibleFields);
    }

    /**
     * 列表总数统计：与 selectListWithFields 同条件，供 metadata.total 返回
     */
    public long countListWithFields(SysOrgUser criteria) {
        return getBaseMapper().countListWithFields(criteria);
    }

    /**
     * 第三方账号 openid 查重（PF3-BE-002，方案 4.4）：openid 全局唯一，绑定/换绑前
     * 校验该 openid 是否已被其他用户绑定，计数 > 0 即拒绝（无本人豁免，方案 4.4 字面）
     */
    public long countDuplicateOpenid(String openid) {
        return getBaseMapper().countDuplicateOpenid(openid);
    }

    /**
     * 双渠道均未绑定时整列置 NULL（PF3-BE-002，方案 4.4 解绑）：通用 update 对
     * field_openid 是 <if> 条件写（null 落不了 NULL），须经本包装走专用置 NULL SQL
     */
    public int clearOpenid(String fieldId) {
        return getBaseMapper().clearOpenid(fieldId);
    }
}
