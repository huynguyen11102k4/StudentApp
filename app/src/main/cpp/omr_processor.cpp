#include "include/omr_processor.h"
#include "include/layout_calculator.h"
#include "include/marker_detector.h"
#include <opencv2/imgproc.hpp>
#include <opencv2/imgcodecs.hpp>
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <limits>
#include <sstream>
#include <cstring>
#include <set>

#define LOG_TAG "OmrProcessor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace omr {

namespace {

constexpr double TPS_STABLE_SMOOTHNESS = 0.80;

const char* dewarpMethodName(DewarpMethod method) {
    switch (method) {
        case DewarpMethod::PERSPECTIVE: return "PERSPECTIVE";
        case DewarpMethod::HYBRID: return "HYBRID";
        case DewarpMethod::TPS: return "TPS";
        case DewarpMethod::NONE:
        default: return "NONE";
    }
}

const char* euclideanCaseName(float maxError) {
    if (maxError <= DEWARP_GOOD_PX) {
        return "EUCLID_LE_15";
    }
    if (maxError < DEWARP_MODERATE_PX) {
        return "EUCLID_15_TO_25";
    }
    return "EUCLID_GE_25";
}

bool idZoneRequiresStudentId(const IdZoneConfig& idZone) {
    return std::any_of(idZone.items.begin(), idZone.items.end(), [](const IdZoneItem& item) {
        return item.enabled && item.type == "student_id" && item.num_digits > 0;
    });
}

bool answerKeyContainsMultipleCorrectAnswers(const std::map<int, std::string>& answerKey) {
    for (const auto& pair : answerKey) {
        int optionCount = 0;
        bool seen[26] = {false};
        for (char c : pair.second) {
            char upper = c;
            if (upper >= 'a' && upper <= 'z') {
                upper = static_cast<char>(upper - 'a' + 'A');
            }
            if (upper >= 'A' && upper <= 'Z' && !seen[upper - 'A']) {
                seen[upper - 'A'] = true;
                optionCount++;
            }
        }
        if (optionCount > 1) {
            return true;
        }
    }
    return false;
}

size_t countUniqueDetectedMarkerIds(const std::vector<DetectedMarker>& markers) {
    std::set<int> uniqueIds;
    for (const auto& marker : markers) {
        uniqueIds.insert(marker.id);
    }
    return uniqueIds.size();
}

int countAnswerOptions(const std::string& answer) {
    bool seen[26] = {false};
    int count = 0;
    for (char c : answer) {
        char upper = c;
        if (upper >= 'a' && upper <= 'z') {
            upper = static_cast<char>(upper - 'a' + 'A');
        }
        if (upper >= 'A' && upper <= 'Z' && !seen[upper - 'A']) {
            seen[upper - 'A'] = true;
            count++;
        }
    }
    return count;
}

std::string normalizedAnswerSet(const std::string& answer) {
    std::vector<char> chars;
    for (char c : answer) {
        char upper = c;
        if (upper >= 'a' && upper <= 'z') {
            upper = static_cast<char>(upper - 'a' + 'A');
        }
        if (upper >= 'A' && upper <= 'Z') {
            chars.push_back(upper);
        }
    }
    std::sort(chars.begin(), chars.end());
    chars.erase(std::unique(chars.begin(), chars.end()), chars.end());

    std::string result;
    for (size_t i = 0; i < chars.size(); i++) {
        if (i > 0) result += ',';
        result += chars[i];
    }
    return result;
}

bool isReadableOmrCode(const std::string& value) {
    return !value.empty() && value.find('?') == std::string::npos;
}

int knownOmrDigitCount(const std::string& value) {
    int count = 0;
    for (char c : value) {
        if (c >= '0' && c <= '9') {
            count++;
        }
    }
    return count;
}

float medianValue(std::vector<float> values) {
    if (values.empty()) return 0.0f;
    size_t mid = values.size() / 2;
    std::nth_element(values.begin(), values.begin() + mid, values.end());
    float med = values[mid];
    if (values.size() % 2 == 0 && mid > 0) {
        std::nth_element(values.begin(), values.begin() + mid - 1, values.end());
        med = (med + values[mid - 1]) * 0.5f;
    }
    return med;
}

int idReadQualityScore(const IdReadResult& id) {
    int score = 0;
    if (isReadableOmrCode(id.student_id)) score += 1000;
    if (isReadableOmrCode(id.exam_code)) score += 600;
    if (isReadableOmrCode(id.class_code)) score += 200;
    score += knownOmrDigitCount(id.student_id) * 10;
    score += knownOmrDigitCount(id.exam_code) * 6;
    score += knownOmrDigitCount(id.class_code) * 2;
    return score;
}

AnswerReadResult buildAnswerResult(
    int questionNumber,
    const std::vector<float>& densities,
    const std::string& selectionMode,
    float densityThreshold,
    float diffThreshold
) {
    int top1Idx = -1;
    int top2Idx = -1;
    float top1Val = -1.0f;
    float top2Val = -1.0f;

    for (size_t i = 0; i < densities.size(); i++) {
        if (densities[i] > top1Val) {
            top2Val = top1Val;
            top2Idx = top1Idx;
            top1Val = densities[i];
            top1Idx = static_cast<int>(i);
        } else if (densities[i] > top2Val) {
            top2Val = densities[i];
            top2Idx = static_cast<int>(i);
        }
    }

    AnswerReadResult ar;
    ar.question_number = questionNumber;
    ar.flag = 0;

    if (top1Val < densityThreshold) {
        ar.answer = "";
        return ar;
    }

    std::string markedAnswer;
    for (size_t i = 0; i < densities.size(); i++) {
        if (densities[i] >= densityThreshold) {
            if (!markedAnswer.empty()) markedAnswer += ',';
            markedAnswer += static_cast<char>('A' + i);
        }
    }

    ar.answer = markedAnswer;
    if (countAnswerOptions(markedAnswer) > 1 ||
        (top2Idx >= 0 && top2Val >= densityThreshold && (top1Val - top2Val) < diffThreshold)) {
        ar.flag = 1;
    }
    return ar;
}

std::string encodeJpegBase64(const cv::Mat& image, int quality) {
    std::vector<uchar> buf;
    cv::imencode(".jpg", image, buf, { cv::IMWRITE_JPEG_QUALITY, quality });

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
    return base64;
}

} // namespace

// ─── Static layout cache ───────────────────────────────────────────

std::unordered_map<uint64_t, LayoutCacheEntry> OmrProcessor::s_layoutCache_;
std::mutex OmrProcessor::s_cacheMutex_;
std::vector<uint64_t> OmrProcessor::s_cacheOrder_;

// ─── Constructor ────────────────────────────────────────────────────

OmrProcessor::OmrProcessor(const OmrOptions& options)
    : options_(options) {}

// ─── Main Entry Point ───────────────────────────────────────────────

OmrResult OmrProcessor::process(
    const cv::Mat& image,
    const std::string& templateJson,
    const std::string& answerKeyJson
) {
    OmrResult result{};
    result.success = false;
    result.scored = false;

    if (image.empty()) {
        result.error_code = "EMPTY_IMAGE";
        result.error_message = "Input image is empty";
        return result;
    }

    // --- Parse template JSON ---
    std::vector<AnchorPoint> anchors;
    IdZoneConfig idZone;
    std::vector<AnswerZoneConfig> answerZones;

    if (!parseTemplateJson(templateJson, anchors, idZone, answerZones)) {
        result.error_code = "PARSE_ERROR";
        result.error_message = "Failed to parse template JSON";
        return result;
    }
    LOGI(
        "OMR_PIPELINE template parsed anchors=%zu idItems=%zu answerZones=%zu templateJsonBytes=%zu",
        anchors.size(),
        idZone.items.size(),
        answerZones.size(),
        templateJson.size()
    );

    // --- Parse answer key (optional) ---
    std::map<std::string, std::map<int, std::string>> answerKeys;
    if (!answerKeyJson.empty() && options_.enable_scoring) {
        parseAnswerKeyJson(answerKeyJson, answerKeys);
    }
    LOGI(
        "OMR_PIPELINE answerKey parsed enabledScoring=%d keys=%zu answerKeyBytes=%zu",
        options_.enable_scoring,
        answerKeys.size(),
        answerKeyJson.size()
    );

    return processWithConfig(image, anchors, idZone, answerZones, answerKeys, templateJson);
}

