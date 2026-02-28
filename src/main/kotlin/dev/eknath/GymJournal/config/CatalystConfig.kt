package dev.eknath.GymJournal.config

import org.springframework.context.annotation.Configuration

@Configuration
class CatalystConfig {
    // No startup initialization.
    // Catalyst SDK is initialized per-request in CatalystAuthFilter via
    // CatalystSDK.init(AuthHeaderProvider), which reads ZGS-injected request headers.
    // This is the correct approach for AppSail with Jakarta servlets (servlet API ≥5).
}
