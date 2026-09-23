package com.cni.casefile;

import com.cni.role.RoleCode;
import com.cni.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaseAccessService {
    private final CaseRepository cases;
    private final CaseMemberRepository members;
    private final UserRepository users;

    public void requireAnyRole(Authentication authentication, RoleCode... allowed) {
        var u = users.findByUsername(authentication.getName()).orElseThrow();
        boolean allowedRole = u.getRoles().stream().anyMatch(r -> Set.of(allowed).contains(r.getCode()));
        if (!allowedRole) {
            throw new AccessDeniedException("Insufficient role");
        }
    }

    public boolean hasAnyRole(Authentication authentication, RoleCode... allowed) {
        var u = users.findByUsername(authentication.getName()).orElseThrow();
        return u.getRoles().stream().anyMatch(r -> Set.of(allowed).contains(r.getCode()));
    }

    /**
     * Returns active cases visible to the current user. This is intentionally
     * simple and prototype-friendly; PostgreSQL remains the source of truth.
     */
    public List<InvestigationCase> accessibleActiveCases(Authentication authentication) {
        var u = users.findByUsername(authentication.getName()).orElseThrow();
        boolean globalAccess = u.getRoles().stream()
                .anyMatch(r -> Set.of(RoleCode.SUPER_ADMIN, RoleCode.AUDITOR).contains(r.getCode()));

        if (globalAccess) {
            return cases.findByDeletedAtIsNull();
        }

        return cases.findByDeletedAtIsNull().stream()
                .filter(c -> c.getCreatedBy().getId().equals(u.getId())
                        || members.existsByInvestigationCase_IdAndUser_Id(c.getId(), u.getId()))
                .toList();
    }

    public InvestigationCase require(UUID caseId, Authentication a) {
        var c = cases.findById(caseId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Case not found"));
        if (c.getDeletedAt() != null) {
            throw new java.util.NoSuchElementException("Case is in Bin");
        }
        var u = users.findByUsername(a.getName()).orElseThrow();
        boolean globalAccess = u.getRoles().stream()
                .anyMatch(r -> Set.of(RoleCode.SUPER_ADMIN, RoleCode.AUDITOR).contains(r.getCode()));

        if (globalAccess) return c;

        if (u.getRoles().stream().anyMatch(r -> r.getCode() == RoleCode.INVESTIGATION_SUPERVISOR)) {
            if (c.getCreatedBy().getId().equals(u.getId())
                    || members.existsByInvestigationCase_IdAndUser_Id(caseId, u.getId())) return c;
            throw new AccessDeniedException("This case has not been assigned to you");
        }

        if (members.existsByInvestigationCase_IdAndUser_Id(caseId, u.getId())) return c;
        throw new AccessDeniedException("You are not assigned to this case");
    }
}
