package dev.eknath.GymJournal.util

import org.springframework.security.core.context.SecurityContextHolder

fun currentUserId(): String =
    SecurityContextHolder.getContext().authentication?.principal as? String
        ?: error("No authenticated user found in security context")
