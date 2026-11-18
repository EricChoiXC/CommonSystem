package com.eric.base.sys.formula.service;

import com.eric.base.common.base.model.Pages;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.formula.dao.SysFormulaDao;
import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.enums.SysFormulaType;
import com.eric.base.sys.formula.model.SysFormula;
import com.eric.base.sys.formula.model.SysFormulaErrorDo;
import com.eric.base.sys.formula.properties.SysFormulaConstants;
import com.eric.base.sys.formula.request.SysFormulaValidateRequest;
import com.eric.base.sys.formula.response.SysFormulaFunctionVo;
import com.eric.base.sys.formula.response.SysFormulaOptionVo;
import com.eric.base.sys.formula.response.SysFormulaValidateResponse;
import com.eric.base.sys.formula.response.SysFormulaVo;
import com.eric.base.sys.formula.service.engine.FormulaFunctionRegistry;
import com.eric.base.sys.formula.spi.FormulaFunctionDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.eric.base.sys.formula.properties.SysFormulaConstants.CS_FORMULA_CODE_DUPLICATED;
import static com.eric.base.sys.formula.properties.SysFormulaConstants.CS_FORMULA_NOT_FOUND;
import static com.eric.base.sys.formula.properties.SysFormulaConstants.CS_FORMULA_TYPE_MISMATCH;

/**
 * 命名公式管理服务
 *
 * @author Eric
 * @date 2026/11/18
 * @description 承载命名公式（sys_formula）管理六动作编排与组件读取聚合（方案 §3.1
 *              「命名公式管理面」/ §4.1 / §5.1，F14-BE-007）：
 *              <ul>
 *              <li>管理：list / count / load / save / update / enable / delete——
 *                  编码全局查重（uk_formula_code，save/update 时同编码抛
 *                  CS_FORMULA_CODE_DUPLICATED）；fieldDependencies 双形态承载
 *                  （先例 sys_number_rule.field_rule / SysDatasourceVo）：存储态与
 *                  列表响应为原文 JSON 字符串，load / save / update 响应解析为变量码
 *                  字符串数组；updateEnable 仅更新 field_enable 与修改人/修改时间
 *                  （本表无树形语义，不级联）；</li>
 *              <li>保存期强制校验（方案 §5.1 / UI 文档 4.11.3）：save / update 时
 *                  fieldExpression 先经 {@link SysFormulaValidateService} validate 同源
 *                  校验（宽松模式——命名公式无 vars 声明，变量视为未知，引用失效变量
 *                  不阻断保存、由 validate 报告 §3.3；expectedReturnType 取
 *                  fieldReturnType），errors 非空即拒绝保存（首错以
 *                  CS_FORMULA_*: message 异常前缀承载，对齐 SysDatasourceService
 *                  错误形态，经 GlobalExceptionHandler BAD_REQUEST 分支返回）；</li>
 *              <li>依赖解析落 field_dependencies：保存期校验通过后取 validate 响应的
 *                  dependencies（引擎提取变量码数组、路径投影取根码、去重保序），
 *                  Jackson 序列化为 JSON 字符串落库，禁止手工拼串；空依赖统一存
 *                  {@code []}（先例 SysDatasourceService#toStoreJson——update 通道
 *                  &lt;if test="!= null"&gt; 需非 null 才能清除既有值，语义与 NULL 等价）；</li>
 *              <li>执行 / 试算侧钩子 {@link #requireEnabledFormula}：按 field_code
 *                  加载启用中的命名公式，编码不存在或已停用抛 CS_FORMULA_NOT_FOUND
 *                  （方案 §5.3「公式编码/ID 不存在，或命名公式已停用」）——消费方
 *                  （v1.5 表单引擎 / v1.7 流程引擎）与 {@link SysFormulaSimulateService}
 *                  据此判断可执行性；管理面行不存在沿用先例普通参数错误（BAD_REQUEST），
 *                  CS_FORMULA_NOT_FOUND 收口在执行 / 试算侧；</li>
 *              <li>组件读取聚合：{@link #options()}（启用中的命名公式下拉，
 *                  SysFormulaOptionVo 契约：仅暴露编码/名称/类型/返回类型，不暴露
 *                  表达式原文）与 {@link #functions()}（函数注册中心清单导出为
 *                  SysFormulaFunctionVo，同一份元数据驱动前端函数面板与后端参数校验）。</li>
 *              </ul>
 *              CS_* 错误码以异常 message 前缀承载（DOC-002 §4.11.6 管理接口口径），
 *              错误码全部静态引用 {@link SysFormulaConstants}，禁止手写字符串。
 */
