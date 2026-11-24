package com.eric.base.sys.form.service;

import com.eric.base.common.base.model.Pages;
import com.eric.base.common.exception.AuthorizationException;
import com.eric.base.common.utils.RoleUtil;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import com.eric.base.sys.form.dao.SysFormDataDao;
import com.eric.base.sys.form.dao.SysFormDao;
import com.eric.base.sys.form.dao.SysFormVersionDao;
import com.eric.base.sys.form.enums.SysFormVersionStatus;
import com.eric.base.sys.form.model.SysFormData;
import com.eric.base.sys.form.model.SysFormDataErrorDo;
import com.eric.base.sys.form.model.SysForm;
import com.eric.base.sys.form.model.SysFormSchemaDo;
import com.eric.base.sys.form.model.SysFormVersion;
import com.eric.base.sys.form.properties.SysFormConstants;
import com.eric.base.sys.form.request.SysFormDataQuery;
import com.eric.base.sys.form.request.SysFormDataSaveRequest;
import com.eric.base.sys.form.request.SysFormDataValidateRequest;
import com.eric.base.sys.form.response.SysFormDataDetailVo;
import com.eric.base.sys.form.response.SysFormDataSavedVo;
import com.eric.base.sys.form.response.SysFormDataValidateVo;
import com.eric.base.sys.form.response.SysFormDataVo;
import com.eric.base.sys.form.service.engine.FormDataBasicValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_DATA_FORBIDDEN;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_DATA_INVALID;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_DATA_NOT_FOUND;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_DISABLED;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_NO_PUBLISHED_VERSION;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_NOT_FOUND;

/**
 * 表单数据引擎服务
 *
 * @author Eric
 * @date 2026/11/24
 * @description 承载数据面保存 / 回显 / 删除 / 查询与独立校验（F15-BE-003，API 分册 §4；
 *              功能设计 §6 数据引擎与集成口径）：JSON 存储、数据与定义分离存储（ADR-07，
 *              数据引擎不承载任何具体业务逻辑——架构原则 3）。Controller / Resolver 随
 *              F15-BE-006 落地，本服务只承载业务（AGENTS §3：Service 承载业务、不得直连
 *              Mapper）。
 *              <ul>
 *              <li><b>保存校验链</b>（功能设计 §6.1 按序执行，全部服务端权威；分册 §4.3
 *                  S0~S7）：S0 资源前置（表单存在且启用、存在发布版本——按 formId 锁定
 *                  field_current_version_id 指向版本 Schema 求值，原则 3）→ S1 字段白名单
 *                  （未知字段拒绝 CS_FORM_UNKNOWN_FIELD，分册 §7.4 定稿①）→ S2 基础校验
 *                  （{@link FormDataBasicValidator}：必填 / 类型 / 长度 / 枚举选项值 /
 *                  明细行结构与行数上限，失败以 CS_FORM_DATA_INVALID message 前缀 + 首条
 *                  中文摘要抛出）→ S3 显隐复算与提交裁剪 / S4 动态校验（F15-BE-004 扩展点）
 *                  → S5 级联复核 / S6 公式重算（F15-BE-005 扩展点）→ S7 字段权限裁剪
 *                  （F15-BE-004 扩展点）。一期 save 仅承载新增（无实例更新接口，一期只增
 *                  不改——分册 §4.4）；field_creator_id 由服务端绑定会话用户（不信任请求体
 *                  用户标识），business_id 随请求绑定。</li>
 *              <li><b>独立校验</b>（分册 §4.4）：与 save 完全共用同一校验引擎与校验链
 *                  （S0~S2 同序同语义）、不落库；字段级结果以 data.errors 结构化承载
 *                  （HTTP 200、code 为成功，对齐公式 validate / simulate 先例），S0/S1
 *                  资源与确定性拒绝仍以 CS_FORM_* 异常返回。动态校验（S4）随 F15-BE-004
 *                  并联后 errors 由基础段与动态段合并产出。</li>
 *              <li><b>回显</b>（分册 §4.5）：数据行不存在 → CS_FORM_DATA_NOT_FOUND；归属
 *                  判定（R06 定稿，功能设计 §12.2）：本人数据按 field_creator_id 判定、
 *                  SYS_FORM_ADMIN / SYS_ADMIN 放行全部，非本人且非管理员 →
 *                  CS_FORM_DATA_FORBIDDEN（code = FORBIDDEN + message 前缀，先例日程归属
 *                  判定）；响应含数据本体 + 实例锁定版本 Schema（原则 2「在途实例用原版本」，
 *                  协议头以主档元数据归一）。</li>
 *              <li><b>查询</b>（分册 §4.6）：单一表单维度（fieldFormId 必填，不存在抛
 *                  CS_FORM_NOT_FOUND）；Service 层强制注入归属过滤——非管理员覆盖
 *                  fieldCreatorId 为会话用户（不信任请求体传入的用户标识，先例流程
 *                  scope=myCreated 强制注入），管理员保留传入筛选（仅管理员生效）；分页
 *                  { body, pages } 形态，总数经 metadata.total（由 Controller 组装，Service
 *                  出 list + count 双入口，先例 SysFormService）；列表行不携带 formData
 *                  字段值内容。</li>
 *              <li><b>删除</b>（分册 §4.7）：创建人或管理员；数据行不存在 →
 *                  CS_FORM_DATA_NOT_FOUND，非创建人且非管理员 → CS_FORM_DATA_FORBIDDEN；
 *                  物理删除（sys_form_data 流水结构，一期只增不改 + 物理删除）。</li>
 *              <li><b>JSON 双形态承载</b>（先例 sys_number_rule.field_rule /
 *                  SysFormService）：field_form_data 存储态为原文 JSON 字符串（Jackson
 *                  序列化，禁止手工拼串），load 响应解析为字段值 Map 对象。</li>
 *              </ul>
 *              三种拒绝语义（R06 定稿，功能设计 §12.2 可独立读通口径）：未登录 →
 *              UNAUTHORIZED（鉴权层，Service 侧防御性兜底）；无接口权限 → FORBIDDEN
 *              （resolver 层，登录即可口径下仅未登录触发，随 F15-BE-006）；非本人非管理员
 *              访问他人数据 → CS_FORM_DATA_FORBIDDEN（code = FORBIDDEN + message 前缀，
 *              本服务层强制）。
 */
