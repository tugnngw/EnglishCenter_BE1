# Dev 5 — Schedule, Booking & Notification (API contract)

Tài liệu bàn giao cho TASK D5-01 → D5-14, D5-16, D5-19 → D5-22.
Đã nối thanh toán SePay `MENTOR_SESSION` của Dev 4 cho booking (mục 5).
Theo `task.md` đã làm thêm: TASK-D5-02 (WebSocket + job nhắc buổi kèm), TASK-D5-01 (lịch học cá nhân),
TASK-D5-03 (Learning Path BE), TASK-D5-04 (AI Practice BE, Gemini Flash) — xem mục 9 → 12.
Chưa làm: D5-15 kiểm tra enrollment cho booking, D5-17 gửi email.

## 1. Mô hình dữ liệu (migration `V6__d5_booking_notification.sql`, `V7__d5_mentor_session_payment.sql`)

> V4 đã dùng cho SePay (Dev 4), V5 cho Exam (Dev 3) nên migration của Dev 5 là **V6, V7**.
> DB local nào đã từng chạy bản V4 cũ của Dev 5 thì phải tạo lại DB trước khi chạy.

| Bảng | Vai trò | Trạng thái |
|---|---|---|
| `mentor_schedules` (có từ V1) | Slot thời gian rảnh của mentor (AvailabilitySlot) | `available` đang mở · `booked` đã có người đặt · `cancelled` mentor đóng slot · `completed` đã dạy xong |
| `bookings` (mới) | 1 lượt học viên đặt 1 slot. Hủy / đổi lịch chỉ đổi trạng thái để giữ lịch sử | `pending` chờ thanh toán · `confirmed` đã thanh toán · `completed` · `cancelled` · `no_show` |
| `payment_orders` (Dev 4, V7 bổ sung) | Thêm `order_type` (`COURSE` / `MENTOR_SESSION`) và `booking_id`; `course_id` cho phép null với đơn buổi kèm | `PENDING` · `PAID` · `CANCELLED` · `EXPIRED` · `REVIEW_REQUIRED` |
| `notifications` (có từ V1) | Thêm cột `is_read`, `read_at`, `related_booking_id` | — |

- Cột `mentor_schedules.student_id` không dùng nữa (học viên lưu ở `bookings`).
- Đặt lịch xong booking là `pending` và slot bị giữ (`booked`) trong thời hạn đơn SePay (`SEPAY_ORDER_EXPIRY_MINUTES`, mặc định 15 phút). Webhook báo đã thanh toán thì booking chuyển `confirmed`.
- Giá slot = hourly_rate × số giờ, **làm tròn tới đồng** vì SePay chỉ nhận số tiền nguyên VND.
- Đổi lịch: booking cũ `cancelled` (lý do "Đổi lịch"), booking mới có `rescheduledFromId` trỏ về booking cũ.

## 2. Chính sách thời gian (D5-19)

- Giờ học (`start_time`, `end_time`), `bookings.cancelled_at`, `notifications.created_at / read_at` **lưu theo UTC** trong DB.
- API **nhận và trả thời điểm ISO-8601 UTC** có hậu tố `Z`, vd `2026-10-25T12:30:00Z`.
- FE gửi `new Date(...).toISOString()` và hiển thị bằng `toLocaleString()` theo múi giờ máy người dùng.
- `created_at / last_updated` của `BaseEntity` vẫn do JPA Auditing ghi theo giờ máy chủ như các module khác; API đổi sang UTC khi trả về.
- Không đổi cấu hình chung (`hibernate.jdbc.time_zone`) để không ảnh hưởng module của dev khác.

## 3. Cấu hình

```properties
# application.properties
# Phải hủy / đổi lịch trước giờ học ít nhất bao nhiêu giờ (admin hủy không bị giới hạn)
app.booking.cancel-deadline-hours=24
```

## 4. API (prefix `/api/v1`)

Response đơn: `ApiResponse { code: 1000, message, result }`. Danh sách có phân trang: `PageResponse { currentPage, pageSize, totalPages, totalElements, data }` (`?page=0&size=20`).

### 4.1 Slot của mentor (D5-02 → D5-05)

