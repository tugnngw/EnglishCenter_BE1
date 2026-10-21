package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.MentorProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentorProfileRepository extends JpaRepository<MentorProfile, UUID>, JpaSpecificationExecutor<MentorProfile> {

    @Override
    @NonNull
    @EntityGraph(attributePaths = {"user", "user.role"})
    Page<MentorProfile> findAll(@Nullable Specification<MentorProfile> spec, @NonNull Pageable pageable);

    @Override
    @NonNull
    @EntityGraph(attributePaths = {"user", "user.role"})
    Optional<MentorProfile> findById(@NonNull UUID id);
}
