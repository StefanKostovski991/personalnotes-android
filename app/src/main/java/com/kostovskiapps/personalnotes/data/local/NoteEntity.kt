package com.kostovskiapps.personalnotes.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room representation of a note. Only primitives are stored: enums are kept as their stable
 * string ids and the file list is serialised, so a schema change never corrupts user data.
 *
 * [contentFingerprint] is a hash of the note's payload. It identifies the same note when it
 * arrives back from another user, which is how repeated imports are detected.
 */
@Entity(
    tableName = "notes",
    indices = [Index(value = ["contentFingerprint"])],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
    val locationLabel: String,
    val latitude: Double?,
    val longitude: Double?,
    val colorId: String,
    val kindId: String,
    val fileRefs: List<String>,
    val contentFingerprint: String,
)
