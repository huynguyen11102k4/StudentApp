#ifndef LAYOUT_CALCULATOR_H
#define LAYOUT_CALCULATOR_H

#include "omr_constants.h"
#include <vector>
#include <string>
#include <opencv2/core.hpp>

namespace omr {

/**
 * LayoutCalculator — Computes bubble grid positions for ID zones and answer zones
 * per the OMR template specification (Sections 2.3, 2.4, §3).
 */
class LayoutCalculator {
public:
    // ─── calculateGridLayout (§3 of spec) ───────────────────
    /**
     * Calculate column spacing and centering offset for a uniform grid.
     *
     * @param totalWidth   Available width after padding
     * @param numColumns   Number of columns (bubbles per row)
     * @param bubbleSize   Diameter of each bubble
     * @param maxSpacing   Maximum allowed center-to-center spacing
     * @param minSpacing   Minimum allowed center-to-center spacing
     * @return GridLayoutResult with spacing, offset, ok flag, and optional error
     */
    static GridLayoutResult calculateGridLayout(
        float totalWidth,
        int numColumns,
        float bubbleSize,
        float maxSpacing = MAX_SPACING,
        float minSpacing = MIN_SPACING
    );

    /**
     * Generate column center X positions using calculateGridLayout.
     */
    static std::vector<float> generateColumnPositions(
        float startX,
        float totalWidth,
        int numColumns,
        float bubbleSize
    );

    // ─── ID Zone Layout (§2.3) ──────────────────────────────
    /**
     * Compute all bubble cell positions for the ID zone.
     *
     * @param zone      ID zone configuration (bounding_box + items)
     * @param outCells  Output vector of BubbleCell with centers, zone_id, row, col
     * @return true if layout computed successfully
     */
    static bool computeIdZoneLayout(
        const IdZoneConfig& zone,
        std::vector<BubbleCell>& outCells
    );

    // ─── Answer Zone Layout (§2.4) ──────────────────────────
    /**
     * Compute all bubble cell positions for an answer zone.
     *
     * @param zone      Answer zone configuration
     * @param outCells  Output vector of BubbleCell
     * @return true if layout computed successfully
     */
    static bool computeAnswerZoneLayout(
        const AnswerZoneConfig& zone,
        std::vector<BubbleCell>& outCells
    );

private:
    /**
     * Compute effective area with padding from bounding box.
     */
    static void computeEffectiveArea(
        const cv::Rect2f& bbox,
        float& effX, float& effY,
        float& effW, float& effH
    );
};

} // namespace omr

#endif // LAYOUT_CALCULATOR_H