@Service
public class SysFormulaService {

    private static final Logger log = LoggerFactory.getLogger(SysFormulaService.class);

    /** 依赖 JSON 序列化 / 解析（变量码字符串数组，禁止手工拼串，先例 sys_number_rule.field_rule） */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static final TypeReference<List<String>> DEPENDENCY_LIST_TYPE = new TypeReference<>() {
    };

    private final SysFormulaDao baseDao;

    private final SysFormulaValidateService validateService;

    private final FormulaFunctionRegistry functionRegistry;

    public SysFormulaService(SysFormulaDao baseDao, SysFormulaValidateService validateService,
                             FormulaFunctionRegistry functionRegistry) {
        this.baseDao = baseDao;
        this.validateService = validateService;
        this.functionRegistry = functionRegistry;
    }

    /* ==================== 管理 ==================== */

    /**
     * 命名公式管理列表：fieldDependencies 原文返回不解析（列表页不解析 JSON，仅取
     * 数组长度展示「变量依赖数」），条件 fieldName / fieldCode 模糊、fieldType /
     * fieldEnable 精确，排序固定创建时间降序。
     *
     * @param body  查询条件（可空字段忽略）
     * @param pages 分页参数（可空，needPage=false 返回全部）
     * @return 列表（fieldDependencies 为原文 JSON 字符串）
     */
    public List<SysFormulaVo> list(SysFormula body, Pages pages) {
        List<SysFormula> rows = baseDao.selectList(normalizeCriteria(body), pages);
        List<SysFormulaVo> result = new ArrayList<>(rows.size());
        for (SysFormula row : rows) {
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
    public long count(SysFormula body) {
        return baseDao.selectCount(normalizeCriteria(body));
    }

    /**
     * 命名公式详情：fieldDependencies 解析为变量码字符串数组返回（前端详情页逐项展示、
     * 编辑回填）；损坏 JSON 属存储态程序异常，抛 IllegalStateException 令事务回滚。
     *
     * @param fieldId 公式 ID
     * @return 详情（解析后形态）
     */
    public SysFormulaVo load(String fieldId) {
        SysFormula exist = requireRow(StringUtil.requireText(fieldId, "fieldId"));
        return toVo(exist, true);
    }

    /**
     * 新建命名公式。编排顺序：基础必填 → 公式类型枚举校验 → 保存期强制 validate
     * （宽松模式，方案 §5.1，不通过阻断保存）→ 编码全局查重（CS_FORMULA_CODE_DUPLICATED）
     * → 依赖解析落 field_dependencies → 落库。
     * <p>fieldId 沿用前端经 /backend/common/initID 预取值，为空 / 非 32 位时由
     * BaseModel.setFieldId 兜底 IDUtil.initID()；新建默认启用（表单无启用字段，忽略入参）；
     * fieldDependencies 不受理请求传入值——以保存期解析提取结果为准。</p>
     *
     * @param body 保存体（fieldCode / fieldName / fieldType / fieldExpression /
     *             fieldReturnType 必填，fieldRemark 可选）
     * @return 保存后的详情（依赖解析后形态）
     */
    @Transactional
    public SysFormulaVo save(SysFormula body) {
        requireBody(body);
        String fieldType = body.getFieldType().trim();
        String expression = body.getFieldExpression().trim();
        String returnTypeText = body.getFieldReturnType().trim();
        requireFormulaType(fieldType);
        List<String> dependencies = requireValidationPassed(expression, returnTypeText);
        assertCodeAvailable(body.getFieldCode(), null);

        SysFormula model = new SysFormula();
        model.setFieldId(body.getFieldId());
        model.setFieldCode(body.getFieldCode().trim());
        model.setFieldName(body.getFieldName().trim());
        model.setFieldType(fieldType);
        model.setFieldExpression(expression);
        model.setFieldDependencies(toStoreJson(dependencies));
        model.setFieldReturnType(requireReturnType(returnTypeText).name());
        model.setFieldRemark(StringUtil.trimToNull(body.getFieldRemark()));
        model.setFieldEnable(Boolean.TRUE);
        String operatorId = UserUtil.getCurrentUserId();
        Date now = new Date();
        model.setFieldCreatorId(operatorId);
        model.setFieldCreateTime(now);
        model.setFieldUpdaterId(operatorId);
        model.setFieldUpdateTime(now);
        baseDao.insert(model);
        log.info("formula saved: id={}, code={}, dependencies={}",
                model.getFieldId(), model.getFieldCode(), dependencies.size());
        return load(model.getFieldId());
    }

    /**
     * 编辑命名公式。fieldCode 可改但须查重（排除自身）；fieldExpression 变更后重新
     * 强制 validate 并重解析依赖（field_dependencies 随表达式更新）；fieldEnable
     * 不受理（启停走 /enable 通道，保持库值不变）。
     *
     * @param body 保存体（fieldId / fieldCode / fieldName / fieldType / fieldExpression /
     *             fieldReturnType 必填）
     * @return 更新后的详情（依赖解析后形态）
     */
    @Transactional
    public SysFormulaVo update(SysFormula body) {
        requireBody(body);
        StringUtil.requireText(body.getFieldId(), "fieldId");
        SysFormula exist = requireRow(body.getFieldId());
        String fieldType = body.getFieldType().trim();
        String expression = body.getFieldExpression().trim();
        String returnTypeText = body.getFieldReturnType().trim();
        requireFormulaType(fieldType);
        List<String> dependencies = requireValidationPassed(expression, returnTypeText);
        assertCodeAvailable(body.getFieldCode(), exist.getFieldId());

        SysFormula model = new SysFormula();
        model.setFieldId(exist.getFieldId());
        model.setFieldCode(body.getFieldCode().trim());
        model.setFieldName(body.getFieldName().trim());
        model.setFieldType(fieldType);
        model.setFieldExpression(expression);
        model.setFieldDependencies(toStoreJson(dependencies));
        model.setFieldReturnType(requireReturnType(returnTypeText).name());
        model.setFieldRemark(StringUtil.trimToNull(body.getFieldRemark()));
        model.setFieldUpdaterId(UserUtil.getCurrentUserId());
        model.setFieldUpdateTime(new Date());
        // fieldEnable 不进入更新模型：启停走 /enable 通道（Mapper <if test="!= null"> 跳过该列）
        int affected = baseDao.update(model);
        if (affected != 1) {
            throw new IllegalStateException("update expected one affected row but got " + affected);
        }
        log.info("formula updated: id={}, code={}, dependencies={}",
                model.getFieldId(), model.getFieldCode(), dependencies.size());
        return load(exist.getFieldId());
    }

    /**
     * 启停用：仅更新 field_enable 与修改人/修改时间（Mapper updateEnable 专属语句，
     * 只更新自身一行，不级联——本表无树形语义）。停用后命名公式对执行期引用不可见
     * （消费方按 field_code 执行报 CS_FORMULA_NOT_FOUND），管理列表仍可查询与重新启用。
     *
     * @param fieldId     公式 ID
     * @param fieldEnable 必填：true 启用 / false 停用
     */
    @Transactional
    public void updateEnable(String fieldId, Boolean fieldEnable) {
        String id = StringUtil.requireText(fieldId, "fieldId");
        if (null == fieldEnable) {
            throw new IllegalArgumentException("fieldEnable 必填，不能为空");
        }
        SysFormula exist = requireRow(id);
        int affected = baseDao.updateEnable(id, fieldEnable, UserUtil.getCurrentUserId(), new Date());
        if (affected != 1) {
            throw new IllegalStateException("enable expected one affected row but got " + affected);
        }
        log.info("formula enable switched: id={}, enable={}", exist.getFieldId(), fieldEnable);
    }

    /**
     * 删除命名公式：无附加在用校验——命名公式属公共公式库元数据，误删影响面为
     * 消费方按 field_code 执行报 CS_FORMULA_NOT_FOUND，前端对删除须二次确认
     * （列表页通用口径）。
     *
     * @param fieldId 公式 ID
     */
    @Transactional
    public void delete(String fieldId) {
        String id = StringUtil.requireText(fieldId, "fieldId");
        SysFormula exist = requireRow(id);
        baseDao.deleteById(exist.getFieldId());
        log.info("formula deleted: id={}, code={}", exist.getFieldId(), exist.getFieldCode());
    }

    /* ==================== 执行 / 试算侧钩子 ==================== */

    /**
     * 按编码加载启用中的命名公式（供执行 / 试算侧判断，方案 §3.3 / §5.3）：
     * 消费方按 field_code 引用、执行时加载启用中的公式——编码不存在或已停用抛
     * {@code CS_FORMULA_NOT_FOUND}（错误码以异常 message 前缀承载，供
     * {@link SysFormulaSimulateService#simulateByCode} 与 v1.5 / v1.7 服务内消费方
     * 程序化判定），管理列表不受影响（停用行仍可查询与重新启用）。
     *
     * @param fieldCode 公式编码（消费方引用键）
     * @return 启用中的公式行
     */
    public SysFormula requireEnabledFormula(String fieldCode) {
        String code = StringUtil.requireText(fieldCode, "fieldCode");
        SysFormula formula = baseDao.selectByCode(code);
        if (null == formula) {
            throw formulaError(CS_FORMULA_NOT_FOUND, "公式不存在: " + code);
        }
        if (!Boolean.TRUE.equals(formula.getFieldEnable())) {
            throw formulaError(CS_FORMULA_NOT_FOUND, "公式已停用: " + code);
        }
        return formula;
    }

    /* ==================== 组件读取聚合 ==================== */

    /**
     * 可用公式下拉（组件接口 /options 的命名公式节点）：仅暴露启用中的命名公式消费
     * 所需元数据（编码、名称、类型与返回类型，供插入与结果适配预判），<b>不暴露
     * 表达式原文与变量依赖</b>（执行时由消费方按 code 加载，管理面经 /load 读取；
     * 停用行不在下拉暴露，经管理列表识别）。左树「变量」「业务模型」「函数」三组
     * 节点由前端组装（变量组 = 系统变量 + availableVars；业务模型组 = 数据源
     * targets 聚合，方案 A6 不另建字段台账；函数组 = /functions），本接口提供
     * 命名公式条目（SysFormulaOptionVo 契约）。
     *
     * @return 下拉条目（启用中的命名公式，创建时间降序）
     */
    public List<SysFormulaOptionVo> options() {
        SysFormula criteria = new SysFormula();
        criteria.setFieldEnable(Boolean.TRUE);
        List<SysFormulaOptionVo> result = new ArrayList<>();
        for (SysFormula row : baseDao.selectList(criteria, null)) {
            SysFormulaOptionVo vo = new SysFormulaOptionVo();
            vo.setFieldCode(row.getFieldCode());
            vo.setFieldName(row.getFieldName());
            vo.setFieldType(row.getFieldType());
            vo.setFieldReturnType(row.getFieldReturnType());
            result.add(vo);
        }
        return result;
    }

    /**
     * 函数注册中心清单（组件接口 /functions）：注册中心全量函数（内置九类 + SPI 扩展）
     * 导出为 {@link SysFormulaFunctionVo}——同一份元数据驱动前端函数面板 / 插入模板
     * 与后端参数类型校验（方案 §2.2「前后端共用函数定义」）；category / returnType
     * 以枚举名透出，函数名全局唯一。
     *
     * @return 函数元数据列表（按注册顺序）
     */
    public List<SysFormulaFunctionVo> functions() {
        List<SysFormulaFunctionVo> result = new ArrayList<>();
        for (FormulaFunctionDefinition definition : functionRegistry.getAllFunctions()) {
            SysFormulaFunctionVo vo = new SysFormulaFunctionVo();
            vo.setFunctionCode(definition.getFunctionCode());
            vo.setFunctionName(definition.getFunctionName());
            vo.setCategory(null == definition.getCategory() ? null : definition.getCategory().name());
            vo.setDescription(definition.getDescription());
            vo.setParameters(null == definition.getParameters()
                    ? new ArrayList<>() : new ArrayList<>(definition.getParameters()));
            vo.setReturnType(null == definition.getReturnType() ? null : definition.getReturnType().name());
            result.add(vo);
        }
        return result;
    }

    /* ==================== 内部：保存期校验与查重 ==================== */

    /**
     * 基础校验：实体非空，fieldCode / fieldName / fieldType / fieldExpression /
     * fieldReturnType 必填（库列 NOT NULL）
     */
    private static void requireBody(SysFormula body) {
        if (null == body) {
            throw new IllegalArgumentException("公式实体不能为空");
        }
        StringUtil.requireText(body.getFieldCode(), "fieldCode");
        StringUtil.requireText(body.getFieldName(), "fieldName");
        StringUtil.requireText(body.getFieldType(), "fieldType");
        StringUtil.requireText(body.getFieldExpression(), "fieldExpression");
        StringUtil.requireText(body.getFieldReturnType(), "fieldReturnType");
    }

    /**
     * 公式类型枚举校验（SysFormulaType 六值闭合集，仅分类标记不改变执行语义）：
     * 非法值报 {@code CS_FORMULA_TYPE_MISMATCH}（九类错误码中最贴近「类型不符」语义者）
     */
    private static void requireFormulaType(String fieldType) {
        if (null == SysFormulaType.of(fieldType)) {
            throw formulaError(CS_FORMULA_TYPE_MISMATCH, "公式类型不合法: " + fieldType);
        }
    }

    /**
     * 期望返回类型枚举校验：非法值报 {@code CS_FORMULA_TYPE_MISMATCH}
     * （expectedReturnType 与枚举面不符，方案 §5.3）
     */
    private static SysFormulaReturnType requireReturnType(String fieldReturnType) {
        SysFormulaReturnType returnType = SysFormulaReturnType.of(fieldReturnType);
        if (null == returnType) {
            throw formulaError(CS_FORMULA_TYPE_MISMATCH, "期望返回类型不合法: " + fieldReturnType);
        }
        return returnType;
    }

    /**
     * 保存期强制 validate（方案 §5.1「保存前强制 validate 通过」）：宽松模式
     * （命名公式无 vars 声明，变量视为未知——引用失效变量不阻断保存，方案 §3.3），
     * expectedReturnType 取 fieldReturnType；errors 非空即拒绝保存，首错以
     * CS_FORMULA_*: message 异常抛出（与 /validate 接口同源同口径），校验通过时
     * 返回 validate 响应携带的依赖数组（同一提取口径，不再二次解析）
     */
    private List<String> requireValidationPassed(String expression, String returnTypeText) {
        SysFormulaValidateRequest request = new SysFormulaValidateRequest();
        request.setExpression(expression);
        request.setExpectedReturnType(returnTypeText);
        SysFormulaValidateResponse validation = validateService.validate(request);
        if (!validation.getErrors().isEmpty()) {
            SysFormulaErrorDo first = validation.getErrors().get(0);
            throw formulaError(first.getCode(), first.getMessage());
        }
        return validation.getDependencies();
    }

    /**
     * 编码全局查重（uk_formula_code，D4 Service 层查重先例）：DB 同编码行
     * （update 场景排除自身）即抛 {@code CS_FORMULA_CODE_DUPLICATED}
     */
    private void assertCodeAvailable(String fieldCode, String excludeFieldId) {
        String code = StringUtil.requireText(fieldCode, "fieldCode");
        SysFormula exist = baseDao.selectByCode(code);
        if (null != exist && !exist.getFieldId().equals(excludeFieldId)) {
            throw formulaError(CS_FORMULA_CODE_DUPLICATED, "公式编码已存在: " + code);
        }
    }

    private SysFormula requireRow(String fieldId) {
        SysFormula exist = baseDao.selectById(fieldId);
        if (null == exist) {
            throw new IllegalArgumentException("公式不存在: " + fieldId);
        }
        return exist;
    }

    /* ==================== 内部：模型转换与 JSON 承载 ==================== */

    /**
     * 实体 → 响应 Vo：parseStoredJson=true 时 fieldDependencies 解析为变量码字符串
     * 数组（load / save / update 响应），false 时原文 JSON 字符串直出（列表响应，
     * 先例 SysDatasourceVo 双形态）。
     */
    private SysFormulaVo toVo(SysFormula model, boolean parseStoredJson) {
        SysFormulaVo vo = new SysFormulaVo();
        vo.setFieldId(model.getFieldId());
        vo.setFieldCode(model.getFieldCode());
        vo.setFieldName(model.getFieldName());
        vo.setFieldEnable(model.getFieldEnable());
        vo.setFieldCreatorId(model.getFieldCreatorId());
        vo.setFieldCreateTime(model.getFieldCreateTime());
        vo.setFieldUpdaterId(model.getFieldUpdaterId());
        vo.setFieldUpdateTime(model.getFieldUpdateTime());
        vo.setFieldType(model.getFieldType());
        vo.setFieldExpression(model.getFieldExpression());
        if (parseStoredJson) {
            vo.setFieldDependencies(parseDependencies(model.getFieldDependencies()));
        } else {
            vo.setFieldDependencies(model.getFieldDependencies());
        }
        vo.setFieldReturnType(model.getFieldReturnType());
        vo.setFieldRemark(model.getFieldRemark());
        return vo;
    }

    /**
     * 依赖 JSON 解析归一（Object ↔ List&lt;String&gt;）：存储态原文字符串经 readValue、
     * 请求体反序列化产物（List）经 convertValue；NULL=表达式未引用任何变量返回 null；
     * 损坏 JSON 属存储态程序异常（依赖仅由保存期引擎提取落库，非用户直填配置），
     * 抛 IllegalStateException 令事务回滚
     */
    private static List<String> parseDependencies(Object raw) {
        if (null == raw || (raw instanceof String json && StringUtil.isEmpty(json))) {
            return null;
        }
        try {
            if (raw instanceof String json) {
                return JSON_MAPPER.readValue(json, DEPENDENCY_LIST_TYPE);
            }
            return JSON_MAPPER.convertValue(raw, DEPENDENCY_LIST_TYPE);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalStateException("变量依赖 JSON 损坏，无法解析", e);
        }
    }

    /**
     * 依赖 JSON 落库序列化：空依赖统一存 {@code []}（先例
     * SysDatasourceService#toStoreJson——update 通道 &lt;if test="!= null"&gt; 需非 null
     * 才能清除既有值，语义与 NULL「表达式未引用任何变量」等价）；已校验结构的
     * 序列化失败属程序异常，抛 IllegalStateException 令事务回滚
     */
    private static String toStoreJson(List<String> dependencies) {
        try {
            return JSON_MAPPER.writeValueAsString(null == dependencies ? List.of() : dependencies);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("变量依赖 JSON 序列化失败", e);
        }
    }

    /**
     * 查询条件归一：空白转 null（trim），空对象兜底（对齐 SysDatasourceService#normalizeCriteria）
     */
    private static SysFormula normalizeCriteria(SysFormula body) {
        SysFormula criteria = null == body ? new SysFormula() : body;
        criteria.setFieldName(StringUtil.trimToNull(criteria.getFieldName()));
        criteria.setFieldCode(StringUtil.trimToNull(criteria.getFieldCode()));
        criteria.setFieldType(StringUtil.trimToNull(criteria.getFieldType()));
        return criteria;
    }

    /**
     * CS_* 业务错误码异常构造：message 以错误码为前缀（DOC-002 §4.11.6 管理接口口径），
     * 经 GlobalExceptionHandler 既有 BAD_REQUEST 分支返回，前端按前缀识别
     */
    private static IllegalArgumentException formulaError(String code, String message) {
        return new IllegalArgumentException(code + ": " + message);
    }
}
