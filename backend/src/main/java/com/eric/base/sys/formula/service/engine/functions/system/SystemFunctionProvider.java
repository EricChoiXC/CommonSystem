package com.eric.base.sys.formula.service.engine.functions.system;

import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.formula.enums.SysFormulaFunctionCategory;
import com.eric.base.sys.formula.enums.SysFormulaReturnType;
import com.eric.base.sys.formula.service.engine.functions.FormulaFunctions;
import com.eric.base.sys.formula.spi.FormulaFunctionDefinition;
import com.eric.base.sys.formula.spi.FormulaFunctionProvider;
import com.eric.base.sys.org.model.SysOrgUser;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorObject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SYSTEM 会话上下文函数 Provider（方案 §2.2 首批：CURRENT_USER/CURRENT_DEPT/CURRENT_ROLE）
 *
 * @author Eric
 * @date 2026/11/18
 * @description 内置函数注册单元之一。安全语义（方案 §6.3）：全部无参函数，
 *              只从服务端 {@link UserUtil} 会话上下文取值，不接受任何入参——
 *              公式引擎的变量表中即使被调用方注入 CURRENT_USER 等同名变量也不生效
 *              （函数调用优先于变量解析）。无会话时返回 null（由 IFNULL 兜底）。
 */
@Component
public class SystemFunctionProvider implements FormulaFunctionProvider {

    @Override
    public List<FormulaFunctionDefinition> getFunctions() {
        List<FormulaFunctionDefinition> functions = new ArrayList<>();
        functions.add(FormulaFunctions.definition("CURRENT_USER", "当前用户", SysFormulaFunctionCategory.SYSTEM,
                "CURRENT_USER()：当前登录用户名称（sys_org_element.field_name）；无入参，"
                        + "值出自服务端会话（UserUtil），不信任任何前端传值；未登录返回 null",
                SysFormulaReturnType.STRING, new CurrentUserFunction()));
        functions.add(FormulaFunctions.definition("CURRENT_DEPT", "当前部门", SysFormulaFunctionCategory.SYSTEM,
                "CURRENT_DEPT()：当前用户所属部门名称（用户主组织 field_parent_name）；无入参，"
                        + "值出自服务端会话；未登录或无部门返回 null",
                SysFormulaReturnType.STRING, new CurrentDeptFunction()));
        functions.add(FormulaFunctions.definition("CURRENT_ROLE", "当前角色", SysFormulaFunctionCategory.SYSTEM,
                "CURRENT_ROLE()：当前登录用户的角色编码集合（逗号分隔，去重保序）；"
                        + "无入参，值出自服务端会话；未登录或无角色返回 null",
                SysFormulaReturnType.STRING, new CurrentRoleFunction()));
        return functions;
    }

    /** CURRENT_USER()：当前登录用户名 */
    public static class CurrentUserFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "CURRENT_USER";
        }

        @Override
        public AviatorObject call(Map<String, Object> env) {
            SysOrgUser user = UserUtil.getCurrentUser();
            return FormulaFunctions.wrap(null == user ? null : user.getFieldName());
        }
    }

    /** CURRENT_DEPT()：当前用户所属部门名 */
    public static class CurrentDeptFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "CURRENT_DEPT";
        }

        @Override
        public AviatorObject call(Map<String, Object> env) {
            SysOrgUser user = UserUtil.getCurrentUser();
            return FormulaFunctions.wrap(null == user ? null : user.getFieldParentName());
        }
    }

    /** CURRENT_ROLE()：角色编码集合（去重保序，逗号分隔） */
    public static class CurrentRoleFunction extends AbstractFunction {

        @Override
        public String getName() {
            return "CURRENT_ROLE";
        }

        @Override
        public AviatorObject call(Map<String, Object> env) {
            SysOrgUser user = UserUtil.getCurrentUser();
            return FormulaFunctions.wrap(null == user ? null : joinRoleCodes(user.getRoles()));
        }

        /**
         * 角色编码集合拼接：去重保序（LinkedHashSet）、空白编码过滤；
         * 全空返回 null（调用方包装为 AviatorNil）
         */
        public static String joinRoleCodes(List<com.eric.base.sys.org.model.SysOrgRole> roles) {
            if (null == roles || roles.isEmpty()) {
                return null;
            }
            Set<String> codes = new LinkedHashSet<>();
            roles.forEach(role -> {
                if (null != role && null != role.getFieldCode() && !role.getFieldCode().isBlank()) {
                    codes.add(role.getFieldCode().trim());
                }
            });
            return codes.isEmpty() ? null : String.join(",", codes);
        }
    }
}
