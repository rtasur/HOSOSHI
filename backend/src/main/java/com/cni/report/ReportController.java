package com.cni.report;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cni.casefile.CaseAccessService;
import com.cni.user.UserRepository;

import lombok.RequiredArgsConstructor;

@RestController
@Transactional
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportRepository repo;
    private final CaseAccessService access;
    private final UserRepository users;

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam UUID caseId,
            Authentication authentication) {
        access.require(caseId, authentication);
        return repo.findByInvestigationCase_IdOrderByUpdatedAtDesc(caseId)
                .stream()
                .map(this::view)
                .toList();
    }

    @org.springframework.security.access.prepost.PreAuthorize(
            "hasAnyRole('SUPER_ADMIN','INVESTIGATION_SUPERVISOR','INVESTIGATOR','INTELLIGENCE_ANALYST')")
    @PostMapping
    public Map<String, Object> create(
            @RequestBody Req request,
            Authentication authentication) {
        var investigationCase = access.require(request.caseId(), authentication);
        var user = users.findByUsername(authentication.getName())
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        var now = Instant.now();

        var report = repo.save(InvestigationReport.builder()
                .investigationCase(investigationCase)
                .author(user)
                .title(request.title())
                .content(request.content())
                .status("DRAFT")
                .createdAt(now)
                .updatedAt(now)
                .build());

        return view(report);
    }

    @org.springframework.security.access.prepost.PreAuthorize(
            "hasAnyRole('SUPER_ADMIN','INVESTIGATION_SUPERVISOR','INVESTIGATOR','INTELLIGENCE_ANALYST')")
    @PutMapping("/{id}")
    public Map<String, Object> update(
            @PathVariable UUID id,
            @RequestBody Req request,
            Authentication authentication) {
        var report = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Report not found"));

        access.require(report.getInvestigationCase().getId(), authentication);

        if (request.title() != null) {
            report.setTitle(request.title());
        }
        if (request.content() != null) {
            report.setContent(request.content());
        }
        report.setUpdatedAt(Instant.now());

        return view(repo.save(report));
    }

    private Map<String, Object> view(InvestigationReport report) {
        return Map.of(
                "id", report.getId(),
                "caseId", report.getInvestigationCase().getId(),
                "title", report.getTitle(),
                "content", Objects.toString(report.getContent(), ""),
                "status", report.getStatus(),
                "author", report.getAuthor().getFullName(),
                "createdAt", report.getCreatedAt(),
                "updatedAt", report.getUpdatedAt());
    }

    public record Req(UUID caseId, String title, String content) {
    }
}
