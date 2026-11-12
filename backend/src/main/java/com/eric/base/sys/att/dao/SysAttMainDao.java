package com.eric.base.sys.att.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.att.mapper.SysAttFileMapper;
import com.eric.base.sys.att.mapper.SysAttMainMapper;
import com.eric.base.sys.att.model.SysAttFile;
import com.eric.base.sys.att.model.SysAttMain;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 附件主表数据访问对象
 *
 * @author Eric
 * @date 2026/8/7
 * @description 与 SysAttMainMapper 一对一对接；上传时承担 sys_att_main 与
 *              sys_att_file 双表同 field_id 写入（见 {@link #insert}）。
 */
@Repository
public class SysAttMainDao extends BaseDao<SysAttMain, SysAttMainMapper> {

    private final SysAttFileMapper sysAttFileMapper;

    public SysAttMainDao(SysAttMainMapper baseMapper, SysAttFileMapper sysAttFileMapper) {
        super(baseMapper);
        this.sysAttFileMapper = sysAttFileMapper;
    }

    /**
     * 上传双表写入：先写 sys_att_file 再写 sys_att_main，两表 field_id 一致
     */
    @Override
    public int insert(SysAttMain model) {
        sysAttFileMapper.insert(toFile(model));
        return getBaseMapper().insert(model);
    }

    @Override
    public int deleteById(String fieldId) {
        sysAttFileMapper.deleteById(fieldId);
        return getBaseMapper().deleteById(fieldId);
    }

    /**
     * 更新附件有效性（软删除主表）
     */
    public int updateEnable(String fieldId, boolean enable) {
        return getBaseMapper().updateEnable(fieldId, enable);
    }

    /**
     * 下载次数 +1
     */
    public int incrDownload(String fieldId) {
        return getBaseMapper().incrDownload(fieldId);
    }

    /**
     * 预览次数 +1
     */
    public int incrView(String fieldId) {
        return getBaseMapper().incrView(fieldId);
    }

    /**
     * 附件列表总数（与 selectListSorted 过滤条件一致，供分页 metadata.total 使用）
     */
    public long selectCount(SysAttMain model) {
        return getBaseMapper().selectCount(model);
    }

    /**
     * 附件列表分页查询（单条件排序版）：sortField / sortOrder 由 Service 层归一化后传入
     */
    public List<SysAttMain> selectListSorted(SysAttMain model, Pages pages,
                                             String sortField, String sortOrder) {
        return getBaseMapper().selectListSorted(model, pages, sortField, sortOrder);
    }

    private SysAttFile toFile(SysAttMain model) {
        SysAttFile file = new SysAttFile();
        file.setFieldId(model.getFieldId());
        file.setFieldName(model.getFieldName());
        file.setFieldFileType(model.getFieldFileType());
        file.setFieldFilePath(model.getFieldFilePath());
        file.setFieldCreateTime(model.getFieldCreateTime());
        file.setFieldFileSize(model.getFieldFileSize());
        file.setFieldIsDelete(model.getFieldIsDelete());
        return file;
    }
}
