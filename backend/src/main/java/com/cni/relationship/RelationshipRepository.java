package com.cni.relationship;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RelationshipRepository extends JpaRepository<Relationship, UUID> {
    List<Relationship> findByInvestigationCase_Id(UUID caseId);
}
