package com.eric.base.common.exception;

/**
 * 模块组关闭异常
 *
 * @author Eric
 * @date 2026/11/12
 * @description 已登录请求命中已关闭模块组的 @ModuleGroup 注解时抛出，
 *              由全局异常处理器转换为统一响应返回给前端
 *              （code 由处理器固定写死 MODULE_DISABLED，本异常只携带模块组标识与用户可读文案）。
 */
public class ModuleDisabledException extends RuntimeException {

    /**
     * 已关闭的模块组标识，对应 common_module_group.field_key
     */
    private final String moduleGroupKey;

    public ModuleDisabledException(String moduleGroupKey) {
        super("该模块未开启，请联系管理员");
        this.moduleGroupKey = moduleGroupKey;
    }

    public String getModuleGroupKey() {
        return moduleGroupKey;
    }
}
