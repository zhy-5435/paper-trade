package com.stocksim.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksim.common.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OperationAuditServiceTest {

    @Mock
    private OperationLogMapper operationLogMapper;

    private OperationAuditService service;

    @BeforeEach
    void setUp() {
        service = new OperationAuditService(operationLogMapper, new ObjectMapper());
        UserContext.set(new UserContext.LoginUser(1L, "admin", 2));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordInsertsFullFields() {
        service.record(OperationType.STOCK_MAINTAIN, "stock", "600000",
                new Object[]{Map.of("stockCode", "600000")});
        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        OperationLog saved = captor.getValue();
        assertThat(saved.getOperatorId()).isEqualTo(1L);
        assertThat(saved.getOperatorName()).isEqualTo("admin");
        assertThat(saved.getOperationType()).isEqualTo(2);
        assertThat(saved.getTargetType()).isEqualTo("stock");
        assertThat(saved.getTargetId()).isEqualTo("600000");
        assertThat(saved.getIp()).isEqualTo("10.0.0.1");
        assertThat(saved.getDetail()).contains("600000");
    }

    @Test
    void loginAuditMasksPasswordAndUsesLoginType() {
        service.recordLogin(2L, "user01",
                new Object[]{Map.of("username", "user01", "password", "secret")});
        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        OperationLog saved = captor.getValue();
        assertThat(saved.getOperatorId()).isEqualTo(2L);
        assertThat(saved.getOperationType()).isEqualTo(1);
        assertThat(saved.getDetail()).contains("***").doesNotContain("secret");
    }

    @Test
    void insertFailureDoesNotPropagate() {
        doThrow(new RuntimeException("DB 异常")).when(operationLogMapper).insert(any(OperationLog.class));
        assertThatCode(() -> service.record(OperationType.OTHER, "settlement", null, new Object[]{}))
                .doesNotThrowAnyException();
    }
}