| Method | Path | Quyền | Body / Query | Kết quả |
|---|---|---|---|---|
| POST | `/mentor/availability` | MENTOR | `{ startTime, endTime, meetingUrl? }` | `MentorScheduleResponse`. Giá = hourly_rate × số giờ |
| GET | `/mentor/availability` | MENTOR | `page, size` | Page slot chưa kết thúc của mentor, sắp theo giờ bắt đầu |
| PUT | `/mentor/availability/{scheduleId}` | MENTOR | `{ startTime, endTime, meetingUrl?, status }` — `status`: `available` (mở) / `cancelled` (đóng) | Chỉ sửa slot chưa có người đặt |
| DELETE | `/mentor/availability/{scheduleId}` | MENTOR | — | Không xóa được slot còn booking chưa hủy |
| GET | `/mentors/{mentorId}/availability` | Public | `from?, to?` (ISO UTC; mặc định from = hiện tại, to = from + 7 ngày) | List slot `available` + `booked`, chưa tới giờ, **ẩn meetingUrl** |

`MentorScheduleResponse`: `scheduleId, mentorId, startTime, endTime, status, price, meetingUrl`.

### 4.2 Booking (D5-06 → D5-14)

| Method | Path | Quyền | Body / Query | Ghi chú |
|---|---|---|---|---|
| POST | `/bookings` | STUDENT | `{ scheduleId }` | Khóa dòng slot (`SELECT … FOR UPDATE`), chặn đặt trùng slot và học viên trùng giờ. Trả booking `pending` + `paymentOrderCode` |
| GET | `/bookings/{bookingId}` | Học viên / mentor của booking, ADMIN | — | |
| PUT | `/bookings/{bookingId}/cancel` | STUDENT, MENTOR (của booking) | `{ reason? }` (body không bắt buộc) | Chỉ booking `pending/confirmed`, trước giờ học ≥ N giờ. Slot mở lại `available`. Booking `pending` thì hủy luôn đơn SePay đang chờ; booking đã thanh toán **không hoàn tiền tự động** |
| PUT | `/bookings/{bookingId}/reschedule` | STUDENT | `{ newScheduleId }` | Chỉ booking `confirmed` (đã thanh toán), slot mới **cùng mentor và cùng giá**, cùng thời hạn như hủy, 1 transaction. Giữ nguyên khoản đã thanh toán |
| GET | `/me/schedule` | STUDENT | `type=upcoming|past` | upcoming: `pending/confirmed` chưa kết thúc (gần nhất trước); past: còn lại (mới nhất trước) |
| GET | `/mentor/schedule` | MENTOR | `type=upcoming|past` | Như trên, theo mentor |
| PUT | `/mentor/bookings/{bookingId}/status` | MENTOR | `{ status }` — `completed` / `no_show` | Chỉ booking `confirmed`, sau giờ bắt đầu. Slot chuyển `completed` |
| GET | `/admin/bookings` | ADMIN | `status?, mentorId?, studentId?` | Mới nhất trước |
| PUT | `/admin/bookings/{bookingId}/cancel` | ADMIN | `{ reason? }` | Không áp dụng thời hạn hủy |

`BookingResponse`: `bookingId, scheduleId, mentorId, mentorName, studentId, studentName, startTime, endTime, price, meetingUrl, status, cancelReason, cancelledAt, rescheduledFromId, createdAt, paymentOrderCode` (`paymentOrderCode` chỉ có khi booking `pending` và còn đơn đang chờ).

### 4.3 Thông báo trong ứng dụng (D5-16)

| Method | Path | Kết quả |
|---|---|---|
| GET | `/notifications?page&size` | Page `NotificationResponse`, mới nhất trước |
| GET | `/notifications/unread-count` | `number` |
| PUT | `/notifications/{id}/read` | `NotificationResponse` |
| PUT | `/notifications/read-all` | `number` — số thông báo vừa đánh dấu |

`NotificationResponse`: `notificationId, type, title, message, isRead, readAt, createdAt`.
Hệ thống tự tạo thông báo loại `mentor_session` cho **cả học viên và mentor** khi thanh toán xong (đặt lịch thành công), hủy, đổi lịch. Booking bị hủy vì không thanh toán chỉ báo cho học viên.

## 5. Thanh toán buổi kèm 1-1 (SePay `MENTOR_SESSION`)

Dùng lại toàn bộ hạ tầng SePay của Dev 4 (`docs/DEV4_SEPAY.md`): cấu hình `.env`, webhook HMAC, mã đơn `EC` + 10 số, QR `vietqr.app`.

