package com.galmarino.vialix.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.galmarino.vialix.settings.Settings
import com.stadiamaps.ferrostar.core.AndroidTtsObserver
import com.stadiamaps.ferrostar.core.AndroidTtsStatusListener
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
 * never sets one). The observer only ever *follows* the settings store — Ferrostar's own mute
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
    }
}
