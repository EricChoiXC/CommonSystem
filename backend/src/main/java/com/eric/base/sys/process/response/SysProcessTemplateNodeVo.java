package com.eric.base.sys.process.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 流程模板节点视图对象
 *
 * @author Eric
 * @date 2026/8/22
 * @description 模板节点行数据：固定节点（START/DRAFT/END）无节点配置；
 *              审批/抄送/机器人/条件分支/并行开始/人工决策节点配置项按字段展开，
 *              保存时由服务端序列化到 sys_process_template_node.field_node_config（JSON 字符串）。
 *              配置项依据 docs/设计文档/sys/process/流程核心业务.md「节点具体配置项信息」
 *              与 docs/设计文档/sys/process/流程v1_7接口清单.md §4.2「nodes[] 元素」：
 *              <ul>
 *              <li>输出字段名以接口清单 §4.2 契约为准（配置类字段不带 field 前缀，如
 *                  {@code nodeKey}）；实体列仍为 {@code field_node_key}、实体属性仍为
 *                  {@code fieldNodeKey}，Service 负责两者映射；</li>
 *              <li>存量字段（handlerSource/handlerIds/handlerNames/handlerField/approveMode/
 *                  triggerType/condition/autoSkip/autoContinueSkip/ops）保留兼容；</li>
 *              <li>v1_7 扩展字段：handlerFormula / handlerResolveMode / skipMode /
 *                  emptyHandlerMode / rejectTargets / operations / tasks / ccSource / ccIds /
 *                  ccNames / ccFormula / flowDecisions / defaultFlowKey / joinMode。</li>
 *              </ul>
 */
@Data
@Schema(description = "流程模板节点：固定节点无配置；其余节点按节点类型取用配置字段（v1_7 起流转由连线决定，fieldNo 仅展示排序）")
public class SysProcessTemplateNodeVo {

    /** 节点 id */
    @Schema(description = "节点 ID")
    private String fieldId;

    /** 节点编号（服务端按固定节点规则归一：START=1、DRAFT=2、其余节点顺延、END 最后；仅展示排序，不参与流转判定） */
    @Schema(description = "节点展示序号（服务端归一：START=1、DRAFT=2、其余顺延、END 最后；v1_7 起不参与流转判定）")
    private Integer fieldNo;

    /** 节点业务键（模板内唯一，固定节点 N1=开始 N2=起草 N3=结束，其余 N4… 顺延；连线以此表达） */
    @Schema(description = "节点业务键：固定节点 N1(开始)/N2(起草)/N3(结束)，其余 N4… 顺延；模板内唯一，连线以该键表达")
    private String nodeKey;

    /** 节点名称 */
    @Schema(description = "节点名称")
    private String fieldNodeName;

    /** 节点类型：START / DRAFT / END / APPROVE / CC / CONDITION / FORK / JOIN / ROBORT / MANUAL（10 类，ROBORT 沿用历史拼写） */
    @Schema(description = "节点类型：START/DRAFT/END/APPROVE/CC/CONDITION/FORK/JOIN/ROBORT/MANUAL（10 类，ROBORT 沿用历史拼写）")
    private String fieldNodeType;

    /** 审批来源：org=组织架构（默认）/ field=表单字段 / formula=公式定义器 */
    @Schema(description = "审批来源：org=组织架构（默认）/ field=表单字段 / formula=公式定义器（v1_7 扩展）")
    private String handlerSource;

    /** 审批人 id 列表（地址本多选，机构/部门/人员/岗位/群组/角色全类型，';' 分隔；审批来源为 org 时有效） */
    @Schema(description = "审批人 ID 列表（地址本多选，全类型，';' 分隔；审批来源 org 时有效）")
    private String handlerIds;

    /** 审批人名称列表（与 handlerIds 一一对应，';' 分隔；审批来源为 org 时有效） */
    @Schema(description = "审批人名称列表（与 handlerIds 一一对应，';' 分隔；审批来源 org 时有效）")
    private String handlerNames;

    /** 审批人字段（文档实体字段属性名，取自 process_template.${fieldModelName}.fields 字典；审批来源为 field 时有效） */
    @Schema(description = "审批人字段（文档实体字段属性名，取自分类 fields 字典；审批来源 field 时必填）")
    private String handlerField;

