# OMR Core — Tài liệu kỹ thuật

## 1. Tổng quan kiến trúc

```
┌──────────────────────────────────────────────────────┐
│                    Kotlin / Java                      │
│  OmrEngine.kt  ←→  NativeLib.kt  ←→  JNI            │
├──────────────────────────────────────────────────────┤
│                   C++ Native Layer                    │
│                                                      │
│  native_bridge.cpp  (JNI entry points)               │
│         │                                            │
│         ▼                                            │
│  OmrProcessor.cpp  (Pipeline orchestrator)           │
│    ├── MarkerDetector.cpp  (ArUco + dewarp)          │
│    ├── LayoutCalculator.cpp  (Grid layout)           │
│    ├── distortion_analyzer.cpp  (Distortion metrics) │
│    └── tps_warper.cpp  (TPS warping)                 │
│                                                      │
│  Dependencies: OpenCV 4.12 (objdetect/aruco)         │
└──────────────────────────────────────────────────────┘
```

---

## 2. Cấu trúc thư mục

```
app/src/main/cpp/
├── CMakeLists.txt                    # Build config (C++17, O3, OpenCV, jnigraphics)
├── include/
│   ├── omr_constants.h               # Hằng số & cấu trúc dữ liệu
│   ├── omr_processor.h               # Pipeline chính + Layout cache API
│   ├── layout_calculator.h           # Tính toán vị trí ô tròn
│   ├── marker_detector.h             # Phát hiện marker + nắn chỉnh
│   ├── distortion_analyzer.h         # Phân tích biến dạng (có sẵn)
│   └── tps_warper.h                  # TPS warping (có sẵn)
├── native_bridge.cpp                 # JNI bridge
├── omr_processor.cpp                 # Pipeline implementation
├── layout_calculator.cpp             # Grid layout implementation
├── marker_detector.cpp               # ArUco detection + dewarp
├── distortion_analyzer.cpp           # (có sẵn)
├── tps_warper.cpp                    # (có sẵn)
└── native-lib.cpp                    # Demo JNI (có sẵn)

app/src/main/java/com/omr/scanner/
├── OmrEngine.kt                      # Kotlin wrapper
├── NativeLib.kt                      # JNI declarations + DewarpProcessor
├── MarkerDetector.kt                 # Kotlin-side ArUco (có sẵn)
└── ...
```

---

## 3. Hằng số (omr_constants.h)

| Hằng số | Giá trị | Mô tả |
|---|---|---|
| `PAGE_WIDTH` | `827` | Chiều rộng trang chuẩn (px) |
| `PAGE_HEIGHT` | `1169` | Chiều cao trang chuẩn (px) |
| `PADDING` | `5` | Padding tối thiểu trong bounding box |
| `BUBBLE_SIZE_MIN` | `15` | Đường kính ô tròn tối thiểu |
| `BUBBLE_SIZE_MAX` | `25` | Đường kính ô tròn tối đa |
| `BUBBLE_SIZE_DEFAULT` | `18` | Đường kính mặc định |
| `BUBBLE_RATIO` | `0.8` | Tỉ lệ bubble / cell |
| `MAX_SPACING` | `40` | Khoảng cách tâm tối đa |
| `MIN_SPACING` | `4` | Khoảng cách tâm tối thiểu |
| `DENSITY_THRESHOLD` | `0.40` | Ngưỡng mật độ đen để xác định ô được tô |
| `DIFF_THRESHOLD` | `0.20` | Chênh lệch Top1-Top2 để flag nghi vấn |
| `DEWARP_GOOD_PX` | `15` | Ngưỡng Perspective-only |
| `DEWARP_MODERATE_PX` | `40` | Ngưỡng Hybrid |
| `LAPLACIAN_BLUR_THRESH` | `80` | Ngưỡng cảnh báo ảnh mờ |
| `BRIGHTNESS_MIN` | `100` | Ngưỡng cảnh báo thiếu sáng |
| `MAX_DEBUG_DIMENSION` | `1200` | Kích thước tối đa ảnh debug (resize nếu vượt) |
| `DEBUG_JPEG_QUALITY` | `75` | Chất lượng JPEG cho ảnh debug |
| `MAX_CACHE_ENTRIES` | `8` | Số template tối đa trong layout cache |
| `ADAPTIVE_BLOCK_SIZE` | `31` | Kích thước block cho adaptive threshold (lẻ, 3-255) |
| `ADAPTIVE_C` | `8.0` | Hằng số trừ khỏi trung bình block (adaptive threshold) |
| `PRE_BILATERAL_D` | `9` | Bilateral filter diameter trước marker detection |
| `PRE_CLAHE_CLIP_LIMIT` | `4.0` | CLAHE clip limit trước marker detection |
| `POST_BILATERAL_D` | `5` | Bilateral filter diameter sau warp |
| `POST_GAUSSIAN_KSIZE` | `3` | Gaussian blur kernel size sau warp |
| `MORPH_CLOSE_KSIZE` | `3` | Morph close kernel size cho cleanup |
| `MORPH_OPEN_KSIZE` | `3` | Morph open kernel size cho cleanup |
| `MORPH_FINAL_KSIZE` | `2` | Morph close kernel size cuối cùng |

