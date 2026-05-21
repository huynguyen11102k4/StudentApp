#include "include/marker_detector.h"
#include "include/distortion_analyzer.h"
#include <opencv2/objdetect/aruco_detector.hpp>
#include <opencv2/objdetect/aruco_dictionary.hpp>
#include <opencv2/calib3d.hpp>
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <limits>
#include <set>

#define LOG_TAG "MarkerDetect"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace omr {

namespace {

constexpr size_t MIN_MARKERS_FOR_FAST_DEWARP = 10;

bool hasAllMarkers(const std::map<int, cv::Point2f>& markers, const std::vector<int>& ids) {
    for (int id : ids) {
        if (markers.find(id) == markers.end()) return false;
    }
    return true;
}

void logPointPair(const char* label, int id, const cv::Point2f& src, const cv::Point2f& dst) {
    LOGI("%s marker=%d src=(%.2f, %.2f) dst=(%.2f, %.2f)",
         label, id, src.x, src.y, dst.x, dst.y);
}

void appendUniqueMarkers(
    std::vector<DetectedMarker>& target,
    const std::vector<DetectedMarker>& candidates,
    const char* passName
) {
    for (const auto& candidate : candidates) {
        auto existing = std::find_if(target.begin(), target.end(), [&](const DetectedMarker& marker) {
            return marker.id == candidate.id;
        });
        if (existing == target.end()) {
            target.push_back(candidate);
            LOGI("Marker recovery pass=%s accepted id=%d center=(%.2f, %.2f)",
                 passName, candidate.id, candidate.center.x, candidate.center.y);
        }
    }
}

cv::Mat applyGamma(const cv::Mat& gray, double gamma) {
    cv::Mat lut(1, 256, CV_8UC1);
    uchar* p = lut.ptr();
    double invGamma = 1.0 / std::max(0.01, gamma);
    for (int i = 0; i < 256; i++) {
        p[i] = cv::saturate_cast<uchar>(std::pow(i / 255.0, invGamma) * 255.0);
    }
    cv::Mat adjusted;
    cv::LUT(gray, lut, adjusted);
    return adjusted;
}

std::map<int, cv::Point2f> buildExpectedMap(const std::vector<AnchorPoint>& expectedAnchors) {
    std::map<int, cv::Point2f> expectedMap;
    for (const auto& ap : expectedAnchors) {
        expectedMap[ap.id] = cv::Point2f(ap.abs_x, ap.abs_y);
    }
    return expectedMap;
}

float scoreMarkerAssignment(
    const std::map<int, cv::Point2f>& detectedMap,
    const std::map<int, cv::Point2f>& expectedMap
) {
    if (detectedMap.size() < 4) {
        return std::numeric_limits<float>::max();
    }

    std::vector<cv::Point2f> srcPts;
    std::vector<cv::Point2f> dstPts;
    for (const auto& pair : detectedMap) {
        auto expectedIt = expectedMap.find(pair.first);
        if (expectedIt == expectedMap.end()) continue;
        srcPts.push_back(pair.second);
        dstPts.push_back(expectedIt->second);
    }

    if (srcPts.size() < 4) {
        return std::numeric_limits<float>::max();
    }

    cv::Mat h = cv::findHomography(srcPts, dstPts, 0);
    if (h.empty()) {
        return std::numeric_limits<float>::max();
    }

    std::vector<cv::Point2f> projected;
    cv::perspectiveTransform(srcPts, projected, h);
    float sum = 0.0f;
    float maxErr = 0.0f;
    for (size_t i = 0; i < projected.size(); i++) {
        float dx = projected[i].x - dstPts[i].x;
        float dy = projected[i].y - dstPts[i].y;
        float err = std::sqrt(dx * dx + dy * dy);
        sum += err;
        maxErr = std::max(maxErr, err);
    }

    float avg = projected.empty() ? std::numeric_limits<float>::max()
                                  : sum / static_cast<float>(projected.size());
    return avg + maxErr * 0.25f;
}

void blendWarpedSection(
    const cv::Mat& localWarped,
    cv::Mat& refined,
    const std::vector<cv::Point>& maskPoly,
    float featherPx
) {
    cv::Mat mask = cv::Mat::zeros(refined.size(), CV_8UC1);
    cv::fillConvexPoly(mask, maskPoly, cv::Scalar(255));

    cv::Rect imageRect(0, 0, refined.cols, refined.rows);
    cv::Rect roi = cv::boundingRect(maskPoly) & imageRect;
    if (roi.empty()) {
        return;
    }

    cv::Mat maskRoi = mask(roi);
    cv::Mat dist;
    cv::distanceTransform(maskRoi, dist, cv::DIST_L2, 3);

    cv::Mat alpha;
    dist.convertTo(alpha, CV_32F, 1.0f / std::max(1.0f, featherPx));
    cv::threshold(alpha, alpha, 1.0, 1.0, cv::THRESH_TRUNC);

    cv::Mat alphaForBlend;
    if (refined.channels() == 1) {
        alphaForBlend = alpha;
    } else {
        std::vector<cv::Mat> channels(refined.channels(), alpha);
        cv::merge(channels, alphaForBlend);
    }

    cv::Mat localF;
    cv::Mat refinedF;
    localWarped(roi).convertTo(localF, CV_32F);
    refined(roi).convertTo(refinedF, CV_32F);

    cv::Mat invAlpha = cv::Scalar::all(1.0) - alphaForBlend;
    cv::Mat localWeighted;
    cv::Mat refinedWeighted;
    cv::multiply(localF, alphaForBlend, localWeighted);
    cv::multiply(refinedF, invAlpha, refinedWeighted);

    cv::Mat blended;
    cv::add(localWeighted, refinedWeighted, blended);
    blended.convertTo(refined(roi), refined.type());
}

std::vector<cv::Point> directSectionMaskPoly(const std::vector<int>& quad) {
    auto pt = [](float x, float y) {
        return cv::Point(cvRound(x), cvRound(y));
    };

    if (quad == std::vector<int>{1, 12, 6, 5}) {
        return {pt(0, 0), pt(414, 0), pt(414, 403), pt(0, 403)};
    }
    if (quad == std::vector<int>{12, 2, 7, 6}) {
        return {pt(414, 0), pt(PAGE_WIDTH, 0), pt(PAGE_WIDTH, 403), pt(414, 403)};
    }
    if (quad == std::vector<int>{5, 6, 9, 10}) {
        return {pt(0, 403), pt(414, 403), pt(414, 763), pt(0, 763)};
    }
    if (quad == std::vector<int>{6, 7, 8, 9}) {
        return {pt(414, 403), pt(PAGE_WIDTH, 403), pt(PAGE_WIDTH, 763), pt(414, 763)};
    }
    if (quad == std::vector<int>{10, 9, 11, 4}) {
        return {pt(0, 763), pt(414, 763), pt(414, PAGE_HEIGHT), pt(0, PAGE_HEIGHT)};
    }
    if (quad == std::vector<int>{9, 8, 3, 11}) {
        return {pt(414, 763), pt(PAGE_WIDTH, 763), pt(PAGE_WIDTH, PAGE_HEIGHT), pt(414, PAGE_HEIGHT)};
    }

    return {};
}

std::map<int, cv::Point2f> buildBestDetectedMap(
    const std::vector<DetectedMarker>& detectedMarkers,
    const std::vector<AnchorPoint>& expectedAnchors
) {
    std::map<int, std::vector<cv::Point2f>> grouped;
    for (const auto& dm : detectedMarkers) {
        grouped[dm.id].push_back(dm.center);
    }

    std::map<int, cv::Point2f> detectedMap;
    std::vector<int> duplicateIds;
    for (const auto& pair : grouped) {
        if (pair.second.empty()) continue;
        detectedMap[pair.first] = pair.second.front();
        if (pair.second.size() > 1) {
            duplicateIds.push_back(pair.first);
            LOGE("Duplicate marker id=%d candidates=%zu", pair.first, pair.second.size());
        }
    }

    std::vector<int> missingIds;
    std::map<int, cv::Point2f> expectedMap = buildExpectedMap(expectedAnchors);
    for (const auto& pair : expectedMap) {
        if (grouped.find(pair.first) == grouped.end()) {
            missingIds.push_back(pair.first);
            LOGE("Missing expected marker id=%d", pair.first);
        }
    }

    // Common AprilTag failure mode: one marker is misclassified as a nearby
    // duplicate id. If exactly one expected marker is missing, try relabeling
    // each duplicate candidate and keep the assignment with the lowest
    // homography reprojection error.
    if (missingIds.size() == 1 && !duplicateIds.empty()) {
        int missingId = missingIds.front();
        float bestScore = scoreMarkerAssignment(detectedMap, expectedMap);
        std::map<int, cv::Point2f> bestMap = detectedMap;
        int bestDuplicateId = -1;
        int bestOriginalIndex = -1;
        int bestMissingIndex = -1;

        for (int duplicateId : duplicateIds) {
            const auto& candidates = grouped[duplicateId];
            if (candidates.size() < 2) continue;

            for (size_t originalIdx = 0; originalIdx < candidates.size(); originalIdx++) {
                for (size_t missingIdx = 0; missingIdx < candidates.size(); missingIdx++) {
                    if (missingIdx == originalIdx) continue;

                    std::map<int, cv::Point2f> candidateMap = detectedMap;
                    candidateMap[duplicateId] = candidates[originalIdx];
                    candidateMap[missingId] = candidates[missingIdx];

                    float score = scoreMarkerAssignment(candidateMap, expectedMap);
                    if (score < bestScore) {
                        bestScore = score;
                        bestMap = candidateMap;
                        bestDuplicateId = duplicateId;
                        bestOriginalIndex = static_cast<int>(originalIdx);
                        bestMissingIndex = static_cast<int>(missingIdx);
                    }
                }
            }
        }

        if (bestDuplicateId >= 0) {
            LOGI(
                "Recovered marker label: duplicate id=%d candidate[%d] kept, candidate[%d] relabeled to missing id=%d score=%.2f",
                bestDuplicateId,
                bestOriginalIndex,
                bestMissingIndex,
                missingId,
                bestScore
            );
            return bestMap;
        }
    }

    return detectedMap;
}

} // namespace

