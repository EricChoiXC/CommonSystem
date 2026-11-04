package com.common.system.sys.att.mapper;

import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.att.model.SysAttMain;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 附件主表 Mapper
 *
 * @author Eric
 * @date 2026/8/7
 * @description 与 SysAttMainMapper.xml 同名对应，一对一操作 sys_att_main；
 *              查询统一 LEFT JOIN sys_att_file 组装文件信息。
 */
@Mapper
public interface SysAttMainMapper extends IBaseMapper<SysAttMain> {

    /**
     * 更新附件有效性（软删除主表记录时置 0）
     */
    int updateEnable(@Param("fieldId") String fieldId, @Param("enable") boolean enable);

    /**
     * 下载次数 +1
     */
    int incrDownload(@Param("fieldId") String fieldId);

    /**
     * 预览次数 +1
     */
    int incrView(@Param("fieldId") String fieldId);

    /**
     * 附件列表总数（与 selectList 过滤条件一致，供分页 metadata.total 使用）
     */
    long selectCount(@Param("model") SysAttMain model);

    /**
     * 附件列表分页查询（单条件排序版）：与 selectList 过滤条件一致，
     * sortField / sortOrder 必须为白名单值（createTime / download / view + asc / desc），
     * 由 Service 层归一化后传入。
     */
    List<SysAttMain> selectListSorted(@Param("model") SysAttMain model,
                                      @Param("pages") Pages pages,
                                      @Param("sortField") String sortField,
                                      @Param("sortOrder") String sortOrder);
}
