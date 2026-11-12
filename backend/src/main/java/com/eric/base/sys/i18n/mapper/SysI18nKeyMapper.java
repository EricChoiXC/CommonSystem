package com.eric.base.sys.i18n.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.i18n.model.SysI18nKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 多语言-资源 Mapper
 *
 * @author Eric
 * @date 2026/11/5
 * @description 与 SysI18nKeyMapper.xml 同名对应，一对一操作 sys_i18n_key；
 *              key 仅随 resources/i18n/** 资源文件扫描产生（D5），无人工维护接口。
 */
@Mapper
public interface SysI18nKeyMapper extends IBaseMapper<SysI18nKey> {

    /**
     * 按 (field_model, field_key) 查询资源项（启动/部署同步幂等登记与导入校验用）
     */
    SysI18nKey selectByModelAndKey(@Param("fieldModel") String fieldModel,
                                   @Param("fieldKey") String fieldKey);

    /**
     * 指定模块的全部资源项（field_key 升序；翻译矩阵行序与其一致）
     */
    List<SysI18nKey> selectByModel(@Param("fieldModel") String fieldModel);

    /**
     * 模块聚合：去重 field_model 列表（按 field_model 升序），供翻译维护模块下拉
     */
    List<String> selectDistinctModels();

    /**
     * 按 (field_model, field_key) 统计资源项数（同步幂等判定，避免逐条查询）
     */
    long countByModelAndKey(@Param("fieldModel") String fieldModel,
                            @Param("fieldKey") String fieldKey);

    /**
     * 按资源 key 全局查询（资源 key 全局唯一：一个 key 只归属一个 field_model，
     * 启动/部署同步据此拦截跨模块重复登记）
     */
    SysI18nKey selectByKey(@Param("fieldKey") String fieldKey);

    /**
     * 按资源 key 全局删除（D5：key 仅随资源文件扫描产生，
     * 资源文件移除 key 后随启动/部署同步级联清理登记）
     */
    int deleteByKey(@Param("fieldKey") String fieldKey);
}
