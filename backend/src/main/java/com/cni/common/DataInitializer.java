package com.cni.common;

import com.cni.role.Role;
import com.cni.role.RoleCode;
import com.cni.role.RoleRepository;
import com.cni.user.AppUser;
import com.cni.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import java.time.Instant; import java.util.*;

@Configuration @RequiredArgsConstructor
public class DataInitializer {
    @Value("${app.bootstrap-admin.username:admin}") private String adminUsername;
    @Value("${app.bootstrap-admin.password:123456}") private String adminPassword;
    @Bean CommandLineRunner seed(RoleRepository roles, UserRepository users, PasswordEncoder encoder){return args->{
        Map<RoleCode,Role> map=new EnumMap<>(RoleCode.class);
        for(RoleCode code:RoleCode.values()){ final var c=code; map.put(code,roles.findByCode(code).orElseGet(()->roles.save(Role.builder().code(c).displayName(display(c)).description(description(c)).build()))); }
        if(!users.existsByUsername(adminUsername)){var now=Instant.now(); users.save(AppUser.builder().username(adminUsername).passwordHash(encoder.encode(adminPassword)).fullName("Hososhi Administrator").email("admin@hososhi.local").enabled(true).status("APPROVED").requestedRole("ADMIN").roles(new HashSet<>(Set.of(map.get(RoleCode.ADMIN)))).createdAt(now).updatedAt(now).build());}
    };}
    private String display(RoleCode c){return switch(c){case ADMIN->"Admin";case INVESTIGATION_SUPERVISOR->"Investigation Supervisor";case INVESTIGATOR->"Investigator";case INTELLIGENCE_ANALYST->"Intelligence Analyst";case DATA_OPERATOR->"Data Operator";case AUDITOR->"Auditor";};}
    private String description(RoleCode c){return switch(c){case ADMIN->"Full platform administration";case INVESTIGATION_SUPERVISOR->"Create, assign and supervise investigations";case INVESTIGATOR->"Investigate authorized cases and evidence";case INTELLIGENCE_ANALYST->"Perform graph and intelligence analysis";case DATA_OPERATOR->"Import and validate intelligence data";case AUDITOR->"Read-only oversight and audit";};}
}
