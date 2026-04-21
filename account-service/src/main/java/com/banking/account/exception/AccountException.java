package com.banking.account.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class AccountException extends ResponseStatusException {

    private AccountException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static AccountException notFound(String message) {
        return new AccountException(HttpStatus.NOT_FOUND, message);
    }

    public static AccountException forbidden() {
        return new AccountException(HttpStatus.FORBIDDEN, "Access denied");
    }

    public static AccountException conflict(String message) {
        return new AccountException(HttpStatus.CONFLICT, message);
    }

    public static AccountException badRequest(String message) {
        return new AccountException(HttpStatus.BAD_REQUEST, message);
    }
}
