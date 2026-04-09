package com.notesapp.notes_api.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // 1. Read Authorization header
        val authHeader = request.getHeader("Authorization")

        // 2. If missing or not Bearer, skip — let the request continue
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        // 3. Extract the token (strip "Bearer " prefix)
        val token = authHeader.removePrefix("Bearer ")

        // 4. Validate the token
        if (jwtService.isTokenValid(token)) {
            // 5. Extract userId and set authentication in SecurityContext
            val userId = jwtService.extractUserId(token)
            val authentication = UsernamePasswordAuthenticationToken(
                userId,    // principal — who the user is
                null,      // credentials — already verified, not needed
                emptyList() // authorities/roles — empty for now
            )
            SecurityContextHolder.getContext().authentication = authentication
        }

        // 6. Continue the filter chain
        filterChain.doFilter(request, response)
    }
}
