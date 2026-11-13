package com.eric.base.sys.datasource.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.sys.datasource.mapper.SysDatasourceMapper;
import com.eric.base.sys.datasource.model.SysDatasource;
import org.springframework.stereotype.Repository;

import java.util.Date;

/**
 * 数据源数据访问对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 与 SysDatasourceMapper 一对一对接，不承载业务逻辑；
 *              基础 CRUD 走 BaseDao，编码查重（selectByCode）、列表总数（selectCount）
 *              与启停用（updateEnable）为模块专属语句。
 */
@Repository
public class SysDatasourceDao extends BaseDao<SysDatasource, SysDatasourceMapper> {

    public SysDatasourceDao(SysDatasourceMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 编码查重 / 执行期解析：按编码取数据源。
     *
     * @param fieldCode 数据源编码
     * @return 同编码数据源；不存在时返回 null
     */
    public SysDatasource selectByCode(String fieldCode) {
        return getBaseMapper().selectByCode(fieldCode);
    }

    /**
     * 管理列表总数：过滤条件与 {@link #selectList} 一致，供响应 metadata.total 使用。
     *
     * @param criteria 查询条件（可空字段忽略）
     * @return 匹配总数
     */
    public long selectCount(SysDatasource criteria) {
        return getBaseMapper().selectCount(null == criteria ? new SysDatasource() : criteria);
    }

    /**
     * 启停用：仅更新 field_enable 与修改人/修改时间。
     *
     * @param fieldId     数据源 ID
     * @param fieldEnable 是否启用
     * @param updaterId   修改人 ID
     * @param updateTime  修改时间
     * @return 影响行数
     */
    public int updateEnable(String fieldId, Boolean fieldEnable, String updaterId, Date updateTime) {
        return getBaseMapper().updateEnable(fieldId, fieldEnable, updaterId, updateTime);
    }
}
