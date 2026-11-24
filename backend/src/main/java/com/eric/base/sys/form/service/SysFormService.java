package com.eric.base.sys.form.service;

import com.eric.base.common.base.model.Pages;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.form.dao.SysFormDataDao;
import com.eric.base.sys.form.dao.SysFormDao;
import com.eric.base.sys.form.dao.SysFormVersionDao;
import com.eric.base.sys.form.enums.SysFormStatus;
import com.eric.base.sys.form.enums.SysFormVersionStatus;
import com.eric.base.sys.form.model.SysForm;
import com.eric.base.sys.form.model.SysFormSchemaDo;
import com.eric.base.sys.form.model.SysFormSchemaErrorDo;
import com.eric.base.sys.form.model.SysFormVersion;
import com.eric.base.sys.form.properties.SysFormConstants;
import com.eric.base.sys.form.request.SysFormRollbackRequest;
import com.eric.base.sys.form.request.SysFormSaveRequest;
import com.eric.base.sys.form.request.SysFormUpdateRequest;
import com.eric.base.sys.form.response.SysFormVersionCreatedVo;
import com.eric.base.sys.form.response.SysFormVersionVo;
import com.eric.base.sys.form.response.SysFormVo;
import com.eric.base.sys.form.service.engine.FormDataBasicValidator;
import com.eric.base.sys.form.service.engine.FormFormulaEvaluator;
import com.eric.base.sys.form.service.engine.FormSchemaValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_CODE_DUPLICATED;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_HAS_DATA;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_NO_DRAFT;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_NOT_FOUND;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_VERSION_NOT_FOUND;
import static com.eric.base.sys.form.properties.SysFormConstants.CS_FORM_VERSION_STATE_INVALID;

/**
 * 表单定义与版本管理服务
 *
 * @author Eric
 * @date 2026/11/24
 * @description 承载定义面九动作（F15-BE-002，API 分册 §3；功能设计 §5 不可变版本五原则 /
 *              §5.2 状态机）的业务编排：列表 / 详情 / 新建（主档 + 初始草稿行）/ 更新（只写
 *              DRAFT 行）/ 启停用（只更新自身一行，先例 org）/ 删除（存在实例数据禁删，分册
 *              §3.7 定稿②）/ 发布（草稿行原地冻结为不可变版本行）/ 版本列表 / 回滚（复制
 *              历史版本副本重发布为新版本，不产生指针回拨）。Controller / Resolver 随
 *              F15-BE-006 落地，本服务只承载业务（AGENTS §3：Service 承载业务、不得直连
 *              Mapper，Dao 与 Mapper 一对一由 F15-DB-001 提供）。
 *              <ul>
 *              <li>编码全局查重（先例 formula）：save 时同编码即抛 CS_FORM_CODE_DUPLICATED，
 *                  uk_form_code 库级兜底；编码为保存后稳定标识，update 不受理编码。</li>
 *              <li>保存 / 更新前 Schema 结构校验阻断：{@link FormSchemaValidator}（P2 结构 +
 *                  P3 资源上限，以存储原文 String 形态校验——体积上限 MAX_SCHEMA_JSON_LENGTH
 *                  仅 String 入口检查），errors 非空即拒绝，首错以 CS_FORM_*: message 前缀
 *                  抛出（对齐 SysFormulaService 错误形态，经 GlobalExceptionHandler
 *                  BAD_REQUEST 分支返回，前端按前缀识别错误码）。</li>
 *              <li>状态机（功能设计 §5.2 / 分册 §3.11）：主档 DRAFT ──publish──▶ PUBLISHED
 *                  （启停用走 field_enable 独立字段）；版本行 DRAFT ──publish──▶ PUBLISHED
 *                  ──被新版本替代──▶ DEPRECATED。非法流转确定性拒绝：无草稿行时直接发布
 *                  （P1）→ CS_FORM_NO_DRAFT；终态行（PUBLISHED / DEPRECATED）内容更新由
 *                  契约层「只写 DRAFT 行」保证不可达，实现层以 freezeDraft / update 语句的
 *                  WHERE 守护（field_status='DRAFT'）兜底，影响行数不符时防御性抛
 *                  CS_FORM_VERSION_STATE_INVALID（双保险）。</li>
 *              <li>发布（原则 1）：草稿行原地冻结（版本号 = 现存最大 + 1 回填、状态
 *                  DRAFT → PUBLISHED、published_at 回填，内容不改写）→ 原当前发布版本转
 *                  DEPRECATED → 主档 field_current_version_id 切换 + 主档状态置 PUBLISHED，
 *                  单事务完成；快照内容（field_schema_json）发布后不可变。</li>
 *              <li>回滚（原则 5）：复制目标历史版本内容（PUBLISHED / DEPRECATED）产生新发布
 *                  版本行（新版本号、published_at 为回滚时刻、不经草稿态一步发布）→ 原当前
 *                  发布版本转 DEPRECATED → 主档指针切换——不回拨指针、不影响既有草稿行。</li>
 *              <li>发布校验链 P4（公式静态校验：表达式合法 / 引用字段存在 / 依赖图无环，服务内
 *                  调用 F14 校验服务，错误码复用 CS_FORMULA_*）随 F15-BE-005 集成
 *                  FormFormulaEvaluator 后并入 {@link #publish(String)}，本任务先落 P1 / P2 / P3。</li>
 *              <li>JSON 双形态承载（先例 sys_number_rule.field_rule / SysFormulaService）：
 *                  存储态为原文 JSON 字符串（Jackson 序列化，禁止手工拼串），load 响应解析为
 *                  {@link SysFormSchemaDo} 协议对象；协议头 formId / formCode / formName 以
 *                  主档元数据为权威归一（分册 §2.4），协议头 version 由响应层按行级
 *                  field_version 动态回填（存储态保持发布时原文，不可变原则）。</li>
 *              </ul>
 *              CS_* 错误码以异常 message 前缀承载（分册 §7.1），全部静态引用
 *              {@link SysFormConstants}，禁止手写字符串。
 */
