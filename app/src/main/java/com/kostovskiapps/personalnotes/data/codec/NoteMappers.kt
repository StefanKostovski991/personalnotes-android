package com.kostovskiapps.personalnotes.data.codec

import com.kostovskiapps.personalnotes.data.local.NoteEntity
import com.kostovskiapps.personalnotes.domain.model.Note
import com.kostovskiapps.personalnotes.domain.model.NoteColor
import com.kostovskiapps.personalnotes.domain.model.NoteKind
import com.kostovskiapps.personalnotes.domain.model.NoteLocation

/** Entity -> domain. */
fun NoteEntity.toDomain(): Note = Note(
    id = id,
    title = title,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
    location = NoteLocation(
        label = locationLabel,
        latitude = latitude,
        longitude = longitude,
    ),
    color = NoteColor.fromId(colorId),
    kind = NoteKind.fromId(kindId),
    fileRefs = fileRefs,
)

/** Domain -> entity. [fingerprint] is passed in because it is derived from the payload. */
fun Note.toEntity(fingerprint: String): NoteEntity = NoteEntity(
    id = id,
    title = title,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
    locationLabel = location.label,
    latitude = location.latitude,
    longitude = location.longitude,
    colorId = color.id,
    kindId = kind.id,
    fileRefs = fileRefs,
    contentFingerprint = fingerprint,
)

/**
 * Domain -> wire payload.
 *
 * [id] is a stable string so a note keeps its identity across devices even though the local
 * Room id is auto generated. [attachments] are supplied by the caller, which is the only
 * layer allowed to read the private files.
 */
fun Note.toPayload(
    id: String = localPayloadId(),
    attachments: List<NoteAttachmentPayload> = emptyList(),
): NotePayload = NotePayload(
    id = id,
    title = title,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
    colorId = color.id,
    kindId = kind.id,
    location = if (location.label.isBlank() && !location.hasCoordinates) {
        null
    } else {
        NoteLocationPayload(
            label = location.label,
            latitude = location.latitude,
            longitude = location.longitude,
        )
    },
    attachments = attachments,
)

/** Payload -> domain, with attachments already stored and turned into file refs. */
fun NotePayload.toDomain(fileRefs: List<String>, localId: Long = Note.NO_ID): Note = Note(
    id = localId,
    title = title,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
    location = NoteLocation(
        label = location?.label.orEmpty(),
        latitude = location?.latitude,
        longitude = location?.longitude,
    ),
    color = NoteColor.fromId(colorId),
    kind = NoteKind.fromId(kindId),
    fileRefs = fileRefs,
)

/** A local-only identity for outbound notes; the receiving side assigns its own Room id. */
private fun Note.localPayloadId(): String =
    if (isPersisted) "local-$id" else "new-${System.currentTimeMillis()}"
