package com.eric.base.sys.datasource.service;

import com.eric.base.common.base.model.Pages;
import com.eric.base.common.exception.AuthorizationException;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.resolver.AuthorizationHelper;
import com.eric.base.common.utils.FieldProjector;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.datasource.dao.SysDatasourceDao;
import com.eric.base.sys.datasource.enums.SysDatasourceOp;
import com.eric.base.sys.datasource.enums.SysDatasourceVar;
import com.eric.base.sys.datasource.model.SysDatasource;
import com.eric.base.sys.datasource.response.SysDatasourceOptionVo;
import com.eric.base.sys.datasource.response.SysDatasourceTargetVo;
import com.eric.base.sys.datasource.response.SysDatasourceVo;
import com.eric.base.sys.datasource.spi.SysDatasourceConditionDo;
import com.eric.base.sys.datasource.spi.SysDatasourceDefaultDo;
import com.eric.base.sys.datasource.spi.SysDatasourceFieldDo;
import com.eric.base.sys.datasource.spi.SysDatasourceProvider;
import com.eric.base.sys.datasource.spi.SysDatasourceQueryContext;
import com.eric.base.sys.datasource.spi.SysDatasourceQueryResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.eric.base.sys.datasource.properties.SysDatasourceConstants.CS_DATASOURCE_CODE_DUPLICATED;
import static com.eric.base.sys.datasource.properties.SysDatasourceConstants.CS_DATASOURCE_CONDITION_INVALID;
import static com.eric.base.sys.datasource.properties.SysDatasourceConstants.CS_DATASOURCE_FIELD_INVALID;
import static com.eric.base.sys.datasource.properties.SysDatasourceConstants.CS_DATASOURCE_NOT_FOUND;
import static com.eric.base.sys.datasource.properties.SysDatasourceConstants.CS_DATASOURCE_TARGET_INVALID;

/**
 * 数据源业务服务
 *
 * @author Eric
 * @date 2026/11/13
 * @description 承载自定义数据源管理与统一执行编排（方案 §3.1 / §5.2 / §6，样例代码 §2.5）：
 *              <ul>
 *              <li>管理：list/count/load/save/update/enable/delete——编码全局查重
 *                  （Registry 占用全集 + DB selectByCode），fieldTarget 保存后锁定，
 *                  target 存在性校验，条件/字段保存期静态校验；</li>
 *              <li>执行编排 execute（方案 §3.1 时序）：解析数据源（默认源走 Registry，
 *                  自定义源走 DB 且须启用）→ 委托目标 action 鉴权 → 条件合并 → 排序白名单 →
 *                  Provider 查询 → FieldProjector 投影 → rows/total/fields；</li>
 *              <li>options / targets：消费下拉与配置表单数据源。</li>
 *              </ul>
 *              方案 §6 安全设计七条与本类的落点对照：
 *              <ol>
 *              <li>执行收口（A1）：取数只经 {@link #execute}；{@link #options} 仅返回
 *                  编码/名称/有效字段定义与固定条件数量，不暴露固定条件原文；</li>
 *              <li>能力白名单（A2）：{@link #requireTargetProvider} 限定 field_target 只能取
 *                  Registry 已注册能力编码（save 期校验、execute 期解析双保险）；</li>
 *              <li>条件白名单（A7）：{@link #validateConditions}（保存期）与
 *                  {@link #mergeConditions}（执行期）限定 key ∈ Provider conditionFields、
 *                  op ∈ 字段声明 ops、{@code literalAllowed=false} 字段仅接受运行时变量，
 *                  字面量一律拒绝（{@link #resolveConditionValue}）；</li>
 *              <li>固定条件不可覆盖：{@link #mergeConditions} 固定条件先放、调用方同名 key
 *                  直接丢弃；</li>
 *              <li>投影安全（A4）：{@link #resolveOutputFields} 限定白名单 ⊆ Provider
 *                  resultFields 母集，投影复用 {@link FieldProjector}（属性名正则天然排除
 *                  class 等系统方法，null 值不输出）；</li>
 *              <li>排序白名单：{@link #sanitizeSortField} 限定 sortField ∈ sortable=true
 *                  字段集，非法值回退 null（目标能力按默认排序执行，不进入 SQL 拼接）；</li>
 *              <li>数据边界不放宽（A5）：本类不做任何边界放宽——条件合并只可能收紧，
 *                  目标能力固定数据边界由 Provider 及其 Mapper 固定段保留。</li>
 *              </ol>
 *              CS_* 错误码以异常 message 前缀承载（DOC-002 §4.10.6，对齐编号机制
 *              NumberRuleDuplicatedException 先例），经 GlobalExceptionHandler 既有
 *              BAD_REQUEST 分支返回，前端按前缀识别。
 */
