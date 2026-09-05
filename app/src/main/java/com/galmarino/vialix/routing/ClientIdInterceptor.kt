package com.galmarino.vialix.routing

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Identifies the app to routing servers. The FOSSGIS Valhalla demo server asks every published
 * client to send an `X-Client-Id` header so operators can reach out if traffic becomes a problem.
 */
class ClientIdInterceptor(
    private val clientId: String,
    private val userAgent: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request =
            chain.request().newBuilder()
                .header("X-Client-Id", clientId)
                .header("User-Agent", "$userAgent ($clientId)")
                .build()
        return chain.proceed(request)
    }
}
