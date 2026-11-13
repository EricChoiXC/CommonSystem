package com.eric.base.sys.number.service;

import com.eric.base.common.base.model.Pages;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.log.enums.SysLogResult;
import com.eric.base.sys.log.enums.SysLogType;
import com.eric.base.sys.log.service.LogRecord;
import com.eric.base.sys.log.service.LogService;
import com.eric.base.sys.number.dao.SysNumberRecordDao;
import com.eric.base.sys.number.dao.SysNumberRecordDetailDao;
import com.eric.base.sys.number.dao.SysNumberRuleDao;
import com.eric.base.sys.number.model.SysNumberAcquireResultDo;
import com.eric.base.sys.number.model.SysNumberRecordDetail;
import com.eric.base.sys.number.model.SysNumberRule;
import com.eric.base.sys.number.request.SysNumberManageQuery;
import com.eric.base.sys.number.request.SysNumberResetRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.eric.base.sys.number.properties.SysNumberConstants.ACTION_MANAGE_RESET;
import static com.eric.base.sys.number.properties.SysNumberConstants.ACTION_MANAGE_REISSUE;
import static com.eric.base.sys.number.properties.SysNumberConstants.ACTION_MANAGE_VOID;

/**
 * 编号取号明细管理与特权操作服务
 *
 * @author Eric
 * @date 2026/11/13
 * @description 承载编号机制特权管理面（任务 NB3-BE-005，方案 §3.2 manage 组、§4.3、§5.4）：
 *              <ul>
 *              <li>recordList / count：取号明细筛选查询与总数（总数供响应 metadata.total，
 *                  条件形态对齐 {@link SysNumberRuleService}#list / count 先例）——
 *                  fieldScopeKey / fieldPeriod 精确、voidFlag 三态（true 仅已作废 /
 *                  false 仅未作废 / null 不过滤）、recordTimeBegin / recordTimeEnd 取号时间
 *                  范围（含边界），排序固定取号时间降序；fieldRuleId 翻译为作用域键查询
 *                  （明细表无 field_rule_id 列，固定接入作用域即规则 field_id，方案 §5.1），
 *                  与 fieldScopeKey 同时传入以 fieldRuleId 优先；</li>
 *              <li>reset：重置计数器（§5.4）——将作用域（可限定流水项 fieldSerialId /
 *                  周期 fieldPeriod，可空 = 全部）使用中计数行经 sealByScope 封存，
 *                  下次取号经取号侧封存行分支按 start 重开；<b>会造成重号</b>（§5.4 预期语义，
 *                  既有明细不删除不改动、审计保留，uk_number_record_detail_no 已由
 *                  V1_01_045 放宽为含 field_record_time 不再阻断重开重发）；
 *                  0 行语义：有 fieldSerialId / fieldPeriod 限定时 0 行可能 = 本就无该行，
 *                  静默返回 sealed=0（幂等）；无任何限定且 0 行 = 该作用域从未取号 /
 *                  无使用中计数行，抛 IllegalStateException；</li>
 *              <li>voidDetail：作废明细（§5.4 / D8 唯一例外更新）——仅置 field_void_time /
 *                  field_void_reason 两字段，不回退计数器（避免重号）；已作废明细重复作废
 *                  幂等返回原状态，不重复更新；</li>
 *              <li>reissue：补号（§5.4）——对已作废明细对应单据经
 *                  {@link SysNumberGenerateService#acquireByModel} 按计数器顺延取新号，
 *                  响应同 generate（{ code, recordDetailId, reused }），旧号保留作废记录
 *                  供审计；仅支持固定接入明细（作用域命中编号规则，宿主持有规则 JSON 的
 *                  非固定接入明细不支持）；</li>
 *              <li>操作日志（§4.3 特权操作审计联动）：reset / void / reissue 三操作照
 *                  {@link com.eric.base.common.module.service.CommonModuleService}#recordEnableLog
 *                  先例经 {@link LogService} / {@link LogRecord} 记录——成功记 SUCCESS
 *                  （关键参数与结果写入 after），失败记 FAILED 及失败原因后原样抛出；
 *                  日志记录失败仅记 WARN，绝不影响主流程。</li>
 *              </ul>
 */
@Service
public class SysNumberManageService {

    private static final Logger log = LoggerFactory.getLogger(SysNumberManageService.class);

    /** 操作日志 module 取值（sys_log_operation.field_module，对齐 application.yml MODEL_KEY） */
    private static final String LOG_MODULE = "SYS-NUMBER";

    /** 日志变更 JSON 序列化（非固定结构小对象，Jackson 序列化禁止手工拼串） */
    private static final ObjectMapper LOG_JSON_MAPPER = new ObjectMapper();

