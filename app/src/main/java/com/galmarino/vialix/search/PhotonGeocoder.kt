package com.galmarino.vialix.search

import com.stadiamaps.ferrostar.core.InvalidStatusCodeException
import com.stadiamaps.ferrostar.core.NoResponseBodyException
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uniffi.ferrostar.GeographicCoordinate

/**
 * [Geocoder] backed by a Photon server (komoot's open-source OSM geocoder). Shares the app's
 * `OkHttpClient`, so requests carry the same identifying headers as routing requests.
 */
class PhotonGeocoder(
    private val endpoint: String,
    private val client: OkHttpClient,
    private val limit: Int = DEFAULT_LIMIT,
) : Geocoder {

    override suspend fun search(query: String, bias: GeographicCoordinate?, languageTag: String?): List<Place> =
        fetch(buildUrl(endpoint, query, bias, photonLanguage(languageTag), limit))

    override suspend fun reverse(coordinate: GeographicCoordinate, languageTag: String?): Place? =
        fetch(buildReverseUrl(endpoint, coordinate, photonLanguage(languageTag))).firstOrNull()

    private suspend fun fetch(url: HttpUrl): List<Place> {
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw InvalidStatusCodeException(response.code)
            val body = response.body.string().ifEmpty { throw NoResponseBodyException() }
            PhotonResponseParser.parse(body)
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 8

        /**
         * Pure so it can be tested. Builds on the endpoint's own query string, so a self-hosted
         * `...?api_key=` survives. Photon ignores `lat`/`lon` unless both are present, and only
         * knows a few `lang` values, hence the all-or-nothing parameters.
         */
        fun buildUrl(endpoint: String, query: String, bias: GeographicCoordinate?, lang: String?, limit: Int): HttpUrl =
            endpoint.toHttpUrl().newBuilder().apply {
                addQueryParameter("q", query)
                addQueryParameter("limit", limit.toString())
                if (lang != null) addQueryParameter("lang", lang)
                if (bias != null) {
                    addQueryParameter("lat", bias.lat.toString())
                    addQueryParameter("lon", bias.lng.toString())
                }
            }.build()

        /**
         * Photon serves reverse lookups from `/reverse` next to `/api`: replace the last path
         * segment (an empty one for a bare host or a trailing slash), keeping the endpoint's own
         * query string.
         */
        fun buildReverseUrl(endpoint: String, coordinate: GeographicCoordinate, lang: String?): HttpUrl {
            val base = endpoint.toHttpUrl()
            return base.newBuilder().apply {
                setPathSegment(base.pathSegments.lastIndex, REVERSE_SEGMENT)
                addQueryParameter("lat", coordinate.lat.toString())
                addQueryParameter("lon", coordinate.lng.toString())
                addQueryParameter("limit", "1")
                if (lang != null) addQueryParameter("lang", lang)
            }.build()
        }

        private const val REVERSE_SEGMENT = "reverse"
    }
}

/** Suspends until the call completes; cancelling the coroutine cancels the call on the wire. */
private suspend fun Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        enqueue(
            object : Callback {
                // If the coroutine was cancelled in the meantime, nobody will read (and close) the body.
                override fun onResponse(call: Call, response: Response) = continuation.resume(response) { response.close() }

                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }
            },
        )
        continuation.invokeOnCancellation { cancel() }
    }
