package com.eric.base.sys.number.service;

import com.eric.base.common.base.model.Pages;
import com.eric.base.common.utils.IDUtil;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.number.dao.SysNumberRecordDetailDao;
import com.eric.base.sys.number.dao.SysNumberRuleDao;
import com.eric.base.sys.number.model.SysNumberModelDo;
import com.eric.base.sys.number.model.SysNumberRule;
import com.eric.base.sys.number.model.SysNumberRuleConfigDo;
import com.eric.base.sys.number.model.SysNumberRuleErrorDo;
import com.eric.base.sys.number.request.SysNumberRuleQuery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 编号规则维护服务
 *
 * @author Eric
 * @date 2026/11/13
 * @description 承载编号规则的业务维护（任务 NB3-BE-002，方案 §1.2 / §3.2 rule 组 / §5.5）：
 *              <ul>
 *              <li>list / count：规则列表与总数（总数供响应 metadata.total），条件
 *                  fieldName / fieldCode 模糊、fieldModelId / fieldModelField / fieldEnable 精确，
 *                  field_rule 原文返回不解析（A4），排序固定创建时间降序；</li>
 *              <li>load：按 ID 读取并将 field_rule 解析为 {@link SysNumberRuleConfigDo} 回填
 *                  （JSON 损坏按规则非法处理）；</li>
 *              <li>save：C1~C5 全量校验（委托 {@link SysNumberRuleValidator}）→ 编码查重（D4）→
 *                  接入项存在性（R6）→ 新建默认启用并同事务停用同接入项原已启用的其他规则
 *                  （D1 切换式启用）→ 收尾自检同接入项启用计数恰为 1（R2）；</li>
 *              <li>update：另校验 C7（seq 项 id 锁定库中旧值，D5）；编码查重排除自身；
 *                  field_model_id / field_model_name / field_model_field 不受理变更
 *                  （一律以库中旧值为准，D3）；启用态保持不变（表单无该字段，忽略入参）；</li>
 *              <li>enable：启用时 C1~C5 校验 + 接入项存在性 + 切换式启用 + 收尾自检；
 *                  停用仅更新自身；</li>
 *              <li>delete：删除保护（D9）——已产生取号明细（作用域 = 规则 field_id）的规则
 *                  拒绝删除（{@link NumberRuleInUseException}），仅可停用；</li>
 *              <li>validateConfig：规则配置公共校验入口（方案 §1.3），委托校验组件执行
 *                  C1~C5，供宿主模块（如分类保存 field_code_rule）复用；</li>
 *              <li>modelOptions：接入项下拉数据源（委托 {@link SysNumberModelCacheService}，
 *                  NB3-BE-003 modelOptions 接口数据源）。</li>
 *              </ul>
 *              切换启用（disableOtherEnabledByModel + 自身置启用 + 收尾自检）在同一事务内完成，
 *              自检不符抛 IllegalStateException 令事务回滚，避免双启用 / 双停用（R2）。
 */
@Service
public class SysNumberRuleService {

    private static final Logger log = LoggerFactory.getLogger(SysNumberRuleService.class);

    /** 规则 JSON 序列化 / 解析（固定结构 POJO，禁止手工拼串，先例 SysNumberGenerateService） */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final SysNumberRuleDao ruleDao;
    private final SysNumberRecordDetailDao detailDao;
    private final SysNumberModelCacheService modelCacheService;
    private final SysNumberRuleValidator validator;

    public SysNumberRuleService(SysNumberRuleDao ruleDao,
                                SysNumberRecordDetailDao detailDao,
                                SysNumberModelCacheService modelCacheService,
                                SysNumberRuleValidator validator) {
        this.ruleDao = ruleDao;
        this.detailDao = detailDao;
        this.modelCacheService = modelCacheService;
        this.validator = validator;
    }

    /**
     * 规则列表：field_rule 原文返回不解析（A4，列表页不解析 JSON），
     * 条件 fieldName / fieldCode 模糊、fieldModelId / fieldModelField / fieldEnable 精确，
     * 排序固定创建时间降序。
     *
     * @param query 查询条件（可空，空字段忽略）
     * @param pages 分页参数（可空，needPage=false 返回全部）
     * @return 规则列表（field_rule 为原文字符串）
     */
    public List<SysNumberRule> list(SysNumberRuleQuery query, Pages pages) {
        return ruleDao.selectQueryList(normalizeQuery(query), normalizePages(pages));
    }

