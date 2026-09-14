package com.cni.user;

import com.cni.auth.AuthController.UserView;
import com.cni.audit.AuditService;
import com.cni.casefile.CaseMemberRepository;
import com.cni.casefile.CaseRepository;
import com.cni.role.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserRepository repo;
    private final RoleRepository roleRepo;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final CaseMemberRepository caseMembers;
    private final CaseRepository cases;

    @GetMapping("/assignable")
    public List<UserView> assignable() {
        return repo.findAllByEnabledTrueOrderByFullNameAsc().stream().map(this::view).toList();
    }

    @GetMapping("/admin/all")
    @Transactional(readOnly = true)
    public List<AdminUserView> all(Authentication a) {
        requireAdmin(a);
        return repo.findAll(org.springframework.data.domain.Sort.by("fullName"))
                .stream().map(this::adminView).toList();
    }

    @GetMapping("/admin/pending")
    @Transactional(readOnly = true)
    public List<AdminUserView> pending(Authentication a) {
        requireAdmin(a);
        return repo.findByStatusIgnoreCaseOrderByCreatedAtAsc("PENDING")
                .stream().map(this::adminView).toList();
    }

    /**
     * Single, independent admin payload so the console never shows misleading zeroes
     * just because one of several parallel requests failed.
     */
    @GetMapping("/admin/overview")
    @Transactional(readOnly = true)
    public AdminOverview overview(Authentication a) {
        requireAdmin(a);
        var users = repo.findAll(org.springframework.data.domain.Sort.by("fullName"))
                .stream().map(this::adminView).toList();
        var pending = repo.findByStatusIgnoreCaseOrderByCreatedAtAsc("PENDING")
                .stream().map(this::adminView).toList();
        var caseRows = cases.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"))
                .stream().map(c -> new AdminCaseView(
                        c.getId(), c.getCaseNumber(), c.getTitle(),
                        c.getPriority().name(), c.getStatus().name(),
                        c.getCreatedBy().getId(), c.getCreatedBy().getFullName(),
                        c.getCreatedAt(), c.getUpdatedAt())).toList();
        return new AdminOverview(users, pending, caseRows);
    }

    @PostMapping("/admin/{id}/approve")
    public AdminUserView approve(@PathVariable UUID id, Authentication a) {
        requireAdmin(a);
        var u = repo.findById(id).orElseThrow();
        u.setStatus("APPROVED");
        u.setEnabled(true);
        u.setUpdatedAt(Instant.now());
        audit.log(a, "USER_APPROVED", "USER", id, u.getUsername());
        return adminView(repo.save(u));
    }

    @PostMapping("/admin/{id}/reject")
    public AdminUserView reject(@PathVariable UUID id, Authentication a) {
        requireAdmin(a);
        var u = repo.findById(id).orElseThrow();
        u.setStatus("REJECTED");
        u.setEnabled(false);
        u.setUpdatedAt(Instant.now());
        audit.log(a, "USER_REJECTED", "USER", id, u.getUsername());
        return adminView(repo.save(u));
    }

    @PostMapping("/admin/{id}/suspend")
    public AdminUserView suspend(@PathVariable UUID id, Authentication a) {
        requireAdmin(a);
        var u = repo.findById(id).orElseThrow();
        u.setStatus("SUSPENDED");
        u.setEnabled(false);
        u.setUpdatedAt(Instant.now());
        audit.log(a, "USER_SUSPENDED", "USER", id, u.getUsername());
        return adminView(repo.save(u));
    }

    @PatchMapping("/admin/{id}/role")
    public AdminUserView role(@PathVariable UUID id, @RequestBody Map<String, String> b, Authentication a) {
        requireAdmin(a);
        var u = repo.findById(id).orElseThrow();
        var requested = b.get("role");
        if (requested == null || requested.isBlank()) {
            throw new IllegalArgumentException("Role is required");
        }
        var r = roleRepo.findByCode(RoleCode.valueOf(requested)).orElseThrow();
        u.setRoles(new HashSet<>(Set.of(r)));
        u.setUpdatedAt(Instant.now());
        audit.log(a, "USER_ROLE_UPDATED", "USER", id, r.getCode().name());
        return adminView(repo.save(u));
    }

    private void requireAdmin(Authentication a) {
        var u = repo.findByUsername(a.getName()).orElseThrow();
        if (u.getRoles().stream().noneMatch(r -> r.getCode() == RoleCode.ADMIN)) {
            throw new AccessDeniedException("Admin only");
        }
    }

    private UserView view(AppUser u) {
        return new UserView(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(),
                u.getRoles().stream().map(r -> r.getCode().name()).sorted().toList(), u.getStatus());
    }

    private AdminUserView adminView(AppUser u) {
        var privileged = u.getRoles().stream().anyMatch(r -> Set.of(
                RoleCode.ADMIN, RoleCode.INVESTIGATION_SUPERVISOR, RoleCode.AUDITOR).contains(r.getCode()));
        var assigned = privileged
                ? List.of("ALL CASES")
                : caseMembers.findByUser_Id(u.getId()).stream()
                .map(m -> m.getInvestigationCase().getCaseNumber() + " " + m.getMemberRole().name())
                .sorted().toList();
        return new AdminUserView(
                u.getId(), u.getUsername(), u.getFullName(), u.getEmail(),
                u.getRoles().stream().map(r -> r.getCode().name()).sorted().toList(),
                u.getStatus(), u.isEnabled(), u.getRequestedRole(), assigned,
                u.getCreatedAt(), u.getUpdatedAt());
    }

    public record AdminUserView(UUID id, String username, String fullName, String email,
                                List<String> roles, String status, boolean enabled,
                                String requestedRole, List<String> accessibleCases,
                                Instant createdAt, Instant updatedAt) {}

    public record AdminCaseView(UUID id, String caseNumber, String title, String priority,
                                String status, UUID createdBy, String createdByName,
                                Instant createdAt, Instant updatedAt) {}

    public record AdminOverview(List<AdminUserView> users,
                                List<AdminUserView> pending,
                                List<AdminCaseView> cases) {}
}
