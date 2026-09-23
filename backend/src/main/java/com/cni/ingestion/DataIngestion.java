package com.cni.ingestion;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.cni.casefile.InvestigationCase;
import com.cni.user.AppUser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "data_ingestions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataIngestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private InvestigationCase investigationCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @Column(nullable = false)
    private String sourceType;

    @Column(nullable = false)
    private String fileName;

    private long recordCount;
    private String status;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private String storedName;
    private String sha256;
    private String contentType;
    private long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