    /**
     * 规则列表总数：查询条件与 {@link #list} 完全一致，供响应 metadata.total 使用。
     *
     * @param query 查询条件（可空，空字段忽略）
     * @return 匹配总数
     */
    public long count(SysNumberRuleQuery query) {
        return ruleDao.countQueryList(normalizeQuery(query));
    }

    /**
     * 规则详情：将 field_rule（存储态 JSON 原文）解析为 {@link SysNumberRuleConfigDo}
     * 回填到实体 fieldRule 字段返回（前端 NumberRuleComposer 直接消费，方案 §3.2 rule/load）。
     * JSON 损坏按规则非法处理，抛 {@link NumberRuleInvalidException}。
     *
     * @param fieldId 规则 ID
     * @return 规则实体（fieldRule 已是解析后的配置对象）
     */
    public SysNumberRule load(String fieldId) {
        String id = StringUtil.requireText(fieldId, "fieldId");
        SysNumberRule rule = ruleDao.selectById(id);
        if (null == rule) {
            throw new NumberRuleNotFoundException("编号规则不存在: " + id);
        }
        rule.setFieldRule(parseStoredConfig(rule.getFieldRule(), rule.getFieldId()));
        return rule;
    }

    /**
     * 新建保存（默认启用，D1 切换式启用）。执行顺序：
     * fieldRule 归一为 {@link SysNumberRuleConfigDo} → C1~C5 全量校验 → 编码查重（D4）→
     * 接入项存在性（R6）→ 落库（fieldEnable 置 true，同事务停用同接入项原已启用的其他规则）→
     * 收尾自检同接入项启用计数恰为 1（R2）。
     * <p>fieldId 沿用调用方传入值（前端经 getInitID 预取），为空 / 非 32 位时由
     * BaseModel.setFieldId 兜底 IDUtil.initID()（先例 SysI18nLanguageService#save）；
     * field_model_name / field_model_field 以接入项缓存带出（D3「由下拉带出」），
     * 保证与取号定位 (modelName, modelField) 一致，不采信请求体。</p>
     *
     * @param body 规则实体（fieldCode / fieldName / fieldModelId / fieldRule 必填；
     *             fieldEnable 不受理，新建一律默认启用）
     * @return 保存后的规则（field_rule 已解析为配置对象）
     */
    @Transactional
    public SysNumberRule save(SysNumberRule body) {
        requireRuleBody(body);
        String modelId = StringUtil.requireText(body.getFieldModelId(), "fieldModelId");
        SysNumberRuleConfigDo config = normalizeConfig(body.getFieldRule());
        // C1~C5 全量校验（C7 仅 update 场景）
        validator.assertValid(config);
        // 编码查重（D4，先例 i18n 语言码 Service 层查重）
        String code = body.getFieldCode().trim();
        assertCodeAvailable(code, null);
        // 接入项存在性（R6：拦截引用失效接入项的规则新增）
        assertModelAvailable(modelId);

        SysNumberRule model = new SysNumberRule();
        model.setFieldId(body.getFieldId());
        model.setFieldCode(code);
        model.setFieldName(body.getFieldName().trim());
        model.setFieldModelId(modelId);
        SysNumberModelDo modelOption = modelCacheService.getModel(modelId);
        if (null != modelOption) {
            model.setFieldModelName(modelOption.getModelName());
            model.setFieldModelField(modelOption.getModelField());
        }
        model.setFieldRule(toStoreJson(config));
        // 新建默认启用（表单无启用字段，忽略入参 fieldEnable）
        model.setFieldEnable(Boolean.TRUE);
        String updaterId = UserUtil.getCurrentUserId();
        Date now = new Date();
        model.setFieldCreatorId(updaterId);
        model.setFieldCreateTime(now);
        model.setFieldUpdaterId(updaterId);
        model.setFieldUpdateTime(now);
        ruleDao.insert(model);
        // D1 切换式启用：同事务停用同接入项原已启用的其他规则（排除自身）
        ruleDao.disableOtherEnabledByModel(modelId, model.getFieldId(), updaterId, now);
        // R2 收尾自检：不符即抛 IllegalStateException 令事务回滚，避免双启用
        assertSingleEnabled(modelId);
        log.info("number rule saved: id={}, code={}, model={}", model.getFieldId(), code, modelId);
        return load(model.getFieldId());
    }

