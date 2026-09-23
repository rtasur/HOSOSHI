package com.cni.common;

import com.cni.role.Role;
import com.cni.role.RoleCode;
import com.cni.role.RoleRepository;
import com.cni.user.AppUser;
import com.cni.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {
    @Value("${app.bootstrap-admin.username:admin}")
    private String adminUsername;

    @Value("${app.bootstrap-admin.password:123456}")
    private String adminPassword;

    @Value("${app.bootstrap-demo-users.enabled:true}")
    private boolean demoUsersEnabled;

    @Bean
    CommandLineRunner seed(RoleRepository roles, UserRepository users, PasswordEncoder encoder) {
        return args -> {
            Map<RoleCode, Role> roleMap = new EnumMap<>(RoleCode.class);
            for (RoleCode code : RoleCode.values()) {
                Role role = roles.findByCode(code).orElseGet(() -> roles.save(
                        Role.builder()
                                .code(code)
                                .displayName(display(code))
                                .description(description(code))
                                .build()));
                roleMap.put(code, role);
            }

            ensureSuperAdmin(users, roleMap.get(RoleCode.SUPER_ADMIN), encoder);

            if (demoUsersEnabled) {
                ensureDemoUser(users, roleMap.get(RoleCode.INVESTIGATION_SUPERVISOR), encoder,
                        "supervisor", "supervisor123", "Hososhi Investigation Supervisor", "supervisor@hososhi.local");
                ensureDemoUser(users, roleMap.get(RoleCode.INVESTIGATOR), encoder,
                        "investigator", "investigator123", "Hososhi Investigator", "investigator@hososhi.local");
                ensureDemoUser(users, roleMap.get(RoleCode.INTELLIGENCE_ANALYST), encoder,
                        "analyst", "analyst123", "Hososhi Intelligence Analyst", "analyst@hososhi.local");
                ensureDemoUser(users, roleMap.get(RoleCode.DATA_OPERATOR), encoder,
                        "operator", "operator123", "Hososhi Data Operator", "operator@hososhi.local");
                ensureDemoUser(users, roleMap.get(RoleCode.AUDITOR), encoder,
                        "auditor", "auditor123", "Hososhi Auditor", "auditor@hososhi.local");
            }
        };
    }

    private void ensureSuperAdmin(UserRepository users, Role role, PasswordEncoder encoder) {
        var existing = users.findByUsername(adminUsername);
        if (existing.isEmpty()) {
            var now = Instant.now();
            users.save(AppUser.builder()
                    .username(adminUsername)
                    .passwordHash(encoder.encode(adminPassword))
                    .fullName("Hososhi Super Administrator")
                    .email("admin@hososhi.local")
                    .enabled(true)
                    .status("APPROVED")
                    .requestedRole(RoleCode.SUPER_ADMIN.name())
                    .roles(new HashSet<>(Set.of(role)))
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            return;
        }

        var user = existing.get();
        boolean changed = false;
        if (!user.isEnabled()) {
            user.setEnabled(true);
            changed = true;
        }
        if (!"APPROVED".equalsIgnoreCase(user.getStatus())) {
            user.setStatus("APPROVED");
            changed = true;
        }
        if (!RoleCode.SUPER_ADMIN.name().equals(user.getRequestedRole())) {
            user.setRequestedRole(RoleCode.SUPER_ADMIN.name());
            changed = true;
        }
        if (user.getRoles().size() != 1 || user.getRoles().stream().noneMatch(r -> r.getCode() == RoleCode.SUPER_ADMIN)) {
            user.setRoles(new HashSet<>(Set.of(role)));
            changed = true;
        }
        if (changed) {
            user.setUpdatedAt(Instant.now());
            users.save(user);
        }
    }

    private void ensureDemoUser(UserRepository users, Role role, PasswordEncoder encoder, String username, String password,
                                String fullName, String email) {
        if (users.existsByUsername(username)) {
            return;
        }
        var now = Instant.now();
        // Demo credentials exist only for the local prototype and are intentionally
        // created only when the account does not already exist.
        users.save(AppUser.builder()
                .username(username)
                .passwordHash(encoder.encode(password))
                .fullName(fullName)
                .email(email)
                .enabled(true)
                .status("APPROVED")
                .requestedRole(role.getCode().name())
                .roles(new HashSet<>(Set.of(role)))
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private String display(RoleCode code) {
        return switch (code) {
            case SUPER_ADMIN -> "Super Admin";
            case INVESTIGATION_SUPERVISOR -> "Investigation Supervisor";
            case INVESTIGATOR -> "Investigator";
            case INTELLIGENCE_ANALYST -> "Intelligence Analyst";
            case DATA_OPERATOR -> "Data Operator";
            case AUDITOR -> "Auditor";
        };
    }

    private String description(RoleCode code) {
        return switch (code) {
            case SUPER_ADMIN -> "Full platform administration";
            case INVESTIGATION_SUPERVISOR -> "Create, assign and supervise investigations";
            case INVESTIGATOR -> "Investigate authorized cases and evidence";
            case INTELLIGENCE_ANALYST -> "Perform graph and intelligence analysis";
            case DATA_OPERATOR -> "Import and validate intelligence data";
            case AUDITOR -> "Read-only oversight and audit";
        };
    }
}
