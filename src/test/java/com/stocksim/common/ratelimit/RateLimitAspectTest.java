package com.stocksim.common.ratelimit;

import com.stocksim.common.BizException;
import com.stocksim.common.ErrorCode;
import com.stocksim.common.context.UserContext;
import com.stocksim.common.properties.RateLimitProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    @Mock
    private RateLimiterClient rateLimiterClient;
    @Mock
    private ProceedingJoinPoint pjp;
    @Mock
    private RateLimit rateLimit;

    private RateLimitProperties properties;
    private RateLimitAspect aspect;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        aspect = new RateLimitAspect(rateLimiterClient, properties);
        UserContext.set(new UserContext.LoginUser(2L, "user01", 1));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void disabledProceedsWithoutAcquire() throws Throwable {
        properties.setEnabled(false);
        when(pjp.proceed()).thenReturn("ok");
        assertThat(aspect.around(pjp, rateLimit)).isEqualTo("ok");
        verify(rateLimiterClient, never()).tryAcquire(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void nullUserProceedsWithoutAcquire() throws Throwable {
        UserContext.clear();
        when(pjp.proceed()).thenReturn("ok");
        assertThat(aspect.around(pjp, rateLimit)).isEqualTo("ok");
        verify(rateLimiterClient, never()).tryAcquire(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void acquireSuccessProceedsAndSetsRateOnce() throws Throwable {
        when(rateLimit.type()).thenReturn(RateLimitType.ORDER);
        when(rateLimiterClient.tryAcquire("rl:ORDER:2")).thenReturn(true);
        when(pjp.proceed()).thenReturn("ok");
        assertThat(aspect.around(pjp, rateLimit)).isEqualTo("ok");
        verify(rateLimiterClient).setRate("rl:ORDER:2", 5);
        // 同 key 第二次不再 setRate（本地 Set 记已初始化）
        assertThat(aspect.around(pjp, rateLimit)).isEqualTo("ok");
        verify(rateLimiterClient, times(1)).setRate("rl:ORDER:2", 5);
    }

    @Test
    void acquireFailureThrows1004() throws Throwable {
        when(rateLimit.type()).thenReturn(RateLimitType.QUOTE);
        when(rateLimiterClient.tryAcquire("rl:QUOTE:2")).thenReturn(false);
        assertThatThrownBy(() -> aspect.around(pjp, rateLimit))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
        verify(pjp, never()).proceed();
        verify(rateLimiterClient).setRate("rl:QUOTE:2", 20);
    }

    @Test
    void concurrentFirstUseInitializesBeforeAcquire() throws Throwable {
        when(rateLimit.type()).thenReturn(RateLimitType.ORDER);
        AtomicBoolean initialized = new AtomicBoolean(false);
        CountDownLatch setRateStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(inv -> {
            setRateStarted.countDown();
            release.await();
            initialized.set(true);
            return null;
        }).when(rateLimiterClient).setRate(anyString(), anyLong());
        when(rateLimiterClient.tryAcquire(anyString())).thenAnswer(inv -> {
            if (!initialized.get()) {
                throw new IllegalStateException("RateLimiter is not initialized");
            }
            return true;
        });
        when(pjp.proceed()).thenReturn("ok");

        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                UserContext.set(new UserContext.LoginUser(2L, "user01", 1));
                try {
                    start.await();
                    return aspect.around(pjp, rateLimit);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                } finally {
                    UserContext.clear();
                }
            }));
        }
        start.countDown();
        setRateStarted.await();
        release.countDown();
        for (Future<Object> f : futures) {
            assertThat(f.get()).isEqualTo("ok");
        }
        pool.shutdown();
        verify(rateLimiterClient, times(1)).setRate("rl:ORDER:2", 5);
    }
}