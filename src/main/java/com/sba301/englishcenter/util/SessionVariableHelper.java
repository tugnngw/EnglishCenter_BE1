package com.sba301.englishcenter.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Helper class to set PostgreSQL session variables for database triggers.
 * Used to pass current user ID to triggers that need to validate operations.
 */
@Component
@Slf4j
public class SessionVariableHelper {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Set the current user ID in PostgreSQL session variable.
     * This variable is read by database triggers to validate operations.
     *
     * @param userId Current authenticated user's ID
     */
    @Transactional
    public void setCurrentUserId(UUID userId) {
        if (userId == null) {
            log.warn("Attempted to set null userId in session variable");
            return;
        }

        entityManager.createNativeQuery("SELECT set_config('app.current_user_id', :userId, true)")
                .setParameter("userId", userId.toString())
                .getSingleResult();
        log.debug("Set session variable app.current_user_id = {}", userId);
    }
}
