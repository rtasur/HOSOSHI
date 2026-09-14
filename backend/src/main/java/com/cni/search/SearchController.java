package com.cni.search;

import com.cni.casefile.CaseAccessService;
import com.cni.casefile.CaseRepository;
import com.cni.entity.EntityRepository;
import com.cni.entity.CaseEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {
    private final EntityRepository entities;
    private final CaseEntityRepository caseEntities;
    private final CaseRepository cases;
    private final CaseAccessService access;

    @GetMapping
    public Map<String,Object> search(@RequestParam String q, Authentication a) {
        String query=q==null?"":q.trim();
        if(query.length()<2) return Map.of("cases",List.of(),"entities",List.of());
        var caseRows=cases.findByDeletedAtIsNull().stream()
                .filter(c -> c.getCaseNumber().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
                        || c.getTitle().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                .filter(c->{try{access.require(c.getId(),a);return true;}catch(Exception e){return false;}})
                .sorted(Comparator.comparing(com.cni.casefile.InvestigationCase::getUpdatedAt).reversed())
                .limit(20)
                .map(c->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",c.getId());m.put("caseNumber",c.getCaseNumber());m.put("title",c.getTitle());m.put("priority",c.getPriority().name());return m;}).toList();
        var entityRows=entities.findTop50ByPrimaryNameContainingIgnoreCaseOrderByPrimaryNameAsc(query).stream()
                .flatMap(e->caseEntities.findByEntity_Id(e.getId()).stream().filter(ce->{try{access.require(ce.getInvestigationCase().getId(),a);return true;}catch(Exception x){return false;}}).map(ce->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",e.getId());m.put("referenceCode",e.getReferenceCode());m.put("primaryName",e.getPrimaryName());m.put("entityType",e.getEntityType().name());m.put("caseId",ce.getInvestigationCase().getId());m.put("caseNumber",ce.getInvestigationCase().getCaseNumber());return m;}))
                .toList();
        return Map.of("cases",caseRows,"entities",entityRows);
    }
}
