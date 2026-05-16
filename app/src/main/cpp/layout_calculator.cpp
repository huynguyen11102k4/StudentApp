#include "include/layout_calculator.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <android/log.h>

#define LOG_TAG "LayoutCalc"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace omr {

GridLayoutResult LayoutCalculator::calculateGridLayout(
    float totalWidth,
    int numColumns,
    float bubbleSize,
    float maxSpacing,
    float minSpacing
) {
    if (numColumns <= 0) {
        return { 0.0f, 0.0f, false, "numColumns must be >= 1" };
    }
    if (totalWidth <= 0.0f) {
        return { 0.0f, 0.0f, false, "totalWidth must be > 0" };
    }

    if (numColumns == 1) {
        float offset = (totalWidth - bubbleSize) / 2.0f;
        return { 0.0f, std::max(0.0f, offset), offset >= -0.01f, "" };
    }

    float effectiveMin = bubbleSize + minSpacing;
    float spacing = (totalWidth - bubbleSize) / static_cast<float>(numColumns - 1);
    spacing = std::max(effectiveMin, std::min(maxSpacing, spacing));

    float occupiedWidth = static_cast<float>(numColumns - 1) * spacing + bubbleSize;
    if (occupiedWidth > totalWidth + 0.01f) {
        char buf[160];
        std::snprintf(
            buf,
            sizeof(buf),
            "Not enough space: need %.0fpx but only %.0fpx available.",
            std::ceil(occupiedWidth),
            std::ceil(totalWidth)
        );
        return { 0.0f, 0.0f, false, std::string(buf) };
    }

    float offset = (totalWidth - occupiedWidth) / 2.0f;
    return { spacing, std::max(0.0f, offset), true, "" };
}

std::vector<float> LayoutCalculator::generateColumnPositions(
    float startX,
    float totalWidth,
    int numColumns,
    float bubbleSize
) {
    GridLayoutResult layout = calculateGridLayout(totalWidth, numColumns, bubbleSize);
    if (!layout.ok) {
        return {};
    }

    std::vector<float> positions;
    positions.reserve(numColumns);
    for (int col = 0; col < numColumns; col++) {
        float cx = startX + layout.offset + static_cast<float>(col) * layout.spacing + bubbleSize / 2.0f;
        positions.push_back(cx);
    }
    return positions;
}

void LayoutCalculator::computeEffectiveArea(
    const cv::Rect2f& bbox,
    float& effX,
    float& effY,
    float& effW,
    float& effH
) {
    float pad = std::min({ PADDING, bbox.width / 8.0f, bbox.height / 8.0f });
    pad = std::max(0.0f, pad);
    effX = bbox.x + pad;
    effY = bbox.y + pad;
    effW = bbox.width - 2.0f * pad;
    effH = bbox.height - 2.0f * pad;
}

