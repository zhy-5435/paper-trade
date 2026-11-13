package com.stocksim.account.controller;

import com.stocksim.account.dto.AccountResponse;
import com.stocksim.account.dto.FlowResponse;
import com.stocksim.account.dto.MoneyRequest;
import com.stocksim.account.service.AccountService;
import com.stocksim.common.PageResult;
import com.stocksim.common.Result;
import com.stocksim.common.context.UserContext;
import com.stocksim.position.service.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Tag(name = "资金账户")
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;
    private final PositionService positionService;

    public AccountController(AccountService accountService, PositionService positionService) {
        this.accountService = accountService;
        this.positionService = positionService;
    }

    @Operation(summary = "账户余额/冻结/可用/总资产（总资产=余额+持仓实时市值）")
    @GetMapping
    public Result<AccountResponse> account() {
        Long userId = UserContext.userId();
        AccountResponse account = accountService.getAccount(userId);
        BigDecimal totalAsset = account.balance().add(positionService.totalMarketValue(userId));
        return Result.ok(new AccountResponse(account.accountNo(), account.currency(), account.balance(),
                account.frozenAmount(), account.availableAmount(), totalAsset, account.status()));
    }

    @Operation(summary = "模拟入金（requestId 幂等）")
    @PostMapping("/deposit")
    public Result<FlowResponse> deposit(@Valid @RequestBody MoneyRequest request) {
        return Result.ok(accountService.deposit(UserContext.userId(), request));
    }

    @Operation(summary = "模拟出金（requestId 幂等）")
    @PostMapping("/withdraw")
    public Result<FlowResponse> withdraw(@Valid @RequestBody MoneyRequest request) {
        return Result.ok(accountService.withdraw(UserContext.userId(), request));
    }

    @Operation(summary = "资金流水分页")
    @GetMapping("/flows")
    public Result<PageResult<FlowResponse>> flows(@RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  @RequestParam(required = false) Integer flowType) {
        return Result.ok(accountService.listFlows(UserContext.userId(), page, size, flowType));
    }
}