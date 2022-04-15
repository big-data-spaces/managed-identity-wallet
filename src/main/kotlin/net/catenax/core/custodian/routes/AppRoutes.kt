package net.catenax.core.custodian.routes

import io.bkbn.kompendium.auth.Notarized.notarizedAuthenticate
import io.bkbn.kompendium.auth.configuration.JwtAuthConfiguration
import io.ktor.application.*
import io.ktor.routing.*
import net.catenax.core.custodian.services.BusinessPartnerDataService
import net.catenax.core.custodian.services.WalletService

fun Application.appRoutes(walletService: WalletService, businessPartnerDataService: BusinessPartnerDataService) {

    routing {
        route("/api") {

            val authConfig = object : JwtAuthConfiguration {
                override val name: String = "auth-jwt"
            }

            // based on: authenticate("auth-jwt")
            notarizedAuthenticate(authConfig) {
                walletRoutes(walletService, businessPartnerDataService)
                businessPartnerDataRoutes(businessPartnerDataService)
                didDocRoutes(walletService)
                vcRoutes(walletService)
                vpRoutes(walletService)
            }
        }
    }
}
