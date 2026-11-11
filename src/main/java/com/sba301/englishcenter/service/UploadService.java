package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.request.UploadSignatureRequest;
import com.sba301.englishcenter.dto.response.UploadSignatureResponse;

/**
 * Cấp chữ ký upload Cloudinary cho bài tập.
 * <p>
 * BE là người quyết định {@code resource_type} và {@code folder} — client KHÔNG
 * tự chọn được, nhờ vậy không thể đẩy file sai loại lên chỗ không phù hợp.
 * <p>
 * Quy tắc phân loại (theo quyết định đã chốt):
 * <ul>
 *     <li>ảnh (png/jpg/jpeg/webp/gif) → {@code image};</li>
 *     <li>PDF / Word / Excel / PowerPoint / txt → {@code raw};</li>
 *     <li>audio / video (mp3/wav/m4a/mp4/mov/webm) → {@code video}.</li>
 * </ul>
 * <p>
 * Mục đích {@code lesson_video} (video bài học) → thư mục
 * {@code courses/{courseId}/lessons}, delivery type {@code upload} (public).
 */
public interface UploadService {

    /**
     * Cấp chữ ký upload.
     *
     * @param request mục đích + thông tin file
     */
    UploadSignatureResponse createSignature(UploadSignatureRequest request);
}
