#include <jni.h>
#include <opencv2/opencv.hpp>
#include <android/log.h>
#include <android/bitmap.h>
#include "include/distortion_analyzer.h"
#include "include/tps_warper.h"
#include "include/omr_processor.h"
#include "include/layout_calculator.h"
#include "include/marker_detector.h"
#include <algorithm>

#define LOG_TAG "OMR_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace omr;

extern "C" {

/**
 * Helper: Convert Java long (Mat address) to cv::Mat pointer
 */
static cv::Mat& matFromAddr(jlong addr) {
    return *reinterpret_cast<cv::Mat*>(addr);
}

/**
 * Helper: Parse Java Map<Integer, Point> to C++ std::map<int, cv::Point2f>
 */
static void parsePointMap(JNIEnv* env, jobject mapObj, std::map<int, cv::Point2f>& outMap) {
    if (mapObj == nullptr) return;

    jclass mapClass = env->GetObjectClass(mapObj);
    jmethodID entrySetMethod = env->GetMethodID(mapClass, "entrySet", "()Ljava/util/Set;");
    jobject entrySet = env->CallObjectMethod(mapObj, entrySetMethod);

    jclass setClass = env->GetObjectClass(entrySet);
    jmethodID iteratorMethod = env->GetMethodID(setClass, "iterator", "()Ljava/util/Iterator;");
    jobject iterator = env->CallObjectMethod(entrySet, iteratorMethod);

    jclass iteratorClass = env->GetObjectClass(iterator);
    jmethodID hasNextMethod = env->GetMethodID(iteratorClass, "hasNext", "()Z");
    jmethodID nextMethod = env->GetMethodID(iteratorClass, "next", "()Ljava/lang/Object;");

    while (env->CallBooleanMethod(iterator, hasNextMethod)) {
        jobject entry = env->CallObjectMethod(iterator, nextMethod);
        jclass entryClass = env->GetObjectClass(entry);

        jmethodID getKeyMethod = env->GetMethodID(entryClass, "getKey", "()Ljava/lang/Object;");
        jmethodID getValueMethod = env->GetMethodID(entryClass, "getValue", "()Ljava/lang/Object;");

        jobject keyObj = env->CallObjectMethod(entry, getKeyMethod);
        jobject valueObj = env->CallObjectMethod(entry, getValueMethod);

        // Get marker ID
        jclass integerClass = env->GetObjectClass(keyObj);
        jmethodID intValueMethod = env->GetMethodID(integerClass, "intValue", "()I");
        int markerId = env->CallIntMethod(keyObj, intValueMethod);

        // Get Point (x, y)
        jclass pointClass = env->GetObjectClass(valueObj);
        jfieldID xField = env->GetFieldID(pointClass, "x", "D");
        jfieldID yField = env->GetFieldID(pointClass, "y", "D");

        double x = env->GetDoubleField(valueObj, xField);
        double y = env->GetDoubleField(valueObj, yField);

        outMap[markerId] = cv::Point2f(static_cast<float>(x), static_cast<float>(y));
    }
}

static std::vector<DetectedMarker> detectedMarkersFromMap(
        const std::map<int, cv::Point2f>& detectedMap
) {
    std::vector<DetectedMarker> markers;
    markers.reserve(detectedMap.size());
    for (const auto& pair : detectedMap) {
        DetectedMarker marker;
        marker.id = pair.first;
        marker.center = pair.second;
        markers.push_back(marker);
    }
    return markers;
}

static std::vector<AnchorPoint> anchorsFromMap(
        const std::map<int, cv::Point2f>& expectedMap,
        int width,
        int height
) {
    std::vector<AnchorPoint> anchors;
    anchors.reserve(expectedMap.size());
    for (const auto& pair : expectedMap) {
        anchors.push_back(AnchorPoint{
            pair.first,
            pair.second.x,
            pair.second.y,
            width > 0 ? pair.second.x / static_cast<float>(width) : 0.0f,
            height > 0 ? pair.second.y / static_cast<float>(height) : 0.0f,
        });
    }
    return anchors;
}

static void copyOrResizeToDst(const cv::Mat& warped, cv::Mat& dst, int width, int height) {
    if (warped.empty()) {
        return;
    }

    if (width > 0 && height > 0 && (warped.cols != width || warped.rows != height)) {
        cv::resize(warped, dst, cv::Size(width, height));
    } else {
        warped.copyTo(dst);
    }
}

/**
 * JNI: Analyze distortion
 */
JNIEXPORT jobject JNICALL
Java_com_examhub_student_omr_nativebridge_NativeLib_analyzeDistortion(
        JNIEnv* env,
        jclass clazz,
        jobject detectedMarkersMap,
        jobject expectedMarkersMap
) {
    LOGI("analyzeDistortion called");

    std::map<int, cv::Point2f> detectedMarkers;
    std::map<int, cv::Point2f> expectedMarkers;

    // Sử dụng hàm helper để parse map một cách chính xác
    parsePointMap(env, detectedMarkersMap, detectedMarkers);
    parsePointMap(env, expectedMarkersMap, expectedMarkers);

    DistortionAnalysis analysis = DistortionAnalyzer::analyzeDistortion(
            detectedMarkers, expectedMarkers
    );

    jclass resultClass = env->FindClass("com/examhub/student/omr/model/DistortionResult");
    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(DDILjava/lang/String;)V");
    jstring recommendation = env->NewStringUTF(analysis.recommendation.c_str());

    jobject result = env->NewObject(
            resultClass, constructor, analysis.rmsError, analysis.maxDeviation,
            static_cast<int>(analysis.severity), recommendation
    );

    LOGI("analyzeDistortion completed: RMS=%.2f", analysis.rmsError);
    return result;
}

/**
 * JNI: Apply TPS warping (ĐÃ SỬA: Dùng dstAddr để tránh Memory Leak)
 */
JNIEXPORT void JNICALL
Java_com_examhub_student_omr_nativebridge_NativeLib_tpsWarp(
        JNIEnv* env,
        jclass clazz,
        jlong srcAddr,
        jlong dstAddr,
        jobject srcPointsList,
        jobject dstPointsList,
        jint width,
        jint height,
        jdouble smoothness
) {
    LOGI("tpsWarp called: %dx%d, smoothness=%.2f", width, height, smoothness);

    cv::Mat& src = matFromAddr(srcAddr);
    cv::Mat& dst = matFromAddr(dstAddr);

    std::vector<cv::Point2f> srcPoints;
    std::vector<cv::Point2f> dstPoints;

    jclass listClass = env->GetObjectClass(srcPointsList);
    jmethodID sizeMethod = env->GetMethodID(listClass, "size", "()I");
    jmethodID getMethod = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");

    int numPoints = env->CallIntMethod(srcPointsList, sizeMethod);
    for (int i = 0; i < numPoints; i++) {
        jobject pointObj = env->CallObjectMethod(srcPointsList, getMethod, i);
        jclass pointClass = env->GetObjectClass(pointObj);
        jfieldID xField = env->GetFieldID(pointClass, "x", "D");
        jfieldID yField = env->GetFieldID(pointClass, "y", "D");

        double x = env->GetDoubleField(pointObj, xField);
        double y = env->GetDoubleField(pointObj, yField);
        srcPoints.push_back(cv::Point2f(static_cast<float>(x), static_cast<float>(y)));
    }

    numPoints = env->CallIntMethod(dstPointsList, sizeMethod);
    for (int i = 0; i < numPoints; i++) {
        jobject pointObj = env->CallObjectMethod(dstPointsList, getMethod, i);
        jclass pointClass = env->GetObjectClass(pointObj);
        jfieldID xField = env->GetFieldID(pointClass, "x", "D");
        jfieldID yField = env->GetFieldID(pointClass, "y", "D");

        double x = env->GetDoubleField(pointObj, xField);
        double y = env->GetDoubleField(pointObj, yField);
        dstPoints.push_back(cv::Point2f(static_cast<float>(x), static_cast<float>(y)));
    }

    // Chạy TPS Warper và copy thẳng kết quả vào Mat do Kotlin quản lý
    cv::Mat dewarped = TpsWarper::tpsWarp(src, srcPoints, dstPoints, cv::Size(width, height), smoothness);
    dewarped.copyTo(dst);

    LOGI("tpsWarp completed");
}

/**
 * JNI: Apply Mesh warping (STUB - Hàm giả định để không báo lỗi thiếu JNI)
 */
JNIEXPORT void JNICALL
Java_com_examhub_student_omr_nativebridge_NativeLib_meshWarp(
        JNIEnv* env,
        jclass clazz,
        jlong srcAddr,
        jlong dstAddr,
        jobject detectedMarkersMap,
        jobject expectedMarkersMap,
        jint width,
        jint height,
        jint gridSize,
        jdouble smoothness
) {
    LOGI("meshWarp called: %dx%d, gridSize=%d, smoothness=%.2f", width, height, gridSize, smoothness);
    cv::Mat& src = matFromAddr(srcAddr);
    cv::Mat& dst = matFromAddr(dstAddr);

    std::map<int, cv::Point2f> detectedMap;
    std::map<int, cv::Point2f> expectedMap;
    parsePointMap(env, detectedMarkersMap, detectedMap);
    parsePointMap(env, expectedMarkersMap, expectedMap);

    std::vector<cv::Point2f> srcPoints;
    std::vector<cv::Point2f> dstPoints;
    for (const auto& pair : detectedMap) {
        auto expectedIt = expectedMap.find(pair.first);
        if (expectedIt == expectedMap.end()) {
            continue;
        }
        srcPoints.push_back(pair.second);
        dstPoints.push_back(expectedIt->second);
    }

    if (srcPoints.size() < 4) {
        LOGE("meshWarp needs at least 4 matched markers, found %zu. Falling back to resize.", srcPoints.size());
        cv::resize(src, dst, cv::Size(width, height));
        return;
    }

    cv::Mat warped = TpsWarper::tpsWarp(
            src,
            srcPoints,
            dstPoints,
            cv::Size(width, height),
            smoothness
    );
    copyOrResizeToDst(warped, dst, width, height);
    LOGI("meshWarp completed with %zu control points", srcPoints.size());
}

/**
 * JNI: Apply Hybrid warping (STUB - Hàm giả định để không báo lỗi thiếu JNI)
 */
JNIEXPORT void JNICALL
Java_com_examhub_student_omr_nativebridge_NativeLib_hybridWarp(
        JNIEnv* env,
        jclass clazz,
        jlong srcAddr,
        jlong dstAddr,
        jobject detectedMarkersMap,
        jobject expectedMarkersMap,
        jint width,
        jint height,
        jint blendMargin
) {
    LOGI("hybridWarp called: %dx%d, blendMargin=%d", width, height, blendMargin);
    cv::Mat& src = matFromAddr(srcAddr);
    cv::Mat& dst = matFromAddr(dstAddr);

    std::map<int, cv::Point2f> detectedMap;
    std::map<int, cv::Point2f> expectedMap;
    parsePointMap(env, detectedMarkersMap, detectedMap);
    parsePointMap(env, expectedMarkersMap, expectedMap);

    const auto detectedMarkers = detectedMarkersFromMap(detectedMap);
    const auto expectedAnchors = anchorsFromMap(expectedMap, width, height);

    if (detectedMarkers.size() < 4 || expectedAnchors.size() < 4) {
        LOGE("hybridWarp needs at least 4 markers, detected=%zu expected=%zu. Falling back to resize.",
             detectedMarkers.size(), expectedAnchors.size());
        cv::resize(src, dst, cv::Size(width, height));
        return;
    }

    MarkerDetector detector;
    cv::Mat warped = detector.dewarpHybrid(src, detectedMarkers, expectedAnchors);
    if (warped.empty()) {
        LOGE("hybridWarp failed. Falling back to resize.");
        cv::resize(src, dst, cv::Size(width, height));
        return;
    }

    copyOrResizeToDst(warped, dst, width, height);
    LOGI("hybridWarp completed with %zu detected markers", detectedMarkers.size());
}

/**
 * JNI: Release Mat (Vẫn giữ lại cho an toàn nếu bạn cần gọi thủ công ở đâu đó)
 */
JNIEXPORT void JNICALL
Java_com_examhub_student_omr_nativebridge_NativeLib_releaseMat(
        JNIEnv* env,
        jclass clazz,
        jlong matAddr
) {
    if (matAddr != 0) {
        cv::Mat* mat = reinterpret_cast<cv::Mat*>(matAddr);
        delete mat;
    }
}

// ═══════════════════════════════════════════════════════════════════
// NEW: OMR Processing Pipeline JNI
// ═══════════════════════════════════════════════════════════════════

/**
 * Convert Android Bitmap to cv::Mat (BGR)
 */
static bool bitmapToMat(JNIEnv* env, jobject bitmap, cv::Mat& outMat) {
    if (bitmap == nullptr) return false;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to get bitmap info");
        return false;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to lock bitmap pixels");
        return false;
    }

    // Convert Android bitmap to cv::Mat
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        cv::Mat temp(info.height, info.width, CV_8UC4, pixels);
        cv::cvtColor(temp, outMat, cv::COLOR_RGBA2BGR);
    } else if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        cv::Mat temp(info.height, info.width, CV_8UC2, pixels);
        cv::cvtColor(temp, outMat, cv::COLOR_BGR5652BGR);
    } else if (info.format == ANDROID_BITMAP_FORMAT_A_8) {
        cv::Mat temp(info.height, info.width, CV_8UC1, pixels);
        cv::cvtColor(temp, outMat, cv::COLOR_GRAY2BGR);
    } else {
        LOGE("Unsupported bitmap format: %d", info.format);
        AndroidBitmap_unlockPixels(env, bitmap);
        return false;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return true;
}

