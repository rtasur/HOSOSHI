package com.cni.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository repo;
    public void log(Authentication auth, String action, String resourceType, UUID resourceId, String details) {
        repo.save(AuditLog.builder()
            .username(auth == null ? "SYSTEM" : auth.getName())
            .action(action).resourceType(resourceType).resourceId(resourceId).details(details).createdAt(Instant.now()).build());
    }
}
