package com.cni.relationship;

import com.cni.audit.AuditService;
import com.cni.casefile.CaseAccessService;
import com.cni.entity.EntityRepository;
import com.cni.entity.CaseEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@RestController
@Transactional
@RequestMapping("/api/v1/relationships")
@RequiredArgsConstructor
public class RelationshipController {
    private final RelationshipRepository repo;
    private final CaseAccessService access;
    private final EntityRepository entityRepo;
    private final CaseEntityRepository caseEntities;
    private final AuditService audit;

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SUPER_ADMIN','INVESTIGATION_SUPERVISOR','INVESTIGATOR','INTELLIGENCE_ANALYST')")
    @PostMapping
    public Map<String,Object> create(@RequestBody CreateRelationshipRequest r, Authentication a) {
        var c = access.require(r.caseId(), a);
        if (r.sourceEntityId() == null || r.targetEntityId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and target entities are required");
        }
        if (r.sourceEntityId().equals(r.targetEntityId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An entity cannot be connected to itself");
        }
        var relationshipType = r.relationshipType() == null ? "ASSOCIATED_WITH" : r.relationshipType().trim().toUpperCase(Locale.ROOT);
        if (relationshipType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Relationship type is required");
        }
        var s = entityRepo.findById(r.sourceEntityId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source entity not found"));
        var t = entityRepo.findById(r.targetEntityId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target entity not found"));
        if (!caseEntities.existsByInvestigationCase_IdAndEntity_Id(c.getId(), s.getId()) || !caseEntities.existsByInvestigationCase_IdAndEntity_Id(c.getId(), t.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both entities must belong to the selected case");
        }
        var rel = repo.save(Relationship.builder()
                .investigationCase(c).sourceEntity(s).targetEntity(t)
                .relationshipType(relationshipType)
                .confidence(r.confidence() == null ? 0.5 : Math.max(0, Math.min(1, r.confidence())))
                .status("PROPOSED")
                .sourceReference(r.sourceReference())
                .observedAt(r.observedAt())
                .createdAt(Instant.now()).build());
        audit.log(a, "RELATIONSHIP_CREATED", "RELATIONSHIP", rel.getId(), rel.getRelationshipType());
        return view(rel);
    }

    @GetMapping
    public List<Map<String,Object>> list(@RequestParam UUID caseId, Authentication a) {
        access.require(caseId,a);
        return repo.findByInvestigationCase_Id(caseId).stream().map(this::view).toList();
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SUPER_ADMIN','INVESTIGATION_SUPERVISOR','INVESTIGATOR','INTELLIGENCE_ANALYST')")
    @PatchMapping("/{id}/verify")
    public Map<String,Object> verify(@PathVariable UUID id, Authentication a) {
        var r = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Relationship not found"));
        access.require(r.getInvestigationCase().getId(), a);
        r.setStatus("VERIFIED");
        r.setVerifiedAt(Instant.now());
        r.setVerifiedBy(a.getName());
        audit.log(a, "RELATIONSHIP_VERIFIED", "RELATIONSHIP", id, r.getRelationshipType());
        return view(repo.save(r));
    }

    private Map<String,Object> view(Relationship r) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id",r.getId());
        m.put("source",r.getSourceEntity().getId());
        m.put("target",r.getTargetEntity().getId());
        m.put("sourceLabel",r.getSourceEntity().getPrimaryName());
        m.put("targetLabel",r.getTargetEntity().getPrimaryName());
        m.put("type",r.getRelationshipType());
        m.put("confidence",r.getConfidence());
        m.put("status",r.getStatus());
        m.put("sourceReference",r.getSourceReference());
        m.put("observedAt",r.getObservedAt());
        m.put("verifiedAt",r.getVerifiedAt());
        m.put("verifiedBy",r.getVerifiedBy());
        return m;
    }

    public record CreateRelationshipRequest(UUID caseId, UUID sourceEntityId, UUID targetEntityId,
                                            String relationshipType, Double confidence,
                                            String sourceReference, Instant observedAt) {}
}
