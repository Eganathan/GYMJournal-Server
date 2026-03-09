package dev.eknath.GymJournal.util

import com.zc.component.users.ZCUser
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Returns the authenticated user's Catalyst user ID for the current request.
 *
 * Resolution order:
 *  1. ZCUser.getInstance().currentUser?.userId — SDK-resolved (works when ZGS
 *     properly forwards the user session, i.e. same-domain deployments).
 *  2. Spring Security context principal — set by CatalystAuthFilter from
 *     X-Catalyst-Uid header (fallback for cross-domain deployments where
 *     ZCUser.currentUser is null).
 *
 * Throws if neither source yields a valid ID (should never happen in practice
 * since CatalystAuthFilter rejects unauthenticated requests with 401).
 */
fun currentUserId(): Long =
    ZCUser.getInstance().currentUser?.userId
        ?: SecurityContextHolder.getContext().authentication?.principal
            ?.toString()?.toLongOrNull()
        ?: error("No authenticated user — both ZCUser.currentUser and security context are null")
