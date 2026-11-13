package com.eric.base.sys.datasource.enums;

import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/**
 * 数据源固定条件运行时变量
 *
 * @author Eric
 * @date 2026/11/13
 * @description 固定条件 value 支持 ${xxx} 占位符，执行期由 SysDatasourceService 解析；
 *              变量随执行上下文求值（如当前登录人），保证「我创建的」按执行者动态翻译。
 */
public enum SysDatasourceVar {

    /** 当前登录人 ID（「我创建的」条件载体） */
    CURRENT_USER_ID(() -> {
        SysOrgUser user = UserUtil.getCurrentUser();
        return null == user ? null : user.getFieldId();
    }),
    /** 当天日期 yyyy-MM-dd */
    CURRENT_DATE(() -> LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)),
    /** 当前时间 yyyy-MM-dd HH:mm:ss */
    CURRENT_DATETIME(() -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

    private final Supplier<String> resolver;

    SysDatasourceVar(Supplier<String> resolver) {
        this.resolver = resolver;
    }

    /** 变量占位符形态：${CURRENT_USER_ID} */
    public String placeholder() {
        return "${" + name() + "}";
    }

    /** 求值；未登录时 CURRENT_USER_ID 求值为 null，由调用方走 UNAUTHORIZED */
    public String resolve() {
        return resolver.get();
    }

    /**
     * 判断文本是否本变量占位符
     */
    public boolean matches(String text) {
        return null != text && placeholder().equals(text.trim());
    }
}
