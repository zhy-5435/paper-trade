package com.stocksim.common.audit;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 审计切面（Q9）：@AfterReturning 仅业务成功后落库（afterCompletion 失败路径也执行，不可用）。
 * 操作人/ip/detail 由 OperationAuditService 组装；审计失败不影响业务。
 */
@Aspect
@Component
public class AuditAspect {

    private final OperationAuditService auditService;

    public AuditAspect(OperationAuditService auditService) {
        this.auditService = auditService;
    }

    @AfterReturning(pointcut = "@annotation(operationAudit)", returning = "result")
    public void afterReturning(JoinPoint joinPoint, OperationAudit operationAudit, Object result) {
        Object[] args = joinPoint.getArgs();
        auditService.record(operationAudit.type(), operationAudit.targetType(), extractTargetId(args), args);
    }

    private String extractTargetId(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof String s) {
                return s;
            }
        }
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            for (String accessor : new String[]{"stockCode", "code", "orderNo"}) {
                try {
                    Method method = arg.getClass().getMethod(accessor);
                    Object value = method.invoke(arg);
                    if (value != null) {
                        return value.toString();
                    }
                } catch (ReflectiveOperationException ignore) {
                    // 非记录 accessor，尝试下一个
                }
            }
        }
        return null;
    }
}