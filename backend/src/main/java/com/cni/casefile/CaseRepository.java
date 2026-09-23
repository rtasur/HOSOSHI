package com.cni.casefile;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseRepository extends JpaRepository<InvestigationCase, UUID> {

    long countByStatusIn(Collection<CaseStatus> statuses);

    boolean existsByCaseNumber(String caseNumber);

    long countByPriority(CasePriority priority);

    List<InvestigationCase> findByDeletedAtIsNull();

    List<InvestigationCase> findByDeletedAtIsNotNullOrderByDeletedAtDesc();

    long countByDeletedAtIsNullAndPriority(CasePriority priority);

    long countByDeletedAtIsNullAndStatusIn(Collection<CaseStatus> statuses);

    long countByDeletedAtIsNotNull();
}
