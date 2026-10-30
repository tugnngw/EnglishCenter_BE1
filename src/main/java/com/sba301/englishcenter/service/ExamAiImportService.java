package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.request.AiImportRequest;
import com.sba301.englishcenter.dto.response.AiImportResponse;

/**
 * Dev 3 - AI đọc file đề (PDF / Word / text) rồi trả bản nháp để admin xem trước:
 * đề thi viết theo mẫu nhập đề, hoặc danh sách đề luyện Writing / Speaking (AI Practice).
 * Không ghi gì vào DB — admin bấm Tạo mới gọi các API tạo đề sẵn có.
 */
public interface ExamAiImportService {

    AiImportResponse parse(AiImportRequest request);
}