@Service
public class SysDatasourceService {

    private static final Logger log = LoggerFactory.getLogger(SysDatasourceService.class);

    /** 配置 JSON 序列化 / 解析（固定结构 POJO，禁止手工拼串，先例 sys_number_rule.field_rule） */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static final TypeReference<List<SysDatasourceConditionDo>> CONDITION_LIST_TYPE =
            new TypeReference<>() {
            };

    private static final TypeReference<List<String>> FIELD_LIST_TYPE = new TypeReference<>() {
    };

    private final SysDatasourceDao baseDao;

    private final SysDatasourceRegistry registry;

    private final AuthorizationHelper authorizationHelper;

    public SysDatasourceService(SysDatasourceDao baseDao, SysDatasourceRegistry registry,
                                AuthorizationHelper authorizationHelper) {
        this.baseDao = baseDao;
        this.registry = registry;
        this.authorizationHelper = authorizationHelper;
    }

    /* ==================== 管理 ==================== */

    /**
     * 自定义数据源管理列表：fieldConditions / fieldFields 原文返回不解析（列表页不解析 JSON），
     * 条件 fieldName / fieldCode 模糊、fieldTarget / fieldEnable 精确，排序固定创建时间降序。
     *
     * @param body  查询条件（可空字段忽略）
     * @param pages 分页参数（可空，needPage=false 返回全部）
     * @return 列表（fieldTargetName / fieldTargetPath 由 Registry 带出，目标失效时为 null，R05）
     */
    public List<SysDatasourceVo> list(SysDatasource body, Pages pages) {
        List<SysDatasource> rows = baseDao.selectList(normalizeCriteria(body), pages);
        List<SysDatasourceVo> result = new ArrayList<>(rows.size());
        for (SysDatasource row : rows) {
            result.add(toVo(row, false));
        }
        return result;
    }

    /**
     * 管理列表总数：查询条件与 {@link #list} 完全一致，供响应 metadata.total 使用。
     *
     * @param body 查询条件（可空字段忽略）
     * @return 匹配总数
     */
    public long count(SysDatasource body) {
        return baseDao.selectCount(normalizeCriteria(body));
    }

    /**
     * 数据源详情：fieldConditions / fieldFields 解析为对象返回（前端表单/详情页直接消费）；
     * 损坏 JSON 按配置无效抛 {@code CS_DATASOURCE_CONDITION_INVALID} / {@code CS_DATASOURCE_FIELD_INVALID}。
     *
     * @param fieldId 数据源 ID
     * @return 详情（解析后形态，fieldTargetName / fieldTargetPath 由 Registry 带出）
     */
    public SysDatasourceVo load(String fieldId) {
        SysDatasource exist = requireRow(StringUtil.requireText(fieldId, "fieldId"));
        return toVo(exist, true);
    }

    /**
     * 新建自定义数据源。校验顺序：基础必填 → target 存在性（A2）→ 条件静态校验（A7）→
     * 字段白名单校验 → 编码全局查重（Registry 占用全集 + DB，防伪装默认源/能力码）。
     * <p>fieldId 沿用前端经 /backend/common/initID 预取值，为空 / 非 32 位时由
     * BaseModel.setFieldId 兜底 IDUtil.initID()；新建默认启用（表单无启用字段，忽略入参）；
     * 条件 / 字段白名单空配置统一序列化为 {@code []} 落库（语义与 NULL 等价，
     * 且保证 update 通道可清除既有值）。</p>
     *
     * @param body 保存体（fieldCode / fieldName / fieldTarget 必填）
     * @return 保存后的详情（解析后形态）
     */
    @Transactional
    public SysDatasourceVo save(SysDatasource body) {
        requireBody(body);
        SysDatasourceProvider provider = requireTargetProvider(body.getFieldTarget());
        List<SysDatasourceConditionDo> conditions = parseConditions(body.getFieldConditions());
        validateConditions(provider, conditions);
        List<String> fields = parseFields(body.getFieldFields());
        validateOutputFields(provider, fields);
        assertCodeAvailable(body.getFieldCode().trim(), null);

        SysDatasource model = new SysDatasource();
        model.setFieldId(body.getFieldId());
        model.setFieldCode(body.getFieldCode().trim());
        model.setFieldName(body.getFieldName().trim());
        model.setFieldTarget(provider.getTargetCode());
        model.setFieldConditions(toStoreJson(conditions));
        model.setFieldFields(toStoreJson(fields));
        model.setFieldRemark(StringUtil.trimToNull(body.getFieldRemark()));
        model.setFieldEnable(Boolean.TRUE);
        String operatorId = UserUtil.getCurrentUserId();
        Date now = new Date();
        model.setFieldCreatorId(operatorId);
        model.setFieldCreateTime(now);
        model.setFieldUpdaterId(operatorId);
        model.setFieldUpdateTime(now);
        baseDao.insert(model);
        log.info("datasource saved: id={}, code={}, target={}",
                model.getFieldId(), model.getFieldCode(), model.getFieldTarget());
        return load(model.getFieldId());
    }

