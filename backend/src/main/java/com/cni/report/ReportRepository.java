package com.cni.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<InvestigationReport, UUID> {
    List<InvestigationReport> findByInvestigationCase_IdOrderByUpdatedAtDesc(UUID caseId);
}
