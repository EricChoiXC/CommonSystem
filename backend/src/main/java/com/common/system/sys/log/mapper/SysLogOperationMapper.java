package com.common.system.sys.log.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.log.model.SysLogOperation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 通用日志 Mapper
 *
 * @author Eric
 * @date 2026/8/8
 * @description 与 SysLogOperationMapper.xml 同名对应，一对一操作 sys_log_operation。
 *              流水表只增不改：不提供 update / delete 语句，仅 insert / 批量 insert / 查询。
 */
@Mapper
public interface SysLogOperationMapper extends IBaseMapper<SysLogOperation> {

    /**
     * 批量插入（日志批量异步落库使用）
     */
    int insertBatch(@Param("list") List<SysLogOperation> list);

    /**
     * 分页查询日志：按 field_log_type 区分操作日志/登录日志，
     * 支持模块、动作、操作人（姓名/登录名）、业务文档 ID、结果、操作时间范围筛选；
     * sortOrder 必须为白名单值（asc / desc），由 Service 层归一化后传入。
     */
    List<SysLogOperation> selectLogList(@Param("model") SysLogOperation model,
                                        @Param("pages") Pages pages,
                                        @Param("sortOrder") String sortOrder,
                                        @Param("operateTimeStart") String operateTimeStart,
                                        @Param("operateTimeEnd") String operateTimeEnd);

    /**
     * 统计日志总数（与 selectLogList 条件一致，供分页 metadata.total 使用）
     */
    long selectLogCount(@Param("model") SysLogOperation model,
                        @Param("operateTimeStart") String operateTimeStart,
                        @Param("operateTimeEnd") String operateTimeEnd);
}
