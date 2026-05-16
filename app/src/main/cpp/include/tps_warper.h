#ifndef TPS_WARPER_H
#define TPS_WARPER_H

#include <opencv2/opencv.hpp>
#include <vector>

namespace omr {

class TpsWarper {
public:
    static cv::Mat tpsWarp(
        const cv::Mat& src,
        const std::vector<cv::Point2f>& srcPoints,
        const std::vector<cv::Point2f>& dstPoints,
        const cv::Size& outputSize,
        double smoothness = 0.8
    );
    
private:
    static void computeTpsWeights(
        const std::vector<cv::Point2f>& srcPoints,
        const std::vector<cv::Point2f>& dstPoints,
        double smoothness,
        cv::Mat& weightsX,
        cv::Mat& weightsY
    );

    static double tpsKernel(double r);

    static cv::Mat buildKernelMatrix(
        const std::vector<cv::Point2f>& points,
        double smoothness
    );

    static cv::Point2f tpsTransformPoint(
        const cv::Point2f& point,
        const std::vector<cv::Point2f>& srcPoints,
        const cv::Mat& weightsX,
        const cv::Mat& weightsY
    );
};

}

#endif