```
POST /bookings {scheduleId}
  -> booking pending + slot booked + payment_orders(order_type=MENTOR_SESSION, booking_id, amount = mentor_schedules.price)
  -> FE: GET /payments/orders/{paymentOrderCode} lấy QR, poll vài giây / lần
SePay webhook (đúng số tiền, đơn còn hạn)
  -> payment_transactions(transaction_type=mentor_session, mentor_schedule_id) success
  -> booking confirmed + thông báo cho học viên và mentor, đơn PAID
Đơn hết hạn (job mỗi phút / khi xem đơn) hoặc POST /payments/orders/{code}/cancel
  -> đơn EXPIRED / CANCELLED, booking cancelled ("Hết hạn thanh toán" / "Học viên hủy thanh toán"), slot available
```

| Tình huống | Kết quả |
|---|---|
| Thanh toán đúng số tiền, đơn còn hạn | Booking `confirmed`, 1 giao dịch `mentor_session`, không tạo enrollment |
| Webhook gửi lại / gửi song song | Chỉ xác nhận 1 lần (khóa đơn + sepay_id duy nhất) |
| Sai số tiền | Đơn `REVIEW_REQUIRED`, booking vẫn `pending` tới khi hết hạn |
| Tiền về sau khi đơn hết hạn / bị hủy / booking đã bị hủy | Đơn `REVIEW_REQUIRED`, booking giữ nguyên trạng thái hủy |
| Hủy booking đã thanh toán | Booking `cancelled`, slot mở lại, **không hoàn tiền tự động** (admin xử lý ngoại lệ) |
| Đổi lịch booking đã thanh toán | Chỉ sang slot cùng mentor, cùng giá; không tạo đơn mới |

Thứ tự khóa dòng luôn là **đơn thanh toán -> slot** (webhook, hết hạn, hủy) để tránh deadlock.
Code: `BookingServiceImpl.book`, `PaymentServiceImpl.createMentorSessionOrder / settleMentorSession / expireDueOrders`,
`BookingPaymentServiceImpl` (tách riêng để không bị phụ thuộc vòng giữa Booking và Payment).

## 6. Mã lỗi Dev 5

| Code | Tên | HTTP |
|---|---|---|
| 6001 | SCHEDULE_NOT_FOUND | 404 |
| 6002 | INVALID_SCHEDULE_TIME | 400 |
| 6003 | SCHEDULE_TIME_CONFLICT | 409 |
| 6004 | SCHEDULE_NOT_AVAILABLE | 409 |
| 6005 | MENTOR_HOURLY_RATE_NOT_SET | 400 |
| 6006 | SCHEDULE_HAS_BOOKING | 409 |
| 6007 | SCHEDULE_STATUS_INVALID | 400 |
| 6008 | BOOKING_NOT_FOUND | 404 |
| 6009 | BOOKING_NOT_CANCELLABLE | 400 |
| 6010 | BOOKING_CANCEL_DEADLINE_PASSED | 400 |
| 6011 | RESCHEDULE_MENTOR_MISMATCH | 400 |
| 6012 | BOOKING_STATUS_INVALID | 400 |
| 6013 | BOOKING_NOT_STARTED | 400 |
| 6014 | NOTIFICATION_NOT_FOUND | 404 |
| 6015 | SESSION_PRICE_INVALID | 400 |
| 6016 | BOOKING_PAYMENT_PENDING | 400 |
| 6017 | RESCHEDULE_PRICE_MISMATCH | 400 |
| 6018 | STUDY_SCHEDULE_NOT_FOUND | 404 |
| 6019 | STUDY_SCHEDULE_TIME_INVALID | 400 |
| 6020 | LEARNING_PATH_NOT_FOUND | 404 |
| 6021 | LEARNING_PATH_ITEM_INVALID | 400 |
| 6022 | LEARNING_PATH_HAS_STUDENTS | 409 |
| 6023 | PRACTICE_EXAM_NOT_FOUND | 404 |
| 6024 | PRACTICE_ANSWER_NOT_FOUND | 404 |
| 6025 | PRACTICE_EVALUATION_NOT_FOUND | 404 |
| 6026 | PRACTICE_EXAM_HAS_ANSWERS | 409 |
| 6027 | AI_NOT_CONFIGURED | 503 |
| 6028 | AI_EVALUATION_FAILED | 502 |

Mã 5001–5023 đã thuộc Dev 3 (Exam) nên Dev 5 dùng dải **6001–6028**.

## 7. Test (D5-21)

