package com.eric.base.sys.form.properties;

import java.util.Set;

/**
 * 自定义表单模块常量
 *
 * @author Eric
 * @date 2026/11/24
 * @description 自定义表单模块接口标识（resolver 鉴权使用）、错误码、角色编码、组件 type
 *              白名单与资源限制常量，供 Controller / Resolver / Service / Engine 静态引用，
 *              禁止手写字符串与散落魔数。口径依据（任务 F15-BE-001）：
 *              <ul>
 *              <li>ACTION_* 与错误码：docs/v1_5-自定义表单/自定义表单API接口设计分册.md
 *                  §6.1 / §7.1（逐字对齐，禁止编码变体）；</li>
 *              <li>资源限制常量：docs/v1_5-自定义表单/自定义表单功能详细设计.md §2.2
 *                  （数值为草案建议值，编码期可复核调整，语义与超限错误码不变）；</li>
 *              <li>组件 type 白名单：功能设计 §1.3 组件全集定稿清单（R03）+ ADR-10
 *                  （未注册 type / 超限 Schema 在保存期由结构校验器拒绝，
 *                  渲染期未知 type 兜底占位不阻断整表）。</li>
 *              </ul>
 */
public class SysFormConstants {

    /* ==================== 接口标识（resolver 鉴权使用，API 分册 §6.1） ==================== */

    /* ---------- 定义面（sys.form.define.*，9 个，准入 SYS_FORM_ADMIN） ---------- */

    /** 表单定义列表（POST /define/list） */
    public static final String ACTION_FORM_DEFINE_LIST = "sys.form.define.list";
    /** 新建表单（POST /define/save） */
    public static final String ACTION_FORM_DEFINE_SAVE = "sys.form.define.save";
    /** 表单详情（GET /define/load?fieldId=） */
    public static final String ACTION_FORM_DEFINE_LOAD = "sys.form.define.load";
    /** 更新表单（元数据 + 草稿 Schema，PATCH /define/update） */
    public static final String ACTION_FORM_DEFINE_UPDATE = "sys.form.define.update";
    /** 表单启停用（PATCH /define/enable，请求体 { fieldId, fieldEnable }） */
    public static final String ACTION_FORM_DEFINE_ENABLE = "sys.form.define.enable";
    /** 删除表单（DELETE /define/delete?fieldId=，存在实例数据禁删） */
    public static final String ACTION_FORM_DEFINE_DELETE = "sys.form.define.delete";
    /** 发布表单（POST /define/publish，草稿冻结为不可变版本行） */
    public static final String ACTION_FORM_DEFINE_PUBLISH = "sys.form.define.publish";
    /** 版本列表（GET /define/versions?fieldId=） */
    public static final String ACTION_FORM_DEFINE_VERSIONS = "sys.form.define.versions";
    /** 回滚版本（POST /define/rollback，重发布历史版本副本为新版本） */
    public static final String ACTION_FORM_DEFINE_ROLLBACK = "sys.form.define.rollback";

    /* ---------- 数据面（sys.form.data.*，6 个，登录即可（R06），归属由 Service 层判定） ---------- */

    /** 获取填写用 Schema（当前发布版本，GET /data/schema?formId=） */
    public static final String ACTION_FORM_DATA_SCHEMA = "sys.form.data.schema";
    /** 保存表单数据（POST /data/save，一期仅新增语义） */
    public static final String ACTION_FORM_DATA_SAVE = "sys.form.data.save";
    /** 独立校验入口（POST /data/validate，与 save 共用校验引擎、不落库） */
    public static final String ACTION_FORM_DATA_VALIDATE = "sys.form.data.validate";
    /** 数据详情 / 回显（GET /data/load?fieldId=，含实例锁定版本 Schema） */
    public static final String ACTION_FORM_DATA_LOAD = "sys.form.data.load";
    /** 数据列表（POST /data/list，归属强制过滤 + 分页） */
    public static final String ACTION_FORM_DATA_LIST = "sys.form.data.list";
    /** 删除数据（DELETE /data/delete?fieldId=，创建人或管理员） */
    public static final String ACTION_FORM_DATA_DELETE = "sys.form.data.delete";

    /* ==================== 错误码（API 分册 §7.1，16 项逐字对齐） ==================== */

