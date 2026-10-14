package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.MentorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MentorProfileRepository extends JpaRepository<MentorProfile, UUID>, JpaSpecificationExecutor<MentorProfile> {
}