@Service
public class SysFormService {

    private static final Logger log = LoggerFactory.getLogger(SysFormService.class);

    /** Schema JSON 序列化 / 解析（存储态字符串 ↔ 协议对象，禁止手工拼串，先例 SysFormulaService） */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final SysFormDao formDao;

    private final SysFormVersionDao versionDao;

    private final SysFormDataDao dataDao;

    private final FormSchemaValidator schemaValidator;

    private final FormFormulaEvaluator formulaEvaluator;

    public SysFormService(SysFormDao formDao, SysFormVersionDao versionDao,
                          SysFormDataDao dataDao, FormSchemaValidator schemaValidator,
                          FormFormulaEvaluator formulaEvaluator) {
        this.formDao = formDao;
        this.versionDao = versionDao;
        this.dataDao = dataDao;
        this.schemaValidator = schemaValidator;
        this.formulaEvaluator = formulaEvaluator;
    }

    /* ==================== 定义面管理 ==================== */

    /**
     * 表单定义列表（分册 §3.2）：fieldFormCode / fieldFormName 模糊、fieldStatus /
     * fieldEnable 精确（fieldStatus 非法值确定性拒绝），排序固定创建时间降序；行字段含
     * hasDraft 预判（是否存在 DRAFT 版本行）与 fieldCurrentVersion（当前发布版本号），
     * 不携带 Schema 内容（体积大，经 /load 获取）。
     *
     * @param body  查询条件（可空字段忽略）
     * @param pages 分页参数（可空，needPage=false 返回全部）
     * @return 列表行（draftSchema / publishedSchema 恒 null）
     */
    public List<SysFormVo> list(SysForm body, Pages pages) {
        SysForm criteria = normalizeCriteria(body);
        requireStatusValue(criteria.getFieldStatus());
        List<SysForm> rows = formDao.selectList(criteria, pages);
        Set<String> draftFormIds = loadDraftFormIds();
        List<SysFormVo> result = new ArrayList<>(rows.size());
        for (SysForm row : rows) {
            result.add(toRowVo(row, draftFormIds.contains(row.getFieldId())));
        }
        return result;
    }

    /**
     * 管理列表总数：查询条件与 {@link #list} 完全一致，供响应 metadata.total 使用。
     *
     * @param body 查询条件（可空字段忽略）
     * @return 匹配总数
     */
    public long count(SysForm body) {
        SysForm criteria = normalizeCriteria(body);
        requireStatusValue(criteria.getFieldStatus());
        return formDao.selectCount(criteria);
    }

    /**
     * 表单详情（分册 §3.4）：元数据行字段 + 草稿 Schema + 当前发布版本 Schema。
     * 消费口径：设计器页以 draftSchema 为编辑对象；draftSchema 为 null（再编辑场景）时
     * 以 publishedSchema 初始化画布，首次 /update 由服务端创建草稿行。
     *
     * @param fieldId 表单主档 ID
     * @return 详情（draftSchema / publishedSchema 按行存在性下发）
     */
    public SysFormVo load(String fieldId) {
        SysForm exist = requireForm(StringUtil.requireText(fieldId, "fieldId"));
        SysFormVersion draftRow = selectDraftRow(exist.getFieldId());
        SysFormVersion publishedRow = StringUtil.isEmpty(exist.getFieldCurrentVersionId())
                ? null : versionDao.selectById(exist.getFieldCurrentVersionId());
        SysFormSchemaDo draftSchema = parseSchema(null == draftRow ? null : draftRow.getFieldSchemaJson());
        SysFormSchemaDo publishedSchema = parseSchema(null == publishedRow ? null : publishedRow.getFieldSchemaJson());
        // 协议头归一（分册 §2.4）：载荷协议头以主档权威元数据为准（名称可能经 /update 变更，
        // 存储态历史载荷不回写），存储态快照内容（fields[] 等）原样下发不触碰
        normalizeHeader(draftSchema, exist);
        normalizeHeader(publishedSchema, exist);
        // 发布载荷协议头 version 回填行级版本号（分册 §4.2 口径；草稿载荷 version 恒 null）
        if (null != publishedSchema) {
            publishedSchema.setVersion(publishedRow.getFieldVersion());
        }
        return toVo(exist, null != draftRow, null == publishedRow ? null : publishedRow.getFieldVersion(),
                draftSchema, publishedSchema);
    }

