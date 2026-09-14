package com.cni.auth;

import com.cni.role.*;
import com.cni.user.*;
import lombok.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

@RestController @RequestMapping("/api/v1/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager; private final UserRepository userRepository; private final RoleRepository roleRepository; private final JwtService jwtService; private final PasswordEncoder encoder;
    @PostMapping("/login") public LoginResponse login(@Valid @RequestBody LoginRequest r){
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(r.username(),r.password()));
        var u=userRepository.findByUsername(r.username()).orElseThrow(); var roles=roles(u);
        return new LoginResponse(jwtService.generateToken(u.getUsername(),roles),"Bearer",jwtService.ttlSeconds(),toUser(u));
    }
    @GetMapping("/me") public UserView me(Authentication a){return toUser(userRepository.findByUsername(a.getName()).orElseThrow());}
    @PostMapping("/signup") public Map<String,Object> signup(@Valid @RequestBody SignupRequest r){
        if(userRepository.existsByUsername(r.username())) throw new IllegalArgumentException("Username already exists");
        var requested=RoleCode.valueOf(r.requestedRole());
        if(requested==RoleCode.ADMIN) throw new IllegalArgumentException("Admin registration is restricted");
        var role=roleRepository.findByCode(requested).orElseThrow(); var now=Instant.now();
        var u=userRepository.save(AppUser.builder().username(r.username()).passwordHash(encoder.encode(r.password())).fullName(r.fullName()).email(r.email()).enabled(false).status("PENDING").requestedRole(requested.name()).roles(new HashSet<>(Set.of(role))).createdAt(now).updatedAt(now).build());
        return Map.of("status","PENDING","userId",u.getId(),"message","Registration submitted for administrator approval.");
    }
    private UserView toUser(AppUser u){return new UserView(u.getId(),u.getUsername(),u.getFullName(),u.getEmail(),roles(u),u.getStatus());}
    private List<String> roles(AppUser u){return u.getRoles().stream().map(x->x.getCode().name()).sorted().toList();}
    public record LoginRequest(@NotBlank String username,@NotBlank String password){}
    public record SignupRequest(@NotBlank String username,@NotBlank @Size(min=6) String password,@NotBlank String fullName,String email,@NotBlank String requestedRole){}
    public record LoginResponse(String token,String tokenType,long expiresIn,UserView user){}
    public record UserView(UUID id,String username,String fullName,String email,List<String> roles,String status){}
}
