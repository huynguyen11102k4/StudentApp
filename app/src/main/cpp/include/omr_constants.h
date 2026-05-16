#ifndef OMR_CONSTANTS_H
#define OMR_CONSTANTS_H

#include <cstdint>
#include <vector>
#include <string>
#include <opencv2/core.hpp>

namespace omr {

// ─── PAGE DIMENSIONS ────────────────────────────────────────
constexpr float PAGE_WIDTH  = 827.0f;
constexpr float PAGE_HEIGHT = 1169.0f;
constexpr float PADDING     = 5.0f;

// ─── MARKER ─────────────────────────────────────────────────
constexpr float MARKER_SIZE = 50.0f;

// ─── BUBBLE ─────────────────────────────────────────────────
constexpr float BUBBLE_SIZE_MIN     = 15.0f;
constexpr float BUBBLE_SIZE_MAX     = 25.0f;
constexpr float BUBBLE_SIZE_DEFAULT = 18.0f;
constexpr float BUBBLE_RATIO        = 0.8f;

// ─── SPACING ────────────────────────────────────────────────
constexpr float MAX_SPACING = 40.0f;
constexpr float MIN_SPACING = 4.0f;

// ─── ID ZONE OVERHEAD ───────────────────────────────────────
// label row (16) + gap (2) + write-box row (bubbleSize+4) + gap (3)
constexpr float ID_LABEL_ROW_H     = 16.0f;
constexpr float ID_LABEL_GAP       = 2.0f;
constexpr float ID_WRITE_BOX_PAD   = 4.0f;
constexpr float ID_WRITE_BOX_GAP   = 3.0f;
constexpr float ID_LEFT_NUM_GAP    = 4.0f;
constexpr float ID_GROUP_GAP       = 12.0f;
constexpr int   ID_NUM_ROWS        = 10;

// ─── ANSWER ZONE ─────────────────────────────────────────────
constexpr float ANSWER_NUMBER_COL_RATIO = 0.12f;
constexpr float ANSWER_NUMBER_COL_MIN   = 14.0f;
constexpr float ANSWER_NUMBER_COL_MAX   = 30.0f;
constexpr float ANSWER_NUMBER_MAX_RATIO = 0.32f;

// ─── READING THRESHOLDS ─────────────────────────────────────
constexpr float DENSITY_THRESHOLD = 0.40f;
constexpr float DIFF_THRESHOLD    = 0.20f;  // Flag ambiguity
constexpr float BUBBLE_READ_CORE_RATIO = 0.58f;  // Ignore printed ring / warp seams
constexpr int   BUBBLE_READ_MIN_CORE_RADIUS = 3;

// ─── ADAPTIVE THRESHOLD ─────────────────────────────────────
// When use_adaptive_threshold = true, the warped grayscale image
// is converted to binary via cv::adaptiveThreshold before
// bubble density reading. This handles uneven lighting better
// than the fixed 128 grayscale cutoff.
constexpr int   ADAPTIVE_BLOCK_SIZE = 31;    // Must be odd; larger → more stable in low-light
constexpr float ADAPTIVE_C          = 8.0f;  // Subtracted from mean; higher → less sensitive

// ─── PRE-MARKER PREPROCESSING ───────────────────────────────
// Bilateral filter + CLAHE before marker detection.
// Matches Python pipeline steps B1.5 + B2.
// Significantly improves marker detection on wrinkled / low-contrast paper.
constexpr int   PRE_BILATERAL_D           = 9;
constexpr float PRE_BILATERAL_SIGMA_COLOR = 25.0f;
constexpr float PRE_BILATERAL_SIGMA_SPACE = 75.0f;
constexpr float PRE_CLAHE_CLIP_LIMIT      = 4.0f;
constexpr int   PRE_CLAHE_TILE_SIZE       = 4;

// ─── POST-WARP PREPROCESSING ────────────────────────────────
// Bilateral filter + Gaussian blur after dewarp, before reading.
// Matches Python pipeline steps B5 + B6.
// Reduces noise and smooths paper texture for cleaner thresholding.
constexpr int   POST_BILATERAL_D           = 5;
constexpr float POST_BILATERAL_SIGMA_COLOR = 50.0f;
constexpr float POST_BILATERAL_SIGMA_SPACE = 50.0f;
constexpr int   POST_GAUSSIAN_KSIZE        = 3;

// ─── MORPHOLOGICAL CLEANUP ──────────────────────────────────
// Applied after adaptive threshold to remove noise from paper
// folds/creases. Matches Python pipeline steps B7-B9.
constexpr int   MORPH_CLOSE_KSIZE     = 3;
constexpr int   MORPH_OPEN_KSIZE      = 3;
constexpr int   MORPH_FINAL_KSIZE     = 2;

// ─── DEWARP THRESHOLDS ──────────────────────────────────────
constexpr float DEWARP_GOOD_PX      = 15.0f;  // Perspective only
constexpr float DEWARP_MODERATE_PX  = 25.0f;  // Hybrid
// > MODERATE_PX → TPS or Mesh

// ─── QUALITY ─────────────────────────────────────────────────
constexpr float LAPLACIAN_BLUR_THRESH = 80.0f;
constexpr float BRIGHTNESS_MIN        = 100.0f;

// ─── AUTO-ADAPTIVE THRESHOLDS ──────────────────────────────
// Used by autoConfigurePipeline() to decide preprocessing
constexpr float LAPLACIAN_GOOD     = 200.0f;  // Above this: scan quality, no preprocessing needed
constexpr float BRIGHTNESS_GOOD    = 150.0f;  // Above this: well-lit
constexpr float LAPLACIAN_VERY_BLUR = 50.0f;  // Below this: very blurry, full preprocessing
constexpr float BRIGHTNESS_VERY_DARK = 80.0f; // Below this: very dark, needs large adaptive block
constexpr int   ADAPTIVE_BLOCK_LARGE = 51;    // Larger block for low-quality images

// ─── DEBUG IMAGE ────────────────────────────────────────────
constexpr int MAX_DEBUG_DIMENSION = 1200;  // Max width or height; larger dimensions are resized down
constexpr int DEBUG_JPEG_QUALITY  = 75;    // JPEG quality for debug image encoding

// ─── LAYOUT CACHE ───────────────────────────────────────────
constexpr size_t MAX_CACHE_ENTRIES = 8;    // Max number of cached templates

// ─── STRUCTURES ──────────────────────────────────────────────

struct AnchorPoint {
    int id;
    float abs_x;
    float abs_y;
    float u;
    float v;
};

struct IdZoneItem {
    std::string type;       // "student_id" | "class_code" | "exam_code"
    bool enabled;
    int num_digits;
};

struct IdZoneConfig {
    cv::Rect2f bounding_box;
    std::vector<IdZoneItem> items;
};

struct AnswerZoneConfig {
    std::string zone_id;
    std::string type;       // "multiple_choice_grid"
    cv::Rect2f bounding_box;
    int rows;
    int options;
    bool has_start_number;
    bool has_end_number;
    int start_question_index;
    std::string selection_mode;  // "single" (default) | "multiple"
};

struct GridLayoutResult {
    float spacing;
    float offset;
    bool ok;
    std::string error;
};

struct BubbleCell {
    int row;
    int col;
    float cx;
    float cy;
    float radius;
    std::string zone_id;
    // For answer zones
    int question_index;
    char choice_letter;    // 'A'-'F'
    bool is_number_cell;   // for question number cells
};

struct OmrOptions {
    bool enable_scoring       = false;
    bool enable_debug_image   = false;
    float density_threshold   = DENSITY_THRESHOLD;
    float diff_threshold      = DIFF_THRESHOLD;
    bool return_debug_base64  = false;

