package com.eric.base.sys.form.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.sys.form.mapper.SysFormMapper;
import com.eric.base.sys.form.model.SysForm;
import org.springframework.stereotype.Repository;

import java.util.Date;

/**
 * 表单主档数据访问对象
 *
 * @author Eric
 * @date 2026/11/24
 * @description 与 SysFormMapper 一对一对接，不承载业务逻辑（F15-DB-001）；
 *              基础 CRUD 走 BaseDao（insert / update / deleteById / selectById /
 *              selectList / loadModel），selectList 条件见 Mapper（fieldFormCode /
 *              fieldFormName 模糊、fieldStatus / fieldEnable 精确，默认创建时间降序）；
 *              selectCount 供列表 metadata.total，selectByCode 供编码全局查重与
 *              精确定位，updateEnable 供 /define/enable 通道启停用，updateCurrentVersionId
 *              供发布/回滚事务切换当前发布版本指针——发布/回滚编排与编码查重由
 *              SysFormService 承载（F15-BE-002）。
 */
@Repository
public class SysFormDao extends BaseDao<SysForm, SysFormMapper> {

    public SysFormDao(SysFormMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 管理列表总数（F15-DB-001）：条件与 {@link #selectList(SysForm, com.eric.base.common.base.model.Pages)}
     * 完全一致，供响应 metadata.total。
     *
     * @param model 查询条件（可空字段忽略）
     * @return 匹配总数
     */
    public long selectCount(SysForm model) {
        return getBaseMapper().selectCount(model);
    }

    /**
     * 编码查重 / 精确定位（F15-DB-001，分册 §5.1.6②）：按编码取表单，
     * uk_form_code 为库级兜底、应用层查重抛 CS_FORM_CODE_DUPLICATED。
     *
     * @param fieldCode 表单编码
     * @return 同编码表单；不存在时返回 null
     */
    public SysForm selectByCode(String fieldCode) {
        return getBaseMapper().selectByCode(fieldCode);
    }

    /**
     * 启停用（F15-DB-001，分册 §5.1.6⑥）：仅更新 field_enable 与修改人/修改时间，
     * 只更新自身一行（本表无树形语义，不级联后代）；停用关闭填写入口与数据提交，
     * 定义面与数据查询保留。
     *
     * @param fieldId     表单 ID
     * @param fieldEnable 是否启用
     * @param updaterId   修改人 ID
     * @param updateTime  修改时间
     * @return 影响行数
     */
    public int updateEnable(String fieldId, Boolean fieldEnable, String updaterId, Date updateTime) {
        return getBaseMapper().updateEnable(fieldId, fieldEnable, updaterId, updateTime);
    }

    /**
     * 发布/回滚切换当前版本指针（F15-DB-001，分册 §5.1.6⑦）：指针与主档状态成对
     * 切换（方法原子性说明见 {@link SysFormMapper#updateCurrentVersionId}），须与
     * 版本行冻结/退役同事务调用，不可单独执行。
     *
     * @param fieldId               表单 ID
     * @param fieldStatus           同步后的主档状态（PUBLISHED）
     * @param fieldCurrentVersionId 当前发布版本行 id
     * @param updaterId             修改人 ID
     * @param updateTime            修改时间
     * @return 影响行数
     */
    public int updateCurrentVersionId(String fieldId, String fieldStatus,
                                      String fieldCurrentVersionId, String updaterId, Date updateTime) {
        return getBaseMapper().updateCurrentVersionId(fieldId, fieldStatus, fieldCurrentVersionId, updaterId, updateTime);
    }
}