@Service
public class SysFormDataService {

    private static final Logger log = LoggerFactory.getLogger(SysFormDataService.class);

    /** 表单数据 JSON 序列化 / 解析（存储态字符串 ↔ 字段值 Map，禁止手工拼串） */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final SysFormDataDao dataDao;

    private final SysFormDao formDao;

    private final SysFormVersionDao versionDao;

    private final FormDataBasicValidator basicValidator;

    public SysFormDataService(SysFormDataDao dataDao, SysFormDao formDao,
                              SysFormVersionDao versionDao, FormDataBasicValidator basicValidator) {
        this.dataDao = dataDao;
        this.formDao = formDao;
        this.versionDao = versionDao;
        this.basicValidator = basicValidator;
    }

    /* ==================== 保存（一期仅新增，分册 §4.3） ==================== */

    /**
     * 保存表单数据（分册 §4.3，事务内）。校验链按序执行：S0 资源前置 → S1 字段白名单
     * （未知字段拒绝）→ S2 基础校验 → 载荷归一（Schema 字段序 + 明细行结构归一）落库。
     * S3 显隐复算裁剪 / S4 动态校验 / S5 级联复核 / S6 公式重算 / S7 权限裁剪为
     * F15-BE-004 / F15-BE-005 的并联扩展点（见保存校验链注记），并联后落库形态 =
     * 裁剪与重算后的最终 JSON。fieldId 沿用前端 getInitID 预生成值；field_creator_id
     * 绑定会话用户（R06：数据归属 = 创建人，提交时由服务端绑定）；field_process_instance_id
     * 一期不写入（v1.8 BPM 预留列）。
     *
     * @param request 保存体（fieldId / formId / formData 必填，businessId 可选）
     * @return 保存响应 { fieldId, formData }（formData 为服务端最终落库形态）
     */
    @Transactional
    public SysFormDataSavedVo save(SysFormDataSaveRequest request) {
        if (null == request) {
            throw new IllegalArgumentException("保存请求不能为空");
        }
        String fieldId = StringUtil.requireText(request.getFieldId(), "fieldId");
        String formId = StringUtil.requireText(request.getFormId(), "formId");
        Map<String, Object> formData = requireFormData(request.getFormData());
        String operatorId = requireSessionUser();

        // S0 资源前置：表单存在且启用、存在发布版本（锁定 field_current_version_id 版本行）
        SysFormVersion versionRow = requirePublishedVersion(formId);
        SysFormSchemaDo schema = parseSchema(versionRow.getFieldSchemaJson());
        // S1 字段白名单（未知字段拒绝）+ S2 基础校验（必填/类型/长度/枚举选项值/明细行结构与行数上限）
        List<SysFormDataErrorDo> errors = basicValidator.validate(schema, formData);
        requireBasicValid(errors);
        // 载荷归一（Schema 字段序 + 明细行结构归一，明细行数据结构归一落库形态）
        Map<String, Object> payload = basicValidator.normalize(schema, formData);
        // S3 显隐复算与提交裁剪 / S4 动态校验（F15-BE-004 扩展点：FormRuleEngine 同一条件
        //   求值器复算显隐、隐藏字段值裁剪不落库，规则 setRequired/setValidate 与静态校验合并裁决）；
        // S5 级联选项复核 / S6 公式重算（F15-BE-005 扩展点：提交值 ∈ 父项当前条件允许选项集，
        //   type=formula 字段经 FormulaEngine 服务内重算以服务端结果为准回填 payload）；
        // S7 字段权限裁剪（F15-BE-004 扩展点：permission=hidden 无论渲染态一律裁剪）。
        // 并联后以裁剪与重算后的最终 payload 序列化落库（ADR-08 持久层 / ADR-09）。

        Date now = new Date();
        SysFormData model = new SysFormData();
        model.setFieldId(fieldId);
        model.setFieldFormId(formId);
        model.setFieldFormVersionId(versionRow.getFieldId());
        model.setFieldBusinessId(StringUtil.trimToNull(request.getBusinessId()));
        model.setFieldProcessInstanceId(null);
        model.setFieldFormData(toStoreJson(payload));
        model.setFieldCreatorId(operatorId);
        model.setFieldCreateTime(now);
        dataDao.insert(model);
        log.info("form data saved: id={}, formId={}, versionId={}, businessId={}",
                fieldId, formId, versionRow.getFieldId(), model.getFieldBusinessId());

        SysFormDataSavedVo vo = new SysFormDataSavedVo();
        vo.setFieldId(fieldId);
        vo.setFormData(payload);
        return vo;
    }

