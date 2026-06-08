#ifndef MARKER_DETECTOR_H
#define MARKER_DETECTOR_H

#include "omr_constants.h"
#include <vector>
#include <map>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/objdetect/aruco_detector.hpp>
#include <opencv2/objdetect/aruco_dictionary.hpp>

namespace omr {

/**
 * Detected marker info in image space.
 */
struct DetectedMarker {
    int id;
    cv::Point2f center;
    std::vector<cv::Point2f> corners;  // 4 corners, ordered
};

/**
 * Dewarp method selection result.
 */
enum class DewarpMethod {
    PERSPECTIVE,
    HYBRID,
    TPS,
    NONE
};

struct DewarpInfo {
    DewarpMethod method;
    float avg_euclidean_error;   // Average pixel error between detected and expected
    float max_euclidean_error;
    std::string reason;
};

/**
 * MarkerDetector — Detects AprilTag markers via ArUco and performs dewarping.
 */
class MarkerDetector {
public:
    MarkerDetector();

    /**
     * Detect AprilTag markers in grayscale image.
     * Uses DICT_APRILTAG_16h5 dictionary. Returns markers with ID 0-15 mapped to 1-16.
     */
    std::vector<DetectedMarker> detectMarkers(const cv::Mat& grayImg);

    /**
     * Determine the dewarp method based on Euclidean error between
     * detected marker centers and their expected positions (in normalized PAGE coords).
     *
     * Per spec:
     *   e < 15px  → Perspective (GOOD)
     *   15 <= e < 25px → Hybrid
     *   e >= 25px → TPS / Mesh
     */
    DewarpInfo determineDewarpMethod(
        const std::vector<DetectedMarker>& detectedMarkers,
        const std::vector<AnchorPoint>& expectedAnchors
    );

    /**
     * Perform perspective dewarp using 4 corner markers.
     * Maps source image corners to standard PAGE_WIDTH x PAGE_HEIGHT.
     *
     * @return warped image at canonical size, and the 4 corner points used
     */
    cv::Mat dewarpPerspective(
        const cv::Mat& src,
        const std::vector<DetectedMarker>& detectedMarkers,
        const std::vector<AnchorPoint>& expectedAnchors,
        std::vector<cv::Point2f>& usedCorners
    );

    /**
     * Perform hybrid dewarp: perspective + local refinement with remaining markers.
     */
    cv::Mat dewarpHybrid(
        const cv::Mat& src,
        const std::vector<DetectedMarker>& detectedMarkers,
        const std::vector<AnchorPoint>& expectedAnchors
    );

    /**
     * Perform full-page piecewise mesh dewarp from marker control points.
     *
     * Uses marker centers and marker corners as control points, then builds a
     * Delaunay mesh on template coordinates. Each triangle is warped from the
     * camera image into the canonical page.
     */
    cv::Mat dewarpPiecewiseMesh(
        const cv::Mat& src,
        const std::vector<DetectedMarker>& detectedMarkers,
        const std::vector<AnchorPoint>& expectedAnchors
    );

    /**
     * Perform full-page Thin Plate Spline dewarp from marker control points.
     */
    cv::Mat dewarpTps(
        const cv::Mat& src,
        const std::vector<DetectedMarker>& detectedMarkers,
        const std::vector<AnchorPoint>& expectedAnchors,
        double smoothness
    );

    /**
     * Warp the source image into canonical page space using only markers near
     * a target zone. Intended for second-pass answer-zone reading when global
     * residual is high.
     */
    cv::Mat dewarpLocalRegion(
        const cv::Mat& src,
        const std::vector<DetectedMarker>& detectedMarkers,
        const std::vector<AnchorPoint>& expectedAnchors,
        const cv::Rect2f& targetRect,
        std::vector<int>& usedMarkerIds
    );

    /**
     * Check image quality: returns (laplacianVariance, meanBrightness).
     */
    static void checkQuality(
        const cv::Mat& grayImg,
        float& laplacianVar,
        float& meanBrightness
    );

    /**
     * Get 4 corner marker IDs: TL, TR, BR, BL based on anchor positions.
     */
    static std::vector<int> getCornerMarkerIds(
        const std::vector<AnchorPoint>& anchors
    );

private:
    cv::aruco::DetectorParameters detectorParams_;
    cv::aruco::Dictionary dictionary_;
    cv::Ptr<cv::aruco::ArucoDetector> arucoDetector_;

    /**
     * Map detected marker ID to expected anchor point.
     */
    cv::Point2f mapToAnchor(
        int markerId,
        const std::vector<DetectedMarker>& detected,
        const std::vector<AnchorPoint>& expected
    );

    /**
     * Calculate Euclidean distance between detected center and expected anchor.
     */
    float computeEuclideanError(
        const cv::Point2f& detected,
        const cv::Point2f& expected
    );
};

} // namespace omr

#endif // MARKER_DETECTOR_H