    /**
     * 编辑保存：C1~C5 + C7（seq 项 id 锁定库中旧值，D5）→ 编码查重（排除自身，D4）→
     * 接入项存在性（R6）→ 更新 field_code / field_name / field_rule + 审计字段。
     * <p><b>D3 不受理变更</b>：field_model_id / field_model_name / field_model_field
     * 一律以库中旧值为准，忽略入参（改动会使既有计数器作用域悬空）；
     * <b>启用态保持不变</b>：表单无该字段，忽略入参 fieldEnable（启停走 enable 接口）。
     * 更新影响行数 ≠ 1 抛 IllegalStateException（对齐 updateEnable 先例）。</p>
     *
     * @param body 规则实体（fieldId / fieldCode / fieldName / fieldRule 必填；
     *             模型三字段与 fieldEnable 不受理）
     * @return 更新后的规则（field_rule 已解析为配置对象）
     */
    @Transactional
    public SysNumberRule update(SysNumberRule body) {
        requireRuleBody(body);
        String fieldId = StringUtil.requireText(body.getFieldId(), "fieldId");
        SysNumberRule exist = ruleDao.selectById(fieldId);
        if (null == exist) {
            throw new NumberRuleNotFoundException("编号规则不存在: " + fieldId);
        }
        SysNumberRuleConfigDo oldConfig = parseStoredConfig(exist.getFieldRule(), exist.getFieldId());
        SysNumberRuleConfigDo config = normalizeConfig(body.getFieldRule());
        // C1~C5 + C7：新旧规则 seq 项 id 比对，锁定计数器锚点（D5）
        validator.assertValid(config, oldConfig);
        // 编码查重（排除自身）
        String code = body.getFieldCode().trim();
        assertCodeAvailable(code, exist.getFieldId());
        // 接入项存在性（R6：field_model_id 锁定不可改，yml 移除后编辑一并拦截）
        assertModelAvailable(exist.getFieldModelId());

        SysNumberRule model = new SysNumberRule();
        model.setFieldId(exist.getFieldId());
        model.setFieldCode(code);
        model.setFieldName(body.getFieldName().trim());
        model.setFieldRule(toStoreJson(config));
        model.setFieldUpdaterId(UserUtil.getCurrentUserId());
        model.setFieldUpdateTime(new Date());
        // 仅更新编码 / 名称 / 规则 / 审计字段：模型三字段与启用态不进入更新模型（D3）
        int affected = ruleDao.update(model);
        if (affected != 1) {
            throw new IllegalStateException("update expected one affected row but got " + affected);
        }
        log.info("number rule updated: id={}, code={}", exist.getFieldId(), code);
        return load(exist.getFieldId());
    }

    /**
     * 启停用（D1 切换式启用）。
     * <p>启用（true）：C1~C5 校验（库中 field_rule 解析）→ 接入项存在性（R6）→
     * 同事务停用同接入项其他已启用规则（排除自身）→ 自身置启用 →
     * 收尾自检同接入项启用计数恰为 1（R2）。
     * 停用（false）：仅更新自身 field_enable，不校验规则、不联动其他规则。</p>
     *
     * @param fieldId     规则 ID
     * @param fieldEnable 必填：true 启用（切换式）、false 停用
     */
    @Transactional
    public void enable(String fieldId, Boolean fieldEnable) {
        String id = StringUtil.requireText(fieldId, "fieldId");
        if (null == fieldEnable) {
            throw new IllegalArgumentException("fieldEnable 必填，不能为空");
        }
        SysNumberRule exist = ruleDao.selectById(id);
        if (null == exist) {
            throw new NumberRuleNotFoundException("编号规则不存在: " + id);
        }
        String updaterId = UserUtil.getCurrentUserId();
        Date now = new Date();
        if (Boolean.TRUE.equals(fieldEnable)) {
            SysNumberRuleConfigDo config = parseStoredConfig(exist.getFieldRule(), exist.getFieldId());
            // 启用前强制校验（C1~C5）
            validator.assertValid(config);
            assertModelAvailable(exist.getFieldModelId());
            // D1 切换式启用：同事务先停用其他已启用规则，再置自身启用
            ruleDao.disableOtherEnabledByModel(exist.getFieldModelId(), exist.getFieldId(), updaterId, now);
            updateSelfEnable(exist.getFieldId(), Boolean.TRUE, updaterId, now);
            // R2 收尾自检
            assertSingleEnabled(exist.getFieldModelId());
        } else {
            // 停用仅更新自身启用状态
            updateSelfEnable(exist.getFieldId(), Boolean.FALSE, updaterId, now);
        }
        log.info("number rule enable switched: id={}, enable={}", exist.getFieldId(), fieldEnable);
    }

