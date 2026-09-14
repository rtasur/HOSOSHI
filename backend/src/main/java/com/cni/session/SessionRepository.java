package com.cni.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<InvestigationSession, UUID> {
    List<InvestigationSession> findByUser_IdAndStatusOrderByUpdatedAtDesc(UUID userId, String status);
    Optional<InvestigationSession> findFirstByUser_IdAndInvestigationCase_IdAndStatusOrderByUpdatedAtDesc(UUID userId, UUID caseId, String status);
    List<InvestigationSession> findByInvestigationCase_IdAndStatus(UUID caseId, String status);
}
