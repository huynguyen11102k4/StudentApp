package com.examhub.student

import org.opencv.core.Point


object Config {
    
    const val FULL_WIDTH = 1200
    const val FULL_HEIGHT = 1800
    

    val MARKER_CENTERS = mapOf(
        // Corner markers
        1 to Point(78.0, 59.5),        // Top-Left
        2 to Point(78.0, 2491.5),      // Bottom-Left
        3 to Point(1729.5, 59.5),      // Top-Right
        4 to Point(1730.5, 2492.0),    // Bottom-Right
        
        // Internal alignment markers
        7 to Point(920.8, 146.0),
        9 to Point(1485.0, 146.5),
        13 to Point(370.0, 791.0),
        14 to Point(370.2, 1558.2),
        15 to Point(920.5, 790.5),
        16 to Point(920.5, 1558.0),
        17 to Point(1484.0, 791.0),
        18 to Point(1483.5, 1558.2),
        19 to Point(370.0, 2352.5),
        20 to Point(920.5, 2353.0),
        21 to Point(1486.0, 2353.2)
    )
    
    enum class ProcessingMode {
        REALTIME,   
        OFFLINE     
    }
    
    object DewarpThresholds {
        const val GOOD_THRESHOLD = 5.0      
        const val MODERATE_THRESHOLD = 20.0 
    }
    
    object PreprocessParams {
        // Bilateral filter for marker detection
        const val BILATERAL_D = 9
        const val BILATERAL_SIGMA_COLOR = 25.0
        const val BILATERAL_SIGMA_SPACE = 75.0
        
        // CLAHE
        const val CLAHE_CLIP_LIMIT = 4.0
        const val CLAHE_TILE_SIZE = 4
        
        // Post-warp bilateral
        const val BILATERAL_POST_D = 5
        const val BILATERAL_POST_SIGMA_COLOR = 50.0
        const val BILATERAL_POST_SIGMA_SPACE = 50.0
        
        // Gaussian blur
        const val GAUSSIAN_KSIZE = 3
        const val GAUSSIAN_SIGMA = 0.0
    }
    
    object ThresholdParams {
        const val ADAPTIVE_BLOCK_SIZE = 251
        const val ADAPTIVE_C = 5.0
        
        const val MORPH_KERNEL_SIZE = 3
        const val MORPH_ITERATIONS = 1
        
        const val FINAL_MORPH_KERNEL_SIZE = 2
        const val FINAL_MORPH_ITERATIONS = 1
    }

    object RoiParams {
        const val MIN_AREA = 20
        const val MIN_CIRCULARITY = 0.25
        const val FILL_THRESHOLD = 0.6  
    }
}