    /* ==================== 独立校验（与 save 共用引擎，不落库，分册 §4.4） ==================== */

    /**
     * 独立校验表单数据（分册 §4.4）：与 save 完全共用同一校验引擎与校验链（S0~S2 本任务
     * 承载段，S4 动态校验随 F15-BE-004 并联）、不落库。字段级结果以 data.errors 结构化
     * 承载（正常业务结果，HTTP 200、code 为成功）；S0/S1 资源与确定性拒绝仍以 CS_FORM_*
     * 异常返回（与 save 同语义）。pass = true 时回带服务端最终数据预览（同 save 落库形态）。
     *
     * @param request 校验体（formId / formData 必填，无 fieldId——不落库）
     * @return { pass, errors, formData }
     */
    public SysFormDataValidateVo validate(SysFormDataValidateRequest request) {
        if (null == request) {
            throw new IllegalArgumentException("校验请求不能为空");
        }
        String formId = StringUtil.requireText(request.getFormId(), "formId");
        Map<String, Object> formData = requireFormData(request.getFormData());

        // S0 资源前置（与 save 同语义，资源类拒绝以 CS_FORM_* 异常返回）
        SysFormVersion versionRow = requirePublishedVersion(formId);
        SysFormSchemaDo schema = parseSchema(versionRow.getFieldSchemaJson());
        // S1 字段白名单（未知字段拒绝，与 save 同语义）+ S2 基础校验
        List<SysFormDataErrorDo> errors = basicValidator.validate(schema, formData);
        // S4 动态校验（F15-BE-004 扩展点：规则 setRequired/setValidate 与静态校验合并裁决，
        //   动态段错误并入 errors——ruleId 由动态段回填）；S3/S5/S6 同 save 扩展点注记。

        SysFormDataValidateVo vo = new SysFormDataValidateVo();
        vo.setPass(errors.isEmpty());
        vo.setErrors(errors);
        if (errors.isEmpty()) {
            vo.setFormData(basicValidator.normalize(schema, formData));
        } else {
            vo.setFormData(null);
        }
        return vo;
    }

