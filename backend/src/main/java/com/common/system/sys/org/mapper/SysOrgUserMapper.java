package com.common.system.sys.org.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.org.model.SysOrgUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户数据访问接口
 *
 * @author Eric
 * @date 2026/8/3
 * @description 用户表(sys_org_user)数据访问
 */
@Mapper
public interface SysOrgUserMapper extends IBaseMapper<SysOrgUser> {

    /**
     * 仅更新密码(field_password)与最后修改密码时间(field_last_chg_pwd_time)
     */
    int updatePassword(@Param("model") SysOrgUser model);

    /**
     * 按登录名查询用户（登录专用）。
     * 与普通查询不同：额外返回 field_password（BCrypt 哈希串）供 Service 校验，
     * 该字段在模型上标注 WRITE_ONLY，序列化响应时不会输出。
     *
     * @param loginName 登录名（精确匹配）
     * @return 匹配的用户，不存在时返回 null
     */
    SysOrgUser selectByLoginName(@Param("loginName") String loginName);

    /**
     * 按邮箱查询用户（登录/校验邮箱唯一性）
     */
    SysOrgUser selectByEmail(@Param("email") String email);

    /**
     * 按电话查询用户（登录/校验电话唯一性）
     */
    SysOrgUser selectByPhone(@Param("phone") String phone);

    /**
     * 第三方账号 openid 查重：openid 全局唯一，绑定/换绑前须校验该 openid
     * 是否已被任何渠道（微信/QQ）的其他用户绑定（见开发方案 4.1 节）
     *
     * @param openid 待校验的第三方 openid
     * @return 已绑定该 openid 的用户数（0 表示可用）
     */
    long countDuplicateOpenid(@Param("openid") String openid);

    SysOrgUser selectByIdWithFields(@Param("fieldId") String id, @Param("visibility") String visibility);

    List<SysOrgUser> selectListWithFields(@Param("model") SysOrgUser model, @Param("pages") Pages pages, @Param("visibility") String visibility);

    /**
     * 列表总数统计：与 selectListWithFields 同条件（计数与可见字段裁剪无关），供 metadata.total 返回
     */
    long countListWithFields(@Param("model") SysOrgUser model);

}