OmrResult OmrProcessor::processWithConfig(
    const cv::Mat& image,
    const std::vector<AnchorPoint>& anchorPoints,
    const IdZoneConfig& idZone,
    const std::vector<AnswerZoneConfig>& answerZones,
    const std::map<std::string, std::map<int, std::string>>& answerKeys,
    const std::string& templateJson
) {
    OmrResult result{};
    result.success = false;
    result.scored = false;

    if (image.empty()) {
        result.error_code = "EMPTY_IMAGE";
        result.error_message = "Input image is empty";
        return result;
    }

    // ── Step 1: Convert to grayscale if needed ─────────────────
    cv::Mat gray;
    if (image.channels() >= 3) {
        cv::cvtColor(image, gray, cv::COLOR_BGR2GRAY);
    } else {
        gray = image.clone();
    }

    // ── Step 2: Quality check ─────────────────────────────────
    MarkerDetector::checkQuality(gray, result.laplacian_variance, result.mean_brightness);

    if (result.laplacian_variance < LAPLACIAN_BLUR_THRESH) {
        result.warnings.push_back("BLURRY");
    }
    if (result.mean_brightness < BRIGHTNESS_MIN) {
        result.warnings.push_back("LOW_LIGHT");
    }

    // ── Step 2.5a: Auto-adaptive pipeline decision ────────────
    LOGI(
        "OMR_PIPELINE initial options auto=%d adaptive=%d block=%d C=%.1f preMarker=%d postWarp=%d morph=%d scoring=%d debug=%d density=%.2f diff=%.2f",
        options_.auto_adaptive,
        options_.use_adaptive_threshold,
        options_.adaptive_block_size,
        options_.adaptive_c,
        options_.preprocess_markers,
        options_.preprocess_post_warp,
        options_.morph_cleanup,
        options_.enable_scoring,
        options_.enable_debug_image,
        options_.density_threshold,
        options_.diff_threshold
    );
    if (options_.auto_adaptive) {
        autoConfigurePipeline(result.laplacian_variance, result.mean_brightness);
    }
    LOGI(
        "OMR_PIPELINE selected options adaptive=%d block=%d C=%.1f preMarker=%d postWarp=%d morph=%d qualityLap=%.2f brightness=%.2f",
        options_.use_adaptive_threshold,
        options_.adaptive_block_size,
        options_.adaptive_c,
        options_.preprocess_markers,
        options_.preprocess_post_warp,
        options_.morph_cleanup,
        result.laplacian_variance,
        result.mean_brightness
    );

    // ── Step 2.5: Preprocessing for marker detection (optional) ──
    // Matches Python pipeline: Bilateral + CLAHE before ArUco
    cv::Mat grayForMarkers = gray;
    if (options_.preprocess_markers) {
        LOGI("OMR_PIPELINE marker preprocessing selected: bilateral+CLAHE");
        grayForMarkers = preprocessForMarkers(gray);
    } else {
        LOGI("OMR_PIPELINE marker preprocessing selected: none");
    }

    // ── Step 3: Detect markers ────────────────────────────────
    MarkerDetector markerDetector;
    std::vector<DetectedMarker> detectedMarkers = markerDetector.detectMarkers(grayForMarkers);
    size_t uniqueMarkerCount = countUniqueDetectedMarkerIds(detectedMarkers);
    size_t requiredMarkerCount = static_cast<size_t>(
        std::max(4, std::min(options_.required_markers, static_cast<int>(anchorPoints.size())))
    );
    LOGI("OMR_PIPELINE marker detection completed count=%zu unique=%zu expected=%zu required=%zu",
         detectedMarkers.size(), uniqueMarkerCount, anchorPoints.size(), requiredMarkerCount);

    if (uniqueMarkerCount < requiredMarkerCount) {
        result.error_code = "MARKER_NOT_FOUND";
        result.error_message = "Not enough markers found: " +
                               std::to_string(uniqueMarkerCount) + "/" +
                               std::to_string(requiredMarkerCount);
        return result;
    }

    // ── Step 4: Align/Warp image ──────────────────────────────
    cv::Mat warped = alignImage(image, detectedMarkers, anchorPoints, result);
    if (warped.empty()) {
        result.error_code = "DEWARP_FAILED";
        result.error_message = "Image dewarping failed";
        return result;
    }

    // Convert warped to gray for bubble reading
    cv::Mat warpedGray;
    if (warped.channels() >= 3) {
        cv::cvtColor(warped, warpedGray, cv::COLOR_BGR2GRAY);
    } else {
        warpedGray = warped;
    }

    // ── Post-warp preprocessing (optional) ────────────────────
    // Matches Python pipeline: Bilateral + GaussianBlur
    if (options_.preprocess_post_warp) {
        LOGI("OMR_PIPELINE post-warp preprocessing selected: bilateral+gaussian");
        warpedGray = preprocessPostWarp(warpedGray);
    } else {
        LOGI("OMR_PIPELINE post-warp preprocessing selected: none");
    }

    // ── Adaptive threshold preprocessing (optional) ──────────
    bool geometryDistortedForRead = false;
    for (const auto& warning : result.warnings) {
        if (warning.rfind("HOMOGRAPHY_HIGH_RESIDUAL", 0) == 0 ||
            warning.rfind("PARTIAL_MARKER_HOMOGRAPHY", 0) == 0 ||
            warning == "PIECEWISE_MESH_DEWARP" ||
            warning == "TPS_DEWARP") {
            geometryDistortedForRead = true;
            break;
        }
    }
    if (geometryDistortedForRead && !options_.use_adaptive_threshold) {
        options_.use_adaptive_threshold = true;
        options_.adaptive_block_size = ADAPTIVE_BLOCK_SIZE;
        options_.adaptive_c = ADAPTIVE_C;
        options_.morph_cleanup = true;
        result.warnings.push_back("GEOMETRY_ADAPTIVE_THRESHOLD");
        LOGI("OMR_THRESHOLD forced: geometry distortion detected -> adaptive + morph");
    }

    warpedBinary_.release();
    if (options_.use_adaptive_threshold) {
        // Ensure block size is odd and in valid range
        int bs = options_.adaptive_block_size | 1;  // force odd
        bs = std::max(3, std::min(255, bs));

        cv::adaptiveThreshold(warpedGray, warpedBinary_, 255,
            cv::ADAPTIVE_THRESH_GAUSSIAN_C,
            cv::THRESH_BINARY,        // bubbles=0 (dark), background=255 → same <128 density check
            bs,
            options_.adaptive_c);

        // ── Morphological cleanup (optional) ─────────────────
        // Matches Python pipeline: Close + Open + Otsu
        if (options_.morph_cleanup) {
            applyMorphCleanup(warpedBinary_);
        }

        LOGI("Adaptive threshold: blockSize=%d, C=%.1f, morph=%d",
             bs, options_.adaptive_c, options_.morph_cleanup);
        LOGI("OMR_THRESHOLD selected: ADAPTIVE_GAUSSIAN block=%d C=%.1f morphCleanup=%d", bs, options_.adaptive_c, options_.morph_cleanup);
    } else {
        LOGI("OMR_THRESHOLD selected: FIXED_GRAY_DENSITY cutoff=128");
    }
    LOGI(
        "OMR_READER selected: bubbleDensity=INNER_CORE coreRatio=%.2f minCoreRadius=%d ignorePrintedRing=1",
        BUBBLE_READ_CORE_RATIO,
        BUBBLE_READ_MIN_CORE_RADIUS
    );

    // ── Step 5: Read ID zone (with cache) ────────────────────
    uint64_t tplHash = templateHash(templateJson);
    const LayoutCacheEntry* cacheEntry = getCachedLayout(tplHash);

    bool strongDewarpUsed = false;
    for (const auto& warning : result.warnings) {
        if (warning == "PIECEWISE_MESH_DEWARP" || warning == "TPS_DEWARP") {
            strongDewarpUsed = true;
            break;
        }
    }

    cv::Mat idReadGray = warpedGray;
    cv::Mat idAlignedDebugGray;

    if (cacheEntry != nullptr) {
        LOGI("Layout cache HIT (hash=%llu)", static_cast<unsigned long long>(tplHash));
        LOGI("OMR_PIPELINE layout selected: CACHE_HIT hash=%llu", static_cast<unsigned long long>(tplHash));
    } else {
        LOGI("Layout cache MISS (hash=%llu), computing...", static_cast<unsigned long long>(tplHash));
        LOGI("OMR_PIPELINE layout selected: CACHE_MISS_COMPUTE hash=%llu", static_cast<unsigned long long>(tplHash));
    }

    auto readIdCandidate = [&](const cv::Mat& candidateGray, const cv::Mat& candidateBinary) {
        cv::Mat savedBinary = warpedBinary_;
        if (!candidateBinary.empty()) {
            warpedBinary_ = candidateBinary;
        }

        IdReadResult candidate = cacheEntry != nullptr
            ? readIdZoneCached(candidateGray, idZone, cacheEntry->idCells)
            : readIdZone(candidateGray, idZone);

        warpedBinary_ = savedBinary;
        return candidate;
    };

    auto tryLocalIdZoneRead = [&](const char* reason, bool requireReadableStudent) {
        MarkerDetector idMarkerDetector;
        std::vector<int> usedIdMarkerIds;
        cv::Mat localIdWarped = idMarkerDetector.dewarpLocalRegion(
            image,
            detectedMarkers,
            anchorPoints,
            idZone.bounding_box,
            usedIdMarkerIds
        );

        if (localIdWarped.empty()) {
            LOGI("OMR_ID local zone retry skipped reason=%s: local warp failed", reason);
            return false;
        }

        cv::Mat localIdGray;
        if (localIdWarped.channels() >= 3) {
            cv::cvtColor(localIdWarped, localIdGray, cv::COLOR_BGR2GRAY);
        } else {
            localIdGray = localIdWarped;
        }

        if (options_.preprocess_post_warp) {
            localIdGray = preprocessPostWarp(localIdGray);
        }

        cv::Mat localIdBinary;
        if (options_.use_adaptive_threshold) {
            int bs = options_.adaptive_block_size | 1;
            bs = std::max(3, std::min(255, bs));

            cv::adaptiveThreshold(localIdGray, localIdBinary, 255,
                cv::ADAPTIVE_THRESH_GAUSSIAN_C,
                cv::THRESH_BINARY,
                bs,
                options_.adaptive_c);

            if (options_.morph_cleanup) {
                applyMorphCleanup(localIdBinary);
            }
        }

        IdReadResult localIdResult = readIdCandidate(localIdGray, localIdBinary);
        int currentQuality = idReadQualityScore(result.id_result);
        int localQuality = idReadQualityScore(localIdResult);
        bool localStudentReadable = isReadableOmrCode(localIdResult.student_id);
        bool shouldSelect = localQuality > currentQuality && localQuality > 0;
        if (requireReadableStudent) {
            shouldSelect = shouldSelect && localStudentReadable;
        }

        if (shouldSelect) {
            result.id_result = localIdResult;
            idReadGray = localIdGray;
            idAlignedDebugGray = localIdGray;
            result.warnings.push_back("LOCAL_ID_ZONE_READ");
            LOGI(
                "OMR_ID selected: LOCAL_ZONE_DEWARP reason=%s markers=%zu quality=%d previous=%d student_id=%s",
                reason,
                usedIdMarkerIds.size(),
                localQuality,
                currentQuality,
                result.id_result.student_id.c_str()
            );
            if (requireReadableStudent) {
                LOGI(
                    "OMR_DEWARP_ID_RETRY executedAlgorithm=LOCAL_HOMOGRAPHY reason=%s markers=%zu"
                    " quality=%d previous=%d studentId=%s",
                    reason,
                    usedIdMarkerIds.size(),
                    localQuality,
                    currentQuality,
                    result.id_result.student_id.c_str()
                );
            }
            return true;
        }

        LOGI(
            "OMR_ID local zone retry skipped reason=%s markers=%zu quality=%d previous=%d"
            " studentReadable=%d student_id=%s",
            reason,
            usedIdMarkerIds.size(),
            localQuality,
            currentQuality,
            localStudentReadable,
            localIdResult.student_id.c_str()
        );
        return false;
    };

    result.id_result = readIdCandidate(idReadGray, warpedBinary_);
    LOGI("OMR_ID selected: SHARED_MAIN_WARP quality=%d strongDewarp=%d",
         idReadQualityScore(result.id_result), strongDewarpUsed);

    if (geometryDistortedForRead && !idZone.items.empty()) {
        LOGI("OMR_ID geometry mode: shared main warp first, then one local ID-zone pass");
        tryLocalIdZoneRead("geometry_distorted", false);
    }

    const bool needsStudentId = idZoneRequiresStudentId(idZone);
    const bool studentIdUnreadable = needsStudentId && !isReadableOmrCode(result.id_result.student_id);

    if (studentIdUnreadable && !idZone.items.empty()) {
        int previousQuality = idReadQualityScore(result.id_result);
        LOGI(
            "OMR_ID result unreadable for student_id; trying local ID-zone retry previousQuality=%d student_id=%s",
            previousQuality,
            result.id_result.student_id.c_str()
        );
        tryLocalIdZoneRead("id_unreadable", true);
    }

    std::vector<AnswerZoneConfig> answerZonesForRead = answerZones;
    std::string effectiveSelectionMode = answerZones.empty() ? "single" : answerZones[0].selection_mode;
    if (!answerKeys.empty()) {
        std::string examCode = result.id_result.exam_code;
        auto keyIt = answerKeys.find(examCode);
        if (keyIt == answerKeys.end() && examCode.empty() && answerKeys.size() == 1) {
            keyIt = answerKeys.begin();
        }
        if (keyIt != answerKeys.end() && answerKeyContainsMultipleCorrectAnswers(keyIt->second)) {
            for (auto& zone : answerZonesForRead) {
                zone.selection_mode = "multiple";
            }
            effectiveSelectionMode = "multiple";
            LOGI("OMR_READER selected: multiple mode from answer key");
        } else if (!answerZonesForRead.empty()) {
            effectiveSelectionMode = answerZonesForRead[0].selection_mode;
        }
    }

    if (cacheEntry != nullptr) {
        result.answers = readAnswerZonesCached(warpedGray, answerZonesForRead, cacheEntry->answerCells);
    } else {
        result.answers = readAnswerZones(warpedGray, answerZonesForRead);

        LayoutCacheEntry entry;
        LayoutCalculator::computeIdZoneLayout(idZone, entry.idCells);
        for (const auto& az : answerZones) {
            LayoutCalculator::computeAnswerZoneLayout(az, entry.answerCells[az.zone_id]);
        }
        putCachedLayout(tplHash, entry);
    }

    bool geometryWarning = false;
    std::map<std::string, cv::Mat> answerAlignedDebugGrays;
    for (const auto& warning : result.warnings) {
        if (warning.rfind("HOMOGRAPHY_HIGH_RESIDUAL", 0) == 0 ||
            warning.rfind("PARTIAL_MARKER_HOMOGRAPHY", 0) == 0 ||
            warning == "PIECEWISE_MESH_DEWARP" ||
            warning == "TPS_DEWARP") {
            geometryWarning = true;
            break;
        }
    }
    if (geometryWarning && !answerZonesForRead.empty()) {
        std::vector<AnswerReadResult> localAnswers = readAnswerZonesLocalDewarp(
            image,
            detectedMarkers,
            anchorPoints,
            answerZonesForRead,
            &answerAlignedDebugGrays
        );
        if (localAnswers.size() >= result.answers.size() && !localAnswers.empty()) {
            result.answers = localAnswers;
            result.warnings.push_back("LOCAL_ANSWER_ZONE_READ");
            LOGI("OMR_PIPELINE answer reading selected: LOCAL_ZONE_DEWARP answers=%zu",
                 result.answers.size());
        } else {
            answerAlignedDebugGrays.clear();
            LOGI("OMR_PIPELINE local zone read skipped: global=%zu local=%zu",
                 result.answers.size(), localAnswers.size());
        }
    }
    LOGI(
        "OMR_PIPELINE read completed student_id=%s class_code=%s exam_code=%s answers=%zu",
        result.id_result.student_id.c_str(),
        result.id_result.class_code.c_str(),
        result.id_result.exam_code.c_str(),
        result.answers.size()
    );

    // ── Step 7: Scoring (if enabled) ──────────────────────────
    if (options_.enable_scoring && !answerKeys.empty()) {
        // Try to find answer key matching exam_code
        std::string examCode = result.id_result.exam_code;
        auto keyIt = answerKeys.find(examCode);
        if (keyIt == answerKeys.end() && examCode.empty() && answerKeys.size() == 1) {
            keyIt = answerKeys.begin();
            LOGI("OMR_SCORING selected: single answer key because template has no exam_code field key='%s'", keyIt->first.c_str());
        }

        if (keyIt != answerKeys.end()) {
            LOGI("OMR_SCORING selected: answerKey exam_code='%s' questions=%zu", examCode.c_str(), keyIt->second.size());
            result.score_result = scoreAnswers(
                result.answers,
                keyIt->second,
                effectiveSelectionMode
            );
            result.scored = true;
            LOGI(
                "OMR_SCORING completed correct=%d total=%d score=%.2f",
                result.score_result.correct_count,
                result.score_result.total_questions,
                result.score_result.total_score
            );
        } else {
            result.scored = false;
            result.warnings.push_back("No answer key found for exam_code='" + examCode + "'");
            LOGI("Scoring skipped: no answer key for exam_code='%s'", examCode.c_str());
            LOGI("OMR_SCORING selected: no_matching_answer_key exam_code='%s' availableKeys=%zu", examCode.c_str(), answerKeys.size());
        }
    } else {
        result.scored = false;
        LOGI("OMR_SCORING selected: disabled_or_no_keys enable=%d keys=%zu", options_.enable_scoring, answerKeys.size());
    }

    // ── Step 8: Debug image (if enabled) ──────────────────────
    if (options_.enable_debug_image) {
        LOGI("OMR_DEBUG selected: generate debug image returnBase64=%d", options_.return_debug_base64);
        if (options_.return_debug_base64 && !warpedGray.empty()) {
            result.dewarped_image_base64 = encodeJpegBase64(warpedGray, DEBUG_JPEG_QUALITY);
        }

        // Determine which answer key to pass for debug coloring
        const std::map<int, std::string>* correctKeyForDebug = nullptr;
        const std::map<int, std::string>* activeKey = nullptr;
        if (!answerKeys.empty()) {
            std::string examCode = result.id_result.exam_code;
            auto keyIt = answerKeys.find(examCode);
            if (keyIt == answerKeys.end() && examCode.empty() && answerKeys.size() == 1) {
                keyIt = answerKeys.begin();
            }
            if (keyIt != answerKeys.end()) {
                activeKey = &keyIt->second;
                correctKeyForDebug = activeKey;
            }
        }

        cv::Mat debugImg = generateDebugImage(
            warpedGray, idZone, answerZones,
            result.id_result, result.answers,
            result.scored ? &result.score_result : nullptr,
            correctKeyForDebug,
            idAlignedDebugGray.empty() ? nullptr : &idAlignedDebugGray,
            answerAlignedDebugGrays.empty() ? nullptr : &answerAlignedDebugGrays
        );

        if (!debugImg.empty()) {
            if (options_.return_debug_base64) {
                result.debug_image_base64 = encodeJpegBase64(debugImg, DEBUG_JPEG_QUALITY);
            } else {
                // Store file path (caller handles writing)
                result.debug_image_base64 = "debug_image_generated";
            }
        }
    }

    result.success = true;
    return result;
}