    // ─── Adaptive threshold ────────────────────────────────
    // When true: use cv::adaptiveThreshold to binarize the
    // warped image before reading bubble density. Recommended
    // for real-world camera images with uneven lighting.
    // When false: use fixed grayscale cutoff at 128 (faster).
    bool use_adaptive_threshold = false;

    // Block size for adaptive threshold (must be odd, 3-255).
    // Larger blocks average over more area → better for
    // low-contrast images, but may miss small bubbles.
    int adaptive_block_size     = ADAPTIVE_BLOCK_SIZE;

    // Constant subtracted from the block mean.
    // Higher values → fewer pixels classified as "dark".
    float adaptive_c            = ADAPTIVE_C;

    // ─── Preprocessing pipeline ────────────────────────────
    // These match the proven Python pipeline steps:
    //   pre_marker: Bilateral + CLAHE → better marker detection
    //   post_warp:  Bilateral + GaussianBlur → reduce noise
    //   morph_cleanup: Close + Open + Otsu → clean binary

    /** Bilateral + CLAHE on input image before marker detection. */
    bool preprocess_markers     = false;

    /** Bilateral + GaussianBlur on warped image before reading. */
    bool preprocess_post_warp   = false;

    /** Morphological close+open+Otsu after adaptive threshold. */
    bool morph_cleanup          = false;

