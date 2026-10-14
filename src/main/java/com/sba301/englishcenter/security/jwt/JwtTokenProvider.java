package com.sba301.englishcenter.security.jwt;

import com.sba301.englishcenter.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.signer-key}")
    private String signerKey;

    @Getter
    @Value("${jwt.access-token-expiry:900000}")
    private long accessTokenExpiry;

    @Getter
    @Value("${jwt.refresh-token-expiry:604800000}")
    private long refreshTokenExpiry;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(signerKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UserPrincipal principal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiry);
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(principal.getEmail())
                .id(jti)
                .claim("userId", principal.getUserId().toString())
                .claim("role", principal.getRoleName())
                .claim("tokenType", "ACCESS")
                .issuer("english-center")
                .audience().add("english-center-api").and()
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(UserPrincipal principal) {
        return generateRefreshToken(principal, UUID.randomUUID().toString());
    }

    public String generateRefreshToken(UserPrincipal principal, String tokenId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiry);

        return Jwts.builder()
                .subject(principal.getEmail())
                .id(tokenId)
                .claim("userId", principal.getUserId().toString())
                .claim("tokenType", "REFRESH")
                .issuer("english-center")
                .audience().add("english-center-api").and()
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public static String hashToken(String token) {
        if (token == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUsernameFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    public String getJtiFromToken(String token) {
        return getClaimsFromToken(token).getId();
    }

    public UUID getUserIdFromToken(String token) {
        String userIdStr = getClaimsFromToken(token).get("userId", String.class);
        return userIdStr != null ? UUID.fromString(userIdStr) : null;
    }

    public Date getExpirationFromToken(String token) {
        return getClaimsFromToken(token).getExpiration();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.error("JWT validation error: {}", ex.getMessage());
            return false;
        }
    }
}