    /**
     * 新建表单（分册 §3.3，事务内）。编排顺序：必填校验 → ①编码全局查重（重复抛
     * CS_FORM_CODE_DUPLICATED）→ ②Schema 结构校验（schema 缺省时以空白草稿初始化
     * {@code fields:[]}，携带时先归一协议头再校验，失败阻断）→ ③创建主档行
     * （field_status = DRAFT、field_current_version_id 为空）+ 至多一行的 DRAFT 版本行。
     * <p>fieldId 必填（前端经 GET /backend/common/initID 预生成 32 位 ID，分册 §3.3 请求表）；
     * 新建默认启用。</p>
     *
     * @param request 保存体（fieldFormCode / fieldFormName 必填，schema 可选）
     * @return 保存后的详情（含初始化后的 draftSchema）
     */
    @Transactional
    public SysFormVo save(SysFormSaveRequest request) {
        if (null == request) {
            throw new IllegalArgumentException("保存请求不能为空");
        }
        String fieldId = StringUtil.requireText(request.getFieldId(), "fieldId");
        String formCode = StringUtil.requireText(request.getFieldFormCode(), "fieldFormCode");
        String formName = StringUtil.requireText(request.getFieldFormName(), "fieldFormName");
        // ① 编码全局查重（分册 §3.3 动作①；uk_form_code 库级兜底）
        assertCodeAvailable(formCode);
        // ② Schema 结构校验（schema 缺省 → 空白草稿初始化；携带 → 归一协议头后校验，失败阻断）
        SysFormSchemaDo schema = null == request.getSchema()
                ? blankSchema(fieldId, formCode, formName)
                : normalizeSchema(request.getSchema(), fieldId, formCode, formName);
        String schemaJson = requireSchemaValid(toStoreJson(schema));

        Date now = new Date();
        String operatorId = UserUtil.getCurrentUserId();
        // ③ 主档行（DRAFT、无当前版本）
        SysForm model = new SysForm();
        model.setFieldId(fieldId);
        model.setFieldFormCode(formCode);
        model.setFieldFormName(formName);
        model.setFieldCurrentVersionId(null);
        model.setFieldStatus(SysFormStatus.DRAFT.name());
        model.setFieldEnable(Boolean.TRUE);
        model.setFieldCreatorId(operatorId);
        model.setFieldCreateTime(now);
        model.setFieldUpdaterId(operatorId);
        model.setFieldUpdateTime(now);
        formDao.insert(model);
        // ③ DRAFT 版本行（field_version NULL，发布时原地冻结回填；field_enable 内建列恒 TRUE 无业务语义）
        SysFormVersion draft = new SysFormVersion();
        draft.initFieldId();
        draft.setFieldFormId(model.getFieldId());
        draft.setFieldVersion(null);
        draft.setFieldSchemaJson(schemaJson);
        draft.setFieldStatus(SysFormVersionStatus.DRAFT.name());
        draft.setFieldPublishedAt(null);
        draft.setFieldCreatorId(operatorId);
        draft.setFieldCreateTime(now);
        draft.setFieldUpdaterId(operatorId);
        draft.setFieldUpdateTime(now);
        draft.setFieldEnable(Boolean.TRUE);
        versionDao.insert(draft);
        log.info("form saved: id={}, code={}, draftVersionId={}", model.getFieldId(), formCode, draft.getFieldId());
        return load(model.getFieldId());
    }

