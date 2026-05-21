# Kế hoạch app học sinh: tái sử dụng module, cấu trúc và style giao diện

Tài liệu này mô tả cách xây app học sinh dựa trên nền app giáo viên hiện tại. Mục tiêu là tái sử dụng tối đa các phần đã ổn định như auth, network, cache, OMR native, FCM, theme Material 3, nhưng tách rõ nghiệp vụ học sinh để tránh làm app giáo viên bị phình và khó bảo trì.

## 1. Phạm vi app học sinh

App học sinh tập trung vào 5 nhóm chức năng:

1. Đăng ký, đăng nhập, Google login, OTP quên mật khẩu.
2. Tham gia lớp bằng Join Code và nhận Internal ID 7 chữ số trong từng lớp.
3. Lockdown Mode khi làm bài giấy.
4. Quét phiếu, xử lý ảnh, xác nhận visual debug trước khi nộp.
5. Xem kết quả, nhận thông báo và gửi khiếu nại/phúc khảo.

App học sinh không cần các chức năng của giáo viên như tạo kỳ thi, quản lý lớp, chấm bài, tải dữ liệu chấm hay xử lý khiếu nại từ phía giáo viên.

## 2. Chiến lược tái sử dụng

Nên tách project theo hướng dùng chung một phần `core` hoặc clone module hiện tại rồi refactor dần. Nếu thời gian gấp, có thể tạo app học sinh trong cùng repo với package/app module mới, nhưng vẫn nên gom phần dùng chung vào các package rõ ràng.

Đề xuất module logic:

- `core-network`: Retrofit, OkHttp, auth interceptor, token refresh, ETag cache, unauthorized handler.
- `core-auth`: login, Google login, OTP, token/session/device id.
- `core-omr`: native OpenCV/OMR, template parsing, marker detect, dewarp, debug image.
- `core-notification`: FCM token registration, FirebaseMessagingService base, notification routing.
- `core-ui`: Material 3 theme, dimensions, icon containers, badges, card styles, common inset helpers.
- `student-app`: màn hình và nghiệp vụ riêng của học sinh.
- `teacher-app`: màn hình và nghiệp vụ riêng của giáo viên hiện tại.

Nếu chưa muốn multi-module Gradle ngay, vẫn nên giữ ranh giới package:

```text
com.omr.scanner.core.auth
com.omr.scanner.core.network
com.omr.scanner.core.omr
com.omr.scanner.core.ui
com.examhub.student.*
com.omr.scanner.teacher.*
```

## 3. Module có thể tái sử dụng trực tiếp

### Auth và phiên đăng nhập

Có thể tái sử dụng:

- `AuthApiService`
- `AuthRepository`
- `AuthRepositoryImpl`
- `TokenManager`
- `TokenAuthenticator`
- `AuthInterceptor`
- `UnauthorizedInterceptor`
- `LoginViewModel`
- `ForgotPasswordViewModel`
- request/response liên quan: `LoginRequest`, `GoogleLoginRequest`, `OtpRequest`, `OtpVerifyRequest`, `RefreshTokenRequest`, `AuthTokenResponse`

Cần chỉnh:

- Thêm API đăng ký học sinh, ví dụ `POST /auth/register/student`.
- Profile học sinh cần map thêm `studentCode`, `internalIds`, danh sách lớp đã tham gia.
- Login xong route theo role: `STUDENT` vào app học sinh, `TEACHER` vào app giáo viên.

### Network, cache, error handling

Có thể tái sử dụng:

- Retrofit/Koin setup trong `NetworkModule`.
- `safeApiFlow`, `ApiResult`, `ApiException`.
- ETag cache cho danh sách lớp, exam, result, notification.
- Cache-first pattern đã áp dụng: hiển thị dữ liệu local trước, refresh server sau.

Cần chỉnh:

- Không dùng repository giáo viên cho app học sinh.
- Tạo `StudentClassRepository`, `StudentExamRepository`, `StudentSubmissionRepository`, `StudentResultRepository`, `StudentAppealRepository`.

### FCM và thông báo

Có thể tái sử dụng:

- Firebase setup.
- `FcmTokenRegistrar`.
- `OmrFirebaseMessagingService`, nhưng nên đổi thành base service hoặc service riêng cho student.
- `NotificationPreferenceManager`.
- `NotificationApiService`, nếu backend dùng chung endpoint mobile notifications.

Cần chỉnh:

- Notification route cho học sinh:
  - `submission_scored` -> `StudentResultDetailFragment`
  - `appeal_resolved` -> `StudentAppealDetailFragment`
  - `exam_opened` -> `StudentExamDetailFragment`
  - `class_approved` -> `StudentClassDetailFragment`

