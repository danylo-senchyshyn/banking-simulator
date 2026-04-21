package com.banking.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class AuthException extends ResponseStatusException {
    public AuthException(HttpStatus status, String message) {
        super(status, message);
    }

    public static AuthException emailTaken() {
        return new AuthException(HttpStatus.CONFLICT, "Email already in use");
    }

    public static AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    public static AuthException accountBlocked() {
        return new AuthException(HttpStatus.FORBIDDEN, "Account is blocked");
    }

    public static AuthException tooManyAttempts() {
        return new AuthException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Try again in 15 minutes");
    }

    public static AuthException invalidToken() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
    }

    public static AuthException userNotFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "User not found");
    }

    public static AuthException wrongPassword() {
        return new AuthException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
    }

    public static AuthException duplicateRequest(String message) {
        return new AuthException(HttpStatus.CONFLICT, message);
    }
}
