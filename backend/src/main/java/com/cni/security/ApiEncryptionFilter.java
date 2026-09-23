package com.cni.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Optional application-layer AES-256-GCM payload protection for JSON API bodies.
 * TLS remains the required transport security control; this wrapper is an
 * additional prototype control and is intentionally disabled unless configured.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiEncryptionFilter extends jakarta.servlet.http.HttpFilter {
    private static final String HEADER = "X-Hososhi-Encrypted";
    private static final String PREFIX = "HOSOSHI1.";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final boolean enabled;
    private final byte[] key;

    public ApiEncryptionFilter(
            @Value("${app.api-encryption.enabled:false}") boolean enabled,
            @Value("${app.api-encryption.key:}") String encodedKey) {
        this.enabled = enabled;
        if (!enabled) {
            this.key = null;
            return;
        }
        try {
            this.key = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("app.api-encryption.key must be Base64 encoded", ex);
        }
        if (this.key.length != 32) {
            throw new IllegalStateException("app.api-encryption.key must decode to exactly 32 bytes");
        }
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!enabled || !isApiPayload(request) || !HEADER.equalsIgnoreCase(request.getHeader(HEADER))) {
            // Only opt-in clients receive application-layer encryption. This preserves
            // compatibility with Swagger/manual API clients and existing unencrypted calls.
            chain.doFilter(request, response);
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            byte[] encrypted = request.getInputStream().readAllBytes();
            byte[] plaintext = decrypt(encrypted);
            HttpServletRequest wrappedRequest = new BodyRequestWrapper(request, plaintext);
            wrappedRequest.setAttribute(HEADER, "true");

            chain.doFilter(wrappedRequest, wrappedResponse);
            byte[] body = wrappedResponse.getContentAsByteArray();
            if (isJsonResponse(wrappedResponse) && body.length > 0 && !isAlreadyEncrypted(body)) {
                byte[] encryptedResponse = encrypt(body);
                wrappedResponse.resetBuffer();
                wrappedResponse.setHeader(HEADER, "true");
                wrappedResponse.setContentType(MediaType.TEXT_PLAIN_VALUE);
                wrappedResponse.setContentLength(encryptedResponse.length);
                ServletOutputStream out = wrappedResponse.getOutputStream();
                out.write(encryptedResponse);
                out.flush();
            }
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            wrappedResponse.resetBuffer();
            wrappedResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            wrappedResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            wrappedResponse.getWriter().write("{\"message\":\"Invalid encrypted API payload\"}");
        } finally {
            wrappedResponse.copyBodyToResponse();
        }
    }

    private boolean isApiPayload(HttpServletRequest request) {
        String path = request.getRequestURI();
        String api = request.getContextPath() + "/api/v1/";
        String auth = request.getContextPath() + "/api/v1/auth/";
        return path.startsWith(api)
                && !path.startsWith(auth)
                && !path.contains("/health")
                && !path.startsWith(request.getContextPath() + "/api/v1/swagger")
                && !path.startsWith(request.getContextPath() + "/api/v1/v3/");
    }

    private boolean isJson(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith(MediaType.APPLICATION_JSON_VALUE);
    }

    private boolean isJsonResponse(HttpServletResponse response) {
        String contentType = response.getContentType();
        return contentType != null && contentType.toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private boolean isAlreadyEncrypted(byte[] body) {
        return new String(body, 0, Math.min(body.length, PREFIX.length()), StandardCharsets.UTF_8).startsWith(PREFIX);
    }

    private byte[] encrypt(byte[] plaintext) throws GeneralSecurityException {
        byte[] iv = new byte[IV_LENGTH];
        new java.security.SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);
        String payload = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
                + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] decrypt(byte[] payload) throws GeneralSecurityException {
        String value = new String(payload, StandardCharsets.UTF_8).trim();
        if (!value.startsWith(PREFIX)) throw new IllegalArgumentException("Invalid payload prefix");
        String[] parts = value.substring(PREFIX.length()).split("\\.", 2);
        if (parts.length != 2) throw new IllegalArgumentException("Invalid payload format");
        byte[] iv = Base64.getUrlDecoder().decode(parts[0]);
        byte[] ciphertext = Base64.getUrlDecoder().decode(parts[1]);
        if (iv.length != IV_LENGTH) throw new IllegalArgumentException("Invalid IV");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        return cipher.doFinal(ciphertext);
    }

    private static final class BodyRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] body;
        BodyRequestWrapper(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public String getContentType() { return MediaType.APPLICATION_JSON_VALUE; }
        @Override public ServletInputStream getInputStream() {
            final ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] b, int off, int len) { return input.read(b, off, len); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener readListener) {
                    if (readListener == null) throw new IllegalArgumentException("readListener must not be null");
                    try {
                        if (isFinished()) readListener.onAllDataRead();
                        else readListener.onDataAvailable();
                    } catch (IOException ex) {
                        readListener.onError(ex);
                    }
                }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
    }
}
