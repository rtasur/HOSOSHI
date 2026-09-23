package com.cni.timeline;

import com.cni.casefile.CaseAccessService;
import com.cni.entity.CaseEntityRepository;
import com.cni.evidence.EvidenceRepository;
import com.cni.ingestion.IngestionRepository;
import com.cni.relationship.RelationshipRepository;
import com.cni.report.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/timeline")
@RequiredArgsConstructor
public class TimelineController {
    private final CaseAccessService access;
    private final CaseEntityRepository caseEntities;
    private final RelationshipRepository relationships;
    private final EvidenceRepository evidence;
    private final IngestionRepository ingestion;
    private final ReportRepository reports;

    @GetMapping("/cases/{caseId}")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> timeline(@PathVariable UUID caseId, Authentication auth) {
        access.require(caseId, auth);
        List<Map<String, Object>> out = new ArrayList<>();

        caseEntities.findByInvestigationCase_IdOrderByEntity_PrimaryNameAsc(caseId)
                .forEach(ce -> out.add(event(
                        ce.getRegisteredAt(),
                        "ENTITY_REGISTERED",
                        "Entity " + ce.getEntity().getReferenceCode() + " registered",
                        ce.getEntity().getPrimaryName(),
                        List.of(ce.getEntity().getId()))));

        relationships.findByInvestigationCase_Id(caseId)
                .forEach(r -> out.add(event(
                        r.getObservedAt() != null ? r.getObservedAt() : r.getCreatedAt(),
                        "RELATIONSHIP",
                        "Relationship: " + r.getRelationshipType(),
                        r.getSourceEntity().getReferenceCode() + " → " + r.getTargetEntity().getReferenceCode(),
                        List.of(r.getSourceEntity().getId(), r.getTargetEntity().getId()))));

        evidence.findByInvestigationCase_IdOrderByCreatedAtDesc(caseId)
                .forEach(e -> out.add(event(e.getCreatedAt(), "EVIDENCE", "Evidence uploaded", e.getOriginalName(), List.of())));
        ingestion.findByInvestigationCase_IdOrderByCreatedAtDesc(caseId)
                .forEach(i -> out.add(event(i.getCreatedAt(), "INGESTION", "Source imported: " + i.getSourceType(), i.getFileName(), List.of())));
        reports.findByInvestigationCase_IdOrderByUpdatedAtDesc(caseId)
                .forEach(r -> out.add(event(r.getUpdatedAt(), "REPORT", "Report updated", r.getTitle(), List.of())));

        out.sort((a, b) -> String.valueOf(b.get("timestamp")).compareTo(String.valueOf(a.get("timestamp"))));
        return out;
    }

    private Map<String, Object> event(Instant timestamp, String type, String title, String detail, List<UUID> entityIds) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", timestamp);
        m.put("type", type);
        m.put("title", title);
        m.put("detail", detail);
        m.put("entityIds", entityIds);
        return m;
    }
}