    /**
     * 编辑自定义数据源。<b>fieldTarget 保存后锁定（D3 先例）</b>：以库值为准不受理变更、
     * 入参忽略（改动会使既有条件/字段白名单悬空），target 存在性与条件/字段校验均按
     * 库值 target 的 Provider 定义执行（先例 SysNumberRuleService#update 按库值校验接入项）；
     * 编码可改但须查重（排除自身 + Registry 占用全集）；启用态保持不变（启停走 enable 通道）。
     *
     * @param body 保存体（fieldId / fieldCode / fieldName 必填）
     * @return 更新后的详情（解析后形态）
     */
    @Transactional
    public SysDatasourceVo update(SysDatasource body) {
        requireBody(body);
        String fieldId = StringUtil.requireText(body.getFieldId(), "fieldId");
        SysDatasource exist = requireRow(fieldId);
        SysDatasourceProvider provider = requireTargetProvider(exist.getFieldTarget());
        List<SysDatasourceConditionDo> conditions = parseConditions(body.getFieldConditions());
        validateConditions(provider, conditions);
        List<String> fields = parseFields(body.getFieldFields());
        validateOutputFields(provider, fields);
        assertCodeAvailable(body.getFieldCode().trim(), fieldId);

        SysDatasource model = new SysDatasource();
        model.setFieldId(fieldId);
        model.setFieldCode(body.getFieldCode().trim());
        model.setFieldName(body.getFieldName().trim());
        model.setFieldConditions(toStoreJson(conditions));
        model.setFieldFields(toStoreJson(fields));
        model.setFieldRemark(StringUtil.trimToNull(body.getFieldRemark()));
        model.setFieldUpdaterId(UserUtil.getCurrentUserId());
        model.setFieldUpdateTime(new Date());
        // fieldTarget 不进入更新模型：Mapper update 无该列入口（保存后锁定，D3 先例）
        int affected = baseDao.update(model);
        if (affected != 1) {
            throw new IllegalStateException("update expected one affected row but got " + affected);
        }
        log.info("datasource updated: id={}, code={}", fieldId, model.getFieldCode());
        return load(fieldId);
    }

    /**
     * 启停用：仅更新 field_enable 与修改人/修改时间（Mapper updateEnable 专属语句）。
     * 停用后 /query 即报 {@code CS_DATASOURCE_NOT_FOUND}、/options 不再返回该源。
     *
     * @param fieldId     数据源 ID
     * @param fieldEnable 必填：true 启用 / false 停用
     */
    @Transactional
    public void updateEnable(String fieldId, Boolean fieldEnable) {
        String id = StringUtil.requireText(fieldId, "fieldId");
        if (null == fieldEnable) {
            throw new IllegalArgumentException("fieldEnable 必填，不能为空");
        }
        SysDatasource exist = requireRow(id);
        int affected = baseDao.updateEnable(id, fieldEnable, UserUtil.getCurrentUserId(), new Date());
        if (affected != 1) {
            throw new IllegalStateException("enable expected one affected row but got " + affected);
        }
        log.info("datasource enable switched: id={}, enable={}", exist.getFieldId(), fieldEnable);
    }

    /**
     * 删除自定义数据源：无附加在用校验——数据源属取数配置元数据，误删影响面为
     * 消费方取数失败（{@code CS_DATASOURCE_NOT_FOUND}），前端对启用中的数据源删除须二次确认。
     *
     * @param fieldId 数据源 ID
     */
    @Transactional
    public void delete(String fieldId) {
        String id = StringUtil.requireText(fieldId, "fieldId");
        SysDatasource exist = requireRow(id);
        baseDao.deleteById(exist.getFieldId());
        log.info("datasource deleted: id={}, code={}", exist.getFieldId(), exist.getFieldCode());
    }

    /* ==================== 统一执行编排 ==================== */

