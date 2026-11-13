package com.eric.base.sys.datasource.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.datasource.model.SysDatasource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * 数据源数据访问映射
 *
 * @author Eric
 * @date 2026/11/13
 * @description 与 resources/mappers/sys/datasource/SysDatasourceMapper.xml 一对一对接，
 *              只做 SQL 映射，不承载业务逻辑。基础语句（insert / update / deleteById /
 *              selectById / selectList / loadModel）继承 {@link IBaseMapper}；
 *              selectByCode 供编码全局查重与 /query 执行期解析；selectCount 与 selectList
 *              条件一致，供列表 metadata.total；updateEnable 供 /enable 通道启停用。
 */
@Mapper
public interface SysDatasourceMapper extends IBaseMapper<SysDatasource> {

    /**
     * 编码查重 / 执行期解析：按编码取数据源。
     *
     * @param fieldCode 数据源编码
     * @return 同编码数据源；不存在时返回 null
     */
    SysDatasource selectByCode(@Param("fieldCode") String fieldCode);

    /**
     * 管理列表总数：过滤条件与 {@link #selectList} 完全一致，供响应 metadata.total 使用。
     *
     * @param model 查询条件（可空字段忽略）
     * @return 匹配总数
     */
    long selectCount(@Param("model") SysDatasource model);

    /**
     * 启停用：仅更新 field_enable 与修改人/修改时间，不触碰其他字段。
     *
     * @param fieldId     数据源 ID
     * @param fieldEnable 是否启用
     * @param updaterId   修改人 ID
     * @param updateTime  修改时间
     * @return 影响行数
     */
    int updateEnable(@Param("fieldId") String fieldId,
                     @Param("fieldEnable") Boolean fieldEnable,
                     @Param("updaterId") String updaterId,
                     @Param("updateTime") Date updateTime);
}
