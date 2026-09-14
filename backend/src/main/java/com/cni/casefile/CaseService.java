package com.cni.casefile;

import com.cni.audit.AuditService;
import com.cni.role.RoleCode;
import com.cni.session.InvestigationSession;
import com.cni.session.SessionRepository;
import com.cni.user.AppUser;
import com.cni.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CaseService {
    private final CaseRepository caseRepository;
    private final CaseMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SessionRepository sessionRepository;

    @Transactional(readOnly = true)
    public List<CaseResponse> list(Authentication a, String sort, String priority) {
        var u = currentUser(a);
        var all = caseRepository.findByDeletedAtIsNull();

        List<InvestigationCase> visible;
        if (hasRole(u, RoleCode.ADMIN, RoleCode.AUDITOR)) {
            visible = all;
        } else if (hasRole(u, RoleCode.INVESTIGATION_SUPERVISOR)) {
            visible = all.stream()
                    .filter(c -> c.getCreatedBy().getId().equals(u.getId())
                            || memberRepository.existsByInvestigationCase_IdAndUser_Id(c.getId(), u.getId()))
                    .toList();
        } else {
            visible = all.stream()
                    .filter(c -> memberRepository.existsByInvestigationCase_IdAndUser_Id(c.getId(), u.getId()))
                    .toList();
        }

        if (priority != null && !priority.isBlank()) {
            try {
                var p = CasePriority.valueOf(priority);
                visible = visible.stream().filter(c -> c.getPriority() == p).toList();
            } catch (Exception ignored) {
                // Ignore invalid filter values.
            }
        }

        var sorted = new ArrayList<>(visible);
        var mode = sort == null ? "priority-desc" : sort;
        Comparator<InvestigationCase> cmp = Comparator.comparingInt(c -> priorityRank(c.getPriority()));
        if (mode.startsWith("priority")) {
            cmp = mode.endsWith("asc") ? cmp : cmp.reversed();
        } else if ("newest".equals(mode)) {
            cmp = Comparator.comparing(InvestigationCase::getCreatedAt).reversed();
        } else if ("oldest".equals(mode)) {
            cmp = Comparator.comparing(InvestigationCase::getCreatedAt);
        } else if ("updated".equals(mode)) {
            cmp = Comparator.comparing(InvestigationCase::getUpdatedAt).reversed();
        }
        sorted.sort(cmp);
        return sorted.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> bin(Authentication a) {
        var u = currentUser(a);
        requireRole(u, RoleCode.ADMIN);
        return caseRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CaseResponse create(CreateCaseRequest r, Authentication a) {
        var u = currentUser(a);
        requireRole(u, RoleCode.ADMIN, RoleCode.INVESTIGATION_SUPERVISOR);
        var now = Instant.now();
        var c = caseRepository.save(InvestigationCase.builder()
                .caseNumber(r.caseNumber() == null || r.caseNumber().isBlank() ? nextCaseNumber() : r.caseNumber())
                .title(r.title())
                .description(r.description())
                .category(r.category())
                .priority(r.priority() == null ? CasePriority.MEDIUM : r.priority())
                .status(r.status() == null ? CaseStatus.OPEN : r.status())
                .classification(r.classification() == null ? CaseClassification.RESTRICTED : r.classification())
                .createdBy(u)
                .createdAt(now)
                .updatedAt(now)
                .deletedAt(null)
                .deletedBy(null)
                .build());

        memberRepository.save(CaseMember.builder()
                .investigationCase(c)
                .user(u)
                .memberRole(CaseMemberRole.SUPERVISOR)
                .assignedAt(now)
                .build());
        auditService.log(a, "CASE_CREATED", "CASE", c.getId(), c.getCaseNumber());
        return toResponse(c);
    }

    @Transactional(readOnly = true)
    public CaseResponse get(UUID id, Authentication a) {
        var c = caseRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Case not found"));
        if (c.getDeletedAt() != null) throw new NoSuchElementException("Case is in Bin");
        assertAccess(c, a);
        return toResponse(c);
    }

    @Transactional
    public void assign(UUID id, AssignRequest r, Authentication a) {
        var c = activeCase(id);
        var u = currentUser(a);
        requireRole(u, RoleCode.ADMIN, RoleCode.INVESTIGATION_SUPERVISOR);

        if (hasRole(u, RoleCode.INVESTIGATION_SUPERVISOR)) {
            assertAccess(c, a);
            if (r.memberRole() != CaseMemberRole.INVESTIGATOR) {
                throw new AccessDeniedException("Investigation Supervisors can assign cases only to Investigators");
            }
        } else if (r.memberRole() != CaseMemberRole.SUPERVISOR && r.memberRole() != CaseMemberRole.INVESTIGATOR) {
            throw new IllegalArgumentException("Admin case assignment supports Supervisor or Investigator roles");
        }

        var target = userRepository.findById(r.userId()).orElseThrow(() -> new NoSuchElementException("User not found"));
        if (!target.isEnabled() || !"APPROVED".equalsIgnoreCase(target.getStatus())) {
            throw new IllegalArgumentException("Only approved and enabled users can be assigned to a case");
        }
        validateMemberRole(target, r.memberRole());

        var existing = memberRepository.findByInvestigationCase_Id(id).stream()
                .filter(m -> m.getUser().getId().equals(target.getId()))
                .findFirst();
        if (existing.isPresent()) {
            existing.get().setMemberRole(r.memberRole());
            existing.get().setAssignedAt(Instant.now());
            memberRepository.save(existing.get());
        } else {
            memberRepository.save(CaseMember.builder()
                    .investigationCase(c).user(target).memberRole(r.memberRole()).assignedAt(Instant.now()).build());
        }
        c.setUpdatedAt(Instant.now());
        caseRepository.save(c);
        auditService.log(a, "CASE_MEMBER_ASSIGNED", "CASE", id, target.getUsername() + ":" + r.memberRole().name());
    }

    @Transactional
    public CaseResponse update(UUID id, UpdateCaseRequest r, Authentication a) {
        var c = activeCase(id);
        assertAccess(c, a);
        if (r.title() != null) c.setTitle(r.title());
        if (r.description() != null) c.setDescription(r.description());
        if (r.category() != null) c.setCategory(r.category());
        if (r.priority() != null) c.setPriority(r.priority());
        if (r.status() != null) c.setStatus(r.status());
        if (r.classification() != null) c.setClassification(r.classification());
        c.setUpdatedAt(Instant.now());
        return toResponse(caseRepository.save(c));
    }

    @Transactional
    public void delete(UUID id, Authentication a) {
        var actor = currentUser(a);
        requireRole(actor, RoleCode.ADMIN);
        var c = activeCase(id);
        var now = Instant.now();
        c.setDeletedAt(now);
        c.setDeletedBy(actor.getId());
        c.setUpdatedAt(now);
        caseRepository.save(c);

        for (var session : sessionRepository.findByInvestigationCase_IdAndStatus(id, "ACTIVE")) {
            session.setStatus("CLOSED");
            session.setUpdatedAt(now);
            sessionRepository.save(session);
        }

        auditService.log(a, "CASE_DELETED_TO_BIN", "CASE", id, c.getCaseNumber());
    }

    @Transactional
    public CaseResponse restore(UUID id, Authentication a) {
        var actor = currentUser(a);
        requireRole(actor, RoleCode.ADMIN);
        var c = caseRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Case not found"));
        if (c.getDeletedAt() == null) return toResponse(c);
        c.setDeletedAt(null);
        c.setDeletedBy(null);
        c.setUpdatedAt(Instant.now());
        var restored = caseRepository.save(c);
        auditService.log(a, "CASE_RESTORED_FROM_BIN", "CASE", id, c.getCaseNumber());
        return toResponse(restored);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> members(UUID id, Authentication a) {
        assertAccess(activeCase(id), a);
        return memberRepository.findByInvestigationCase_Id(id).stream()
                .map(m -> new MemberResponse(
                        m.getUser().getId(), m.getUser().getUsername(), m.getUser().getFullName(),
                        m.getUser().getRoles().stream().map(r -> r.getCode().name()).sorted().toList(),
                        m.getMemberRole().name(), m.getAssignedAt()))
                .toList();
    }

    @Transactional
    public void removeMember(UUID id, UUID userId, Authentication a) {
        var c = activeCase(id);
        var actor = currentUser(a);
        requireRole(actor, RoleCode.ADMIN, RoleCode.INVESTIGATION_SUPERVISOR);
        if (hasRole(actor, RoleCode.INVESTIGATION_SUPERVISOR)) {
            assertAccess(c, a);
            var target = userRepository.findById(userId).orElseThrow(() -> new NoSuchElementException("User not found"));
            if (!hasRole(target, RoleCode.INVESTIGATOR)) {
                throw new AccessDeniedException("Investigation Supervisors can remove only Investigators they supervise");
            }
        }
        memberRepository.deleteByInvestigationCase_IdAndUser_Id(id, userId);
        c.setUpdatedAt(Instant.now());
        caseRepository.save(c);
        auditService.log(a, "CASE_MEMBER_REMOVED", "CASE", id, userId.toString());
    }

    private InvestigationCase activeCase(UUID id) {
        var c = caseRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Case not found"));
        if (c.getDeletedAt() != null) throw new NoSuchElementException("Case is in Bin");
        return c;
    }

    private void validateMemberRole(AppUser target, CaseMemberRole memberRole) {
        boolean ok = switch (memberRole) {
            case SUPERVISOR -> hasRole(target, RoleCode.INVESTIGATION_SUPERVISOR);
            case INVESTIGATOR -> hasRole(target, RoleCode.INVESTIGATOR);
            case ANALYST -> hasRole(target, RoleCode.INTELLIGENCE_ANALYST);
            case DATA_OPERATOR -> hasRole(target, RoleCode.DATA_OPERATOR);
        };
        if (!ok) throw new IllegalArgumentException("User does not have the required global role for this assignment");
    }

    private void assertAccess(InvestigationCase c, Authentication a) {
        if (c.getDeletedAt() != null) throw new NoSuchElementException("Case is in Bin");
        var u = currentUser(a);
        if (hasRole(u, RoleCode.ADMIN, RoleCode.AUDITOR)) return;
        if (hasRole(u, RoleCode.INVESTIGATION_SUPERVISOR)) {
            if (c.getCreatedBy().getId().equals(u.getId()) || memberRepository.existsByInvestigationCase_IdAndUser_Id(c.getId(), u.getId())) return;
            throw new AccessDeniedException("This case has not been assigned to you");
        }
        if (memberRepository.existsByInvestigationCase_IdAndUser_Id(c.getId(), u.getId())) return;
        throw new AccessDeniedException("You are not assigned to this case");
    }

    private void requireRole(AppUser u, RoleCode... rs) {
        if (!hasRole(u, rs)) throw new AccessDeniedException("Insufficient role");
    }

    private boolean hasRole(AppUser u, RoleCode... rs) {
        return u.getRoles().stream().anyMatch(x -> Set.of(rs).contains(x.getCode()));
    }

    private AppUser currentUser(Authentication a) {
        return userRepository.findByUsername(a.getName()).orElseThrow();
    }

    private int priorityRank(CasePriority p) {
        return switch (p) { case LOW -> 1; case MEDIUM -> 2; case HIGH -> 3; case CRITICAL -> 4; };
    }

    private String nextCaseNumber() {
        return "CNI-" + java.time.Year.now().getValue() + "-" + String.format("%04d", caseRepository.count() + 1);
    }

    private CaseResponse toResponse(InvestigationCase c) {
        return new CaseResponse(
                c.getId(), c.getCaseNumber(), c.getTitle(), c.getDescription(), c.getCategory(),
                c.getPriority().name(), c.getStatus().name(), c.getClassification().name(),
                c.getCreatedBy().getId(), c.getCreatedBy().getFullName(), c.getCreatedAt(), c.getUpdatedAt(),
                c.getDeletedAt(), c.getDeletedBy());
    }

    public record CreateCaseRequest(String caseNumber, String title, String description, String category,
                                    CasePriority priority, CaseStatus status, CaseClassification classification) {}
    public record UpdateCaseRequest(String title, String description, String category,
                                    CasePriority priority, CaseStatus status, CaseClassification classification) {}
    public record AssignRequest(UUID userId, CaseMemberRole memberRole) {}
    public record CaseResponse(UUID id, String caseNumber, String title, String description, String category,
                               String priority, String status, String classification, UUID createdBy,
                               String createdByName, Instant createdAt, Instant updatedAt, Instant deletedAt,
                               UUID deletedBy) {}
    public record MemberResponse(UUID id, String username, String fullName, List<String> roles,
                                 String memberRole, Instant assignedAt) {}
}
