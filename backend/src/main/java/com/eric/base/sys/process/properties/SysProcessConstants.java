package com.eric.base.sys.process.properties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程模块常量
 *
 * @author Eric
 * @date 2026/8/21
 * @description 流程模块接口标识（resolver 鉴权使用）、角色编码与公共配置项元信息，
 *              供 Controller / Resolver / Service 静态引用。
 *              权限与公共配置依据 docs/设计文档/sys/process/流程核心业务.md。
 */
public class SysProcessConstants {

    private SysProcessConstants() {
    }

    /* ==================== 模块标识与页面路由 ==================== */

    /** 流程模块 MODEL_KEY（properties/sys/process/application.yml 的 MODEL_KEY，通知待办 field_model_name 使用） */
    public static final String MODEL_KEY_SYS_PROCESS = "SYS-PROCESS";
    /** 流程实例详情页路由前缀（前端路由 processExample/detail/:id，通知待办跳转链接使用） */
    public static final String EXAMPLE_DETAIL_URL_PREFIX = "/processExample/detail/";

    /* ==================== 接口标识（resolver 鉴权使用） ==================== */

    /** 流程公共配置列表 */
    public static final String ACTION_PROCESS_SETTING_LIST = "sys.process.setting.list";
    /** 流程公共配置提交（仅更新有变更/重置的配置项） */
    public static final String ACTION_PROCESS_SETTING_SAVE = "sys.process.setting.save";
    /** 流程公共配置重置（还原为默认值） */
    public static final String ACTION_PROCESS_SETTING_RESET = "sys.process.setting.reset";

    /** 流程模板分类列表（process_template 配置项） */
    public static final String ACTION_PROCESS_MODULE_MODELS = "sys.process.module.models";
    /** 流程模板列表查询 */
    public static final String ACTION_PROCESS_MODULE_LIST = "sys.process.module.list";
    /** 流程模板详情 */
    public static final String ACTION_PROCESS_MODULE_LOAD = "sys.process.module.load";
    /** 流程模板新建 */
    public static final String ACTION_PROCESS_MODULE_SAVE = "sys.process.module.save";
    /** 流程模板编辑（历史版本归档 + 节点全量替换） */
    public static final String ACTION_PROCESS_MODULE_UPDATE = "sys.process.module.update";
    /** 流程模板启停用 */
    public static final String ACTION_PROCESS_MODULE_ENABLE = "sys.process.module.enable";

    /** 置为默认模板（替换同分类现有默认模板，仅模板管理员） */
    public static final String ACTION_PROCESS_MODULE_SET_DEFAULT = "sys.process.module.setDefault";

    /** 流程实例列表查询 */
    public static final String ACTION_PROCESS_EXAMPLE_LIST = "sys.process.example.list";
    /** 流程实例所属模块选项（process_template 配置项，流程实例页筛选下拉） */
    public static final String ACTION_PROCESS_EXAMPLE_MODELS = "sys.process.example.models";
    /** 流程实例详情（流程实例 + 节点 + 工作项 + 审批记录） */
    public static final String ACTION_PROCESS_EXAMPLE_LOAD = "sys.process.example.load";
    /** 流程审批面板数据（业务文档页嵌入审批组件使用） */
    public static final String ACTION_PROCESS_EXAMPLE_PANEL = "sys.process.example.panel";
    /** 流程实例特权操作（跳转/终审通过/作废/暂停/恢复，仅 SYS_PROCESS_EXAMPLE_ADMIN） */
    public static final String ACTION_PROCESS_EXAMPLE_ADMIN_OPERATE = "sys.process.example.adminOperate";

    /** 统一审批操作接口（14 类审批操作，另需引擎内操作人身份校验） */
    public static final String ACTION_PROCESS_EXAMPLE_OPERATE = "sys.process.example.operate";
    /** 流程监控列表（仅 SYS_PROCESS_EXAMPLE_ADMIN；Controller 由 P17-BE-015 落地） */
    public static final String ACTION_PROCESS_EXAMPLE_MONITOR_LIST = "sys.process.example.monitorList";
    /** 流程管理员统计（仅 SYS_PROCESS_EXAMPLE_ADMIN；Controller 由 Wave 4 落地） */
    public static final String ACTION_PROCESS_STATISTIC_ADMIN = "sys.process.statistic.admin";
    /** 我的流程统计（登录且启用即可，准入 SYS_PROCESS_MY_VIEWER；Controller 由 Wave 4 落地） */
    public static final String ACTION_PROCESS_STATISTIC_MY = "sys.process.statistic.my";