---

## 4. AprilTag Marker

### 4.1 Lựa chọn thư viện

Sử dụng **OpenCV ArUco** (module `objdetect`, có sẵn trong OpenCV 4.x, **không cần** opencv_contrib riêng):

```cpp
#include <opencv2/objdetect/aruco_detector.hpp>
#include <opencv2/objdetect/aruco_dictionary.hpp>
```

ArUco `DICT_APRILTAG_16h5` có 30 mã marker. Template OMR sử dụng **12 marker cố định** với ID từ **0 đến 11** (tương ứng với `anchor_points[].id` trong template JSON).

### 4.2 Cách hoạt động

- `MarkerDetector::detectMarkers(grayImg)` phát hiện tất cả AprilTag có trong ảnh
- 12 marker được map với `anchor_points` từ template JSON qua `id`
- Nếu template không cung cấp `anchor_points`, fallback về `DEFAULT_ANCHOR_POINTS` (các ID 1–12)
- Nếu phát hiện **< 4 marker**: trả về lỗi `MARKER_NOT_FOUND`. Phần xử lý thủ công (nhập tọa độ bằng tay) để ở tầng Kotlin (`MarkerDetector.kt`)

---

## 5. Pipeline xử lý

```
  Input: Android Bitmap (đã xoay về 0°) + templateJson + answerKeyJson
    │
    ▼
[1] bitmapToMat()                     // RGBA_8888 → BGR, RGB_565 → BGR
    │
    ▼
[2] checkQuality()                    // Laplacian variance + mean brightness
    │                                  // → warnings BLURRY / LOW_LIGHT
    ▼
[2b] preprocessForMarkers() [opt]     // Bilateral(d=9) + CLAHE(4.0)
    │                                  // Cải thiện marker detection trên giấy nhàu
    ▼
[3] MarkerDetector::detectMarkers()   // ArUco AprilTag 16h5
    │                                  // → 12 marker corner + center
    ▼
[4] determineDewarpMethod()           // Tính Euclidean error
    │  e < 15px  → Perspective
    │  15 ≤ e < 40px → Hybrid (Perspective + TPS nhẹ smoothness=1.5)
    │  e ≥ 40px → TPS full (smoothness=0.3)
    ▼
[5] dewarp → PAGE_WIDTH × PAGE_HEIGHT // Ảnh chuẩn hóa 827×1169
    │
    ▼
[5b] preprocessPostWarp() [opt]       // Bilateral(d=5) + Gaussian(3×3)
    │                                  // Giảm nhiễu texture giấy
    ▼
[5c] adaptiveThreshold() [opt]        // cv::adaptiveThreshold → binary
    │   + morphCleanup() [opt]         // Close + Open + Otsu + Close
    │
    ├─► [5d] CHECK LAYOUT CACHE       // FNV-1a hash của templateJson
    │
    ├─► [6] readIdZone()              // Đọc SBD / mã lớp / mã đề
    │      readBubbleDensity() × 10 hàng × N cột
    │      → student_id, class_code, exam_code
    │
    └─► [7] readAnswerZones()         // Đọc đáp án
           readBubbleDensity() × rows × options
           → AnswerReadResult[]
    │
    ▼
[8] scoreAnswers() (nếu enableScoring) // So sánh với answerKey
    │
    ▼
[9] generateDebugImage() (nếu enableDebugImage)
    │                                  // Resize về ≤1200px nếu cần
    │                                  // JPEG quality 75
    ▼
  Output: JSON { success, id_result, student_answers, score_result,
                 debug_image_base64, warnings }
```

