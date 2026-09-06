package com.galmarino.vialix.voice

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import com.galmarino.vialix.R
import com.galmarino.vialix.settings.Settings
import com.stadiamaps.ferrostar.core.AndroidTtsObserver
import com.stadiamaps.ferrostar.core.AndroidTtsStatusListener
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import uniffi.ferrostar.SpokenInstruction

/**
 * Spoken turn-by-turn guidance.
 *
 * Ferrostar's [AndroidTtsObserver] does the work: the navigation core hands it every
 * [uniffi.ferrostar.SpokenInstruction] as the trip progresses, and it speaks them through the
 * platform TTS engine while holding transient audio focus, so music from other apps ducks for the
 * duration of each announcement.
 *
 * Two things Ferrostar deliberately leaves to the app are added here, both driven by the
 * [Settings] flow: whether guidance is muted, and which language the voice speaks (its `onInit`
 * never sets one). The app also speaks one phrase of its own, "Rerouting" ([announceRerouting]),
 * through the same observer so that mute, audio focus and ducking apply to it as well. The observer only ever *follows* the settings store — Ferrostar's own mute
 * button writes to the store too (see `NavigationViewModel.toggleMute`), so the button and the
 * settings screen cannot disagree.
 *
 * The observer must be attached to `FerrostarCore` *before* the navigation ViewModel is built —
 * see [com.galmarino.vialix.AppGraph].
 */
class VoiceGuidance(
    context: Context,
    private val settings: StateFlow<Settings>,
    scope: CoroutineScope,
) {

    private val appContext: Context = context.applicationContext

    /**
     * Attach this to `FerrostarCore.spokenInstructionObserver`. Its `muteState` is what Ferrostar's
     * navigation view binds its mute button to.
     */
    val observer: AndroidTtsObserver = AndroidTtsObserver(appContext)

    init {
        observer.statusObserver = StatusListener()
        scope.launch {
            settings.collect { current ->
                observer.setMuted(!current.voiceEnabled)
                applyLanguage(current)
            }
        }
    }

    /**
     * Binds the TTS engine. Safe to call repeatedly: the observer ignores the call when an engine
     * is already bound. Engine start-up takes a moment, so call it before the first instruction is
     * due rather than at the moment it fires.
     */
    fun start() {
        observer.start()
    }

    /** Releases the TTS engine. A later [start] binds a fresh one. */
    fun shutdown() {
        observer.shutdown()
    }

    private var announcementStartedAt: Long? = null

    /**
     * Says "Rerouting" in the guidance language, queued behind whatever is being spoken. The
     * phrase is a [SpokenInstruction] like any other, so the observer's mute switch silences it.
     * Languages the app has no translation for get the English phrase (resource fallback), which
     * beats silence when the map has just gone blank.
     */
    fun announceRerouting() {
        if (observer.isMuted) return
        val text = guidanceResources().getString(R.string.rerouting_announcement)
        announcementStartedAt = SystemClock.elapsedRealtime()
        observer.onSpokenInstructionTrigger(
            SpokenInstruction(text = text, ssml = null, triggerDistanceBeforeManeuver = 0.0, utteranceId = UUID.randomUUID()),
        )
    }

    /**
     * How long the phrase started by [announceRerouting] still needs, roughly. `FerrostarCore.replaceRoute`
     * stops speech and clears the queue, so the reroute processor waits this long before swapping
     * the route in, or the word is cut off whenever the server answers within a second.
     */
    fun remainingAnnouncementMs(): Long {
        val started = announcementStartedAt ?: return 0
        return (started + ANNOUNCEMENT_MS - SystemClock.elapsedRealtime()).coerceAtLeast(0)
    }

    /** The app's strings in the guidance language rather than the interface language. */
    private fun guidanceResources() =
        appContext.createConfigurationContext(
            Configuration(appContext.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(settings.value.resolvedLanguageTag()))
            },
        ).resources

    /**
     * Points the bound engine at the guidance language, so the voice matches the language the
     * instruction text was rendered in. A no-op until the engine is up; anything the engine cannot
     * speak is left alone, so it keeps its own default.
     */
    private fun applyLanguage(current: Settings = settings.value) {
        val tts = observer.tts ?: return
        val locale = Locale.forLanguageTag(current.resolvedLanguageTag())
        if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
            tts.setLanguage(locale)
        } else {
            Log.i(TAG, "No TTS voice for $locale; using the engine default")
        }
    }

    private inner class StatusListener : AndroidTtsStatusListener {
        override fun onTtsInitialized(tts: TextToSpeech?, status: Int) {
            if (tts == null || status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TTS engine unavailable (status $status); guidance will be silent")
                return
            }
            applyLanguage()
        }

        override fun onTtsShutdownAndRelease() = Unit

        override fun onTtsSpeakError(utteranceId: String, status: Int) {
            Log.w(TAG, "Failed to speak instruction $utteranceId (status $status)")
        }
    }

    private companion object {
        const val TAG = "VoiceGuidance"

        /** Generous budget for one or two spoken words, engine start-up included. */
        const val ANNOUNCEMENT_MS = 1500L
    }
}
