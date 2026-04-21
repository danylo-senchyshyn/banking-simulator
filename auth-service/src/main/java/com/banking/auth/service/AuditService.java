package com.banking.auth.service;

import com.banking.auth.domain.AuditLog;
import com.banking.auth.domain.User;
import com.banking.auth.repository.AuditLogRepository;
import com.banking.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Async
    public void log(User user, String action, String ipAddress, String details) {
        // Re-fetch user in new transaction to avoid FK violation when called async after parent tx commits
        UUID userId = user.getId();
        userRepository.findById(userId).ifPresent(u ->
                auditLogRepository.save(AuditLog.builder()
                        .user(u)
                        .action(action)
                        .ipAddress(ipAddress)
                        .details(details)
                        .build())
        );
    }
}

