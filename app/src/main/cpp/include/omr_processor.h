#ifndef OMR_PROCESSOR_H
#define OMR_PROCESSOR_H

#include "omr_constants.h"
#include "layout_calculator.h"
#include "marker_detector.h"
#include <string>
#include <vector>
#include <map>
#include <unordered_map>
#include <mutex>
#include <opencv2/core.hpp>

namespace omr {

// ─── Layout Cache ───────────────────────────────────────────

/**
 * Pre-computed bubble cell positions for a given template.
 * Avoids re-parsing JSON and re-computing grid layouts on every call
 * when processing multiple sheets from the same exam.
 */
struct LayoutCacheEntry {
    std::vector<BubbleCell> idCells;
    // answer zone cells keyed by zone_id
    std::map<std::string, std::vector<BubbleCell>> answerCells;
};

/**
 * OmrProcessor — Main OMR pipeline orchestrator.
 *
 * Features a static layout cache (keyed by template JSON hash)
 * to avoid recalculating cell positions for the same exam.
 *
 * Usage:
 *   OmrProcessor processor(options);
 *   OmrResult result = processor.process(image, templateJson, answerKeyJson);
 */
class OmrProcessor {
public:
    explicit OmrProcessor(const OmrOptions& options = OmrOptions());

    /**
     * Main entry point: processes an image with template JSON and optional answer key.
     *
     * @param image          Input image (BGR or grayscale)
     * @param templateJson   JSON string from /mobile/exams/:examId/template
     * @param answerKeyJson  Optional JSON string from /mobile/exams/:examId/answer-keys
     *                       Format: { "examCode": { "1": "A", "2": "B", ... } }
     * @return OmrResult with success flag, extracted data, and optional scoring
     */
    OmrResult process(
        const cv::Mat& image,
        const std::string& templateJson,
        const std::string& answerKeyJson = ""
    );

    /**
     * Direct processing with pre-parsed configs (for JNI efficiency).
     * @param templateJson  Original template JSON string (for layout cache key)
     */
    OmrResult processWithConfig(
        const cv::Mat& image,
        const std::vector<AnchorPoint>& anchorPoints,
        const IdZoneConfig& idZone,
        const std::vector<AnswerZoneConfig>& answerZones,
        const std::map<std::string, std::map<int, std::string>>& answerKeys,
        const std::string& templateJson
    );

    /**
     * Serialize OmrResult to JSON string.
     */
    static std::string resultToJson(const OmrResult& result);

    // ─── Layout Cache ─────────────────────────────────────
    /**
     * Compute a hash from template JSON for cache lookup.
     * Same template JSON → same hash → reused cell positions.
     */
    static uint64_t templateHash(const std::string& templateJson);

    /**
     * Get a cached layout entry, or nullptr if not found.
     */
    static const LayoutCacheEntry* getCachedLayout(uint64_t hash);

    /**
     * Store a layout entry in the cache.
     * Up to MAX_CACHE_ENTRIES entries; oldest is evicted on overflow.
     */
    static void putCachedLayout(uint64_t hash, const LayoutCacheEntry& entry);

    /**
     * Clear all cached layouts (e.g., on template update).
     */
    static void clearLayoutCache();

private:
    OmrOptions options_;

    // ─── Adaptive threshold preprocessed image ────────────
    // When use_adaptive_threshold is enabled, this holds the
    // binarized warped image (0=dark/bubble, 255=background).
    // Empty otherwise; readBubbleDensity checks this first.
    cv::Mat warpedBinary_;

    // ─── Parsing ─────────────────────────────────────────────
    bool parseTemplateJson(
        const std::string& json,
        std::vector<AnchorPoint>& anchors,
        IdZoneConfig& idZone,
        std::vector<AnswerZoneConfig>& answerZones
    );

    bool parseAnswerKeyJson(
        const std::string& json,
        std::map<std::string, std::map<int, std::string>>& answerKeys
    );

    // ─── Alignment ───────────────────────────────────────────
    cv::Mat alignImage(
        const cv::Mat& image,
        const std::vector<DetectedMarker>& markers,
        const std::vector<AnchorPoint>& anchors,
        OmrResult& result
    );

    // ─── Preprocessing ─────────────────────────────────────
    /**
     * Auto-configure all preprocessing flags based on image quality.
     * Call this AFTER quality check, BEFORE marker detection.
     *
     * Decision matrix:
     *   Laplacian > 200 & Brightness > 150 → nothing (good scan)
     *   Laplacian < 50  → full preprocessing (very blurry)
     *   Brightness < 80 → adaptive(large block) + morph (very dark)
     *   Laplacian < 80  → preMarker + adaptive (blurry)
     *   Brightness < 100 → adaptive + morph (low light)
     *   Otherwise       → adaptive only (safe default for camera)
     */
    void autoConfigurePipeline(float laplacianVar, float meanBrightness);