// ─── Parse Template JSON ───────────────────────────────────────────

// Minimal JSON parser — uses nlohmann-style manual parsing for simplicity
// since we avoid external dependencies. For production, use nlohmann/json.
namespace {

// Extremely simple JSON value extractor
// In production, use nlohmann/json. Here we implement a minimal parser
// that handles the specific JSON structure we expect.

std::string jsonGetString(const std::string& json, const std::string& key, size_t startPos = 0) {
    std::string search = "\"" + key + "\"";
    size_t pos = json.find(search, startPos);
    if (pos == std::string::npos) return "";

    pos = json.find(':', pos + search.length());
    if (pos == std::string::npos) return "";

    // Skip whitespace
    while (++pos < json.length() && (json[pos] == ' ' || json[pos] == '\t' || json[pos] == '\n'));

    if (pos >= json.length()) return "";

    if (json[pos] == '"') {
        size_t end = json.find('"', pos + 1);
        if (end == std::string::npos) return "";
        return json.substr(pos + 1, end - pos - 1);
    }
    // Number value
    if (json[pos] == '-' || (json[pos] >= '0' && json[pos] <= '9')) {
        size_t end = pos;
        while (end < json.length() && (json[end] == '-' || json[end] == '.' ||
               (json[end] >= '0' && json[end] <= '9'))) end++;
        return json.substr(pos, end - pos);
    }
    // true/false
    if (json.substr(pos, 4) == "true") return "true";
    if (json.substr(pos, 5) == "false") return "false";
    return "";
}

float jsonGetFloat(const std::string& json, const std::string& key, float defVal = 0.0f, size_t startPos = 0) {
    std::string s = jsonGetString(json, key, startPos);
    if (s.empty()) return defVal;
    return std::stof(s);
}

int jsonGetInt(const std::string& json, const std::string& key, int defVal = 0, size_t startPos = 0) {
    std::string s = jsonGetString(json, key, startPos);
    if (s.empty()) return defVal;
    return std::stoi(s);
}

bool jsonGetBool(const std::string& json, const std::string& key, bool defVal = false, size_t startPos = 0) {
    std::string s = jsonGetString(json, key, startPos);
    if (s == "true") return true;
    if (s == "false") return false;
    return defVal;
}

// Find a JSON object start position by key
size_t jsonFindObject(const std::string& json, const std::string& key, size_t startPos = 0) {
    std::string search = "\"" + key + "\"";
    size_t pos = json.find(search, startPos);
    if (pos == std::string::npos) return std::string::npos;

    pos = json.find(':', pos + search.length());
    if (pos == std::string::npos) return std::string::npos;

    while (++pos < json.length() && (json[pos] == ' ' || json[pos] == '\t' || json[pos] == '\n'));
    if (pos < json.length() && (json[pos] == '{' || json[pos] == '[')) return pos;
    return std::string::npos;
}

size_t jsonFindAnyObject(const std::string& json, const std::vector<std::string>& keys, size_t startPos = 0) {
    for (const auto& key : keys) {
        size_t pos = jsonFindObject(json, key, startPos);
        if (pos != std::string::npos) return pos;
    }
    return std::string::npos;
}

size_t jsonFindAnyKey(const std::string& json, const std::vector<std::string>& keys, size_t startPos = 0) {
    size_t best = std::string::npos;
    for (const auto& key : keys) {
        size_t pos = json.find("\"" + key + "\"", startPos);
        if (pos != std::string::npos && (best == std::string::npos || pos < best)) {
            best = pos;
        }
    }
    return best;
}

std::string jsonGetAnyString(const std::string& json, const std::vector<std::string>& keys, const std::string& defVal = "", size_t startPos = 0) {
    for (const auto& key : keys) {
        std::string value = jsonGetString(json, key, startPos);
        if (!value.empty()) return value;
    }
    return defVal;
}

float jsonGetAnyFloat(const std::string& json, const std::vector<std::string>& keys, float defVal = 0.0f, size_t startPos = 0) {
    std::string value = jsonGetAnyString(json, keys, "", startPos);
    return value.empty() ? defVal : std::stof(value);
}

int jsonGetAnyInt(const std::string& json, const std::vector<std::string>& keys, int defVal = 0, size_t startPos = 0) {
    std::string value = jsonGetAnyString(json, keys, "", startPos);
    return value.empty() ? defVal : std::stoi(value);
}

bool jsonGetAnyBool(const std::string& json, const std::vector<std::string>& keys, bool defVal = false, size_t startPos = 0) {
    std::string value = jsonGetAnyString(json, keys, "", startPos);
    if (value == "true") return true;
    if (value == "false") return false;
    return defVal;
}

// Find matching closing brace/bracket
size_t jsonFindClose(const std::string& json, size_t openPos) {
    if (openPos >= json.length()) return std::string::npos;
    char open = json[openPos];
    char close = (open == '{') ? '}' : (open == '[' ? ']' : '\0');
    if (close == '\0') return std::string::npos;

    int depth = 0;
    for (size_t i = openPos; i < json.length(); i++) {
        if (json[i] == open) depth++;
        else if (json[i] == close) {
            depth--;
            if (depth == 0) return i;
        }
    }
    return std::string::npos;
}

} // anonymous namespace

