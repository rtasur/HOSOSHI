package com.cni.auth;

import com.cni.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.stream.Stream;

@Service @RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    @Override public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user=userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        var authorities=user.getRoles().stream().flatMap(r -> Stream.of(new SimpleGrantedAuthority("ROLE_"+r.getCode().name()), new SimpleGrantedAuthority(r.getCode().name()))).toList();
        return User.builder().username(user.getUsername()).password(user.getPasswordHash()).disabled(!user.isEnabled() || !"APPROVED".equals(user.getStatus())).authorities(authorities).build();
    }
}
