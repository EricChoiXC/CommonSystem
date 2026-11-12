package com.eric.base.sys.log.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 *
 * @author Eric
 * @date 2026/8/8
 * @description 标注在 Service 方法上，由 {@code OperationLogAspect} 在方法执行成功后
 *              自动采集入参（业务文档 ID）、执行人、结果并落库，适用于 save / update / delete
 *              等统一动作，侵入最小。业务文档 ID 自动从方法参数提取：优先取 BaseModel 参数的
 *              fieldId，其次取参数名为 fieldId / documentId / id 的 String 参数。
 *              <p>示例：{@code @OperationLog(module = "SYS-ORG", action = "save",
 *              remark = "#body.fieldName")}</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /**
     * 模块（MODEL_KEY，如 SYS-ORG），取值来自各模块 application.yml 的 MODEL_KEY
     */
    String module();

    /**
     * 动作（save/update/delete/submit/import/export/download/changePwd 等）
     */
    String action();

    /**
     * 备注（可选）：支持 SpEL 表达式（以 # 开头，可引用方法参数，如 #body.fieldName），
     * 解析失败时回退原字符串
     */
    String remark() default "";
}
