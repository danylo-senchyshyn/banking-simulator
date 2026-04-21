package com.banking.transaction.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class TransactionException extends ResponseStatusException {

    private TransactionException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static TransactionException notFound(String reason) {
        return new TransactionException(HttpStatus.NOT_FOUND, reason);
    }

    public static TransactionException conflict(String reason) {
        return new TransactionException(HttpStatus.CONFLICT, reason);
    }

    public static TransactionException badRequest(String reason) {
        return new TransactionException(HttpStatus.BAD_REQUEST, reason);
    }
}
