package com.eric.base.sys.form.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.form.model.SysForm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * 表单主档数据访问映射
 *
 * @author Eric
 * @date 2026/11/24
 * @description 与 resources/mappers/sys/form/SysFormMapper.xml 一对一对接，
 *              只做 SQL 映射，不承载业务逻辑（F15-DB-001）。基础语句（insert /
 *              update / deleteById / selectById / selectList / loadModel）继承
 *              {@link IBaseMapper}；selectList 条件 fieldFormCode / fieldFormName
 *              模糊、fieldStatus / fieldEnable 精确，排序固定创建时间降序；
 *              selectCount 与 selectList 条件一致，供列表 metadata.total；
 *              selectByCode 供编码全局查重（uk_form_code 库级兜底，save 时同编码
 *              抛 CS_FORM_CODE_DUPLICATED）与按 field_form_code 精确定位；
 *              updateEnable 供 /define/enable 通道启停用（只更新自身一行，
 *              本表无树形语义不级联）；updateCurrentVersionId 供发布/回滚事务
 *              切换当前发布版本指针并同步主档状态（分册 §5.1.6⑦）。
 */
@Mapper
public interface SysFormMapper extends IBaseMapper<SysForm> {

    /**
     * 管理列表总数（F15-DB-001）：过滤条件与 {@link #selectList} 完全一致（共用
     * QueryConditions 片段），供响应 metadata.total 使用。
     *
     * @param model 查询条件（可空字段忽略）
     * @return 匹配总数
     */
    long selectCount(@Param("model") SysForm model);

    /**
     * 编码查重 / 精确定位（F15-DB-001）：按编码取表单（field_form_code 全局唯一，
     * uk_form_code 库级兜底）。Service 层保存期查重：save 时同编码即抛
     * CS_FORM_CODE_DUPLICATED；编码为跨模块引用键时由此精确定位。
     *
     * @param fieldCode 表单编码（对应列 field_form_code）
     * @return 同编码表单；不存在时返回 null
     */
    SysForm selectByCode(@Param("fieldCode") String fieldCode);

    /**
     * 启停用（F15-DB-001）：仅更新 field_enable 与修改人/修改时间，不触碰其他字段；
     * 停用后关闭填写入口与数据提交（数据面拒绝 CS_FORM_DISABLED），定义面与数据
     * 查询保留，可重新启用。
     *
     * @param fieldId     表单 ID
     * @param fieldEnable 是否启用
     * @param updaterId   修改人 ID
     * @param updateTime  修改时间
     * @return 影响行数
     */
    int updateEnable(@Param("fieldId") String fieldId,
                     @Param("fieldEnable") Boolean fieldEnable,
                     @Param("updaterId") String updaterId,
                     @Param("updateTime") Date updateTime);

    /**
     * 发布/回滚切换当前版本指针（F15-DB-001，分册 §5.1.6⑦）：与版本行操作
     * （草稿冻结 freezeDraft、原当前发布版本转 DEPRECATED deprecate）同事务执行，
     * 保证「草稿冻结 → 旧版本转 DEPRECATED → 主档指针切换 + 状态同步」动作原子性
     * ——指针（field_current_version_id）与状态（field_status：首次发布 DRAFT→
     * PUBLISHED；回滚后保持 PUBLISHED 并指向回滚产生的新版本行）必须成对切换，
     * 拆开两条语句将出现「指针指向新版本而状态仍 DRAFT」的中间态。发布/回滚
     * 编排由 SysFormService 承载（F15-BE-002）。
     *
     * @param fieldId              表单 ID
     * @param fieldStatus          同步后的主档状态（PUBLISHED）
     * @param fieldCurrentVersionId 当前发布版本行 id（sys_form_version.field_id）
     * @param updaterId            修改人 ID
     * @param updateTime           修改时间
     * @return 影响行数
     */
    int updateCurrentVersionId(@Param("fieldId") String fieldId,
                               @Param("fieldStatus") String fieldStatus,
                               @Param("fieldCurrentVersionId") String fieldCurrentVersionId,
                               @Param("updaterId") String updaterId,
                               @Param("updateTime") Date updateTime);
}
