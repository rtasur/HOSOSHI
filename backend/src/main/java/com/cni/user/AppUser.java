package com.cni.user;

import com.cni.role.Role;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "app_users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable=false, unique=true, length=100) private String username;
    @Column(name="password_hash", nullable=false) private String passwordHash;
    @Column(name="full_name", nullable=false, length=150) private String fullName;
    @Column(length=200) private String email;
    @Column(nullable=false) @Builder.Default private boolean enabled=true;
    @Column(nullable=false, length=24) @Builder.Default private String status="APPROVED";
    @Column(name="requested_role", length=64) private String requestedRole;
    @ManyToMany(fetch=FetchType.EAGER) @JoinTable(name="user_roles", joinColumns=@JoinColumn(name="user_id"), inverseJoinColumns=@JoinColumn(name="role_id"))
    @Builder.Default private Set<Role> roles=new HashSet<>();
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
}
