package com.cni.evidence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvidenceRepository extends JpaRepository<EvidenceFile, UUID> {
    List<EvidenceFile> findByInvestigationCase_IdOrderByCreatedAtDesc(UUID caseId);
}