    /**
     * 更新表单——元数据与草稿 Schema（分册 §3.5，事务内）。只写 DRAFT 版本行，已发布
     * （PUBLISHED）/ 已替代（DEPRECATED）版本行内容永不更新（发布快照不可变，ADR-06）；
     * 对已发布版本的「修正」语义 = 再编辑 → 新版本发布。主档 PUBLISHED 且无草稿行时自动
     * 创建 DRAFT 行承载本次提交 Schema（「再编辑」入口）；主档 DRAFT（从未发布）更新唯一
     * DRAFT 行。编码不可变更（本接口不含编码字段）；停用不阻断本接口（分册 §3.5）。
     *
     * @param request 更新体（fieldId 必填；fieldFormName 与 schema 至少携带一项）
     * @return 更新后的详情（元数据 + draftSchema + publishedSchema）
     */
    @Transactional
    public SysFormVo update(SysFormUpdateRequest request) {
        if (null == request) {
            throw new IllegalArgumentException("更新请求不能为空");
        }
        String fieldId = StringUtil.requireText(request.getFieldId(), "fieldId");
        String formName = StringUtil.trimToNull(request.getFieldFormName());
        if (null == formName && null == request.getSchema()) {
            throw new IllegalArgumentException("fieldFormName 与 schema 至少携带一项");
        }
        SysForm exist = requireForm(fieldId);
        Date now = new Date();
        String operatorId = UserUtil.getCurrentUserId();

        if (null != formName) {
            // 名称更新（Mapper update 仅 SET field_form_name + 审计；编码禁改、状态不在此通道）
            SysForm meta = new SysForm();
            meta.setFieldId(exist.getFieldId());
            meta.setFieldFormName(formName);
            meta.setFieldUpdaterId(operatorId);
            meta.setFieldUpdateTime(now);
            int affected = formDao.update(meta);
            if (affected != 1) {
                throw new IllegalStateException("update expected one affected row but got " + affected);
            }
        }
        if (null != request.getSchema()) {
            // 协议头归一以主档权威元数据为准（名称取更新后终值），version 请求携带一律忽略
            String effectiveName = null != formName ? formName : exist.getFieldFormName();
            SysFormSchemaDo schema = normalizeSchema(request.getSchema(),
                    exist.getFieldId(), exist.getFieldFormCode(), effectiveName);
            String schemaJson = requireSchemaValid(toStoreJson(schema));
            SysFormVersion draftRow = selectDraftRow(exist.getFieldId());
            if (null != draftRow) {
                // 原地重写草稿行（Mapper update 仅 SET field_schema_json + 审计，WHERE 守护
                // field_status='DRAFT'——终态行被并发冻结时影响行数 0，防御性拒绝，分册 §3.11 双保险）
                SysFormVersion rewrite = new SysFormVersion();
                rewrite.setFieldId(draftRow.getFieldId());
                rewrite.setFieldSchemaJson(schemaJson);
                rewrite.setFieldUpdaterId(operatorId);
                rewrite.setFieldUpdateTime(now);
                int affected = versionDao.update(rewrite);
                if (affected != 1) {
                    throw formError(CS_FORM_VERSION_STATE_INVALID, "草稿版本行状态已变更（"
                            + draftRow.getFieldStatus() + "），拒绝内容更新: " + draftRow.getFieldId());
                }
            } else {
                // 主档已发布且无草稿行：自动创建 DRAFT 行承载本次提交（再编辑入口，分册 §3.5；
                // 画布已由前端以 publishedSchema 初始化，等价于复制当前发布版本为新草稿后应用变更）
                SysFormVersion draft = new SysFormVersion();
                draft.initFieldId();
                draft.setFieldFormId(exist.getFieldId());
                draft.setFieldVersion(null);
                draft.setFieldSchemaJson(schemaJson);
                draft.setFieldStatus(SysFormVersionStatus.DRAFT.name());
                draft.setFieldPublishedAt(null);
                draft.setFieldCreatorId(operatorId);
                draft.setFieldCreateTime(now);
                draft.setFieldUpdaterId(operatorId);
                draft.setFieldUpdateTime(now);
                draft.setFieldEnable(Boolean.TRUE);
                versionDao.insert(draft);
                log.info("form draft created for re-edit: formId={}, draftVersionId={}",
                        exist.getFieldId(), draft.getFieldId());
            }
        }
        return load(exist.getFieldId());
    }

    /**
     * 启停用（分册 §3.6）：只更新主档自身一行（Mapper updateEnable 专属语句，仅
     * field_enable + 审计两列），无级联对象（自定义表单无子级，先例 org）。停用关闭填写
     * 入口与数据提交（数据面拒绝 CS_FORM_DISABLED），定义面与数据查询保留；停用不改变
     * field_current_version_id 语义（功能设计 §5.4）。
     *
     * @param fieldId     表单 ID
     * @param fieldEnable 必填：true 启用 / false 停用
     */
    @Transactional
    public void updateEnable(String fieldId, Boolean fieldEnable) {
        String id = StringUtil.requireText(fieldId, "fieldId");
        if (null == fieldEnable) {
            throw new IllegalArgumentException("fieldEnable 必填，不能为空");
        }
        SysForm exist = requireForm(id);
        int affected = formDao.updateEnable(exist.getFieldId(), fieldEnable,
                UserUtil.getCurrentUserId(), new Date());
        if (affected != 1) {
            throw new IllegalStateException("enable expected one affected row but got " + affected);
        }
        log.info("form enable switched: id={}, enable={}, status={}",
                exist.getFieldId(), fieldEnable, exist.getFieldStatus());
    }

    /**
     * 删除表单（分册 §3.7，事务内）。删除前置校验（分册 §7.4 定稿②）：sys_form_data 中该
     * formId 存在任意实例数据（不分版本、不分启用状态）→ 拒绝删除（CS_FORM_HAS_DATA），
     * 不提供级联删除——需保留数据时用「停用」达成「不再使用」的管理目的。通过校验后先删
     * 该表单全部版本行（含 DRAFT / PUBLISHED / DEPRECATED——无实例数据时历史版本无保留
     * 价值）再删主档行。
     *
     * @param fieldId 表单主档 ID
     */
    @Transactional
    public void delete(String fieldId) {
        SysForm exist = requireForm(StringUtil.requireText(fieldId, "fieldId"));
        if (dataDao.existsByFormId(exist.getFieldId())) {
            throw formError(CS_FORM_HAS_DATA, "表单存在实例数据，禁止删除（需保留数据请使用停用）: "
                    + exist.getFieldFormCode());
        }
        versionDao.deleteByFormId(exist.getFieldId());
        formDao.deleteById(exist.getFieldId());
        log.info("form deleted: id={}, code={}", exist.getFieldId(), exist.getFieldFormCode());
    }

