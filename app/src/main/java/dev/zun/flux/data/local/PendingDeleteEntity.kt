package dev.zun.flux.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_deletes")
data class PendingDeleteEntity(
    @PrimaryKey val jobId: String,
    val createdAt: Long,
)