    /* ==================== 角色编码 ==================== */

    /** 流程管理员：拥有流程模块所有操作权限 */
    public static final String ROLE_SYS_PROCESS_ADMIN = "SYS_PROCESS_ADMIN";
    /** 流程模板管理员：维护流程模板，SYS_PROCESS_TEMPLATE_VIEWER 上级 */
    public static final String ROLE_SYS_PROCESS_TEMPLATE_ADMIN = "SYS_PROCESS_TEMPLATE_ADMIN";
    /** 流程模板查看者：仅查看流程模板 */
    public static final String ROLE_SYS_PROCESS_TEMPLATE_VIEWER = "SYS_PROCESS_TEMPLATE_VIEWER";
    /** 流程实例管理员：流程实例所有操作权限，SYS_PROCESS_EXAMPLE_VIEWER 上级 */
    public static final String ROLE_SYS_PROCESS_EXAMPLE_ADMIN = "SYS_PROCESS_EXAMPLE_ADMIN";
    /** 流程实例查看者：仅查看流程实例 */
    public static final String ROLE_SYS_PROCESS_EXAMPLE_VIEWER = "SYS_PROCESS_EXAMPLE_VIEWER";
    /** 流程模块公共配置维护员：维护流程模块公共配置 */
    public static final String ROLE_SYS_PROCESS_SETTING_ADMIN = "SYS_PROCESS_SETTING_ADMIN";
    /** 我的流程查看者：访问审批一级菜单「我发起的 / 我参与的 / 我的流程统计」（默认全员授予口径见权限分册） */
    public static final String ROLE_SYS_PROCESS_MY_VIEWER = "SYS_PROCESS_MY_VIEWER";

    /* ==================== 流程公共配置项 ==================== */

    /** 流程公共配置类型：process（流程运转） */
    public static final String SETTING_TYPE_PROCESS = "process";
    /** 流程公共配置类型：notify（通知待办） */
    public static final String SETTING_TYPE_NOTIFY = "notify";

    /** 流程公共配置覆盖的配置类型（页面列表范围） */
    public static final List<String> SETTING_TYPES = List.of(SETTING_TYPE_PROCESS, SETTING_TYPE_NOTIFY);

    /** 是否自动跳过重复审批人 */
    public static final String SETTING_PROCESS_AUTO_HANDLE_PASS = "auto_handle_pass";
    /** 自动跳过重复审批人是否包含起草人 */
    public static final String SETTING_PROCESS_AUTO_HANDLE_PASS_DRAFTER = "auto_handle_pass_drafter";
    /** 是否连续跳过（上一有效节点自动跳过的处理人，本节点是否继续自动跳过） */
    public static final String SETTING_PROCESS_AUTO_CONTINUE_PASS = "auto_continue_pass";
    /** 流程办结通知审批人 */
    public static final String SETTING_PROCESS_FINISH_NOTIFY_HANDLER = "finish_notify_handler";
    /** 审批待办默认标题 */
    public static final String SETTING_NOTIFY_HANDLE_SUBJECT = "notify_handle_subject";
    /** 被驳回流程待办默认标题 */
    public static final String SETTING_NOTIFY_REFUSE_SUBJECT = "notify_refuse_subject";
    /** 流程办结起草人待阅默认标题 */
    public static final String SETTING_NOTIFY_FINISH_DRAFTER_SUBJECT = "notify_finish_drafter_subject";
    /** 流程办结审批人待阅默认标题 */
    public static final String SETTING_NOTIFY_FINISH_HANDLER_SUBJECT = "notify_finish_handler_subject";
    /** 抄送待阅默认标题（抄送节点 / 抄送动作 handler_cc 共用，v1_7 新增） */
    public static final String SETTING_NOTIFY_CC_SUBJECT = "notify_cc_subject";
    /** 催办待阅默认标题（起草人催办 / 已审批人催办共用，v1_7 新增） */
    public static final String SETTING_NOTIFY_EXPEDITE_SUBJECT = "notify_expedite_subject";

    /* ==================== 通知待办标题模板占位符（notify.* subject 模板共用口径） ==================== */

