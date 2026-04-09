package com.notesapp.notes_api.exception

// Thrown when login credentials are wrong
class InvalidCredentialsException(message: String = "Invalid email or password") : RuntimeException(message)

// Thrown when registering with an email that already exists
class EmailAlreadyExistsException(message: String = "Email already exists") : RuntimeException(message)

// Thrown when a refresh token is invalid, expired, or not found
class InvalidTokenException(message: String = "Invalid or expired token") : RuntimeException(message)

// Thrown when a resource (user, note, etc.) is not found
class ResourceNotFoundException(message: String = "Resource not found") : RuntimeException(message)
