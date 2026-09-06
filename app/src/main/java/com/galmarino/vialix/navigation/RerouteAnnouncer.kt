package com.galmarino.vialix.navigation

/**
 * Decides when "Rerouting" is spoken: once when the user first leaves the route, and not again for
 * [MIN_GAP_MS] even if the deviation flag flaps as the fixes come in near the threshold. Pure so
 * it can be tested; the ViewModel feeds it the core's deviation state.
 */
class RerouteAnnouncer(private val minGapMs: Long = MIN_GAP_MS) {

    private var wasOffRoute = false
    private var lastAnnouncedAtMs: Long? = null

    /** `true` when the transition to [offRoute] should be announced now. */
    fun onDeviation(offRoute: Boolean, nowMs: Long): Boolean {
        val risingEdge = offRoute && !wasOffRoute
        wasOffRoute = offRoute
        if (!risingEdge) return false
        val last = lastAnnouncedAtMs
        if (last != null && nowMs - last < minGapMs) return false
        lastAnnouncedAtMs = nowMs
        return true
    }

    /** Forget the previous trip: the next deviation announces again at once. */
    fun reset() {
        wasOffRoute = false
        lastAnnouncedAtMs = null
    }

    companion object {
        const val MIN_GAP_MS = 10_000L
    }
}