---

## 6. Các module chi tiết

### 6.1 LayoutCalculator

Triển khai chính xác các công thức trong tài liệu đặc tả §2.3, §2.4, §3.

#### `calculateGridLayout(totalWidth, numColumns, bubbleSize)` (§3)

```
Input:  totalWidth, numColumns, bubbleSize
Output: { spacing, offset, ok, error? }

N = 1:  offset = (totalWidth - bubbleSize) / 2
N ≥ 2:  effectiveMin = bubbleSize + MIN_SPACING
        spacing = clamp((totalWidth - bubbleSize) / (N-1),
                        effectiveMin, MAX_SPACING)
        offset   = (totalWidth - (N-1) × spacing - bubbleSize) / 2
```

#### `computeIdZoneLayout(zone)` — ID Zone (§2.3)

1. Effective area: `padding = min(PADDING, bbox.width/8, bbox.height/8)`
2. Ước lượng bubbleSize: `min(effW/totalCols, (effH-overhead)/10) × BUBBLE_RATIO`
3. Cột số thứ tự bên trái: `rowNumColW = min(prelimSpacing, effW×0.15)`
4. Vùng bubble grid: `bubbleGridX`, `bubbleGridWidth`, `bubbleGridY`, `bubbleGridHeight`
5. Áp dụng `calculateGridLayout` cho cả chiều ngang và dọc
6. Sinh tọa độ từng ô: `cx = bubbleGridX + colOffset + colIndex × colSpacing + bubbleSize/2`

#### `computeAnswerZoneLayout(zone)` — Answer Zone (§2.4)

1. Effective area (tương tự)
2. Cột số thứ tự: `numberColWidth = clamp(effW×0.12, 14, 30)`
3. Vùng lựa chọn: `choiceAreaW = effW - startNumberWidth - endNumberWidth`
4. `bubbleSize = clamp(min(dx, dy) × BUBBLE_RATIO, 15, 25)`
5. Sinh tọa độ: `y = effY + (i+0.5)×dy`, `x = choiceAreaX + (opt+0.5)×dx`

### 6.2 MarkerDetector

| Phương thức | Chức năng |
|---|---|
| `detectMarkers(grayImg)` | Phát hiện AprilTag bằng ArUco `DICT_APRILTAG_16h5`, trả về `vector<DetectedMarker>` với ID 0–29 |
| `determineDewarpMethod()` | Tính Euclidean error trung bình giữa detected và expected → chọn Perspective / Hybrid / TPS |
| `dewarpPerspective()` | `getPerspectiveTransform` 4 góc → warp về 827×1169 |
| `dewarpHybrid()` | Perspective + TPS nhẹ (smoothness=1.5) dùng tất cả marker |
| `checkQuality()` | Laplacian variance (độ mờ) + mean brightness (độ sáng) |

**Fallback khi thiếu marker**: Core module chỉ báo lỗi `MARKER_NOT_FOUND`. Tầng Kotlin có thể xử lý bằng cách yêu cầu người dùng chạm 4 góc thủ công (theo `MarkerDetector.kt`).

