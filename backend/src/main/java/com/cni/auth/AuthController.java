package com.cni.auth;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cni.audit.AuditService;
import com.cni.role.Role;
import com.cni.role.RoleCode;
import com.cni.role.RoleRepository;
import com.cni.user.AppUser;
import com.cni.user.UserRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtService jwtService;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            audit.log(null, "LOGIN_FAILED", "AUTH", null, "Authentication failed");
            throw ex;
        }

        AppUser user = userRepository.findByUsername(request.username()).orElseThrow();
        List<String> roles = roles(user);
        String token = jwtService.generateToken(user.getUsername(), roles);

        audit.log(null, "LOGIN_SUCCESS", "USER", user.getId(), user.getUsername());
        return new LoginResponse(token, "Bearer", jwtService.ttlSeconds(), toUser(user));
    }

    @GetMapping("/me")
    public UserView me(Authentication authentication) {
        return toUser(userRepository.findByUsername(authentication.getName()).orElseThrow());
    }

    @PostMapping("/signup")
    public Map<String, Object> signup(@Valid @RequestBody SignupRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username already exists");
        }

        RoleCode requested = RoleCode.valueOf(request.requestedRole());
        if (requested == RoleCode.SUPER_ADMIN) {
            throw new IllegalArgumentException("Super Admin registration is restricted");
        }

        Role role = roleRepository.findByCode(requested).orElseThrow();
        Instant now = Instant.now();

        AppUser user = userRepository.save(AppUser.builder()
            .username(request.username())
            .passwordHash(encoder.encode(request.password()))
            .fullName(request.fullName())
            .email(request.email())
            .enabled(false)
            .status("PENDING")
            .requestedRole(requested.name())
            .roles(new HashSet<>(Set.of(role)))
            .createdAt(now)
            .updatedAt(now)
            .build());

        audit.log(null, "USER_SIGNUP_REQUESTED", "USER", user.getId(), user.getUsername());

        return Map.of(
            "status", "PENDING",
            "userId", user.getId(),
            "message", "Registration submitted for administrator approval.");
    }

    private UserView toUser(AppUser user) {
        return new UserView(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.getEmail(),
            roles(user),
            user.getStatus());
    }

    private List<String> roles(AppUser user) {
        return user.getRoles().stream()
            .map(role -> role.getCode().name())
            .sorted()
            .toList();
    }

    public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
    }

    public record SignupRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 6) String password,
        @NotBlank String fullName,
        String email,
        @NotBlank String requestedRole) {
    }

    public record LoginResponse(
        String token,
        String tokenType,
        long expiresIn,
        UserView user) {
    }

    public record UserView(
        UUID id,
        String username,
        String fullName,
        String email,
        List<String> roles,
        String status) {
    }
}
