package com.common.system.sys.att.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.att.model.SysAttFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 附件文件表 Mapper
 *
 * @author Eric
 * @date 2026/8/7
 * @description 与 SysAttFileMapper.xml 同名对应，一对一操作 sys_att_file。
 */
@Mapper
public interface SysAttFileMapper extends IBaseMapper<SysAttFile> {
    /**
     * 更新文件删除标记（软删除文件记录时置 1）
     */
    int updateIsDelete(@Param("fieldId") String fieldId, @Param("isDelete") boolean isDelete);

    /**
     * 文件列表总数（与 selectList 过滤条件一致，供分页 metadata.total 使用）
     */
    long selectCount(@Param("model") SysAttFile model);

    /**
     * 文件列表分页查询（单条件排序版）：与 selectList 过滤条件一致，
     * sortField / sortOrder 必须为白名单值（createTime / fileSize + asc / desc），
     * 由 Service 层归一化后传入。
     */
    List<SysAttFile> selectListSorted(@Param("model") SysAttFile model,
                                      @Param("pages") Pages pages,
                                      @Param("sortField") String sortField,
                                      @Param("sortOrder") String sortOrder);
}
