package com.eric.base.sys.form.mapper;

import com.eric.base.common.base.dao.IBaseMapper;
import com.eric.base.sys.form.model.SysFormVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 表单版本数据访问映射
 *
 * @author Eric
 * @date 2026/11/24
 * @description 与 resources/mappers/sys/form/SysFormVersionMapper.xml 一对一对接，
 *              只做 SQL 映射，不承载业务逻辑（F15-DB-001）。基础语句（insert /
 *              update / deleteById / selectById / selectList / loadModel）继承
 *              {@link IBaseMapper}；基础 update 承载「草稿行内容重写」语义（仅
 *              SET field_schema_json + 审计两列，WHERE 守护 field_status='DRAFT'
 *              防御终态行被写，分册 §5.2.6⑤）；selectList 条件 fieldFormId /
 *              fieldStatus 精确 + 分页；selectByFormId 供版本列表（field_version
 *              降序，草稿行排序位置随 F15-BE-002 定稿——分册 §9.3 条目 1）；
 *              selectMaxVersion 供发布/回滚「现存最大版本号 + 1」分配（并发竞争
 *              由 uk_form_version_form_version 唯一索引兜底，分册 §5.2.4）；
 *              freezeDraft / deprecate 为状态机流转语句（WHERE 守护起始态，与应用层
 *              状态机拒绝构成双保险，分册 §4.3 / §5.2.6⑥⑦）；deleteByFormId 供
 *              删除表单事务内清空全部版本行（分册 §5.2.6⑧）。
 */
@Mapper
public interface SysFormVersionMapper extends IBaseMapper<SysFormVersion> {

    /**
     * 条件查询总数（F15-DB-001）：过滤条件与 {@link #selectList} 完全一致（共用
     * QueryConditions 片段），供分页响应 metadata.total 使用。
     *
     * @param model 查询条件（可空字段忽略）
     * @return 匹配总数
     */
    long selectCount(@Param("model") SysFormVersion model);

    /**
     * 版本列表（F15-DB-001，分册 §5.2.6①）：按表单主档 ID 取全部版本行，按
     * field_version 降序（走 idx_form_version_form 索引）。草稿行 field_version 为
     * NULL（版本号仅发布动作分配），其在降序结果中的排序位置随 F15-BE-002 定稿
     * 并回写 API 分册 §3.9——分册 §9.3 待确认事项条目 1，不阻塞建表。
     *
     * @param formId 表单主档 ID（sys_form.field_id）
     * @return 版本行清单（含草稿行与全部历史发布行）
     */
    List<SysFormVersion> selectByFormId(@Param("formId") String formId);

    /**
     * 现存最大版本号（F15-DB-001，分册 §5.2.6②）：发布/回滚按「最大 + 1」分配
     * 整数版本号；该表单从未发布时返回 null，由 Service 以 0 兜底后 +1。两管理员
     * 并发发布同表单的版本号竞争由 uk_form_version_form_version 唯一索引兜底
     * （失败方 duplicate key 回滚重试，分册 §5.2.4）。
     *
     * @param formId 表单主档 ID
     * @return 现存最大整数版本号；无发布版本时返回 null
     */
    Integer selectMaxVersion(@Param("formId") String formId);

    /**
     * 草稿冻结（F15-DB-001，分册 §5.2.6⑥）：发布动作对草稿行原地冻结回填——
     * field_version 回填分配号、field_status DRAFT→'PUBLISHED'、field_published_at
     * 回填发布时刻并刷新修改审计；WHERE 守护 field_status='DRAFT'，终态行不可能
     * 被重复冻结。与主档指针切换（SysFormMapper.updateCurrentVersionId）、原当前
     * 发布版本转 DEPRECATED 同事务（分册 §5.2.6 事务口径）。
     *
     * @param fieldId      草稿行 ID
     * @param fieldVersion 发布分配的整数版本号（「现存最大 + 1」）
     * @param publishedAt  发布时刻
     * @param updaterId    修改人 ID（发布动作发起者）
     * @param updateTime   修改时间
     * @return 影响行数
     */
    int freezeDraft(@Param("fieldId") String fieldId,
                    @Param("fieldVersion") Integer fieldVersion,
                    @Param("publishedAt") Date publishedAt,
                    @Param("updaterId") String updaterId,
                    @Param("updateTime") Date updateTime);

    /**
     * 旧版本退役（F15-DB-001，分册 §5.2.6⑦）：发布/回滚事务内将原当前发布版本
     * PUBLISHED→'DEPRECATED'（被新版本替代）并刷新修改审计；WHERE 守护
     * field_status='PUBLISHED'，草稿行与已退役行均不可经此语句流转。
     *
     * @param fieldId    原当前发布版本行 ID
     * @param updaterId  修改人 ID
     * @param updateTime 修改时间
     * @return 影响行数
     */
    int deprecate(@Param("fieldId") String fieldId,
                  @Param("updaterId") String updaterId,
                  @Param("updateTime") Date updateTime);

    /**
     * 删除表单全部版本行（F15-DB-001，分册 §5.2.6⑧）：删除表单事务内先删该表单
     * 全部版本行再删主档行（前置实例数据存在性校验 CS_FORM_HAS_DATA 由
     * SysFormDataDao 承担，随 F15-DB-002 落地），走 idx_form_version_form 索引。
     *
     * @param formId 表单主档 ID
     * @return 影响行数
     */
    int deleteByFormId(@Param("formId") String formId);
}
