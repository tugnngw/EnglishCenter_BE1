BỘ ĐỀ LUYỆN TẬP MẪU ĐỂ TEST WEB (không phải đề thi thật)
=========================================================

Mỗi thư mục là 1 chứng chỉ, trong đó có 4 file = 4 kỹ năng:
  IELTS/       IELTS - Listening / Reading / Writing / Speaking
  TOEIC/       TOEIC - Listening / Reading (L&R)  +  Writing / Speaking (S&W)
  VSTEP/       VSTEP - Listening / Reading / Writing / Speaking
  TOEFL iBT/   TOEFL iBT - Listening / Reading / Writing / Speaking

File viết sẵn theo MẪU NHẬP ĐỀ của web ([SECTION], *đáp án đúng, [FILL], [ESSAY]...)
=> web đọc thẳng trên trình duyệt, KHÔNG gọi AI, không tốn token.

CÁCH TẠO ĐỀ TRÊN WEB
1. Admin -> AI Practice -> "Tạo đề luyện tập"
2. Tiêu đề: VD "IELTS Practice Test 1", chọn chứng chỉ IELTS, thời gian 60 phút
3. "+ Thêm phần thi" 4 lần, mỗi phần chọn đúng kỹ năng rồi kéo file tương ứng vào:
     Listening -> "IELTS - Listening.txt"
     Reading   -> "IELTS - Reading.txt"
     Writing   -> "IELTS - Writing.txt"
     Speaking  -> "IELTS - Speaking.txt"
4. "Đọc đề" -> xem trước -> "Tạo đề" (tick "Xuất bản ngay")

FILE NGHE (Listening)
- Giọng đọc máy (TTS), chỉ để test. Đã chép sẵn vào FE_EnglishCenter/public/audio/practice/
    ielts-listening.mp3, toeic-listening.mp3, vstep-listening.mp3, toefl-listening.mp3
- Trong file đề đã có dòng [AUDIO] /audio/practice/<tên>.mp3 nên chạy web bằng Vite là nghe được.
- Khi deploy thật: upload mp3 lên Cloudinary rồi sửa link [AUDIO] cho đúng.

CHẤM ĐIỂM
- Listening / Reading: chấm tự động theo đáp án (dấu * hoặc dòng "= đáp án").
- Writing / Speaking: dòng [INSTRUCTIONS] của mỗi phần là TIÊU CHÍ CHẤM (rubric).
  Khi học viên nộp bài, BE gửi rubric + đề bài + bài làm cho AI (Claude) chấm
  theo thang của chứng chỉ mục tiêu của học viên.
- {10}, {20}... ở cuối câu = số điểm của câu đó.
