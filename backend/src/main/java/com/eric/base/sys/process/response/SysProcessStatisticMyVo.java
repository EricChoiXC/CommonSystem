package com.eric.base.sys.process.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 我的流程统计视图对象
 *
 * @author Eric
 * @date 2026/11/23
 * @description 我的统计响应（P17-BE-016，依据 docs/设计文档/sys/process/流程v1_7接口清单.md §7.2）：
 *              今日发起、我的未办结、待审、今日已审与平均耗时五项指标。
 *              统计对象只取会话用户（UserUtil），请求体不接收任何 userId 字段（防越权）；
 *              未登录时注入 null，本人条件比对 NULL 恒不成立，五项指标全为 0（零泄露）。
 */
@Data
@Schema(description = "我的统计：todayCreatedCount / myProcessingCount / pendingApproveCount / todayApprovedCount / avgDurationMs")
public class SysProcessStatisticMyVo {

    /** 今日发起：本人起草（field_creator_id = 会话用户）且创建时间在今日的实例数 */
    @Schema(description = "今日发起：本人起草且创建时间在今日的流程实例数")
    private long todayCreatedCount;

    /** 我的未办结：本人发起且状态 ∈ DRAFT / PROCESSING / RETURN / PAUSE 的实例数 */
    @Schema(description = "我的未办结：本人发起且状态为 DRAFT / PROCESSING / RETURN / PAUSE 的流程实例数")
    private long myProcessingCount;

    /** 待审：本人为处理人（field_handler_id = 会话用户）且状态为 PROCESSING 的工作项数 */
    @Schema(description = "待审：本人为处理人且状态为 PROCESSING 的工作项数")
    private long pendingApproveCount;

    /** 今日已审：本人办结（field_handler_id = 会话用户，状态 FINISH）且完成时间在今日的工作项数 */
    @Schema(description = "今日已审：本人办结（状态 FINISH）且完成时间在今日的工作项数")
    private long todayApprovedCount;

    /** 平均耗时（毫秒）：本人参与（工作项处理人为会话用户，与「我参与的」口径一致）且已办结（FINISH）实例的平均办结时长（自然时长；无办结实例为 0） */
    @Schema(description = "平均耗时（毫秒）：本人参与且已办结（FINISH）实例的平均办结时长（自然时长；无办结实例为 0）")
    private long avgDurationMs;
}
