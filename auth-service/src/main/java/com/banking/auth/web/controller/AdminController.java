package com.banking.auth.web.controller;

import com.banking.auth.domain.User;
import com.banking.auth.service.UserService;
import com.banking.auth.web.api.AdminApi;
import com.banking.auth.web.dto.ChangeRoleRequest;
import com.banking.auth.web.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AdminController implements AdminApi {

    private final UserService userService;

    @Override
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @Override
    public ResponseEntity<UserResponse> blockUser(UUID id, boolean blocked, HttpServletRequest httpRequest, User admin) {
        return ResponseEntity.ok(userService.blockUser(id, blocked, httpRequest.getRemoteAddr(), admin));
    }

    @Override
    public ResponseEntity<UserResponse> changeRole(UUID id, ChangeRoleRequest request, HttpServletRequest httpRequest, User admin) {
        return ResponseEntity.ok(userService.changeRole(id, request, httpRequest.getRemoteAddr(), admin));
    }
}
