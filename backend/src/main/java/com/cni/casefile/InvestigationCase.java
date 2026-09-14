package com.cni.casefile;

import com.cni.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "investigation_cases")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvestigationCase {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "case_number", nullable = false, unique = true, length = 64)
    private String caseNumber;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    private String category;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private CasePriority priority;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private CaseStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private CaseClassification classification;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;
    @Column(name = "deleted_by")
    private UUID deletedBy;
}