    /**
     * 统一执行入口（方案 §3.1 时序）：按 code 解析数据源 → 委托目标 action 鉴权 →
     * 条件合并 → 排序白名单 → Provider 查询 → FieldProjector 投影。
     * <p>接口段鉴权（sys.datasource.query，登录即可）由 Controller 前置完成；
     * 目标段鉴权在查询前经 {@link AuthorizationHelper} 委托目标 action（§5.2 两段式）——
     * 「能通过数据源取数」的前提是「有目标接口权限」。</p>
     *
     * @param code      数据源编码（默认或自定义）
     * @param body      调用方过滤条件（固定条件同名覆盖之）
     * @param pages     分页参数（可空 = 不分页）
     * @param sortField 排序字段（须命中目标能力可排序白名单，非法值回退默认排序）
     * @param sortOrder 排序方向 asc / desc（可为 null，默认 desc 由 Provider 处理）
     * @return 投影后行列表、总数与实际输出列（供前端渲染表头，R04：列头以 fields 为准）
     */
    public SysDatasourceExecution execute(String code, Map<String, Object> body, Pages pages,
                                          String sortField, String sortOrder) {
        // ① 解析数据源：默认源走 Registry，自定义源走 sys_datasource 且须启用
        Resolved resolved = resolve(code);
        // ② 委托目标 action 鉴权（§5.2 两段式目标段）：未登录 UNAUTHORIZED、无权限 FORBIDDEN
        authorizationHelper.authorize(resolved.provider().getTargetResolver(),
                new AuthorizationContext(resolved.provider().getTargetAction()));
        // ③ 条件合并：固定条件（解析变量）覆盖调用方同名条件，白名单与字面量限制校验（A7）
        Map<String, Object> conditions = mergeConditions(resolved, body);
        // ④ 排序字段白名单校验，非法值回退 null（目标能力按默认排序执行）
        String safeSortField = sanitizeSortField(resolved.provider(), sortField);
        // ⑤ Provider 执行查询（沿用目标能力既有数据边界，A5 只收紧不放宽）
        SysDatasourceQueryContext context = new SysDatasourceQueryContext();
        context.setConditions(conditions);
        context.setPages(pages);
        context.setSortField(safeSortField);
        context.setSortOrder(sortOrder);
        SysDatasourceQueryResult result = resolved.provider().query(context);
        // ⑥ 字段投影（A4）：配置白名单为空 = 目标能力全字段
        List<String> fields = resolveOutputFields(resolved);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (null != result.getRows()) {
            for (Object row : result.getRows()) {
                rows.add(FieldProjector.project(row, fields));
            }
        }
        return new SysDatasourceExecution(rows, result.getTotal(), fields);
    }

    /**
     * 执行结果：rows 为投影后动态列（{@code List<Map<String,Object>>}，值 null 的字段不输出），
     * fields 为实际输出列供前端渲染表头。
     */
    public record SysDatasourceExecution(List<Map<String, Object>> rows, long total, List<String> fields) {
    }

    /* ==================== options / targets ==================== */

    /**
     * 可用数据源下拉：默认数据源（CODE）+ 启用的自定义数据源（CUSTOM）。
     * 只暴露消费所需元数据（编码、名称、能力、有效字段定义与固定条件数量），
     * <b>不暴露固定条件原文（A1：固定条件属服务端强制语义，不是可协商参数）</b>；
     * 目标失效（Provider 下线残留）的启用行不在消费下拉暴露（R05：经管理列表识别，
     * 执行报 {@code CS_DATASOURCE_TARGET_INVALID}）。
     *
     * @return 下拉条目（默认源在前，自定义启用源按创建时间降序）
     */
    public List<SysDatasourceOptionVo> options() {
        List<SysDatasourceOptionVo> result = new ArrayList<>();
        for (SysDatasourceDefaultDo def : registry.listDefaults()) {
            SysDatasourceProvider provider = registry.getProviderByDefaultCode(def.getCode());
            if (null == provider) {
                continue;
            }
            SysDatasourceOptionVo vo = new SysDatasourceOptionVo();
            vo.setCode(def.getCode());
            vo.setName(def.getName());
            vo.setSourceType(SysDatasourceRegistry.typeOf(false));
            vo.setTarget(provider.getTargetCode());
            vo.setTargetPath(provider.getTargetPath());
            vo.setFields(effectiveFields(provider, def.getFields()));
            vo.setConditionCount(normalizeConditions(def.getConditions()).size());
            result.add(vo);
        }
        SysDatasource criteria = new SysDatasource();
        criteria.setFieldEnable(Boolean.TRUE);
        for (SysDatasource custom : baseDao.selectList(criteria, null)) {
            SysDatasourceProvider provider = registry.getProviderByTarget(custom.getFieldTarget());
            if (null == provider) {
                continue;
            }
            SysDatasourceOptionVo vo = new SysDatasourceOptionVo();
            vo.setCode(custom.getFieldCode());
            vo.setName(custom.getFieldName());
            vo.setSourceType(SysDatasourceRegistry.typeOf(true));
            vo.setTarget(custom.getFieldTarget());
            vo.setTargetPath(provider.getTargetPath());
            vo.setFields(effectiveFields(provider, parseFields(custom.getFieldFields())));
            vo.setConditionCount(normalizeConditions(parseConditions(custom.getFieldConditions())).size());
            result.add(vo);
        }
        return result;
    }