### OMR và xử lý ảnh

Có thể tái sử dụng:

- `OmrEngine`
- `OmrProcessor`
- `OmrProcessingResult`
- native `libomr_native.so`
- template parsing, marker detection, dewarp, threshold, debug image generation.
- `CameraARFragment`/camera utilities nếu tách được khỏi luồng chấm của giáo viên.

Cần chỉnh:

- App học sinh không chấm điểm local.
- Chỉ cần bóc tách:
  - ID zone.
  - exam code/class code/student internal ID.
  - answer selections.
  - debug image/warped image để học sinh xác nhận.
- Không cần answer key local.
- Không hiển thị đúng/sai trước khi server chấm.
- Thêm check chống thi hộ: Internal ID/SBD bóc tách phải khớp tài khoản học sinh trong lớp/kỳ thi.

### UI Material 3

Có thể tái sử dụng:

- `colors.xml`, `themes.xml`, `dimens.xml`, `styles.xml`.
- icon container, badge, card style, toolbar style.
- helper `applySystemWindowInsets`, `collectOnStarted`.
- BottomNavigationView style nếu app học sinh có nhiều tab.

Cần chỉnh:

- Palette nên cùng hệ Material 3 nhưng nhẹ hơn, tập trung vào trạng thái học tập.
- App học sinh cần ít tính quản trị hơn, CTA rõ hơn: “Tham gia lớp”, “Vào bài làm”, “Nộp bài”.

## 4. Module cần viết mới

### Student Class

Chức năng:

- Nhập Join Code.
- Xem danh sách lớp đã tham gia.
- Xem Internal ID trong từng lớp.
- Xem giáo viên, môn học, số kỳ thi đang mở.

API đề xuất:

```http
POST /api/v1/student/classes/join
GET  /api/v1/student/classes
GET  /api/v1/student/classes/:classId
```

Model đề xuất:

```kotlin
data class StudentClass(
    val id: String,
    val className: String,
    val subject: String?,
    val teacherName: String,
    val joinCode: String?,
    val internalId: String,
    val activeExamCount: Int
)
```

### Student Exam

Chức năng:

- Xem kỳ thi đang mở.
- Xem trạng thái: chưa làm, đang làm, đã nộp, đã có điểm, hết hạn.
- Vào Lockdown Mode nếu kỳ thi yêu cầu.
- Mở camera scan phiếu.

API đề xuất:

```http
GET /api/v1/student/exams
GET /api/v1/student/exams/:examId
GET /api/v1/student/exams/:examId/template
```

### Lockdown Mode

Chức năng:

- Bắt đầu phiên làm bài.
- Kích hoạt LockTask API.
- Ghi log vi phạm.
- Kết thúc phiên khi nộp bài hoặc hết giờ.

Module đề xuất:

```text
student/lockdown/
  LockdownActivity.kt
  LockdownController.kt
  LockViolationReporter.kt
  LockdownPolicy.kt
```

Lưu ý kỹ thuật:

- LockTask API chỉ khóa mạnh khi app là Device Owner hoặc được allowlist bởi DPC/MDM.
- Nếu không có Device Owner, Android chỉ hỗ trợ screen pinning nhẹ hơn và học sinh vẫn có thể thoát bằng thao tác hệ thống.
- Cần ghi rõ trong luận văn/sản phẩm: mức bảo mật phụ thuộc chế độ triển khai thiết bị.
- Các log cần gửi server:
  - app paused/resumed bất thường.
  - screen off/on.
  - attempt exit.
  - network lost.
  - camera permission revoked.
  - time drift.

### Student OMR Confirm

Chức năng:

- Chụp ảnh phiếu.
- Nắn phẳng.
- Hiển thị debug overlay các ô đã nhận.
- Cho học sinh xác nhận trước khi nộp.
- Chặn nộp nếu Internal ID/SBD không khớp.

Luồng:

```text
StudentExamDetail
  -> LockdownActivity nếu bắt buộc
  -> StudentScanFragment
  -> StudentOmrConfirmFragment
  -> SecureSubmit
  -> SubmissionResultPendingFragment
```

### Secure Submit

Chức năng:

- Upload ảnh gốc lên S3/presigned URL.
- Gửi payload đáp án đã ký/mã hóa.
- Không tin dữ liệu client tuyệt đối, server vẫn phải validate lại.

Khuyến nghị bảo mật:

