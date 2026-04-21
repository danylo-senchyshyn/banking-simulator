package com.banking.account.web.controller;

import com.banking.account.mapper.AccountMapper;
import com.banking.account.service.AccountService;
import com.banking.account.web.api.AccountApi;
import com.banking.account.web.dto.AccountResponse;
import com.banking.account.web.dto.CreateAccountRequest;
import com.banking.account.web.dto.DepositRequest;
import com.banking.common.AppConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AccountController implements AccountApi {

    private final AccountService accountService;
    private final AccountMapper accountMapper;

    @Override
    public ResponseEntity<AccountResponse> createAccount(String userId, CreateAccountRequest request) {
        var account = accountService.create(UUID.fromString(userId), request.currency());
        return ResponseEntity.status(HttpStatus.CREATED).body(accountMapper.toResponse(account));
    }

    @Override
    public ResponseEntity<List<AccountResponse>> getMyAccounts(String userId) {
        var accounts = accountService.getMyAccounts(UUID.fromString(userId))
                .stream().map(accountMapper::toResponse).toList();
        return ResponseEntity.ok(accounts);
    }

    @Override
    public ResponseEntity<AccountResponse> getAccount(String userId, UUID accountId) {
        var account = accountService.getAccount(accountId, UUID.fromString(userId));
        return ResponseEntity.ok(accountMapper.toResponse(account));
    }

    @Override
    public ResponseEntity<BigDecimal> getBalance(String userId, UUID accountId) {
        var balance = accountService.getBalance(accountId, UUID.fromString(userId));
        return ResponseEntity.ok(balance);
    }

    @Override
    public ResponseEntity<AccountResponse> deposit(
            String userId,
            UUID accountId,
            DepositRequest request,
            @RequestHeader(value = AppConstants.Headers.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        var account = accountService.deposit(accountId, UUID.fromString(userId), request.amount(), idempotencyKey);
        return ResponseEntity.ok(accountMapper.toResponse(account));
    }

    @Override
    public ResponseEntity<AccountResponse> closeAccount(String userId, UUID accountId) {
        var account = accountService.close(accountId, UUID.fromString(userId));
        return ResponseEntity.ok(accountMapper.toResponse(account));
    }
}