### 6.3 TPS Warper (tps_warper.cpp — có sẵn)

Thuật toán **Thin Plate Spline** dùng phân rã SVD để giải hệ phương trình radial basis function. Độ mượt (`smoothness`) điều chỉnh mức độ uốn:

- `smoothness = 0.3`: Uốn mạnh, dùng cho biến dạng nặng (e ≥ 40px)
- `smoothness = 1.5`: Uốn nhẹ, dùng cho hybrid dewarp (15 ≤ e < 40px)

**Lưu ý**: TPS có chi phí O(N³) với N điểm điều khiển. Với 12 marker, thời gian tính < 100ms. Nếu cần tối ưu hơn, có thể thay bằng `cv::findHomography` với RANSAC dùng toàn bộ marker.

### 6.4 OmrProcessor

#### Auto-Adaptive Pipeline (MẶC ĐỊNH — khuyên dùng cho giáo viên)

Giáo viên không cần biết "Bilateral filter" hay "adaptive threshold" là gì. Khi `autoAdaptive = true` (mặc định), app tự động quyết định tất cả các bước tiền xử lý dựa trên chất lượng ảnh:

| Chất lượng ảnh | Laplacian | Brightness | App tự bật |
|---|---|---|---|
| Scan phẳng, đủ sáng | > 200 | > 150 | *(không gì cả)* |
| Rất mờ | < 50 | bất kỳ | preMarker + postWarp + adaptive(bs=51) + morph |
| Rất tối | bất kỳ | < 80 | adaptive(bs=51) + morph |
| Hơi mờ | < 80 | ≥ 100 | preMarker + postWarp + adaptive |
| Thiếu sáng | ≥ 80 | < 100 | adaptive + morph |
| Ảnh chụp thường | còn lại | còn lại | chỉ adaptive |

**Giáo viên chỉ cần chụp ảnh — không cần cấu hình gì.**

```kotlin
// Cách dùng đơn giản nhất (auto-adaptive, mặc định)
val result = OmrEngine.process(bitmap, templateJson, answerKeyJson)
```

#### Preprocessing Pipeline (tùy chọn nâng cao)

Dành cho người dùng nâng cao muốn kiểm soát thủ công:

| Cờ | Bước | Python ref | Mục đích |
|---|---|---|---|
| `preprocessMarkers` | Bilateral(d=9) + CLAHE(4.0) | B1.5+B2 | Tăng cường marker detection trên giấy nhàu, tương phản thấp |
| `preprocessPostWarp` | Bilateral(d=5) + Gaussian(3×3) | B5+B6 | Giảm nhiễu texture giấy sau khi nắn chỉnh |
| `morphCleanup` | Close(3×3) + Open(3×3) + Otsu + Close(2×2) | B7-B10 | Làm sạch noise từ nếp gấp / bóng sau adaptive threshold |

```cpp
// Khuyến nghị cho ảnh chụp thực tế:
options.use_adaptive_threshold = true;
options.preprocess_markers   = true;   // Phát hiện marker tốt hơn
options.preprocess_post_warp = true;   // Giảm nhiễu giấy
options.morph_cleanup        = true;   // Xóa noise nếp gấp
```

#### Đọc ID Zone (§2.3)

- Duyệt từng cột (0..totalCols-1), mỗi cột 10 hàng (0-9)
- `readBubbleDensity()`: tạo **circular mask** bán kính `bubbleSize/2`, dùng `patch.at<uchar>(py, px)` để đọc giá trị pixel. Đếm pixel tối (< 128) chia cho tổng pixel trong mask.
- **Hai chế độ đọc**:
  - **Mặc định (grayscale)**: ảnh grayscale 0-255, ngưỡng cố định 128. Nhanh, phù hợp ảnh scan phẳng.
  - **Adaptive threshold**: nếu `useAdaptiveThreshold=true`, ảnh được tiền xử lý bằng `cv::adaptiveThreshold` (Gaussian, blockSize, C). Ảnh nhị phân có bubble=0 (đen), nền=255 (trắng) → tương thích với cùng ngưỡng < 128. Xử lý tốt ánh sáng không đều, thêm ~50ms.
