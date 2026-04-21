package com.banking.auth.web.controller;

import com.banking.auth.domain.User;
import com.banking.auth.service.UserService;
import com.banking.auth.web.api.UserApi;
import com.banking.auth.web.dto.ChangePasswordRequest;
import com.banking.auth.web.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserService userService;

    @Override
    public ResponseEntity<UserResponse> getProfile(User user) {
        return ResponseEntity.ok(userService.getProfile(user));
    }

    @Override
    public ResponseEntity<Void> changePassword(User user, ChangePasswordRequest request, HttpServletRequest httpRequest) {
        userService.changePassword(user, request, httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
