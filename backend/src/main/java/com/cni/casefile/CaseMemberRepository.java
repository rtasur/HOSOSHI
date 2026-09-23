package com.cni.casefile;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseMemberRepository extends JpaRepository<CaseMember, UUID> {

    List<CaseMember> findByInvestigationCase_Id(UUID caseId);

    List<CaseMember> findByUser_Id(UUID userId);

    boolean existsByInvestigationCase_IdAndUser_Id(UUID caseId, UUID userId);

    boolean existsByInvestigationCase_IdAndUser_Username(UUID caseId, String username);

    long countByUser_Id(UUID userId);

    void deleteByInvestigationCase_IdAndUser_Id(UUID caseId, UUID userId);
}