    /** 审批人公式（审批来源为 formula 时必填；v1_7 新增） */
    @Schema(description = "审批人公式（审批来源 formula 时必填；v1_7 新增）")
    private String handlerFormula;

    /** 审批人解析方式：leader=负责人（默认）/ member=成员；仅对选中对象中的部门/岗位/机构生效（v1_7 新增） */
    @Schema(description = "审批人解析方式：leader=负责人（默认）/ member=成员（v1_7 新增，仅对部门/岗位/机构生效）")
    private String handlerResolveMode;

    /** 审批模式：single=单签（默认）/ serial=串行 / countersign=会签 */
    @Schema(description = "审批模式：single=单签（默认）/ serial=串行（v1_7 新增）/ countersign=会签")
    private String approveMode;

    /** 节点触发：fixed=固定 / condition=条件判断（存量字段保留原值，v1_7 起不再参与流转判定） */
    @Schema(description = "节点触发：fixed=固定 / condition=条件判断（存量字段，v1_7 起不参与流转判定）")
    private String triggerType;

    /** 触发条件（判断公式，triggerType=condition 时必填；存量字段保留原值，不再参与流转判定） */
    @Schema(description = "触发条件（判断公式，triggerType=condition 时必填；存量字段，v1_7 起不参与流转判定）")
    private String condition;

    /** 是否允许自动跳过（旧键）：system=跟随系统 / yes=是 / no=否，默认 system；v1_7 起语义由 skipMode 承接 */
    @Schema(description = "是否允许自动跳过（旧键）：system=跟随系统 / yes=是 / no=否；v1_7 起由 skipMode 承接")
    private String autoSkip;

    /** 相同审批人是否跳过：system=同系统配置（默认）/ none / prev / any；旧值 yes→prev、no→none 读取归一（v1_7 新增） */
    @Schema(description = "相同审批人跳过模式：system=同系统配置（默认）/ none=不跳过 / prev=上一审批节点相同则跳过 / any=已审批人相同则跳过（v1_7 新增）")
    private String skipMode;

    /** 是否允许连续跳过：system=跟随系统 / yes=是 / no=否，默认 system */
    @Schema(description = "是否允许连续跳过：system=跟随系统 / yes=是 / no=否，默认 system")
    private String autoContinueSkip;

    /** 无审批人时处理方式：system=同系统配置（默认跳过）/ skip=跳过该节点 / error=不跳过（转流程异常）（v1_7 新增） */
    @Schema(description = "无审批人时处理方式：system=同系统配置（默认跳过）/ skip=跳过该节点 / error=不跳过（转流程异常）（v1_7 新增）")
    private String emptyHandlerMode;

    /** 可驳回目标节点 key 列表（候选限定为起草节点 + 图上游审批节点；空=默认仅起草节点；v1_7 新增） */
    @Schema(description = "可驳回目标节点 key 列表（候选限定为起草节点 + 图上游审批节点；空=默认仅起草节点；v1_7 新增）")
    private List<String> rejectTargets;

    /** 审批操作三段明细（必须包含且仅包含一个 handler_pass；v1_7 新增） */
    @Schema(description = "审批操作三段明细（起草人/审批人/已处理人）：{operateType, operateName, defaultContent}；"
            + "审批节点必须包含且仅包含一个 handler_pass（v1_7 新增）")
    private List<OperationDetailVo> operations;

    /** 流程任务明细：{taskName, taskBean(@ProcessTask name), taskType(ProcessTaskType), params}（v1_7 新增） */
    @Schema(description = "流程任务明细：{taskName, taskBean(@ProcessTask name), taskType(ProcessTaskType), params}；"
            + "保存时经 ProcessTaskRegistry 校验（任务已注册、processTemplate 匹配、allowTaskType 含所选步骤）")
    private List<TaskDetailVo> tasks;

    /** 抄送源：org=地址本（默认）/ formula=公式定义器（CC 节点有效；v1_7 新增） */
    @Schema(description = "抄送源：org=地址本（默认）/ formula=公式定义器（CC 节点有效；v1_7 新增）")
    private String ccSource;