    /**
     * Bilateral filter + CLAHE on the grayscale input image.
     * Improves marker detection on wrinkled / low-contrast paper.
     * Matches Python pipeline steps B1.5 + B2.
     */
    cv::Mat preprocessForMarkers(const cv::Mat& gray);

    /**
     * Bilateral + GaussianBlur on the warped image.
     * Reduces paper texture noise before thresholding.
     * Matches Python pipeline steps B5 + B6.
     */
    cv::Mat preprocessPostWarp(const cv::Mat& warpedGray);

    /**
     * Morphological close → open → Otsu on the binary image.
     * Cleans noise from paper folds/creases.
     * Matches Python pipeline steps B7-B10.
     */
    void applyMorphCleanup(cv::Mat& binary);

    // ─── ID Reading ──────────────────────────────────────────
    IdReadResult readIdZone(
        const cv::Mat& warped,
        const IdZoneConfig& idZone
    );

    /** Overload that uses pre-cached cell positions (skips layout computation). */
    IdReadResult readIdZoneCached(
        const cv::Mat& warped,
        const IdZoneConfig& idZone,
        const std::vector<BubbleCell>& cells
    );

    // ─── Answer Reading ──────────────────────────────────────
    std::vector<AnswerReadResult> readAnswerZones(
        const cv::Mat& warped,
        const std::vector<AnswerZoneConfig>& answerZones
    );

    /** Overload that uses pre-cached cell positions. */
    std::vector<AnswerReadResult> readAnswerZonesCached(
        const cv::Mat& warped,
        const std::vector<AnswerZoneConfig>& answerZones,
        const std::map<std::string, std::vector<BubbleCell>>& cachedCells
    );

    // ─── Bubble Reading ──────────────────────────────────────
    float readBubbleDensity(
        const cv::Mat& grayImg,
        float cx, float cy, float radius
    );

    cv::Point2f refineBubbleCenter(
        const cv::Mat& grayImg,
        float cx, float cy, float radius
    );

    int readBubbleColumn(
        const cv::Mat& grayImg,
        const std::vector<float>& cyList,
        float cx, float radius,
        float& top1Density, float& top2Density
    );

    // ─── Scoring ─────────────────────────────────────────────
    ScoreResult scoreAnswers(
        const std::vector<AnswerReadResult>& answers,
        const std::map<int, std::string>& correctAnswers,
        const std::string& selectionMode
    );

    bool checkAnswerMatch(
        const std::string& studentAnswer,
        const std::string& correctAnswer,
        const std::string& selectionMode
    );

    // ─── Debug Image ─────────────────────────────────────────
    /**
     * Generate visual debug overlay.
     *
     * With scoring (correctAnswers != nullptr):
     *   GREEN  = student selected the CORRECT answer
     *   RED    = student selected a WRONG answer
     *   YELLOW = correct answer the student MISSED, OR student answer flagged ambiguous
     *
     * Without scoring (correctAnswers == nullptr):
     *   BLUE   = all bubbles the student filled in (density >= threshold)
     */
    cv::Mat generateDebugImage(
        const cv::Mat& warped,
        const IdZoneConfig& idZone,
        const std::vector<AnswerZoneConfig>& answerZones,
        const IdReadResult& idResult,
        const std::vector<AnswerReadResult>& answers,
        const ScoreResult* score,
        const std::map<int, std::string>* correctAnswers
    );

    void drawBubbleCell(
        cv::Mat& img,
        float cx, float cy, float radius,
        const cv::Scalar& color,
        int thickness = 1
    );

    // ─── JSON serialization helpers ──────────────────────────
    static std::string escapeJson(const std::string& s);
    static std::string idResultToJson(const IdReadResult& r);
    static std::string answersToJson(const std::vector<AnswerReadResult>& answers);
    static std::string scoreToJson(const ScoreResult& s);

    // ─── Layout Cache (static, shared across instances) ──────
    static std::unordered_map<uint64_t, LayoutCacheEntry> s_layoutCache_;
    static std::mutex s_cacheMutex_;
    static std::vector<uint64_t> s_cacheOrder_;  // LRU tracking
};

} // namespace omr

#endif // OMR_PROCESSOR_H