| File | Nội dung |
|---|---|
| `MentorScheduleServiceImplTest` | Mở slot (UTC, giá), trùng giờ, sửa / đóng / mở lại, xóa slot có booking, xem lịch trống ẩn link |
| `BookingServiceImplTest` | Đặt slot, 2 học viên đặt cùng slot, học viên trùng giờ, hủy quá hạn, hủy sai người, admin hủy, đổi lịch (thành công / khác mentor / trùng giờ / quá hạn), mentor cập nhật kết quả |
| `NotificationServiceImplTest` | Tạo thông báo, đọc 1 / đọc tất cả, đọc thông báo của người khác |
| `BookingPaymentServiceImplTest` | Xác nhận booking sau thanh toán, tiền về khi booking đã hủy, trả slot khi đơn hết hạn |
| `MentorSessionPaymentIntegrationTest` (PostgreSQL thật) | Đặt lịch tạo đơn đúng giá, webhook xác nhận 1 lần (kể cả gửi song song), sai số tiền, hết hạn trả slot + tiền về muộn, hủy đơn / hủy booking |
| `MentorScheduleControllerTest`, `BookingControllerTest` | Phân quyền (401/403), validation (400), mã lỗi chuẩn |

Chạy test thanh toán buổi kèm giống hướng dẫn trong `DEV4_SEPAY.md` (DB PostgreSQL dùng một lần):

```powershell
$env:PAYMENT_TEST_DB_URL='jdbc:postgresql://localhost:55439/englishcenter_dev4_test'
mvn '-Dtest=MentorSessionPaymentIntegrationTest,BookingServiceImplTest,BookingPaymentServiceImplTest' test
```

Lưu ý: unit test kiểm tra service luôn đọc slot qua `findWithLockByScheduleId` và chặn lượt đặt thứ hai. Kiểm tra race condition thật (2 transaction song song) cần chạy với PostgreSQL thật.

## 8. Phần còn lại / bàn giao cho dev khác

- **D5-15:** slot chưa gắn khóa học; khi Dev 4 có `EnrollmentService` cần thêm `course_id` cho booking rồi mới kiểm tra.
- **D5-17:** bảng `notifications` đã có `email_status`, `scheduled_at`, `sent_at` để làm email sau.
- Admin quản lý Learning Path / đề luyện tập **chỉ qua API + Swagger** (chưa có UI admin).

## 9. Thông báo realtime WebSocket + nhắc buổi kèm (TASK-D5-02, migration `V9`)

- Endpoint STOMP: `/ws-connect` (FE nối qua proxy Vite). Frame `CONNECT` gửi header `Authorization: Bearer <accessToken>`.
- Subscribe `/topic/notifications/{userId}` — chỉ được subscribe topic của chính mình; client không được `SEND`.
- Mỗi thông báo mới (`NotificationService.notify`) được đẩy **sau khi transaction commit**; payload giống `NotificationResponse`.
- Job `BookingReminderJob` (mỗi phút) gửi `session_reminder` cho học viên + mentor trước giờ học `app.booking.reminder-minutes-before` phút (mặc định 30), mỗi booking 1 lần (`bookings.reminder_sent_at`).

## 10. Lịch học cá nhân (TASK-D5-01, migration `V10`) — role `USER` / `STUDENT`

| Method | Path | Ghi chú |
|---|---|---|
| GET | `/users/me/study-schedules?from&to&status&page&size` | mặc định từ đầu hôm nay (UTC) tới +30 ngày |
| POST | `/users/me/study-schedules` | `{title, description?, lessonId?, scheduledTime, repeatRule: none/daily/weekly, reminderMinutesBefore 0-1440}` — giờ phải ở tương lai |
| PUT | `/users/me/study-schedules/{id}` | sửa lịch của mình |
| PUT | `/users/me/study-schedules/{id}/status` | `{status: pending/done/skipped/cancelled}` |
| DELETE | `/users/me/study-schedules/{id}` | |

Job `StudyReminderJob` gửi `study_reminder` đúng hạn; lịch lặp `daily` / `weekly` đã qua giờ tự dời sang kỳ sau.

## 11. Learning Path (TASK-D5-03) — dữ liệu JSONB trong `learning_paths`

| Method | Path | Quyền |
|---|---|---|
| GET | `/learning-paths?certificateId&page&size` | public |
| GET | `/learning-paths/{id}` | public |
| POST | `/users/me/learning-paths/{id}/enroll` | STUDENT (gọi lại vẫn chỉ ghi danh 1 lần) |
| GET | `/users/me/learning-paths/progress` | STUDENT |
| POST / PUT / DELETE | `/admin/learning-paths[/{id}]` | ADMIN (Swagger) |

