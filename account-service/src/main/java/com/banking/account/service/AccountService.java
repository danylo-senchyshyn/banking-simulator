package com.banking.account.service;

import com.banking.account.domain.Account;
import com.banking.account.domain.AccountStatus;
import com.banking.account.domain.Currency;
import com.banking.account.exception.AccountException;
import com.banking.account.repository.AccountRepository;
import com.banking.common.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {


    private final AccountRepository accountRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final MoneyAuditService moneyAuditService;

    @Transactional
    public Account create(UUID userId, Currency currency) {
        log.info("Creating account: userId={}, currency={}", userId, currency);
        Account account = Account.builder()
                .userId(userId)
                .currency(currency)
                .build();
        Account saved = accountRepository.save(account);
        log.info("Account created: id={}, userId={}", saved.getId(), userId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Account> getMyAccounts(UUID userId) {
        return accountRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Account getAccount(UUID accountId, UUID userId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountException.notFound("Account not found"));
    }

    @Cacheable(value = AppConstants.Cache.BALANCES, key = "#accountId")
    @Transactional(readOnly = true)
    public BigDecimal getBalance(UUID accountId, UUID userId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountException.notFound("Account not found"))
                .getBalance();
    }

    @CacheEvict(value = AppConstants.Cache.BALANCES, key = "#accountId")
    @Transactional
    public Account deposit(UUID accountId, UUID userId, BigDecimal amount, String idempotencyKey) {
        if (idempotencyKey != null) {
            String redisKey = AppConstants.Redis.IDEMPOTENCY_DEPOSIT_PREFIX + idempotencyKey;
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey))) {
                log.warn("Duplicate deposit request detected: idempotencyKey={}", idempotencyKey);
                throw AccountException.conflict("Duplicate request");
            }
        }

        log.info("Deposit: accountId={}, userId={}, amount={}", accountId, userId, amount);
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountException.notFound("Account not found"));
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw AccountException.conflict("Cannot deposit to a closed account");
        }
        account.setBalance(account.getBalance().add(amount));
        Account saved = accountRepository.save(account);

        if (idempotencyKey != null) {
            stringRedisTemplate.opsForValue().set(AppConstants.Redis.IDEMPOTENCY_DEPOSIT_PREFIX + idempotencyKey, "processed", AppConstants.Redis.IDEMPOTENCY_TTL);
        }

        log.info("Deposit complete: accountId={}, newBalance={}", accountId, saved.getBalance());
        moneyAuditService.logDeposit(userId, accountId, amount, saved.getCurrency().name());
        return saved;
    }

    @Caching(evict = {
        @CacheEvict(value = AppConstants.Cache.BALANCES, key = "#fromAccountId"),
        @CacheEvict(value = AppConstants.Cache.BALANCES, key = "#toAccountId")
    })
    @Transactional
    public void applyBalanceUpdate(UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
        Account from = accountRepository.findById(fromAccountId)
                .orElseThrow(() -> AccountException.notFound("From account not found"));
        Account to = accountRepository.findById(toAccountId)
                .orElseThrow(() -> AccountException.notFound("To account not found"));

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        accountRepository.save(from);
        accountRepository.save(to);
    }

    @CacheEvict(value = AppConstants.Cache.BALANCES, key = "#accountId")
    @Transactional
    public Account close(UUID accountId, UUID userId) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountException.notFound("Account not found"));
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw AccountException.conflict("Account is already closed");
        }
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw AccountException.conflict("Cannot close account with non-zero balance");
        }
        account.setStatus(AccountStatus.CLOSED);
        return accountRepository.save(account);
    }
}
