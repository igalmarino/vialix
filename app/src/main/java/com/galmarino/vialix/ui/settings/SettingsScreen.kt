package com.galmarino.vialix.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galmarino.vialix.R
import com.galmarino.vialix.settings.DistanceUnits
import com.galmarino.vialix.settings.NavSettings
import com.galmarino.vialix.settings.ThemeMode
import com.galmarino.vialix.settings.UiLanguage
import com.galmarino.vialix.settings.systemLocale
import com.galmarino.vialix.settings.systemManagesAppLocale
import com.galmarino.vialix.ui.ROUTING_PROFILES
import com.galmarino.vialix.ui.SectionHeaderRow
import com.galmarino.vialix.voice.SpokenLanguage
import java.util.Locale

private enum class Picker { Language, Profile, Units, Theme, AppLanguage }

/**
 * Full-screen settings page. Every control reads from [NavSettings.state] and writes through one
 * of its setters; the things that depend on a setting (TTS, routing, formatting) observe the store
 * themselves, so nothing here needs to know who is listening.
 *
 * @param showDeveloperOptions Adds the "Developer" section (route simulation). Debug builds only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: NavSettings, showDeveloperOptions: Boolean, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val state by settings.state.collectAsStateWithLifecycle()
    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
    val activity = LocalActivity.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.section_guidance))
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_voice_language)) },
                supportingContent = { Text(languageLabel(state.languageTag)) },
                modifier = Modifier.clickable { picker = Picker.Language },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_voice_guidance)) },
                supportingContent = { Text(stringResource(R.string.setting_voice_guidance_summary)) },
                trailingContent = { Switch(checked = state.voiceEnabled, onCheckedChange = settings::setVoiceEnabled) },
                modifier = Modifier.clickable { settings.setVoiceEnabled(!state.voiceEnabled) },
            )

            HorizontalDivider()

            SectionHeader(stringResource(R.string.section_routing))
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_routing_profile)) },
                supportingContent = { Text(profileLabel(state.routingProfile)) },
                modifier = Modifier.clickable { picker = Picker.Profile },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_units)) },
                supportingContent = { Text(stringResource(state.units.labelRes())) },
                modifier = Modifier.clickable { picker = Picker.Units },
            )

            HorizontalDivider()

            SectionHeader(stringResource(R.string.section_appearance))
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_theme)) },
                supportingContent = { Text(stringResource(state.themeMode.labelRes())) },
                modifier = Modifier.clickable { picker = Picker.Theme },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_app_language)) },
                supportingContent = { Text(uiLanguageLabel(state.uiLanguageTag)) },
                modifier = Modifier.clickable { picker = Picker.AppLanguage },
            )

            if (showDeveloperOptions) {
                HorizontalDivider()

                SectionHeader(stringResource(R.string.section_developer))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.simulate_driving)) },
                    supportingContent = { Text(stringResource(R.string.simulate_driving_summary)) },
                    trailingContent = {
                        Switch(checked = state.simulateDriving, onCheckedChange = settings::setSimulateDriving)
                    },
                    modifier = Modifier.clickable { settings.setSimulateDriving(!state.simulateDriving) },
                )
            }
        }
    }

    when (picker) {
        Picker.Language ->
            ChoiceDialog(
                title = stringResource(R.string.setting_voice_language),
                choices = languageChoices(),
                selected = state.languageTag,
                onSelect = { settings.setLanguageTag(it); picker = null },
                onDismiss = { picker = null },
            )
        Picker.Profile ->
            ChoiceDialog(
                title = stringResource(R.string.setting_routing_profile),
                choices = ROUTING_PROFILES.map { Choice(it.costing, stringResource(it.labelRes)) },
                selected = state.routingProfile,
                onSelect = { settings.setRoutingProfile(it); picker = null },
                onDismiss = { picker = null },
            )
        Picker.Units ->
            ChoiceDialog(
                title = stringResource(R.string.setting_units),
                choices = DistanceUnits.entries.map { Choice(it, stringResource(it.labelRes())) },
                selected = state.units,
                onSelect = { settings.setUnits(it); picker = null },
                onDismiss = { picker = null },
            )
        Picker.Theme ->
            ChoiceDialog(
                title = stringResource(R.string.setting_theme),
                choices = ThemeMode.entries.map { Choice(it, stringResource(it.labelRes())) },
                selected = state.themeMode,
                onSelect = { settings.setThemeMode(it); picker = null },
                onDismiss = { picker = null },
            )
        Picker.AppLanguage ->
            ChoiceDialog(
                title = stringResource(R.string.setting_app_language),
                choices = uiLanguageChoices(),
                selected = state.uiLanguageTag,
                onSelect = {
                    settings.setUiLanguageTag(it)
                    picker = null
                    // On API 33+ the framework restarts the Activity with the new locale itself.
                    if (!systemManagesAppLocale) activity?.recreate()
                },
                onDismiss = { picker = null },
            )
        null -> Unit
    }
}

/** The same list subheader as the search screen's recents header. */
@Composable
private fun SectionHeader(text: String) {
    SectionHeaderRow(title = text, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

/** "System default (Español (España))" for `null`, otherwise the language named in itself. */
@Composable
private fun languageLabel(tag: String?): String =
    if (tag == null) {
        stringResource(R.string.language_system_default, languageDisplayName(SpokenLanguage.valhallaTag()))
    } else {
        languageDisplayName(tag)
    }

/** The system-default entry first, then every supported language sorted by its own name. */
@Composable
private fun languageChoices(): List<Choice<String?>> =
    listOf(Choice<String?>(null, languageLabel(null))) +
        SpokenLanguage.SUPPORTED_TAGS.map { Choice<String?>(it, languageDisplayName(it)) }.sortedBy { it.label }

/** "System default (English)" for `null` — naming the translation the device language gets — else the language in itself. */
@Composable
private fun uiLanguageLabel(tag: String?): String =
    if (tag == null) {
        val context = LocalContext.current
        stringResource(R.string.language_system_default, languageDisplayName(UiLanguage.resolve(systemLocale(context))))
    } else {
        languageDisplayName(tag)
    }

@Composable
private fun uiLanguageChoices(): List<Choice<String?>> =
    listOf(Choice<String?>(null, uiLanguageLabel(null))) +
        UiLanguage.SUPPORTED_TAGS.map { Choice<String?>(it, languageDisplayName(it)) }.sortedBy { it.label }

/** Each language named in itself ("Español (España)"), so a user can find theirs whatever the UI language. */
internal fun languageDisplayName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}

@Composable
private fun profileLabel(costing: String): String =
    ROUTING_PROFILES.firstOrNull { it.costing == costing }?.let { stringResource(it.labelRes) } ?: costing

private fun ThemeMode.labelRes(): Int =
    when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }

private fun DistanceUnits.labelRes(): Int =
    when (this) {
        DistanceUnits.METRIC -> R.string.units_metric
        DistanceUnits.IMPERIAL -> R.string.units_imperial
    }