    /* ==================== 版本管理 ==================== */

    /**
     * 发布表单（分册 §3.8，事务内）。发布校验链按序全部通过方可发布，任一失败确定性拒绝：
     * P1 草稿行存在（无草稿行直接发布为非法流转，CS_FORM_NO_DRAFT）→ P2/P3 Schema 结构
     * 与资源上限校验（按存储原文 String 形态，含体积上限；CS_FORM_SCHEMA_INVALID /
     * CS_FORM_SCHEMA_LIMIT_EXCEEDED）→ P4 公式静态校验（{@link FormFormulaEvaluator
     * #assertPublishable}：表达式合法 / 期望返回类型与引用字段存在 / 依赖图无环，错误码
     * 复用 CS_FORMULA_* 族——分册 §7.2 复用映射）。发布成功动作：
     * 草稿行原地冻结（版本号回填 = 现存最大 + 1、状态 DRAFT → PUBLISHED、published_at
     * 回填）→ 原当前发布版本（若有）PUBLISHED → DEPRECATED → 主档 field_current_version_id
     * 切换至新版本行、主档状态 DRAFT → PUBLISHED（首次发布）。表单停用不阻断发布
     * （定义面动作；发布后填写入口是否开启仍由 field_enable 决定）。
     *
     * @param fieldId 表单主档 ID
     * @return 新发布版本行 ID 与整数版本号
     */
    @Transactional
    public SysFormVersionCreatedVo publish(String fieldId) {
        SysForm exist = requireForm(StringUtil.requireText(fieldId, "fieldId"));
        // P1 草稿行存在（「无草稿行时直接发布」为非法流转，分册 §3.11）
        SysFormVersion draftRow = selectDraftRow(exist.getFieldId());
        if (null == draftRow) {
            throw formError(CS_FORM_NO_DRAFT, "表单无草稿版本行，无法发布（再编辑后先保存草稿）: "
                    + exist.getFieldFormCode());
        }
        // P2/P3 Schema 结构与资源上限校验（按存储原文校验——体积上限仅 String 入口检查）
        requireSchemaValid(toStoreJson(draftRow.getFieldSchemaJson()));
        // P4 公式静态校验（F15-BE-005，分册 §7.2 复用映射）：表达式合法（引擎 validate
        //   宽松模式，CS_FORMULA_EXPRESSION_TOO_LONG / SYNTAX_INVALID / FUNCTION_NOT_FOUND
        //   原样透出）/ 期望返回类型合法（CS_FORMULA_TYPE_MISMATCH）/ 引用字段存在（宿主
        //   数据层级内已配置字段 + ${list.field} 投影根码与行内字段存在，CS_FORMULA_VARIABLE_
        //   INVALID）/ 依赖图无环（CS_FORMULA_EXECUTE_FAILED，运行期拓扑熔断同码兜底）——
        //   引擎首错即抛，失败阻断发布（功能设计 §5.2 发布校验链）
        formulaEvaluator.assertPublishable(parseSchema(draftRow.getFieldSchemaJson()));

        Integer maxVersion = versionDao.selectMaxVersion(exist.getFieldId());
        int nextVersion = (null == maxVersion ? 0 : maxVersion) + 1;
        Date now = new Date();
        String operatorId = UserUtil.getCurrentUserId();
        // 草稿行原地冻结（版本号 / PUBLISHED / published_at 回填，WHERE 守护 DRAFT；快照内容不改写）
        int frozen = versionDao.freezeDraft(draftRow.getFieldId(), nextVersion, now, operatorId, now);
        if (frozen != 1) {
            // WHERE 守护拦截（并发发布或状态漂移）：防御性拒绝（分册 §3.11 双保险）
            throw formError(CS_FORM_VERSION_STATE_INVALID, "草稿版本行状态已变更，发布失败（可能并发发布）: "
                    + draftRow.getFieldId());
        }
        // 原当前发布版本退役（PUBLISHED → DEPRECATED；首次发布无当前版本）
        deprecateCurrentVersion(exist, operatorId, now);
        // 主档指针切换 + 状态同步（首次发布 DRAFT → PUBLISHED；指针与状态成对切换，分册 §5.1.6⑦）
        int switched = formDao.updateCurrentVersionId(exist.getFieldId(), SysFormStatus.PUBLISHED.name(),
                draftRow.getFieldId(), operatorId, now);
        if (switched != 1) {
            throw new IllegalStateException("publish expected one affected row but got " + switched);
        }
        log.info("form published: formId={}, versionId={}, version={}",
                exist.getFieldId(), draftRow.getFieldId(), nextVersion);
        SysFormVersionCreatedVo vo = new SysFormVersionCreatedVo();
        vo.setFieldVersionId(draftRow.getFieldId());
        vo.setFieldVersion(nextVersion);
        return vo;
    }