MarkerDetector::MarkerDetector() {
    dictionary_ = cv::aruco::getPredefinedDictionary(cv::aruco::DICT_APRILTAG_16h5);

    detectorParams_ = cv::aruco::DetectorParameters();
    detectorParams_.adaptiveThreshWinSizeMin  = 3;
    detectorParams_.adaptiveThreshWinSizeMax  = 53;
    detectorParams_.adaptiveThreshWinSizeStep = 8;
    detectorParams_.adaptiveThreshConstant    = 5.0;
    detectorParams_.minMarkerPerimeterRate    = 0.015;
    detectorParams_.maxMarkerPerimeterRate    = 4.0;
    detectorParams_.minCornerDistanceRate     = 0.03;
    detectorParams_.minOtsuStdDev             = 3.0;
    detectorParams_.errorCorrectionRate       = 0.8;
    detectorParams_.cornerRefinementMethod    = cv::aruco::CORNER_REFINE_SUBPIX;
    detectorParams_.cornerRefinementWinSize   = 5;
    detectorParams_.cornerRefinementMaxIterations = 30;
    detectorParams_.cornerRefinementMinAccuracy   = 0.01;

    arucoDetector_ = cv::makePtr<cv::aruco::ArucoDetector>(dictionary_, detectorParams_);
}

