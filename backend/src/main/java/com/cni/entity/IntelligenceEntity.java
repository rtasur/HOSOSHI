package com.cni.entity;

import com.cni.casefile.InvestigationCase;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "entities")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IntelligenceEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    /** Legacy compatibility pointer. Case membership is authoritative in case_entities. */
    private InvestigationCase investigationCase;

    @Column(name = "reference_code", nullable = false, length = 16)
    private String referenceCode;

    @Column(name = "map_code", nullable = false, length = 3)
    private String mapCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 40)
    private EntityType entityType;

    @Column(name = "primary_name", nullable = false, length = 200)
    private String primaryName;

    @Column(name = "alias", length = 200)
    private String alias;

    @Column(name = "case_role", length = 40)
    private String caseRole;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 40)
    private String gender;

    @Column(name = "nationality", length = 80)
    private String nationality;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "email", length = 200)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "location_label")
    private String locationLabel;

    @Column(name = "location_lat")
    private Double locationLat;

    @Column(name = "location_lng")
    private Double locationLng;

    @Column(name = "source_reference")
    private String sourceReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