- Không chỉ “mã hóa JSON” rồi tin client, vì app Android có thể bị reverse engineering.
- Nên dùng hybrid encryption:
  - AES-GCM mã hóa payload.
  - RSA/ECDH dùng để bọc AES key.
  - HMAC/signature để chống sửa payload.
- Server vẫn phải đối chiếu:
  - userId từ JWT.
  - class membership.
  - exam window.
  - internalId extracted.
  - submission nonce/idempotency key.
  - hash ảnh gốc.

Payload đề xuất:

```json
{
  "examId": "...",
  "classId": "...",
  "studentInternalId": "1234567",
  "examCode": "001",
  "answers": [
    { "questionNumber": 1, "choice": "A", "confidence": 0.92 }
  ],
  "omrDebug": {
    "templateVersion": "...",
    "processingVersion": "...",
    "warpedImageHash": "...",
    "rawImageHash": "..."
  }
}
```

### Result và Appeal

Chức năng:

- Xem điểm sau khi server chấm.
- Xem visual debug đúng/sai/nghi vấn.
- Chọn câu để khiếu nại.
- Gửi lý do.
- Theo dõi trạng thái khiếu nại.

API đề xuất:

```http
GET  /api/v1/student/results
GET  /api/v1/student/results/:submissionId
POST /api/v1/student/appeals
GET  /api/v1/student/appeals
GET  /api/v1/student/appeals/:appealId
```

Trạng thái appeal:

- `PENDING`: chờ giáo viên xử lý.
- `RESOLVED`: giáo viên chấp nhận/đã điều chỉnh.
- `REJECTED`: giáo viên từ chối.

## 5. Cấu trúc màn hình đề xuất

Bottom navigation app học sinh:

1. Trang chủ
2. Lớp học
3. Bài thi
4. Kết quả
5. Hồ sơ

Không nên đưa “Thông báo” thành tab riêng nếu app học sinh còn ít chức năng. Nên để icon chuông ở top bar và màn notification riêng, vì học sinh dùng chủ yếu theo luồng bài thi/kết quả.

### Trang chủ học sinh

Nội dung:

- Greeting: “Xin chào, Huy”
- Card trạng thái học vụ: số lớp, kỳ thi đang mở, bài đã nộp.
- CTA lớn: “Tham gia lớp”
- Card kỳ thi cần làm hôm nay.
- Card kết quả mới nhất.
- Notification icon ở top right.

### Lớp học

Nội dung:

- Search/filter lớp.
- Danh sách lớp dạng MaterialCardView.
- Mỗi item hiển thị:
  - Tên lớp.
  - Môn học.
  - Giáo viên.
  - Internal ID dạng badge.
  - Số kỳ thi đang mở.

### Bài thi

Nội dung:

- Filter chip: Tất cả, Đang mở, Đã nộp, Đã có điểm, Đã hết hạn.
- Mỗi item hiển thị:
  - Tên kỳ thi.
  - Lớp/môn.
  - Thời gian còn lại hoặc deadline.
  - Trạng thái.
  - CTA “Vào bài” hoặc “Xem bài đã nộp”.

### Scan và Confirm

Nội dung:

- Camera nền tối.
- Control bar Material ở dưới.
- Chip trạng thái marker: “Đã nhận 12/12 marker”.
- Sau khi chụp, màn confirm hiển thị ảnh nắn phẳng toàn màn.
- Bottom sheet:
  - Internal ID nhận được.
  - Exam code.
  - Số câu đã nhận.
  - Cảnh báo nếu có ô nghi vấn.
  - Button “Nộp bài”.

### Kết quả

Nội dung:

- Điểm tổng.
- Số câu đúng/sai/bỏ trống/nghi vấn.
- Visual debug image.
- Danh sách câu.
- CTA “Gửi khiếu nại” cho câu có nghi vấn hoặc học sinh chọn.

### Hồ sơ

Nội dung:

- Avatar, họ tên, email.
- Mã học sinh nếu có.
- Danh sách Internal ID theo lớp.
- Cài đặt thông báo.
- Đổi mật khẩu.
- Đăng xuất.

## 6. Style giao diện

Giữ Material Design 3 giống app giáo viên để hai app cùng một hệ sinh thái.

Nguyên tắc:

- Root dùng `CoordinatorLayout` hoặc `ConstraintLayout`.
- Top bar gọn, có title/subtitle nếu cần.
- Nội dung scroll bằng `NestedScrollView` hoặc RecyclerView.
- `MaterialCardView` cho card/list item.
- `MaterialButton` cho CTA.
- `Chip` cho trạng thái.
- Không dùng Compose nếu tiếp tục theo XML hiện tại.
- Tất cả text đưa vào `strings.xml`.
- Màu dùng `@color` hoặc `?attr/color...`, không hardcode trong layout.
- Touch target tối thiểu 48dp.