std::vector<DetectedMarker> MarkerDetector::detectMarkers(const cv::Mat& grayImg) {
    auto runDetector = [&](const cv::Mat& input, const char* passName) {
        std::vector<DetectedMarker> passResult;
        std::vector<std::vector<cv::Point2f>> corners;
        std::vector<int> ids;

        arucoDetector_->detectMarkers(input, corners, ids);
        LOGI("Aruco pass=%s detected %zu markers", passName, ids.size());

        for (size_t i = 0; i < ids.size(); i++) {
            DetectedMarker dm;
            dm.id = ids[i];

            float cx = 0.0f;
            float cy = 0.0f;
            for (const auto& c : corners[i]) {
                dm.corners.push_back(c);
                cx += c.x;
                cy += c.y;
            }
            cx /= 4.0f;
            cy /= 4.0f;
            dm.center = cv::Point2f(cx, cy);
            passResult.push_back(dm);
        }
        return passResult;
    };

    std::vector<DetectedMarker> result = runDetector(grayImg, "raw");

    if (result.size() < MIN_MARKERS_FOR_FAST_DEWARP) {
        cv::Mat normalized;
        cv::normalize(grayImg, normalized, 0, 255, cv::NORM_MINMAX);
        appendUniqueMarkers(result, runDetector(normalized, "normalized"), "normalized");
    }

    if (result.size() < MIN_MARKERS_FOR_FAST_DEWARP) {
        cv::Mat brightened = applyGamma(grayImg, 0.65);
        appendUniqueMarkers(result, runDetector(brightened, "gamma_bright"), "gamma_bright");
    }

    if (result.size() < MIN_MARKERS_FOR_FAST_DEWARP) {
        cv::Mat claheImg;
        auto clahe = cv::createCLAHE(3.0, cv::Size(8, 8));
        clahe->apply(grayImg, claheImg);
        appendUniqueMarkers(result, runDetector(claheImg, "clahe"), "clahe");
    }

    if (result.size() < MIN_MARKERS_FOR_FAST_DEWARP) {
        cv::Mat blurred;
        cv::GaussianBlur(grayImg, blurred, cv::Size(0, 0), 1.0);
        cv::Mat sharpened;
        cv::addWeighted(grayImg, 1.6, blurred, -0.6, 0.0, sharpened);
        appendUniqueMarkers(result, runDetector(sharpened, "sharpen"), "sharpen");
    }

    if (result.size() < MIN_MARKERS_FOR_FAST_DEWARP) {
        cv::Mat binary;
        cv::adaptiveThreshold(grayImg, binary, 255,
                              cv::ADAPTIVE_THRESH_GAUSSIAN_C,
                              cv::THRESH_BINARY, 41, 5.0);
        appendUniqueMarkers(result, runDetector(binary, "adaptive_binary"), "adaptive_binary");
    }

    if (result.size() < MIN_MARKERS_FOR_FAST_DEWARP) {
        cv::Mat binary;
        cv::adaptiveThreshold(grayImg, binary, 255,
                              cv::ADAPTIVE_THRESH_MEAN_C,
                              cv::THRESH_BINARY, 61, 3.0);
        appendUniqueMarkers(result, runDetector(binary, "adaptive_mean_binary"), "adaptive_mean_binary");
    }

    std::set<int> uniqueIds;
    for (const auto& dm : result) {
        uniqueIds.insert(dm.id);
    }
    LOGI("Aruco detected %zu markers, %zu unique ids after recovery", result.size(), uniqueIds.size());
    for (const auto& dm : result) {
        LOGI("Marker id=%d center=(%.2f, %.2f)", dm.id, dm.center.x, dm.center.y);
    }
    return result;
}

