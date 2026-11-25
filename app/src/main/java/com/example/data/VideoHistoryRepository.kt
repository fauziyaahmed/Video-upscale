package com.example.data

import kotlinx.coroutines.flow.Flow

class VideoHistoryRepository(private val dao: EnhancedVideoDao) {
    val exportedVideos: Flow<List<EnhancedVideoEntity>> = dao.getAllVideos()

    suspend fun saveExport(entity: EnhancedVideoEntity): Long = dao.insertVideo(entity)

    suspend fun deleteExport(id: Long) = dao.deleteById(id)
}
