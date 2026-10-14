package com.example.data.model

enum class PdfCompressionLevel(
    val label: String,
    val subtitle: String,
    val qualityPercentage: Int,
    val scaleFactor: Float,
    val estimatedReductionRatio: Double
) {
    LOW(
        label = "Low Compression",
        subtitle = "High quality, slight size reduction (~20%)",
        qualityPercentage = 85,
        scaleFactor = 0.9f,
        estimatedReductionRatio = 0.80
    ),
    MEDIUM(
        label = "Recommended",
        subtitle = "Optimal balance of quality & file size (~50%)",
        qualityPercentage = 65,
        scaleFactor = 0.75f,
        estimatedReductionRatio = 0.50
    ),
    HIGH(
        label = "High Compression",
        subtitle = "Smaller size, good for email sharing (~70%)",
        qualityPercentage = 45,
        scaleFactor = 0.60f,
        estimatedReductionRatio = 0.30
    ),
    EXTREME(
        label = "Extreme Compression",
        subtitle = "Maximum size reduction (~85%)",
        qualityPercentage = 25,
        scaleFactor = 0.45f,
        estimatedReductionRatio = 0.15
    )
}
