package com.common.system.sys.att.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.att.model.SysAttOperation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 附件操作日志 Mapper
 *
 * @author Eric
 * @date 2026/8/7
 * @description 与 SysAttOperationMapper.xml 同名对应，一对一操作 sys_att_operation。
 */
@Mapper
public interface SysAttOperationMapper extends IBaseMapper<SysAttOperation> {

    /**
     * 按附件 ID 查询操作日志（后台附件列表跳转查看），按操作时间排序，
     * sortOrder 必须为白名单值（asc / desc），由 Service 层归一化后传入。
     */
    List<SysAttOperation> selectListByAttId(@Param("model") SysAttOperation model,
                                            @Param("pages") Pages pages,
                                            @Param("sortOrder") String sortOrder);

    /**
     * 按附件 ID 统计操作日志总数（与 selectListByAttId 条件一致，供分页 metadata.total 使用）
     */
    long selectCountByAttId(@Param("model") SysAttOperation model);
}
