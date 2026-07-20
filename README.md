# ExamHub StudentApp

StudentApp là ứng dụng Android dành cho học sinh trong hệ thống ExamHub. Ứng dụng hỗ trợ đăng nhập, xem lớp và kỳ thi, tải dữ liệu làm bài offline, quét phiếu OMR bằng camera, nộp bài online/offline, tự đồng bộ bài nộp khi có mạng, nhận thông báo Firebase và xem kết quả.

## Yêu Cầu Môi Trường

- Windows, macOS hoặc Linux.
- Android Studio phiên bản mới, khuyến nghị bản có hỗ trợ Android Gradle Plugin 9.x.
- JDK 17 trở lên. Nếu dùng Android Studio, có thể dùng JDK đi kèm Android Studio.
- Android SDK:
  - Compile SDK: Android API 36.
  - Min SDK của app: API 24.
  - Android SDK Build-Tools tương ứng với SDK đã cài.
- Android NDK và CMake:
  - CMake 3.22.1.
  - NDK theo phiên bản Android Studio đề xuất cho project.
- Thiết bị thật hoặc emulator có camera nếu cần kiểm thử luồng quét OMR.
- Backend ExamHub đang chạy và truy cập được từ thiết bị/emulator.

## Clone Source

```powershell
git clone <student-app-repository-url>
cd StudentApp
```

Nếu repository có submodule trong tương lai, chạy thêm:

```powershell
git submodule update --init --recursive
```

## Cấu Trúc Quan Trọng

- `app/`: module Android chính.
- `opencv/`: module OpenCV Android SDK được Gradle include bằng `include(":opencv")`.
- `app/src/main/cpp/`: native OMR pipeline dùng C++/OpenCV.
- `gradle/libs.versions.toml`: khai báo phiên bản dependency.
- `app/google-services.json`: cấu hình Firebase cho Firebase Cloud Messaging và Google services.

## OpenCV Module

Project đang phụ thuộc vào module local:

```kotlin
implementation(project(":opencv"))
```

Vì vậy thư mục `opencv/` cần có trong repo hoặc phải được bổ sung trước khi build. Nếu clone về mà thiếu `opencv/`, tải OpenCV Android SDK cùng phiên bản đang dùng trong project, giải nén và copy SDK module vào:

```text
StudentApp/opencv
```

Thư mục hợp lệ tối thiểu cần có:

```text
opencv/build.gradle
opencv/java/AndroidManifest.xml
opencv/java/src
opencv/native
```

Không commit các output sinh ra bởi Gradle/CMake như:

```text
opencv/build/
opencv/.cxx/
opencv/.externalNativeBuild/
```

## Firebase Và Google Services

Project cần `app/google-services.json` để build với plugin:

```kotlin
alias(libs.plugins.google.services)
```

Nếu dùng Firebase project riêng:

1. Vào Firebase Console.
2. Tạo hoặc chọn Android app có package name:

```text
com.examhub.student
```

3. Tải file `google-services.json`.
4. Đặt file vào:

```text
app/google-services.json
```

Nếu không dùng Firebase thật, vẫn cần một file `google-services.json` hợp lệ cho môi trường dev để Gradle build qua bước Google Services.

## Cấu Hình Backend URL

Mặc định debug build dùng:

```text
http://10.0.2.2:3001/api/v1/
```

Giá trị này nằm trong `app/build.gradle.kts`:

```kotlin
val debugApiBaseUrl = "http://10.0.2.2:3001/api/v1/"
```

Quy ước địa chỉ:

- Android Emulator truy cập backend trên máy host bằng `10.0.2.2`.
- Thiết bị thật phải dùng IP LAN của máy chạy backend, ví dụ `http://192.168.0.198:3001/api/v1/`.

App có màn hình đổi Backend URL trong Login/Settings. Khi đổi URL, app sẽ xóa token, profile cache, device-scoped data và bài nộp offline đang queue để tránh lẫn dữ liệu giữa các môi trường.

## Cài Dependency Và Build

Dùng Gradle Wrapper có sẵn trong repo.

Kiểm tra build debug:

```powershell
.\gradlew.bat :app:assembleDebug
```

Trên macOS/Linux:

```bash
./gradlew :app:assembleDebug
```

APK debug sau khi build nằm ở:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Chạy Bằng Android Studio