bool OmrProcessor::parseTemplateJson(
    const std::string& json,
    std::vector<AnchorPoint>& anchors,
    IdZoneConfig& idZone,
    std::vector<AnswerZoneConfig>& answerZones
) {
    // ─── Parse gridConfig ─────────────────────────────────────
    size_t gridPos = jsonFindAnyObject(json, {"gridConfig", "grid_config"});
    size_t gridEnd = (gridPos != std::string::npos) ? jsonFindClose(json, gridPos) : std::string::npos;

    // If no gridConfig wrapper, try parsing from root
    std::string dataJson = json;
    if (gridPos != std::string::npos && gridEnd != std::string::npos) {
        dataJson = json.substr(gridPos, gridEnd - gridPos + 1);
    }

    // ─── Parse anchor_points ──────────────────────────────────
    size_t apPos = jsonFindAnyObject(dataJson, {"anchor_points", "anchorPoints"});
    if (apPos != std::string::npos) {
        size_t apEnd = jsonFindClose(dataJson, apPos);
        size_t arrayStart = dataJson.find('[', apPos);

        if (arrayStart != std::string::npos && arrayStart < apEnd) {
            // Parse each anchor object
            size_t searchFrom = arrayStart + 1;
            while (searchFrom < apEnd) {
                size_t objStart = dataJson.find('{', searchFrom);
                if (objStart == std::string::npos || objStart >= apEnd) break;

                size_t objEnd = jsonFindClose(dataJson, objStart);
                if (objEnd == std::string::npos) break;

                std::string objStr = dataJson.substr(objStart, objEnd - objStart + 1);

                AnchorPoint ap;
                ap.id = jsonGetInt(objStr, "id", -1);
                ap.abs_x = jsonGetAnyFloat(objStr, {"abs_x", "absX"}, 0.0f);
                ap.abs_y = jsonGetAnyFloat(objStr, {"abs_y", "absY"}, 0.0f);
                ap.u = jsonGetFloat(objStr, "u", 0.0f);
                ap.v = jsonGetFloat(objStr, "v", 0.0f);

                if (ap.id > 0) {
                    anchors.push_back(ap);
                }

                searchFrom = objEnd + 1;
            }
        }
    }

    // Fallback to defaults if not enough anchors
    if (anchors.size() < 4) {
        LOGI("Using default anchor points (found %zu in JSON)", anchors.size());
        anchors = getDefaultAnchorPoints();
    }

    // ─── Parse id_zones ───────────────────────────────────────
    size_t izPos = jsonFindAnyObject(dataJson, {"id_zones", "idZones"});
    if (izPos != std::string::npos) {
        size_t izEnd = jsonFindClose(dataJson, izPos);
        std::string izStr = dataJson.substr(izPos, izEnd - izPos + 1);

        // bounding_box
        size_t bbPos = jsonFindAnyObject(izStr, {"bounding_box", "boundingBox"});
        if (bbPos != std::string::npos) {
            size_t bbEnd = jsonFindClose(izStr, bbPos);
            std::string bbStr = izStr.substr(bbPos, bbEnd - bbPos + 1);

            idZone.bounding_box.x = jsonGetFloat(bbStr, "x", 389);
            idZone.bounding_box.y = jsonGetFloat(bbStr, "y", 80);
            idZone.bounding_box.width  = jsonGetFloat(bbStr, "width", 418);
            idZone.bounding_box.height = jsonGetFloat(bbStr, "height", 288);
        }

        // items array
        size_t itemsPos = dataJson.find("\"items\"", izPos);
        if (itemsPos != std::string::npos && itemsPos < izEnd) {
            size_t arrStart = dataJson.find('[', itemsPos);
            size_t arrEnd = (arrStart != std::string::npos) ? jsonFindClose(dataJson, arrStart) : std::string::npos;

            if (arrStart != std::string::npos && arrEnd != std::string::npos) {
                size_t searchFrom = arrStart + 1;
                while (searchFrom < arrEnd) {
                    size_t objStart = dataJson.find('{', searchFrom);
                    if (objStart == std::string::npos || objStart >= arrEnd) break;
                    size_t objEnd = jsonFindClose(dataJson, objStart);
                    if (objEnd == std::string::npos) break;

                    std::string itemStr = dataJson.substr(objStart, objEnd - objStart + 1);

                    IdZoneItem item;
                    item.type = jsonGetString(itemStr, "type");
                    item.enabled = jsonGetBool(itemStr, "enabled", true);
                    item.num_digits = jsonGetAnyInt(itemStr, {"num_digits", "numDigits"}, 0);

                    if (!item.type.empty() && item.num_digits > 0) {
                        idZone.items.push_back(item);
                    }
                    searchFrom = objEnd + 1;
                }
            }
        }
    }

    // Empty/missing items mean this template has no readable ID fields.
    if (idZone.items.empty()) {
        LOGI("Template has no ID zone items; ID reading will be skipped");
    }

    // ─── Parse answer_zones ───────────────────────────────────
    size_t azArrayPos = jsonFindAnyKey(dataJson, {"answer_zones", "answerZones"});
    if (azArrayPos != std::string::npos) {
        size_t arrStart = dataJson.find('[', azArrayPos);
        if (arrStart != std::string::npos) {
            size_t arrEnd = jsonFindClose(dataJson, arrStart);

            size_t searchFrom = arrStart + 1;
            while (searchFrom < arrEnd) {
                size_t objStart = dataJson.find('{', searchFrom);
                if (objStart == std::string::npos || objStart >= arrEnd) break;
                size_t objEnd = jsonFindClose(dataJson, objStart);
                if (objEnd == std::string::npos) break;

                std::string zoneStr = dataJson.substr(objStart, objEnd - objStart + 1);

                AnswerZoneConfig az;
                az.zone_id = jsonGetAnyString(zoneStr, {"zone_id", "zoneId"});

                // bounding_box
                size_t bbPos2 = jsonFindAnyObject(zoneStr, {"bounding_box", "boundingBox"});
                if (bbPos2 != std::string::npos) {
                    size_t bbEnd2 = jsonFindClose(zoneStr, bbPos2);
                    std::string bbStr2 = zoneStr.substr(bbPos2, bbEnd2 - bbPos2 + 1);
                    az.bounding_box.x = jsonGetFloat(bbStr2, "x", 0);
                    az.bounding_box.y = jsonGetFloat(bbStr2, "y", 0);
                    az.bounding_box.width  = jsonGetFloat(bbStr2, "width", 0);
                    az.bounding_box.height = jsonGetFloat(bbStr2, "height", 0);
                }

                // layout sub-object
                size_t layoutPos = jsonFindObject(zoneStr, "layout");
                if (layoutPos != std::string::npos) {
                    size_t layoutEnd = jsonFindClose(zoneStr, layoutPos);
                    std::string layoutStr = zoneStr.substr(layoutPos, layoutEnd - layoutPos + 1);
                    az.rows = jsonGetInt(layoutStr, "rows", jsonGetAnyInt(zoneStr, {"rows"}, 20));
                    az.options = jsonGetInt(layoutStr, "options", 4);
                    az.has_start_number = jsonGetAnyBool(layoutStr, {"has_start_number", "hasStartNumber"}, true);
                    az.has_end_number = jsonGetAnyBool(layoutStr, {"has_end_number", "hasEndNumber"}, false);
                    az.start_question_index = jsonGetAnyInt(layoutStr, {"start_question_index", "startQuestionIndex"}, 1);
                    az.selection_mode = jsonGetAnyString(layoutStr, {"selectionMode", "selection_mode"});
                    if (az.selection_mode.empty()) az.selection_mode = "single";
                } else {
                    int startNumber = jsonGetAnyInt(zoneStr, {"start_number", "startNumber"}, 1);
                    int endNumber = jsonGetAnyInt(zoneStr, {"end_number", "endNumber"}, startNumber + 19);
                    az.rows = jsonGetAnyInt(
                        zoneStr,
                        {"rows"},
                        std::max(0, endNumber - startNumber + 1)
                    );
                    az.options = jsonGetAnyInt(zoneStr, {"options_per_question", "optionsPerQuestion"}, 4);
                    az.has_start_number = true;
                    az.has_end_number = false;
                    az.start_question_index = startNumber;
                    az.selection_mode = "single";
                }

                az.type = "multiple_choice_grid";

                if (az.zone_id.empty()) {
                    az.zone_id = "answer_zone_" + std::to_string(answerZones.size() + 1);
                }

                if (az.bounding_box.width > 0 && az.bounding_box.height > 0) {
                    answerZones.push_back(az);
                }
                searchFrom = objEnd + 1;
            }
        }
    }

    // Fallback for answer zones
    if (answerZones.empty()) {
        LOGI("Using default answer zones (2-zone layout)");
        AnswerZoneConfig az1;
        az1.zone_id = "answer_zone_1";
        az1.type = "multiple_choice_grid";
        az1.bounding_box = cv::Rect2f(90, 408, 279, 723);
        az1.rows = 20;
        az1.options = 4;
        az1.has_start_number = true;
        az1.has_end_number = false;
        az1.start_question_index = 1;
        az1.selection_mode = "single";
        answerZones.push_back(az1);

        AnswerZoneConfig az2;
        az2.zone_id = "answer_zone_2";
        az2.type = "multiple_choice_grid";
        az2.bounding_box = cv::Rect2f(457, 408, 279, 723);
        az2.rows = 20;
        az2.options = 4;
        az2.has_start_number = true;
        az2.has_end_number = false;
        az2.start_question_index = 21; // continues from zone 1
        az2.selection_mode = "single";
        answerZones.push_back(az2);
    }

    LOGI("Parsed template: %zu anchors, %zu id items, %zu answer zones",
         anchors.size(), idZone.items.size(), answerZones.size());

    return true;
}

// ─── Parse Answer Key JSON ─────────────────────────────────────────

bool OmrProcessor::parseAnswerKeyJson(
    const std::string& json,
    std::map<std::string, std::map<int, std::string>>& answerKeys
) {
    // The answer key JSON format:
    // { "data": { "answerKeys": { "examCode1": { "1": "A", "2": "B", ... }, ... } } }
    // or simply: { "123": { "1": "A", "2": "B", ... } }

    // Find answerKeys object
    size_t akPos = jsonFindObject(json, "answerKeys");
    std::string akJson;
    if (akPos != std::string::npos) {
        size_t akEnd = jsonFindClose(json, akPos);
        akJson = json.substr(akPos, akEnd - akPos + 1);
    } else {
        // Try direct format: root is { "examCode": {...}, ... }
        akJson = json;
    }

    // For each exam code entry
    size_t searchFrom = 1; // skip opening {
    while (searchFrom < akJson.length()) {
        // Find a key (exam code)
        size_t keyStart = akJson.find('"', searchFrom);
        if (keyStart == std::string::npos) break;
        size_t keyEnd = akJson.find('"', keyStart + 1);
        if (keyEnd == std::string::npos) break;

        std::string examCode = akJson.substr(keyStart + 1, keyEnd - keyStart - 1);

        // Skip non-exam-code keys (like nested wrapper keys)
        if (examCode == "data" || examCode == "answerKeys") {
            searchFrom = keyEnd + 1;
            continue;
        }

        // Find the value object
        size_t objStart = akJson.find('{', keyEnd);
        if (objStart == std::string::npos) break;
        size_t objEnd = jsonFindClose(akJson, objStart);
        if (objEnd == std::string::npos) break;

        std::string examObj = akJson.substr(objStart, objEnd - objStart + 1);

        // Parse question-answer pairs
        std::map<int, std::string> qaMap;
        size_t qSearchFrom = 1; // skip opening {
        while (qSearchFrom < examObj.length()) {
            size_t qKeyStart = examObj.find('"', qSearchFrom);
            if (qKeyStart == std::string::npos) break;
            size_t qKeyEnd = examObj.find('"', qKeyStart + 1);
            if (qKeyEnd == std::string::npos) break;

            std::string qNumStr = examObj.substr(qKeyStart + 1, qKeyEnd - qKeyStart - 1);
            int qNum;
            try {
                qNum = std::stoi(qNumStr);
            } catch (...) {
                qSearchFrom = qKeyEnd + 1;
                continue;
            }

            std::string answer = jsonGetString(examObj, qNumStr, qKeyStart);
            if (!answer.empty()) {
                qaMap[qNum] = answer;
            }

            qSearchFrom = qKeyEnd + 1;
        }

        if (!qaMap.empty()) {
            answerKeys[examCode] = qaMap;
            LOGI("Parsed answer key for exam code '%s': %zu questions",
                 examCode.c_str(), qaMap.size());
        }

        searchFrom = objEnd + 1;
    }

    return true;
}

// ─── Align Image ────────────────────────────────────────────────────

cv::Mat OmrProcessor::alignImage(
    const cv::Mat& image,
    const std::vector<DetectedMarker>& markers,
    const std::vector<AnchorPoint>& anchors,
    OmrResult& result
) {
    MarkerDetector md;
    DewarpInfo dewarpInfo = md.determineDewarpMethod(markers, anchors);

    LOGI("Dewarp decision: %s", dewarpInfo.reason.c_str());
    LOGI(
        "OMR_DEWARP selected method=%s avgErr=%.2f maxErr=%.2f markerCount=%zu anchorCount=%zu reason=%s",
        dewarpMethodName(dewarpInfo.method),
        dewarpInfo.avg_euclidean_error,
        dewarpInfo.max_euclidean_error,
        markers.size(),
        anchors.size(),
        dewarpInfo.reason.c_str()
    );
    if (dewarpInfo.max_euclidean_error >= DEWARP_MODERATE_PX) {
        result.warnings.push_back(
            "HOMOGRAPHY_HIGH_RESIDUAL:" + std::to_string(static_cast<int>(std::round(dewarpInfo.max_euclidean_error)))
        );
    }

    std::vector<cv::Point2f> usedCorners;
    cv::Mat warped;
    const char* executedAlgorithm = "NONE";

    switch (dewarpInfo.method) {
        case DewarpMethod::PERSPECTIVE: {
            LOGI("OMR_DEWARP executing piecewiseMesh method=%s", dewarpMethodName(dewarpInfo.method));
            executedAlgorithm = "HOMOGRAPHY";
            warped = md.dewarpPerspective(image, markers, anchors, usedCorners);
            break;
        }
        case DewarpMethod::HYBRID: {
            LOGI("OMR_DEWARP executing hybrid mesh first for moderate residual");
            executedAlgorithm = "MESH";
            warped = md.dewarpPiecewiseMesh(image, markers, anchors);
            if (warped.empty()) {
                LOGI("OMR_DEWARP hybrid mesh fallback: perspective section refinement");
                executedAlgorithm = "HOMOGRAPHY_SECTION_REFINE_FALLBACK";
                warped = md.dewarpPerspective(image, markers, anchors, usedCorners);
            } else {
                result.warnings.push_back("PIECEWISE_MESH_DEWARP");
            }
            break;
        }
        case DewarpMethod::TPS: {
            LOGI(
                "OMR_DEWARP executing severe full TPS smoothness=%.2f",
                TPS_STABLE_SMOOTHNESS
            );
            executedAlgorithm = "TPS";
            warped = md.dewarpTps(image, markers, anchors, TPS_STABLE_SMOOTHNESS);
            if (warped.empty()) {
                LOGI("OMR_DEWARP TPS fallback: piecewise mesh");
                executedAlgorithm = "MESH_FALLBACK";
                warped = md.dewarpPiecewiseMesh(image, markers, anchors);
            } else {
                result.warnings.push_back("TPS_DEWARP");
            }
            if (warped.empty()) {
                LOGI("OMR_DEWARP mesh fallback: perspective");
                executedAlgorithm = "HOMOGRAPHY_FALLBACK";
                warped = md.dewarpPerspective(image, markers, anchors, usedCorners);
            }
            break;
        }
        default:
            LOGI("OMR_DEWARP selected NONE: no warp executed");
            break;
    }

    LOGI(
        "OMR_DEWARP_METRIC euclideanCase=%s selectedMethod=%s executedAlgorithm=%s avgEuclidPx=%.2f maxEuclidPx=%.2f markerCount=%zu outputEmpty=%d",
        euclideanCaseName(dewarpInfo.max_euclidean_error),
        dewarpMethodName(dewarpInfo.method),
        executedAlgorithm,
        dewarpInfo.avg_euclidean_error,
        dewarpInfo.max_euclidean_error,
        markers.size(),
        warped.empty()
    );
    LOGI("OMR_DEWARP completed outputEmpty=%d output=%dx%d", warped.empty(), warped.cols, warped.rows);
    return warped;
}

