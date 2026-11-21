package com.eric.base.sys.process.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 流程实例连线视图对象
 *
 * @author Eric
 * @date 2026/11/21
 * @description 实例连线快照行数据（对应 sys_process_example_line）：实例创建时从模板连线
 *              全量快照复制，实例详情 load 与审批面板 panel 响应透出 lines 供只读流程图渲染。
 *              字段同模板连线 {@link SysProcessTemplateLineVo}，仅 fieldTemplateId 替换为
 *              fieldExampleId。依据 docs/设计文档/sys/process/流程v1_7接口清单.md
 *              §5.3 / §5.4「lines[] 元素」。
 */
@Data
@Schema(description = "流程实例连线快照：起始节点键 → 目标节点键的有向边（实例创建时从模板连线全量快照复制，供只读流程图渲染）")
public class SysProcessExampleLineVo {

    /** 连线 ID */
    @Schema(description = "连线 ID（32 位）")
    private String fieldId;

    /** 流程实例 ID */
    @Schema(description = "流程实例 ID（替代模板侧 fieldTemplateId，创建时按实例回填）")
    private String fieldExampleId;

    /** 起始节点 nodeKey（实例内节点业务键快照） */
    @Schema(description = "起始节点 nodeKey（实例内节点业务键快照，如 N1）")
    private String fieldFromKey;

    /** 目标节点 nodeKey（实例内节点业务键快照） */
    @Schema(description = "目标节点 nodeKey（实例内节点业务键快照，如 N3）")
    private String fieldToKey;

    /** 连线类型：NORMAL / CONDITION / PARALLEL / MANUAL */
    @Schema(description = "连线类型：NORMAL=普通 / CONDITION=条件分支 / PARALLEL=并行分支 / MANUAL=人工决策")
    private String fieldLineType;

    /** 分支序号（条件/并行分支的判断顺序） */
    @Schema(description = "分支序号（条件/并行分支的判断顺序，上移下移即改此值）")
    private Integer fieldBranchOrder;

    /** 分支名称（可空） */
    @Schema(description = "分支名称（条件/并行分支展示名，可空）")
    private String fieldBranchName;

    /** 分支条件公式（可空） */
    @Schema(description = "分支条件公式（条件分支判断条件 / 并行分支启动条件，可空）")
    private String fieldCondition;

    /** 是否默认分支 */
    @Schema(description = "是否默认分支：条件分支（CONDITION）节点的出边中有且仅有一条为 true")
    private Boolean fieldIsDefault;
}
