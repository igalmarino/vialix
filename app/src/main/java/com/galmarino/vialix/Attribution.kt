package com.galmarino.vialix

/**
 * Who the menu drawer credits for routing and search. Derived from the configured endpoints so
 * that pointing [NavConfig.valhallaEndpoint] at another provider (Stadia Maps' terms, for one,
 * require attribution) credits that provider instead of the default FOSSGIS server.
 */
object Attribution {

    private val KNOWN_HOSTS =
        mapOf(
            "valhalla1.openstreetmap.de" to "Valhalla (FOSSGIS)",
            "api.stadiamaps.com" to "Stadia Maps",
            "photon.komoot.io" to "Photon (komoot)",
        )

    /** The provider's name for a known public host, otherwise the host itself (`routing.example.org`). */
    fun creditFor(endpoint: String): String {
        val host =
            endpoint
                .substringAfter("://")
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore(':')
                .lowercase()
        return KNOWN_HOSTS[host] ?: host
    }
}
