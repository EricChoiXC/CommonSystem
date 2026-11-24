package com.eric.base.sys.form.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.form.model.SysFormData;
import com.eric.base.sys.form.request.SysFormDataQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
     * 数据列表（Query 请求对象通道，F15-BE-003）：条件同 {@link #selectList} 并扩展创建
     * 时间区间（fieldCreateTimeBegin/End）——区间筛选由 Query 请求对象承载（本 Mapper XML
     * 注释登记「随 F15-BE-003 落地」，先例 SysProcessExampleQuery），fieldCreatorId 归属
     * 过滤值仍由 Service 层强制注入（Mapper 不读会话，API 分册 §4.8）。供 /data/list
     * 使用（{@link #selectListByQuery} / {@link #selectCountByQuery} 条件一致供
     * metadata.total）。
     *
     * @param query 查询条件（可空字段忽略；fieldFormId 必填由 Service 先行校验）
     * @param pages 分页参数（null 或 needPage=false 返回全部）
     * @return 列表行（BaseColumns 轻量列集，不返回 field_form_data 内容）
     */
    List<SysFormData> selectListByQuery(@Param("query") SysFormDataQuery query, @Param("pages") Pages pages);

    /**
     * 数据列表总数（Query 请求对象通道，F15-BE-003）：条件与 {@link #selectListByQuery}
     * 完全一致（共用 QueryRequestConditions 片段），供响应 metadata.total 使用。
     *
     * @param query 查询条件（可空字段忽略）
     * @return 匹配总数
     */
    long selectCountByQuery(@Param("query") SysFormDataQuery query);

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
