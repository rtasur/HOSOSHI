package com.cni.casefile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CaseRepository extends JpaRepository<InvestigationCase,UUID>{
 long countByStatusIn(Collection<CaseStatus> statuses);
 boolean existsByCaseNumber(String caseNumber);
 long countByPriority(CasePriority priority);
 List<InvestigationCase> findByDeletedAtIsNull();
 List<InvestigationCase> findByDeletedAtIsNotNullOrderByDeletedAtDesc();
 long countByDeletedAtIsNullAndPriority(CasePriority priority);
 long countByDeletedAtIsNullAndStatusIn(Collection<CaseStatus> statuses);
 long countByDeletedAtIsNotNull();
}
