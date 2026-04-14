package com.notesapp.notes_api.events

data class NoteCreatedEvent(
    var id: Long? = null,
    var ownerId: Long? = null,
    var title: String = "",
    var createdAt: Long = 0,
)