// ─── Read ID Zone ───────────────────────────────────────────────────

std::vector<BubbleCell> OmrProcessor::autoFitIdCells(
    const cv::Mat& warpedGray,
    const std::vector<BubbleCell>& cells
) {
    if (warpedGray.empty() || cells.empty()) {
        return cells;
    }

    std::map<int, std::vector<float>> colDx;
    std::map<int, std::vector<float>> colDy;
    std::map<int, std::vector<float>> rowDy;
    std::vector<float> allDx;
    std::vector<float> allDy;

    for (const auto& cell : cells) {
        cv::Point2f refined = refineBubbleCenter(warpedGray, cell.cx, cell.cy, cell.radius);
        float dx = refined.x - cell.cx;
        float dy = refined.y - cell.cy;
        float dist = std::sqrt(dx * dx + dy * dy);
        if (dist < 0.5f || dist > cell.radius * 0.85f) {
            continue;
        }
        colDx[cell.col].push_back(dx);
        colDy[cell.col].push_back(dy);
        rowDy[cell.row].push_back(dy);
        allDx.push_back(dx);
        allDy.push_back(dy);
    }

    if (allDx.size() < 8) {
        LOGI("ID auto-fit skipped: usableCorrections=%zu", allDx.size());
        return cells;
    }

    float globalDx = medianValue(allDx);
    float globalDy = medianValue(allDy);
    std::vector<BubbleCell> fitted = cells;

    float maxShift = 0.0f;
    for (auto& cell : fitted) {
        bool hasCol = colDx[cell.col].size() >= 3 && colDy[cell.col].size() >= 3;
        bool hasRow = rowDy[cell.row].size() >= 3;
        float dx = hasCol ? medianValue(colDx[cell.col]) : globalDx;
        float dy = hasRow ? medianValue(rowDy[cell.row]) : (hasCol ? medianValue(colDy[cell.col]) : globalDy);
        dx = std::max(-cell.radius * 0.85f, std::min(cell.radius * 0.85f, dx));
        dy = std::max(-cell.radius * 0.85f, std::min(cell.radius * 0.85f, dy));
        cell.cx += dx;
        cell.cy += dy;
        maxShift = std::max(maxShift, std::sqrt(dx * dx + dy * dy));
    }

    LOGI("ID auto-fit applied usableCorrections=%zu global=(%.2f,%.2f) maxShift=%.2f",
         allDx.size(), globalDx, globalDy, maxShift);
    return fitted;
}

IdReadResult OmrProcessor::readIdZone(
    const cv::Mat& warpedGray,
    const IdZoneConfig& idZone
) {
    IdReadResult result;
    result.id_ok = false;

    if (idZone.items.empty()) {
        result.id_ok = true;
        return result;
    }

    // Compute layout
    std::vector<BubbleCell> cells;
    if (!LayoutCalculator::computeIdZoneLayout(idZone, cells)) {
        result.id_error = "Failed to compute ID zone layout";
        return result;
    }

    if (cells.empty()) {
        result.id_error = "No ID cells generated";
        return result;
    }

    cells = autoFitIdCells(warpedGray, cells);

    // Group cells by column index
    // col 0..(totalDigits-1), each has 10 rows
    std::map<int, std::vector<BubbleCell>> colGroups;
    for (const auto& cell : cells) {
        colGroups[cell.col].push_back(cell);
    }

    // Read each column
    int totalCols = static_cast<int>(colGroups.size());
    std::vector<int> digitResults(totalCols, -1);

    for (int col = 0; col < totalCols; col++) {
        auto colCells = colGroups[col];
        std::sort(colCells.begin(), colCells.end(),
            [](const BubbleCell& a, const BubbleCell& b) { return a.row < b.row; });

        float top1Density = 0.0f;
        float top2Density = 0.0f;
        int topRow = -1;
        for (const auto& cell : colCells) {
            if (cell.row < 0 || cell.row >= 10) continue;
            float density = readBubbleDensity(warpedGray, cell.cx, cell.cy, cell.radius);
            if (density > top1Density) {
                top2Density = top1Density;
                top1Density = density;
                topRow = cell.row;
            } else if (density > top2Density) {
                top2Density = density;
            }
        }

        digitResults[col] = top1Density >= options_.density_threshold ? topRow : -1;
    }

    // Map digits to items
    int colIdx = 0;
    for (const auto& item : idZone.items) {
        if (!item.enabled) continue;

        std::string value;
        bool allRead = true;

        for (int d = 0; d < item.num_digits; d++) {
            int digit = digitResults[colIdx];
            if (digit >= 0 && digit <= 9) {
                value += static_cast<char>('0' + digit);
            } else {
                value += '?';
                allRead = false;
            }
            colIdx++;
        }

        if (item.type == "student_id") {
            result.student_id = value;
        } else if (item.type == "class_code") {
            result.class_code = value;
        } else if (item.type == "exam_code") {
            result.exam_code = value;
        }
    }

    // Check if ID is valid (has at least some digits)
    if ((!result.student_id.empty() && result.student_id.find_first_not_of("?") != std::string::npos) ||
        (!result.class_code.empty() && result.class_code.find_first_not_of("?") != std::string::npos) ||
        (!result.exam_code.empty() && result.exam_code.find_first_not_of("?") != std::string::npos)) {
        result.id_ok = true;
    }

    LOGI("ID Read: student_id=%s, class_code=%s, exam_code=%s",
         result.student_id.c_str(), result.class_code.c_str(), result.exam_code.c_str());

    return result;
}

// ─── Read ID Zone (cached cells) ──────────────────────────────────

IdReadResult OmrProcessor::readIdZoneCached(
    const cv::Mat& warpedGray,
    const IdZoneConfig& idZone,
    const std::vector<BubbleCell>& cells
) {
    IdReadResult result;
    result.id_ok = false;

    if (idZone.items.empty()) {
        result.id_ok = true;
        return result;
    }

    if (cells.empty()) {
        // Fallback to full computation
        return readIdZone(warpedGray, idZone);
    }

    std::vector<BubbleCell> fittedCells = autoFitIdCells(warpedGray, cells);

    // Group cells by column index (same logic as readIdZone)
    std::map<int, std::vector<BubbleCell>> colGroups;
    for (const auto& cell : fittedCells) {
        colGroups[cell.col].push_back(cell);
    }

    int totalCols = static_cast<int>(colGroups.size());
    std::vector<int> digitResults(totalCols, -1);

    for (int col = 0; col < totalCols; col++) {
        auto colCells = colGroups[col];
        std::sort(colCells.begin(), colCells.end(),
            [](const BubbleCell& a, const BubbleCell& b) { return a.row < b.row; });

        float top1Density = 0.0f;
        float top2Density = 0.0f;
        int topRow = -1;
        for (const auto& cell : colCells) {
            if (cell.row < 0 || cell.row >= 10) continue;
            float density = readBubbleDensity(warpedGray, cell.cx, cell.cy, cell.radius);
            if (density > top1Density) {
                top2Density = top1Density;
                top1Density = density;
                topRow = cell.row;
            } else if (density > top2Density) {
                top2Density = density;
            }
        }
        digitResults[col] = top1Density >= options_.density_threshold ? topRow : -1;
    }

    int colIdx = 0;
    for (const auto& item : idZone.items) {
        if (!item.enabled) continue;
        std::string value;
        for (int d = 0; d < item.num_digits; d++) {
            int digit = digitResults[colIdx];
            value += (digit >= 0 && digit <= 9) ? static_cast<char>('0' + digit) : '?';
            colIdx++;
        }
        if (item.type == "student_id") result.student_id = value;
        else if (item.type == "class_code") result.class_code = value;
        else if (item.type == "exam_code") result.exam_code = value;
    }

    if ((!result.student_id.empty() && result.student_id.find_first_not_of("?") != std::string::npos) ||
        (!result.class_code.empty() && result.class_code.find_first_not_of("?") != std::string::npos) ||
        (!result.exam_code.empty() && result.exam_code.find_first_not_of("?") != std::string::npos)) {
        result.id_ok = true;
    }

    return result;
}

// ─── Read Answer Zones ──────────────────────────────────────────────

std::vector<AnswerReadResult> OmrProcessor::readAnswerZones(
    const cv::Mat& warpedGray,
    const std::vector<AnswerZoneConfig>& answerZones
) {
    std::vector<AnswerReadResult> allResults;

    for (const auto& zone : answerZones) {
        // Compute layout
        std::vector<BubbleCell> cells;
        if (!LayoutCalculator::computeAnswerZoneLayout(zone, cells)) {
            LOGE("Failed to compute layout for zone %s", zone.zone_id.c_str());
            continue;
        }

        // Group by question index
        std::map<int, std::vector<BubbleCell>> questionGroups;
        for (const auto& cell : cells) {
            if (!cell.is_number_cell) {
                questionGroups[cell.question_index].push_back(cell);
            }
        }

        // Read each question
        for (auto& [qIdx, qCells] : questionGroups) {
            // Sort by option index
            std::sort(qCells.begin(), qCells.end(),
                [](const BubbleCell& a, const BubbleCell& b) { return a.col < b.col; });

            // Read density for each option
            std::vector<float> densities;
            densities.reserve(qCells.size());
            for (const auto& cell : qCells) {
                cv::Point2f center = refineBubbleCenter(warpedGray, cell.cx, cell.cy, cell.radius);
                float d = readBubbleDensity(warpedGray, center.x, center.y, cell.radius);
                densities.push_back(d);
            }

            allResults.push_back(buildAnswerResult(
                qIdx,
                densities,
                zone.selection_mode,
                options_.density_threshold,
                options_.diff_threshold
            ));
        }
    }

    // Sort by question number
    std::sort(allResults.begin(), allResults.end(),
        [](const AnswerReadResult& a, const AnswerReadResult& b) {
            return a.question_number < b.question_number;
        });

    LOGI("Read %zu answers from %zu zones", allResults.size(), answerZones.size());
    return allResults;
}

// ─── Read Answer Zones (cached cells) ─────────────────────────────

std::vector<AnswerReadResult> OmrProcessor::readAnswerZonesLocalDewarp(
    const cv::Mat& image,
    const std::vector<DetectedMarker>& markers,
    const std::vector<AnchorPoint>& anchors,
    const std::vector<AnswerZoneConfig>& answerZones,
    std::map<std::string, cv::Mat>* debugLocalGrays
) {
    std::vector<AnswerReadResult> allResults;
    if (image.empty() || markers.size() < 4 || answerZones.empty()) {
        return allResults;
    }

    MarkerDetector markerDetector;
    cv::Mat savedBinary = warpedBinary_;

    for (const auto& zone : answerZones) {
        std::vector<int> usedMarkerIds;
        cv::Mat localWarped = markerDetector.dewarpLocalRegion(
            image,
            markers,
            anchors,
            zone.bounding_box,
            usedMarkerIds
        );

        if (localWarped.empty()) {
            LOGI("LOCAL_ZONE_READ skipped zone=%s: local warp failed",
                 zone.zone_id.c_str());
            continue;
        }

        cv::Mat localGray;
        if (localWarped.channels() >= 3) {
            cv::cvtColor(localWarped, localGray, cv::COLOR_BGR2GRAY);
        } else {
            localGray = localWarped;
        }

        if (options_.preprocess_post_warp) {
            localGray = preprocessPostWarp(localGray);
        }

        if (debugLocalGrays != nullptr) {
            (*debugLocalGrays)[zone.zone_id] = localGray.clone();
        }

        warpedBinary_.release();
        if (options_.use_adaptive_threshold) {
            int bs = options_.adaptive_block_size | 1;
            bs = std::max(3, std::min(255, bs));

            cv::adaptiveThreshold(localGray, warpedBinary_, 255,
                cv::ADAPTIVE_THRESH_GAUSSIAN_C,
                cv::THRESH_BINARY,
                bs,
                options_.adaptive_c);

            if (options_.morph_cleanup) {
                applyMorphCleanup(warpedBinary_);
            }
        }

        std::vector<AnswerZoneConfig> singleZone = {zone};
        std::vector<AnswerReadResult> zoneAnswers = readAnswerZones(localGray, singleZone);
        allResults.insert(allResults.end(), zoneAnswers.begin(), zoneAnswers.end());

        LOGI("LOCAL_ZONE_READ completed zone=%s markers=%zu answers=%zu",
             zone.zone_id.c_str(), usedMarkerIds.size(), zoneAnswers.size());
    }

    warpedBinary_ = savedBinary;

    std::sort(allResults.begin(), allResults.end(),
        [](const AnswerReadResult& a, const AnswerReadResult& b) {
            return a.question_number < b.question_number;
        });

    return allResults;
}

