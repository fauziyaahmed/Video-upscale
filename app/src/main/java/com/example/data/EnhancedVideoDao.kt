package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EnhancedVideoDao {
    @Query("SELECT * FROM enhanced_videos ORDER BY createdAtMs DESC")
    fun getAllVideos(): Flow<List<EnhancedVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: EnhancedVideoEntity): Long

    @Query("DELETE FROM enhanced_videos WHERE id = :id")
    suspend fun deleteById(id: Long)
}
