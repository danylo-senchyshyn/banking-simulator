package com.banking.transaction.web.controller;

import com.banking.common.AppConstants;
import com.banking.transaction.domain.TransactionStatus;
import com.banking.transaction.exception.TransactionException;
import com.banking.transaction.mapper.TransactionMapper;
import com.banking.transaction.service.TransactionService;
import com.banking.transaction.web.dto.TransactionResponse;
import com.banking.transaction.web.dto.TransferRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransactionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    TransactionService transactionService;

    @MockBean
    TransactionMapper transactionMapper;

    private static final String USER_ID_HEADER        = AppConstants.Headers.USER_ID;
    private static final String IDEMPOTENCY_KEY_HEADER = AppConstants.Headers.IDEMPOTENCY_KEY;
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRANSACTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID FROM_ACCOUNT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID TO_ACCOUNT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String IDEMPOTENCY_KEY = "test-idempotency-key-001";

    private TransactionResponse sampleResponse() {
        return new TransactionResponse(
                TRANSACTION_ID,
                IDEMPOTENCY_KEY,
                USER_ID,
                FROM_ACCOUNT_ID,
                TO_ACCOUNT_ID,
                BigDecimal.valueOf(100),
                "USD",
                TransactionStatus.PENDING,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    // =====================================================================
    // POST /api/transactions/transfer
    // =====================================================================

    @Test
    void transfer_validRequest_returns202WithResponse() throws Exception {
        TransactionResponse response = sampleResponse();
        when(transactionService.transfer(eq(USER_ID), eq(IDEMPOTENCY_KEY), any(TransferRequest.class)))
                .thenReturn(null);
        when(transactionMapper.toResponse(any())).thenReturn(response);

        TransferRequest request = new TransferRequest(FROM_ACCOUNT_ID, TO_ACCOUNT_ID, BigDecimal.valueOf(100), "USD");

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header(USER_ID_HEADER, USER_ID.toString())
                        .header(IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(TRANSACTION_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void transfer_missingFromAccountId_returns400() throws Exception {
        // fromAccountId is null — violates @NotNull
        TransferRequest request = new TransferRequest(null, TO_ACCOUNT_ID, BigDecimal.valueOf(100), "USD");

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header(USER_ID_HEADER, USER_ID.toString())
                        .header(IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_duplicateIdempotencyKey_returns409() throws Exception {
        when(transactionService.transfer(eq(USER_ID), eq(IDEMPOTENCY_KEY), any(TransferRequest.class)))
                .thenThrow(TransactionException.conflict("Duplicate idempotency key"));

        TransferRequest request = new TransferRequest(FROM_ACCOUNT_ID, TO_ACCOUNT_ID, BigDecimal.valueOf(100), "USD");

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header(USER_ID_HEADER, USER_ID.toString())
                        .header(IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // =====================================================================
    // GET /api/transactions
    // =====================================================================

    @Test
    void getMyTransactions_returns200WithPage() throws Exception {
        when(transactionService.getMyTransactions(eq(USER_ID), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/transactions")
                        .header(USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // =====================================================================
    // GET /api/transactions/{id}
    // =====================================================================

    @Test
    void getTransaction_existingTransaction_returns200() throws Exception {
        TransactionResponse response = sampleResponse();
        when(transactionService.getTransaction(eq(TRANSACTION_ID), eq(USER_ID))).thenReturn(null);
        when(transactionMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/transactions/{id}", TRANSACTION_ID)
                        .header(USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TRANSACTION_ID.toString()))
                .andExpect(jsonPath("$.idempotencyKey").value(IDEMPOTENCY_KEY));
    }

    @Test
    void getTransaction_notFound_returns404() throws Exception {
        when(transactionService.getTransaction(eq(TRANSACTION_ID), eq(USER_ID)))
                .thenThrow(TransactionException.notFound("Transaction not found"));

        mockMvc.perform(get("/api/v1/transactions/{id}", TRANSACTION_ID)
                        .header(USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isNotFound());
    }
}