    private final SysNumberRuleDao ruleDao;
    private final SysNumberRecordDao recordDao;
    private final SysNumberRecordDetailDao detailDao;
    private final SysNumberGenerateService generateService;
    private final LogService logService;

    public SysNumberManageService(SysNumberRuleDao ruleDao,
                                  SysNumberRecordDao recordDao,
                                  SysNumberRecordDetailDao detailDao,
                                  SysNumberGenerateService generateService,
                                  LogService logService) {
        this.ruleDao = ruleDao;
        this.recordDao = recordDao;
        this.detailDao = detailDao;
        this.generateService = generateService;
        this.logService = logService;
    }

    /**
     * 取号明细筛选查询（方案 §3.2 manage/recordList）：条件见类头说明，
     * 排序固定取号时间降序。
     *
     * @param query 查询条件（可空，空字段忽略；fieldRuleId 优先于 fieldScopeKey）
     * @param pages 分页参数（可空，needPage=false 返回全部）
     * @return 取号明细列表
     */
    public List<SysNumberRecordDetail> recordList(SysNumberManageQuery query, Pages pages) {
        return detailDao.selectQueryList(normalizeQuery(query), normalizePages(pages));
    }

    /**
     * 取号明细筛选总数：查询条件与 {@link #recordList} 完全一致，供响应 metadata.total 使用。
     *
     * @param query 查询条件（可空，空字段忽略）
     * @return 匹配总数
     */
    public long count(SysNumberManageQuery query) {
        return detailDao.countQueryList(normalizeQuery(query));
    }

    /**
     * 重置计数器（方案 §5.4 reset）：封存作用域使用中计数行，下次取号按 start 重开。
     * <p>作用域定位：fieldRuleId 有值 → 校验规则存在（不存在抛
     * {@link NumberRuleNotFoundException}）且作用域 = 规则 field_id；否则用 fieldScopeKey
     * （两者都空抛 IllegalArgumentException）；两者都传以 fieldRuleId 优先（与 recordList
     * 翻译口径一致）。fieldSerialId / fieldPeriod 可空 = 全部。</p>
     * <p><b>会造成重号</b>（§5.4 预期语义）：既有明细不删除不改动（审计保留），重开按 start
     * 重发同号，uk_number_record_detail_no 已由 V1_01_045 放宽为含 field_record_time。
     * <b>0 行语义</b>：有 fieldSerialId / fieldPeriod 限定时 0 行可能 = 本就无该使用中
     * 计数行，按幂等静默返回 sealed=0；无任何限定且 0 行 = 该作用域无任何使用中计数行，
     * 抛 IllegalStateException（对齐「影响行数不符」先例）。</p>
     *
     * @param request 重置请求（作用域二选一，可限定流水项 / 周期）
     * @return 封存行数（0 或 1）
     */
    @Transactional
    public Integer reset(SysNumberResetRequest request) {
        if (null == request) {
            throw new IllegalArgumentException("重置请求不能为空：fieldRuleId 与 fieldScopeKey 至少传一个");
        }
        String ruleId = StringUtil.trimToNull(request.getFieldRuleId());
        String scopeKeyInput = StringUtil.trimToNull(request.getFieldScopeKey());
        String serialId = StringUtil.trimToNull(request.getFieldSerialId());
        String period = StringUtil.trimToNull(request.getFieldPeriod());
        // 作用域定位：fieldRuleId 优先（固定接入，校验规则存在），否则用 fieldScopeKey 原样作作用域
        String scopeKey;
        if (StringUtil.isNotEmpty(ruleId)) {
            SysNumberRule rule = ruleDao.selectById(ruleId);
            if (null == rule) {
                throw new NumberRuleNotFoundException("编号规则不存在: " + ruleId);
            }
            scopeKey = ruleId;
        } else if (StringUtil.isNotEmpty(scopeKeyInput)) {
            scopeKey = scopeKeyInput;
        } else {
            throw new IllegalArgumentException("重置作用域不能为空：fieldRuleId 与 fieldScopeKey 至少传一个");
        }
        boolean limited = StringUtil.isNotEmpty(serialId) || StringUtil.isNotEmpty(period);
        try {
            int sealed = recordDao.sealByScope(scopeKey, serialId, period);
            if (0 == sealed && !limited) {
                throw new IllegalStateException("无可重置的使用中计数行: " + scopeKey);
            }
            Map<String, Object> after = resetAfterParams(ruleId, scopeKey, serialId, period, sealed);
            recordManageLog(ACTION_MANAGE_RESET, scopeKey, null, toJson(after),
                    "重置计数器：封存后下次取号按 start 重开（会造成重号）",
                    SysLogResult.SUCCESS.getValue(), null);
            log.info("number record reset: scope={}, ruleId={}, serialId={}, period={}, sealed={}",
                    scopeKey, ruleId, serialId, period, sealed);
            return sealed;
        } catch (RuntimeException e) {
            recordManageLog(ACTION_MANAGE_RESET, scopeKey, null,
                    toJson(resetAfterParams(ruleId, scopeKey, serialId, period, null)),
                    "重置计数器：封存后下次取号按 start 重开（会造成重号）",
                    SysLogResult.FAILED.getValue(), e.getMessage());
            throw e;
        }
    }

