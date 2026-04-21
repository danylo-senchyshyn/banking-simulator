package com.banking.account.integration;

import com.banking.account.domain.Account;
import com.banking.account.domain.AccountStatus;
import com.banking.account.domain.Currency;
import com.banking.account.repository.AccountRepository;
import com.banking.account.service.AccountService;
import com.banking.account.service.BalanceUpdateConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AccountServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("account_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockBean
    BalanceUpdateConsumer balanceUpdateConsumer;

    @Autowired
    AccountService accountService;

    @SpyBean
    AccountRepository accountRepository;

    @AfterEach
    void cleanUp() {
        accountRepository.deleteAll();
    }

    @Test
    void createAccount_persistsToDatabase() {
        UUID userId = UUID.randomUUID();

        Account account = accountService.create(userId, Currency.USD);

        assertThat(account.getId()).isNotNull();
        assertThat(account.getUserId()).isEqualTo(userId);
        assertThat(account.getCurrency()).isEqualTo(Currency.USD);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(accountRepository.findById(account.getId())).isPresent();
    }

    @Test
    void deposit_updatesBalanceInDatabase() {
        UUID userId = UUID.randomUUID();
        Account account = accountService.create(userId, Currency.EUR);

        accountService.deposit(account.getId(), userId, new BigDecimal("250.50"), null);

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualByComparingTo(new BigDecimal("250.50"));
    }

    @Test
    void deposit_duplicateIdempotencyKey_throwsConflict() {
        UUID userId = UUID.randomUUID();
        Account account = accountService.create(userId, Currency.USD);
        String idempotencyKey = "deposit-key-001";

        accountService.deposit(account.getId(), userId, new BigDecimal("100.00"), idempotencyKey);

        assertThatThrownBy(() -> accountService.deposit(account.getId(), userId, new BigDecimal("100.00"), idempotencyKey))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Duplicate request");
    }

    @Test
    void getBalance_cachedInRedis() {
        UUID userId = UUID.randomUUID();
        Account account = accountService.create(userId, Currency.UAH);
        accountService.deposit(account.getId(), userId, new BigDecimal("500.00"), null);

        // First call hits the repository
        BigDecimal first = accountService.getBalance(account.getId(), userId);
        // Second call should be served from cache
        BigDecimal second = accountService.getBalance(account.getId(), userId);

        assertThat(first).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(second).isEqualByComparingTo(new BigDecimal("500.00"));

        // findByIdAndUserId is called once by deposit, once by the first getBalance (which populates the cache).
        // The second getBalance is served from cache — so exactly 2 calls total, not 3.
        verify(accountRepository, times(2)).findByIdAndUserId(account.getId(), userId);
    }

    @Test
    void closeAccount_zeroBalance_setsStatusClosed() {
        UUID userId = UUID.randomUUID();
        Account account = accountService.create(userId, Currency.GBP);

        Account closed = accountService.close(account.getId(), userId);

        assertThat(closed.getStatus()).isEqualTo(AccountStatus.CLOSED);
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void closeAccount_nonZeroBalance_throwsConflict() {
        UUID userId = UUID.randomUUID();
        Account account = accountService.create(userId, Currency.USD);
        accountService.deposit(account.getId(), userId, new BigDecimal("50.00"), null);

        assertThatThrownBy(() -> accountService.close(account.getId(), userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot close account with non-zero balance");
    }
}