    /** 标题占位符：流程起草人姓名（渲染失败回退空串） */
    public static final String NOTIFY_PLACEHOLDER_CREATOR_NAME = "${fieldCreatorName}";
    /** 标题占位符：流程名称（模板名；模板缺失回退流程编号，再回退空串） */
    public static final String NOTIFY_PLACEHOLDER_EXAMPLE_NAME = "${fieldName}";
    /** 抄送待阅默认标题（notify_cc_subject 缺省回退值，与 application.yml SETTING 默认值一致，v1_7 新增） */
    public static final String DEFAULT_NOTIFY_CC_SUBJECT = "您被抄送的流程：${fieldName}";
    /** 催办待阅默认标题（notify_expedite_subject 缺省回退值，与 application.yml SETTING 默认值一致，v1_7 新增） */
    public static final String DEFAULT_NOTIFY_EXPEDITE_SUBJECT = "请尽快处理${fieldCreatorName}提交的流程：${fieldName}";

    /**
     * 公共配置中文描述（页面仅显示中文描述与值），key = "${field_type}.${field_key}"。
     * 顺序与 docs/设计文档/sys/process/流程核心业务.md「流程公共配置」保持一致。
     */
    public static final Map<String, String> SETTING_DESC = buildSettingDesc();

    private static Map<String, String> buildSettingDesc() {
        Map<String, String> desc = new LinkedHashMap<>();
        desc.put(SETTING_TYPE_PROCESS + "." + SETTING_PROCESS_AUTO_HANDLE_PASS, "是否自动跳过重复审批人");
        desc.put(SETTING_TYPE_PROCESS + "." + SETTING_PROCESS_AUTO_HANDLE_PASS_DRAFTER, "自动跳过重复审批人是否包含起草人");
        desc.put(SETTING_TYPE_PROCESS + "." + SETTING_PROCESS_AUTO_CONTINUE_PASS, "是否连续跳过");
        desc.put(SETTING_TYPE_PROCESS + "." + SETTING_PROCESS_FINISH_NOTIFY_HANDLER, "流程办结通知审批人");
        desc.put(SETTING_TYPE_NOTIFY + "." + SETTING_NOTIFY_HANDLE_SUBJECT, "审批待办默认标题");
        desc.put(SETTING_TYPE_NOTIFY + "." + SETTING_NOTIFY_REFUSE_SUBJECT, "被驳回流程待办默认标题");
        desc.put(SETTING_TYPE_NOTIFY + "." + SETTING_NOTIFY_FINISH_DRAFTER_SUBJECT, "流程办结起草人待阅默认标题");
        desc.put(SETTING_TYPE_NOTIFY + "." + SETTING_NOTIFY_FINISH_HANDLER_SUBJECT, "流程办结审批人待阅默认标题");
        desc.put(SETTING_TYPE_NOTIFY + "." + SETTING_NOTIFY_CC_SUBJECT, "抄送待阅默认标题（抄送节点/审批人抄送共用）");
        desc.put(SETTING_TYPE_NOTIFY + "." + SETTING_NOTIFY_EXPEDITE_SUBJECT, "催办待阅默认标题（起草人催办/已审批人催办共用）");
        return desc;
    }

    /* ==================== 流程模板（sys.process.module.*） ==================== */

    /** 节点类型：开始节点 */
    public static final String NODE_TYPE_START = "START";
    /** 节点类型：结束节点 */
    public static final String NODE_TYPE_END = "END";
    /** 节点类型：草稿节点 */
    public static final String NODE_TYPE_DRAFT = "DRAFT";
    /** 节点类型：审批节点 */
    public static final String NODE_TYPE_APPROVE = "APPROVE";
    /** 节点类型：机器人节点（历史拼写沿用不改名，正确拼写应为 ROBOT） */
    public static final String NODE_TYPE_ROBORT = "ROBORT";
    /** 节点类型：抄送节点（v1_7 新增，解析抄送对象后自动办结继续） */
    public static final String NODE_TYPE_CC = "CC";
    /** 节点类型：条件分支（v1_7 新增，按出边分支条件取首个 true，无 true 走默认分支） */
    public static final String NODE_TYPE_CONDITION = "CONDITION";
    /** 节点类型：并行开始（v1_7 新增，逐出边求值启动条件，激活分支各自流转） */
    public static final String NODE_TYPE_FORK = "FORK";
    /** 节点类型：并行结束（v1_7 新增，按汇聚模式等待激活分支到达后流出） */
    public static final String NODE_TYPE_JOIN = "JOIN";
    /** 节点类型：人工决策（v1_7 新增，按最近一次人工指定流向或默认流向流出） */
    public static final String NODE_TYPE_MANUAL = "MANUAL";