    /**
     * 作废取号明细（方案 §5.4 void / D8 唯一例外更新）：仅置 field_void_time /
     * field_void_reason 两字段，不回退计数器（避免重号），不改任何业务字段。
     * <p>已作废明细（field_void_time 非空）重复作废<b>幂等返回原状态</b>，不重复更新；
     * 更新影响行数 ≠ 1 抛 IllegalStateException（对齐 updateEnable / update 先例，
     * WHERE 含 field_void_time IS NULL 守卫）。</p>
     *
     * @param fieldId         取号明细 ID（必填）
     * @param fieldVoidReason 作废原因（可空）
     * @return 作废后明细状态（幂等场景为原状态）
     */
    @Transactional
    public SysNumberRecordDetail voidDetail(String fieldId, String fieldVoidReason) {
        String id = StringUtil.requireText(fieldId, "fieldId");
        SysNumberRecordDetail exist = detailDao.selectById(id);
        if (null == exist) {
            throw new IllegalArgumentException("取号明细不存在: " + id);
        }
        try {
            SysNumberRecordDetail result;
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = new HashMap<>();
            if (null != exist.getFieldVoidTime()) {
                // D8 幂等：已作废明细重复作废返回原状态，不重复更新
                result = exist;
                before.put("voided", Boolean.TRUE);
                after.put("voided", Boolean.TRUE);
                after.put("fieldVoidReason", exist.getFieldVoidReason());
                log.info("number record void idempotent hit: id={}, code={}", id, exist.getFieldCode());
            } else {
                Date voidTime = new Date();
                String reason = StringUtil.trimToNull(fieldVoidReason);
                int affected = detailDao.updateVoid(id, voidTime, reason);
                if (affected != 1) {
                    throw new IllegalStateException("void expected one affected row but got " + affected);
                }
                result = detailDao.selectById(id);
                before.put("voided", Boolean.FALSE);
                after.put("voided", Boolean.TRUE);
                after.put("fieldVoidReason", reason);
                log.info("number record voided: id={}, code={}", id, exist.getFieldCode());
            }
            recordManageLog(ACTION_MANAGE_VOID, id, toJson(before), toJson(after),
                    "作废取号明细：编号不再可用，不回退计数器",
                    SysLogResult.SUCCESS.getValue(), null);
            return result;
        } catch (RuntimeException e) {
            recordManageLog(ACTION_MANAGE_VOID, id, null, null,
                    "作废取号明细：编号不再可用，不回退计数器",
                    SysLogResult.FAILED.getValue(), e.getMessage());
            throw e;
        }
    }

    /**
     * 补号（方案 §5.4 reissue）：对已作废明细对应单据按计数器顺延重新取号，
     * 旧号保留作废记录供审计。
     * <p>前置：明细必须已作废（未作废抛 IllegalArgumentException）；
     * 固定接入明细按作用域命中规则（{@code ruleDao.selectById(detail.fieldScopeKey)}），
     * 未命中 = 非固定接入明细（宿主持有规则 JSON，本模块无从取规则配置），抛
     * IllegalArgumentException；命中后委托
     * {@link SysNumberGenerateService#acquireByModel} 为原 field_model_id 顺延取新号，
     * 响应同 generate（{ code, recordDetailId, reused }）。原单据归属校验由业务模块在
     * 调用前自行完成（D7 同口径）。</p>
     *
     * @param fieldId 原取号明细 ID（必填，须已作废）
     * @return 取号结果 { code, recordDetailId, reused }
     */
    @Transactional
    public SysNumberAcquireResultDo reissue(String fieldId) {
        String id = StringUtil.requireText(fieldId, "fieldId");
        SysNumberRecordDetail detail = detailDao.selectById(id);
        if (null == detail) {
            throw new IllegalArgumentException("取号明细不存在: " + id);
        }
        if (null == detail.getFieldVoidTime()) {
            throw new IllegalArgumentException("取号明细未作废，不允许补号（仅已作废明细可补号）: " + id);
        }
        // 固定接入明细：作用域 = 规则 field_id；未命中规则 = 非固定接入（宿主持有规则 JSON），不支持 reissue
        SysNumberRule rule = ruleDao.selectById(detail.getFieldScopeKey());
        if (null == rule) {
            throw new IllegalArgumentException("非固定接入明细不支持补号（作用域未命中编号规则，宿主持有规则 JSON）: "
                    + detail.getFieldScopeKey());
        }
        try {
            SysNumberAcquireResultDo result = generateService.acquireByModel(
                    rule.getFieldModelName(), rule.getFieldModelField(), detail.getFieldModelId());
            Map<String, Object> after = new HashMap<>();
            after.put("originalDetailId", id);
            after.put("modelId", detail.getFieldModelId());
            after.put("scopeKey", detail.getFieldScopeKey());
            if (null != result) {
                after.put("code", result.getCode());
                after.put("recordDetailId", result.getRecordDetailId());
                after.put("reused", result.isReused());
            }
            recordManageLog(ACTION_MANAGE_REISSUE, id, null, toJson(after),
                    "补号：按计数器顺延取新号，旧号保留作废记录供审计",
                    SysLogResult.SUCCESS.getValue(), null);
            log.info("number record reissued: id={}, scope={}, modelId={}, newCode={}",
                    id, detail.getFieldScopeKey(), detail.getFieldModelId(),
                    null == result ? null : result.getCode());
            return result;
        } catch (RuntimeException e) {
            recordManageLog(ACTION_MANAGE_REISSUE, id, null, null,
                    "补号：按计数器顺延取新号，旧号保留作废记录供审计",
                    SysLogResult.FAILED.getValue(), e.getMessage());
            throw e;
        }
    }

