package dev.eknath.GymJournal.config

import org.springframework.context.annotation.Configuration

@Configuration
class CatalystConfig {
    // No startup initialization.
    // ZCProject is initialized per-request by CatalystAuthFilter using the user's Bearer token.
    // In AppSail production, ZCProject.initProject() would be called here, but locally
    // catalyst serve does not provide the app-level credentials that the no-arg overload requires.
}
