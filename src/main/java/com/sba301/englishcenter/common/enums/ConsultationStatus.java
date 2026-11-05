package com.sba301.englishcenter.common.enums;

/**
 * Trạng thái yêu cầu tư vấn (cột consultation_requests.status, VARCHAR - V21).
 * pending: học viên vừa gửi, chưa mentor nào nhận · contacted: mentor đã nhận và đang liên hệ ·
 * agreed: đã chốt lộ trình, mentor tạo gói kèm 1-1 · closed: kết thúc không chốt.
 */
public enum ConsultationStatus {
    pending,
    contacted,
    agreed,
    closed
}