    /**
     * 版本列表（分册 §3.9）：版本行不返回 Schema 内容；排序定稿（F15-BE-002，回写分册
     * §3.9 / 数据库分册 §9.3 条目 1）——草稿行恒排最前（每表单至多一行），其余按
     * fieldVersion 降序（最新在前），由 Mapper selectByFormId 保证。
     *
     * @param fieldId 表单主档 ID
     * @return 版本行清单（含草稿行与全部历史发布行）
     */
    public List<SysFormVersionVo> versions(String fieldId) {
        SysForm exist = requireForm(StringUtil.requireText(fieldId, "fieldId"));
        List<SysFormVersion> rows = versionDao.selectByFormId(exist.getFieldId());
        List<SysFormVersionVo> result = new ArrayList<>(rows.size());
        for (SysFormVersion row : rows) {
            SysFormVersionVo vo = new SysFormVersionVo();
            vo.setFieldId(row.getFieldId());
            vo.setFieldVersion(row.getFieldVersion());
            vo.setFieldStatus(row.getFieldStatus());
            vo.setFieldPublishedAt(row.getFieldPublishedAt());
            vo.setFieldCreatorId(row.getFieldCreatorId());
            vo.setFieldCreateTime(row.getFieldCreateTime());
            result.add(vo);
        }
        return result;
    }

    /**
     * 回滚版本（分册 §3.10，事务内）。目标版本行必须存在（含归属校验——他表版本行按
     * CS_FORM_VERSION_NOT_FOUND 处理）且为 PUBLISHED / DEPRECATED（DRAFT 行不是「历史
     * 发布版本」，以草稿为目标等价于「发布草稿」，语义混用拒绝）。回滚 = 复制目标版本内容
     * 产生新发布版本行（版本号 = 现存最大 + 1、状态 PUBLISHED、published_at = 回滚时刻，
     * 不经草稿态一步发布）→ 原当前发布版本转 DEPRECATED → 主档 field_current_version_id
     * 指向新行——不产生指针回拨（DEPRECATED 不可逆的恢复路径即本接口）、不影响既有草稿行；
     * 表单停用不阻断回滚。
     *
     * @param request 回滚体（fieldId / fieldVersionId 必填）
     * @return 回滚产生的新版本行 ID 与版本号
     */
    @Transactional
    public SysFormVersionCreatedVo rollback(SysFormRollbackRequest request) {
        if (null == request) {
            throw new IllegalArgumentException("回滚请求不能为空");
        }
        String fieldId = StringUtil.requireText(request.getFieldId(), "fieldId");
        String versionId = StringUtil.requireText(request.getFieldVersionId(), "fieldVersionId");
        SysForm exist = requireForm(fieldId);
        SysFormVersion target = versionDao.selectById(versionId);
        if (null == target || !exist.getFieldId().equals(target.getFieldFormId())) {
            // 不存在或归属不符（他表版本行）均按目标不存在处理，避免跨表单探测
            throw formError(CS_FORM_VERSION_NOT_FOUND, "回滚目标版本行不存在: " + versionId);
        }
        SysFormVersionStatus targetStatus = SysFormVersionStatus.of(target.getFieldStatus());
        if (null == targetStatus) {
            throw new IllegalStateException("版本行状态异常: " + target.getFieldId());
        }
        if (SysFormVersionStatus.DRAFT == targetStatus) {
            throw formError(CS_FORM_VERSION_STATE_INVALID, "回滚目标须为已发布版本（PUBLISHED/DEPRECATED），"
                    + "目标行为草稿: " + versionId);
        }

        Integer maxVersion = versionDao.selectMaxVersion(exist.getFieldId());
        int nextVersion = (null == maxVersion ? 0 : maxVersion) + 1;
        Date now = new Date();
        String operatorId = UserUtil.getCurrentUserId();
        // 复制目标版本内容产生新发布行（快照原样复制——版本行内容不可变原则的复制面）
        SysFormVersion copy = new SysFormVersion();
        copy.initFieldId();
        copy.setFieldFormId(exist.getFieldId());
        copy.setFieldVersion(nextVersion);
        copy.setFieldSchemaJson(target.getFieldSchemaJson());
        copy.setFieldStatus(SysFormVersionStatus.PUBLISHED.name());
        copy.setFieldPublishedAt(now);
        copy.setFieldCreatorId(operatorId);
        copy.setFieldCreateTime(now);
        copy.setFieldUpdaterId(operatorId);
        copy.setFieldUpdateTime(now);
        copy.setFieldEnable(Boolean.TRUE);
        versionDao.insert(copy);
        // 原当前发布版本退役 + 主档指针切换（回滚后状态保持 PUBLISHED 并指向新版本行）
        deprecateCurrentVersion(exist, operatorId, now);
        int switched = formDao.updateCurrentVersionId(exist.getFieldId(), SysFormStatus.PUBLISHED.name(),
                copy.getFieldId(), operatorId, now);
        if (switched != 1) {
            throw new IllegalStateException("rollback expected one affected row but got " + switched);
        }
        log.info("form rolled back: formId={}, targetVersionId={}, newVersionId={}, version={}",
                exist.getFieldId(), versionId, copy.getFieldId(), nextVersion);
        SysFormVersionCreatedVo vo = new SysFormVersionCreatedVo();
        vo.setFieldVersionId(copy.getFieldId());
        vo.setFieldVersion(nextVersion);
        return vo;
    }