/**
 * JNI: Full OMR processing pipeline.
 *
 * @param bitmap         Android Bitmap (input image)
 * @param templateJson   JSON string from /mobile/exams/:examId/template
 * @param answerKeyJson  Optional JSON string for answer keys (null = no scoring)
 * @param densityThresh  Bubble density threshold (0.0-1.0)
 * @param diffThresh     Ambiguity difference threshold (0.0-1.0)
 * @param enableScoring  Whether to compare with answer key and compute score
 * @param enableDebug    Whether to generate debug image
 * @param useAdaptive    Whether to use adaptive threshold instead of fixed 128
 * @param adaptiveBSize  Adaptive threshold block size (odd, 3-255)
 * @param adaptiveC      Adaptive threshold constant subtracted from mean
 * @param preMarker      Whether to apply bilateral+CLAHE before marker detection
 * @param postWarp       Whether to apply bilateral+GaussianBlur after warp
 * @param morphCleanup   Whether to apply morphological cleanup after threshold
 * @param autoAdaptive   If true: auto-decide all of the above from image quality
 * @return               Result JSON string
 */
JNIEXPORT jstring JNICALL
Java_com_examhub_student_omr_nativebridge_NativeLib_processOmr(
        JNIEnv* env,
        jclass clazz,
        jobject bitmap,
        jstring templateJson,
        jstring answerKeyJson,
        jfloat densityThresh,
        jfloat diffThresh,
        jboolean enableScoring,
        jboolean enableDebug,
        jboolean useAdaptive,
        jint adaptiveBSize,
        jfloat adaptiveC,
        jboolean preMarker,
        jboolean postWarp,
        jboolean morphCleanup,
        jint requiredMarkers,
        jboolean autoAdaptive
) {
    LOGI("processOmr called");

    // --- Convert Bitmap to cv::Mat ---
    cv::Mat image;
    if (!bitmapToMat(env, bitmap, image)) {
        LOGE("Failed to convert bitmap");
        std::string errJson = "{\"success\":false,\"error_code\":\"BITMAP_CONVERT_FAILED\","
                              "\"error_message\":\"Failed to convert Android Bitmap to cv::Mat\"}";
        return env->NewStringUTF(errJson.c_str());
    }

    LOGI("Bitmap converted: %dx%d, channels=%d", image.cols, image.rows, image.channels());

    // --- Get JSON strings ---
    const char* templateStr = env->GetStringUTFChars(templateJson, nullptr);
    std::string templateJsonStr(templateStr);
    env->ReleaseStringUTFChars(templateJson, templateStr);

    std::string answerKeyStr;
    if (answerKeyJson != nullptr) {
        const char* answerKeyChars = env->GetStringUTFChars(answerKeyJson, nullptr);
        answerKeyStr = std::string(answerKeyChars);
        env->ReleaseStringUTFChars(answerKeyJson, answerKeyChars);
    }

    // --- Configure options ---
    OmrOptions options;
    options.enable_scoring       = (enableScoring == JNI_TRUE) && !answerKeyStr.empty();
    options.enable_debug_image   = (enableDebug == JNI_TRUE);
    options.density_threshold    = static_cast<float>(densityThresh);
    options.diff_threshold       = static_cast<float>(diffThresh);
    options.return_debug_base64  = (enableDebug == JNI_TRUE);
    options.use_adaptive_threshold = (useAdaptive == JNI_TRUE);
    options.adaptive_block_size  = static_cast<int>(adaptiveBSize);
    options.adaptive_c           = static_cast<float>(adaptiveC);
    options.preprocess_markers   = (preMarker == JNI_TRUE);
    options.preprocess_post_warp = (postWarp == JNI_TRUE);
    options.morph_cleanup        = (morphCleanup == JNI_TRUE);
    options.required_markers     = std::max(4, std::min(12, static_cast<int>(requiredMarkers)));
    options.auto_adaptive        = (autoAdaptive == JNI_TRUE);

    LOGI(
        "OMR_JNI options templateBytes=%zu answerKeyBytes=%zu scoring=%d debug=%d auto=%d adaptive=%d block=%d C=%.1f preMarker=%d postWarp=%d morph=%d requiredMarkers=%d density=%.2f diff=%.2f",
        templateJsonStr.size(),
        answerKeyStr.size(),
        options.enable_scoring,
        options.enable_debug_image,
        options.auto_adaptive,
        options.use_adaptive_threshold,
        options.adaptive_block_size,
        options.adaptive_c,
        options.preprocess_markers,
        options.preprocess_post_warp,
        options.morph_cleanup,
        options.required_markers,
        options.density_threshold,
        options.diff_threshold
    );

    // --- Run pipeline ---
    OmrProcessor processor(options);
    OmrResult result = processor.process(image, templateJsonStr, answerKeyStr);

    // --- Serialize result ---
    std::string resultJson = OmrProcessor::resultToJson(result);

    LOGI("processOmr completed: success=%d, answers=%zu, score=%d/%d",
         result.success, result.answers.size(),
         result.scored ? result.score_result.correct_count : 0,
         result.scored ? result.score_result.total_questions : 0);

    if (!result.success) {
        LOGI("Error: %s - %s", result.error_code.c_str(), result.error_message.c_str());
    }

    return env->NewStringUTF(resultJson.c_str());
}

