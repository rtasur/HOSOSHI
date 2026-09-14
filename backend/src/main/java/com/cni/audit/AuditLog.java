package com.cni.audit;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String username;
    @Column(nullable = false) private String action;
    private String resourceType;
    private UUID resourceId;
    @Column(columnDefinition = "TEXT") private String details;
    @Column(nullable = false) private Instant createdAt;
}
