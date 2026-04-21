package com.banking.auth.service;

import com.banking.auth.domain.Role;
import com.banking.auth.domain.User;
import com.banking.auth.exception.AuthException;
import com.banking.auth.repository.UserRepository;
import com.banking.auth.web.dto.ChangePasswordRequest;
import com.banking.auth.web.dto.ChangeRoleRequest;
import com.banking.auth.web.dto.UserResponse;
import com.banking.auth.mapper.UserMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final UserMapper userMapper;

    public UserResponse getProfile(User user) {
        return userMapper.toResponse(user);
    }

    @Transactional
    public void changePassword(User user, ChangePasswordRequest request, String ip) {
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw AuthException.wrongPassword();
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        auditService.log(user, "CHANGE_PASSWORD", ip, null);
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(userMapper::toResponse).toList();
    }

    @Transactional
    public UserResponse blockUser(UUID userId, boolean blocked, String ip, User admin) {
        User user = userRepository.findById(userId).orElseThrow(AuthException::userNotFound);
        user.setBlocked(blocked);
        userRepository.save(user);
        auditService.log(admin, blocked ? "BLOCK_USER" : "UNBLOCK_USER", ip, "targetUserId=" + userId);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse changeRole(UUID userId, ChangeRoleRequest request, String ip, User admin) {
        User user = userRepository.findById(userId).orElseThrow(AuthException::userNotFound);
        Role previous = user.getRole();
        user.setRole(request.role());
        userRepository.save(user);
        auditService.log(admin, "CHANGE_ROLE", ip, "targetUserId=" + userId + " from=" + previous + " to=" + request.role());
        return userMapper.toResponse(user);
    }
}