bool LayoutCalculator::computeIdZoneLayout(
    const IdZoneConfig& zone,
    std::vector<BubbleCell>& outCells
) {
    outCells.clear();

    float effX, effY, effW, effH;
    computeEffectiveArea(zone.bounding_box, effX, effY, effW, effH);

    int totalCols = 0;
    for (const auto& item : zone.items) {
        if (item.enabled) {
            totalCols += item.num_digits;
        }
    }

    if (totalCols <= 0) {
        LOGE("ID Zone: no enabled items");
        return false;
    }

    float estimatedOverhead = ID_LABEL_ROW_H
        + ID_LABEL_GAP
        + (BUBBLE_SIZE_DEFAULT + ID_WRITE_BOX_PAD)
        + ID_WRITE_BOX_GAP;
    float estimatedGridH = std::max(1.0f, effH - estimatedOverhead);

    float rawDx = effW / static_cast<float>(totalCols);
    float rawDy = estimatedGridH / static_cast<float>(ID_NUM_ROWS);
    float bubbleSize = std::min(std::min(rawDx, rawDy) * BUBBLE_RATIO, BUBBLE_SIZE_MAX);

    if (bubbleSize < BUBBLE_SIZE_MIN) {
        LOGE("ID Zone: bubbleSize %.2f is below minimum %.2f", bubbleSize, BUBBLE_SIZE_MIN);
        return false;
    }

    GridLayoutResult prelimLayout = calculateGridLayout(effW, totalCols, bubbleSize);
    float prelimSpacing = (prelimLayout.ok && prelimLayout.spacing > 0.0f)
        ? prelimLayout.spacing
        : bubbleSize;
    float rowNumColW = std::min(prelimSpacing, effW * 0.15f);
    float rowNumGap = ID_LEFT_NUM_GAP;

    float bubbleGridWidth = effW - rowNumColW - rowNumGap;
    if (bubbleGridWidth <= 0.0f) {
        LOGE("ID Zone: not enough width");
        return false;
    }
    int enabledItemCount = 0;
    for (const auto& item : zone.items) {
        if (item.enabled) {
            enabledItemCount++;
        }
    }
    float groupGap = enabledItemCount > 1 ? ID_GROUP_GAP : 0.0f;
    float totalGroupGap = std::max(0, enabledItemCount - 1) * groupGap;
    float columnGridWidth = bubbleGridWidth - totalGroupGap;
    if (columnGridWidth <= 0.0f) {
        LOGE("ID Zone: not enough width after group gaps");
        return false;
    }
    float bubbleGridX = effX + rowNumColW + rowNumGap;

    float writeBoxRowH = bubbleSize + ID_WRITE_BOX_PAD;
    float topOverhead = ID_LABEL_ROW_H + ID_LABEL_GAP + writeBoxRowH + ID_WRITE_BOX_GAP;
    float bubbleGridHeight = effH - topOverhead;
    if (bubbleGridHeight <= 0.0f) {
        LOGE("ID Zone: not enough height");
        return false;
    }
    float bubbleGridY = effY + topOverhead;

    GridLayoutResult hLayout = calculateGridLayout(columnGridWidth, totalCols, bubbleSize);
    GridLayoutResult vLayout = calculateGridLayout(bubbleGridHeight, ID_NUM_ROWS, bubbleSize);

    float colSpacing = (hLayout.ok && hLayout.spacing > 0.0f)
        ? hLayout.spacing
        : columnGridWidth / static_cast<float>(totalCols);
    float colOffset = (hLayout.ok && hLayout.spacing > 0.0f) ? hLayout.offset : 0.0f;
    float rowSpacing = (vLayout.ok && vLayout.spacing > 0.0f)
        ? vLayout.spacing
        : bubbleGridHeight / static_cast<float>(ID_NUM_ROWS);
    float rowOffset = (vLayout.ok && vLayout.spacing > 0.0f) ? vLayout.offset : 0.0f;

    LOGI(
        "ID Zone grid: totalCols=%d groups=%d groupGap=%.2f bubble=%.2f grid=(%.2f,%.2f,%.2f,%.2f) columnGridWidth=%.2f colSpacing=%.2f rowSpacing=%.2f colOffset=%.2f rowOffset=%.2f layout=backend-contract",
        totalCols,
        enabledItemCount,
        groupGap,
        bubbleSize,
        bubbleGridX,
        bubbleGridY,
        bubbleGridWidth,
        bubbleGridHeight,
        columnGridWidth,
        colSpacing,
        rowSpacing,
        colOffset,
        rowOffset
    );

    int colIndex = 0;
    int itemIndex = 0;
    for (const auto& item : zone.items) {
        if (!item.enabled) {
            continue;
        }

        float groupOffsetX = static_cast<float>(itemIndex) * groupGap;
        for (int d = 0; d < item.num_digits; d++) {
            float cx = bubbleGridX
                + colOffset
                + groupOffsetX
                + static_cast<float>(colIndex) * colSpacing
                + bubbleSize / 2.0f;

            for (int r = 0; r < ID_NUM_ROWS; r++) {
                float cy = bubbleGridY
                    + rowOffset
                    + static_cast<float>(r) * rowSpacing
                    + bubbleSize / 2.0f;

                BubbleCell cell;
                cell.zone_id = "id_" + item.type;
                cell.row = r;
                cell.col = colIndex;
                cell.cx = cx;
                cell.cy = cy;
                cell.radius = bubbleSize / 2.0f;
                cell.question_index = -1;
                cell.choice_letter = '\0';
                cell.is_number_cell = false;
                outCells.push_back(cell);
            }
            colIndex++;
        }
        itemIndex++;
    }

    LOGI("ID Zone: generated %zu bubble cells", outCells.size());
    return true;
}

