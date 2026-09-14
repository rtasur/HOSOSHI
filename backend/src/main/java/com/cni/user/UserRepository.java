package com.cni.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface UserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByUsername(String username);
    List<AppUser> findAllByEnabledTrueOrderByFullNameAsc();
    boolean existsByUsername(String username);
    List<AppUser> findByStatusOrderByCreatedAtAsc(String status);
    List<AppUser> findByStatusIgnoreCaseOrderByCreatedAtAsc(String status);
}