- `readBubbleColumn()`: tìm hàng có mật độ cao nhất ≥ `density_threshold`. Nếu chênh lệch Top1−Top2 < `diff_threshold` → vẫn lấy Top1.
- Ghép kết quả theo thứ tự `student_id` → `class_code` → `exam_code`

#### Đọc Answer Zone (§2.4)

- Nhóm ô theo `question_index`, sắp xếp theo option index (col)
- Đọc mật độ từng ô bằng `readBubbleDensity()`

**Single mode** (mặc định — `selection_mode = "single"`):
- Chỉ chọn **MỘT** ô có mật độ cao nhất
- Nếu Top1 ≥ threshold → lấy đáp án đó
- Nếu Top1−Top2 < `diff_threshold` → vẫn lấy Top1 nhưng đặt `flag = 1` (nghi vấn tẩy xóa)
- Nếu Top1 < threshold → đáp án rỗng (không tô)

**Multiple mode** (`selection_mode = "multiple"`):
- Chọn **tất cả** ô có mật độ ≥ threshold
- Nối các lựa chọn bằng dấu phẩy, ví dụ `"A,C,D"`

#### Chấm điểm

- Tự động khớp `exam_code` đọc được với answer key
- Single: so sánh trực tiếp (không phân biệt hoa/thường)
- Multiple: split by comma → sort → so sánh chuỗi đã sắp xếp
- Mỗi câu đúng +1 điểm (có thể mở rộng trọng số)

#### Debug Image

- **Có answerKeyJson**: Xanh lá (đúng), Đỏ (sai), Vàng (bỏ sót / flag=1)
- **Không answerKeyJson**: Xanh dương (tất cả ô đã tô)
- ID zone: vẽ các ô được tô
- **Resize**: nếu chiều > `MAX_DEBUG_DIMENSION` (1200px) → resize giữ aspect ratio
- **JPEG quality**: `DEBUG_JPEG_QUALITY` (75) để giảm kích thước base64

---

## 7. Layout Cache

Mỗi lần gọi `processOmr` cần parse JSON và tính tọa độ bubble. Với cùng một template (cùng `examId`), kết quả này không thay đổi. **Layout cache** tránh tính toán lại:

| Thuộc tính | Giá trị |
|---|---|
| Key | FNV-1a 64-bit hash của `templateJson` |
| Dung lượng | Tối đa `MAX_CACHE_ENTRIES` (8) template |
| Chiến lược eviction | LRU (Least Recently Used) |
| Thread safety | `std::mutex` bảo vệ truy cập đồng thời |

```cpp
// JNI — Xóa cache khi cập nhật template
NativeLib.clearOmrCache()
```

```
Flow:
  templateJson → FNV-1a hash → cache lookup
    HIT  → dùng pre-computed BubbleCell[], bỏ qua LayoutCalculator
    MISS → tính toán mới, lưu vào cache
```

---

## 8. Xử lý ảnh đầu vào

### 8.1 Xoay ảnh (rotation)

**Ảnh phải được xoay về hướng chuẩn (0°) TRƯỚC KHI gọi `processOmr`.** Camera Android thường trả về ảnh xoay 90°/180°/270° tùy hướng thiết bị.

```kotlin
// Khuyến nghị: xử lý ở Kotlin trước khi gọi native
fun correctRotation(bitmap: Bitmap, exifOrientation: Int): Bitmap {
    val matrix = Matrix()
    when (exifOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90  -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
```

### 8.2 Chuyển đổi màu

`native_bridge.cpp` tự động phát hiện định dạng Bitmap và chuyển đổi:

| Android Format | Xử lý |
|---|---|
| `RGBA_8888` | `cv::cvtColor(temp, outMat, cv::COLOR_RGBA2BGR)` |
| `RGB_565` | `cv::cvtColor(temp, outMat, cv::COLOR_BGR5652BGR)` |
| `A_8` | `cv::cvtColor(temp, outMat, cv::COLOR_GRAY2BGR)` |

---

## 9. JNI Bridge (native_bridge.cpp)

| Hàm JNI | Mô tả |
|---|---|
| `processOmr(bitmap, templateJson, answerKeyJson, densityThresh, diffThresh, enableScoring, enableDebug)` | Pipeline đầy đủ, trả về JSON string |
| `bitmapToNativeMat(bitmap)` | Android Bitmap → native `cv::Mat*` |
| `matToJpegBase64(matAddr, quality)` | `cv::Mat` → JPEG base64 string |
| `clearOmrCache()` | Xóa toàn bộ layout cache |
| `releaseMat(matAddr)` | Giải phóng native Mat |
| `analyzeDistortion(...)` | Phân tích biến dạng (có sẵn) |
| `tpsWarp(...)` | TPS warping (có sẵn) |

---

## 10. Cấu trúc JSON Input/Output

### 10.1 Template JSON (input)

```json
{
  "gridConfig": {
    "anchor_points": [
      { "id": 1, "abs_x": 45, "abs_y": 45, "u": 0.0544, "v": 0.0385 }
    ],
    "id_zones": {
      "bounding_box": { "x": 389, "y": 80, "width": 418, "height": 288 },
      "items": [
        { "type": "student_id", "enabled": true, "num_digits": 8 },
        { "type": "class_code", "enabled": true, "num_digits": 5 },
        { "type": "exam_code",  "enabled": true, "num_digits": 3 }
      ]
    },
    "answer_zones": [
      {
        "zone_id": "answer_zone_1",
        "bounding_box": { "x": 90, "y": 408, "width": 279, "height": 723 },
        "layout": {
          "rows": 20, "options": 4,
          "has_start_number": true, "has_end_number": false,
          "start_question_index": 1,
          "selectionMode": "single"
        }
      }
    ]
  }
}
```

### 10.2 Answer Key JSON (input, optional)

```json
{
  "data": {
    "answerKeys": {
      "123": { "1": "A", "2": "C", "3": "B" },
      "456": { "1": "D", "2": "A", "3": "C,B" }
    }
  }
}
```

### 10.3 Result JSON (output)

```json
{
  "success": true,
  "error_code": "",
  "error_message": "",
  "warnings": ["BLURRY"],
  "id_result": {
    "student_id": "12345678",
    "class_code": "12A07",
    "exam_code": "123",
    "id_ok": true,
    "id_error": ""
  },
  "student_answers": [
    { "question_number": 1, "answer": "A", "flag": 0 },
    { "question_number": 2, "answer": "C", "flag": 1 }
  ],
  "scored": true,
  "score_result": {
    "total_questions": 40,
    "correct_count": 32,
    "total_score": 32.0,
    "details": [
      { "question_number": 1, "student_answer": "A",
        "correct_answer": "A", "is_correct": true, "flag": 0 }
    ]
  },
  "laplacian_variance": 156.3,
  "mean_brightness": 187.5,
  "debug_image_base64": "/9j/4AAQ..."
}
```

---

## 11. Mã lỗi

| error_code | Ý nghĩa | Xử lý đề xuất |
|---|---|---|
| `EMPTY_IMAGE` | Ảnh đầu vào rỗng | Kiểm tra camera/capture |
| `MARKER_NOT_FOUND` | Không tìm đủ marker (< 4) | Yêu cầu người dùng chụp lại hoặc nhập tọa độ thủ công |
| `DEWARP_FAILED` | Nắn chỉnh ảnh thất bại | Có thể do ảnh quá mờ hoặc góc chụp quá xiên |
| `PARSE_ERROR` | Parse JSON template thất bại | Kiểm tra dữ liệu API trả về |
| `BITMAP_CONVERT_FAILED` | Chuyển đổi Bitmap → cv::Mat thất bại | Kiểm tra định dạng ảnh |