    /* ==================== 回显（分册 §4.5） ==================== */

    /**
     * 数据详情 / 回显（分册 §4.5）：数据本体 + 实例锁定版本 Schema（渲染器详情态 / 编辑态
     * 复用，原则 2「在途实例用原版本」——不随主档当前版本漂移）。前置拒绝：数据行不存在 →
     * CS_FORM_DATA_NOT_FOUND；归属判定（R06 定稿）：本人数据（field_creator_id = 会话用户）
     * 或 SYS_FORM_ADMIN / SYS_ADMIN 放行，非本人且非管理员 → CS_FORM_DATA_FORBIDDEN
     * （code = FORBIDDEN + message 前缀）。schema 协议头以主档权威元数据归一（先例
     * SysFormService#normalizeHeader）；formData 读取层裁剪（permission=hidden 字段不下发，
     * ADR-08 读取层）随 F15-BE-004 并联，本任务透传落库形态。
     *
     * @param fieldId 数据行 ID
     * @return 详情（formData 解析后的字段值对象 + 实例锁定版本 Schema）
     */
    public SysFormDataDetailVo load(String fieldId) {
        String id = StringUtil.requireText(fieldId, "fieldId");
        SysFormData row = requireData(id);
        assertDataAccessible(row, "查询");

        SysForm main = formDao.selectById(row.getFieldFormId());
        SysFormVersion versionRow = StringUtil.isEmpty(row.getFieldFormVersionId())
                ? null : versionDao.selectById(row.getFieldFormVersionId());
        SysFormSchemaDo schema = null == versionRow ? null : parseSchema(versionRow.getFieldSchemaJson());
        // 协议头归一（分册 §2.4 口径延伸）：formId / formCode / formName 以主档权威元数据为准，
        // 仅改写响应对象不动存储态快照；version 恒为行级 field_version（实例锁定发布版本号）
        normalizeHeader(schema, main);
        if (null != schema && null != versionRow) {
            schema.setVersion(versionRow.getFieldVersion());
        }

        SysFormDataDetailVo vo = new SysFormDataDetailVo();
        vo.setFieldId(row.getFieldId());
        vo.setFieldFormId(row.getFieldFormId());
        vo.setFieldFormVersionId(row.getFieldFormVersionId());
        vo.setFieldBusinessId(row.getFieldBusinessId());
        vo.setFieldCreatorId(row.getFieldCreatorId());
        vo.setFieldCreateTime(row.getFieldCreateTime());
        vo.setFormData(parseFormData(row.getFieldFormData()));
        vo.setSchema(schema);
        return vo;
    }

    /* ==================== 查询（归属强制过滤 + 分页，分册 §4.6） ==================== */

    /**
     * 数据列表（分册 §4.6）：单一表单维度（fieldFormId 必填，表单不存在 → CS_FORM_NOT_FOUND）；
     * 归属强制过滤（R06 定稿）——非管理员 Service 层强制覆盖 fieldCreatorId 为会话用户
     * （不信任请求体传入的用户标识，先例流程 scope=myCreated 强制注入），管理员保留传入
     * 筛选（仅管理员生效）；businessId / processInstanceId 精确、创建时间区间、分页；列表行
     * 不携带 formData 字段值内容（JSON 体积不可控，详情经 /load）。
     *
     * @param query 查询条件
     * @param pages 分页参数（可空，needPage=false 返回全部）
     * @return 列表行（metadata.total 由 Controller 组装，总数见 {@link #count}）
     */
    public List<SysFormDataVo> list(SysFormDataQuery query, Pages pages) {
        SysFormDataQuery criteria = requireListCriteria(query);
        List<SysFormData> rows = dataDao.selectListByQuery(criteria, pages);
        List<SysFormDataVo> result = new ArrayList<>(rows.size());
        for (SysFormData row : rows) {
            result.add(toRowVo(row));
        }
        return result;
    }