    /* ==================== 内部：校验与查重 ==================== */

    /**
     * 表单主档加载，不存在抛 {@code CS_FORM_NOT_FOUND}（分册 §7.1 第 2 项：定义面
     * load / update / enable / delete / publish / versions / rollback 全部承载）
     */
    private SysForm requireForm(String fieldId) {
        SysForm exist = formDao.selectById(fieldId);
        if (null == exist) {
            throw formError(CS_FORM_NOT_FOUND, "表单不存在: " + fieldId);
        }
        return exist;
    }

    /**
     * 编码全局查重（uk_form_code，Service 层查重先例 formula）：DB 同编码行即抛
     * {@code CS_FORM_CODE_DUPLICATED}（编码保存后不可变更，查重仅 save 通道）
     */
    private void assertCodeAvailable(String formCode) {
        SysForm exist = formDao.selectByCode(formCode);
        if (null != exist) {
            throw formError(CS_FORM_CODE_DUPLICATED, "表单编码已存在: " + formCode);
        }
    }

    /**
     * Schema 结构校验（分册 §3.5 / §3.8 发布校验链 P2/P3）：原文 JSON 经
     * {@link FormSchemaValidator#validate(String)}（含体积上限 MAX_SCHEMA_JSON_LENGTH），
     * errors 非空即拒绝——首错错误码为 message 前缀、逐条中文原因随 message 附带；
     * 校验通过返回原文（save / update 落库复用同一序列化产物）
     */
    private String requireSchemaValid(String schemaJson) {
        List<SysFormSchemaErrorDo> errors = schemaValidator.validate(schemaJson);
        if (null == errors || errors.isEmpty()) {
            return schemaJson;
        }
        StringBuilder message = new StringBuilder();
        for (SysFormSchemaErrorDo error : errors) {
            if (!message.isEmpty()) {
                message.append("；");
            }
            message.append(error.getMessage());
        }
        throw formError(errors.get(0).getCode(), message.toString());
    }

    /** 主档状态筛选值合法性（分册 §3.2 fieldStatus ∈ DRAFT / PUBLISHED，非法值确定性拒绝） */
    private static void requireStatusValue(String fieldStatus) {
        if (!StringUtil.isEmpty(fieldStatus) && null == SysFormStatus.of(fieldStatus)) {
            throw new IllegalArgumentException("fieldStatus 非法: " + fieldStatus
                    + "（取值域 DRAFT/PUBLISHED）");
        }
    }

    /* ==================== 内部：行装配与 JSON 承载 ==================== */

