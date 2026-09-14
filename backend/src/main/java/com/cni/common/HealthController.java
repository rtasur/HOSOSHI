package com.cni.common;

import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    @GetMapping
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "criminal-network-intelligence-backend", "time", Instant.now().toString());
    }
}
