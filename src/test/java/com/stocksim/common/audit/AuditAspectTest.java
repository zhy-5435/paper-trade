package com.stocksim.common.audit;

import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock
    private OperationAuditService auditService;
    @Mock
    private JoinPoint joinPoint;
    @Mock
    private OperationAudit operationAudit;

    @Test
    void afterReturningRecordsWithStringTargetId() {
        Object[] args = {"600000", "req"};
        when(joinPoint.getArgs()).thenReturn(args);
        when(operationAudit.type()).thenReturn(OperationType.STOCK_MAINTAIN);
        when(operationAudit.targetType()).thenReturn("stock");
        new AuditAspect(auditService).afterReturning(joinPoint, operationAudit, "result");
        verify(auditService).record(OperationType.STOCK_MAINTAIN, "stock", "600000", args);
    }

    @Test
    void afterReturningRecordsWithNullTargetIdWhenNoString() {
        Object[] args = {123};
        when(joinPoint.getArgs()).thenReturn(args);
        when(operationAudit.type()).thenReturn(OperationType.OTHER);
        when(operationAudit.targetType()).thenReturn("settlement");
        new AuditAspect(auditService).afterReturning(joinPoint, operationAudit, "result");
        verify(auditService).record(OperationType.OTHER, "settlement", null, args);
    }
}