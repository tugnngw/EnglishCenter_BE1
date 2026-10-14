package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.common.enums.AuthTokenType;
import com.sba301.englishcenter.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    Optional<AuthToken> findByTokenHashAndTokenType(String tokenHash, AuthTokenType tokenType);

    boolean existsByTokenHashAndTokenType(String tokenHash, AuthTokenType tokenType);

    boolean existsByTokenHashAndTokenTypeAndRevokedFalse(String tokenHash, AuthTokenType tokenType);

    void deleteByExpiresAtBefore(LocalDateTime time);

    void deleteAllByUser_UserIdAndTokenType(UUID userId, AuthTokenType tokenType);
}
