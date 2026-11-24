package com.eric.base.sys.form.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.sys.form.mapper.SysFormVersionMapper;
import com.eric.base.sys.form.model.SysFormVersion;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * 表单版本数据访问对象
 *
 * @author Eric
 * @date 2026/11/24
 * @description 与 SysFormVersionMapper 一对一对接，不承载业务逻辑（F15-DB-001）；
 *              基础 CRUD 走 BaseDao——基础 update 承载「草稿行内容重写」语义
 *              （仅 SET field_schema_json + 审计两列，WHERE 守护 field_status='DRAFT'，
 *              分册 §5.2.6⑤），deleteById 供行级清理；selectList 条件见 Mapper
 *              （fieldFormId / fieldStatus 精确 + 分页），selectCount 供分页总数，
 *              selectByFormId 供版本列表（草稿行恒排最前、其余 field_version 降序），
 *              selectMaxVersion 供发布/回滚版本号分配，selectVersionNumberById 供管理
 *              列表行 fieldCurrentVersion 轻量点查，freezeDraft / deprecate 为发布与
 *              回滚事务内的状态机流转语句，deleteByFormId 供删除表单事务清空版本行
 *              ——发布/回滚事务编排由 SysFormService 承载（F15-BE-002）。
 */
@Repository
public class SysFormVersionDao extends BaseDao<SysFormVersion, SysFormVersionMapper> {

    public SysFormVersionDao(SysFormVersionMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 条件查询总数（F15-DB-001）：条件与 {@link #selectList(SysFormVersion, com.eric.base.common.base.model.Pages)}
     * 完全一致，供分页响应 metadata.total。
     *
     * @param model 查询条件（可空字段忽略）
     * @return 匹配总数
     */
    public long selectCount(SysFormVersion model) {
        return getBaseMapper().selectCount(model);
    }

    /**
     * 版本列表（F15-DB-001，分册 §5.2.6①）：按表单取全部版本行（草稿行恒排最前，其余
     * field_version 降序——排序定稿见 {@link #selectByFormId}），供 /define/versions 响应；
     * 含草稿行与全部历史发布行。
     *
     * @param formId 表单主档 ID
     * @return 版本行清单
     */
    public List<SysFormVersion> selectByFormId(String formId) {
        return getBaseMapper().selectByFormId(formId);
    }

    /**
     * 轻量取版本号（F15-BE-002）：按行主键点查 field_version（不取 Schema 体积列），
     * 供管理列表行 fieldCurrentVersion 组装；版本行不存在时返回 null。
     *
     * @param fieldId 版本行 ID
     * @return 整数版本号；不存在时返回 null
     */
    public Integer selectVersionNumberById(String fieldId) {
        return getBaseMapper().selectVersionNumberById(fieldId);
    }

    /**
     * 现存最大版本号（F15-DB-001，分册 §5.2.6②）：发布/回滚「最大 + 1」分配；
     * 无发布版本时返回 null，Service 以 0 兜底后 +1，并发竞争由唯一索引兜底。
     *
     * @param formId 表单主档 ID
     * @return 现存最大整数版本号；无发布版本时返回 null
     */
    public Integer selectMaxVersion(String formId) {
        return getBaseMapper().selectMaxVersion(formId);
    }

    /**
     * 草稿冻结（F15-DB-001，分册 §5.2.6⑥）：发布动作对草稿行原地冻结回填
     * （版本号 / 状态 DRAFT→PUBLISHED / 发布时刻 / 修改审计），WHERE 守护
     * field_status='DRAFT'；须与主档指针切换、原当前版本退役同事务调用。
     *
     * @param fieldId      草稿行 ID
     * @param fieldVersion 发布分配的整数版本号
     * @param publishedAt  发布时刻
     * @param updaterId    修改人 ID
     * @param updateTime   修改时间
     * @return 影响行数
     */
    public int freezeDraft(String fieldId, Integer fieldVersion, Date publishedAt, String updaterId, Date updateTime) {
        return getBaseMapper().freezeDraft(fieldId, fieldVersion, publishedAt, updaterId, updateTime);
    }

    /**
     * 旧版本退役（F15-DB-001，分册 §5.2.6⑦）：原当前发布版本 PUBLISHED→DEPRECATED
     * 并刷新修改审计，WHERE 守护 field_status='PUBLISHED'；发布/回滚事务内调用。
     *
     * @param fieldId    原当前发布版本行 ID
     * @param updaterId  修改人 ID
     * @param updateTime 修改时间
     * @return 影响行数
     */
    public int deprecate(String fieldId, String updaterId, Date updateTime) {
        return getBaseMapper().deprecate(fieldId, updaterId, updateTime);
    }

    /**
     * 删除表单全部版本行（F15-DB-001，分册 §5.2.6⑧）：删除表单事务内先于主档行
     * 执行（事务口径见 {@link SysFormVersionMapper#deleteByFormId}）。
     *
     * @param formId 表单主档 ID
     * @return 影响行数
     */
    public int deleteByFormId(String formId) {
        return getBaseMapper().deleteByFormId(formId);
    }
}