DewarpInfo MarkerDetector::determineDewarpMethod(
    const std::vector<DetectedMarker>& detectedMarkers,
    const std::vector<AnchorPoint>& expectedAnchors
) {
    DewarpInfo info;
    info.method = DewarpMethod::NONE;

    if (detectedMarkers.empty()) {
        info.avg_euclidean_error = std::numeric_limits<float>::max();
        info.max_euclidean_error = std::numeric_limits<float>::max();
        info.reason = "No markers detected";
        return info;
    }

    std::map<int, cv::Point2f> detectedMap = buildBestDetectedMap(detectedMarkers, expectedAnchors);
    std::map<int, cv::Point2f> expectedMap = buildExpectedMap(expectedAnchors);
    std::vector<cv::Point2f> srcPts;
    std::vector<cv::Point2f> dstPts;
    for (const auto& ap : expectedAnchors) {
        auto detectedIt = detectedMap.find(ap.id);
        auto expectedIt = expectedMap.find(ap.id);
        if (detectedIt == detectedMap.end() || expectedIt == expectedMap.end()) continue;
        srcPts.push_back(detectedIt->second);
        dstPts.push_back(expectedIt->second);
    }

    if (srcPts.size() < 4) {
        info.method = DewarpMethod::NONE;
        info.reason = "Not enough matched markers for homography";
        info.max_euclidean_error = std::numeric_limits<float>::max();
        info.avg_euclidean_error = std::numeric_limits<float>::max();
        LOGI("Dewarp selection: method=%d residualAvg=inf residualMax=inf matched=0 reason=%s",
             static_cast<int>(info.method), info.reason.c_str());
        return info;
    }

    bool hasAllCorners = hasAllMarkers(detectedMap, {1, 2, 3, 4});
    cv::Mat globalH = hasAllCorners && srcPts.size() == 4
        ? cv::getPerspectiveTransform(srcPts, dstPts)
        : cv::findHomography(srcPts, dstPts, cv::RANSAC, 8.0);
    if (globalH.empty()) {
        info.method = DewarpMethod::NONE;
        info.reason = "Failed to compute global homography from matched markers";
        info.max_euclidean_error = std::numeric_limits<float>::max();
        info.avg_euclidean_error = std::numeric_limits<float>::max();
        LOGI("Dewarp selection: method=%d residualAvg=inf residualMax=inf matched=0 reason=%s",
             static_cast<int>(info.method), info.reason.c_str());
        return info;
    }

    std::vector<int> matchedIds;
    std::vector<cv::Point2f> detectedCenters;
    std::vector<cv::Point2f> expectedCenters;
    for (const auto& ap : expectedAnchors) {
        auto detectedIt = detectedMap.find(ap.id);
        if (detectedIt == detectedMap.end()) continue;
        matchedIds.push_back(ap.id);
        detectedCenters.push_back(detectedIt->second);
        expectedCenters.emplace_back(ap.abs_x, ap.abs_y);
    }

    std::vector<cv::Point2f> projectedCenters;
    cv::perspectiveTransform(detectedCenters, projectedCenters, globalH);

    float sumError = 0.0f;
    float maxError = 0.0f;
    int matchedCount = 0;
    for (size_t i = 0; i < projectedCenters.size() && i < expectedCenters.size(); i++) {
        float dx = projectedCenters[i].x - expectedCenters[i].x;
        float dy = projectedCenters[i].y - expectedCenters[i].y;
        float e = std::sqrt(dx * dx + dy * dy);
        sumError += e;
        maxError = std::max(maxError, e);
        matchedCount++;
    }

    info.max_euclidean_error = maxError;
    info.avg_euclidean_error = matchedCount == 0
        ? std::numeric_limits<float>::max()
        : sumError / static_cast<float>(matchedCount);

    if (maxError < DEWARP_GOOD_PX) {
        info.method = DewarpMethod::PERSPECTIVE;
        info.reason = "Global residual is low: using perspective";
    } else if (maxError < DEWARP_MODERATE_PX) {
        info.method = DewarpMethod::HYBRID;
        info.reason = "Global residual is moderate: using hybrid TPS refinement";
    } else {
        info.method = DewarpMethod::TPS;
        info.reason = "Global residual >= 25px: using full TPS/Mesh path";
    }

    LOGI("Dewarp selection: method=%d residualAvg=%.2f residualMax=%.2f matched=%d goodThreshold=%.2f tpsThreshold=%.2f reason=%s",
         static_cast<int>(info.method), info.avg_euclidean_error, maxError,
         matchedCount, DEWARP_GOOD_PX, DEWARP_MODERATE_PX, info.reason.c_str());

    return info;
}

