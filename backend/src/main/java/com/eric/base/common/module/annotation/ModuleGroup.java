package com.eric.base.common.module.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 模块组注解
 *
 * @author Eric
 * @date 2026/11/12
 * @description 标注在 Controller 类或接口方法上，表示该组接口受模块组开关控制；
 *              方法级注解覆盖类级注解。模块组关闭时已登录请求抛出
 *              {@link com.eric.base.common.exception.ModuleDisabledException}，
 *              经全局异常处理器返回 MODULE_DISABLED。仅定义注解本身，
 *              判定与拦截由 ModuleGroupInterceptor 承担。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ModuleGroup {

    /** 模块组标识，对应 common_module_group.field_key（必填） */
    String moduleGroupKey();
}
