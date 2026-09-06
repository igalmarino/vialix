// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.map

import android.util.Log
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException

/**
 * Downloads a map style so [PoiLabelStylePatch] (and, for the dark theme, [NightStylePatch]) can
 * be applied before MapLibre sees it. The result is the patched style JSON, or [MapStyleState.Unavailable]
 * when anything goes wrong (offline, non-JSON body, relative URLs) — the caller then points MapLibre
 * at the URL directly and only loses the patches. The URL is a parameter because a dedicated dark style can
 * be configured; by default the dark theme is derived from the light style, so a theme toggle
 * re-patches the same download, which is why the last body is remembered.
 */
class MapStyleLoader(private val client: OkHttpClient) {

    @Volatile private var lastDownload: Pair<String, String>? = null

    /** @param night whether to turn the (light) style into its night version. */
    suspend fun load(styleUrl: String, night: Boolean): MapStyleState {
        val body =
            lastDownload?.takeIf { it.first == styleUrl }?.second
                ?: fetch(styleUrl)?.also { lastDownload = styleUrl to it }
                ?: return MapStyleState.Unavailable(styleUrl, downloadFailed = true)
        if (!PoiLabelStylePatch.canBeInlined(body)) {
            Log.i(TAG, "Style uses relative URLs; loading it unpatched")
            return MapStyleState.Unavailable(styleUrl, downloadFailed = false)
        }
        return try {
            val patched = PoiLabelStylePatch.apply(body)
            MapStyleState.Patched(if (night) NightStylePatch.apply(patched) else patched)
        } catch (e: JSONException) {
            Log.w(TAG, "Style is not valid JSON; loading it unpatched", e)
            MapStyleState.Unavailable(styleUrl, downloadFailed = false)
        }
    }

    private suspend fun fetch(styleUrl: String): String? = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(styleUrl).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) return
                    Log.w(TAG, "Could not download map style", e)
                    continuation.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) Log.w(TAG, "Map style download failed: HTTP ${it.code}")
                        continuation.resume(if (it.isSuccessful) it.body.string() else null)
                    }
                }
            },
        )
    }

    private companion object {
        const val TAG = "MapStyleLoader"
    }
}
