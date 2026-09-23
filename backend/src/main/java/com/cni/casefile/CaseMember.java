package com.cni.casefile;

import com.cni.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "case_members")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CaseMember {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "case_id", nullable = false)
    private InvestigationCase investigationCase;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @Enumerated(EnumType.STRING) @Column(name = "member_role", nullable = false, length = 32)
    private CaseMemberRole memberRole;
    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;
}