std::vector<int> MarkerDetector::getCornerMarkerIds(
    const std::vector<AnchorPoint>& anchors
) {
    (void)anchors;
    return {1, 2, 3, 4};
}

cv::Mat MarkerDetector::dewarpPerspective(
    const cv::Mat& src,
    const std::vector<DetectedMarker>& detectedMarkers,
    const std::vector<AnchorPoint>& expectedAnchors,
    std::vector<cv::Point2f>& usedCorners
) {
    usedCorners.clear();

    std::map<int, cv::Point2f> detectedMap = buildBestDetectedMap(detectedMarkers, expectedAnchors);

    std::map<int, cv::Point2f> expectedMap = buildExpectedMap(expectedAnchors);
    for (const auto& ap : expectedAnchors) {
        LOGI("Expected anchor id=%d pos=(%.2f, %.2f)", ap.id, ap.abs_x, ap.abs_y);
    }

    std::vector<cv::Point2f> srcPts;
    std::vector<cv::Point2f> dstPts;

    for (const auto& ap : expectedAnchors) {
        auto detectedIt = detectedMap.find(ap.id);
        auto expectedIt = expectedMap.find(ap.id);
        if (detectedIt == detectedMap.end() || expectedIt == expectedMap.end()) {
            continue;
        }

        srcPts.push_back(detectedIt->second);
        dstPts.push_back(expectedIt->second);
        logPointPair("GlobalHomography", ap.id, detectedIt->second, expectedIt->second);
    }

    usedCorners = srcPts;

    if (srcPts.size() < 4) {
        LOGE("Global homography needs at least 4 matched markers, found %zu", srcPts.size());
        return cv::Mat();
    }

    cv::Mat globalH = cv::findHomography(srcPts, dstPts, cv::RANSAC, 8.0);
    if (globalH.empty()) {
        LOGE("Global homography failed with %zu matched markers", srcPts.size());
        return cv::Mat();
    }

    cv::Mat warped;
    cv::warpPerspective(src, warped, globalH,
                        cv::Size(static_cast<int>(PAGE_WIDTH), static_cast<int>(PAGE_HEIGHT)),
                        cv::INTER_LINEAR, cv::BORDER_CONSTANT, cv::Scalar(255, 255, 255));

    LOGI("Template canvas size: %.0fx%.0f. Global perspective maps markers 1,2,3,4 to their template anchor positions, not page outer corners.",
         PAGE_WIDTH, PAGE_HEIGHT);
    LOGI("Global perspective dewarp: input=%dx%d output=%dx%d",
         src.cols, src.rows, warped.cols, warped.rows);

    std::vector<int> detectedIds;
    std::vector<cv::Point2f> detectedCenters;
    for (const auto& pair : detectedMap) {
        detectedIds.push_back(pair.first);
        detectedCenters.push_back(pair.second);
    }

    std::vector<cv::Point2f> canonicalCenters;
    if (!detectedCenters.empty()) {
        cv::perspectiveTransform(detectedCenters, canonicalCenters, globalH);
    }

    std::map<int, cv::Point2f> canonicalDetectedMap;
    float residualSum = 0.0f;
    float residualMax = 0.0f;
    int residualCount = 0;
    for (size_t i = 0; i < detectedIds.size() && i < canonicalCenters.size(); i++) {
        canonicalDetectedMap[detectedIds[i]] = canonicalCenters[i];
        auto expectedIt = expectedMap.find(detectedIds[i]);
        if (expectedIt != expectedMap.end()) {
            float dx = canonicalCenters[i].x - expectedIt->second.x;
            float dy = canonicalCenters[i].y - expectedIt->second.y;
            float residual = std::sqrt(dx * dx + dy * dy);
            residualSum += residual;
            residualMax = std::max(residualMax, residual);
            residualCount++;
            LOGI("PostGlobal marker=%d canonical=(%.2f, %.2f) expected=(%.2f, %.2f) residual=(%.2f, %.2f)",
                 detectedIds[i], canonicalCenters[i].x, canonicalCenters[i].y,
                 expectedIt->second.x, expectedIt->second.y, dx, dy);
        }
    }

    float residualAvg = residualCount > 0 ? residualSum / residualCount : 0.0f;
    constexpr float LOCAL_REFINE_MAX_THRESHOLD = 3.0f;
    if (residualMax <= LOCAL_REFINE_MAX_THRESHOLD) {
        LOGI("SectionPerspective skipped: global residual already low avg=%.2f max=%.2f threshold=%.2f",
             residualAvg, residualMax, LOCAL_REFINE_MAX_THRESHOLD);
        return warped;
    }

    LOGI("SectionPerspective enabled: global residual avg=%.2f max=%.2f threshold=%.2f",
         residualAvg, residualMax, LOCAL_REFINE_MAX_THRESHOLD);
    bool directPiecewise = residualMax >= DEWARP_MODERATE_PX;
    LOGI("SectionPerspective mode=%s residualMax=%.2f directThreshold=%.2f",
         directPiecewise ? "DIRECT_CAMERA_TO_TEMPLATE" : "GLOBAL_THEN_LOCAL",
         residualMax,
         DEWARP_MODERATE_PX);

    const std::vector<std::vector<int>> sectionQuads = {
        {1, 12, 6, 5},
        {5, 6, 9, 10},
        {6, 7, 8, 9},
        {10, 9, 11, 4},
        {9, 8, 3, 11},
        // The ID grid sits mostly in the top-right section. Merge it last so
        // adjacent local warps cannot overwrite thin bubble strokes there.
        {12, 2, 7, 6}
    };

    cv::Mat refined = warped.clone();
    const cv::Mat& sectionInput = directPiecewise ? src : warped;
    const std::map<int, cv::Point2f>& sectionSrcMap = directPiecewise
        ? detectedMap
        : canonicalDetectedMap;

    for (const auto& quad : sectionQuads) {
        if (!hasAllMarkers(sectionSrcMap, quad) || !hasAllMarkers(expectedMap, quad)) {
            LOGE("SectionPerspective skipped quad=(%d,%d,%d,%d): missing marker",
                 quad[0], quad[1], quad[2], quad[3]);
            continue;
        }

        std::vector<cv::Point2f> localSrc;
        std::vector<cv::Point2f> localDst;
        for (int id : quad) {
            localSrc.push_back(sectionSrcMap.at(id));
            localDst.push_back(expectedMap[id]);
            logPointPair("SectionPerspective", id, sectionSrcMap.at(id), expectedMap[id]);
        }

        cv::Mat localH = cv::getPerspectiveTransform(localSrc, localDst);
        cv::Mat localWarped;
        cv::warpPerspective(sectionInput, localWarped, localH, warped.size(),
                            cv::INTER_LINEAR, cv::BORDER_CONSTANT, cv::Scalar(255, 255, 255));

        std::vector<cv::Point> maskPoly = directSectionMaskPoly(quad);
        const bool usesSectionBoundaryMask = !maskPoly.empty();
        if (maskPoly.empty()) {
            for (const auto& p : localDst) {
                maskPoly.emplace_back(cv::Point(cvRound(p.x), cvRound(p.y)));
            }
        }
        constexpr float SECTION_BLEND_FEATHER_PX = 8.0f;
        if (directPiecewise) {
            cv::Mat mask = cv::Mat::zeros(warped.size(), CV_8UC1);
            cv::fillConvexPoly(mask, maskPoly, cv::Scalar(255));
            localWarped.copyTo(refined, mask);
        } else {
            blendWarpedSection(localWarped, refined, maskPoly, SECTION_BLEND_FEATHER_PX);
        }

        LOGI("SectionPerspective applied quad=(%d,%d,%d,%d) source=%s blend=%s mask=%s featherPx=%.1f",
             quad[0], quad[1], quad[2], quad[3],
             directPiecewise ? "camera" : "global",
             directPiecewise ? "hard" : "feather",
             usesSectionBoundaryMask ? "section-boundary" : "anchor-quad",
             directPiecewise ? 0.0f : SECTION_BLEND_FEATHER_PX);
    }

    return refined;
}

cv::Mat MarkerDetector::dewarpHybrid(
    const cv::Mat& src,
    const std::vector<DetectedMarker>& detectedMarkers,
    const std::vector<AnchorPoint>& expectedAnchors
) {
    std::vector<cv::Point2f> corners;
    return dewarpPerspective(src, detectedMarkers, expectedAnchors, corners);
}

void MarkerDetector::checkQuality(
    const cv::Mat& grayImg,
    float& laplacianVar,
    float& meanBrightness
) {
    cv::Mat lap;
    cv::Laplacian(grayImg, lap, CV_64F);
    cv::Scalar mean, stddev;
    cv::meanStdDev(lap, mean, stddev);
    laplacianVar = static_cast<float>(stddev.val[0] * stddev.val[0]);

    cv::Scalar avg = cv::mean(grayImg);
    meanBrightness = static_cast<float>(avg.val[0]);

    LOGI("Quality laplacian=%.2f meanBrightness=%.2f", laplacianVar, meanBrightness);
}

} // namespace omr
