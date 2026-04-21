package com.banking.account.service;

import com.banking.account.domain.Account;
import com.banking.account.domain.AccountStatus;
import com.banking.account.domain.Currency;
import com.banking.account.exception.AccountException;
import com.banking.account.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private MoneyAuditService moneyAuditService;

    @InjectMocks
    private AccountService accountService;

    private UUID userId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();
    }

    private Account buildAccount(BigDecimal balance, AccountStatus status) {
        Account account = Account.builder()
                .userId(userId)
                .currency(Currency.USD)
                .balance(balance)
                .status(status)
                .build();
        account.setId(accountId);
        return account;
    }

    // ===== create =====

    @Test
    void create_savesAndReturnsAccount() {
        Account saved = buildAccount(BigDecimal.ZERO, AccountStatus.ACTIVE);
        when(accountRepository.save(any())).thenReturn(saved);

        Account result = accountService.create(userId, Currency.USD);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getCurrency()).isEqualTo(Currency.USD);
        verify(accountRepository).save(any());
    }

    // ===== getMyAccounts =====

    @Test
    void getMyAccounts_returnsAccountsForUser() {
        Account acc = buildAccount(BigDecimal.valueOf(100), AccountStatus.ACTIVE);
        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(acc));

        List<Account> result = accountService.getMyAccounts(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(userId);
    }

    // ===== getAccount =====

    @Test
    void getAccount_found_returnsAccount() {
        Account acc = buildAccount(BigDecimal.ZERO, AccountStatus.ACTIVE);
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(acc));

        Account result = accountService.getAccount(accountId, userId);

        assertThat(result).isEqualTo(acc);
    }

    @Test
    void getAccount_notFound_throwsNotFound() {
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(accountId, userId))
                .isInstanceOf(AccountException.class)
                .satisfies(e -> assertThat(((AccountException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ===== deposit =====

    @Test
    void deposit_activeAccount_addsAmount() {
        Account account = buildAccount(BigDecimal.valueOf(100), AccountStatus.ACTIVE);
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Account result = accountService.deposit(accountId, userId, BigDecimal.valueOf(50), null);

        assertThat(result.getBalance()).isEqualByComparingTo("150");
    }

    @Test
    void deposit_closedAccount_throwsConflict() {
        Account account = buildAccount(BigDecimal.ZERO, AccountStatus.CLOSED);
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.deposit(accountId, userId, BigDecimal.valueOf(50), null))
                .isInstanceOf(AccountException.class)
                .satisfies(e -> assertThat(((AccountException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void deposit_accountNotFound_throwsNotFound() {
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.deposit(accountId, userId, BigDecimal.valueOf(50), null))
                .isInstanceOf(AccountException.class)
                .satisfies(e -> assertThat(((AccountException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ===== applyBalanceUpdate =====

    @Test
    void applyBalanceUpdate_debitsFromAndCreditsTo() {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        Account from = Account.builder().userId(userId).currency(Currency.USD)
                .balance(BigDecimal.valueOf(500)).status(AccountStatus.ACTIVE).build();
        from.setId(fromId);
        Account to = Account.builder().userId(userId).currency(Currency.USD)
                .balance(BigDecimal.valueOf(100)).status(AccountStatus.ACTIVE).build();
        to.setId(toId);

        when(accountRepository.findById(fromId)).thenReturn(Optional.of(from));
        when(accountRepository.findById(toId)).thenReturn(Optional.of(to));
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        accountService.applyBalanceUpdate(fromId, toId, BigDecimal.valueOf(200));

        assertThat(from.getBalance()).isEqualByComparingTo("300");
        assertThat(to.getBalance()).isEqualByComparingTo("300");
        verify(accountRepository, times(2)).save(any());
    }

    @Test
    void applyBalanceUpdate_fromAccountNotFound_throws() {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        when(accountRepository.findById(fromId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.applyBalanceUpdate(fromId, toId, BigDecimal.valueOf(100)))
                .isInstanceOf(AccountException.class)
                .satisfies(e -> assertThat(((AccountException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ===== close =====

    @Test
    void close_zeroBalance_closesAccount() {
        Account account = buildAccount(BigDecimal.ZERO, AccountStatus.ACTIVE);
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Account result = accountService.close(accountId, userId);

        assertThat(result.getStatus()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void close_nonZeroBalance_throwsConflict() {
        Account account = buildAccount(BigDecimal.valueOf(50), AccountStatus.ACTIVE);
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.close(accountId, userId))
                .isInstanceOf(AccountException.class)
                .satisfies(e -> assertThat(((AccountException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void close_alreadyClosed_throwsConflict() {
        Account account = buildAccount(BigDecimal.ZERO, AccountStatus.CLOSED);
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.close(accountId, userId))
                .isInstanceOf(AccountException.class)
                .satisfies(e -> assertThat(((AccountException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void close_accountNotFound_throwsNotFound() {
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.close(accountId, userId))
                .isInstanceOf(AccountException.class)
                .satisfies(e -> assertThat(((AccountException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
