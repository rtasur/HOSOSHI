package com.cni.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long ttlMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.ttl-minutes:120}") long ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
        var bytes = secret.getBytes(StandardCharsets.UTF_8);
        var key = new SecretKeySpec(bytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    public String generateToken(String username, List<String> roles) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
            .subject(username)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(ttlMinutes * 60))
            .claim("roles", roles)
            .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    public long ttlSeconds() {
        return ttlMinutes * 60;
    }
}
