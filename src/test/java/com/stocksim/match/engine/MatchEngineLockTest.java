package com.stocksim.match.engine;

import com.stocksim.common.BizNoGenerator;
import com.stocksim.common.lock.LockProvider;
import com.stocksim.match.mapper.DealRecordMapper;
import com.stocksim.match.notify.DealNotify;
import com.stocksim.order.mapper.TradeOrderMapper;
import com.stocksim.stock.service.StockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchEngineLockTest {

    @Mock
    private TradeOrderMapper tradeOrderMapper;
    @Mock
    private DealRecordMapper dealRecordMapper;
    @Mock
    private StockService stockService;
    @Mock
    private DealNotify dealNotify;
    @Mock
    private BizNoGenerator bizNoGenerator;
    @Mock
    private LockProvider lockProvider;
    @Mock
    private Lock lock;

    @Test
    void 撮合锁超时留单不回源不撮合() throws InterruptedException {
        when(lockProvider.stockLock("600000")).thenReturn(lock);
        when(lock.tryLock(3, TimeUnit.SECONDS)).thenReturn(false);
        MatchEngine engine = new MatchEngine(tradeOrderMapper, dealRecordMapper, stockService,
                dealNotify, bizNoGenerator, new DealSeq(), lockProvider);

        engine.onNewOrder("600000", "B1");

        verifyNoInteractions(tradeOrderMapper, dealNotify, stockService);
    }
}