    /**
     * 目标查询能力清单（targets）：Registry 能力全集 + 各自条件/返回字段定义，
     * 配置表单据此选能力、编排固定条件、勾选返回字段；code 即 field_target 取值。
     *
     * @return 能力列表（Registry 注册顺序）
     */
    public List<SysDatasourceTargetVo> targets() {
        List<SysDatasourceTargetVo> result = new ArrayList<>();
        for (SysDatasourceProvider provider : registry.listProviders()) {
            SysDatasourceTargetVo vo = new SysDatasourceTargetVo();
            vo.setCode(provider.getTargetCode());
            vo.setName(provider.getTargetName());
            vo.setTargetAction(provider.getTargetAction());
            vo.setTargetPath(provider.getTargetPath());
            vo.setConditionFields(provider.getConditionFields());
            vo.setResultFields(provider.getResultFields());
            result.add(vo);
        }
        return result;
    }

    /* ==================== 内部：解析与执行编排 ==================== */

    /**
     * 解析数据源（方案 §3.1 ①）：默认源（Registry）优先，其次自定义（sys_datasource，须启用）；
     * code 不存在 / 已停用抛 {@code CS_DATASOURCE_NOT_FOUND}；自定义源 target 失效
     * （Provider 下线残留）抛 {@code CS_DATASOURCE_TARGET_INVALID}（R05）。
     */
    private Resolved resolve(String code) {
        String safeCode = StringUtil.trimToNull(code);
        SysDatasourceDefaultDo def = registry.getDefault(safeCode);
        if (null != def) {
            SysDatasourceProvider provider = registry.getProviderByDefaultCode(safeCode);
            return new Resolved(provider, normalizeConditions(def.getConditions()), def.getFields());
        }
        SysDatasource custom = null == safeCode ? null : baseDao.selectByCode(safeCode);
        if (null == custom || !Boolean.TRUE.equals(custom.getFieldEnable())) {
            throw datasourceError(CS_DATASOURCE_NOT_FOUND, "数据源不存在或已停用: " + code);
        }
        SysDatasourceProvider provider = registry.getProviderByTarget(custom.getFieldTarget());
        if (null == provider) {
            throw datasourceError(CS_DATASOURCE_TARGET_INVALID,
                    "数据源挂接的目标查询能力不存在或已下线: " + custom.getFieldTarget());
        }
        return new Resolved(provider, parseConditions(custom.getFieldConditions()),
                parseFields(custom.getFieldFields()));
    }

    /**
     * 条件合并（方案 §3.1 ③ / §6.3 / §6.4）：固定条件在前（解析变量 + op 防御校验），
     * 调用方条件 key 须 ∈ Provider 条件白名单、{@code literalAllowed=false} 字段禁止字面量，
     * 固定条件已占用的同名 key 直接丢弃（固定条件不可覆盖）。
     */
    private Map<String, Object> mergeConditions(Resolved resolved, Map<String, Object> body) {
        Map<String, SysDatasourceFieldDo> whitelist = conditionWhitelist(resolved.provider());
        Map<String, Object> merged = new LinkedHashMap<>();
        // ① 固定条件（保存期已校验；Registry/Provider 定义变化时此处置防御）
        for (SysDatasourceConditionDo fixed : resolved.fixedConditions()) {
            SysDatasourceFieldDo def = whitelist.get(fixed.getKey());
            if (null == def) {
                throw conditionInvalid("固定条件字段不在目标能力条件白名单: " + fixed.getKey());
            }
            merged.put(fixed.getKey(), resolveFixedValue(def, fixed));
        }
        // ② 调用方条件（固定条件已占用的同名 key 丢弃，§6.4）
        if (null != body) {
            for (Map.Entry<String, Object> entry : body.entrySet()) {
                if (merged.containsKey(entry.getKey())) {
                    continue;
                }
                SysDatasourceFieldDo def = whitelist.get(entry.getKey());
                if (null == def || null == entry.getValue()) {
                    throw conditionInvalid("调用方条件字段不在白名单或值为空: " + entry.getKey());
                }
                merged.put(entry.getKey(), resolveCallerValue(def, entry.getValue()));
            }
        }
        return merged;
    }

    /**
     * 固定条件值解析：op ∈ 字段声明 ops（防御校验）→ 运行时变量求值 / 字面量
     * 字面量限制（A7）→ 类型转换。
     */
    private Object resolveFixedValue(SysDatasourceFieldDo def, SysDatasourceConditionDo fixed) {
        SysDatasourceOp op = SysDatasourceOp.of(fixed.getOp());
        if (null == op || null == def.getOps() || !def.getOps().contains(op.name())) {
            throw conditionInvalid("固定条件操作符不受支持: " + fixed.getOp());
        }
        return resolveConditionValue(def, fixed.getValue(), fixed.getKey());
    }

