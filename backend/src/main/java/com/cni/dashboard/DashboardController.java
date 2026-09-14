package com.cni.dashboard;

import com.cni.casefile.*;
import com.cni.entity.*;
import com.cni.relationship.*;
import com.cni.evidence.*;
import com.cni.ingestion.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final EntityRepository entities;
    private final RelationshipRepository relationships;
    private final CaseRepository cases;
    private final EvidenceRepository evidence;
    private final IngestionRepository ingestion;

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        var counts = new LinkedHashMap<String, Long>();
        for (CasePriority p : CasePriority.values()) counts.put(p.name(), cases.countByDeletedAtIsNullAndPriority(p));
        return Map.of(
                "entitiesIdentified", entities.count(),
                "activeConnections", relationships.count(),
                "sourceRecords", evidence.count() + ingestion.count(),
                "activeCases", cases.countByDeletedAtIsNullAndStatusIn(List.of(CaseStatus.OPEN, CaseStatus.UNDER_INVESTIGATION)),
                "priorityCounts", counts,
                "deletedCases", cases.countByDeletedAtIsNotNull());
    }
}
