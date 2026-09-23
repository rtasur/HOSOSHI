package com.cni.common;

import org.neo4j.driver.Driver;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final JdbcTemplate jdbc;
    private final Driver neo4j;

    public HealthController(JdbcTemplate jdbc, Driver neo4j) {
        this.jdbc = jdbc;
        this.neo4j = neo4j;
    }

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> checks = new LinkedHashMap<>();
        boolean postgresOk = false;
        boolean neo4jOk = false;

        try {
            Integer result = jdbc.queryForObject("SELECT 1", Integer.class);
            postgresOk = result != null && result == 1;
        } catch (Exception ignored) {
            // Report readiness failure below rather than exposing database details.
        }

        try (var session = neo4j.session()) {
            session.run("RETURN 1").consume();
            neo4jOk = true;
        } catch (Exception ignored) {
            // Report readiness failure below rather than exposing graph details.
        }

        checks.put("postgresql", postgresOk ? "UP" : "DOWN");
        checks.put("neo4j", neo4jOk ? "UP" : "DOWN");

        if (!postgresOk || !neo4jOk) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "HOSOSHI dependencies are not ready");
        }

        return Map.of(
                "status", "UP",
                "service", "criminal-network-intelligence-backend",
                "checks", checks,
                "time", Instant.now().toString());
    }
}
