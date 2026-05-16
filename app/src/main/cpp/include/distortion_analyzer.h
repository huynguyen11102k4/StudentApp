#ifndef DISTORTION_ANALYZER_H
#define DISTORTION_ANALYZER_H

#include <opencv2/opencv.hpp>
#include <vector>
#include <map>
#include <string>

namespace omr {

enum class DistortionSeverity {
    GOOD,
    MODERATE,
    SEVERE
};

struct DistortionAnalysis {
    double rmsError;
    double maxDeviation;
    int numMarkers;
    DistortionSeverity severity;
    std::string recommendation;
    
    // Per-marker deviations
    std::map<int, double> markerDeviations;
};

class DistortionAnalyzer {
public:
    static DistortionAnalysis analyzeDistortion(
        const std::map<int, cv::Point2f>& detectedMarkers,
        const std::map<int, cv::Point2f>& expectedMarkers
    );

    static double calculateRMSError(
        const std::map<int, cv::Point2f>& detectedMarkers,
        const std::map<int, cv::Point2f>& expectedMarkers
    );

    static double calculateMaxDeviation(
        const std::map<int, cv::Point2f>& detectedMarkers,
        const std::map<int, cv::Point2f>& expectedMarkers
    );

    static DistortionSeverity classifySeverity(double rmsError);

    static std::string getRecommendation(DistortionSeverity severity);

    static void printDistortionReport(const DistortionAnalysis& analysis);
};

}

#endif