    /** 表单编码保存期全局查重失败；承载 define/save */
    public static final String CS_FORM_CODE_DUPLICATED = "CS_FORM_CODE_DUPLICATED";
    /** 表单主档不存在；承载 define/load / update / enable / delete / publish / versions / rollback 与 data/schema / save / validate / list */
    public static final String CS_FORM_NOT_FOUND = "CS_FORM_NOT_FOUND";
    /** 表单已停用——填写入口与数据提交关闭（定义面与数据查询保留）；承载 data/schema / save / validate */
    public static final String CS_FORM_DISABLED = "CS_FORM_DISABLED";
    /** 无已发布版本（主档 DRAFT 未发布），填写与提交入口不可用；承载 data/schema / save / validate */
    public static final String CS_FORM_NO_PUBLISHED_VERSION = "CS_FORM_NO_PUBLISHED_VERSION";
    /** 无草稿版本行时直接发布（非法流转）；承载 define/publish */
    public static final String CS_FORM_NO_DRAFT = "CS_FORM_NO_DRAFT";
    /** Schema 结构校验失败（字段码非法 / 层级重复、type 白名单外、容器嵌套违规、明细表内嵌容器、级联条件非法、规则 operator / 动作 / 事件非法或 target 不存在等，逐条附中文原因）；承载 define/save / update / publish */
    public static final String CS_FORM_SCHEMA_INVALID = "CS_FORM_SCHEMA_INVALID";
    /** Schema 资源上限超限（字段总数 / 嵌套深度 / Schema 体积 / 规则总数 / 文本长度 / 明细行数上限）；承载 define/save / update / publish */
    public static final String CS_FORM_SCHEMA_LIMIT_EXCEEDED = "CS_FORM_SCHEMA_LIMIT_EXCEEDED";
    /** 版本状态非法流转 / 非法目标（回滚目标为 DRAFT 行；终态行内容更新的实现层防御性拒绝）；承载 define/rollback（防御性：define/update） */
    public static final String CS_FORM_VERSION_STATE_INVALID = "CS_FORM_VERSION_STATE_INVALID";
    /** 回滚目标版本行不存在；承载 define/rollback */
    public static final String CS_FORM_VERSION_NOT_FOUND = "CS_FORM_VERSION_NOT_FOUND";
    /** 表单存在实例数据禁删（不提供级联删除）；承载 define/delete */
    public static final String CS_FORM_HAS_DATA = "CS_FORM_HAS_DATA";
    /** 提交数据含 Schema 字段白名单外未知字段（任意层级，含明细行内未知键，拒绝不做静默裁剪）；承载 data/save / validate */
    public static final String CS_FORM_UNKNOWN_FIELD = "CS_FORM_UNKNOWN_FIELD";
    /** 数据基础 / 动态校验失败（必填 / 类型 / 长度 / 枚举选项值 / 明细行数超限 / 条件必填与条件校验，message 前缀 + 首条中文摘要）；承载 data/save（data/validate 的字段级结果以 data.errors 结构化承载、不抛本码） */
    public static final String CS_FORM_DATA_INVALID = "CS_FORM_DATA_INVALID";
    /** 数据实例不存在；承载 data/load / delete */
    public static final String CS_FORM_DATA_NOT_FOUND = "CS_FORM_DATA_NOT_FOUND";
    /** 数据归属越权（非本人且非管理员，code = FORBIDDEN + message 前缀）；承载 data/load / delete */
    public static final String CS_FORM_DATA_FORBIDDEN = "CS_FORM_DATA_FORBIDDEN";
    /** 级联选项复核不通过：提交值 ∉ 父项当前条件允许选项集（R08 第 3 条服务端复核）；承载 data/save / validate */
    public static final String CS_FORM_CASCADE_OPTION_INVALID = "CS_FORM_CASCADE_OPTION_INVALID";
    /** 服务端规则复算熔断：单次求值动作执行次数达 MAX_RULE_EXECUTIONS_PER_CYCLE 或规则链深度达 MAX_RULE_CHAIN_DEPTH；承载 data/save / validate */
    public static final String CS_FORM_RULE_CYCLE_EXCEEDED = "CS_FORM_RULE_CYCLE_EXCEEDED";

    /* ==================== 资源限制（功能设计 §2.2，全部收口此处） ==================== */

    /**
     * 单表单字段总数上限（含容器内字段与明细行内字段，防 Schema 爆炸；
     * 超限抛 CS_FORM_SCHEMA_LIMIT_EXCEEDED。数值为 §2.2 草案建议值，
     * 编码期可复核调整，语义与超限错误码不变）
     */
    public static final int MAX_FIELD_COUNT = 200;
    /**
     * 字段最大嵌套深度（顶层字段 = 1，容器内字段 = 容器链深 + 1；R04：明细表内禁容器；
     * 超限抛 CS_FORM_SCHEMA_LIMIT_EXCEEDED。数值为 §2.2 草案建议值，
     * 编码期可复核调整，语义与超限错误码不变）
     */
    public static final int MAX_NEST_DEPTH = 3;
    /**
     * field_schema_json 序列化体积上限（字符数，512 KB；应用层校验，先例 formula 表达式
     * TEXT 应用层口径；超限抛 CS_FORM_SCHEMA_LIMIT_EXCEEDED。数值为 §2.2 草案建议值，
     * 编码期可复核调整，语义与超限错误码不变）
     */
    public static final int MAX_SCHEMA_JSON_LENGTH = 524288;
    /**
     * 单表单规则总数上限（超限抛 CS_FORM_SCHEMA_LIMIT_EXCEEDED。数值为 §2.2 草案建议值，
     * 编码期可复核调整，语义与超限错误码不变）
     */
    public static final int MAX_RULE_COUNT = 100;
    /**
     * 单次交互（一次事件引发的规则风暴）动作执行次数上限，触发熔断（功能设计 §3.5）；
     * 服务端复算同语义，超限抛 CS_FORM_RULE_CYCLE_EXCEEDED。数值为 §2.2 草案建议值，
     * 编码期可复核调整，语义与超限错误码不变
     */
    public static final int MAX_RULE_EXECUTIONS_PER_CYCLE = 50;
    /**
     * 规则链深度上限（动作引发的后续规则级联层数，超限熔断；服务端复算同语义，
     * 超限抛 CS_FORM_RULE_CYCLE_EXCEEDED。数值为 §2.2 草案建议值，
     * 编码期可复核调整，语义与超限错误码不变）
     */
    public static final int MAX_RULE_CHAIN_DEPTH = 5;
    /**
     * 单明细表行数上限（服务端校验 + 前端行数上限提示，props.rowMax 不得超过该常量；
     * 超限抛 CS_FORM_SCHEMA_LIMIT_EXCEEDED。数值为 §2.2 草案建议值，
     * 编码期可复核调整，语义与超限错误码不变）
     */
    public static final int MAX_DETAIL_ROWS = 200;
    /**
     * 单行 / 多行文本 maxlength 应用层上限（超限抛 CS_FORM_SCHEMA_LIMIT_EXCEEDED。
     * 数值为 §2.2 草案建议值，编码期可复核调整，语义与超限错误码不变）
     */
    public static final int MAX_TEXT_LENGTH = 2000;