std::vector<AnswerReadResult> OmrProcessor::readAnswerZonesCached(
    const cv::Mat& warpedGray,
    const std::vector<AnswerZoneConfig>& answerZones,
    const std::map<std::string, std::vector<BubbleCell>>& cachedCells
) {
    std::vector<AnswerReadResult> allResults;

    for (const auto& zone : answerZones) {
        auto cellIt = cachedCells.find(zone.zone_id);
        if (cellIt == cachedCells.end()) continue;

        const auto& cells = cellIt->second;

        // Group by question index
        std::map<int, std::vector<BubbleCell>> questionGroups;
        for (const auto& cell : cells) {
            if (!cell.is_number_cell) {
                questionGroups[cell.question_index].push_back(cell);
            }
        }

        // Read each question (same logic as readAnswerZones)
        for (auto& [qIdx, qCells] : questionGroups) {
            std::sort(qCells.begin(), qCells.end(),
                [](const BubbleCell& a, const BubbleCell& b) { return a.col < b.col; });

            std::vector<float> densities;
            densities.reserve(qCells.size());
            for (const auto& cell : qCells) {
                cv::Point2f center = refineBubbleCenter(warpedGray, cell.cx, cell.cy, cell.radius);
                densities.push_back(readBubbleDensity(warpedGray, center.x, center.y, cell.radius));
            }

            allResults.push_back(buildAnswerResult(
                qIdx,
                densities,
                zone.selection_mode,
                options_.density_threshold,
                options_.diff_threshold
            ));
        }
    }

    std::sort(allResults.begin(), allResults.end(),
        [](const AnswerReadResult& a, const AnswerReadResult& b) {
            return a.question_number < b.question_number;
        });

    LOGI("Read %zu answers (cached) from %zu zones", allResults.size(), answerZones.size());
    return allResults;
}

// ─── Bubble Density Reading ────────────────────────────────────────