    /**
     * 草稿版本行加载（任意时刻每表单至多一行）：按 fieldFormId + fieldStatus=DRAFT 精确查询，
     * 返回含 field_schema_json 全列行（更新重写与发布冻结需快照内容）；不存在返回 null
     */
    private SysFormVersion selectDraftRow(String formId) {
        SysFormVersion criteria = new SysFormVersion();
        criteria.setFieldFormId(formId);
        criteria.setFieldStatus(SysFormVersionStatus.DRAFT.name());
        List<SysFormVersion> rows = versionDao.selectList(criteria, null);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 全表草稿行索引（列表 hasDraft 预判专用）：每表单至多一行 DRAFT，结果量级 = 表单数 */
    private Set<String> loadDraftFormIds() {
        SysFormVersion criteria = new SysFormVersion();
        criteria.setFieldStatus(SysFormVersionStatus.DRAFT.name());
        Set<String> formIds = new HashSet<>();
        for (SysFormVersion row : versionDao.selectList(criteria, null)) {
            formIds.add(row.getFieldFormId());
        }
        return formIds;
    }

    /**
     * 列表行 Vo 装配（分册 §3.2）：行字段 + hasDraft 预判 + fieldCurrentVersion（按行主键
     * 点查版本号，一页行数次主键查询量级可控）；不携带 Schema 内容
     */
    private SysFormVo toRowVo(SysForm row, boolean hasDraft) {
        Integer currentVersion = StringUtil.isEmpty(row.getFieldCurrentVersionId())
                ? null : versionDao.selectVersionNumberById(row.getFieldCurrentVersionId());
        return toVo(row, hasDraft, currentVersion, null, null);
    }

    /** 行字段 + 可空载荷 Vo 装配（分册 §3.2 / §3.4 同构） */
    private SysFormVo toVo(SysForm row, boolean hasDraft, Integer currentVersion,
                           SysFormSchemaDo draftSchema, SysFormSchemaDo publishedSchema) {
        SysFormVo vo = new SysFormVo();
        vo.setFieldId(row.getFieldId());
        vo.setFieldFormCode(row.getFieldFormCode());
        vo.setFieldFormName(row.getFieldFormName());
        vo.setFieldStatus(row.getFieldStatus());
        vo.setFieldEnable(row.getFieldEnable());
        vo.setFieldCurrentVersionId(row.getFieldCurrentVersionId());
        vo.setFieldCurrentVersion(currentVersion);
        vo.setHasDraft(hasDraft);
        vo.setDraftSchema(draftSchema);
        vo.setPublishedSchema(publishedSchema);
        vo.setFieldCreatorId(row.getFieldCreatorId());
        vo.setFieldCreateTime(row.getFieldCreateTime());
        vo.setFieldUpdaterId(row.getFieldUpdaterId());
        vo.setFieldUpdateTime(row.getFieldUpdateTime());
        return vo;
    }

    /**
     * Schema 协议头归一（分册 §2.4）：请求体顶层元数据（fieldId / fieldFormCode /
     * fieldFormName）为权威，服务端据此归一载荷协议头 formId / formCode / formName；
     * version 由服务端管理（发布 / 回滚动作回填），请求携带一律忽略（草稿恒 null）。
     * 归一产物为新对象，不改写调用方持有的请求载荷
     */
    private static SysFormSchemaDo normalizeSchema(SysFormSchemaDo schema, String formId,
                                                   String formCode, String formName) {
        SysFormSchemaDo normalized = new SysFormSchemaDo();
        normalized.setFormId(formId);
        normalized.setFormCode(formCode);
        normalized.setFormName(formName);
        normalized.setVersion(null);
        normalized.setFields(schema.getFields());
        return normalized;
    }

    /** 空白草稿初始化（分册 §3.3：schema 缺省时以 {@code { formId, formCode, formName, fields:[] }} 初始化） */
    private static SysFormSchemaDo blankSchema(String formId, String formCode, String formName) {
        SysFormSchemaDo schema = new SysFormSchemaDo();
        schema.setFormId(formId);
        schema.setFormCode(formCode);
        schema.setFormName(formName);
        schema.setFields(new ArrayList<>());
        return schema;
    }

    /**
     * 存储态 JSON 归一（Object → 原文字符串）：String 原样返回（JSON 列存储态为原文），
     * 其他对象经 Jackson 序列化（双形态承载先例 SysFormulaService#parseDependencies 反向）；
     * 序列化失败属程序异常，抛 IllegalStateException 令事务回滚
     */
    private static String toStoreJson(Object raw) {
        if (raw instanceof String json) {
            return json;
        }
        try {
            return JSON_MAPPER.writeValueAsString(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("表单 Schema JSON 序列化失败", e);
        }
    }

    /**
     * 存储态 Schema 解析（String 原文 → 协议对象）：损坏 JSON 属存储态程序异常（内容仅由
     * 保存期校验链落库，非用户直填），抛 IllegalStateException 令事务回滚；协议头归一与
     * version 回填由调用方（{@link #load}）按主档元数据与行级 field_version 完成——存储态
     * 快照保持发布时原文（不可变原则），响应层动态归一不触碰不可变内容
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
     * load 响应协议头归一（分册 §2.4）：协议头 formId / formCode / formName 以主档权威
     * 元数据为准（请求载荷不信任、存储态历史载荷不回写——名称可能经 /update 变更），
     * 仅改写响应对象，不动存储态内容
     */
    private static void normalizeHeader(SysFormSchemaDo schema, SysForm main) {
        if (null == schema) {
            return;
        }
        schema.setFormId(main.getFieldId());
        schema.setFormCode(main.getFieldFormCode());
        schema.setFormName(main.getFieldFormName());
    }

    /**
     * 查询条件归一：空白转 null（trim），空对象兜底（对齐 SysFormulaService#normalizeCriteria）
     */
    private static SysForm normalizeCriteria(SysForm body) {
        SysForm criteria = null == body ? new SysForm() : body;
        criteria.setFieldFormCode(StringUtil.trimToNull(criteria.getFieldFormCode()));
        criteria.setFieldFormName(StringUtil.trimToNull(criteria.getFieldFormName()));
        criteria.setFieldStatus(StringUtil.trimToNull(criteria.getFieldStatus()));
        return criteria;
    }

    /**
     * 原当前发布版本退役（发布 / 回滚事务内）：主档 field_current_version_id 为空（首次
     * 发布）跳过；否则按 PUBLISHED → DEPRECATED 流转（WHERE 守护 PUBLISHED，影响行数
     * 不符属指针数据异常，抛 IllegalStateException 令事务回滚）
     */
    private void deprecateCurrentVersion(SysForm form, String operatorId, Date now) {
        if (StringUtil.isEmpty(form.getFieldCurrentVersionId())) {
            return;
        }
        int affected = versionDao.deprecate(form.getFieldCurrentVersionId(), operatorId, now);
        if (affected != 1) {
            throw new IllegalStateException("deprecate expected one affected row but got " + affected
                    + ": " + form.getFieldCurrentVersionId());
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