bool LayoutCalculator::computeAnswerZoneLayout(
    const AnswerZoneConfig& zone,
    std::vector<BubbleCell>& outCells
) {
    outCells.clear();

    int rows = zone.rows;
    int options = zone.options;
    bool hasStart = zone.has_start_number;
    bool hasEnd = zone.has_end_number;

    if (rows <= 0 || options <= 0) {
        LOGE("AnswerZone [%s]: invalid rows=%d options=%d", zone.zone_id.c_str(), rows, options);
        return false;
    }

    float innerX, innerY, innerW, innerH;
    computeEffectiveArea(zone.bounding_box, innerX, innerY, innerW, innerH);

    int numberColumnCount = (hasStart ? 1 : 0) + (hasEnd ? 1 : 0);
    float preferredNumberWidth = std::max(
        ANSWER_NUMBER_COL_MIN,
        std::min(ANSWER_NUMBER_COL_MAX, innerW * ANSWER_NUMBER_COL_RATIO)
    );
    float totalNumberWidth = numberColumnCount > 0
        ? std::min(preferredNumberWidth * static_cast<float>(numberColumnCount), innerW * ANSWER_NUMBER_MAX_RATIO)
        : 0.0f;
    float numberColumnWidth = numberColumnCount > 0
        ? totalNumberWidth / static_cast<float>(numberColumnCount)
        : 0.0f;

    float startNumberWidth = hasStart ? numberColumnWidth : 0.0f;
    float endNumberWidth = hasEnd ? numberColumnWidth : 0.0f;

    float choiceAreaX = innerX + startNumberWidth;
    float choiceAreaW = innerW - startNumberWidth - endNumberWidth;
    float choiceStep = choiceAreaW / static_cast<float>(options);
    float rowStep = innerH / static_cast<float>(rows);

    float bubbleSize = std::min(choiceStep, rowStep) * BUBBLE_RATIO;
    bubbleSize = std::max(4.0f, std::min(BUBBLE_SIZE_MAX, bubbleSize));

    LOGI(
        "AnswerZone [%s]: rows=%d options=%d bubble=%.2f choiceStep=%.2f rowStep=%.2f layout=template-bank-even",
        zone.zone_id.c_str(),
        rows,
        options,
        bubbleSize,
        choiceStep,
        rowStep
    );

    for (int row = 0; row < rows; row++) {
        int questionIndex = zone.start_question_index + row;
        float y = innerY + (static_cast<float>(row) + 0.5f) * rowStep;

        if (hasStart) {
            BubbleCell numberCell;
            numberCell.zone_id = zone.zone_id;
            numberCell.row = row;
            numberCell.col = -1;
            numberCell.cx = innerX + startNumberWidth / 2.0f;
            numberCell.cy = y;
            numberCell.radius = bubbleSize / 2.0f;
            numberCell.question_index = questionIndex;
            numberCell.choice_letter = '\0';
            numberCell.is_number_cell = true;
            outCells.push_back(numberCell);
        }

        for (int option = 0; option < options; option++) {
            float x = choiceAreaX
                + (static_cast<float>(option) + 0.5f) * choiceStep;

            BubbleCell cell;
            cell.zone_id = zone.zone_id;
            cell.row = row;
            cell.col = option;
            cell.cx = x;
            cell.cy = y;
            cell.radius = bubbleSize / 2.0f;
            cell.question_index = questionIndex;
            cell.choice_letter = static_cast<char>('A' + option);
            cell.is_number_cell = false;
            outCells.push_back(cell);
        }

        if (hasEnd) {
            BubbleCell numberCell;
            numberCell.zone_id = zone.zone_id;
            numberCell.row = row;
            numberCell.col = options;
            numberCell.cx = choiceAreaX + choiceAreaW + endNumberWidth / 2.0f;
            numberCell.cy = y;
            numberCell.radius = bubbleSize / 2.0f;
            numberCell.question_index = questionIndex;
            numberCell.choice_letter = '\0';
            numberCell.is_number_cell = true;
            outCells.push_back(numberCell);
        }
    }

    LOGI("AnswerZone [%s]: generated %zu cells", zone.zone_id.c_str(), outCells.size());
    return true;
}

} // namespace omr