    /* ==================== 内部 ==================== */

    /**
     * 查询条件归一（对齐 SysNumberRuleService#normalizeQuery）：空白转 null、空对象兜底；
     * fieldRuleId 翻译为作用域键（明细表无 field_rule_id 列，固定接入作用域 = 规则 field_id），
     * 与 fieldScopeKey 同时传入以 fieldRuleId 优先。
     */
    private static SysNumberManageQuery normalizeQuery(SysNumberManageQuery query) {
        SysNumberManageQuery safe = null == query ? new SysNumberManageQuery() : query;
        safe.setFieldScopeKey(StringUtil.trimToNull(safe.getFieldScopeKey()));
        safe.setFieldRuleId(StringUtil.trimToNull(safe.getFieldRuleId()));
        safe.setFieldPeriod(StringUtil.trimToNull(safe.getFieldPeriod()));
        if (StringUtil.isNotEmpty(safe.getFieldRuleId())) {
            safe.setFieldScopeKey(safe.getFieldRuleId());
        }
        return safe;
    }

    /**
     * 分页参数归一（对齐 SysNumberRuleService#normalizePages）：页码 / 页大小小于 1 修正
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

    /**
     * reset 操作日志 after 参数（请求关键参数 + 封存行数；失败场景 sealedCount 未知置 null）
     */
    private static Map<String, Object> resetAfterParams(String ruleId, String scopeKey,
                                                        String serialId, String period, Integer sealed) {
        Map<String, Object> after = new HashMap<>();
        after.put("fieldRuleId", ruleId);
        after.put("fieldScopeKey", scopeKey);
        after.put("fieldSerialId", serialId);
        after.put("fieldPeriod", period);
        after.put("sealedCount", sealed);
        return after;
    }

    /**
     * 记录特权操作日志（照 CommonModuleService#recordEnableLog 先例）：logType 用操作日志，
     * module 取 SYS-NUMBER，action 用 {@link SysNumberConstants} 的 ACTION_MANAGE_* 常量，
     * businessId 为被操作对象（明细 ID 或作用域键），before / after 记录关键参数与结果。
     * <p>日志记录失败仅记 WARN（{@code LogService.record} 对非法参数抛
     * {@link IllegalArgumentException}），绝不影响主流程。</p>
     */
    private void recordManageLog(String action, String businessId, String before, String after,
                                 String remark, String result, String failReason) {
        try {
            LogRecord record = new LogRecord();
            record.setLogType(SysLogType.OPERATION.getValue());
            record.setModule(LOG_MODULE);
            record.setAction(action);
            record.setBusinessId(businessId);
            record.setBefore(before);
            record.setAfter(after);
            record.setRemark(remark);
            record.setResult(result);
            record.setFailReason(failReason);
            logService.record(record);
        } catch (Exception e) {
            log.warn("failed to record number manage log, action={}, businessId={}", action, businessId, e);
        }
    }

    /**
     * 日志变更 JSON 序列化（HashMap 容忍 null 值；序列化失败回退 toString，绝不在主流程抛出）
     */
    private static String toJson(Map<String, Object> data) {
        if (null == data) {
            return null;
        }
        try {
            return LOG_JSON_MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            return String.valueOf(data);
        }
    }
}
