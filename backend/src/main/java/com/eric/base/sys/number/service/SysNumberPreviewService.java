package com.eric.base.sys.number.service;

import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.number.dao.SysNumberRecordDao;
import com.eric.base.sys.number.model.SysNumberPreviewVo;
import com.eric.base.sys.number.model.SysNumberRule;
import com.eric.base.sys.number.model.SysNumberRuleConfigDo;
import com.eric.base.sys.number.model.SysNumberRuleConfigDo.Item;
import com.eric.base.sys.number.model.SysNumberRuleErrorDo;
import com.eric.base.sys.number.request.SysNumberPreviewRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 编号预览渲染服务
 *
 * @author Eric
 * @date 2026/11/13
 * @description 承载编号预览的顺延模拟（任务 NB3-BE-004，方案 §3.2 preview/render、§5.1~§5.3）：
 *              <ul>
 *              <li>双入口（二选一，同时传入以 fieldId 优先）：已保存规则按 fieldId 经
 *                  {@link SysNumberRuleService#load} 读取（复用其解析回填与 NOT_FOUND 语义），
 *                  作用域 = 规则 field_id；未保存 JSON（{@code rule}）直渲，无作用域、
 *                  计数视同不存在（样例从 start 起）；</li>
 *              <li>校验就地返回不抛异常：C1~C5 校验经 {@link SysNumberRuleValidator#validate}
 *                  收集错误，校验不过返回 samples=[] + errors 供前端红字提示——这是预览与
 *                  save / enable 硬失败（assertValid 抛 {@link NumberRuleInvalidException}）
 *                  的差异；</li>
 *              <li>顺延模拟（R3 与取号侧口径完全一致）：当前周期键 = 
 *                  {@link SysNumberGenerateService#derivePeriodKey}，当前已用流水 =
 *                  {@link SysNumberRecordDao#selectCurrentSerialNo}（对「无行 / 封存行」返回
 *                  null——封存行周期键与当前周期不同不会命中，与取号侧「新周期 INSERT 从
 *                  start 起」一致），null 视为 start-1；serialNo(i) = max(current, start-1) + i
 *                  （i=1..count），逐个经 {@link SysNumberGenerateService#render} 生成样例；</li>
 *              <li>C6 越界提示（C6 语义）：max(current, start-1) + count 超过
 *                  {@link SysNumberGenerateService#seqCapacity} 时 errors 追加 C6 错误
 *                  （正式取号将报 CRM_NUMBER_SERIAL_EXHAUSTED），样例只渲染到容量内的部分；</li>
 *              <li>只读不落库：除 {@code selectCurrentSerialNo} 这一个读调用外不做任何库操作，
 *                  不复用会写库的 acquire 链路，重复调用同输入结果一致。</li>
 *              </ul>
 *              <p>已知边界（随 NB3-BE-005 reset 收口复核）：人工重置封存后同周期残留行
 *              （field_record_finish_time 非空且周期键同当前）场景本服务只经
 *              selectCurrentSerialNo 取值、无法感知封存标记，该场景预览口径随 reset
 *              落地一并处理，本任务不涉及 reset 相关内容。</p>
 */
@Service
public class SysNumberPreviewService {

    private static final Logger log = LoggerFactory.getLogger(SysNumberPreviewService.class);

    /** 错误码：当前周期流水临近耗尽的越界提示（C6 语义，非校验组件规则号） */
    private static final String CODE_C6 = "C6";

    /** 样例数量缺省值（§3.2 count 默认 3） */
    private static final int DEFAULT_SAMPLE_COUNT = 3;

    /** 样例数量上限（§3.2 count 上限 10） */
    private static final int MAX_SAMPLE_COUNT = 10;

    private final SysNumberRuleService ruleService;

    private final SysNumberRecordDao recordDao;

    private final SysNumberGenerateService generateService;

    private final SysNumberRuleValidator validator;

    public SysNumberPreviewService(SysNumberRuleService ruleService,
                                   SysNumberRecordDao recordDao,
                                   SysNumberGenerateService generateService,
                                   SysNumberRuleValidator validator) {
        this.ruleService = ruleService;
        this.recordDao = recordDao;
        this.generateService = generateService;
        this.validator = validator;
    }

    /**
     * 预览渲染：基于当前计数器顺延模拟，绝不写库、不推进计数器、不落任何记录。
     * <p>入参二选一（同时传入 fieldId 优先）：fieldId 走已保存规则（load 拿实体与解析后
     * config，作用域 = 规则 field_id）；rule 走未保存 JSON 直渲（无作用域，计数视同不存在，
     * 样例从 start 起）；两者都空抛 {@link IllegalArgumentException}（命中全局
     * BAD_REQUEST 分支）。C1~C5 校验不过时<b>不抛异常</b>，返回 samples=[] + errors
     * 就地供前端红字提示（与 save/enable 硬失败的差异）；校验通过才模拟，逐样例
     * 经取号侧共用渲染引擎（R3）生成，count 归一：缺省 3、上限 10、小于 1 归一为 1。</p>
     *
     * @param request 预览请求（fieldId / rule 二选一，count 可空）
     * @return 预览结果 { samples, errors }
     */
    public SysNumberPreviewVo render(SysNumberPreviewRequest request) {
        if (null == request) {
            throw new IllegalArgumentException("预览请求不能为空：fieldId 与 rule 至少传一个");
        }
        String fieldId = StringUtil.trimToNull(request.getFieldId());
        SysNumberRuleConfigDo config;
        String scope;
        if (null != fieldId) {
            // fieldId 优先：已保存规则复用 load 的解析回填与 NOT_FOUND 语义
            SysNumberRule rule = ruleService.load(fieldId);
            config = parsedConfig(rule);
            // 已保存规则作用域 = 规则 field_id（§5.1，与取号侧 acquireByModel 一致）
            scope = rule.getFieldId();
        } else if (null != request.getRule()) {
            // 未保存 JSON 直渲：无作用域，无计数行 → 从 start 起
            config = request.getRule();
            scope = null;
        } else {
            throw new IllegalArgumentException("预览请求不能为空：fieldId 与 rule 至少传一个");
        }

        // C1~C5 校验：不过就地返回 errors，不抛异常（预览与 save/enable 硬失败的差异）
        List<SysNumberRuleErrorDo> errors = validator.validate(config);
        SysNumberPreviewVo vo = new SysNumberPreviewVo();
        vo.setErrors(errors);
        if (!errors.isEmpty()) {
            return vo;
        }

        // C1 已保证 seq 恰好一个，此处防御性兜底取第一个 seq 项
        Item seq = config.getItems().stream()
                .filter(item -> SysNumberRuleConfigDo.TYPE_SEQ.equals(item.getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("预览规则缺少流水号（seq）项，校验组件未拦截"));

        Date now = new Date();
        // 周期键派生与当前已用流水：与取号侧口径完全一致（R3）；
        // selectCurrentSerialNo 对「无行 / 封存行」返回 null（封存行周期键不同不会命中），null 视为 start-1
        String period = generateService.derivePeriodKey(seq.getResetPeriod(), now);
        Integer current = recordDao.selectCurrentSerialNo(scope, seq.getId(), period);
        int start = null == seq.getStart() ? 0 : seq.getStart();
        int base = Math.max(null == current ? start - 1 : current, start - 1);

        // C6 越界提示（C6 语义）：顺延超出容量时 errors 追加提示，样例只渲染到容量内的部分
        int capacity = generateService.seqCapacity(seq);
        int count = normalizeCount(request.getCount());
        if (base + count > capacity) {
            int overflowFromSample = Math.max(capacity - base, 0) + 1;
            errors.add(new SysNumberRuleErrorDo(CODE_C6, seq.getId(),
                    "当前周期流水自第 " + overflowFromSample + " 个样例起超出容量 " + seq.getLength()
                            + " 位（上限 " + capacity + "），正式取号将报 "
                            + NumberSerialExhaustedException.CODE));
            log.info("number preview serial exhausted hint: scope={}, serialId={}, capacity={}, base={}, count={}",
                    scope, seq.getId(), capacity, base, count);
        }

        // 顺延模拟：serialNo(i) = base + i（i=1..样例数），只渲染容量内的部分
        int sampleCount = Math.min(count, Math.max(capacity - base, 0));
        for (int i = 1; i <= sampleCount; i++) {
            vo.getSamples().add(generateService.render(config, base + i, now));
        }
        return vo;
    }

    /**
     * 已保存规则 field_rule 取解析后配置：load 已回填 {@link SysNumberRuleConfigDo}，
     * 非 ConfigDo（理论不可达）按规则非法处理
     */
    private static SysNumberRuleConfigDo parsedConfig(SysNumberRule rule) {
        if (rule.getFieldRule() instanceof SysNumberRuleConfigDo config) {
            return config;
        }
        throw new NumberRuleInvalidException(List.of(new SysNumberRuleErrorDo(
                SysNumberRuleValidator.CODE_C1, null,
                "编号规则 field_rule 未解析为配置对象：规则 " + rule.getFieldId())));
    }

    /**
     * 样例数量归一（§3.2）：null → 3；大于 10 → 10；小于 1 → 1
     */
    private static int normalizeCount(Integer count) {
        if (null == count) {
            return DEFAULT_SAMPLE_COUNT;
        }
        return Math.min(Math.max(count, 1), MAX_SAMPLE_COUNT);
    }
}