    /* ==================== 组件 type 白名单（功能设计 §1.3 组件全集定稿清单 R03 + ADR-10） ==================== */

    /**
     * 字段码约束正则（功能设计 §2.1：对齐 F14 变量码约束，保证 ${code} 映射无歧义可逆；
     * 同一数据层级内唯一，明细行内字段码可与主表同码但行内必须唯一）
     */
    public static final String FIELD_CODE_REGEX = "[a-zA-Z_][a-zA-Z0-9_]*";

    /**
     * 一期可进 Schema fields 的组件 type 全集（18 个 = 数据类 12 + 内容类 4 + 布局类 2，R03）：
     * 结构校验器按白名单拒绝未注册 type（ADR-10，超出一期全集即 CS_FORM_SCHEMA_INVALID）。
     * 控制类 permission 仅协议位不进 fields；代码块 code 随 R02 一期不执行——二者入
     * {@link #REJECTED_TYPES} 给专门文案。
     */
    public static final Set<String> ALLOWED_FIELD_TYPES = Set.of(
            // 数据类（12）
            "input", "textarea", "number", "choice", "select", "date", "switch",
            "cascade", "formula", "datasource", "addressbook", "attachment",
            // 内容类（4）
            "detailTable", "table", "text", "image",
            // 布局类（2，R03 一期新增）
            "group", "grid");

    /**
     * 数据类组件 type 全集（12 个，功能设计 §1.3）：级联选择器 parentField「限定已配置的
     * 数据类字段」（R08 第 4 条，不含明细表容器）与明细行内组件判定（R04）共用。
     */
    public static final Set<String> DATA_FIELD_TYPES = Set.of(
            "input", "textarea", "number", "choice", "select", "date", "switch",
            "cascade", "formula", "datasource", "addressbook", "attachment");

    /**
     * 明细表行内允许的组件 type 全集（R04：行内仅数据类组件，含行内 formula——formula 属
     * 数据类 12 之一，故取值域与 {@link #DATA_FIELD_TYPES} 一致；行内禁任何容器组件与
     * 明细表，二期放开多级嵌套时仅放宽此常量与校验规则，协议结构不变）
     */
    public static final Set<String> ALLOWED_DETAIL_CHILD_TYPES = DATA_FIELD_TYPES;

    /**
     * 一期拒绝进 Schema 的组件 type 集（结构校验器命中即给专门拒绝文案，ADR-10 / R02 / R03）：
     * <ul>
     * <li>code：代码块组件，R02 裁决一期仅预留协议位、不落地执行引擎（二期 GraalJS/受限
     *     解释器隔离执行），结构校验器拒绝其出现在 Schema 中（功能设计 §3.7）；</li>
     * <li>permission：权限组件，仅登记 type 与元数据协议位、不注册运行时物料、不进
     *     Schema fields，其消费形态（字段可见性与可编辑性）随 v1.8 BPM 由流程侧下发
     *     字段权限覆盖（功能设计 §1.3 / §6.6）；</li>
     * <li>map / relation / serialNumber / richtext / tabs / collapse：二期组件
     *     （地图 / 关联数据 / 自动编号 / 富文本 / 标签页 / 折叠面板），一期不注册。</li>
     * </ul>
     */
    public static final Set<String> REJECTED_TYPES = Set.of(
            "code", "permission", "map", "relation", "serialNumber", "richtext", "tabs", "collapse");

    /* ==================== 角色编码 ==================== */

    /** 自定义表单管理员权限码（功能设计 §12.1 定稿：一期仅此 1 个，不增设 SYS_FORM_DATA_ADMIN；roles.yml 由 F15-OPS-001 落地，权限码取 sys_org_role.field_code） */
    public static final String ROLE_SYS_FORM_ADMIN = "SYS_FORM_ADMIN";
}
