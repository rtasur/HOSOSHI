package com.cni.graph;

import com.cni.casefile.CaseAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/graph")
@RequiredArgsConstructor
public class GraphController {
    private final CaseAccessService access;
    private final GraphSyncService graphSync;

    @GetMapping("/cases/{caseId}")
    public Map<String,Object> graph(@PathVariable UUID caseId, Authentication authentication) {
        access.require(caseId, authentication);
        return graphSync.rebuildAndRead(caseId);
    }
}