    /** 抄送对象 id 列表（地址本多选，全类型，';' 分隔；CC 节点有效） */
    @Schema(description = "抄送对象 ID 列表（地址本多选，全类型，';' 分隔；CC 节点有效）")
    private String ccIds;

    /** 抄送对象名称列表（与 ccIds 一一对应，';' 分隔；CC 节点有效） */
    @Schema(description = "抄送对象名称列表（与 ccIds 一一对应，';' 分隔；CC 节点有效）")
    private String ccNames;

    /** 抄送对象公式（抄送源为 formula 时必填；CC 节点有效） */
    @Schema(description = "抄送对象公式（抄送源 formula 时必填；CC 节点有效）")
    private String ccFormula;

    /** 流向决策来源节点 key 列表（起草节点 + 图上游审批节点；MANUAL 节点有效；v1_7 新增） */
    @Schema(description = "流向决策来源节点 key 列表（候选限定为起草节点 + 该节点图上游审批节点；MANUAL 节点有效；v1_7 新增）")
    private List<String> flowDecisions;

    /** 默认流向（本节点实际出边目标之一；MANUAL 节点有效；v1_7 新增） */
    @Schema(description = "默认流向（必须是本节点实际出边目标之一；MANUAL 节点有效；v1_7 新增）")
    private String defaultFlowKey;

    /** 汇聚模式：all=全部激活分支到达后流出（默认）/ any=任一到达即流出（FORK 节点有效；v1_7 新增） */
    @Schema(description = "汇聚模式：all=全部激活分支到达后流出（默认）/ any=任一激活分支到达即流出（FORK 节点有效；v1_7 新增）")
    private String joinMode;

    /**
     * 操作标识开关：key=操作标识（须定义在 process_template.${fieldModelName}.ops 中），
     * value=该节点审批人是否允许执行该操作；未配置/未开启均视为不允许。
     */
    @Schema(description = "操作标识开关：key=操作标识（须定义在分类 ops 字典中），value=true 表示允许；未开启视为不允许")
    private Map<String, Boolean> ops;

    /**
     * 审批操作三段明细行（起草人/审批人/已处理人各一段，v1_7 新增）：
     * 保存校验必须包含且仅包含一个 {@code handler_pass}；前端审批面板按节点配置 ∩ 用户身份渲染按钮。
     */
    @Data
    @Schema(description = "审批操作明细行：{operateType, operateName, defaultContent}")
    public static class OperationDetailVo {

        /** 操作类型（14 类操作标识，如 handler_pass/handler_reject/handler_transfer） */
        @Schema(description = "操作类型（14 类操作标识，如 handler_pass/handler_reject/handler_transfer）")
        private String operateType;

        /** 操作名称（可编辑，默认取操作类型中文名；审批面板按钮显示名） */
        @Schema(description = "操作名称（可编辑，默认取操作类型中文名；审批面板按钮显示名）")
        private String operateName;

        /** 默认审批意见（面板选择该操作时回填审批意见框，用户可改） */
        @Schema(description = "默认审批意见（面板选择该操作时回填审批意见框，用户可改）")
        private String defaultContent;
    }

    /**
     * 流程任务明细行（v1_7 新增，行序即执行顺序，引擎按 tasks 顺序执行）。
     */
    @Data
    @Schema(description = "流程任务明细行：{taskName, taskBean, taskType, params}，行序即执行顺序")
    public static class TaskDetailVo {

        /** 任务名称（仅展示名） */
        @Schema(description = "任务名称（仅展示名）")
        private String taskName;

        /** 执行任务（已注册 @ProcessTask name，保存时经 ProcessTaskRegistry 校验） */
        @Schema(description = "执行任务（已注册 @ProcessTask 的 name，保存时经 ProcessTaskRegistry 校验）")
        private String taskBean;

        /** 执行步骤（ProcessTaskType：CreateProcess/StartProcess/StartNode/StartWorkitem/SendTodo/EndWorkitem/EndNode/EndProcess） */
        @Schema(description = "执行步骤（ProcessTaskType 枚举名）")
        private String taskType;

        /** 任务参数（JSON 对象，可空） */
        @Schema(description = "任务参数（JSON 对象，可空）")
        private Map<String, Object> params;
    }
}
