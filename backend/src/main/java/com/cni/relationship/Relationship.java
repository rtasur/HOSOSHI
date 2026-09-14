package com.cni.relationship;

import com.cni.casefile.InvestigationCase;
import com.cni.entity.IntelligenceEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "relationships")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Relationship {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "case_id", nullable = false)
    private InvestigationCase investigationCase;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_entity_id", nullable = false)
    private IntelligenceEntity sourceEntity;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "target_entity_id", nullable = false)
    private IntelligenceEntity targetEntity;
    @Column(name = "relationship_type", nullable = false) private String relationshipType;
    @Column(nullable = false) private Double confidence;
    @Column(nullable = false) private String status;
    @Column(name = "source_reference") private String sourceReference;
    @Column(name = "observed_at") private Instant observedAt;
    @Column(name = "verified_at") private Instant verifiedAt;
    @Column(name = "verified_by") private String verifiedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
