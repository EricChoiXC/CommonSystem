package com.eric.base.sys.form.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.common.base.model.Pages;
import com.eric.base.sys.form.mapper.SysFormDataMapper;
import com.eric.base.sys.form.model.SysFormData;
import com.eric.base.sys.form.request.SysFormDataQuery;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 表单数据数据访问对象
 *
 * @author Eric
 * @date 2026/11/24
 * @description 与 SysFormDataMapper 一对一对接，不承载业务逻辑（F15-DB-002）；
 *              基础 CRUD 走 BaseDao——一期只增不改（分册 §4.4 / §5.3.6）：insert 承载
 *              保存落库（公式重算值与裁剪后的最终 JSON，由 Service 经 Jackson 序列化），
 *              基础 update 为恒不生效的防御性占位（无实例更新接口，禁止业务调用），
 *              deleteById 为物理删除（创建人或管理员，归属校验由 Service 层承担）；
 *              selectList 条件见 Mapper（fieldFormId / fieldBusinessId /
 *              fieldProcessInstanceId / fieldCreatorId 精确，默认创建时间降序，列表行
 *              不返回 field_form_data），selectCount 供列表 metadata.total，
 *              selectListByQuery / selectCountByQuery 为 Query 请求对象通道（扩展创建时间
 *              区间，fieldCreatorId 仍由 Service 层强制注入），existsByFormId 供
 *              /define/delete 前置 CS_FORM_HAS_DATA 校验——数据面
 *              接口编排与归属强制过滤由 SysFormDataService 承载（F15-BE-003）。
 */
@Repository
public class SysFormDataDao extends BaseDao<SysFormData, SysFormDataMapper> {

    public SysFormDataDao(SysFormDataMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 数据列表总数（F15-DB-002）：条件与 {@link #selectList(SysFormData, com.eric.base.common.base.model.Pages)}
     * 完全一致，供响应 metadata.total。
     *
     * @param model 查询条件（可空字段忽略）
     * @return 匹配总数
     */
    public long selectCount(SysFormData model) {
        return getBaseMapper().selectCount(model);
    }

    /**
     * 数据列表（Query 请求对象通道，F15-BE-003）：条件同 {@link #selectList(SysFormData,
     * com.eric.base.common.base.model.Pages)} 并扩展创建时间区间（fieldCreateTimeBegin/End），
     * fieldCreatorId 归属过滤值由 Service 层强制注入（Mapper 不读会话，API 分册 §4.8）；
     * 供 /data/list 使用，列表行不返回 field_form_data 内容。
     *
     * @param query 查询条件（可空字段忽略；fieldFormId 必填由 Service 先行校验）
     * @param pages 分页参数（null 或 needPage=false 返回全部）
     * @return 列表行
     */
    public List<SysFormData> selectListByQuery(SysFormDataQuery query, Pages pages) {
        return getBaseMapper().selectListByQuery(query, pages);
    }

    /**
     * 数据列表总数（Query 请求对象通道，F15-BE-003）：条件与
     * {@link #selectListByQuery(SysFormDataQuery, Pages)} 完全一致，供响应 metadata.total。
     *
     * @param query 查询条件（可空字段忽略）
     * @return 匹配总数
     */
    public long selectCountByQuery(SysFormDataQuery query) {
        return getBaseMapper().selectCountByQuery(query);
    }

    /**
     * 表单实例数据存在性探测（F15-DB-002，分册 §5.3.6⑤）：/define/delete 前置校验，
     * 该表单存在任意实例数据时禁删（CS_FORM_HAS_DATA，API 分册 §3.7）。
     *
     * @param formId 表单主档 ID
     * @return 存在实例数据时 true
     */
    public boolean existsByFormId(String formId) {
        return getBaseMapper().existsByFormId(formId);
    }
}