    /** 固定节点键：开始节点 */
    public static final String FIXED_NODE_KEY_START = "N1";
    /** 固定节点键：草稿节点 */
    public static final String FIXED_NODE_KEY_DRAFT = "N2";
    /** 固定节点键：结束节点 */
    public static final String FIXED_NODE_KEY_END = "N3";

    /** 并行汇聚模式：全部激活分支到达后流出（缺省） */
    public static final String JOIN_MODE_ALL = "all";
    /** 并行汇聚模式：任一激活分支到达即流出 */
    public static final String JOIN_MODE_ANY = "any";

    /* ==================== 连线类型（sys_process_template_line / sys_process_example_line） ==================== */

    /** 连线类型：普通连线 */
    public static final String LINE_TYPE_NORMAL = "NORMAL";
    /** 连线类型：条件分支（分支条件存连线 field_condition） */
    public static final String LINE_TYPE_CONDITION = "CONDITION";
    /** 连线类型：并行分支（分支启动条件存连线 field_condition） */
    public static final String LINE_TYPE_PARALLEL = "PARALLEL";
    /** 连线类型：人工决策 */
    public static final String LINE_TYPE_MANUAL = "MANUAL";

    /** FORK 无可流转分支时行为：转流程异常（缺省） */
    public static final String NO_BRANCH_MODE_ERROR = "error";
    /** FORK 无可流转分支时行为：全部出边激活后继续 */
    public static final String NO_BRANCH_MODE_CONTINUE = "continue";

    /** 固定开始节点编号 */
    public static final int FIXED_NODE_NO_START = 1;
    /** 固定草稿节点编号 */
    public static final int FIXED_NODE_NO_DRAFT = 2;

    /** 固定节点名称：开始节点 */
    public static final String FIXED_NODE_NAME_START = "开始节点";
    /** 固定节点名称：草稿节点 */
    public static final String FIXED_NODE_NAME_DRAFT = "草稿节点";
    /** 固定节点名称：结束节点 */
    public static final String FIXED_NODE_NAME_END = "结束节点";

    /** 审批模式：单签 */
    public static final String APPROVE_MODE_SINGLE = "single";
    /** 审批模式：会审 */
    public static final String APPROVE_MODE_COUNTERSIGN = "countersign";
    /** 审批模式：串行（v1_7 新增，按审批人列表顺序依次生成工作项） */
    public static final String APPROVE_MODE_SERIAL = "serial";
    /** 节点触发：固定 */
    public static final String TRIGGER_TYPE_FIXED = "fixed";
    /** 节点触发：条件判断 */
    public static final String TRIGGER_TYPE_CONDITION = "condition";
    /** 是否允许自动跳过：跟随系统 */
    public static final String AUTO_SKIP_SYSTEM = "system";
    /** 是否允许自动跳过：是 */
    public static final String AUTO_SKIP_YES = "yes";
    /** 是否允许自动跳过：否 */
    public static final String AUTO_SKIP_NO = "no";

    /**
     * 相同审批人跳过模式（skipMode，v1_7 新增，P17-BE-004 接入引擎）：
     * 跳过总开关与比对范围，与 autoContinueSkip（连续跳过）为两个维度。
     * {@link SysProcessTemplateNodeConfigDo#getSkipMode()} 已做旧键 autoSkip 的 yes→prev、no→none 归一。
     */
    /** 相同审批人跳过模式：同系统配置（沿用公共配置 auto_handle_pass / auto_handle_pass_drafter；缺省） */
    public static final String SKIP_MODE_SYSTEM = "system";
    /** 相同审批人跳过模式：不跳过（即便系统配置允许自动跳过） */
    public static final String SKIP_MODE_NONE = "none";
    /** 相同审批人跳过模式：上一有效审批节点（终态 FINISH/REJECT，自动跳过节点不算）的处理人相同时跳过 */
    public static final String SKIP_MODE_PREV = "prev";
    /** 相同审批人跳过模式：本实例全部已审批人（已通过/已驳回审批节点的处理人并集）相同时跳过 */
    public static final String SKIP_MODE_ANY = "any";

