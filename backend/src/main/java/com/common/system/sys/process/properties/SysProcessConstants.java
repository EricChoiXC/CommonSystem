package com.common.system.sys.process.properties;

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
 *              权限与公共配置依据 artificial/docs/设计文档/sys/process/流程核心业务.md。
 */
public class SysProcessConstants {

    private SysProcessConstants() {
    }

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

    /**
     * 公共配置中文描述（页面仅显示中文描述与值），key = "${field_type}.${field_key}"。
     * 顺序与 artificial/docs/设计文档/sys/process/流程核心业务.md「流程公共配置」保持一致。
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
    /** 节点类型：机器人节点 */
    public static final String NODE_TYPE_ROBORT = "ROBORT";

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

    /** 审批来源：组织架构（默认，直接取节点配置的审批人） */
    public static final String HANDLER_SOURCE_ORG = "org";
    /** 审批来源：表单字段（审批时读取文档实体字段值解析出组织架构作为审批人） */
    public static final String HANDLER_SOURCE_FIELD = "field";

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
    /** 处理人通过 */
    public static final String AUDIT_TYPE_HANDLER_PASS = "HANDLER_PASS";
    /** 处理人驳回 */
    public static final String AUDIT_TYPE_HANDLER_RETURN = "HANDLER_RETURN";
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

    /** 重新提交后流转方式：重新流转（从草稿节点后的首个节点重新流转） */
    public static final String RESUME_MODE_RESTART = "restart";
    /** 重新提交后流转方式：转回本节点（回到驳回的节点重新审批） */
    public static final String RESUME_MODE_BACK_TO_NODE = "backToNode";
}
