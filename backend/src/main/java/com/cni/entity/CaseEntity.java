package com.cni.entity;

import com.cni.casefile.InvestigationCase;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "case_entities", uniqueConstraints = @UniqueConstraint(name = "uk_case_entity", columnNames = {"case_id", "entity_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "case_id", nullable = false)
    private InvestigationCase investigationCase;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "entity_id", nullable = false)
    private IntelligenceEntity entity;
    @Column(name = "case_role", length = 40)
    private String caseRole;
    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "confidence")
    private Double confidence;
    @Column(name = "source_reference")
    private String sourceReference;
}