    /** 审批来源：组织架构（默认，直接取节点配置的审批人） */
    public static final String HANDLER_SOURCE_ORG = "org";
    /** 审批来源：表单字段（审批时读取文档实体字段值解析出组织架构作为审批人） */
    public static final String HANDLER_SOURCE_FIELD = "field";
    /** 审批来源：公式定义器（v1_7 新增，执行节点配置的审批人公式后解析组织架构作为审批人） */
    public static final String HANDLER_SOURCE_FORMULA = "formula";

    /** 审批人解析方式：负责人（部门/岗位/机构取 field_leader_id，缺省值） */
    public static final String HANDLER_RESOLVE_MODE_LEADER = "leader";
    /** 审批人解析方式：成员（部门/机构取子树内员工，岗位取岗位关联用户） */
    public static final String HANDLER_RESOLVE_MODE_MEMBER = "member";

    /** 无审批人处理方式：同系统配置（默认，由下游按系统配置决定是否跳过） */
    public static final String EMPTY_HANDLER_MODE_SYSTEM = "system";
    /** 无审批人处理方式：跳过该节点 */
    public static final String EMPTY_HANDLER_MODE_SKIP = "skip";
    /** 无审批人处理方式：不跳过（转流程异常） */
    public static final String EMPTY_HANDLER_MODE_ERROR = "error";

    /** 抄送源：地址本（默认，取节点配置的抄送对象） */
    public static final String CC_SOURCE_ORG = "org";
    /** 抄送源：公式定义器（v1_7 新增，执行节点配置的抄送对象公式后解析） */
    public static final String CC_SOURCE_FORMULA = "formula";

    /** 是否允许连续跳过：跟随系统（与是否允许自动跳过共用值域 system / yes / no） */
    public static final String AUTO_CONTINUE_SKIP_SYSTEM = "system";
    /** 是否允许连续跳过：是 */
    public static final String AUTO_CONTINUE_SKIP_YES = "yes";
    /** 是否允许连续跳过：否 */
    public static final String AUTO_CONTINUE_SKIP_NO = "no";

    /** 节点配置键：审批来源（org=组织架构（默认）/ field=表单字段） */
    public static final String NODE_CONFIG_HANDLER_SOURCE = "handlerSource";
    /** 节点配置键：审批人 id 列表（地址本多选，限岗位+用户，';' 分隔；审批来源为 org 时有效） */
    public static final String NODE_CONFIG_HANDLER_IDS = "handlerIds";
    /** 节点配置键：审批人名称列表（与 handlerIds 一一对应，';' 分隔；审批来源为 org 时有效） */
    public static final String NODE_CONFIG_HANDLER_NAMES = "handlerNames";
    /** 节点配置键：审批人字段（文档实体字段属性名，审批来源为 field 时有效） */
    public static final String NODE_CONFIG_HANDLER_FIELD = "handlerField";
    /** 节点配置键：审批模式（single / countersign，默认 single） */
    public static final String NODE_CONFIG_APPROVE_MODE = "approveMode";
    /** 节点配置键：节点触发（fixed / condition，默认 fixed） */
    public static final String NODE_CONFIG_TRIGGER_TYPE = "triggerType";
    /** 节点配置键：触发条件（判断公式，节点触发为 condition 时必填） */
    public static final String NODE_CONFIG_CONDITION = "condition";
    /** 节点配置键：是否允许自动跳过（system / yes / no，默认 system） */
    public static final String NODE_CONFIG_AUTO_SKIP = "autoSkip";

    /** 节点配置键：是否允许连续跳过（system / yes / no，默认 system） */
    public static final String NODE_CONFIG_AUTO_CONTINUE_SKIP = "autoContinueSkip";

    /* ==================== 模板保存侧取值（P17-BE-006） ==================== */

    /** 审批操作标识：通过（审批节点 operations 必须包含且仅包含一个该操作） */
    public static final String OPERATE_TYPE_HANDLER_PASS = "handler_pass";

    /* ==================== 统一审批操作类型（operation/operate 的 operateType，14 类操作矩阵） ==================== */