    /**
     * 调用方条件值处理：{@code literalAllowed=false} 字段仅接受运行时变量占位符
     * （如 {@code ${CURRENT_USER_ID}}），字面量一律拒绝（A7 防写死他人 ID 越权）；
     * 其余字段值透传（op 语义由 Provider 映射实现，值类型由调用方负责）。
     */
    private Object resolveCallerValue(SysDatasourceFieldDo def, Object value) {
        if (!def.isLiteralAllowed()) {
            return resolveConditionValue(def, String.valueOf(value), def.getKey());
        }
        return value;
    }

    /**
     * 条件值解析公共段：运行时变量求值（CURRENT_USER_ID 未登录由前置委托鉴权拦截，
     * 此处双保险抛 UNAUTHORIZED）；字面量受 {@code literalAllowed} 限制；
     * 类型转换失败（值类型非法）按条件无效处理。
     */
    private Object resolveConditionValue(SysDatasourceFieldDo def, String value, String key) {
        String text = null == value ? "" : value.trim();
        for (SysDatasourceVar var : SysDatasourceVar.values()) {
            if (var.matches(text)) {
                String resolvedValue = var.resolve();
                if (null == resolvedValue) {
                    throw new AuthorizationException("UNAUTHORIZED", "登录已过期，请重新登录");
                }
                return convertValue(def, resolvedValue, key);
            }
        }
        if (!def.isLiteralAllowed()) {
            throw conditionInvalid("条件字段仅接受运行时变量: " + key);
        }
        return convertValue(def, value, key);
    }

    private Object convertValue(SysDatasourceFieldDo def, String value, String key) {
        try {
            return def.convert(value);
        } catch (RuntimeException e) {
            throw conditionInvalid("条件值类型非法: " + key);
        }
    }

    /**
     * 排序字段白名单（§6.6）：取返回字段定义中 sortable=true 的集合，
     * 非白名单字段回退 null（目标能力按默认排序执行，不进入 SQL 拼接）。
     */
    private String sanitizeSortField(SysDatasourceProvider provider, String sortField) {
        String safe = StringUtil.trimToNull(sortField);
        if (null == safe) {
            return null;
        }
        for (SysDatasourceFieldDo field : provider.getResultFields()) {
            if (field.isSortable() && field.getKey().equals(safe)) {
                return safe;
            }
        }
        return null;
    }

    /**
     * 有效输出字段（§6.5 投影安全）：配置白名单（或默认数据源 fields）为空 =
     * 目标能力默认全字段；非空时逐一校验 ∈ Provider 返回字段母集，
     * 否则抛 {@code CS_DATASOURCE_FIELD_INVALID}（保存期与执行期同口径）。
     */
    private List<String> resolveOutputFields(Resolved resolved) {
        Map<String, SysDatasourceFieldDo> resultFields = new LinkedHashMap<>();
        for (SysDatasourceFieldDo field : resolved.provider().getResultFields()) {
            resultFields.put(field.getKey(), field);
        }
        if (null == resolved.outputFields() || resolved.outputFields().isEmpty()) {
            return new ArrayList<>(resultFields.keySet());
        }
        Set<String> keys = new LinkedHashSet<>();
        for (String key : resolved.outputFields()) {
            if (!resultFields.containsKey(key)) {
                throw datasourceError(CS_DATASOURCE_FIELD_INVALID, "返回字段不在目标能力返回字段母集: " + key);
            }
            keys.add(key);
        }
        return new ArrayList<>(keys);
    }

    /**
     * options 有效字段定义：白名单空 = 目标能力全字段；非空按白名单顺序裁剪母集
     * （未知 key 保存期已拦截，此处跳过以保证下拉可用；执行安全由 resolveOutputFields 承担）。
     */
    private List<SysDatasourceFieldDo> effectiveFields(SysDatasourceProvider provider, List<String> whitelist) {
        List<SysDatasourceFieldDo> all = provider.getResultFields();
        if (null == whitelist || whitelist.isEmpty()) {
            return all;
        }
        List<SysDatasourceFieldDo> result = new ArrayList<>();
        for (String key : whitelist) {
            for (SysDatasourceFieldDo field : all) {
                if (field.getKey().equals(key)) {
                    result.add(field);
                    break;
                }
            }
        }
        return result;
    }

    /* ==================== 内部：管理校验 ==================== */

    /**
     * 基础校验：实体非空、编码与名称必填（库列 NOT NULL）
     */
    private static void requireBody(SysDatasource body) {
        if (null == body) {
            throw new IllegalArgumentException("数据源实体不能为空");
        }
        StringUtil.requireText(body.getFieldCode(), "fieldCode");
        StringUtil.requireText(body.getFieldName(), "fieldName");
    }