    /**
     * 数据列表总数：查询条件与归属强制过滤和 {@link #list} 完全一致，供响应 metadata.total。
     *
     * @param query 查询条件
     * @return 匹配总数
     */
    public long count(SysFormDataQuery query) {
        SysFormDataQuery criteria = requireListCriteria(query);
        return dataDao.selectCountByQuery(criteria);
    }

    /* ==================== 删除（创建人或管理员，分册 §4.7） ==================== */

    /**
     * 删除数据（分册 §4.7，事务内）：数据行不存在 → CS_FORM_DATA_NOT_FOUND；非创建人且
     * 非管理员 → CS_FORM_DATA_FORBIDDEN（创建人或管理员口径，R06 定稿）；物理删除
     * （sys_form_data 流水结构一期只增不改 + 物理删除，无软删语义）。
     *
     * @param fieldId 数据行 ID
     */
    @Transactional
    public void delete(String fieldId) {
        String id = StringUtil.requireText(fieldId, "fieldId");
        SysFormData row = requireData(id);
        assertDataAccessible(row, "删除");
        dataDao.deleteById(id);
        log.info("form data deleted: id={}, formId={}, creatorId={}",
                id, row.getFieldFormId(), row.getFieldCreatorId());
    }

    /* ==================== 内部：资源前置与归属判定 ==================== */

    /**
     * S0 资源前置（分册 §4.3 / §4.4）：表单存在（CS_FORM_NOT_FOUND）→ 启用（停用关闭填写
     * 入口与数据提交，CS_FORM_DISABLED——定义面与数据查询保留）→ 存在发布版本（主档
     * field_current_version_id 指向 PUBLISHED 版本行，CS_FORM_NO_PUBLISHED_VERSION）。
     * 返回锁定的当前发布版本行（原则 3：一期提交入口恒取当前发布版本求值）。
     */
    private SysFormVersion requirePublishedVersion(String formId) {
        SysForm exist = formDao.selectById(formId);
        if (null == exist) {
            throw formError(CS_FORM_NOT_FOUND, "表单不存在: " + formId);
        }
        if (Boolean.FALSE.equals(exist.getFieldEnable())) {
            throw formError(CS_FORM_DISABLED, "表单已停用，填写与提交入口已关闭: " + exist.getFieldFormCode());
        }
        if (StringUtil.isEmpty(exist.getFieldCurrentVersionId())) {
            throw formError(CS_FORM_NO_PUBLISHED_VERSION, "表单无已发布版本，填写与提交入口不可用: "
                    + exist.getFieldFormCode());
        }
        SysFormVersion versionRow = versionDao.selectById(exist.getFieldCurrentVersionId());
        if (null == versionRow || SysFormVersionStatus.PUBLISHED != SysFormVersionStatus.of(versionRow.getFieldStatus())) {
            // 指针指向行缺失或状态漂移（正常状态机下不可达）：按无发布版本处理
            throw formError(CS_FORM_NO_PUBLISHED_VERSION, "表单当前发布版本不可用，填写与提交入口不可用: "
                    + exist.getFieldFormCode());
        }
        return versionRow;
    }

    /**
     * 数据行加载，不存在抛 {@code CS_FORM_DATA_NOT_FOUND}（分册 §7.1 第 13 项：data/load
     * / delete 承载）
     */
    private SysFormData requireData(String fieldId) {
        SysFormData row = dataDao.selectById(fieldId);
        if (null == row) {
            throw formError(CS_FORM_DATA_NOT_FOUND, "表单数据不存在: " + fieldId);
        }
        return row;
    }