Màu đề xuất:

- Primary: xanh olive/green giống app giáo viên để đồng bộ.
- Secondary: teal/blue cho trạng thái công nghệ.
- Result correct: green.
- Result wrong: red.
- Suspicious: amber.
- Pending: blue/teal.
- Lockdown: red hoặc amber nhưng không neon.

Badge trạng thái:

- `Đang mở`: primary/green container.
- `Đã nộp`: secondary/teal container.
- `Đã có điểm`: green container.
- `Hết hạn`: neutral container.
- `Cần xác nhận`: amber container.
- `Khiếu nại`: blue/amber theo trạng thái.

## 7. Điều hướng đề xuất

Navigation graph học sinh nên tách riêng:

```text
student_nav_graph.xml
  studentHomeFragment
  studentClassListFragment
  studentClassDetailFragment
  studentExamListFragment
  studentExamDetailFragment
  studentScanFragment
  studentOmrConfirmFragment
  studentSubmitResultFragment
  studentResultListFragment
  studentResultDetailFragment
  studentAppealCreateFragment
  studentAppealDetailFragment
  studentProfileFragment
  studentNotificationsFragment
```

Deep link/notification routing:

- `route=student_result_detail`, `submission_id`.
- `route=student_appeal_detail`, `appeal_id`.
- `route=student_exam_detail`, `exam_id`.

## 8. Backend cần bổ sung hoặc kiểm tra

Các endpoint app học sinh cần rõ role guard `STUDENT`:

```http
POST /api/v1/auth/register/student
POST /api/v1/student/classes/join
GET  /api/v1/student/classes
GET  /api/v1/student/exams
GET  /api/v1/student/exams/:examId
GET  /api/v1/student/exams/:examId/template
POST /api/v1/student/submissions/presign-image
POST /api/v1/student/submissions
GET  /api/v1/student/results
GET  /api/v1/student/results/:submissionId
POST /api/v1/student/appeals
GET  /api/v1/student/appeals
GET  /api/v1/student/notifications
POST /api/v1/mobile/notifications/fcm-token
```

Backend không nên tin hoàn toàn payload từ app học sinh. Payload OMR từ client chỉ là dữ liệu hỗ trợ UX và submit nhanh. Server nên tự chấm/validate lại từ ảnh gốc hoặc ít nhất xác thực hash, template version, exam version và quyền của student.

## 9. Những điểm không nên tái sử dụng nguyên xi

- Dashboard giáo viên: nghiệp vụ khác, không nên dùng lại.
- Exam detail giáo viên: có tải dữ liệu chấm, khiếu nại giáo viên, progress lớp.
- Class management giáo viên: học sinh chỉ join/view, không quản lý.
- OMR scoring local: học sinh không nên có answer key và không chấm local.
- Appeal resolve: học sinh chỉ tạo/xem khiếu nại, không xử lý.

## 10. Lộ trình triển khai đề xuất

Giai đoạn 1: Auth và học vụ

- Login/register student.
- Join class bằng join code.
- Hiển thị Internal ID theo lớp.
- Student home/class/exam list.

Giai đoạn 2: Exam và scan

- Exam detail.
- Camera scan.
- OMR parse không chấm.
- Confirm screen.
- Chặn submit nếu Internal ID không khớp.

Giai đoạn 3: Secure submit

- Upload ảnh gốc.
- Gửi payload đáp án.
- Idempotency key.
- Server score.
- Pending/result state.

Giai đoạn 4: Result và appeal

- Result list/detail.
- Visual debug đúng/sai/nghi vấn.
- Appeal create/detail.
- FCM result/appeal notification.

Giai đoạn 5: Lockdown hardening

- LockTask API.
- Violation logs.
- Device owner/DPC strategy nếu cần khóa thật.
- Offline/network edge cases.

## 11. Kết luận kỹ thuật

Nên tái sử dụng mạnh các phần core đã có: auth, network, cache, FCM, Material 3 style, OMR native pipeline. Phần cần viết mới chủ yếu là nghiệp vụ student: join class, exam participation, lockdown, secure submit, result và appeal create.

Điểm cần đặc biệt cẩn trọng là Lockdown Mode và Secure Submit. Đây là hai phần dễ bị đánh giá quá mức nếu chỉ làm ở client. Muốn bảo mật thuyết phục, backend phải giữ vai trò xác thực cuối cùng, còn app học sinh tập trung vào trải nghiệm scan, xác nhận và nộp bài có kiểm soát.
