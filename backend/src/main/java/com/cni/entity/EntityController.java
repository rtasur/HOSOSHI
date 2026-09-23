package com.cni.entity;

import com.cni.audit.AuditService;
import com.cni.casefile.CaseAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@RestController
@Transactional
@RequestMapping("/api/v1/entities")
@RequiredArgsConstructor
public class EntityController {

    private final EntityRepository repo;
    private final CaseAccessService access;
    private final AuditService audit;
    private final CaseEntityRepository caseEntities;

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(required = false) UUID caseId,
            Authentication authentication) {

        if (caseId != null) {
            access.require(caseId, authentication);
            return caseEntities.findByInvestigationCase_IdOrderByEntity_PrimaryNameAsc(caseId)
                    .stream()
                    .map(ce -> view(ce.getEntity(), ce))
                    .toList();
        }

        return repo.findAllByOrderByPrimaryNameAsc()
                .stream()
                .flatMap(entity -> caseEntities.findByEntity_Id(entity.getId())
                        .stream()
                        .filter(ce -> visibleCase(ce.getInvestigationCase().getId(), authentication))
                        .map(ce -> view(entity, ce)))
                .toList();
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(
            @RequestParam String query,
            @RequestParam(required = false) EntityType type,
            Authentication authentication) {

        if (query == null || query.trim().length() < 2) {
            return List.of();
        }

        String normalizedQuery = query.trim();
        var matches = type == null
                ? repo.findTop50ByPrimaryNameContainingIgnoreCaseOrderByPrimaryNameAsc(normalizedQuery)
                : repo.findTop50ByEntityTypeAndPrimaryNameContainingIgnoreCaseOrderByPrimaryNameAsc(
                        type, normalizedQuery);

        return matches.stream()
                .flatMap(entity -> caseEntities.findByEntity_Id(entity.getId())
                        .stream()
                        .filter(ce -> visibleCase(ce.getInvestigationCase().getId(), authentication))
                        .map(ce -> view(entity, ce)))
                .limit(50)
                .toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID caseId,
            Authentication authentication) {

        var entity = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Entity not found"));

        CaseEntity caseEntity;
        if (caseId != null) {
            access.require(caseId, authentication);
            caseEntity = caseEntities
                    .findByInvestigationCase_IdAndEntity_Id(caseId, id)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Entity is not registered in this case"));
        } else {
            caseEntity = caseEntities.findByEntity_Id(id)
                    .stream()
                    .filter(ce -> visibleCase(ce.getInvestigationCase().getId(), authentication))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Entity is not accessible"));
        }

        audit.log(authentication, "ENTITY_VIEWED", "ENTITY", id, entity.getReferenceCode());
        return view(entity, caseEntity);
    }

    @org.springframework.security.access.prepost.PreAuthorize(
            "hasAnyRole('SUPER_ADMIN','INVESTIGATION_SUPERVISOR','INVESTIGATOR','INTELLIGENCE_ANALYST','DATA_OPERATOR')")
    @PostMapping
    public Map<String, Object> create(
            @RequestBody CreateEntityRequest request,
            Authentication authentication) {

        if (request.caseId() == null
                || request.entityType() == null
                || blank(request.primaryName())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Case, entity type and name are required");
        }

        var investigationCase = access.require(request.caseId(), authentication);
        Instant now = Instant.now();

        var entity = repo.save(IntelligenceEntity.builder()
                .investigationCase(null)
                .referenceCode(uniqueCode())
                .mapCode(uniqueMapCode())
                .entityType(request.entityType())
                .primaryName(request.primaryName().trim())
                .alias(normalize(request.alias()))
                .caseRole(normalize(request.caseRole()))
                .dateOfBirth(request.dateOfBirth())
                .gender(normalize(request.gender()))
                .nationality(normalize(request.nationality()))
                .phone(normalize(request.phone()))
                .email(normalize(request.email()))
                .description(normalize(request.description()))
                .status(defaultValue(request.status(), "ACTIVE"))
                .confidence(request.confidence() == null ? null : clamp(request.confidence()))
                .locationLabel(normalize(request.locationLabel()))
                .locationLat(request.locationLat())
                .locationLng(request.locationLng())
                .sourceReference(normalize(request.sourceReference()))
                .createdAt(now)
                .updatedAt(now)
                .build());

        var caseEntity = caseEntities.save(CaseEntity.builder()
                .investigationCase(investigationCase)
                .entity(entity)
                .caseRole(normalize(request.caseRole()))
                .registeredAt(now)
                .status(defaultValue(request.status(), "ACTIVE"))
                .confidence(request.confidence() == null ? null : clamp(request.confidence()))
                .sourceReference(normalize(request.sourceReference()))
                .build());

        audit.log(authentication, "ENTITY_CREATED", "ENTITY", entity.getId(), entity.getReferenceCode());
        return view(entity, caseEntity);
    }

    private Map<String, Object> view(IntelligenceEntity entity, CaseEntity caseEntity) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entity.getId());
        result.put("referenceCode", entity.getReferenceCode());
        result.put("mapCode", entity.getMapCode());
        result.put("entityType", entity.getEntityType().name());
        result.put("primaryName", entity.getPrimaryName());
        result.put("alias", entity.getAlias());
        result.put("caseRole", caseEntity != null ? caseEntity.getCaseRole() : entity.getCaseRole());
        result.put("dateOfBirth", entity.getDateOfBirth());
        result.put("gender", entity.getGender());
        result.put("nationality", entity.getNationality());
        result.put("phone", entity.getPhone());
        result.put("email", entity.getEmail());
        result.put("description", entity.getDescription());
        result.put("status", caseEntity != null ? caseEntity.getStatus() : entity.getStatus());
        result.put("confidence", caseEntity != null ? caseEntity.getConfidence() : entity.getConfidence());
        result.put("caseId", caseEntity != null ? caseEntity.getInvestigationCase().getId() : null);
        result.put("caseNumber", caseEntity != null ? caseEntity.getInvestigationCase().getCaseNumber() : null);
        result.put("caseTitle", caseEntity != null ? caseEntity.getInvestigationCase().getTitle() : null);
        result.put("registeredAt", caseEntity != null ? caseEntity.getRegisteredAt() : entity.getCreatedAt());
        result.put("updatedAt", entity.getUpdatedAt());
        result.put("locationLabel", entity.getLocationLabel());
        result.put("locationLat", entity.getLocationLat());
        result.put("locationLng", entity.getLocationLng());
        result.put("sourceReference", caseEntity != null
                ? caseEntity.getSourceReference()
                : entity.getSourceReference());
        return result;
    }

    private String uniqueCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        while (true) {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                code.append(alphabet.charAt(new Random().nextInt(alphabet.length())));
            }
            if (!repo.existsByReferenceCode(code.toString())) {
                return code.toString();
            }
        }
    }

    private String uniqueMapCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Set<String> existing = new HashSet<>();
        repo.findAll().forEach(entity -> {
            if (entity.getMapCode() != null) {
                existing.add(entity.getMapCode());
            }
        });

        while (true) {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                code.append(alphabet.charAt(new Random().nextInt(alphabet.length())));
            }
            if (!existing.contains(code.toString())) {
                return code.toString();
            }
        }
    }

    private static Double clamp(Double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static String normalize(String value) {
        return blank(value) ? null : value.trim();
    }

    private static String defaultValue(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }

    private boolean visibleCase(UUID caseId, Authentication authentication) {
        try {
            access.require(caseId, authentication);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record CreateEntityRequest(
            UUID caseId,
            EntityType entityType,
            String primaryName,
            String alias,
            String caseRole,
            java.time.LocalDate dateOfBirth,
            String gender,
            String nationality,
            String phone,
            String email,
            String description,
            String status,
            Double confidence,
            String locationLabel,
            Double locationLat,
            Double locationLng,
            String sourceReference) {
    }
}
