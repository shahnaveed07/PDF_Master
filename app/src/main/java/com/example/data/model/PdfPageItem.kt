package com.example.data.model

import android.graphics.Bitmap

data class PdfPageItem(
    val pageIndex: Int,
    val displayPageNumber: Int = pageIndex + 1,
    val bitmap: Bitmap? = null,
    val rotationDegrees: Int = 0,
    val isSelected: Boolean = false
)
