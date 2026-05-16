#include "include/tps_warper.h"
#include <cmath>
#include <iostream>

namespace omr {

double TpsWarper::tpsKernel(double r) {
    if (r < 1e-10) return 0.0;
    return r * r * std::log(r);
}

cv::Mat TpsWarper::buildKernelMatrix(
    const std::vector<cv::Point2f>& points,
    double smoothness
) {
    int n = static_cast<int>(points.size());

    cv::Mat K = cv::Mat::zeros(n + 3, n + 3, CV_64F);
    
    // Fill K_ij
    for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
            double dx = points[i].x - points[j].x;
            double dy = points[i].y - points[j].y;
            double r = std::sqrt(dx * dx + dy * dy);
            
            double value = tpsKernel(r);
            K.at<double>(i, j) = value;
            
            if (i == j) {
                K.at<double>(i, j) += smoothness;
            }
        }
    }
    
    // Fill P matrix (affine part: [1, x, y])
    for (int i = 0; i < n; i++) {
        K.at<double>(i, n) = 1.0;
        K.at<double>(i, n + 1) = points[i].x;
        K.at<double>(i, n + 2) = points[i].y;
        
        K.at<double>(n, i) = 1.0;
        K.at<double>(n + 1, i) = points[i].x;
        K.at<double>(n + 2, i) = points[i].y;
    }
    
    return K;
}

void TpsWarper::computeTpsWeights(
    const std::vector<cv::Point2f>& srcPoints,
    const std::vector<cv::Point2f>& dstPoints,
    double smoothness,
    cv::Mat& weightsX,
    cv::Mat& weightsY
) {
    int n = static_cast<int>(srcPoints.size());
    
    // Build kernel matrix
    cv::Mat K = buildKernelMatrix(srcPoints, smoothness);
    
    // Build target vectors (v_x and v_y)
    cv::Mat vx = cv::Mat::zeros(n + 3, 1, CV_64F);
    cv::Mat vy = cv::Mat::zeros(n + 3, 1, CV_64F);
    
    for (int i = 0; i < n; i++) {
        vx.at<double>(i, 0) = dstPoints[i].x;
        vy.at<double>(i, 0) = dstPoints[i].y;
    }
    
    // Solve linear system: K * w = v
    cv::solve(K, vx, weightsX, cv::DECOMP_SVD);
    cv::solve(K, vy, weightsY, cv::DECOMP_SVD);
}

cv::Point2f TpsWarper::tpsTransformPoint(
    const cv::Point2f& point,
    const std::vector<cv::Point2f>& srcPoints,
    const cv::Mat& weightsX,
    const cv::Mat& weightsY
) {
    int n = static_cast<int>(srcPoints.size());
    
    double x = 0.0;
    double y = 0.0;

    // Radial basis part
    for (int i = 0; i < n; i++) {
        double dx = point.x - srcPoints[i].x;
        double dy = point.y - srcPoints[i].y;
        double r = std::sqrt(dx * dx + dy * dy);
        double U = tpsKernel(r);
        
        x += weightsX.at<double>(i, 0) * U;
        y += weightsY.at<double>(i, 0) * U;
    }
    
    // Affine part: a0 + a1*x + a2*y
    x += weightsX.at<double>(n, 0) + 
         weightsX.at<double>(n + 1, 0) * point.x + 
         weightsX.at<double>(n + 2, 0) * point.y;
    
    y += weightsY.at<double>(n, 0) + 
         weightsY.at<double>(n + 1, 0) * point.x + 
         weightsY.at<double>(n + 2, 0) * point.y;
    
    return {static_cast<float>(x), static_cast<float>(y)};
}

cv::Mat TpsWarper::tpsWarp(
        const cv::Mat& src,
        const std::vector<cv::Point2f>& srcPoints,
        const std::vector<cv::Point2f>& dstPoints,
        const cv::Size& outputSize,
        double smoothness
) {
    cv::Mat weightsX, weightsY;
    // cv::remap samples source coordinates for each destination pixel, so the
    // TPS must be fitted in the inverse direction: canonical/template -> camera.
    computeTpsWeights(dstPoints, srcPoints, smoothness, weightsX, weightsY);

    // Khởi tạo 2 ma trận map cho x và y
    cv::Mat map_x(outputSize, CV_32FC1);
    cv::Mat map_y(outputSize, CV_32FC1);

    // Tính toán tọa độ nguồn cho từng điểm đích
#pragma omp parallel for collapse(2)
    for (int y = 0; y < outputSize.height; y++) {
        for (int x = 0; x < outputSize.width; x++) {
            cv::Point2f dstPoint(static_cast<float>(x), static_cast<float>(y));
            cv::Point2f srcPoint = tpsTransformPoint(dstPoint, dstPoints, weightsX, weightsY);

            map_x.at<float>(y, x) = srcPoint.x;
            map_y.at<float>(y, x) = srcPoint.y;
        }
    }

    // Dùng cv::remap nội suy bằng phần cứng (SIMD/Neon) siêu tốc
    cv::Mat dst;
    cv::remap(src, dst, map_x, map_y, cv::INTER_LINEAR, cv::BORDER_CONSTANT, cv::Scalar(255, 255, 255, 255));

    return dst;
}

}
