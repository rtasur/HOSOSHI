package com.cni.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IngestionRepository extends JpaRepository<DataIngestion, UUID> {

    List<DataIngestion> findByInvestigationCase_IdOrderByCreatedAtDesc(UUID caseId);

    long countByStatus(String status);

    boolean existsByInvestigationCase_IdAndStoredNameIsNotNull(UUID caseId);
}