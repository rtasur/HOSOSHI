package com.cni.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CaseEntityRepository extends JpaRepository<CaseEntity, UUID> {
    List<CaseEntity> findByInvestigationCase_IdOrderByEntity_PrimaryNameAsc(UUID caseId);
    Optional<CaseEntity> findByInvestigationCase_IdAndEntity_Id(UUID caseId, UUID entityId);
    List<CaseEntity> findByEntity_Id(UUID entityId);
    boolean existsByInvestigationCase_IdAndEntity_Id(UUID caseId, UUID entityId);
}