    /** 操作类型：起草人提交（DRAFT / RETURN，flowChoice 可选） */
    public static final String OPERATE_TYPE_DRAFTER_SUBMIT = "drafter_submit";
    /** 操作类型：起草人取消（DRAFT / RETURN，D2 并入废弃语义） */
    public static final String OPERATE_TYPE_DRAFTER_CANCEL = "drafter_cancel";
    /** 操作类型：起草人收回（PROCESSING） */
    public static final String OPERATE_TYPE_DRAFTER_RETRACT = "drafter_retract";
    /** 操作类型：起草人催办（PROCESSING；P17-BE-011 落地引擎分发） */
    public static final String OPERATE_TYPE_DRAFTER_EXPEDITE = "drafter_expedite";
    // 操作类型 handler_pass（审批人通过）已在「模板保存侧取值」定义，此处直接复用
    /** 操作类型：审批人拒绝（PROCESSING；驳回目标校验由 P17-BE-009 落地） */
    public static final String OPERATE_TYPE_HANDLER_REJECT = "handler_reject";
    /** 操作类型：审批人抄送（PROCESSING；引擎分发由 P17-BE-012 落地） */
    public static final String OPERATE_TYPE_HANDLER_CC = "handler_cc";
    /** 操作类型：审批人转办（PROCESSING；引擎分发由 P17-BE-010 落地） */
    public static final String OPERATE_TYPE_HANDLER_TRANSFER = "handler_transfer";
    /** 操作类型：转办收回（PROCESSING；引擎分发由 P17-BE-010 落地） */
    public static final String OPERATE_TYPE_HANDLER_TRANSFER_RETRACT = "handler_transfer_retract";
    /** 操作类型：已审批人收回（PROCESSING；引擎分发由 P17-BE-010 落地） */
    public static final String OPERATE_TYPE_PROCESSED_RETRACT = "processed_retract";
    /** 操作类型：已审批人催办（PROCESSING；P17-BE-011 落地引擎分发） */
    public static final String OPERATE_TYPE_PROCESSED_EXPEDITE = "processed_expedite";
    /** 操作类型：管理员废弃（PROCESSING / RETURN / DRAFT / PAUSE） */
    public static final String OPERATE_TYPE_ADMIN_ABANDON = "admin_abandon";
    /** 操作类型：管理员跳转（PROCESSING / RETURN） */
    public static final String OPERATE_TYPE_ADMIN_JUMP = "admin_jump";
    /** 操作类型：管理员更换审批人（PROCESSING；引擎分发由 P17-BE-013 落地） */
    public static final String OPERATE_TYPE_ADMIN_CHANGE_HANDLER = "admin_change_handler";

    /* ==================== 业务错误码前缀（异常 message 前缀承载，见流程v1_7接口清单.md §10） ==================== */

    /** 操作类型不在 14 类取值内或暂未开放 */
    public static final String CS_PROCESS_OPERATE_TYPE_INVALID = "CS_PROCESS_OPERATE_TYPE_INVALID";
    /** 操作类型对应的必填参数缺失 */
    public static final String CS_PROCESS_OPERATE_PARAM_MISSING = "CS_PROCESS_OPERATE_PARAM_MISSING";
    /** 流程状态不满足该操作前置条件 */
    public static final String CS_PROCESS_OPERATE_STATE_CONFLICT = "CS_PROCESS_OPERATE_STATE_CONFLICT";
    /** 操作人身份不符（AuthorizationException FORBIDDEN 语义） */
    public static final String CS_PROCESS_OPERATE_FORBIDDEN = "CS_PROCESS_OPERATE_FORBIDDEN";
    /** 跳转/驳回/人工流向目标节点非法或不满足范围约束 */
    public static final String CS_PROCESS_NODE_TARGET_INVALID = "CS_PROCESS_NODE_TARGET_INVALID";
    /** 流程实例不存在或已删除 */
    public static final String CS_PROCESS_EXAMPLE_NOT_FOUND = "CS_PROCESS_EXAMPLE_NOT_FOUND";

    /* ==================== 流程实例状态（sys_process_example.field_status） ==================== */

    /** 流程状态：废弃 */
    public static final String EXAMPLE_STATUS_ABANDON = "ABANDON";
    /** 流程状态：草稿 */
    public static final String EXAMPLE_STATUS_DRAFT = "DRAFT";
    /** 流程状态：审批中 */
    public static final String EXAMPLE_STATUS_PROCESSING = "PROCESSING";
    /** 流程状态：暂停 */
    public static final String EXAMPLE_STATUS_PAUSE = "PAUSE";
    /** 流程状态：完成 */
    public static final String EXAMPLE_STATUS_FINISH = "FINISH";
    /** 流程状态：驳回 */
    public static final String EXAMPLE_STATUS_RETURN = "RETURN";

