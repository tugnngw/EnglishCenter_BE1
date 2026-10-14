package com.sba301.englishcenter.security;

import com.sba301.englishcenter.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String password;
    private final String roleName;
    private final boolean active;
    private final LocalDateTime lockedUntil;
    private final Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal create(User user) {
        String roleName = user.getRole().getRoleName().toUpperCase();
        Collection<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + roleName)
        );

        return UserPrincipal.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .password(user.getPassword())
                .roleName(roleName)
                .active(Boolean.TRUE.equals(user.getIsActive()))
                .lockedUntil(user.getLockedUntil())
                .authorities(authorities)
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return lockedUntil == null || lockedUntil.isBefore(LocalDateTime.now());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
