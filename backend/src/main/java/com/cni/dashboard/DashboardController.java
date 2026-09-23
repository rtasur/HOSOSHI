package com.cni.dashboard;

import com.cni.casefile.*;
import com.cni.entity.CaseEntityRepository;
import com.cni.relationship.RelationshipRepository;
import com.cni.evidence.EvidenceRepository;
import com.cni.ingestion.IngestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final CaseEntityRepository caseEntities;
    private final RelationshipRepository relationships;
    private final CaseRepository cases;
    private final EvidenceRepository evidence;
    private final IngestionRepository ingestion;
    private final CaseAccessService access;

    @GetMapping("/summary")
    public Map<String, Object> summary(Authentication authentication) {
        var accessible = access.accessibleActiveCases(authentication);
        var caseIds = accessible.stream().map(InvestigationCase::getId).collect(java.util.stream.Collectors.toSet());

        long entityCount = caseEntities.findAll().stream()
                .filter(ce -> ce.getInvestigationCase() != null && caseIds.contains(ce.getInvestigationCase().getId()))
                .map(ce -> ce.getEntity().getId())
                .distinct()
                .count();
        long relationshipCount = relationships.findAll().stream()
                .filter(r -> r.getInvestigationCase() != null && caseIds.contains(r.getInvestigationCase().getId()))
                .count();
        long evidenceCount = evidence.findAll().stream()
                .filter(e -> e.getInvestigationCase() != null && caseIds.contains(e.getInvestigationCase().getId()))
                .count();
        long ingestionCount = ingestion.findAll().stream()
                .filter(i -> i.getInvestigationCase() != null && caseIds.contains(i.getInvestigationCase().getId()))
                .count();

        var counts = new LinkedHashMap<String, Long>();
        for (CasePriority priority : CasePriority.values()) {
            counts.put(priority.name(), accessible.stream()
                    .filter(c -> c.getPriority() == priority)
                    .count());
        }

        long activeCases = accessible.stream()
                .filter(c -> c.getStatus() == CaseStatus.OPEN || c.getStatus() == CaseStatus.UNDER_INVESTIGATION)
                .count();

        long deletedCases = access.hasAnyRole(authentication, com.cni.role.RoleCode.SUPER_ADMIN)
                ? cases.countByDeletedAtIsNotNull() : 0L;

        return Map.of(
                "entitiesIdentified", entityCount,
                "activeConnections", relationshipCount,
                "sourceRecords", evidenceCount + ingestionCount,
                "activeCases", activeCases,
                "priorityCounts", counts,
                "deletedCases", deletedCases);
    }
}
