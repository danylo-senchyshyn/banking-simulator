package com.banking.transaction.web.controller;

import com.banking.transaction.mapper.TransactionMapper;
import com.banking.transaction.service.TransactionService;
import com.banking.transaction.web.api.TransactionApi;
import com.banking.transaction.web.dto.TransactionResponse;
import com.banking.transaction.web.dto.TransferRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TransactionController implements TransactionApi {

    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;

    @Override
    public ResponseEntity<TransactionResponse> transfer(String userId, String idempotencyKey, TransferRequest request) {
        var transaction = transactionService.transfer(UUID.fromString(userId), idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(transactionMapper.toResponse(transaction));
    }

    @Override
    public ResponseEntity<Page<TransactionResponse>> getMyTransactions(String userId, Pageable pageable) {
        Page<TransactionResponse> page = transactionService.getMyTransactions(UUID.fromString(userId), pageable)
                .map(transactionMapper::toResponse);
        return ResponseEntity.ok(page);
    }

    @Override
    public ResponseEntity<TransactionResponse> getTransaction(String userId, UUID transactionId) {
        var transaction = transactionService.getTransaction(transactionId, UUID.fromString(userId));
        return ResponseEntity.ok(transactionMapper.toResponse(transaction));
    }
}
