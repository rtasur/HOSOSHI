package com.cni.session;

import com.cni.casefile.CaseAccessService;
import com.cni.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {
    private final SessionRepository repo;
    private final UserRepository users;
    private final CaseAccessService access;

    @GetMapping("/active")
    @Transactional(readOnly = true)
    public Map<String, Object> active(Authentication authentication) {
        var user = users.findByUsername(authentication.getName()).orElseThrow();
        return repo.findByUser_IdAndStatusOrderByUpdatedAtDesc(user.getId(), "ACTIVE")
                .stream()
                .findFirst()
                .map(this::view)
                .orElseGet(() -> Map.of("active", false));
    }

    @PostMapping("/start")
    @Transactional
    public Map<String, Object> start(@RequestParam UUID caseId, Authentication authentication) {
        var user = users.findByUsername(authentication.getName()).orElseThrow();
        var investigationCase = access.require(caseId, authentication);
        var now = Instant.now();

        var activeSessions = repo.findByUser_IdAndStatusOrderByUpdatedAtDesc(user.getId(), "ACTIVE");
        InvestigationSession reusable = null;

        for (var session : activeSessions) {
            if (session.getInvestigationCase().getId().equals(caseId) && reusable == null) {
                reusable = session;
            } else {
                // Keep only one active session per user. Older sessions are closed so
                // this remains safe even if an older local database contains duplicates.
                session.setStatus("CLOSED");
                session.setUpdatedAt(now);
                repo.save(session);
            }
        }

        if (reusable != null) {
            reusable.setUpdatedAt(now);
            if (reusable.getStateJson() == null || reusable.getStateJson().isBlank()) {
                reusable.setStateJson("{}");
            }
            return view(repo.save(reusable));
        }

        var session = InvestigationSession.builder()
                .user(user)
                .investigationCase(investigationCase)
                .status("ACTIVE")
                .stateJson("{}")
                .startedAt(now)
                .updatedAt(now)
                .build();

        return view(repo.save(session));
    }

    @PostMapping("/{id}/save")
    @Transactional
    public Map<String, Object> save(@PathVariable UUID id,
                                    @RequestBody(required = false) Map<String, Object> state,
                                    Authentication authentication) {
        var session = repo.findById(id).orElseThrow();
        requireOwner(session, authentication);
        session.setStateJson(state == null ? "{}" : new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(state).toString());
        session.setUpdatedAt(Instant.now());
        return view(repo.save(session));
    }

    @PostMapping("/{id}/exit")
    @Transactional
    public Map<String, Object> exit(@PathVariable UUID id, Authentication authentication) {
        var session = repo.findById(id).orElseThrow();
        requireOwner(session, authentication);
        session.setStatus("CLOSED");
        session.setUpdatedAt(Instant.now());
        repo.save(session);
        return Map.of("active", false);
    }

    @PutMapping("/{id}/save")
    public Map<String, Object> saveState(@PathVariable UUID id,
                                         @RequestBody Map<String, Object> state,
                                         Authentication authentication) {
        return save(id, state, authentication);
    }

    private void requireOwner(InvestigationSession session, Authentication authentication) {
        if (!session.getUser().getUsername().equals(authentication.getName())) {
            throw new AccessDeniedException("Session owner only");
        }
    }

    private Map<String, Object> view(InvestigationSession session) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("active", "ACTIVE".equals(session.getStatus()));
        response.put("id", session.getId());
        response.put("caseId", session.getInvestigationCase().getId());
        response.put("caseNumber", session.getInvestigationCase().getCaseNumber());
        response.put("caseTitle", session.getInvestigationCase().getTitle());
        response.put("status", session.getStatus());
        response.put("stateJson", session.getStateJson() == null ? "{}" : session.getStateJson());
        response.put("startedAt", session.getStartedAt());
        response.put("updatedAt", session.getUpdatedAt());
        return response;
    }
}
