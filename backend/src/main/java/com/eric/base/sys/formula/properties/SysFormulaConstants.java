package com.eric.base.sys.formula.properties;

/**
 * 公式定义器模块常量
 *
 * @author Eric
 * @date 2026/11/18
 * @description 公式定义器模块接口标识（resolver 鉴权使用）、错误码、角色编码与资源限制常量，
 *              供 Controller / Resolver / Service / Engine 静态引用，禁止手写字符串与散落魔数。
 *              鉴权口径依据 docs/v1_4-公式定义器/公式定义器功能设计方案.md「§5.1 接口清单」：
 *              <ul>
 *              <li>管理六动作（list / load / save / update / enable / delete）：
 *                  SYS_FORMULA_ADMIN；</li>
 *              <li>组件四动作（options / functions / validate / simulate）：登录即可，
 *                  数据边界由取数函数委托目标 action 鉴权承担（方案 A7）。</li>
 *              </ul>
 */
public class SysFormulaConstants {

    /* ==================== 接口标识（resolver 鉴权使用） ==================== */

    /** 命名公式列表 */
    public static final String ACTION_LIST = "sys.formula.list";
    /** 命名公式详情 */
    public static final String ACTION_LOAD = "sys.formula.load";
    /** 新建命名公式 */
    public static final String ACTION_SAVE = "sys.formula.save";
    /** 编辑命名公式 */
    public static final String ACTION_UPDATE = "sys.formula.update";
    /** 命名公式启停 */
    public static final String ACTION_ENABLE = "sys.formula.enable";
    /** 删除命名公式 */
    public static final String ACTION_DELETE = "sys.formula.delete";
    /** 可用公式下拉（启用中的命名公式） */
    public static final String ACTION_OPTIONS = "sys.formula.options";
    /** 内置函数清单（九类函数元数据，供前端函数面板） */
    public static final String ACTION_FUNCTIONS = "sys.formula.functions";
    /** 公式校验（宽松 / 严格模式） */
    public static final String ACTION_VALIDATE = "sys.formula.validate";
    /** 公式模拟计算（资源限制内受限执行） */
    public static final String ACTION_SIMULATE = "sys.formula.simulate";

    /* ==================== 错误码（方案 §5.3 错误语义表） ==================== */

    /** 公式编码/ID 不存在，或命名公式已停用 */
    public static final String CS_FORMULA_NOT_FOUND = "CS_FORMULA_NOT_FOUND";
    /** 保存时公式编码重复 */
    public static final String CS_FORMULA_CODE_DUPLICATED = "CS_FORMULA_CODE_DUPLICATED";
    /** 表达式长度超上限 */
    public static final String CS_FORMULA_EXPRESSION_TOO_LONG = "CS_FORMULA_EXPRESSION_TOO_LONG";
    /** 词法/语法错误（附引擎错误信息与位置） */
    public static final String CS_FORMULA_SYNTAX_INVALID = "CS_FORMULA_SYNTAX_INVALID";
    /** 调用了未注册函数 */
    public static final String CS_FORMULA_FUNCTION_NOT_FOUND = "CS_FORMULA_FUNCTION_NOT_FOUND";
    /** 参数/返回类型与注册元数据或 expectedReturnType 不符 */
    public static final String CS_FORMULA_TYPE_MISMATCH = "CS_FORMULA_TYPE_MISMATCH";
    /** 变量码不合法（不符合标识符约束）或路径投影格式错误 */
    public static final String CS_FORMULA_VARIABLE_INVALID = "CS_FORMULA_VARIABLE_INVALID";
    /** 执行期失败（除零、空值拒绝、集合规模超限等） */
    public static final String CS_FORMULA_EXECUTE_FAILED = "CS_FORMULA_EXECUTE_FAILED";
    /** 执行超时 */
    public static final String CS_FORMULA_EXECUTE_TIMEOUT = "CS_FORMULA_EXECUTE_TIMEOUT";

    /* ==================== 资源限制（方案 §6.2，全部收口此处） ==================== */

    /** 表达式长度上限（超限抛 CS_FORMULA_EXPRESSION_TOO_LONG） */
    public static final int MAX_EXPRESSION_LENGTH = 4000;
    /** 变量个数上限（表达式引用变量数超限抛 CS_FORMULA_VARIABLE_INVALID） */
    public static final int MAX_VARIABLE_COUNT = 50;
    /** 变量值 JSON 序列化体积上限（字节，超限抛 CS_FORMULA_EXECUTE_FAILED） */
    public static final int MAX_VARS_SERIALIZE_BYTES = 65536;
    /** Aviator Options.MAX_LOOP_COUNT 封顶（循环迭代上限，防死循环） */
    public static final long MAX_LOOP_COUNT = 10000L;
    /** 执行超时（毫秒，独立执行线程 + Future 超时，超时抛 CS_FORMULA_EXECUTE_TIMEOUT） */
    public static final long EXECUTE_TIMEOUT_MILLIS = 5000L;
    /** 编译缓存 LRU 容量上限 */
    public static final int COMPILE_CACHE_CAPACITY = 256;

    /* ==================== 角色编码 ==================== */

    /** 公式管理权限码（roles.yml 由 F14-OPS-001 落地，权限码取 sys_org_role.field_code） */
    public static final String ROLE_SYS_FORMULA_ADMIN = "SYS_FORMULA_ADMIN";
}