1. Mở Android Studio.
2. Chọn `Open` và trỏ đến thư mục `StudentApp`.
3. Chờ Gradle sync xong.
4. Kiểm tra SDK/NDK/CMake nếu Android Studio báo thiếu.
5. Chọn device/emulator.
6. Bấm `Run`.

## Chạy Backend Dev Để App Kết Nối

StudentApp cần backend ExamHub đang chạy. Ví dụ backend local chạy ở port `3001`:

```text
http://localhost:3001/api/v1/
```

Khi chạy bằng emulator, app gọi:

```text
http://10.0.2.2:3001/api/v1/
```

Khi chạy bằng điện thoại thật, máy tính và điện thoại cần cùng mạng Wi-Fi, sau đó cấu hình Backend URL trong app bằng IP LAN của máy tính:

```text
http://<IP-LAN-CUA-MAY-TINH>:3001/api/v1/
```

## Chạy Test

Unit test:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Instrumented test:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Lệnh build đầy đủ trước khi mở pull request:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

## Quyền Trên Thiết Bị

App cần các quyền/chức năng sau:

- Camera: quét phiếu OMR.
- Internet và Network State: gọi API, upload ảnh, đồng bộ offline.
- Notification: nhận thông báo FCM trên Android 13+.
- Device Admin/Kiosk: khóa màn hình trong chế độ làm bài nếu thiết bị được cấu hình.

## Luồng Làm Bài Và Đồng Bộ Offline

Tóm tắt luồng chính:

1. Học sinh đăng nhập.
2. App tải danh sách lớp/kỳ thi.
3. Học sinh tải dữ liệu offline hoặc bắt đầu phiên thi.
4. App cache template OMR, mã học sinh, mã lớp và offline permit.
5. Học sinh chụp bài bằng camera.
6. Native OMR xử lý ảnh, đọc mã học sinh/mã lớp/mã đề và đáp án.
7. App đối chiếu mã học sinh và mã lớp với dữ liệu kỳ thi.
8. Khi nộp bài, app đóng băng `capturedAt`, payload và ảnh.
9. Nếu có mạng, app upload ảnh rồi gọi submit.
10. Nếu mất mạng, app lưu queue và WorkManager tự động đồng bộ khi có mạng.

## Các Lỗi Hay Gặp

### Gradle Báo Thiếu `:opencv`

Kiểm tra thư mục:

```text
StudentApp/opencv
```

Nếu thiếu, tải OpenCV Android SDK và copy module vào đúng đường dẫn trên.

### Lỗi `google-services.json`

Kiểm tra file:

```text
app/google-services.json
```

File phải thuộc đúng Firebase Android app/package `com.examhub.student`.

### App Không Kết Nối Backend

- Emulator: dùng `http://10.0.2.2:3001/api/v1/`.
- Thiết bị thật: dùng IP LAN của máy tính, không dùng `localhost`.
- Đảm bảo backend lắng nghe trên host/port mà thiết bị truy cập được.
- Nếu đổi backend URL trong app, đăng nhập lại sau khi app xóa session cũ.

### Camera/OMR Không Chạy

- Kiểm tra quyền camera.
- Kiểm tra template đã được tải và cache.
- Kiểm tra ảnh có đủ marker và không quá mờ/nghiêng.
- Kiểm tra OpenCV native libs đã được build cho ABI của thiết bị.

### Offline Submission Không Đồng Bộ

- Kiểm tra thiết bị đã có mạng.
- Kiểm tra backend còn cho phép offline sync theo `offline_permit`.
- Nếu backend báo kỳ thi đã hết giờ ở bước presign, session đó không còn được upload ảnh.
- Nếu backend báo device mismatch, cần đồng bộ bằng đúng thiết bị đã bắt đầu phiên thi.

## Quy Ước Git

Không commit:

- Build output: `build/`, `app/build/`, `.gradle/`, `.cxx/`.
- File local: `local.properties`, `.venv/`, `.idea/workspace.xml`.
- Tài liệu và sơ đồ nội bộ: `docs/`, `*.drawio`.

Có thể commit:

- Source Android/Kotlin/C++.
- Gradle config.
- Room schema trong `app/schemas`.
- OpenCV module source/prebuilt SDK cần thiết để project build.
- `README.md` và các file hướng dẫn cài đặt như `INSTALL.md` nếu có.
