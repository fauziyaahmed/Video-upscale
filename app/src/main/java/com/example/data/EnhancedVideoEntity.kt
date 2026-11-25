package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "enhanced_videos")
data class EnhancedVideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceUri: String,
    val outputUri: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val targetWidth: Int,
    val targetHeight: Int,
    val presetName: String,
    val codecLabel: String,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val processingMs: Long,
    val createdAtMs: Long = System.currentTimeMillis()
)
