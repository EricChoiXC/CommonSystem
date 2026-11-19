package com.eric.base.sys.process.model;

import com.eric.base.sys.process.properties.SysProcessConstants;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 流程模板节点配置文档对象
 *
 * @author Eric
 * @date 2026/8/22
 * @description 对应 sys_process_template_node.field_node_config（JSON 字符串）的结构化映射，
 *              由 Jackson 序列化/反序列化。统一 Do + 可空字段，按节点类型取用，向后兼容
 *              （依据 docs/设计文档/sys/process/流程核心业务.md「节点配置弹窗」与
 *              docs/设计文档/sys/process/流程v1_7接口清单.md「nodes[] 元素」契约）：
 *              <ul>
 *              <li>基础字段：审批来源（组织架构/表单字段）、审批人、审批模式（单签/会签）、
 *                  节点触发（固定/条件判断）、触发条件（判断公式）、是否允许自动跳过（跟随系统/是/否）、
 *                  是否允许连续跳过、操作标识开关（按 process_template.${fieldModelName}.ops 字典 key 开关）；</li>
 *              <li>v1_7 扩展字段：审批人公式（handlerFormula，审批源=formula 时必填）、
 *                  审批人解析方式（handlerResolveMode，leader/member，默认 leader）、
 *                  相同审批人跳过模式（skipMode，system/none/prev/any，承接旧 autoSkip 语义）、
 *                  无审批人处理方式（emptyHandlerMode，system/skip/error）、驳回目标（rejectTargets）、
 *                  审批操作三段明细（operations，必含且仅含一个 handler_pass）、流程任务明细（tasks）、
 *                  抄送节点配置（ccSource/ccIds/ccNames/ccFormula）、
 *                  人工决策节点配置（flowDecisions/defaultFlowKey）；</li>
 *              <li>旧值兼容归一：存量配置以旧键 autoSkip 的 yes/no 表达跳过语义，反序列化读取时
 *                  经 {@link #getSkipMode()} 归一为新枚举值（yes→prev、no→none，system/缺省→system），
 *                  不改写存量 JSON（autoSkip 原值保留）；skipMode 显式配置时优先于旧键 autoSkip；</li>
 *              <li>存量字段 triggerType/condition 保留原值；v1_7 条件路由由条件分支节点（CONDITION）
 *                  + 连线分支条件表达，节点级触发条件不再参与流转判定。</li>
 *              </ul>
 *              固定节点（START/DRAFT/END）无节点配置，field_node_config 为空。
 */
@Data
public class SysProcessTemplateNodeConfigDo {

    /** 旧值兼容：autoSkip 旧键取值 yes（读取归一为 prev） */
    private static final String LEGACY_SKIP_YES = "yes";

    /** 旧值兼容：autoSkip 旧键取值 no（读取归一为 none） */
    private static final String LEGACY_SKIP_NO = "no";

    /** 相同审批人跳过模式缺省值：同系统配置 */
    private static final String SKIP_MODE_DEFAULT = "system";

    /** 相同审批人跳过模式：上一审批节点审批人相同时跳过（旧值 yes 的归一结果） */
    private static final String SKIP_MODE_PREV = "prev";

    /** 相同审批人跳过模式：不跳过（旧值 no 的归一结果） */
    private static final String SKIP_MODE_NONE = "none";

    /** 审批人解析方式缺省值：负责人 */
    private static final String RESOLVE_MODE_DEFAULT = "leader";

    /** 无审批人处理方式缺省值：同系统配置（默认跳过） */
    private static final String EMPTY_HANDLER_DEFAULT = "system";

    /** 汇聚模式缺省值：全部激活分支到达后流出 */
    private static final String JOIN_MODE_DEFAULT = "all";

    /** 并行无可流转分支行为缺省值：转流程异常 */
    private static final String NO_BRANCH_MODE_DEFAULT = "error";

    /** 审批来源：org=组织架构（默认，取 handlerIds/handlerNames）/ field=表单字段（取 handlerField，运行期读文档字段值）/ formula=公式定义器（取 handlerFormula，v1_7 新增） */
    private String handlerSource;

    /** 审批人 id 列表（地址本多选，';' 分隔；审批来源为 org 时有效） */
    private String handlerIds;

    /** 审批人名称列表（与 handlerIds 一一对应，';' 分隔；审批来源为 org 时有效） */
    private String handlerNames;

    /** 审批人公式（审批来源为 formula 时必填；v1_7 新增，经公式定义器执行后按结果解析审批人） */
    private String handlerFormula;

    /** 审批人字段（文档实体字段属性名，取自 process_template.${fieldModelName}.fields 字典；审批来源为 field 时有效） */
    private String handlerField;

    /** 审批人解析方式：leader=负责人（部门/岗位/机构 field_leader_id，默认）/ member=成员（部门/岗位/机构员工）；仅对选中对象中的部门/岗位生效（v1_7 新增） */
    private String handlerResolveMode = RESOLVE_MODE_DEFAULT;

    /** 审批模式：single=单签（默认）/ serial=串行（v1_7 新增，按审批人列表顺序依次生成工作项）/ countersign=会签 */
    private String approveMode;

    /** 节点触发：fixed=固定 / condition=条件判断，默认 fixed（存量字段保留原值，不再参与流转判定） */
    private String triggerType;

    /** 触发条件（判断公式，triggerType=condition 时必填；${field} 占位符字段须为文档实体类存在字段；存量字段保留原值，不再参与流转判定） */
    private String condition;

    /** 是否允许自动跳过（旧键）：system=跟随系统 / yes=是 / no=否，默认 system；v1_7 起跳过语义由 skipMode 承接，存量 JSON 保留原值不改写 */
    private String autoSkip;

    /**
     * 相同审批人是否跳过（v1_7 新键，承接旧 autoSkip 语义）：system=同系统配置（默认）/
     * none=不跳过 / prev=上一审批节点审批人相同时跳过 / any=已审批人相同时跳过。
     * 旧值兼容归一：读取时经 {@link #getSkipMode()} 归一——旧键 autoSkip 的 yes/no 映射为 prev/none，
     * skipMode 键携带旧值 yes/no 同样映射；两者同时存在时以显式 skipMode 为准；存量 JSON 不改写。
     */
    private String skipMode;

    /** 是否允许连续跳过：system=跟随系统 / yes=是 / no=否，默认 system（沿用现有设计不变） */
    private String autoContinueSkip;

    /** 无审批人（审批源解析结果为空）时处理方式：system=同系统配置（默认跳过）/ skip=跳过该节点 / error=不跳过（转流程异常）（v1_7 新增） */
    private String emptyHandlerMode = EMPTY_HANDLER_DEFAULT;

    /** 可驳回目标节点 key 列表（候选限定为起草节点 + 图上游审批节点；空=默认仅起草节点；v1_7 新增） */
    private List<String> rejectTargets = new ArrayList<>();

    /**
     * 审批操作三段明细（起草人/审批人/已处理人，v1_7 新增）：每段为 {操作类型, 操作名称, 默认审批意见}；
     * 保存校验必须包含且仅包含一个 handler_pass；前端审批面板按节点配置 ∩ 用户身份渲染按钮。
     */
    private List<OperationDetail> operations = new ArrayList<>();

    /**
     * 流程任务明细（审批/抄送/机器人节点通用，v1_7 新增）：{任务名称, 执行任务(@ProcessTask name),
     * 执行步骤(ProcessTaskType), 任务参数}；行序即执行顺序。
     */
    private List<TaskDetail> tasks = new ArrayList<>();

    /** 抄送源：org=地址本（默认）/ formula=公式定义器（v1_7 新增；抄送节点有效） */
    private String ccSource;

    /** 抄送对象 id 列表（地址本多选，';' 分隔，机构/部门/人员/岗位/群组/角色全类型；抄送节点有效） */
    private String ccIds;

    /** 抄送对象名称列表（与 ccIds 一一对应，';' 分隔；抄送节点有效） */
    private String ccNames;

    /** 抄送对象公式（抄送源为 formula 时必填；解析规则同审批人公式；抄送节点有效） */
    private String ccFormula;

    /** 流向决策来源节点 key 列表（起草节点 + 图上游审批节点；被选中节点的审批人提交/通过时可指定本节点流向；v1_7 新增，人工决策节点有效） */
    private List<String> flowDecisions = new ArrayList<>();

    /** 默认流向（本节点出边目标节点 key 之一；无人工指定时走默认流向；v1_7 新增，人工决策节点有效） */
    private String defaultFlowKey;

    /** 汇聚模式：all=全部激活分支到达后流出（默认）/ any=任一激活分支到达即流出（v1_7 新增，并行开始节点有效） */
    private String joinMode = JOIN_MODE_DEFAULT;

    /** 无可流转分支时行为：error=转流程异常（默认）/ continue=全部出边激活后继续（v1_7 新增，并行开始节点有效） */
    private String noBranchMode = NO_BRANCH_MODE_DEFAULT;

    /**
     * 操作标识开关：key=操作标识（须定义在 process_template.${fieldModelName}.ops 中），
     * value=该节点审批人是否允许执行该操作。
     * 未配置/未开启（true 之外）均视为不允许（默认不允许，兼容旧模板）。
     */
    private Map<String, Boolean> ops;

    /**
     * 读取相同审批人跳过模式（读取时旧值归一，不改写存量 JSON）：
     * <ul>
     * <li>显式 skipMode 优先，携带旧值 yes/no 时同样归一为 prev/none；</li>
     * <li>未显式配置时按旧键 autoSkip 归一：yes→prev、no→none、system/缺省→system。</li>
     * </ul>
     */
    public String getSkipMode() {
        String value = null != skipMode && !skipMode.isBlank() ? skipMode : autoSkip;
        if (null == value || value.isBlank()) {
            return SKIP_MODE_DEFAULT;
        }
        String trimmed = value.trim();
        if (LEGACY_SKIP_YES.equals(trimmed)) {
            return SKIP_MODE_PREV;
        }
        if (LEGACY_SKIP_NO.equals(trimmed)) {
            return SKIP_MODE_NONE;
        }
        return trimmed;
    }

    /**
     * 读取汇聚模式（v1_7 并行开始节点配置）：仅 {@code any} 生效，其余（空/非法/缺省）取 {@code all}。
     */
    public String getJoinMode() {
        return SysProcessConstants.JOIN_MODE_ANY.equalsIgnoreCase(trimToNull(joinMode))
                ? SysProcessConstants.JOIN_MODE_ANY : JOIN_MODE_DEFAULT;
    }

    /**
     * 读取并行无可流转分支时的行为：仅 {@code continue} 生效，其余（空/非法/缺省）取 {@code error}。
     */
    public String getNoBranchMode() {
        return SysProcessConstants.NO_BRANCH_MODE_CONTINUE.equalsIgnoreCase(trimToNull(noBranchMode))
                ? SysProcessConstants.NO_BRANCH_MODE_CONTINUE : NO_BRANCH_MODE_DEFAULT;
    }

    private static String trimToNull(String value) {
        if (null == value) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 审批操作三段明细行（起草人/审批人/已处理人各一段）。
     */
    @Data
    public static class OperationDetail {

        /** 操作类型（14 类操作标识，如 handler_pass/handler_reject/handler_transfer；保存校验必须包含且仅包含一个 handler_pass） */
        private String operateType;

        /** 操作名称（可编辑，默认取操作类型中文名；审批面板按钮显示名） */
        private String operateName;

        /** 默认审批意见（面板选择该操作时回填审批意见框，用户可改） */
        private String defaultContent;
    }

    /**
     * 流程任务明细行（行序即执行顺序，引擎按 tasks 顺序执行）。
     */
    @Data
    public static class TaskDetail {

        /** 任务名称（仅展示名） */
        private String taskName;

        /** 执行任务（已注册 @ProcessTask name，保存时经 ProcessTaskRegistry 校验） */
        private String taskBean;

        /** 执行步骤（ProcessTaskType：CreateProcess/StartProcess/StartNode/StartWorkitem/SendTodo/EndWorkitem/EndNode/EndProcess） */
        private String taskType;

        /** 任务参数（JSON 对象，可空） */
        private Map<String, Object> params;
    }
}