    /**
     * 删除规则（D9 删除保护）：已产生取号明细（sys_number_record_detail 按
     * 作用域键 = 规则 field_id 计数，明细表无 field_rule_id 列）的规则拒绝删除
     * （{@link NumberRuleInUseException}），仅可停用；无明细时物理删除。
     *
     * @param fieldId 规则 ID
     */
    @Transactional
    public void delete(String fieldId) {
        String id = StringUtil.requireText(fieldId, "fieldId");
        SysNumberRule exist = ruleDao.selectById(id);
        if (null == exist) {
            throw new NumberRuleNotFoundException("编号规则不存在: " + id);
        }
        long detailCount = detailDao.countByScopeKey(exist.getFieldId());
        if (detailCount > 0) {
            throw new NumberRuleInUseException("编号规则已产生 " + detailCount
                    + " 条取号明细，拒绝删除（仅可停用）: " + exist.getFieldCode());
        }
        ruleDao.deleteById(exist.getFieldId());
        log.info("number rule deleted: id={}, code={}", exist.getFieldId(), exist.getFieldCode());
    }

    /**
     * 规则配置公共校验入口（方案 §1.3）：委托 {@link SysNumberRuleValidator#assertValid}
     * 执行 C1~C5 全量校验，校验语义与编号规则 save / enable 完全一致，
     * 供宿主模块（如分类编辑页保存 field_code_rule 时）复用。
     * C7 比对仅本服务 update 场景内部使用，不在本入口（无库中旧值可比对）。
     *
     * @param config 规则配置
     */
    public void validateConfig(SysNumberRuleConfigDo config) {
        validator.assertValid(config);
    }

    /**
     * 接入项下拉数据源：委托 {@link SysNumberModelCacheService#getModelOptions()}
     * 返回 numberRule.yml 全量接入项（NB3-BE-003 modelOptions 接口数据源，
     * 卡片归属 NB3-BE-003，数据源已在 NB2-BE-001 就绪，本任务先落）。
     *
     * @return 接入项列表（key / name / modelName / modelField）
     */
    public List<SysNumberModelDo> modelOptions() {
        return modelCacheService.getModelOptions();
    }

    /* ==================== 内部 ==================== */

    /**
     * 基础校验：实体非空、编码与名称必填（库列 NOT NULL）
     */
    private static void requireRuleBody(SysNumberRule body) {
        if (null == body) {
            throw new IllegalArgumentException("编号规则实体不能为空");
        }
        StringUtil.requireText(body.getFieldCode(), "fieldCode");
        StringUtil.requireText(body.getFieldName(), "fieldName");
    }

    /**
     * 编码查重（D4）：同编码仅允许一条；update 场景排除自身（excludeFieldId）。
     */
    private void assertCodeAvailable(String code, String excludeFieldId) {
        SysNumberRule exist = ruleDao.selectByCode(code);
        if (null == exist || exist.getFieldId().equals(excludeFieldId)) {
            return;
        }
        throw new NumberRuleDuplicatedException("规则编码已存在: " + code + "（规则 " + exist.getFieldId() + "）");
    }

    /**
     * 接入项存在性（R6）：field_model_id 必须存在于 numberRule.yml 接入项缓存
     */
    private void assertModelAvailable(String fieldModelId) {
        if (!modelCacheService.containsModel(fieldModelId)) {
            throw new NumberModelNotFoundException("接入项不存在或已失效: " + fieldModelId
                    + "（不在 numberRule.yml 接入项缓存中）");
        }
    }

    /**
     * 切换启用收尾自检（R2）：同接入项启用规则数应恰为 1（D1 不变式），
     * 不符抛 IllegalStateException 令事务回滚
     */
    private void assertSingleEnabled(String fieldModelId) {
        long enabledCount = ruleDao.countEnabledByModel(fieldModelId);
        if (enabledCount != 1) {
            throw new IllegalStateException("切换启用收尾自检失败：接入项 " + fieldModelId
                    + " 启用规则数为 " + enabledCount + "，应恰为 1");
        }
    }

