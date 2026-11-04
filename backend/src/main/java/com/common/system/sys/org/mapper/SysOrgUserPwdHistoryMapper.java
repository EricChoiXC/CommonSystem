package com.common.system.sys.org.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.sys.org.model.SysOrgUserPwdHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 密码历史数据访问映射
 *
 * @author Eric
 * @date 2026/8/25
 * @description 与 sys_org_pwd_history 一对一对接。
 */
@Mapper
public interface SysOrgUserPwdHistoryMapper extends IBaseMapper<SysOrgUserPwdHistory> {

    /**
     * 按用户 ID 查询密码历史（按创建时间降序）
     */
    List<SysOrgUserPwdHistory> selectByUserId(@Param("userId") String userId);

    /**
     * 删除指定用户超出保留条数的历史记录
     * @param userId 用户ID
     * @param keepCount 保留的最新条数
     * @return 删除行数
     */
    int deleteExcess(@Param("userId") String userId, @Param("keepCount") int keepCount);
}