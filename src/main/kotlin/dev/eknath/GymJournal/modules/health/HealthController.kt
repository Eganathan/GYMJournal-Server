package dev.eknath.GymJournal.modules.health

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/health")
class HealthController {

    // Public endpoint — no auth required (configured in SecurityConfig)
    // Use this to warm up the AppSail container on app launch
    @GetMapping
    fun health() = mapOf(
        "status" to "UP",
        "service" to "GymJournal API"
    )
}
