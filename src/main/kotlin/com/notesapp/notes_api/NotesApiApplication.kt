package com.notesapp.notes_api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching

@SpringBootApplication
@EnableCaching
class NotesApiApplication

fun main(args: Array<String>) {
	runApplication<NotesApiApplication>(*args)
}
