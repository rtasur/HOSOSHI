package com.cni.search;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cni.casefile.CaseAccessService;
import com.cni.casefile.CaseRepository;
import com.cni.entity.CaseEntityRepository;
import com.cni.entity.EntityRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final EntityRepository entities;
    private final CaseEntityRepository caseEntities;
    private final CaseRepository cases;
    private final CaseAccessService access;

    @GetMapping
    public Map<String, Object> search(
            @RequestParam String q,
            Authentication authentication) {

        String query = q == null ? "" : q.trim();
        if (query.length() < 2) {
            return Map.of("cases", List.of(), "entities", List.of());
        }

        String normalizedQuery = query.toLowerCase(Locale.ROOT);

        var caseRows = cases.findByDeletedAtIsNull().stream()
                .filter(c -> c.getCaseNumber().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || c.getTitle().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .filter(c -> visibleCase(c.getId(), authentication))
                .sorted(Comparator.comparing(com.cni.casefile.InvestigationCase::getUpdatedAt).reversed())
                .limit(20)
                .map(c -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", c.getId());
                    row.put("caseNumber", c.getCaseNumber());
                    row.put("title", c.getTitle());
                    row.put("priority", c.getPriority().name());
                    return row;
                })
                .toList();

        var entityRows = entities
                .findTop50ByPrimaryNameContainingIgnoreCaseOrderByPrimaryNameAsc(query)
                .stream()
                .flatMap(entity -> caseEntities.findByEntity_Id(entity.getId()).stream()
                        .filter(caseEntity -> visibleCase(
                                caseEntity.getInvestigationCase().getId(), authentication))
                        .map(caseEntity -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("id", entity.getId());
                            row.put("referenceCode", entity.getReferenceCode());
                            row.put("primaryName", entity.getPrimaryName());
                            row.put("entityType", entity.getEntityType().name());
                            row.put("caseId", caseEntity.getInvestigationCase().getId());
                            row.put("caseNumber", caseEntity.getInvestigationCase().getCaseNumber());
                            return row;
                        }))
                .toList();

        return Map.of("cases", caseRows, "entities", entityRows);
    }

    private boolean visibleCase(java.util.UUID caseId, Authentication authentication) {
        try {
            access.require(caseId, authentication);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
