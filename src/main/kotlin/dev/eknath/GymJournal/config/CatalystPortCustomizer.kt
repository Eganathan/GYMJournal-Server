package dev.eknath.GymJournal.config

import org.springframework.boot.web.server.ConfigurableWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.stereotype.Component

@Component
class CatalystPortCustomizer : WebServerFactoryCustomizer<ConfigurableWebServerFactory> {
    override fun customize(factory: ConfigurableWebServerFactory) {
        val port = System.getenv("X_ZOHO_CATALYST_LISTEN_PORT")?.toIntOrNull() ?: 8080
        factory.setPort(port)
    }
}
