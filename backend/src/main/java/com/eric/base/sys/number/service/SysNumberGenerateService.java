package com.eric.base.sys.number.service;

import com.eric.base.common.utils.IDUtil;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.number.dao.SysNumberRecordDao;
import com.eric.base.sys.number.dao.SysNumberRecordDetailDao;
import com.eric.base.sys.number.dao.SysNumberRuleDao;
import com.eric.base.sys.number.model.SysNumberAcquireResultDo;
import com.eric.base.sys.number.model.SysNumberRecord;
import com.eric.base.sys.number.model.SysNumberRecordDetail;
import com.eric.base.sys.number.model.SysNumberRule;
import com.eric.base.sys.number.model.SysNumberRuleConfigDo;
import com.eric.base.sys.number.model.SysNumberRuleConfigDo.Item;
import com.eric.base.sys.number.model.SysNumberRuleErrorDo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 编号取号核心服务
 *
 * @author Eric
 * @date 2026/11/13
 * @description 承载编号机制的取号主链路与渲染引擎（任务 NB2-BE-002，方案 §5）：
 *              <ul>
 *              <li>双入口（§5.1）：{@link #acquireByModel} 固定接入——按 {modelName, modelField}
 *                  经 idx_number_rule_model 定位启用规则，作用域 = 规则 field_id；
 *                  {@link #acquireByConfig} 非固定接入（分类等宿主自持规则 JSON）——
 *                  作用域 = {modelName}:{宿主记录ID}，计数行 field_rule_id 空落明细；</li>
 *              <li>计数器原子推进（§5.2）：INSERT ... ON DUPLICATE KEY UPDATE
 *                  field_serial_no = LAST_INSERT_ID(field_serial_no + 1)，首号按 start 初始化，
 *                  uk_number_record_scope 定位行 + InnoDB 行锁持有至事务提交，并发由行锁串行；
 *                  周期切换时旧周期行经 sealOtherPeriods 封存、新周期行自然 INSERT 从 start 起；</li>
 *              <li>幂等（§5.3）：取号前按 (field_scope_key, field_model_id) 查未作废明细，
 *                  命中返回原编号 reused=true 不推进计数器；计数器行锁等待期间并发同单取号
 *                  可能已提交，明细落库前以锁定读（FOR UPDATE，可见最新提交）二次确认，
 *                  命中即抛错回滚本事务（计数器推进一并回滚不跳号），调用方重试即命中 reused；</li>
 *              <li>渲染引擎（R3，供取号与 NB3 preview 预览共用）：text 常量 / datetime 取号时刻
 *                  （服务器时区 A2）/ seq 按 length 补零 / random 每次 重新生成不入计数；</li>
 *              <li>失败语义（§5.3/§5.5）：规则不存在 CRM_NUMBER_RULE_NOT_FOUND、
 *                  无启用规则 CRM_NUMBER_RULE_DISABLED、规则非法 CRM_NUMBER_RULE_INVALID、
 *                  流水用尽 CRM_NUMBER_SERIAL_EXHAUSTED（C6，取号失败不截断续发，
 *                  异常沿事务回滚使计数器推进一并回滚）。</li>
 *              </ul>
 *              取号事务短小（§5.2）：仅计数器 upsert/取回 + 明细 INSERT + 渲染，
 *              传播级别 REQUIRED——同进程业务保存事务内调用时并入业务事务，
 *              业务回滚则取号一并回滚不产生跳号。
 *              <p>已知设计边界（待 NB3-BE-005 reset 任务收口）：reset 封存后重开按 start
 *              起号时，若重开区段与既有明细序号在 uk_number_record_detail_no 内冲突
 *              （方案 §5.4 已声明「重置会造成重号」），明细插入将触发唯一键约束失败，
 *              重置功能的落地口径随 NB3-BE-005 一并处理。</p>
 */
@Service
public class SysNumberGenerateService {

    private static final Logger log = LoggerFactory.getLogger(SysNumberGenerateService.class);

    /** 规则 JSON 解析（固定接入 field_rule 存储态反序列化，禁止手工拼串） */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /** 周期键：NONE 不重置（§5.1 周期键表） */
    private static final String PERIOD_ALL = "ALL";

    /** 周期键格式化器（A2：取号时刻按服务器时区；Locale.ROOT 保证数字非本地化） */
    private static final DateTimeFormatter DAY_PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);

    private static final DateTimeFormatter MONTH_PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT);

    private static final DateTimeFormatter YEAR_PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy", Locale.ROOT);

    /** random 项字符集（numericOnly=false，§2.2：A-Za-z0-9） */
    private static final String RANDOM_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final SysNumberRecordDao recordDao;
    private final SysNumberRecordDetailDao detailDao;
    private final SysNumberRuleDao ruleDao;
    private final SysNumberRuleValidator validator;

    public SysNumberGenerateService(SysNumberRecordDao recordDao,
                                    SysNumberRecordDetailDao detailDao,
                                    SysNumberRuleDao ruleDao,
                                    SysNumberRuleValidator validator) {
        this.recordDao = recordDao;
        this.detailDao = detailDao;
        this.ruleDao = ruleDao;
        this.validator = validator;
    }

    /**
     * 固定接入取号：按 {modelName, modelField} 定位启用规则后取号。
     * <p>失败语义（§5.3）：接入项未配置任何规则抛 {@link NumberRuleNotFoundException}；
     * 有规则但无启用规则抛 {@link NumberRuleDisabledException}（启用唯一性由
     * save/enable 切换机制保证 D1，取号侧不会命中多条）。
     * 传播 REQUIRED：业务保存事务内调用时并入业务事务（§5.2）。</p>
     *
     * @param modelName  业务模型 Java 类 SimpleName（sys_number_rule.field_model_name）
     * @param modelField 承接编号的字段名（sys_number_rule.field_model_field）
     * @param modelId    业务单据 ID（幂等锚点）
     * @return 取号结果 { code, recordDetailId, reused }
     */
    @Transactional
    public SysNumberAcquireResultDo acquireByModel(String modelName, String modelField, String modelId) {
        String modelNameText = StringUtil.requireText(modelName, "modelName");
        String modelFieldText = StringUtil.requireText(modelField, "modelField");
        String modelIdText = StringUtil.requireText(modelId, "modelId");

        List<SysNumberRule> rules = ruleDao.selectByModel(modelNameText, modelFieldText);
        if (null == rules || rules.isEmpty()) {
            throw new NumberRuleNotFoundException(
                    "接入项未配置编号规则：" + modelNameText + "." + modelFieldText);
        }
        SysNumberRule enabled = rules.stream()
                .filter(rule -> Boolean.TRUE.equals(rule.getFieldEnable()))
                .findFirst()
                .orElse(null);
        if (null == enabled) {
            throw new NumberRuleDisabledException(
                    "接入项无启用编号规则：" + modelNameText + "." + modelFieldText + "，请先在编号管理中启用规则");
        }
        SysNumberRuleConfigDo config = parseRuleConfig(enabled.getFieldRule(), enabled.getFieldId());
        // 固定接入作用域 = 规则 field_id（§5.1），计数行与明细回填 field_rule_id
        return doAcquire(config, enabled.getFieldId(), enabled.getFieldId(), modelNameText, modelIdText);
    }

    /**
     * 非固定接入取号（分类等宿主自持规则 JSON，§1.3）：按调用方给定作用域取号。
     * <p>宿主保存分类时已经 {@code SysNumberRuleService#validateConfig}（NB3-BE-002）
     * 完成 C1~C5 校验，本入口仍统一兜底校验保证渲染不进入非法态；
     * 计数行 field_rule_id 空、明细照常落库。作用域键约定 = {modelName}:{宿主记录ID}（§5.1）。</p>
     *
     * @param config   规则配置（SysNumberRuleConfigDo）
     * @param scopeKey 取号作用域键（如 ErpContract:CAT-xxx）
     * @param modelName 业务模型名（落明细 field_model_name）
     * @param modelId   业务单据 ID（幂等锚点，落明细 field_model_id）
     * @return 取号结果 { code, recordDetailId, reused }
     */
    @Transactional
    public SysNumberAcquireResultDo acquireByConfig(SysNumberRuleConfigDo config,
                                                    String scopeKey, String modelName, String modelId) {
        String scopeKeyText = StringUtil.requireText(scopeKey, "scopeKey");
        String modelNameText = StringUtil.requireText(modelName, "modelName");
        String modelIdText = StringUtil.requireText(modelId, "modelId");
        return doAcquire(config, null, scopeKeyText, modelNameText, modelIdText);
    }

    /**
     * 渲染完整编号（取号与预览共用，R3）：按 items 顺序拼接
     * text 常量 / datetime（recordTime 格式化）/ seq（serialNo 补零）/ random（现场生成）。
     * <p>仅供已通过校验的配置调用（取号入口先行 assertValid，NB3 preview 亦先 validate）；
     * 非法态抛 {@link NumberRuleInvalidException}。</p>
     *
     * @param config     规则配置
     * @param serialNo   流水号取值（预览场景传顺延模拟值）
     * @param recordTime 渲染时刻（datetime 项与取号时间语义对齐，服务器时区）
     * @return 完整编号
     */
    public String render(SysNumberRuleConfigDo config, int serialNo, Date recordTime) {
        if (null == config || null == config.getItems()) {
            throw new NumberRuleInvalidException(List.of(new SysNumberRuleErrorDo(
                    SysNumberRuleValidator.CODE_C1, null, "编号规则不能为空")));
        }
        LocalDateTime time = toLocalDateTime(recordTime);
        StringBuilder builder = new StringBuilder();
        for (Item item : config.getItems()) {
            if (null == item || null == item.getType()) {
                throw new NumberRuleInvalidException(List.of(new SysNumberRuleErrorDo(
                        SysNumberRuleValidator.CODE_C0, null, "规则项缺失或类型为空")));
            }
            switch (item.getType()) {
                case SysNumberRuleConfigDo.TYPE_TEXT -> {
                    if (StringUtil.isEmpty(item.getText())) {
                        throw new NumberRuleInvalidException(List.of(new SysNumberRuleErrorDo(
                                SysNumberRuleValidator.CODE_C0, item.getId(), "文本项内容不能为空")));
                    }
                    builder.append(item.getText());
                }
                case SysNumberRuleConfigDo.TYPE_DATETIME -> builder.append(formatDate(item.getFormat(), time));
                case SysNumberRuleConfigDo.TYPE_SEQ -> builder.append(renderSeq(item, serialNo));
                case SysNumberRuleConfigDo.TYPE_RANDOM -> builder.append(renderRandom(item));
                default -> throw new NumberRuleInvalidException(List.of(new SysNumberRuleErrorDo(
                        SysNumberRuleValidator.CODE_C0, item.getId(), "未知规则项类型：" + item.getType())));
            }
        }
        return builder.toString();
    }

    /**
     * seq 项容量（C6）：length 位可表达的最大流水号 10^length - 1（length 1..9，int 不溢出）。
     *
     * @param seqItem seq 规则项
     * @return 容量上限；length 缺失时返回 0
     */
    public int seqCapacity(Item seqItem) {
        if (null == seqItem || null == seqItem.getLength() || seqItem.getLength() < 1) {
            return 0;
        }
        int capacity = 1;
        for (int i = 0; i < seqItem.getLength(); i++) {
            capacity *= 10;
        }
        return capacity - 1;
    }

    /**
     * 周期键派生（§5.1）：NONE=ALL、DAY=yyyyMMdd、WEEK=yyyy'W'ww（ISO-8601 周年 A2）、
     * MONTH=yyyyMM、YEAR=yyyy；取号时刻按服务器时区。
     * <p>WEEK 用 {@link IsoFields#WEEK_BASED_YEAR} 显式 ISO 周字段（含第一个周四的周为第 1 周，
     * 跨年周归属正确；DateTimeFormatter 周字段随 Locale 变化，不采用）。</p>
     *
     * @param resetPeriod seq 项重置周期
     * @param recordTime  取号时刻
     * @return 周期键（未知/缺省按 NONE=ALL 处理）
     */
    public String derivePeriodKey(String resetPeriod, Date recordTime) {
        if (SysNumberRuleConfigDo.RESET_DAY.equals(resetPeriod)) {
            return toLocalDateTime(recordTime).format(DAY_PERIOD_FORMATTER);
        }
        if (SysNumberRuleConfigDo.RESET_WEEK.equals(resetPeriod)) {
            return weekPeriodKey(toLocalDateTime(recordTime).toLocalDate());
        }
        if (SysNumberRuleConfigDo.RESET_MONTH.equals(resetPeriod)) {
            return toLocalDateTime(recordTime).format(MONTH_PERIOD_FORMATTER);
        }
        if (SysNumberRuleConfigDo.RESET_YEAR.equals(resetPeriod)) {
            return toLocalDateTime(recordTime).format(YEAR_PERIOD_FORMATTER);
        }
        return PERIOD_ALL;
    }

    /**
     * WEEK 周期键：ISO-8601 周年编号 yyyy'W'ww（如 2026W46）
     */
    private static String weekPeriodKey(LocalDate date) {
        return String.format(Locale.ROOT, "%04dW%02d",
                date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /**
     * 取号主链路（§5.2/§5.3）：防御校验 → 幂等预检 → 周期切换封存 → 计数器原子推进 →
     * 取回流水号 → C6 容量校验 → 幂等二次确认（锁定读）→ 渲染 → C5 兜底 → 明细落库。
     * <p>调用方须在事务内（两个公共入口均已 @Transactional REQUIRED）；
     * 任何失败沿异常回滚，计数器推进与明细落库一并撤销，不产生跳号。</p>
     */
    private SysNumberAcquireResultDo doAcquire(SysNumberRuleConfigDo config, String fieldRuleId,
                                               String scopeKey, String modelName, String modelId) {
        // 一、防御校验（C1~C5，§5.3 INVALID 语义）：固定接入保存时已校验、
        // 非固定接入由宿主保存时校验，此处统一兜底保证渲染不进入非法态
        validator.assertValid(config);
        Item seq = config.getItems().stream()
                .filter(item -> SysNumberRuleConfigDo.TYPE_SEQ.equals(item.getType()))
                .findFirst()
                .orElseThrow(() -> new NumberRuleInvalidException(List.of(new SysNumberRuleErrorDo(
                        SysNumberRuleValidator.CODE_C1, null, "缺少流水号（seq）项：唯一性锚点必须恰好一个"))));

        // 二、周期键派生（§5.1）
        Date now = new Date();
        String period = derivePeriodKey(seq.getResetPeriod(), now);

        // 三、幂等预检（§5.3）：命中未作废明细直接返回原编号，不推进计数器（普通读，不加锁防死锁）
        SysNumberRecordDetail exist = detailDao.selectValidByScopeAndModel(scopeKey, modelId);
        if (null != exist) {
            log.info("number idempotent hit: scope={}, model={}/{}, code={}", scopeKey, modelName, modelId, exist.getFieldCode());
            return reusedResult(exist);
        }

        // 四、周期切换封存（§5.2）：同周期取号时无匹配行（0 行更新，幂等）；
        // 不变式：同 (scope, serialId) 至多一行使用中
        recordDao.sealOtherPeriods(scopeKey, seq.getId(), period);

        // 五、计数器原子推进（§5.2）：ODKU + LAST_INSERT_ID，行锁持有至事务提交
        SysNumberRecord counter = new SysNumberRecord();
        counter.initFieldId();
        counter.setFieldRuleId(fieldRuleId);
        counter.setFieldScopeKey(scopeKey);
        counter.setFieldSerialId(seq.getId());
        counter.setFieldPeriod(period);
        counter.setFieldSerialNo(seq.getStart());
        recordDao.upsertSerial(counter);

        // 六、取回推进后流水号（同事务、行锁保证读到本事务写入的最新值）
        Integer serialNo = recordDao.selectCurrentSerialNo(scopeKey, seq.getId(), period);

        // 七、容量校验（C6）：超出即抛错回滚，第五步推进一并回滚，计数器不永久越过容量
        int capacity = seqCapacity(seq);
        if (null == serialNo || serialNo > capacity) {
            throw NumberSerialExhaustedException.of(seq, null == serialNo ? -1 : serialNo, capacity);
        }

        // 八、幂等二次确认：第五步行锁等待期间并发同单取号可能已提交——
        // 锁定读（FOR UPDATE，当前读可见最新提交，不受 RR 快照影响）命中即回滚本事务
        SysNumberRecordDetail concurrent = detailDao.lockValidByScopeAndModel(scopeKey, modelId);
        if (null != concurrent) {
            throw new IllegalStateException("并发取号冲突：单据已取得编号 " + concurrent.getFieldCode()
                    + "，本次取号已回滚，请重试");
        }

        // 九、渲染编号（取号与预览共用渲染引擎 R3）
        String code = render(config, serialNo, now);

        // 十、组装后二次兜底校验（R4）：C5 估算偏松场景（如 random max 超 10^length-1）在此拦截
        if (code.length() > SysNumberRuleConfigDo.MAX_CODE_LENGTH) {
            throw new NumberRuleInvalidException(List.of(new SysNumberRuleErrorDo(
                    SysNumberRuleValidator.CODE_C5, seq.getId(),
                    "实际拼接长度 " + code.length() + " 超过上限 " + SysNumberRuleConfigDo.MAX_CODE_LENGTH)));
        }

        // 十一、明细落库（uk_number_record_detail_no 第二重唯一约束兜底 R1）
        SysNumberRecordDetail detail = new SysNumberRecordDetail();
        detail.initFieldId();
        detail.setFieldScopeKey(scopeKey);
        detail.setFieldPeriod(period);
        detail.setFieldSerialId(seq.getId());
        detail.setFieldSerialNo(serialNo);
        detail.setFieldModelName(modelName);
        detail.setFieldModelId(modelId);
        detail.setFieldCode(code);
        detail.setFieldRecordTime(now);
        detail.setFieldCreatorId(UserUtil.getCurrentUserId());
        detailDao.insert(detail);

        SysNumberAcquireResultDo result = new SysNumberAcquireResultDo();
        result.setCode(code);
        result.setRecordDetailId(detail.getFieldId());
        result.setReused(false);
        log.info("number acquired: scope={}, model={}/{}, serial={}, ruleId={}, code={}",
                scopeKey, modelName, modelId, serialNo, fieldRuleId, code);
        return result;
    }

    /**
     * 固定接入规则 JSON 解析（存储态原文字符串 → SysNumberRuleConfigDo）。
     * 解析失败按 CRM_NUMBER_RULE_INVALID 语义抛出（库内数据由 save 侧 Jackson 写入，
     * 非法即数据异常）。
     */
    private SysNumberRuleConfigDo parseRuleConfig(Object fieldRule, String ruleId) {
        if (!(fieldRule instanceof String json) || StringUtil.isEmpty(json)) {
            throw new NumberRuleInvalidException(List.of(new SysNumberRuleErrorDo(
                    SysNumberRuleValidator.CODE_C1, null, "编号规则 JSON 缺失：规则 " + ruleId)));
        }
        try {
            return JSON_MAPPER.readValue(json, SysNumberRuleConfigDo.class);
        } catch (JsonProcessingException e) {
            throw new NumberRuleInvalidException(List.of(new SysNumberRuleErrorDo(
                    SysNumberRuleValidator.CODE_C1, null, "编号规则 JSON 解析失败：规则 " + ruleId)), e);
        }
    }

    /**
     * seq 渲染：padZero 时按 length 左补零，否则原值输出（超容量已在取号侧 C6 拦截，不截断）
     */
    private String renderSeq(Item item, int serialNo) {
        int width = item.getLength() == null ? 0 : item.getLength();
        if (width > 0 && Boolean.TRUE.equals(item.getPadZero())) {
            return String.format(Locale.ROOT, "%0" + width + "d", serialNo);
        }
        return String.valueOf(serialNo);
    }

    /**
     * random 渲染（§2.2）：每次调用重新生成、不入计数（C2）。
     * numericOnly=true 取 [min, max] 整数（padZero 按 length 补零）；
     * false 取 length 位 A-Za-z0-9 随机串。
     */
    private String renderRandom(Item item) {
        int width = item.getLength() == null ? 0 : item.getLength();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (Boolean.TRUE.equals(item.getNumericOnly())) {
            int min = item.getMin() == null ? 0 : item.getMin();
            int max = item.getMax() == null ? 0 : item.getMax();
            // nextLong + (long) max + 1：max 取 Integer.MAX_VALUE 时避免 nextInt 上界 +1 溢出
            long value = random.nextLong(min, (long) max + 1);
            return width > 0 && Boolean.TRUE.equals(item.getPadZero())
                    ? String.format(Locale.ROOT, "%0" + width + "d", value)
                    : String.valueOf(value);
        }
        StringBuilder builder = new StringBuilder(width);
        for (int i = 0; i < width; i++) {
            builder.append(RANDOM_ALPHABET.charAt(random.nextInt(RANDOM_ALPHABET.length())));
        }
        return builder.toString();
    }

    /**
     * datetime 渲染：校验组件已保证格式合法，此处兜底渲染失败按 C0 语义抛出
     */
    private static String formatDate(String format, LocalDateTime time) {
        try {
            return time.format(DateTimeFormatter.ofPattern(format, Locale.ROOT));
        } catch (RuntimeException e) {
            throw new NumberRuleInvalidException(List.of(new SysNumberRuleErrorDo(
                    SysNumberRuleValidator.CODE_C0, null, "日期项格式渲染失败：" + format)));
        }
    }

    private static LocalDateTime toLocalDateTime(Date recordTime) {
        Instant instant = null == recordTime ? Instant.now() : recordTime.toInstant();
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private static SysNumberAcquireResultDo reusedResult(SysNumberRecordDetail detail) {
        SysNumberAcquireResultDo result = new SysNumberAcquireResultDo();
        result.setCode(detail.getFieldCode());
        result.setRecordDetailId(detail.getFieldId());
        result.setReused(true);
        return result;
    }
}