    /**
     * 目标能力存在性（A2 能力白名单）：fieldTarget 须 ∈ Registry，否则
     * {@code CS_DATASOURCE_TARGET_INVALID}
     */
    private SysDatasourceProvider requireTargetProvider(String target) {
        SysDatasourceProvider provider = registry.getProviderByTarget(StringUtil.trimToNull(target));
        if (null == provider) {
            throw datasourceError(CS_DATASOURCE_TARGET_INVALID,
                    "目标查询能力不存在或已下线: " + target);
        }
        return provider;
    }

    /**
     * 编码全局查重：DB 同编码行（update 场景排除自身）+ Registry 编码占用全集
     * （默认数据源编码 + 目标能力编码，防伪装默认源/能力码，方案 §4.2）
     */
    private void assertCodeAvailable(String code, String excludeFieldId) {
        SysDatasource exist = baseDao.selectByCode(code);
        if (null != exist && !exist.getFieldId().equals(excludeFieldId)) {
            throw datasourceError(CS_DATASOURCE_CODE_DUPLICATED, "数据源编码已存在: " + code);
        }
        if (registry.isCodeOccupied(code)) {
            throw datasourceError(CS_DATASOURCE_CODE_DUPLICATED,
                    "数据源编码与默认数据源/目标能力编码冲突: " + code);
        }
    }

    /**
     * 条件保存期静态校验（A7）：key ∈ Provider 条件白名单、op ∈ 字段声明 ops、
     * {@code literalAllowed=false} 字段仅接受运行时变量，字面量一律拒绝。
     */
    private void validateConditions(SysDatasourceProvider provider, List<SysDatasourceConditionDo> conditions) {
        if (null == conditions || conditions.isEmpty()) {
            return;
        }
        Map<String, SysDatasourceFieldDo> whitelist = conditionWhitelist(provider);
        for (SysDatasourceConditionDo condition : conditions) {
            SysDatasourceFieldDo def = whitelist.get(condition.getKey());
            if (null == def) {
                throw conditionInvalid("条件字段不在目标能力条件白名单: " + condition.getKey());
            }
            SysDatasourceOp op = SysDatasourceOp.of(condition.getOp());
            if (null == op || null == def.getOps() || !def.getOps().contains(op.name())) {
                throw conditionInvalid("条件操作符不受支持: " + condition.getOp());
            }
            if (!def.isLiteralAllowed() && !isVarPlaceholder(condition.getValue())) {
                throw conditionInvalid("条件字段仅接受运行时变量: " + condition.getKey());
            }
        }
    }

    /**
     * 字段白名单保存期校验（§6.5）：各项 ⊆ Provider 返回字段母集，否则
     * {@code CS_DATASOURCE_FIELD_INVALID}
     */
    private void validateOutputFields(SysDatasourceProvider provider, List<String> fields) {
        if (null == fields || fields.isEmpty()) {
            return;
        }
        Set<String> allowed = new LinkedHashSet<>();
        for (SysDatasourceFieldDo field : provider.getResultFields()) {
            allowed.add(field.getKey());
        }
        for (String key : fields) {
            if (!allowed.contains(key)) {
                throw datasourceError(CS_DATASOURCE_FIELD_INVALID, "返回字段不在目标能力返回字段母集: " + key);
            }
        }
    }

    private SysDatasource requireRow(String fieldId) {
        SysDatasource exist = baseDao.selectById(fieldId);
        if (null == exist) {
            throw new IllegalArgumentException("数据源不存在: " + fieldId);
        }
        return exist;
    }

    /* ==================== 内部：模型转换与 JSON 承载 ==================== */

    /**
     * 实体 → 响应 Vo：fieldTargetName / fieldTargetPath 由 Registry 带出（仅展示），
     * 目标失效时两者为 null（R05：管理列表可识别标记）；parseStoredJson=true 时
     * 条件 / 字段白名单解析为对象（load 响应），false 时原文 JSON 字符串直出（列表响应）。
     */
    private SysDatasourceVo toVo(SysDatasource model, boolean parseStoredJson) {
        SysDatasourceVo vo = new SysDatasourceVo();
        vo.setFieldId(model.getFieldId());
        vo.setFieldCode(model.getFieldCode());
        vo.setFieldName(model.getFieldName());
        vo.setFieldEnable(model.getFieldEnable());
        vo.setFieldCreatorId(model.getFieldCreatorId());
        vo.setFieldCreateTime(model.getFieldCreateTime());
        vo.setFieldUpdaterId(model.getFieldUpdaterId());
        vo.setFieldUpdateTime(model.getFieldUpdateTime());
        vo.setFieldTarget(model.getFieldTarget());
        SysDatasourceProvider provider = registry.getProviderByTarget(model.getFieldTarget());
        if (null != provider) {
            vo.setFieldTargetName(provider.getTargetName());
            vo.setFieldTargetPath(provider.getTargetPath());
        }
        if (parseStoredJson) {
            vo.setFieldConditions(parseConditions(model.getFieldConditions()));
            vo.setFieldFields(parseFields(model.getFieldFields()));
        } else {
            vo.setFieldConditions(model.getFieldConditions());
            vo.setFieldFields(model.getFieldFields());
        }
        vo.setFieldRemark(model.getFieldRemark());
        return vo;
    }

