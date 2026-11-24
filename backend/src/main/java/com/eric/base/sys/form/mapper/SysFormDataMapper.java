package com.eric.base.sys.form.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.form.model.SysFormData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 表单数据数据访问映射
 *
 * @author Eric
 * @date 2026/11/24
 * @description 与 resources/mappers/sys/form/SysFormDataMapper.xml 一对一对接，
 *              只做 SQL 映射，不承载业务逻辑（F15-DB-002）。基础语句（insert /
 *              update / deleteById / selectById / selectList / loadModel）继承
 *              {@link IBaseMapper}；基础 update 为恒不生效的防御性占位（一期只增不改，
 *              无实例更新接口，分册 §5.3.6 禁止 UPDATE 语义）；selectList 条件
 *              fieldFormId / fieldBusinessId / fieldProcessInstanceId / fieldCreatorId
 *              精确（归属过滤值由 Service 层强制注入，API 分册 §4.8），列表行不返回
 *              field_form_data 内容（分册 §5.3.6④）；selectCount 与 selectList 条件
 *              完全一致，供响应 metadata.total；existsByFormId 供 /define/delete
 *              前置实例数据存在性探测（CS_FORM_HAS_DATA，API 分册 §3.7）。
 */
@Mapper
public interface SysFormDataMapper extends IBaseMapper<SysFormData> {

    /**
     * 数据列表总数（F15-DB-002）：过滤条件与 {@link #selectList} 完全一致（共用
     * QueryConditions 片段），供响应 metadata.total 使用。
     *
     * @param model 查询条件（可空字段忽略）
     * @return 匹配总数
     */
    long selectCount(@Param("model") SysFormData model);

    /**
     * 表单实例数据存在性探测（F15-DB-002，分册 §5.3.6⑤）：按表单主档 ID 计数
     * （走 idx_form_data_form 索引），供 /define/delete 前置校验——该表单存在任意
     * 实例数据时禁删（CS_FORM_HAS_DATA，API 分册 §3.7）。
     *
     * @param formId 表单主档 ID（sys_form.field_id）
     * @return 该表单下存在实例数据时 true
     */
    boolean existsByFormId(@Param("formId") String formId);
}
