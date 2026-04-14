package com.notesapp.notes_api.events

import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class NoteEventConsumer {

    @KafkaListener(topics = ["note-created"])
    fun handleNoteCreated(event: NoteCreatedEvent) {
        println("Note created: ${event.title} by user ${event.ownerId}")
    }
}