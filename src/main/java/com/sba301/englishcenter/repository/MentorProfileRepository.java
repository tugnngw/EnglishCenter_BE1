package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.MentorProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
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

    // ===== Thống kê cho trang quản lý Mentor (admin) =====

    @Query("select count(m) from MentorProfile m where m.user.role.roleName = 'MENTOR'")
    long countMentors();

    @Query("select count(m) from MentorProfile m where m.user.role.roleName = 'MENTOR' and m.user.isActive = :isActive")
    long countMentorsByActive(@Param("isActive") Boolean isActive);

    /** Mentor tham gia từ mốc {@code since}; dữ liệu cũ chưa có mentor_registered_at thì lấy created_at */
    @Query("select count(m) from MentorProfile m where m.user.role.roleName = 'MENTOR' "
            + "and coalesce(m.mentorRegisteredAt, m.createdAt) >= :since")
    long countMentorsJoinedSince(@Param("since") LocalDateTime since);

    /** Các department đang dùng (để admin lọc đúng theo dữ liệu thật) */
    @Query("select distinct trim(m.department) from MentorProfile m where m.user.role.roleName = 'MENTOR' "
            + "and m.department is not null and trim(m.department) <> '' order by trim(m.department)")
    List<String> findDistinctDepartments();
}