    // ─── Auto-adaptive mode ───────────────────────────────
    /** When true: app auto-decides all preprocessing flags
     *  based on laplacian variance and mean brightness.
     *  Teacher just shoots — no manual tuning needed.
     *  Overrides preprocess_markers/post_warp/morph_cleanup
     *  and use_adaptive_threshold. */
    bool auto_adaptive = false;
};

struct IdReadResult {
    std::string student_id;
    std::string class_code;
    std::string exam_code;
    bool id_ok;
    std::string id_error;
};

struct AnswerReadResult {
    int question_number;
    std::string answer;       // "A", "C", "A,C" (multiple), "" or "X" if none
    int flag;                 // 0=OK, 1=ambiguous/erasure
};

struct ScoringDetail {
    int question_number;
    std::string student_answer;
    std::string correct_answer;
    bool is_correct;
    int flag;                 // from answer result
};

struct ScoreResult {
    int total_questions;
    int correct_count;
    double total_score;
    std::vector<ScoringDetail> details;
};

struct OmrResult {
    bool success;
    std::string error_code;
    std::string error_message;
    std::vector<std::string> warnings;

    // ID
    IdReadResult id_result;

    // Answers
    std::vector<AnswerReadResult> answers;

    // Scoring (if enabled)
    bool scored;
    ScoreResult score_result;

    // Debug image
    std::string debug_image_base64;

    // Quality
    float laplacian_variance;
    float mean_brightness;
};

// ─── DEFAULT ANCHOR POINTS (12 markers, matching template.constants.ts) ───
inline const std::vector<AnchorPoint>& getDefaultAnchorPoints() {
    static const std::vector<AnchorPoint> points = {
        { 1,  45,  45,   45/PAGE_WIDTH,   45/PAGE_HEIGHT },
        { 2,  782, 45,   782/PAGE_WIDTH,  45/PAGE_HEIGHT },
        { 3,  782, 1124, 782/PAGE_WIDTH,  1124/PAGE_HEIGHT },
        { 4,  45,  1124, 45/PAGE_WIDTH,   1124/PAGE_HEIGHT },
        { 5,  45,  403,  45/PAGE_WIDTH,   403/PAGE_HEIGHT },
        { 6,  414, 403,  414/PAGE_WIDTH,  403/PAGE_HEIGHT },
        { 7,  782, 403,  782/PAGE_WIDTH,  403/PAGE_HEIGHT },
        { 8,  782, 763,  782/PAGE_WIDTH,  763/PAGE_HEIGHT },
        { 9,  414, 763,  414/PAGE_WIDTH,  763/PAGE_HEIGHT },
        { 10, 45,  763,  45/PAGE_WIDTH,   763/PAGE_HEIGHT },
        { 11, 414, 1124, 414/PAGE_WIDTH,  1124/PAGE_HEIGHT },
        { 12, 414, 45,   414/PAGE_WIDTH,  45/PAGE_HEIGHT },
    };
    return points;
}

} // namespace omr

#endif // OMR_CONSTANTS_H
