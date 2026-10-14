package com.sba301.englishcenter.config;

import com.sba301.englishcenter.common.enums.RoleName;
import com.sba301.englishcenter.entity.Role;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.repository.RoleRepository;
import com.sba301.englishcenter.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Initializes admin account from environment variables on startup.
 * This replaces the hardcoded admin credentials in migrations.
 *
 * Required environment variables:
 * - ADMIN_EMAIL: Admin email address
 * - ADMIN_PASSWORD: Admin password (must be strong)
 * - ADMIN_FULL_NAME: Admin full name
 * - ADMIN_PHONE: Admin phone number
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:}")
    private String adminEmail;

    @Value("${admin.password:}")
    private String adminPassword;

    @Value("${admin.full-name:System Administrator}")
    private String adminFullName;

    @Value("${admin.phone:}")
    private String adminPhone;

    @Override
    public void run(String... args) {
        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) {
            log.warn("Admin credentials not provided via environment variables. No admin account will be created.");
            log.warn("Set ADMIN_EMAIL and ADMIN_PASSWORD environment variables to create an admin account.");
            return;
        }

        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin account with email {} already exists. Skipping initialization.", adminEmail);
            return;
        }

        Role adminRole = roleRepository.findByRoleName(RoleName.ADMIN.name())
                .orElseThrow(() -> new IllegalStateException("ADMIN role not found in database"));

        User admin = User.builder()
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .fullName(adminFullName)
                .phone(adminPhone)
                .role(adminRole)
                .isActive(true)
                .failedLoginAttempts(0)
                .build();

        userRepository.save(admin);
        log.info("Admin account created successfully with email: {}", adminEmail);
        log.warn("IMPORTANT: Change the admin password after first login!");
    }
}