    /**
     * 是否在途流程（审批中 / 暂停 / 驳回）：
     * 供接入流程的业务模块判断业务下是否存在在途流程（如项目变更的互斥校验、中止时作废登记）。
     */
    public static boolean isInFlight(String status) {
        return EXAMPLE_STATUS_PROCESSING.equals(status)
                || EXAMPLE_STATUS_PAUSE.equals(status)
                || EXAMPLE_STATUS_RETURN.equals(status);
    }

    /* ==================== 流程实例节点状态（sys_process_example_node.field_status） ==================== */

    /** 节点状态：等待处理 */
    public static final String NODE_STATUS_WAITING = "WAITING";
    /** 节点状态：审批中 */
    public static final String NODE_STATUS_PROCESSING = "PROCESSING";
    /** 节点状态：完成 */
    public static final String NODE_STATUS_FINISH = "FINISH";
    /** 节点状态：跳过 */
    public static final String NODE_STATUS_PASS = "PASS";
    /** 节点状态：驳回 */
    public static final String NODE_STATUS_REJECT = "REJECT";

    /* ==================== 流程工作项状态（sys_process_example_workitem.field_status） ==================== */

    /** 工作项状态：审批中 */
    public static final String WORKITEM_STATUS_PROCESSING = "PROCESSING";
    /** 工作项状态：完成 */
    public static final String WORKITEM_STATUS_FINISH = "FINISH";
    /** 工作项状态：废弃 */
    public static final String WORKITEM_STATUS_ABANDON = "ABANDON";
    /** 工作项状态：暂停 */
    public static final String WORKITEM_STATUS_PAUSE = "PAUSE";

    /* ==================== 审批记录类型（sys_process_audit_note.field_handle_type） ==================== */