---

## 12. Cách dùng từ Kotlin

```kotlin
val engine = OmrEngine()

// ═══ Cách dùng cho giáo viên (auto-adaptive, mặc định) ═══
// App tự quyết định mọi thứ. Giáo viên chỉ cần chụp.
val result = OmrEngine.process(
    bitmap = photoBitmap,
    templateJson = templateApiResponse,
    answerKeyJson = answerKeyApiResponse,
    options = OmrEngineOptions.presetAuto  // hoặc bỏ qua (mặc định)
)

// ═══ Presets có sẵn ═══
OmrEngineOptions.presetAuto      // Auto-adaptive (mặc định, khuyên dùng)
OmrEngineOptions.presetPhoto     // Adaptive threshold (cân bằng)
OmrEngineOptions.presetWrinkled  // Full preprocessing (giấy nhàu)
OmrEngineOptions.presetFast      // Không preprocessing (scan phẳng)

// ═══ Cách dùng nâng cao (tự chỉnh từng cờ) ═══
val result2 = OmrEngine.process(
    bitmap = photoBitmap,
    templateJson = templateApiResponse,
    answerKeyJson = answerKeyApiResponse,
    options = OmrEngineOptions(
        densityThreshold = 0.40f,
        diffThreshold = 0.20f,
        enableScoring = true,
        enableDebugImage = true,
        autoAdaptive = false,             // TẮT auto để tự chỉnh
        useAdaptiveThreshold = true,
        preprocessMarkers = true,
        preprocessPostWarp = true,
        morphCleanup = true
    )
)

// Hiển thị ảnh debug (đã resize ≤ 1200px, JPEG quality 75)
val bytes = Base64.decode(result2.debugImageBase64, Base64.DEFAULT)
val debugBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
imageView.setImageBitmap(debugBitmap)

// Xóa cache khi đổi template
NativeLib.clearOmrCache()
```

---

## 13. Tham số có thể điều chỉnh

| Tham số (C++) | Tham số (Kotlin) | Mặc định | Ảnh hưởng |
|---|---|---|---|
| `DENSITY_THRESHOLD` | `densityThreshold` | `0.40` | Tăng → ít ô được nhận diện hơn (chặt hơn). Giảm → nhiễu hơn |
| `DIFF_THRESHOLD` | `diffThreshold` | `0.20` | Tăng → ít flag nghi vấn hơn. Giảm → nhạy với tẩy xóa hơn |
| `DEWARP_GOOD_PX` | *(hằng số)* | `15` | Ngưỡng Perspective-only |
| `DEWARP_MODERATE_PX` | *(hằng số)* | `40` | Ngưỡng Hybrid |
| `LAPLACIAN_BLUR_THRESH` | *(hằng số)* | `80` | Ngưỡng cảnh báo ảnh mờ |
| `BRIGHTNESS_MIN` | *(hằng số)* | `100` | Ngưỡng cảnh báo thiếu sáng |
| `MAX_DEBUG_DIMENSION` | *(hằng số)* | `1200` | Kích thước tối đa ảnh debug (resize xuống nếu vượt) |
| `DEBUG_JPEG_QUALITY` | *(hằng số)* | `75` | Chất lượng JPEG debug (0-100) |
| `MAX_CACHE_ENTRIES` | *(hằng số)* | `8` | Số template tối đa trong cache |
| `useAdaptiveThreshold` | `useAdaptiveThreshold` | `false` | Bật tiền xử lý adaptive threshold (khuyên dùng cho ảnh thực tế) |
| `ADAPTIVE_BLOCK_SIZE` | `adaptiveBlockSize` | `31` | Kích thước block (lẻ). Lớn hơn → ổn định hơn trong thiếu sáng |
| `ADAPTIVE_C` | `adaptiveC` | `8.0` | Hằng số C. Cao hơn → ít nhạy với nét mờ |
| `preprocessMarkers` | `preprocessMarkers` | `false` | Bilateral+CLAHE trước marker detection |
| `preprocessPostWarp` | `preprocessPostWarp` | `false` | Bilateral+GaussianBlur sau warp |
| `morphCleanup` | `morphCleanup` | `false` | Close+Open+Otsu+Close sau adaptive threshold |
| **`autoAdaptive`** | **`autoAdaptive`** | **`true`** | **App tự quyết định tất cả cờ trên từ chất lượng ảnh** |

