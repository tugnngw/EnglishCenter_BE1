package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.response.ProficiencyResponse;

import java.util.List;
import java.util.UUID;

/**
 * Dev 3 - Năng lực của học viên theo chứng chỉ, xác định từ bài kiểm tra năng lực
 * (đề thi thử loại placement_test) gần nhất đã nộp.
 * <p>
 * Dev 5 (lộ trình / lịch học) gọi {@link #getProficiency(UUID, UUID)} để biết học viên đang ở đâu
 * so với mục tiêu.
 */
public interface ProficiencyService {

    /** Năng lực của học viên đang đăng nhập; certificateId null = mọi chứng chỉ đã kiểm tra. */
    List<ProficiencyResponse> getMyProficiency(UUID certificateId);

    /** Năng lực của một học viên bất kỳ (dùng nội bộ cho module khác); certificateId null = tất cả. */
    List<ProficiencyResponse> getProficiency(UUID studentId, UUID certificateId);
}
