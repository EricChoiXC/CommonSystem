package com.eric.base.sys.process.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 流程模板连线视图对象
 *
 * @author Eric
 * @date 2026/11/19
 * @description 模板连线行数据（v1_7 新增，对应 sys_process_template_line）：
 *              v1_7 起连线是流程流转的唯一依据，模板 load 响应透出 lines、
 *              save/update 请求体携带 lines 并做全量替换（删旧插新，不做差量）。
 *              依据 docs/设计文档/sys/process/流程v1_7接口清单.md §4.2「lines[] 元素」
 *              与 §4.3「/save 与 /update 请求契约」。
 */
@Data
@Schema(description = "流程模板连线：起始节点键 → 目标节点键的有向边（v1_7 流转唯一依据，保存时全量替换）")
public class SysProcessTemplateLineVo {

    /** 连线 ID（新建由前端经 GET /backend/common/initID 预生成 32 位 ID） */
    @Schema(description = "连线 ID（32 位，新建时前端经 initID 预生成）")
    private String fieldId;

    /** 模板 ID（服务端按请求上下文回填，请求体可缺省） */
    @Schema(description = "模板 ID（服务端按请求上下文回填，请求体可缺省）")
    private String fieldTemplateId;

    /** 起始节点 nodeKey（必须存在于请求 nodes 的 nodeKey 集合） */
    @Schema(description = "起始节点 nodeKey（须存在于请求 nodes 的 nodeKey 集合，如 N1）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldFromKey;

    /** 目标节点 nodeKey（必须存在于请求 nodes 的 nodeKey 集合） */
    @Schema(description = "目标节点 nodeKey（须存在于请求 nodes 的 nodeKey 集合，如 N3）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldToKey;

    /** 连线类型：NORMAL / CONDITION / PARALLEL / MANUAL（须与起始节点类型匹配，缺省按起始节点类型推导） */
    @Schema(description = "连线类型：NORMAL=普通 / CONDITION=条件分支 / PARALLEL=并行分支 / MANUAL=人工决策；"
            + "须与起始节点类型匹配，缺省按起始节点类型推导")
    private String fieldLineType;

    /** 分支序号（条件/并行分支的判断顺序，上移下移即改此值） */
    @Schema(description = "分支序号（条件/并行分支的判断顺序，上移下移即改此值）")
    private Integer fieldBranchOrder;

    /** 分支名称（可空） */
    @Schema(description = "分支名称（条件/并行分支展示名，可空）")
    private String fieldBranchName;

    /** 分支条件公式（条件分支判断条件 / 并行分支启动条件，可空） */
    @Schema(description = "分支条件公式（条件分支判断条件 / 并行分支启动条件，可空）")
    private String fieldCondition;

    /** 是否默认分支（条件分支节点有且仅有一条为 true） */
    @Schema(description = "是否默认分支：条件分支（CONDITION）节点的出边中有且仅有一条为 true")
    private Boolean fieldIsDefault;
}
