#include "include/distortion_analyzer.h"
#include <cmath>
#include <algorithm>
#include <iostream>
#include <iomanip>

namespace omr {

DistortionAnalysis DistortionAnalyzer::analyzeDistortion(
    const std::map<int, cv::Point2f>& detectedMarkers,
    const std::map<int, cv::Point2f>& expectedMarkers
) {
    DistortionAnalysis analysis;

    analysis.rmsError = calculateRMSError(detectedMarkers, expectedMarkers);
    
    analysis.maxDeviation = calculateMaxDeviation(detectedMarkers, expectedMarkers);
    
    analysis.numMarkers = static_cast<int>(detectedMarkers.size());
    
    for (const auto& pair : detectedMarkers) {
        int markerId = pair.first;
        cv::Point2f detected = pair.second;
        
        auto it = expectedMarkers.find(markerId);
        if (it != expectedMarkers.end()) {
            cv::Point2f expected = it->second;
            double dx = detected.x - expected.x;
            double dy = detected.y - expected.y;
            double deviation = std::sqrt(dx * dx + dy * dy);
            
            analysis.markerDeviations[markerId] = deviation;
        }
    }
    
    analysis.severity = classifySeverity(analysis.rmsError);
    
    analysis.recommendation = getRecommendation(analysis.severity);
    
    return analysis;
}

double DistortionAnalyzer::calculateRMSError(
    const std::map<int, cv::Point2f>& detectedMarkers,
    const std::map<int, cv::Point2f>& expectedMarkers
) {
    double sumSquaredError = 0.0;
    int count = 0;
    
    for (const auto& pair : detectedMarkers) {
        int markerId = pair.first;
        cv::Point2f detected = pair.second;
        
        auto it = expectedMarkers.find(markerId);
        if (it != expectedMarkers.end()) {
            cv::Point2f expected = it->second;
            double dx = detected.x - expected.x;
            double dy = detected.y - expected.y;
            double squaredError = dx * dx + dy * dy;
            
            sumSquaredError += squaredError;
            count++;
        }
    }
    
    if (count == 0) return 0.0;
    
    return std::sqrt(sumSquaredError / count);
}

double DistortionAnalyzer::calculateMaxDeviation(
    const std::map<int, cv::Point2f>& detectedMarkers,
    const std::map<int, cv::Point2f>& expectedMarkers
) {
    double maxDev = 0.0;
    
    for (const auto& pair : detectedMarkers) {
        int markerId = pair.first;
        cv::Point2f detected = pair.second;
        
        auto it = expectedMarkers.find(markerId);
        if (it != expectedMarkers.end()) {
            cv::Point2f expected = it->second;
            double dx = detected.x - expected.x;
            double dy = detected.y - expected.y;
            double deviation = std::sqrt(dx * dx + dy * dy);
            
            maxDev = std::max(maxDev, deviation);
        }
    }
    
    return maxDev;
}

DistortionSeverity DistortionAnalyzer::classifySeverity(double rmsError) {
    if (rmsError < 5.0) {
        return DistortionSeverity::GOOD;
    } else if (rmsError < 20.0) {
        return DistortionSeverity::MODERATE;
    } else {
        return DistortionSeverity::SEVERE;
    }
}

std::string DistortionAnalyzer::getRecommendation(DistortionSeverity severity) {
    switch (severity) {
        case DistortionSeverity::GOOD:
            return "NONE (Perspective transform sufficient)";
        case DistortionSeverity::MODERATE:
            return "HYBRID or MESH (Fast, sufficient quality)";
        case DistortionSeverity::SEVERE:
            return "MESH or TPS (High quality needed)";
        default:
            return "UNKNOWN";
    }
}

void DistortionAnalyzer::printDistortionReport(const DistortionAnalysis& analysis) {
    std::cout << "\n========== DISTORTION ANALYSIS REPORT ==========" << std::endl;
    std::cout << "Number of markers: " << analysis.numMarkers << std::endl;
    std::cout << "RMS Error: " << std::fixed << std::setprecision(2) 
              << analysis.rmsError << " pixels" << std::endl;
    std::cout << "Max Deviation: " << analysis.maxDeviation << " pixels" << std::endl;
    
    std::string severityStr;
    switch (analysis.severity) {
        case DistortionSeverity::GOOD:
            severityStr = "GOOD";
            break;
        case DistortionSeverity::MODERATE:
            severityStr = "MODERATE";
            break;
        case DistortionSeverity::SEVERE:
            severityStr = "SEVERE";
            break;
    }
    std::cout << "Severity: " << severityStr << std::endl;
    std::cout << "Recommendation: " << analysis.recommendation << std::endl;
    
    std::cout << "\nPer-marker deviations:" << std::endl;
    for (const auto& pair : analysis.markerDeviations) {
        std::cout << "  Marker " << pair.first << ": " 
                  << std::fixed << std::setprecision(2) << pair.second 
                  << " pixels" << std::endl;
    }
    std::cout << "================================================\n" << std::endl;
}

}