Body admin: `{certificateId, name, description?, items: [{stage, order, type: COURSE/EXAM/PRACTICE_EXAM, refId, targetBand?}]}`.
`title` của chặng lấy tự động từ khóa học / đề thi / đề luyện tập; `targetBand` chỉ dùng cho `PRACTICE_EXAM`; trùng chặng → 6021.

Tiến độ được **tính lại mỗi lần học viên xem** rồi lưu vào `student_progress`:
- `COURSE`: hoàn thành khi học viên **đã ghi danh khóa** (đã thanh toán — quyết định của nhóm).
- `EXAM`: có lượt thi `passed = true`.
- `PRACTICE_EXAM`: có bài AI chấm đạt `targetBand` (bỏ trống = chỉ cần có bài đã chấm).
- Đủ 100% → `status = completed`, `completedAt` giữ mốc lần đầu. Lộ trình đã có học viên thì không xóa được (6022).

## 12. AI Practice — Writing / Speaking (TASK-D5-04) — bài làm JSONB trong `practice_exams.answers`

| Method | Path | Quyền |
|---|---|---|
| GET | `/practice-exams?type=WRITING/SPEAKING&keyword&page&size` | public |
| GET | `/practice-exams/{id}` | public |
| POST | `/practice-exams/{id}/submit` `{content}` | STUDENT, nộp nhiều lần được |
| POST | `/practice-exams/answers/{answerId}/ai-evaluate` | STUDENT, chủ bài làm; mất 10–60 giây |
| GET | `/practice-exams/evaluations/{evaluationId}` | chủ bài làm hoặc ADMIN |
| GET | `/practice-exams/me/answers` | STUDENT, mới nhất trước |
| POST / PUT / DELETE | `/admin/practice-exams[/{id}]` | ADMIN (Swagger); đề đã có bài làm không xóa được (6026) |

- AI chấm theo 4 tiêu chí IELTS (`taskAchievement`, `coherenceCohesion`, `lexicalResource`, `grammaticalAccuracy`, mỗi tiêu chí `{band, comment}`), `bandScore` 0–9 bước 0.5, `feedback`, `suggestions[]`.
- Gọi AI **ngoài transaction**; bài đã chấm thì trả lại kết quả cũ (không gọi AI lần 2). Chấm xong gửi thông báo `exam_result` (realtime).
- AI lỗi → 6028, bài vẫn ở trạng thái `SUBMITTED` để thử lại.

Cấu hình (đặt trong `.env`, **không commit key**):

```properties
ai.gemini.api-key=${GEMINI_API_KEY:}          # trống -> 6027 AI_NOT_CONFIGURED
ai.gemini.model=${GEMINI_MODEL:gemini-2.5-flash}
ai.gemini.base-url=https://generativelanguage.googleapis.com
ai.gemini.timeout-seconds=60
```

## 13. Test thêm cho mục 9 → 12

| File | Nội dung |
|---|---|
| `NotificationWebSocketIntegrationTest`, `StompAuthChannelInterceptorTest` | Kết nối STOMP bằng JWT, chặn subscribe topic người khác, nhận thông báo sau commit |
| `StudyScheduleServiceImplTest`, `StudyScheduleControllerTest`, `StudyScheduleIntegrationTest` | CRUD lịch học, nhắc 1 lần, lịch lặp tự dời, phân quyền |
| `GeminiEvaluationClientTest` | Gọi Gemini qua HTTP server giả: header key, bóc ```json```, chuẩn hóa band, lỗi HTTP / JSON hỏng |
| `PracticeExamControllerTest`, `LearningPathControllerTest` | public / 401 / 403 / 400, mã 6027 |
| `LearningPathPracticeIntegrationTest` (PostgreSQL thật, AI giả lập) | Nộp + chấm 1 lần, quyền sở hữu, AI lỗi; tiến độ lộ trình theo ghi danh khóa + band AI |

```powershell
$env:PAYMENT_TEST_DB_URL='jdbc:postgresql://localhost:55439/englishcenter_dev4_test'
mvn '-Dtest=LearningPathPracticeIntegrationTest,StudyScheduleIntegrationTest,NotificationWebSocketIntegrationTest' test
```
