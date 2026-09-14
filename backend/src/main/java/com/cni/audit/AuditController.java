package com.cni.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {
    private final AuditLogRepository repo;

    @GetMapping
    public List<Map<String, Object>> list(Authentication authentication) {
        List<Map<String, Object>> result = new ArrayList<>();
        repo.findTop100ByOrderByCreatedAtDesc().forEach(x -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", x.getId());
            row.put("username", Objects.toString(x.getUsername(), "SYSTEM"));
            row.put("action", x.getAction());
            row.put("resourceType", Objects.toString(x.getResourceType(), ""));
            row.put("resourceId", Objects.toString(x.getResourceId(), ""));
            row.put("details", Objects.toString(x.getDetails(), ""));
            row.put("createdAt", x.getCreatedAt());
            result.add(row);
        });
        return result;
    }
}
