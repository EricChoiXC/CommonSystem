package com.eric.base.sys.formula.service.engine;

import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.formula.service.engine.functions.system.SystemFunctionProvider;
import com.eric.base.sys.org.model.SysOrgUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公式只读执行上下文（F14-BE-004，方案 §2.3 / §6.3）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 公式求值的变量来源，由调用方（F14-BE-007 三件 Service 或 v1.7 流程引擎等服务内
 *              消费方）构造后传入 {@link FormulaEngine#execute}：
 *              <ul>
 *              <li>调用方变量：构造时<strong>防御性拷贝</strong>并经
 *                  {@link Collections#unmodifiableMap} 包装——不对外暴露可变引用，
 *                  公式无赋值/副作用能力（方案 §6.3）；构造后调用方修改原 Map 不影响本上下文；</li>
 *              <li>服务端注入系统变量：{@link #SYS_CURRENT_USER_ID}、{@link #SYS_CURRENT_DEPT_ID}、
 *                  {@link #SYS_CURRENT_ROLE}，出自 {@link UserUtil} 会话上下文（§6.3 不信任
 *                  前端传值）。调用方变量若与系统变量同名，服务端注入<strong>优先</strong>，
 *                  调用方值被忽略并输出 WARN 告警日志；</li>
 *              <li>未登录会话不注入系统变量——公式引用 {@code SYS_CURRENT_*} 得到缺失变量
 *                  （nil），由 IFNULL 兜底或函数空值拒绝确定性报错，与普通变量语义一致。</li>
 *              </ul>
 *              系统变量名以 {@code SYS_CURRENT_} 前缀命名（对齐 UI 文档「SYS_CURRENT_* 类
 *              系统变量不可被前端赋值覆盖」口径），与 SYSTEM 函数（{@code CURRENT_USER()} 等）
 *              互为补充：函数取展示值（用户名/部门名/角色串），变量取标识值（用户/部门 ID）。
 */
public class FormulaContext {

    private static final Logger log = LoggerFactory.getLogger(FormulaContext.class);

    /** 系统变量：当前登录用户 ID（sys_org_user.field_id） */
    public static final String SYS_CURRENT_USER_ID = "SYS_CURRENT_USER_ID";

    /** 系统变量：当前用户所属部门 ID（sys_org_user.field_parent_id） */
    public static final String SYS_CURRENT_DEPT_ID = "SYS_CURRENT_DEPT_ID";

    /** 系统变量：当前用户角色编码集合（逗号分隔、去重保序，口径同 CURRENT_ROLE 函数） */
    public static final String SYS_CURRENT_ROLE = "SYS_CURRENT_ROLE";

    /** 调用方变量（防御性拷贝 + 不可变包装） */
    private final Map<String, Object> callerVars;

    /** 服务端注入系统变量（不可变；本类唯一可信来源） */
    private final Map<String, Object> systemVariables;

    /** 合并只读视图（系统变量优先），getVars() 对外暴露的唯一变量表 */
    private final Map<String, Object> mergedVars;

    /**
     * 构造执行上下文（生产入口）：调用方变量 + {@link UserUtil} 服务端会话系统变量注入
     *
     * @param callerVars 调用方变量表，null 视为空表
     */
    public FormulaContext(Map<String, Object> callerVars) {
        this(callerVars, loadSystemVariables());
    }

    /**
     * 构造执行上下文（包内可见，供单测注入确定性系统变量，绕开静态会话依赖）
     *
     * @param callerVars     调用方变量表，null 视为空表
     * @param systemVariables 服务端注入系统变量，null 视为空表
     */
    FormulaContext(Map<String, Object> callerVars, Map<String, Object> systemVariables) {
        Map<String, Object> callerCopy = new LinkedHashMap<>(null == callerVars ? Map.of() : callerVars);
        Map<String, Object> systemCopy = new LinkedHashMap<>(null == systemVariables ? Map.of() : systemVariables);
        for (String key : systemCopy.keySet()) {
            if (callerCopy.containsKey(key)) {
                log.warn("调用方变量试图覆盖系统变量 [{}]，已忽略调用方值（服务端注入优先）", key);
            }
        }
        this.callerVars = Collections.unmodifiableMap(callerCopy);
        this.systemVariables = Collections.unmodifiableMap(systemCopy);
        Map<String, Object> merged = new LinkedHashMap<>(callerCopy);
        merged.putAll(systemCopy);
        this.mergedVars = Collections.unmodifiableMap(merged);
    }

    /** 按变量码取值：系统变量优先，缺失返回 null（缺失变量 = nil 语义，由 IFNULL 或函数空值拒绝兜底） */
    public Object getVariable(String code) {
        if (null == code) {
            return null;
        }
        if (systemVariables.containsKey(code)) {
            return systemVariables.get(code);
        }
        return callerVars.get(code);
    }

    /** 合并只读变量表（系统变量优先；不可变，put 等写操作抛 UnsupportedOperationException） */
    public Map<String, Object> getVars() {
        return mergedVars;
    }

    /** 服务端注入系统变量只读视图 */
    public Map<String, Object> getSystemVariables() {
        return systemVariables;
    }

    /**
     * 服务端会话系统变量装载（UserUtil 唯一来源，§6.3）：未登录返回空表，
     * 单值字段仅装载非空值（缺失变量 = nil，不注入占位）
     */
    private static Map<String, Object> loadSystemVariables() {
        Map<String, Object> system = new LinkedHashMap<>();
        SysOrgUser user = UserUtil.getCurrentUser();
        if (null == user) {
            return system;
        }
        if (null != user.getFieldId() && !user.getFieldId().isBlank()) {
            system.put(SYS_CURRENT_USER_ID, user.getFieldId());
        }
        if (null != user.getFieldParentId() && !user.getFieldParentId().isBlank()) {
            system.put(SYS_CURRENT_DEPT_ID, user.getFieldParentId());
        }
        String roleCodes = SystemFunctionProvider.CurrentRoleFunction.joinRoleCodes(user.getRoles());
        if (null != roleCodes) {
            system.put(SYS_CURRENT_ROLE, roleCodes);
        }
        return system;
    }
}
