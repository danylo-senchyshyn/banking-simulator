package com.banking.account.web.controller;

import com.banking.common.AppConstants;
import com.banking.account.domain.Account;
import com.banking.account.domain.AccountStatus;
import com.banking.account.domain.Currency;
import com.banking.account.exception.AccountException;
import com.banking.account.mapper.AccountMapper;
import com.banking.account.service.AccountService;
import com.banking.account.web.dto.AccountResponse;
import com.banking.account.web.dto.CreateAccountRequest;
import com.banking.account.web.dto.DepositRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    AccountService accountService;

    @MockBean
    AccountMapper accountMapper;

    private static final String USER_ID_HEADER = AppConstants.Headers.USER_ID;
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACCOUNT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private AccountResponse sampleResponse() {
        return new AccountResponse(
                ACCOUNT_ID,
                USER_ID,
                Currency.USD,
                AccountStatus.ACTIVE,
                BigDecimal.valueOf(1000),
                LocalDateTime.now()
        );
    }

    // =====================================================================
    // POST /api/accounts
    // =====================================================================

    @Test
    void createAccount_validRequest_returns201() throws Exception {
        AccountResponse response = sampleResponse();
        when(accountService.create(eq(USER_ID), eq(Currency.USD))).thenReturn(null);
        when(accountMapper.toResponse(any())).thenReturn(response);

        CreateAccountRequest request = new CreateAccountRequest(Currency.USD);

        mockMvc.perform(post("/api/v1/accounts")
                        .header(USER_ID_HEADER, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void createAccount_missingCurrency_returns400() throws Exception {
        // currency is null — violates @NotNull
        String body = "{}";

        mockMvc.perform(post("/api/v1/accounts")
                        .header(USER_ID_HEADER, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // =====================================================================
    // GET /api/accounts
    // =====================================================================

    @Test
    void getMyAccounts_validHeader_returns200WithList() throws Exception {
        AccountResponse response = sampleResponse();
        Account account = Account.builder().userId(USER_ID).currency(Currency.USD).build();
        when(accountService.getMyAccounts(USER_ID)).thenReturn(Collections.singletonList(account));
        when(accountMapper.toResponse(account)).thenReturn(response);

        mockMvc.perform(get("/api/v1/accounts")
                        .header(USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ACCOUNT_ID.toString()));
    }

    // =====================================================================
    // GET /api/accounts/{id}
    // =====================================================================

    @Test
    void getAccount_existingAccount_returns200() throws Exception {
        AccountResponse response = sampleResponse();
        when(accountService.getAccount(eq(ACCOUNT_ID), eq(USER_ID))).thenReturn(null);
        when(accountMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/accounts/{id}", ACCOUNT_ID)
                        .header(USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ACCOUNT_ID.toString()));
    }

    @Test
    void getAccount_notFound_returns404() throws Exception {
        when(accountService.getAccount(eq(ACCOUNT_ID), eq(USER_ID)))
                .thenThrow(AccountException.notFound("Account not found"));

        mockMvc.perform(get("/api/v1/accounts/{id}", ACCOUNT_ID)
                        .header(USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    // POST /api/accounts/{id}/deposit
    // =====================================================================

    @Test
    void deposit_validAmount_returns200() throws Exception {
        AccountResponse response = sampleResponse();
        when(accountService.deposit(eq(ACCOUNT_ID), eq(USER_ID), any(BigDecimal.class), any())).thenReturn(null);
        when(accountMapper.toResponse(any())).thenReturn(response);

        DepositRequest request = new DepositRequest(BigDecimal.valueOf(500));

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", ACCOUNT_ID)
                        .header(USER_ID_HEADER, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1000));
    }

    @Test
    void deposit_zeroAmount_returns400() throws Exception {
        // 0.00 violates @DecimalMin("0.01")
        DepositRequest request = new DepositRequest(BigDecimal.ZERO);

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", ACCOUNT_ID)
                        .header(USER_ID_HEADER, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =====================================================================
    // DELETE /api/accounts/{id}
    // =====================================================================

    @Test
    void closeAccount_activeAccount_returns200() throws Exception {
        AccountResponse response = new AccountResponse(
                ACCOUNT_ID, USER_ID, Currency.USD, AccountStatus.CLOSED,
                BigDecimal.ZERO, LocalDateTime.now()
        );
        when(accountService.close(eq(ACCOUNT_ID), eq(USER_ID))).thenReturn(null);
        when(accountMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(delete("/api/v1/accounts/{id}", ACCOUNT_ID)
                        .header(USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void closeAccount_nonZeroBalance_returns409() throws Exception {
        when(accountService.close(eq(ACCOUNT_ID), eq(USER_ID)))
                .thenThrow(AccountException.conflict("Account has non-zero balance"));

        mockMvc.perform(delete("/api/v1/accounts/{id}", ACCOUNT_ID)
                        .header(USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isConflict());
    }
}