    /**
     * 数据归属判定（R06 定稿，功能设计 §12.2；先例日程归属判定）：会话用户为创建人（
     * field_creator_id 判定）或持有 SYS_FORM_ADMIN / SYS_ADMIN（RoleUtil.hasAnyRole，
     * SYS_ADMIN 自动包含全部权限）放行；否则拒绝——CS_FORM_DATA_FORBIDDEN 以
     * {@link AuthorizationException}（code = FORBIDDEN + message 前缀）抛出，经
     * GlobalExceptionHandler 鉴权分支返回 BaseResponse.code = FORBIDDEN。
     * 未登录（无会话用户）防御性兜底抛 UNAUTHORIZED（鉴权层语义，正常由 resolver 层先行
     * 拦截，随 F15-BE-006 接入）。
     */
    private void assertDataAccessible(SysFormData row, String action) {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (null == user || StringUtil.isEmpty(user.getFieldId())) {
            throw new AuthorizationException("UNAUTHORIZED", "登录已过期，请重新登录");
        }
        if (user.getFieldId().equals(row.getFieldCreatorId())) {
            return;
        }
        if (RoleUtil.hasAnyRole(user, SysFormConstants.ROLE_SYS_FORM_ADMIN)) {
            return;
        }
        log.info("form data {} rejected (not owner, not admin): dataId={}, creatorId={}, userId={}",
                action, row.getFieldId(), row.getFieldCreatorId(), user.getFieldId());
        throw new AuthorizationException("FORBIDDEN", CS_FORM_DATA_FORBIDDEN + ": 仅创建人或管理员可"
                + action + "他人表单数据: " + row.getFieldId());
    }

    /** 会话用户绑定（R06：提交时由服务端绑定 field_creator_id，未登录防御性兜底 UNAUTHORIZED） */
    private String requireSessionUser() {
        String operatorId = UserUtil.getCurrentUserId();
        if (StringUtil.isEmpty(operatorId)) {
            throw new AuthorizationException("UNAUTHORIZED", "登录已过期，请重新登录");
        }
        return operatorId;
    }

    /* ==================== 内部：查询条件与归属注入 ==================== */

    /**
     * 列表条件校验与归属强制注入：fieldFormId 必填（单一表单维度）且表单存在
     * （CS_FORM_NOT_FOUND）；非管理员强制覆盖 fieldCreatorId 为会话用户（不信任请求体
     * 传入的用户标识，归属强制过滤优先——API 分册 §4.8），管理员保留传入筛选（仅管理员
     * 生效，可空查全部）。
     */
    private SysFormDataQuery requireListCriteria(SysFormDataQuery query) {
        SysFormDataQuery criteria = new SysFormDataQuery();
        if (null != query) {
            criteria.setFieldFormId(StringUtil.trimToNull(query.getFieldFormId()));
            criteria.setFieldBusinessId(StringUtil.trimToNull(query.getFieldBusinessId()));
            criteria.setFieldProcessInstanceId(StringUtil.trimToNull(query.getFieldProcessInstanceId()));
            criteria.setFieldCreatorId(StringUtil.trimToNull(query.getFieldCreatorId()));
            criteria.setFieldCreateTimeBegin(query.getFieldCreateTimeBegin());
            criteria.setFieldCreateTimeEnd(query.getFieldCreateTimeEnd());
        }
        if (StringUtil.isEmpty(criteria.getFieldFormId())) {
            throw new IllegalArgumentException("fieldFormId 必填，数据列表在单一表单维度");
        }
        if (null == formDao.selectById(criteria.getFieldFormId())) {
            throw formError(CS_FORM_NOT_FOUND, "表单不存在: " + criteria.getFieldFormId());
        }
        SysOrgUser user = UserUtil.getCurrentUser();
        if (null == user || StringUtil.isEmpty(user.getFieldId())) {
            throw new AuthorizationException("UNAUTHORIZED", "登录已过期，请重新登录");
        }
        if (!RoleUtil.hasAnyRole(user, SysFormConstants.ROLE_SYS_FORM_ADMIN)) {
            // 归属强制过滤（R06）：结果为本人数据子集，不泄露他人数据存在性；请求体
            // fieldCreatorId 条件忽略（不信任请求体用户标识）
            criteria.setFieldCreatorId(user.getFieldId());
        }
        return criteria;
    }

    /* ==================== 内部：行装配与 JSON 承载 ==================== */

    /** 列表行 Vo 装配（分册 §4.6）：行字段不携带 formData 内容（Mapper BaseColumns 同口径） */
    private static SysFormDataVo toRowVo(SysFormData row) {
        SysFormDataVo vo = new SysFormDataVo();
        vo.setFieldId(row.getFieldId());
        vo.setFieldFormId(row.getFieldFormId());
        vo.setFieldFormVersionId(row.getFieldFormVersionId());
        vo.setFieldBusinessId(row.getFieldBusinessId());
        vo.setFieldCreatorId(row.getFieldCreatorId());
        vo.setFieldCreateTime(row.getFieldCreateTime());
        return vo;
    }