    /**
     * 固定条件 JSON 解析归一（Object ↔ List&lt;SysDatasourceConditionDo&gt;）：
     * 请求体反序列化产物（List&lt;LinkedHashMap&gt;）经 convertValue 归一、存储态原文字符串
     * 经 readValue；损坏 JSON 按配置无效抛 {@code CS_DATASOURCE_CONDITION_INVALID}
     * （先例 SysNumberRuleService 对 field_rule 的解析容错）。
     */
    private List<SysDatasourceConditionDo> parseConditions(Object fieldConditions) {
        return parseList(fieldConditions, CONDITION_LIST_TYPE, CS_DATASOURCE_CONDITION_INVALID, "固定条件");
    }

    /**
     * 返回字段白名单 JSON 解析归一（Object ↔ List&lt;String&gt;）；
     * 损坏 JSON 按配置无效抛 {@code CS_DATASOURCE_FIELD_INVALID}
     */
    private List<String> parseFields(Object fieldFields) {
        return parseList(fieldFields, FIELD_LIST_TYPE, CS_DATASOURCE_FIELD_INVALID, "返回字段白名单");
    }

    private static <T> List<T> parseList(Object raw, TypeReference<List<T>> type,
                                         String errorCode, String label) {
        if (null == raw || (raw instanceof String json && StringUtil.isEmpty(json))) {
            return null;
        }
        try {
            if (raw instanceof String json) {
                return JSON_MAPPER.readValue(json, type);
            }
            return JSON_MAPPER.convertValue(raw, type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw datasourceError(errorCode, label + "配置 JSON 损坏，无法解析");
        }
    }

    /**
     * 配置 JSON 落库序列化：空配置统一存 {@code []}（update 通道 &lt;if test="!= null"&gt;
     * 需非 null 才能清除既有值，语义与 NULL 等价）；已校验结构的序列化失败属程序异常，
     * 抛 IllegalStateException 令事务回滚
     */
    private static String toStoreJson(List<?> value) {
        try {
            return JSON_MAPPER.writeValueAsString(null == value ? List.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("数据源配置 JSON 序列化失败", e);
        }
    }

    /**
     * 查询条件归一：空白转 null（trim），空对象兜底（对齐 SysOrgElementService#normalizeQuery）
     */
    private static SysDatasource normalizeCriteria(SysDatasource body) {
        SysDatasource criteria = null == body ? new SysDatasource() : body;
        criteria.setFieldName(StringUtil.trimToNull(criteria.getFieldName()));
        criteria.setFieldCode(StringUtil.trimToNull(criteria.getFieldCode()));
        criteria.setFieldTarget(StringUtil.trimToNull(criteria.getFieldTarget()));
        return criteria;
    }

    private static Map<String, SysDatasourceFieldDo> conditionWhitelist(SysDatasourceProvider provider) {
        Map<String, SysDatasourceFieldDo> whitelist = new LinkedHashMap<>();
        for (SysDatasourceFieldDo field : provider.getConditionFields()) {
            whitelist.put(field.getKey(), field);
        }
        return whitelist;
    }

    private static List<SysDatasourceConditionDo> normalizeConditions(List<SysDatasourceConditionDo> conditions) {
        return null == conditions ? List.of() : conditions;
    }

    /**
     * 判断文本是否任一运行时变量占位符（如 {@code ${CURRENT_USER_ID}}）
     */
    private static boolean isVarPlaceholder(String value) {
        if (null == value) {
            return false;
        }
        for (SysDatasourceVar var : SysDatasourceVar.values()) {
            if (var.matches(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * CS_* 业务错误码异常构造：message 以错误码为前缀（DOC-002 §4.10.6），
     * 经 GlobalExceptionHandler 既有 BAD_REQUEST 分支返回，前端按前缀识别
     */
    private static IllegalArgumentException datasourceError(String code, String message) {
        return new IllegalArgumentException(code + ": " + message);
    }

    private static IllegalArgumentException conditionInvalid(String message) {
        return datasourceError(CS_DATASOURCE_CONDITION_INVALID, message);
    }

    /**
     * 执行期解析结果
     */
    private record Resolved(SysDatasourceProvider provider,
                            List<SysDatasourceConditionDo> fixedConditions,
                            List<String> outputFields) {
    }
}