/**
 * JNI: Get native Mat address from Bitmap for advanced usage.
 * Caller must call releaseMat() when done.
 */
JNIEXPORT jlong JNICALL
Java_com_examhub_student_omr_nativebridge_NativeLib_bitmapToNativeMat(
        JNIEnv* env,
        jclass clazz,
        jobject bitmap
) {
    cv::Mat image;
    if (!bitmapToMat(env, bitmap, image)) {
        return 0;
    }
    cv::Mat* matPtr = new cv::Mat(image.clone());
    return reinterpret_cast<jlong>(matPtr);
}

/**
 * JNI: Encode a Mat as JPEG base64 string.
 */
JNIEXPORT jstring JNICALL
Java_com_examhub_student_omr_nativebridge_NativeLib_matToJpegBase64(
        JNIEnv* env,
        jclass clazz,
        jlong matAddr,
        jint quality
) {
    if (matAddr == 0) {
        return env->NewStringUTF("");
    }

    cv::Mat& mat = *reinterpret_cast<cv::Mat*>(matAddr);
    std::vector<uchar> buf;
    std::vector<int> params = { cv::IMWRITE_JPEG_QUALITY, static_cast<int>(quality) };
    cv::imencode(".jpg", mat, buf, params);

    // Base64 encode
    static const char* base64Chars =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string base64;
    base64.reserve(((buf.size() + 2) / 3) * 4);

    for (size_t i = 0; i < buf.size(); i += 3) {
        uint32_t n = static_cast<uint32_t>(buf[i]) << 16;
        if (i + 1 < buf.size()) n |= static_cast<uint32_t>(buf[i + 1]) << 8;
        if (i + 2 < buf.size()) n |= static_cast<uint32_t>(buf[i + 2]);

        base64 += base64Chars[(n >> 18) & 0x3F];
        base64 += base64Chars[(n >> 12) & 0x3F];
        base64 += (i + 1 < buf.size()) ? base64Chars[(n >> 6) & 0x3F] : '=';
        base64 += (i + 2 < buf.size()) ? base64Chars[n & 0x3F] : '=';
    }

    return env->NewStringUTF(base64.c_str());
}

/**
 * JNI: Get the native Mat address (for interop with other JNI calls).
 */
JNIEXPORT jlong JNICALL
Java_com_examhub_student_omr_nativebridge_NativeLib_getNativeMatAddr(
        JNIEnv* env,
        jclass clazz,
        jlong matAddr
) {
    return matAddr;
}

/**
 * JNI: Clear the layout cache (call after template update).
 */
JNIEXPORT void JNICALL
Java_com_examhub_student_omr_nativebridge_NativeLib_clearOmrCache(
        JNIEnv* env,
        jclass clazz
) {
    LOGI("clearOmrCache called");
    OmrProcessor::clearLayoutCache();
}

} // extern "C"