    /**
     * load 响应 Schema 协议头归一（分册 §2.4 口径延伸）：协议头 formId / formCode /
     * formName 以主档权威元数据为准，仅改写响应对象不动存储态快照；主档缺失（正常状态机
     * 下不可达——表单删除前置实例数据校验）跳过归一防御性下发存储态。
     */
    private static void normalizeHeader(SysFormSchemaDo schema, SysForm main) {
        if (null == schema || null == main) {
            return;
        }
        schema.setFormId(main.getFieldId());
        schema.setFormCode(main.getFieldFormCode());
        schema.setFormName(main.getFieldFormName());
    }

    /** formData 必填校验（分册 §4.3 / §4.4 请求表：formData 必填；普通参数错误无 CS 码） */
    private static Map<String, Object> requireFormData(Map<String, Object> formData) {
        if (null == formData) {
            throw new IllegalArgumentException("formData 必填，不能为空");
        }
        return formData;
    }

    /**
     * S2 基础校验结果裁决（分册 §4.3）：errors 非空即拒绝——抛
     * {@code CS_FORM_DATA_INVALID}（message 前缀 + 首条错误中文摘要，BaseResponse.code =
     * BAD_REQUEST；字段级明细经 /data/validate 的 data.errors 结构化获取）
     */
    private static void requireBasicValid(List<SysFormDataErrorDo> errors) {
        if (null == errors || errors.isEmpty()) {
            return;
        }
        SysFormDataErrorDo first = errors.get(0);
        String summary = first.getMessage() + (errors.size() > 1
                ? "（共 " + errors.size() + " 处校验错误，明细经 /data/validate 获取）" : "");
        throw formError(CS_FORM_DATA_INVALID, summary);
    }

    /**
     * 存储态 JSON 归一（Map → 原文字符串）：JSON 列存储态为原文（先例 sys_number_rule.
     * field_rule 双形态承载），序列化失败属程序异常，抛 IllegalStateException 令事务回滚
     */
    private static String toStoreJson(Map<String, Object> payload) {
        try {
            return JSON_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("表单数据 JSON 序列化失败", e);
        }
    }

    /**
     * 存储态 formData 解析（String 原文 → 字段值 Map）：损坏 JSON 属存储态程序异常（内容仅
     * 由保存校验链落库，非用户直填），抛 IllegalStateException；Map 形态（驱动直返对象）
     * 防御性拷贝
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseFormData(Object raw) {
        if (null == raw) {
            return null;
        }
        if (raw instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        if (raw instanceof String json && !StringUtil.isEmpty(json)) {
            try {
                return JSON_MAPPER.readValue(json, HashMap.class);
            } catch (JsonProcessingException | IllegalArgumentException e) {
                throw new IllegalStateException("表单数据 JSON 损坏，无法解析", e);
            }
        }
        return null;
    }

    /**
     * 存储态 Schema 解析（String 原文 → 协议对象）：口径同 SysFormService#parseSchema——
     * 损坏 JSON 属存储态程序异常，抛 IllegalStateException 令事务回滚
     */
    private static SysFormSchemaDo parseSchema(Object raw) {
        if (null == raw || (raw instanceof String json && StringUtil.isEmpty(json))) {
            return null;
        }
        try {
            if (raw instanceof String json) {
                return JSON_MAPPER.readValue(json, SysFormSchemaDo.class);
            }
            return JSON_MAPPER.convertValue(raw, SysFormSchemaDo.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalStateException("表单 Schema JSON 损坏，无法解析", e);
        }
    }

    /**
     * CS_* 业务错误码异常构造：message 以错误码为前缀（分册 §7.1 承载方式），经
     * GlobalExceptionHandler 既有 BAD_REQUEST 分支返回，前端按前缀识别
     */
    private static IllegalArgumentException formError(String code, String message) {
        return new IllegalArgumentException(code + ": " + message);
    }
}