    /** 起草人提交 */
    public static final String AUDIT_TYPE_DRAFTER_SUBMIT = "DRAFTER_SUBMIT";
    /** 起草人收回 */
    public static final String AUDIT_TYPE_DRAFTER_BACK = "DRAFTER_BACK";
    /** 起草人废弃 */
    public static final String AUDIT_TYPE_DRAFTER_ABANDON = "DRAFTER_ABANDON";
    /** 起草人催办（v1_7 新增，P17-BE-011）：向当前节点全部 PROCESSING 工作项处理人发催办待阅 */
    public static final String AUDIT_TYPE_DRAFTER_EXPEDITE = "DRAFTER_EXPEDITE";
    /** 处理人通过 */
    public static final String AUDIT_TYPE_HANDLER_PASS = "HANDLER_PASS";
    /**
     * 处理人驳回（v1_7 新增，P17-BE-009）：新操作统一写本类型（可指定驳回目标 + resumeMode）；
     * {@link #AUDIT_TYPE_HANDLER_RETURN} 保留兼容历史数据，不再新增写入。
     */
    public static final String AUDIT_TYPE_HANDLER_REJECT = "HANDLER_REJECT";
    /** 处理人驳回（历史类型）：仅存量历史数据使用，v1_7 起新操作统一写 {@link #AUDIT_TYPE_HANDLER_REJECT} */
    public static final String AUDIT_TYPE_HANDLER_RETURN = "HANDLER_RETURN";
    /**
     * 审批人抄送（v1_7 新增，P17-BE-012）：当前处理人在途抄送其他用户（地址本多选），逐人发
     * READ 待阅（标题消费 {@link #SETTING_NOTIFY_CC_SUBJECT}），不改变工作项/节点/流程状态。
     */
    public static final String AUDIT_TYPE_HANDLER_CC = "HANDLER_CC";
    /**
     * 审批人转办（v1_7 新增，P17-BE-010）：原工作项 ABANDON，为被转办人生成
     * {@code field_from_workitem_id} 关联的新工作项，被转办人视为该序位实际处理人。
     */
    public static final String AUDIT_TYPE_HANDLER_TRANSFER = "HANDLER_TRANSFER";
    /**
     * 转办收回（v1_7 新增，P17-BE-010）：被转办工作项仍 PROCESSING 时原审批人收回，
     * 转办链仍 PROCESSING 的被转办工作项整链 ABANDON，并为原审批人生成新工作项（链路回退可逐级追溯）。
     */
    public static final String AUDIT_TYPE_HANDLER_TRANSFER_RETRACT = "HANDLER_TRANSFER_RETRACT";
    /**
     * 已审批人收回（v1_7 新增，P17-BE-010）：工作项所属节点仍 PROCESSING 时，
     * 已办结（FINISH）工作项收回到本人（重新持 PROCESSING 工作项），仅限正常流转场景。
     */
    public static final String AUDIT_TYPE_PROCESSED_RETRACT = "PROCESSED_RETRACT";
    /** 已审批人催办（v1_7 新增，P17-BE-011）：同起草人催办，发起人为已审批人（已办结/在途工作项处理人） */
    public static final String AUDIT_TYPE_PROCESSED_EXPEDITE = "PROCESSED_EXPEDITE";
    /** 自动跳过（重复审批人/起草人自动跳过时的留痕） */
    public static final String AUDIT_TYPE_AUTO_PASS = "AUTO_PASS";
    /** 管理员暂停 */
    public static final String AUDIT_TYPE_ADMIN_PAUSE = "ADMIN_PAUSE";
    /** 管理员恢复 */
    public static final String AUDIT_TYPE_ADMIN_RESUME = "ADMIN_RESUME";
    /** 管理员跳转 */
    public static final String AUDIT_TYPE_ADMIN_JUMP = "ADMIN_JUMP";
    /** 管理员作废 */
    public static final String AUDIT_TYPE_ADMIN_ABANDON = "ADMIN_ABANDON";
    /** 管理员办结 */
    public static final String AUDIT_TYPE_ADMIN_FINISH = "ADMIN_FINISH";
    /** 抄送节点自动留痕（P17-BE-012：抄送对象逐人发 READ 待阅后留痕；空抄送对象按跳过留痕） */
    public static final String AUDIT_TYPE_CC_AUTO = "CC_AUTO";
    /** 流程异常专用留痕（实例转 PAUSE，错误摘要入留痕） */
    public static final String AUDIT_TYPE_SYSTEM_ERROR = "SYSTEM_ERROR";

    /* ==================== 管理员特权操作类型（流程详情页特权操作弹窗） ==================== */

    /** 特权操作：跳转 */
    public static final String ADMIN_OPERATE_JUMP = "JUMP";
    /** 特权操作：终审通过 */
    public static final String ADMIN_OPERATE_FINISH = "FINISH";
    /** 特权操作：作废 */
    public static final String ADMIN_OPERATE_ABANDON = "ABANDON";
    /** 特权操作：暂停 */
    public static final String ADMIN_OPERATE_PAUSE = "PAUSE";
    /** 特权操作：恢复 */
    public static final String ADMIN_OPERATE_RESUME = "RESUME";

    /* ==================== 操作标识（process_template.${fieldModelName}.ops 字典 key） ==================== */

    /** 操作标识：允许修改附件（合同/项目等分类下已配置，节点开启后其审批人可上传/删除附件） */
    public static final String OP_ATTACHMENT = "attachment";

    /* ==================== 工作项配置（sys_process_example_workitem.field_config） ==================== */

    /** 工作项配置键：驳回后重新提交的流转方式（restart=重新流转 / backToNode=转回本节点） */
    public static final String WORKITEM_CONFIG_RESUME_MODE = "resumeMode";

    /** 工作项配置键：人工决策流向（目标节点 key，审批人通过/提交时指定，后提交覆盖先提交） */
    public static final String WORKITEM_CONFIG_FLOW_CHOICE = "flowChoice";

    /** 工作项配置键：驳回目标节点 key（v1_7 新增，驳回者工作项记录，供重新提交 backToNode 进入目标节点） */
    public static final String WORKITEM_CONFIG_REJECT_TARGET_KEY = "rejectTargetKey";

    /** 重新提交后流转方式：重新流转（从草稿节点后的首个节点重新流转） */
    public static final String RESUME_MODE_RESTART = "restart";
    /** 重新提交后流转方式：转回本节点（回到驳回的节点重新审批） */
    public static final String RESUME_MODE_BACK_TO_NODE = "backToNode";
}
