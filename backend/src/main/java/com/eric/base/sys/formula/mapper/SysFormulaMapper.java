package com.eric.base.sys.formula.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.formula.model.SysFormula;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * 公式数据访问映射
 *
 * @author Eric
 * @date 2026/11/18
 * @description 与 resources/mappers/sys/formula/SysFormulaMapper.xml 一对一对接，
 *              只做 SQL 映射，不承载业务逻辑。基础语句（insert / update / deleteById /
 *              selectById / selectList / loadModel）继承 {@link IBaseMapper}；
 *              selectList 条件 fieldCode / fieldName 模糊、fieldType / fieldEnable 精确，
 *              排序固定创建时间降序（F14-BE-006）；selectCount 与 selectList 条件一致，
 *              供列表 metadata.total；selectByCode 供编码全局查重（uk_formula_code）与
 *              执行期按 field_code 加载启用公式（F14-BE-007）；updateEnable 供 /enable
 *              通道启停用（只更新自身，不级联——本表无树形语义）。
 */
@Mapper
public interface SysFormulaMapper extends IBaseMapper<SysFormula> {

    /**
     * 管理列表总数（F14-BE-006）：过滤条件与 {@link #selectList} 完全一致（共用
     * QueryConditions 片段），供响应 metadata.total 使用。
     *
     * @param model 查询条件（可空字段忽略）
     * @return 匹配总数
     */
    long selectCount(@Param("model") SysFormula model);

    /**
     * 编码查重 / 执行期解析（F14-BE-006）：按编码取公式（uk_formula_code 全局唯一）。
     * Service 层查重先例 i18n 语言码 / number 规则编码：save 时同编码即抛
     * CS_FORMULA_CODE_DUPLICATED；消费方按 field_code 引用执行时由此定位公式
     * （停用行由执行侧按启用状态拒绝，报 CS_FORMULA_NOT_FOUND）。
     *
     * @param fieldCode 公式编码
     * @return 同编码公式；不存在时返回 null
     */
    SysFormula selectByCode(@Param("fieldCode") String fieldCode);

    /**
     * 启停用（F14-BE-006）：仅更新 field_enable 与修改人/修改时间，不触碰其他字段
     * （对齐 updateEnable 先例）；停用后命名公式对执行期引用不可见，管理列表仍可查询
     * 与重新启用。
     *
     * @param fieldId     公式 ID
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
