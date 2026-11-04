package com.common.system.sys.att.dao;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.common.base.model.Pages;
import com.common.system.sys.att.mapper.SysAttFileMapper;
import com.common.system.sys.att.model.SysAttFile;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 附件文件表数据访问对象
 *
 * @author Eric
 * @date 2026/8/7
 * @description 与 SysAttFileMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysAttFileDao extends BaseDao<SysAttFile, SysAttFileMapper> {

    public SysAttFileDao(SysAttFileMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 更新文件删除标记（软删除文件记录）
     */
    public int updateIsDelete(String fieldId, boolean isDelete) {
        return getBaseMapper().updateIsDelete(fieldId, isDelete);
    }

    /**
     * 文件列表总数（与 selectListSorted 过滤条件一致，供分页 metadata.total 使用）
     */
    public long selectCount(SysAttFile model) {
        return getBaseMapper().selectCount(model);
    }

    /**
     * 文件列表分页查询（单条件排序版）：sortField / sortOrder 由 Service 层归一化后传入
     */
    public List<SysAttFile> selectListSorted(SysAttFile model, Pages pages,
                                             String sortField, String sortOrder) {
        return getBaseMapper().selectListSorted(model, pages, sortField, sortOrder);
    }
}
