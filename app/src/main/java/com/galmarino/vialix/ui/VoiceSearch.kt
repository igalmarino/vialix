package com.galmarino.vialix.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.galmarino.vialix.R

/**
 * Voice search through whatever speech recogniser the device has (`RecognizerIntent`), so the app
 * needs no speech dependency of its own. Returns the action for the mic button, or null when no
 * recogniser is installed (common on a pure F-Droid device), in which case the button is hidden.
 * The recognised text is handed to [onResult] and the caller runs a normal search with it.
 */
@Composable
fun rememberVoiceSearchLauncher(languageTag: String, onResult: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val prompt = stringResource(R.string.voice_search_prompt)
    val currentOnResult by rememberUpdatedState(onResult)

    val intent =
        remember(languageTag, prompt) {
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    // Needs the RECOGNIZE_SPEECH <queries> entry in the manifest to see other packages on API 30+.
    val available = remember(intent) { context.packageManager.resolveActivity(intent, 0) != null }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
            if (!text.isNullOrEmpty()) currentOnResult(text)
        }

    if (!available) return null
    return {
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            // The recogniser was uninstalled since we checked; nothing sensible to do but log.
            Log.w("VoiceSearch", "No speech recogniser", e)
        }
    }
}
