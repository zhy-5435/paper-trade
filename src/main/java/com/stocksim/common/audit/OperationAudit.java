package com.stocksim.common.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计标记（Q9）：切面 @AfterReturning 仅在业务成功后落 sys_operation_log。
 * 登录审计不走此注解（UserContext 尚未设置），由 auth 内显式 recordLogin。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationAudit {

    OperationType type();

    String targetType() default "";
}