package com.dsc.pdfmaster.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_pdfs")
data class RecentPdfEntity(
    @PrimaryKey val filePath: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val lastAccessedTimestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val thumbnailPath: String? = null
)
