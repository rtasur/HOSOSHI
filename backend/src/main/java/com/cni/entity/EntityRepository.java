package com.cni.entity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface EntityRepository extends JpaRepository<IntelligenceEntity, UUID>{
 List<IntelligenceEntity> findByInvestigationCase_IdOrderByPrimaryNameAsc(UUID caseId);
 List<IntelligenceEntity> findAllByOrderByPrimaryNameAsc();
 List<IntelligenceEntity> findTop50ByEntityTypeAndPrimaryNameContainingIgnoreCaseOrderByPrimaryNameAsc(EntityType entityType,String query);
 List<IntelligenceEntity> findTop50ByPrimaryNameContainingIgnoreCaseOrderByPrimaryNameAsc(String query);
 boolean existsByReferenceCode(String referenceCode);
}