    /**
     * 自身启停更新：仅置 field_enable 与审计字段，影响行数 ≠ 1 抛 IllegalStateException
     * （对齐 SysOrgElementService#updateEnable 先例）
     */
    private void updateSelfEnable(String fieldId, Boolean fieldEnable, String updaterId, Date now) {
        SysNumberRule model = new SysNumberRule();
        model.setFieldId(fieldId);
        model.setFieldEnable(fieldEnable);
        model.setFieldUpdaterId(updaterId);
        model.setFieldUpdateTime(now);
        int affected = ruleDao.update(model);
        if (affected != 1) {
            throw new IllegalStateException("enable expected one affected row but got " + affected);
        }
    }

    /**
     * save / update 入参规则归一：{@link SysNumberRule#getFieldRule()} 声明为 Object，
     * 请求体经 Jackson 反序列化通常为 LinkedHashMap（JSON 对象产物），统一归一为
     * {@link SysNumberRuleConfigDo}（convertValue / readValue），禁止手工拼 JSON 串。
     * 空值或无法解析时按规则非法抛 {@link NumberRuleInvalidException}；
     * 已是 ConfigDo 的直接透传。
     */
    private SysNumberRuleConfigDo normalizeConfig(Object fieldRule) {
        if (null == fieldRule || (fieldRule instanceof String json && StringUtil.isEmpty(json))) {
            throw invalidConfigError("编号规则不能为空：须至少包含一个流水号（seq）项");
        }
        if (fieldRule instanceof SysNumberRuleConfigDo config) {
            return config;
        }
        try {
            if (fieldRule instanceof String json) {
                return JSON_MAPPER.readValue(json, SysNumberRuleConfigDo.class);
            }
            return JSON_MAPPER.convertValue(fieldRule, SysNumberRuleConfigDo.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw invalidConfigError("编号规则 field_rule 无法解析为合法规则结构", e);
        }
    }

    /**
     * 存储态规则解析（field_rule 原文 → ConfigDo）：缺失或 JSON 损坏按规则非法处理，
     * 抛 {@link NumberRuleInvalidException}（message 说明 field_rule 无法解析）
     */
    private SysNumberRuleConfigDo parseStoredConfig(Object fieldRule, String ruleId) {
        if (!(fieldRule instanceof String json) || StringUtil.isEmpty(json)) {
            throw invalidConfigError("编号规则 field_rule 无法解析：规则 " + ruleId + " 缺少规则 JSON");
        }
        try {
            return JSON_MAPPER.readValue(json, SysNumberRuleConfigDo.class);
        } catch (JsonProcessingException e) {
            throw invalidConfigError("编号规则 field_rule 无法解析：规则 " + ruleId + " 的规则 JSON 损坏", e);
        }
    }

    /**
     * 规则 JSON 落库序列化：已校验的固定结构 POJO 序列化失败属程序异常，
     * 抛 IllegalStateException 令事务回滚
     */
    private String toStoreJson(SysNumberRuleConfigDo config) {
        try {
            return JSON_MAPPER.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("编号规则 JSON 序列化失败", e);
        }
    }

    private static NumberRuleInvalidException invalidConfigError(String message) {
        return new NumberRuleInvalidException(List.of(new SysNumberRuleErrorDo(
                SysNumberRuleValidator.CODE_C1, null, message)));
    }

    private static NumberRuleInvalidException invalidConfigError(String message, Throwable cause) {
        return new NumberRuleInvalidException(List.of(new SysNumberRuleErrorDo(
                SysNumberRuleValidator.CODE_C1, null, message)), cause);
    }

    /**
     * 查询条件归一：空白转 null（trim），空对象兜底（对齐 SysOrgElementService#normalizeQuery）
     */
    private static SysNumberRuleQuery normalizeQuery(SysNumberRuleQuery query) {
        SysNumberRuleQuery safe = null == query ? new SysNumberRuleQuery() : query;
        safe.setFieldName(StringUtil.trimToNull(safe.getFieldName()));
        safe.setFieldCode(StringUtil.trimToNull(safe.getFieldCode()));
        safe.setFieldModelId(StringUtil.trimToNull(safe.getFieldModelId()));
        safe.setFieldModelField(StringUtil.trimToNull(safe.getFieldModelField()));
        return safe;
    }

    /**
     * 分页参数归一：页码小于 1 修正为 1、页大小小于 1 修正为 10
     * （对齐 SysOrgElementService#normalizePages）
     */
    private static Pages normalizePages(Pages pages) {
        Pages safe = null == pages ? new Pages() : pages;
        if (safe.getPageNo() < 1) {
            safe.setPageNo(1);
        }
        if (safe.getPageSize() < 1) {
            safe.setPageSize(10);
        }
        return safe;
    }
}
