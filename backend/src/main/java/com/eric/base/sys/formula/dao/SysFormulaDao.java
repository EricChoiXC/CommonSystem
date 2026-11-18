package com.eric.base.sys.formula.dao;

import com.eric.base.common.base.dao.BaseDao;
import com.eric.base.sys.formula.mapper.SysFormulaMapper;
import com.eric.base.sys.formula.model.SysFormula;
import org.springframework.stereotype.Repository;

import java.util.Date;

/**
 * 公式数据访问对象
 *
 * @author Eric
 * @date 2026/11/18
 * @description 与 SysFormulaMapper 一对一对接，不承载业务逻辑（F14-BE-006）；
 *              基础 CRUD 走 BaseDao（insert / update / deleteById / selectById /
 *              selectList / loadModel），selectList 条件见 Mapper（fieldCode / fieldName
 *              模糊、fieldType / fieldEnable 精确，默认创建时间降序）；
 *              selectCount 供列表 metadata.total，selectByCode 供编码全局查重与
 *              执行期按 field_code 加载，updateEnable 供 /enable 通道启停用——
 *              管理编排与依赖解析由 SysFormulaService 承载（F14-BE-007）。
 */
@Repository
public class SysFormulaDao extends BaseDao<SysFormula, SysFormulaMapper> {

    public SysFormulaDao(SysFormulaMapper baseMapper) {
        super(baseMapper);
    }

    /**
     * 管理列表总数（F14-BE-006）：条件与 {@link #selectList(SysFormula, com.eric.base.common.base.model.Pages)}
     * 完全一致，供响应 metadata.total。
     *
     * @param model 查询条件（可空字段忽略）
     * @return 匹配总数
     */
    public long selectCount(SysFormula model) {
        return getBaseMapper().selectCount(model);
    }

    /**
     * 编码查重 / 执行期解析（F14-BE-006，D4 Service 层查重先例）：按编码取公式。
     *
     * @param fieldCode 公式编码
     * @return 同编码公式；不存在时返回 null
     */
    public SysFormula selectByCode(String fieldCode) {
        return getBaseMapper().selectByCode(fieldCode);
    }

    /**
     * 启停用（F14-BE-006）：仅更新 field_enable 与修改人/修改时间，只更新自身一行
     * （本表无树形语义，不级联后代）。
     *
     * @param fieldId     公式 ID
     * @param fieldEnable 是否启用
     * @param updaterId   修改人 ID
     * @param updateTime  修改时间
     * @return 影响行数
     */
    public int updateEnable(String fieldId, Boolean fieldEnable, String updaterId, Date updateTime) {
        return getBaseMapper().updateEnable(fieldId, fieldEnable, updaterId, updateTime);
    }
}
