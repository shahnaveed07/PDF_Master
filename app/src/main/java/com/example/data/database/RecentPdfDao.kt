package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentPdfDao {
    @Query("SELECT * FROM recent_pdfs ORDER BY lastAccessedTimestamp DESC")
    fun getAllRecentPdfs(): Flow<List<RecentPdfEntity>>

    @Query("SELECT * FROM recent_pdfs WHERE isFavorite = 1 ORDER BY lastAccessedTimestamp DESC")
    fun getFavoritePdfs(): Flow<List<RecentPdfEntity>>

    @Query("SELECT * FROM recent_pdfs WHERE filePath = :path LIMIT 1")
    suspend fun getPdfByPath(path: String): RecentPdfEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdf(pdf: RecentPdfEntity)

    @Query("UPDATE recent_pdfs SET isFavorite = :isFavorite WHERE filePath = :filePath")
    suspend fun updateFavorite(filePath: String, isFavorite: Boolean)

    @Query("UPDATE recent_pdfs SET fileName = :newName WHERE filePath = :filePath")
    suspend fun renamePdf(filePath: String, newName: String)

    @Query("DELETE FROM recent_pdfs WHERE filePath = :filePath")
    suspend fun deletePdfByPath(filePath: String)

    @Query("DELETE FROM recent_pdfs")
    suspend fun clearAllRecentPdfs()
}