cv::Point2f OmrProcessor::refineBubbleCenter(
    const cv::Mat& grayImg,
    float cx, float cy, float radius
) {
    if (grayImg.empty()) {
        return {cx, cy};
    }

    int search = static_cast<int>(std::round(std::max(10.0f, radius * 1.25f)));
    int x = static_cast<int>(std::round(cx));
    int y = static_cast<int>(std::round(cy));

    int x0 = std::max(0, x - search);
    int y0 = std::max(0, y - search);
    int x1 = std::min(grayImg.cols - 1, x + search);
    int y1 = std::min(grayImg.rows - 1, y + search);
    if (x1 <= x0 || y1 <= y0) {
        return {cx, cy};
    }

    cv::Rect roi(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    cv::Mat patch = grayImg(roi);
    cv::Mat blurred;
    cv::GaussianBlur(patch, blurred, cv::Size(3, 3), 0.0);

    cv::Mat binary;
    cv::threshold(blurred, binary, 0, 255, cv::THRESH_BINARY_INV | cv::THRESH_OTSU);
    cv::morphologyEx(binary, binary, cv::MORPH_CLOSE, cv::Mat(), cv::Point(-1, -1), 1);

    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(binary, contours, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_SIMPLE);

    cv::Point2f expectedLocal(static_cast<float>(x - x0), static_cast<float>(y - y0));
    cv::Point2f bestLocal = expectedLocal;
    float bestScore = std::numeric_limits<float>::max();

    for (const auto& contour : contours) {
        double area = cv::contourArea(contour);
        double expectedArea = CV_PI * radius * radius;
        if (area < 6.0 || area > expectedArea * 1.8) {
            continue;
        }

        cv::Point2f center;
        float enclosingRadius = 0.0f;
        cv::minEnclosingCircle(contour, center, enclosingRadius);
        if (enclosingRadius < radius * 0.35f || enclosingRadius > radius * 1.45f) {
            continue;
        }

        double perimeter = cv::arcLength(contour, true);
        if (perimeter <= 0.0) {
            continue;
        }
        double circularity = 4.0 * CV_PI * area / (perimeter * perimeter);
        if (circularity < 0.45) {
            continue;
        }

        float dx = center.x - expectedLocal.x;
        float dy = center.y - expectedLocal.y;
        float dist = std::sqrt(dx * dx + dy * dy);
        if (dist > radius * 0.85f) {
            continue;
        }

        float radiusPenalty = std::abs(enclosingRadius - radius) * 0.65f;
        float score = dist + radiusPenalty - static_cast<float>(area) * 0.01f;
        if (score < bestScore) {
            bestScore = score;
            bestLocal = center;
        }
    }

    if (bestScore == std::numeric_limits<float>::max()) {
        return {cx, cy};
    }

    return {bestLocal.x + static_cast<float>(x0), bestLocal.y + static_cast<float>(y0)};
}

float OmrProcessor::readBubbleDensity(
    const cv::Mat& grayImg,
    float cx, float cy, float radius
) {
    // Prefer binary mat from adaptive threshold when available.
    // Both modes count dark pixels (value < 128):
    //   - Grayscale:  0=black … 255=white, dark < 128
    //   - Binary:     0=bubble (dark), 255=background, dark < 128
    const cv::Mat& src = warpedBinary_.empty() ? grayImg : warpedBinary_;

    // Ensure coordinates are within bounds
    int x = static_cast<int>(std::round(cx));
    int y = static_cast<int>(std::round(cy));
    int r = static_cast<int>(std::round(radius * BUBBLE_READ_CORE_RATIO));
    r = std::max(BUBBLE_READ_MIN_CORE_RADIUS, r);

    if (x < 0 || y < 0 || x >= src.cols || y >= src.rows) {
        return 0.0f;
    }

    // Use circular mask via ROI
    int x0 = std::max(0, x - r);
    int y0 = std::max(0, y - r);
    int x1 = std::min(src.cols - 1, x + r);
    int y1 = std::min(src.rows - 1, y + r);

    if (x1 <= x0 || y1 <= y0) return 0.0f;

    cv::Rect roi(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    cv::Mat patch = src(roi);

    float totalPixels = 0.0f;
    float darkPixels = 0.0f;
    float r2 = static_cast<float>(r * r);

    for (int py = 0; py < patch.rows; py++) {
        for (int px = 0; px < patch.cols; px++) {
            float dx = static_cast<float>(px + x0 - x);
            float dy = static_cast<float>(py + y0 - y);
            if (dx * dx + dy * dy <= r2) {
                totalPixels += 1.0f;
                // Dark pixel: value below 128
                // Works for both grayscale and binary (THRESH_BINARY: bubble=0)
                if (patch.at<uchar>(py, px) < 128) {
                    darkPixels += 1.0f;
                }
            }
        }
    }

    if (totalPixels < 1.0f) return 0.0f;
    return darkPixels / totalPixels;
}

int OmrProcessor::readBubbleColumn(
    const cv::Mat& grayImg,
    const std::vector<float>& cyList,
    float cx, float radius,
    float& top1Density, float& top2Density
) {
    top1Density = 0.0f;
    top2Density = 0.0f;
    int topRow = -1;

    for (int r = 0; r < static_cast<int>(cyList.size()); r++) {
        float density = readBubbleDensity(grayImg, cx, cyList[r], radius);

        if (density > top1Density) {
            top2Density = top1Density;
            top1Density = density;
            topRow = r;
        } else if (density > top2Density) {
            top2Density = density;
        }
    }

    if (top1Density >= options_.density_threshold) {
        return topRow;  // Returns 0-9
    }

    return -1;  // No clear reading
}

// ─── Scoring ────────────────────────────────────────────────────────

ScoreResult OmrProcessor::scoreAnswers(
    const std::vector<AnswerReadResult>& answers,
    const std::map<int, std::string>& correctAnswers,
    const std::string& selectionMode
) {
    ScoreResult score{};
    score.total_questions = static_cast<int>(correctAnswers.size());
    score.correct_count = 0;
    score.total_score = 0.0;

    for (const auto& ar : answers) {
        auto it = correctAnswers.find(ar.question_number);
        if (it == correctAnswers.end()) continue;

        ScoringDetail detail;
        detail.question_number = ar.question_number;
        detail.student_answer = ar.answer;
        detail.correct_answer = it->second;
        detail.flag = ar.flag;

        detail.is_correct = checkAnswerMatch(ar.answer, it->second, selectionMode);

        if (detail.is_correct) {
            score.correct_count++;
            score.total_score += 1.0;  // Default: 1 point per question
        }

        score.details.push_back(detail);
    }

    score.total_questions = static_cast<int>(score.details.size());

    LOGI("Scoring: %d/%d correct (%.1f)", score.correct_count, score.total_questions, score.total_score);
    return score;
}

bool OmrProcessor::checkAnswerMatch(
    const std::string& studentAnswer,
    const std::string& correctAnswer,
    const std::string& selectionMode
) {
    std::string sa = normalizedAnswerSet(studentAnswer);
    std::string ca = normalizedAnswerSet(correctAnswer);
    if (sa.empty() || ca.empty()) return false;

    if (selectionMode != "multiple" && countAnswerOptions(ca) == 1 && countAnswerOptions(sa) > 1) {
        return false;
    }

    return sa == ca;
}

// ─── Generate Debug Image ──────────────────────────────────────────

cv::Mat OmrProcessor::generateDebugImage(
    const cv::Mat& warpedGray,
    const IdZoneConfig& idZone,
    const std::vector<AnswerZoneConfig>& answerZones,
    const IdReadResult& idResult,
    const std::vector<AnswerReadResult>& answers,
    const ScoreResult* score,
    const std::map<int, std::string>* correctAnswers,
    const cv::Mat* idAlignedDebugGray,
    const std::map<std::string, cv::Mat>* answerAlignedDebugGrays
) {
    // Always produce a BGR image for color drawing
    cv::Mat debug;
    cv::cvtColor(warpedGray, debug, cv::COLOR_GRAY2BGR);

    // ── Colors ────────────────────────────────────────────────
    const cv::Scalar GREEN(0, 255, 0);       // Correct selection
    const cv::Scalar RED(0, 0, 255);         // Wrong selection
    const cv::Scalar YELLOW(0, 255, 255);    // Missed correct / ambiguous
    const cv::Scalar BLUE(255, 0, 0);        // Selected (no scoring)
    const cv::Scalar GRID_GRAY(120, 120, 120); // All computed answer bubbles

    if (idAlignedDebugGray != nullptr &&
        !idAlignedDebugGray->empty() &&
        idAlignedDebugGray->size() == warpedGray.size()) {
        cv::Mat idAlignedBgr;
        if (idAlignedDebugGray->channels() >= 3) {
            idAlignedBgr = *idAlignedDebugGray;
        } else {
            cv::cvtColor(*idAlignedDebugGray, idAlignedBgr, cv::COLOR_GRAY2BGR);
        }

        const int pad = 24;
        cv::Rect imageRect(0, 0, debug.cols, debug.rows);
        cv::Rect idRoi(
            static_cast<int>(std::floor(idZone.bounding_box.x)) - pad,
            static_cast<int>(std::floor(idZone.bounding_box.y)) - pad,
            static_cast<int>(std::ceil(idZone.bounding_box.width)) + pad * 2,
            static_cast<int>(std::ceil(idZone.bounding_box.height)) + pad * 2
        );
        idRoi &= imageRect;
        if (idRoi.width > 0 && idRoi.height > 0) {
            idAlignedBgr(idRoi).copyTo(debug(idRoi));
        }
    }

    // ── Build lookup maps ─────────────────────────────────────
    if (answerAlignedDebugGrays != nullptr) {
        const int pad = 16;
        cv::Rect imageRect(0, 0, debug.cols, debug.rows);
        for (const auto& zone : answerZones) {
            auto localIt = answerAlignedDebugGrays->find(zone.zone_id);
            if (localIt == answerAlignedDebugGrays->end() ||
                localIt->second.empty() ||
                localIt->second.size() != warpedGray.size()) {
                continue;
            }

            cv::Mat answerAlignedBgr;
            if (localIt->second.channels() >= 3) {
                answerAlignedBgr = localIt->second;
            } else {
                cv::cvtColor(localIt->second, answerAlignedBgr, cv::COLOR_GRAY2BGR);
            }

            cv::Rect zoneRoi(
                static_cast<int>(std::floor(zone.bounding_box.x)) - pad,
                static_cast<int>(std::floor(zone.bounding_box.y)) - pad,
                static_cast<int>(std::ceil(zone.bounding_box.width)) + pad * 2,
                static_cast<int>(std::ceil(zone.bounding_box.height)) + pad * 2
            );
            zoneRoi &= imageRect;
            if (zoneRoi.width > 0 && zoneRoi.height > 0) {
                answerAlignedBgr(zoneRoi).copyTo(debug(zoneRoi));
            }
        }
    }

    std::map<int, AnswerReadResult> answerMap;
    for (const auto& ar : answers) {
        answerMap[ar.question_number] = ar;
    }

    bool hasScore = (score != nullptr && correctAnswers != nullptr);

    // ── Draw all answer zone cells ────────────────────────────
    for (const auto& zone : answerZones) {
        const cv::Mat* answerRefineGray = &warpedGray;
        if (answerAlignedDebugGrays != nullptr) {
            auto localIt = answerAlignedDebugGrays->find(zone.zone_id);
            if (localIt != answerAlignedDebugGrays->end() &&
                !localIt->second.empty() &&
                localIt->second.size() == warpedGray.size()) {
                answerRefineGray = &localIt->second;
            }
        }

        std::vector<BubbleCell> cells;
        if (!LayoutCalculator::computeAnswerZoneLayout(zone, cells)) continue;

        for (const auto& cell : cells) {
            if (cell.is_number_cell) continue;

            int qIdx = cell.question_index;
            cv::Point2f refinedCenter = refineBubbleCenter(*answerRefineGray, cell.cx, cell.cy, cell.radius);
            drawBubbleCell(debug, refinedCenter.x, refinedCenter.y, cell.radius, GRID_GRAY, 1);

            // Determine what student selected for this question
            auto arIt = answerMap.find(qIdx);
            std::string studentAns = (arIt != answerMap.end()) ? arIt->second.answer : "";
            int studentFlag = (arIt != answerMap.end()) ? arIt->second.flag : 0;

            // Check if this specific option is in student's answer
            bool studentChoseThis = false;
            for (char c : studentAns) {
                if (c == cell.choice_letter) { studentChoseThis = true; break; }
            }

            // Determine correct answer for this question
            std::string correctAns;
            if (correctAnswers != nullptr) {
                auto caIt = correctAnswers->find(qIdx);
                if (caIt != correctAnswers->end()) {
                    correctAns = caIt->second;
                }
            }

            bool isCorrectOption = false;
            for (char c : correctAns) {
                if (c == cell.choice_letter) { isCorrectOption = true; break; }
            }

            cv::Scalar color;
            int thickness = 1;

            if (hasScore) {
                // ── Scored mode ────────────────────────────────
                if (studentChoseThis && isCorrectOption) {
                    // Student picked the right answer
                    color = GREEN;
                    thickness = 2;
                } else if (studentChoseThis && !isCorrectOption) {
                    // Student picked the wrong answer
                    color = RED;
                    thickness = 2;
                } else if (!studentChoseThis && isCorrectOption) {
                    // Correct answer that student MISSED
                    color = YELLOW;
                    thickness = 2;
                } else if (studentChoseThis && studentFlag == 1) {
                    // Student picked this with ambiguity flag (even if it might be
                    // correct, the flag means erasure suspected → yellow overlay)
                    // This case is caught above; if it's both correct and flagged,
                    // we still use GREEN for correct. The flag-specific handling
                    // is for the whole question, so we draw YELLOW on the correct
                    // answer position when flag=1 and student answer exists.
                    color = YELLOW;
                    thickness = 2;
                } else {
                    continue;
                }
            } else {
                // ── No-scoring mode: only show filled bubbles ──
                if (studentChoseThis) {
                    color = BLUE;
                    thickness = 2;
                } else {
                    continue;
                }
            }

            drawBubbleCell(debug, refinedCenter.x, refinedCenter.y, cell.radius, color, thickness);
        }

        // ── Extra: per-question YELLOW overlay on flagged questions ──
        // For scored mode, if a question has flag=1, draw YELLOW on the
        // correct answer position regardless of fill state
        if (hasScore && correctAnswers != nullptr) {
            for (const auto& cell : cells) {
                if (cell.is_number_cell) continue;
                int qIdx = cell.question_index;

                auto arIt = answerMap.find(qIdx);
                if (arIt == answerMap.end()) continue;
                if (arIt->second.flag != 1) continue;

                // Check if this cell is the correct answer
                auto caIt = correctAnswers->find(qIdx);
                if (caIt == correctAnswers->end()) continue;

                bool isCorrectOpt = false;
                for (char c : caIt->second) {
                    if (c == cell.choice_letter) { isCorrectOpt = true; break; }
                }

                if (isCorrectOpt) {
                    // Yellow circle on the correct answer of a flagged question
                    cv::Point2f refinedCenter = refineBubbleCenter(*answerRefineGray, cell.cx, cell.cy, cell.radius);
                    drawBubbleCell(debug, refinedCenter.x, refinedCenter.y, cell.radius * 1.15f,
                                   YELLOW, 2);
                }
            }
        }
    }

    // ── Draw ID zone cells ────────────────────────────────────
    // ── Overlay text info ─────────────────────────────────────
    std::vector<int> selectedIdDigits;
    auto appendIdValue = [&](const std::string& value, int digits) {
        for (int i = 0; i < digits; i++) {
            if (i < static_cast<int>(value.size()) &&
                value[i] >= '0' && value[i] <= '9') {
                selectedIdDigits.push_back(value[i] - '0');
            } else {
                selectedIdDigits.push_back(-1);
            }
        }
    };

    for (const auto& item : idZone.items) {
        if (!item.enabled) continue;
        if (item.type == "student_id") {
            appendIdValue(idResult.student_id, item.num_digits);
        } else if (item.type == "class_code") {
            appendIdValue(idResult.class_code, item.num_digits);
        } else if (item.type == "exam_code") {
            appendIdValue(idResult.exam_code, item.num_digits);
        }
    }

    std::vector<BubbleCell> idCells;
    const cv::Mat& idRefineGray =
        (idAlignedDebugGray != nullptr &&
         !idAlignedDebugGray->empty() &&
         idAlignedDebugGray->size() == warpedGray.size())
            ? *idAlignedDebugGray
            : warpedGray;
    if (!selectedIdDigits.empty() &&
        LayoutCalculator::computeIdZoneLayout(idZone, idCells)) {
        idCells = autoFitIdCells(idRefineGray, idCells);
        for (const auto& cell : idCells) {
            drawBubbleCell(debug, cell.cx, cell.cy, cell.radius, GRID_GRAY, 1);

            if (cell.col >= 0 &&
                cell.col < static_cast<int>(selectedIdDigits.size()) &&
                selectedIdDigits[cell.col] == cell.row) {
                drawBubbleCell(debug, cell.cx, cell.cy, cell.radius, GREEN, 2);
            }
        }
    }

    int fontFace = cv::FONT_HERSHEY_SIMPLEX;
    double fontScale = 0.6;
    int textThick = 1;
    int yOff = 30;

    if (!idResult.student_id.empty()) {
        cv::putText(debug, "SBD: " + idResult.student_id,
                    cv::Point(10, yOff), fontFace, fontScale, GREEN, textThick);
        yOff += 25;
    }
    if (!idResult.class_code.empty()) {
        cv::putText(debug, "Class: " + idResult.class_code,
                    cv::Point(10, yOff), fontFace, fontScale, GREEN, textThick);
        yOff += 25;
    }
    if (!idResult.exam_code.empty()) {
        cv::putText(debug, "Exam: " + idResult.exam_code,
                    cv::Point(10, yOff), fontFace, fontScale, GREEN, textThick);
        yOff += 25;
    }
    if (hasScore) {
        cv::putText(debug,
                    "Score: " + std::to_string(score->correct_count) +
                    "/" + std::to_string(score->total_questions),
                    cv::Point(10, yOff), fontFace, fontScale, GREEN, textThick);
        yOff += 25;
    }

    // Legend
    int legendY = debug.rows - 25;
    if (hasScore) {
        cv::putText(debug, "GREEN=Correct  RED=Wrong  YELLOW=Missed/Flagged",
                    cv::Point(10, legendY), fontFace, 0.45, GREEN, 1);
    } else {
        cv::putText(debug, "GRAY=Answer grid  BLUE=Selected",
                    cv::Point(10, legendY), fontFace, 0.45, BLUE, 1);
    }

    // Resize if exceeds max debug dimension (keeps aspect ratio)
    int maxDim = std::max(debug.cols, debug.rows);
    if (maxDim > MAX_DEBUG_DIMENSION) {
        double scale = static_cast<double>(MAX_DEBUG_DIMENSION) / maxDim;
        cv::resize(debug, debug, cv::Size(), scale, scale, cv::INTER_AREA);
    }

    return debug;
}

void OmrProcessor::drawBubbleCell(
    cv::Mat& img,
    float cx, float cy, float radius,
    const cv::Scalar& color,
    int thickness
) {
    cv::Point center(static_cast<int>(std::round(cx)), static_cast<int>(std::round(cy)));
    int r = static_cast<int>(std::round(radius));
    cv::circle(img, center, r, color, thickness);
}

// ─── Result to JSON ────────────────────────────────────────────────

std::string OmrProcessor::escapeJson(const std::string& s) {
    std::string result;
    result.reserve(s.length() * 2);
    for (char c : s) {
        switch (c) {
            case '"':  result += "\\\""; break;
            case '\\': result += "\\\\"; break;
            case '\n': result += "\\n";  break;
            case '\r': result += "\\r";  break;
            case '\t': result += "\\t";  break;
            default:   result += c;
        }
    }
    return result;
}

std::string OmrProcessor::idResultToJson(const IdReadResult& r) {
    std::ostringstream oss;
    oss << "{"
        << "\"student_id\":\"" << escapeJson(r.student_id) << "\","
        << "\"class_code\":\"" << escapeJson(r.class_code) << "\","
        << "\"exam_code\":\"" << escapeJson(r.exam_code) << "\","
        << "\"id_ok\":" << (r.id_ok ? "true" : "false") << ","
        << "\"id_error\":\"" << escapeJson(r.id_error) << "\""
        << "}";
    return oss.str();
}

std::string OmrProcessor::answersToJson(const std::vector<AnswerReadResult>& answers) {
    std::ostringstream oss;
    oss << "[";
    for (size_t i = 0; i < answers.size(); i++) {
        if (i > 0) oss << ",";
        oss << "{"
            << "\"question_number\":" << answers[i].question_number << ","
            << "\"answer\":\"" << escapeJson(answers[i].answer) << "\","
            << "\"flag\":" << answers[i].flag
            << "}";
    }
    oss << "]";
    return oss.str();
}

std::string OmrProcessor::scoreToJson(const ScoreResult& s) {
    std::ostringstream oss;
    oss << "{"
        << "\"total_questions\":" << s.total_questions << ","
        << "\"correct_count\":" << s.correct_count << ","
        << "\"total_score\":" << s.total_score << ","
        << "\"details\":[";
    for (size_t i = 0; i < s.details.size(); i++) {
        if (i > 0) oss << ",";
        oss << "{"
            << "\"question_number\":" << s.details[i].question_number << ","
            << "\"student_answer\":\"" << escapeJson(s.details[i].student_answer) << "\","
            << "\"correct_answer\":\"" << escapeJson(s.details[i].correct_answer) << "\","
            << "\"is_correct\":" << (s.details[i].is_correct ? "true" : "false") << ","
            << "\"flag\":" << s.details[i].flag
            << "}";
    }
    oss << "]}";
    return oss.str();
}

std::string OmrProcessor::resultToJson(const OmrResult& result) {
    std::ostringstream oss;
    oss << "{"
        << "\"success\":" << (result.success ? "true" : "false") << ","
        << "\"error_code\":\"" << escapeJson(result.error_code) << "\","
        << "\"error_message\":\"" << escapeJson(result.error_message) << "\",";

    // Warnings
    oss << "\"warnings\":[";
    for (size_t i = 0; i < result.warnings.size(); i++) {
        if (i > 0) oss << ",";
        oss << "\"" << escapeJson(result.warnings[i]) << "\"";
    }
    oss << "],";

    // ID result
    oss << "\"id_result\":" << idResultToJson(result.id_result) << ",";

    // Answers
    oss << "\"student_answers\":" << answersToJson(result.answers) << ",";

    // Scoring
    oss << "\"scored\":" << (result.scored ? "true" : "false") << ",";
    if (result.scored) {
        oss << "\"score_result\":" << scoreToJson(result.score_result) << ",";
    } else {
        oss << "\"score_result\":null,";
    }

    // Quality
    oss << "\"laplacian_variance\":" << result.laplacian_variance << ","
        << "\"mean_brightness\":" << result.mean_brightness;

    // Debug image (optional — may be large)
    if (!result.dewarped_image_base64.empty()) {
        oss << ",\"dewarped_image_base64\":\"" << escapeJson(result.dewarped_image_base64) << "\"";
    }
    if (!result.debug_image_base64.empty()) {
        oss << ",\"debug_image_base64\":\"" << escapeJson(result.debug_image_base64) << "\"";
    }

    oss << "}";
    return oss.str();
}

// ─── Layout Cache Methods ─────────────────────────────────────────

uint64_t OmrProcessor::templateHash(const std::string& templateJson) {
    // FNV-1a 64-bit hash — fast, good distribution
    uint64_t hash = 14695981039346656037ULL;
    constexpr const char* layoutVersion = "layout-template-bank-even-answer-id-group-gap-v2";
    for (const unsigned char* p = reinterpret_cast<const unsigned char*>(layoutVersion); *p; ++p) {
        hash ^= static_cast<uint64_t>(*p);
        hash *= 1099511628211ULL;
    }
    for (unsigned char c : templateJson) {
        hash ^= static_cast<uint64_t>(c);
        hash *= 1099511628211ULL;
    }
    return hash;
}

const LayoutCacheEntry* OmrProcessor::getCachedLayout(uint64_t hash) {
    std::lock_guard<std::mutex> lock(s_cacheMutex_);
    auto it = s_layoutCache_.find(hash);
    if (it != s_layoutCache_.end()) {
        // Move to front of LRU order
        auto orderIt = std::find(s_cacheOrder_.begin(), s_cacheOrder_.end(), hash);
        if (orderIt != s_cacheOrder_.end()) {
            s_cacheOrder_.erase(orderIt);
            s_cacheOrder_.push_back(hash);
        }
        return &it->second;
    }
    return nullptr;
}

void OmrProcessor::putCachedLayout(uint64_t hash, const LayoutCacheEntry& entry) {
    std::lock_guard<std::mutex> lock(s_cacheMutex_);
    // Evict oldest if at capacity
    if (s_cacheOrder_.size() >= MAX_CACHE_ENTRIES && !s_cacheOrder_.empty()) {
        uint64_t oldest = s_cacheOrder_.front();
        s_cacheOrder_.erase(s_cacheOrder_.begin());
        s_layoutCache_.erase(oldest);
        LOGI("Cache evicted entry (hash=%llu)", static_cast<unsigned long long>(oldest));
    }
    s_layoutCache_[hash] = entry;
    s_cacheOrder_.push_back(hash);
    LOGI("Cache stored entry (hash=%llu, total=%zu)",
         static_cast<unsigned long long>(hash), s_cacheOrder_.size());
}

void OmrProcessor::clearLayoutCache() {
    std::lock_guard<std::mutex> lock(s_cacheMutex_);
    s_layoutCache_.clear();
    s_cacheOrder_.clear();
    LOGI("Layout cache cleared");
}

// ─── Auto-Adaptive Pipeline ──────────────────────────────────────

void OmrProcessor::autoConfigurePipeline(float laplacianVar, float meanBrightness) {
    // Decision matrix for teacher-facing auto mode.
    // Teacher just shoots — the app silently decides.

    if (meanBrightness > BRIGHTNESS_VERY_BRIGHT) {
        // Very bright/glare: avoid raw grayscale reading because pencil marks
        // may be lifted above the fixed dark-pixel cutoff.
        options_.use_adaptive_threshold = true;
        options_.adaptive_block_size    = ADAPTIVE_BLOCK_LARGE;
        options_.adaptive_c             = 5.0f;
        options_.preprocess_markers     = false;
        options_.preprocess_post_warp   = false;
        options_.morph_cleanup          = false;
        LOGI("Auto-adaptive: VERY BRIGHT/GLARE (%.0f) -> adaptive(large, sensitive)", meanBrightness);

    } else if (laplacianVar > LAPLACIAN_GOOD && meanBrightness > BRIGHTNESS_GOOD) {
        // ── Good quality scan: no preprocessing needed ──────
        options_.use_adaptive_threshold = false;
        options_.preprocess_markers     = false;
        options_.preprocess_post_warp   = false;
        options_.morph_cleanup          = false;
        LOGI("Auto-adaptive: GOOD quality → no preprocessing");

    } else if (laplacianVar < LAPLACIAN_VERY_BLUR) {
        // ── Very blurry: full preprocessing ────────────────
        options_.use_adaptive_threshold = true;
        options_.adaptive_block_size    = ADAPTIVE_BLOCK_LARGE;  // 51 — bigger window
        options_.adaptive_c             = 5.0f;                  // lower C = more sensitive
        options_.preprocess_markers     = true;
        options_.preprocess_post_warp   = true;
        options_.morph_cleanup          = true;
        LOGI("Auto-adaptive: VERY BLURRY (%.0f) → full preprocessing", laplacianVar);

    } else if (meanBrightness < BRIGHTNESS_VERY_DARK) {
        // ── Very dark: large adaptive + morph ──────────────
        options_.use_adaptive_threshold = true;
        options_.adaptive_block_size    = ADAPTIVE_BLOCK_LARGE;
        options_.adaptive_c             = 5.0f;
        options_.preprocess_markers     = false;
        options_.preprocess_post_warp   = false;
        options_.morph_cleanup          = true;
        LOGI("Auto-adaptive: VERY DARK (%.0f) → adaptive(large) + morph", meanBrightness);

    } else if (laplacianVar < LAPLACIAN_BLUR_THRESH) {
        // ── Blurry: pre-marker + adaptive ──────────────────
        options_.use_adaptive_threshold = true;
        options_.adaptive_block_size    = ADAPTIVE_BLOCK_SIZE;
        options_.adaptive_c             = ADAPTIVE_C;
        options_.preprocess_markers     = true;
        options_.preprocess_post_warp   = true;
        options_.morph_cleanup          = false;
        LOGI("Auto-adaptive: BLURRY (%.0f) → preMarker + adaptive", laplacianVar);

    } else if (meanBrightness < BRIGHTNESS_MIN) {
        // ── Low light: adaptive + morph ────────────────────
        options_.use_adaptive_threshold = true;
        options_.adaptive_block_size    = ADAPTIVE_BLOCK_SIZE;
        options_.adaptive_c             = ADAPTIVE_C;
        options_.preprocess_markers     = false;
        options_.preprocess_post_warp   = false;
        options_.morph_cleanup          = true;
        LOGI("Auto-adaptive: LOW LIGHT (%.0f) → adaptive + morph", meanBrightness);

    } else {
        // ── Default for camera photos: just adaptive ───────
        options_.use_adaptive_threshold = true;
        options_.adaptive_block_size    = ADAPTIVE_BLOCK_SIZE;
        options_.adaptive_c             = ADAPTIVE_C;
        options_.preprocess_markers     = false;
        options_.preprocess_post_warp   = false;
        options_.morph_cleanup          = false;
        LOGI("Auto-adaptive: DEFAULT → adaptive only");
    }
}

// ─── Preprocessing Methods ─────────────────────────────────────────

cv::Mat OmrProcessor::preprocessForMarkers(const cv::Mat& gray) {
    // Matches Python pipeline steps B1.5 + B2:
    //   Bilateral filter → CLAHE
    cv::Mat bilateral, clahe;

    cv::bilateralFilter(gray, bilateral,
        PRE_BILATERAL_D,
        PRE_BILATERAL_SIGMA_COLOR,
        PRE_BILATERAL_SIGMA_SPACE);

    auto claheObj = cv::createCLAHE(PRE_CLAHE_CLIP_LIMIT,
        cv::Size(PRE_CLAHE_TILE_SIZE, PRE_CLAHE_TILE_SIZE));
    claheObj->apply(bilateral, clahe);

    LOGI("Preprocess for markers: bilateral(d=%d) + CLAHE(clip=%.1f, tile=%d)",
         PRE_BILATERAL_D, PRE_CLAHE_CLIP_LIMIT, PRE_CLAHE_TILE_SIZE);

    return clahe;
}

cv::Mat OmrProcessor::preprocessPostWarp(const cv::Mat& warpedGray) {
    // Matches Python pipeline steps B5 + B6:
    //   Bilateral filter (mild) → GaussianBlur
    cv::Mat bilateral, blurred;

    cv::bilateralFilter(warpedGray, bilateral,
        POST_BILATERAL_D,
        POST_BILATERAL_SIGMA_COLOR,
        POST_BILATERAL_SIGMA_SPACE);

    cv::GaussianBlur(bilateral, blurred,
        cv::Size(POST_GAUSSIAN_KSIZE, POST_GAUSSIAN_KSIZE), 0);

    LOGI("Preprocess post-warp: bilateral(d=%d) + Gaussian(%dx%d)",
         POST_BILATERAL_D, POST_GAUSSIAN_KSIZE, POST_GAUSSIAN_KSIZE);

    return blurred;
}

void OmrProcessor::applyMorphCleanup(cv::Mat& binary) {
    // Matches Python pipeline steps B7-B10:
    //   Close (ellipse 3x3) → Open (ellipse 3x3) → Otsu → Close final (ellipse 2x2)

    cv::Mat kernel3 = cv::getStructuringElement(cv::MORPH_ELLIPSE,
        cv::Size(MORPH_CLOSE_KSIZE, MORPH_CLOSE_KSIZE));

    cv::morphologyEx(binary, binary, cv::MORPH_CLOSE, kernel3, cv::Point(-1,-1), 1);
    cv::morphologyEx(binary, binary, cv::MORPH_OPEN,  kernel3, cv::Point(-1,-1), 1);

    // Otsu threshold: binary is already 0/255, but Otsu helps normalize
    // when some bubble regions are in the mid-range due to partial fill
    cv::threshold(binary, binary, 0, 255,
                  cv::THRESH_BINARY + cv::THRESH_OTSU);

    // Final light cleanup
    cv::Mat kernel2 = cv::getStructuringElement(cv::MORPH_ELLIPSE,
        cv::Size(MORPH_FINAL_KSIZE, MORPH_FINAL_KSIZE));
    cv::morphologyEx(binary, binary, cv::MORPH_CLOSE, kernel2, cv::Point(-1,-1), 1);

    LOGI("Morph cleanup: close(%dx%d) + open + Otsu + close(%dx%d)",
         MORPH_CLOSE_KSIZE, MORPH_CLOSE_KSIZE,
         MORPH_FINAL_KSIZE, MORPH_FINAL_KSIZE);
}

} // namespace omr