---

## 14. Hướng dẫn tích hợp OpenCV

### 14.1 Tải OpenCV Android SDK

1. Truy cập https://github.com/nihui/opencv-mobile (bản rút gọn, ~20MB) hoặc https://opencv.org/releases/
2. Tải `opencv-4.12.0-android-sdk.zip`
3. Giải nén vào `C:/Users/ADMIN/OpenCV-android-sdk/`

### 14.2 Cấu trúc thư mục OpenCV cần có

```
OpenCV-android-sdk/
├── sdk/
│   ├── native/
│   │   ├── jni/
│   │   │   ├── include/opencv2/      ← Headers
│   │   │   │   └── objdetect/
│   │   │   │       ├── aruco_detector.hpp
│   │   │   │       └── aruco_dictionary.hpp
│   │   │   └── libs/                  ← .so files per ABI
│   └── java/                          ← Java wrapper (project :opencv)
```

### 14.3 CMakeLists.txt (đã cấu hình sẵn)

```cmake
set(OpenCV_DIR "C:/Users/ADMIN/OpenCV-android-sdk/sdk/native/jni")
find_package(OpenCV REQUIRED)
target_link_libraries(${CMAKE_PROJECT_NAME} ${OpenCV_LIBS} android log jnigraphics)
```

### 14.4 build.gradle.kts (đã cấu hình sẵn)

```kotlin
implementation(project(":opencv"))
```

---

## 15. Kiểm thử

### 15.1 Tạo ảnh test

1. Dùng `template.constants.ts` và script Python để render template PDF
2. In ra giấy, tô bằng bút chì đen
3. Chụp bằng camera Android ở độ phân giải tối thiểu 1920×1080
4. Đảm bảo ảnh đủ sáng, không bị bóng, toàn bộ trang nằm trong khung hình

### 15.2 Kỳ vọng đầu ra

| Trường hợp | Kỳ vọng |
|---|---|
| Ảnh rõ, đủ 12 marker | `success: true`, `id_ok: true`, đọc đúng đáp án |
| Ảnh thiếu marker | `success: false`, `error_code: "MARKER_NOT_FOUND"` |
| Ảnh mờ | `success: true`, `warnings: ["BLURRY"]` |
| Cùng template, nhiều ảnh | Lần 2+ nhanh hơn (cache hit) |
| Ô tô đậm | `density > 0.6`, chọn chính xác |
| Ô tô nhạt | `density ~ 0.3-0.4`, có thể bị bỏ qua nếu < threshold |
| Tẩy xóa | `flag: 1` nếu chênh lệch < `diff_threshold` |

### 15.3 Kiểm tra hiệu năng

```
Mục tiêu: < 1.5 giây / ảnh trên thiết bị tầm trung (Snapdragon 600 series)
- Lần đầu (cache miss): ~1.2s (có parse JSON + tính layout)
- Lần sau (cache hit):  ~0.8s (bỏ qua parse + layout)
- Debug image: thêm ~0.15s (vẽ + encode JPEG)
```

---

## 16. Build

```bash
# Yêu cầu
# - Android Studio + NDK 28+
# - OpenCV Android SDK 4.12

cd OMR
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
